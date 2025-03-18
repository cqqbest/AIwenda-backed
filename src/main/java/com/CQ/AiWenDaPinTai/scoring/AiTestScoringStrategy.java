package com.CQ.AiWenDaPinTai.scoring;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.CQ.AiWenDaPinTai.common.ErrorCode;
import com.CQ.AiWenDaPinTai.exception.ThrowUtils;
import com.CQ.AiWenDaPinTai.manager.AiManger;
import com.CQ.AiWenDaPinTai.model.dto.question.QuestionAnswerDTO;
import com.CQ.AiWenDaPinTai.model.dto.question.QuestionContentDTO;
import com.CQ.AiWenDaPinTai.model.entity.App;
import com.CQ.AiWenDaPinTai.model.entity.Question;
import com.CQ.AiWenDaPinTai.model.entity.UserAnswer;
import com.CQ.AiWenDaPinTai.model.enums.AppTypeEnum;
import com.CQ.AiWenDaPinTai.model.vo.QuestionVO;
import com.CQ.AiWenDaPinTai.service.AppService;
import com.CQ.AiWenDaPinTai.service.QuestionService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.codec.digest.DigestUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;


import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 *
 */
@ScoringStrategyConfig(appType = 1,scoringStrategy = 1)
public class AiTestScoringStrategy  implements ScoringStrategy{

    @Resource
    private AppService appService;
    @Resource
    private QuestionService questionService;
    @Resource
    private AiManger aiManger;
    @Resource
    private RedissonClient redissonClient;


    private Cache<String, String>answerCacheMap=
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();


    //系统Prompt
    private static final String AI_TEST_SCORING_SYSTEM_MESSAGE = "你是一位严谨的判题专家，我会给你如下信息：\n" +
            "```\n" +
            "应用名称，\n" +
            "【【【应用描述】】】，\n" +
            "题目和用户回答的列表：格式为 [{\"title\": \"题目\",\"answer\": \"用户回答\"}]\n" +
            "```\n" +
            "\n" +
            "请你根据上述信息，按照以下步骤来对用户进行评价：\n" +
            "1. 要求：需要给出一个明确的评价结果，包括评价名称（尽量简短）和评价描述（尽量详细，大于 200 字）\n" +
            "2. 严格按照下面的 json 格式输出评价名称和评价描述\n" +
            "```\n" +
            "{\"resultName\": \"评价名称\", \"resultDesc\": \"评价描述\"}\n" +
            "```\n" +
            "3. 返回格式必须为 JSON 对象\n";
    //用户Prompt
    private String getGenerateQuestionUserMessage(App app, List<QuestionContentDTO> questionContentDTO, List<String> choices) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        List<QuestionAnswerDTO> questionAnswerDTOS = new ArrayList<>();
        for (int i = 0; i < questionContentDTO.size(); i++) {
            QuestionAnswerDTO questionAnswerDTO = new QuestionAnswerDTO();
            questionAnswerDTO.setTitle(questionContentDTO.get(i).getTitle());
            questionAnswerDTO.setUserAnswer(choices.get(i));
            questionAnswerDTOS.add(questionAnswerDTO);
        }
        userMessage.append(JSONUtil.toJsonStr(questionAnswerDTOS));
        return userMessage.toString();
    }

    private static final String AI_ANSWER_LOCK = "AI_ANSWER_LOCK";

    @Override
    public UserAnswer doScore(List<String> choices, App app) throws Exception {
        //提取数据并校验
        App App = appService.getById(app.getId());
        ThrowUtils.throwIf(App == null, ErrorCode.NOT_FOUND_ERROR);
        //设置key
        String key = buildCacheKey(app.getId(),JSONUtil.toJsonStr(choices));
        //获取缓存
        String cache = answerCacheMap.getIfPresent(key);
        if(StrUtil.isNotBlank(cache)){
            //转换为UserAnswer
            UserAnswer userAnswer = JSONUtil.toBean(cache, UserAnswer.class);
            userAnswer.setChoices(JSONUtil.toJsonStr(choices));
            userAnswer.setAppId(app.getId());
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            return userAnswer;
        }
        //设置锁
        RLock lock = redissonClient.getLock(AI_ANSWER_LOCK + key);
        try{
            //尝试抢锁，设置最大等待时间是3，超时释放时间是15秒
            boolean locked = lock.tryLock(3, 15, TimeUnit.SECONDS);
            //如果没有抢到锁返回空
            if(!locked){
                return null;
            }
            //抢到锁执行后续业务
            //获取题目数据
            Question question= questionService.getOne(
                    Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, app.getId())
            );
            ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
            //转换为questioncontentDTO
            String questionContent = question.getQuestionContent();
            List<QuestionContentDTO> questionContentDTO = JSONUtil.toList(questionContent, QuestionContentDTO.class);
            String generateQuestionUserMessage = getGenerateQuestionUserMessage(app, questionContentDTO, choices);
            //调用Ai
            String request = aiManger.doSyncUnstableRequest(AI_TEST_SCORING_SYSTEM_MESSAGE, generateQuestionUserMessage);
            //解析结果
            int start = request.indexOf("{");
            int end = request.lastIndexOf("}");
            String result = request.substring(start, end + 1);
            //设置缓存
            answerCacheMap.put(key, result);
            //封装答案
            UserAnswer userAnswer = JSONUtil.toBean(result, UserAnswer.class);
            String jsonStr = JSONUtil.toJsonStr(choices);
            userAnswer.setChoices(jsonStr);
            userAnswer.setAppId(app.getId());
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            return userAnswer;
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            //判断是否持有锁
            if(lock!=null&&lock.isLocked()){
                //判断是否是当前线程拿锁，防止锁被其他线程释放
                if(lock.isHeldByCurrentThread()){
                    lock.unlock();
                }
            }
        }

    }




    /**
     * 没有用缓存的ai评分
     * @param choices
     * @param
     * @return
     * @throws Exception
     */
   // @Override
    /*
    public UserAnswer doScore(List<String> choices, App app)  {
        //提取数据并校验
        App App = appService.getById(app.getId());
        ThrowUtils.throwIf(App == null, ErrorCode.NOT_FOUND_ERROR);
        //获取题目数据
        Question question= questionService.getOne(
                Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, app.getId())
        );
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        //转换为questioncontentDTO
        String questionContent = question.getQuestionContent();
        List<QuestionContentDTO> questionContentDTO = JSONUtil.toList(questionContent, QuestionContentDTO.class);
        String generateQuestionUserMessage = getGenerateQuestionUserMessage(app, questionContentDTO, choices);
        //调用Ai
        String request = aiManger.doSyncUnstableRequest(AI_TEST_SCORING_SYSTEM_MESSAGE, generateQuestionUserMessage);
        //解析结果
        int start = request.indexOf("{");
        int end = request.lastIndexOf("}");
        String result = request.substring(start, end + 1);
        //封装答案
        UserAnswer userAnswer = JSONUtil.toBean(result, UserAnswer.class);
        String jsonStr = JSONUtil.toJsonStr(choices);
        userAnswer.setChoices(jsonStr);
        userAnswer.setAppId(app.getId());
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        return userAnswer;
    }
     */

    /**
     * 缓存构建key的方法
     */
    public String buildCacheKey(Long appId, String choices) {
        return DigestUtils.md5Hex(appId + choices);
    }
}

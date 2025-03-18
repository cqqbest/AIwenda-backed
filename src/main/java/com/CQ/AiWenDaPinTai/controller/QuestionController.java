package com.CQ.AiWenDaPinTai.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.CQ.AiWenDaPinTai.annotation.AuthCheck;
import com.CQ.AiWenDaPinTai.common.DeleteRequest;
import com.CQ.AiWenDaPinTai.config.VipSchedulersConfig;
import com.CQ.AiWenDaPinTai.constant.UserConstant;
import com.CQ.AiWenDaPinTai.exception.BusinessException;
import com.CQ.AiWenDaPinTai.exception.ThrowUtils;
import com.CQ.AiWenDaPinTai.manager.AiManger;
import com.CQ.AiWenDaPinTai.model.dto.question.*;
import com.CQ.AiWenDaPinTai.model.entity.App;
import com.CQ.AiWenDaPinTai.model.entity.User;
import com.CQ.AiWenDaPinTai.model.enums.AppTypeEnum;
import com.CQ.AiWenDaPinTai.model.vo.QuestionVO;
import com.CQ.AiWenDaPinTai.service.AppService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhipu.oapi.service.v4.model.ModelData;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import com.CQ.AiWenDaPinTai.service.QuestionService;
import com.CQ.AiWenDaPinTai.service.UserService;
import com.CQ.AiWenDaPinTai.common.BaseResponse;
import com.CQ.AiWenDaPinTai.common.ErrorCode;
import com.CQ.AiWenDaPinTai.common.ResultUtils;
import com.CQ.AiWenDaPinTai.model.entity.Question;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 问题接口
 *
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private AppService appService;

    @Resource
    private AiManger aiManger;

    @Resource
    private Scheduler vipScheduler;

    // region 增删改查

    /**
     * 创建问题
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<QuestionContentDTO> questionContent = questionAddRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContent));
        // 数据校验
        questionService.validQuestion(question, true);
        // todo 填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除问题
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新问题（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<QuestionContentDTO> questionContent = questionUpdateRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContent));
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取问题（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    /**
     * 分页获取问题列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取问题列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 分页获取当前登录用户创建的问题列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑问题（给用户使用）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 在此处将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<QuestionContentDTO> questionContent = questionEditRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContent));
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // endregion


    //region:ai生成题目


    //生成题目的系统消息
    private static final String GENERATE_QUESTION_SYSTEM_MESSAGE = "你是一位严谨的出题专家，我会给你如下信息：\n" +
            "```\n" +
            "应用名称，\n" +
            "【【【应用描述】】】，\n" +
            "应用类别，\n" +
            "要生成的题目数，\n" +
            "每个题目的选项数\n" +
            "```\n" +
            "\n" +
            "请你根据上述信息，按照以下步骤来出题：\n" +
            "1. 要求：题目和选项尽可能地短，题目不要包含序号，每题的选项数以我提供的为主，题目不能重复\n" +
            "2. 严格按照下面的 json 格式输出题目和选项\n" +
            "```\n" +
            "[{\"options\":[{\"value\":\"选项内容\",\"key\":\"A\"},{\"value\":\"\",\"key\":\"B\"}],\"title\":\"题目标题\"}]\n" +
            "```\n" +
            "title 是题目，options 是选项，每个选项的 key 按照英文字母序（比如 A、B、C、D）以此类推，value 是选项内容\n" +
            "3. 检查题目是否包含序号，若包含序号则去除序号\n" +
            "4. 返回的题目列表格式必须为 JSON 数组";

    /**
     * 拼接生成题目的用户消息
     *
     * @param app
     * @param questionNumber
     * @param optionNumber
     * @return
     */
    private String getGenerateQuestionUserMessage(App app, int questionNumber, int optionNumber) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        userMessage.append(AppTypeEnum.getEnumByValue(app.getAppType()).getText() + "类").append("\n");
        userMessage.append(questionNumber).append("\n");
        userMessage.append(optionNumber);
        return userMessage.toString();
    }

    @PostMapping("/ai_generate")
    public BaseResponse<List<QuestionContentDTO>> aiGenerateQuestion(@RequestBody AiGenerateQuestionRequest aiGenerateQuestionRequest,HttpServletRequest request) {
        ThrowUtils.throwIf(aiGenerateQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        //提取数据并校验
        Long appId = aiGenerateQuestionRequest.getAppId();
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        int questionNum = aiGenerateQuestionRequest.getQuestionNum();
        int opinionNum = aiGenerateQuestionRequest.getOpinionNum();
        //获取用户消息,系统消息就是上面定义好的
        String generateQuestionUserMessage = getGenerateQuestionUserMessage(app, questionNum, opinionNum);
        //调用Ai生成题目的Manger，选择合适的方法生成题目
        String AiRequest = aiManger.doSyncRequest(GENERATE_QUESTION_SYSTEM_MESSAGE, generateQuestionUserMessage,null);
        //截取json字符串部分（因为ai会生成无用字符）
        int start = AiRequest.indexOf("[");
        int end = AiRequest.lastIndexOf("]");
        String json = AiRequest.substring(start, end + 1);
        //排除非转义字符
        String res = StringEscapeUtils.unescapeJava(json);
        log.info("json:{}", json);
        //将json字符串转换为List<QuestionContentDTO>
        List<QuestionContentDTO> QuestionList = JSONUtil.toList(json, QuestionContentDTO.class);
        //操作数据库
        Question question = new Question();
        question.setAppId(appId);
        question.setQuestionContent(json);
        question.setUserId(userService.getLoginUser(request).getId());
        questionService.save(question);
        //返回题目列表
        return ResultUtils.success(QuestionList);
    }
    //endregion

    @GetMapping("/ai_generate/sse")
    public SseEmitter aiGenerateQuestionSse(AiGenerateQuestionRequest aiGenerateQuestionRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(aiGenerateQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        //提取数据并校验
        Long appId = aiGenerateQuestionRequest.getAppId();
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        int questionNum = aiGenerateQuestionRequest.getQuestionNum();
        int opinionNum = aiGenerateQuestionRequest.getOpinionNum();
        //获取用户消息,系统消息就是上面定义好的
        String generateQuestionUserMessage = getGenerateQuestionUserMessage(app, questionNum, opinionNum);
        //建立连接对象，0L表示不设置超时时间
        SseEmitter sseEmitter = new SseEmitter(0L);
        //调用Ai生成题目的Manger，选择合适的方法生成题目
        Flowable<ModelData> modelDataFlowable = aiManger.doStreamRequest(GENERATE_QUESTION_SYSTEM_MESSAGE, generateQuestionUserMessage, null);
        // 创建一个StringBuilder对象
        StringBuilder sb = new StringBuilder();
        // 创建一个AtomicInteger对象，用于记录大括号的数量
        AtomicInteger flag = new AtomicInteger(0);
        //给用户分配线程池
        Scheduler schedulers = Schedulers.io();
        //获取登录用户
        User loginUser = userService.getLoginUser(request);
        //判断当前用户角色是否为vip，如果是分配独立vip线程池
        if("vip".equals(loginUser.getUserRole())){
            schedulers = vipScheduler;
        }
        // 对modelDataFlowable进行观察，并切换到IO线程
        modelDataFlowable
                .observeOn(schedulers)
                // 将chunk转换为message
                .map(chunk -> chunk.getChoices().get(0).getDelta().getContent())
                // 将message中的空格替换为空字符串
                .map(message -> message.replaceAll("\\s", ""))
                // 过滤掉空字符串
                .filter(StrUtil::isNotBlank)
                // 将message转换为字符流
                .flatMap(messages -> {
                    List<Character> charList = new ArrayList<>();
                    // 遍历message中的每个字符
                    for (char c : messages.toCharArray()) {
                        charList.add(c);

                    }
                    // 返回字符流
                    return Flowable.fromIterable(charList);
                })
                // 对每个字符进行处理
                .doOnNext(c -> {
                    // 如果字符是左大括号，则将flag加1
                    if (c == '{') {
                        flag.addAndGet(1);
                    }
                    // 如果flag大于0，则将字符添加到StringBuilder中
                    if (flag.get() > 0) {
                        sb.append(c);
                    }
                    // 如果字符是右大括号，则将flag减1
                    if (c == '}') {
                        flag.addAndGet(-1);
                        // 如果flag等于0，则将StringBuilder中的内容转换为JSON字符串，并通过sseEmitter发送
                        if (flag.get() == 0) {
                            sseEmitter.send(JSONUtil.toJsonStr(sb.toString()));
                            // 清空StringBuilder
                            sb.setLength(0);
                        }
                    }
                })
                // 如果发生错误，则记录错误日志
                .doOnError((e) ->
                        log.error("sse error", e)
                )
                // 当完成时，完成sseEmitter
                .doOnComplete(sseEmitter::complete)
                // 订阅
                .subscribe();
        // 返回sseEmitter
        return sseEmitter;
    }


    /**
     * 用于测试线程隔离性
     * @param aiGenerateQuestionRequest
     * @return
     */
    @GetMapping("/ai_generate/sse/test")
    public SseEmitter aiGenerateQuestionSseTest(AiGenerateQuestionRequest aiGenerateQuestionRequest,Boolean IsVip) {
        ThrowUtils.throwIf(aiGenerateQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        //提取数据并校验
        Long appId = aiGenerateQuestionRequest.getAppId();
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        int questionNum = aiGenerateQuestionRequest.getQuestionNum();
        int opinionNum = aiGenerateQuestionRequest.getOpinionNum();
        //获取用户消息,系统消息就是上面定义好的
        String generateQuestionUserMessage = getGenerateQuestionUserMessage(app, questionNum, opinionNum);
        //建立连接对象，0L表示不设置超时时间
        SseEmitter sseEmitter = new SseEmitter(0L);
        //调用Ai生成题目的Manger，选择合适的方法生成题目
        Flowable<ModelData> modelDataFlowable = aiManger.doStreamRequest(GENERATE_QUESTION_SYSTEM_MESSAGE, generateQuestionUserMessage, null);
        // 创建一个StringBuilder对象
        StringBuilder sb = new StringBuilder();
        // 创建一个AtomicInteger对象，用于记录大括号的数量
        AtomicInteger flag = new AtomicInteger(0);
        //给用户默认为单线程
        Scheduler scheduler = Schedulers.single();
        //判断是否为VIP用户，如果是，分配独立vip线程
        if(IsVip){
           scheduler = vipScheduler;
        }
        // 对modelDataFlowable进行观察，并切换线程
        modelDataFlowable
                .observeOn(scheduler)
                // 将chunk转换为message
                .map(chunk -> chunk.getChoices().get(0).getDelta().getContent())
                // 将message中的空格替换为空字符串
                .map(message -> message.replaceAll("\\s", ""))
                // 过滤掉空字符串
                .filter(StrUtil::isNotBlank)
                // 将message转换为字符流
                .flatMap(messages -> {
                    List<Character> charList = new ArrayList<>();
                    // 遍历message中的每个字符
                    for (char c : messages.toCharArray()) {
                        charList.add(c);

                    }
                    // 返回字符流
                    return Flowable.fromIterable(charList);
                })
                // 对每个字符进行处理
                .doOnNext(c -> {
                    // 如果字符是左大括号，则将flag加1
                    if (c == '{') {
                        flag.addAndGet(1);
                    }
                    // 如果flag大于0，则将字符添加到StringBuilder中
                    if (flag.get() > 0) {
                        sb.append(c);
                    }
                    // 如果字符是右大括号，则将flag减1
                    if (c == '}') {
                        flag.addAndGet(-1);
                        // 如果flag等于0，则将StringBuilder中的内容转换为JSON字符串，并通过sseEmitter发送
                        if (flag.get() == 0) {
                            sseEmitter.send(JSONUtil.toJsonStr(sb.toString()));
                            // 清空StringBuilder
                            sb.setLength(0);
                        }
                    }
                })
                // 如果发生错误，则记录错误日志
                .doOnError((e) ->
                        log.error("sse error", e)
                )
                // 当完成时，完成sseEmitter
                .doOnComplete(sseEmitter::complete)
                // 订阅
                .subscribe();
        // 返回sseEmitter
        return sseEmitter;
    }
}

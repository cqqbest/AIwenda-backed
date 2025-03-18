package com.CQ.AiWenDaPinTai.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.CQ.AiWenDaPinTai.common.ErrorCode;
import com.CQ.AiWenDaPinTai.constant.CommonConstant;
import com.CQ.AiWenDaPinTai.exception.ThrowUtils;
import com.CQ.AiWenDaPinTai.mapper.ScoringResultMapper;
import com.CQ.AiWenDaPinTai.model.dto.scoringResult.ScoreResultQueryRequest;
import com.CQ.AiWenDaPinTai.model.entity.App;
import com.CQ.AiWenDaPinTai.model.entity.ScoringResult;
import com.CQ.AiWenDaPinTai.model.entity.User;
import com.CQ.AiWenDaPinTai.model.vo.ScoringResultVO;
import com.CQ.AiWenDaPinTai.model.vo.UserVO;
import com.CQ.AiWenDaPinTai.service.AppService;
import com.CQ.AiWenDaPinTai.service.ScoreResultService;
import com.CQ.AiWenDaPinTai.service.UserService;
import com.CQ.AiWenDaPinTai.utils.SqlUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评分结果服务实现
 *
 */
@Service
@Slf4j
public class ScoreResultServiceImpl extends ServiceImpl<ScoringResultMapper, ScoringResult> implements ScoreResultService {

    @Resource
    private UserService userService;
    @Resource
    private AppService appService;

    /**
     * 校验数据
     *
     * @param scoreResult
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validScoreResult(ScoringResult scoreResult, boolean add) {
        ThrowUtils.throwIf(scoreResult == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String resultName = scoreResult.getResultName();
        Long appId = scoreResult.getAppId();
        // 创建数据时，参数不能为空
        if (add) {
            // 补充校验规则
            ThrowUtils.throwIf(StringUtils.isBlank(resultName), ErrorCode.PARAMS_ERROR, "结果名称不能为空");
            ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "appId 非法");
        }
        // 修改数据时，有参数则校验
        // 补充校验规则
        if (StringUtils.isNotBlank(resultName)) {
            ThrowUtils.throwIf(resultName.length() > 128, ErrorCode.PARAMS_ERROR, "结果名称不能超过 128");
        }
        // 补充校验规则
        if (appId != null) {
            App app = appService.getById(appId);
            ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR, "应用不存在");
        }
    }

    /**
     * 获取查询条件
     *
     * @param scoreResultQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<ScoringResult> getQueryWrapper(ScoreResultQueryRequest scoreResultQueryRequest) {
        QueryWrapper<ScoringResult> queryWrapper = new QueryWrapper<>();
        if (scoreResultQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = scoreResultQueryRequest.getId();
        String resultName = scoreResultQueryRequest.getResultName();
        String resultDesc = scoreResultQueryRequest.getResultDesc();
        String resultPicture = scoreResultQueryRequest.getResultPicture();
        String resultProp = scoreResultQueryRequest.getResultProp();
        Integer resultScoreRange = scoreResultQueryRequest.getResultScoreRange();
        Long appId = scoreResultQueryRequest.getAppId();
        Long userId = scoreResultQueryRequest.getUserId();
        Long notId = scoreResultQueryRequest.getNotId();
        String searchText = scoreResultQueryRequest.getSearchText();
        String sortField = scoreResultQueryRequest.getSortField();
        String sortOrder = scoreResultQueryRequest.getSortOrder();

        // 补充需要的查询条件
        // 从多字段中搜索
        if (StringUtils.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("resultName", searchText).or().like("resultDesc", searchText));
        }
        // 模糊查询
        queryWrapper.like(StringUtils.isNotBlank(resultName), "resultName", resultName);
        queryWrapper.like(StringUtils.isNotBlank(resultDesc), "resultDesc", resultDesc);
        queryWrapper.like(StringUtils.isNotBlank(resultProp), "resultProp", resultProp);
        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(appId), "appId", appId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(resultScoreRange), "resultScoreRange", resultScoreRange);
        queryWrapper.eq(StringUtils.isNotBlank(resultPicture), "resultPicture", resultPicture);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取评分结果封装
     *
     * @param scoreResult
     * @param request
     * @return
     */
    @Override
    public ScoringResultVO getScoreResultVO(ScoringResult scoreResult, HttpServletRequest request) {
        // 对象转封装类
        ScoringResultVO scoringResultVO = ScoringResultVO.objToVo(scoreResult);

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = scoreResult.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        scoringResultVO.setUser(userVO);
        // endregion

        return scoringResultVO;
    }

    /**
     * 分页获取评分结果封装
     *
     * @param scoreResultPage
     * @param request
     * @return
     */
    @Override
    public Page<ScoringResultVO> getScoreResultVOPage(Page<ScoringResult> scoreResultPage, HttpServletRequest request) {
        List<ScoringResult> scoreResultList = scoreResultPage.getRecords();
        Page<ScoringResultVO> scoreResultVOPage = new Page<>(scoreResultPage.getCurrent(), scoreResultPage.getSize(), scoreResultPage.getTotal());
        if (CollUtil.isEmpty(scoreResultList)) {
            return scoreResultVOPage;
        }
        // 对象列表 => 封装对象列表
        List<ScoringResultVO> scoringResultVOList = scoreResultList.stream().map(scoreResult -> {
            return ScoringResultVO.objToVo(scoreResult);
        }).collect(Collectors.toList());

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = scoreResultList.stream().map(com.CQ.AiWenDaPinTai.model.entity.ScoringResult::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 填充信息
        scoringResultVOList.forEach(scoringResultVO -> {
            Long userId = scoringResultVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            scoringResultVO.setUser(userService.getUserVO(user));
        });
        // endregion

        scoreResultVOPage.setRecords(scoringResultVOList);
        return scoreResultVOPage;
    }

}

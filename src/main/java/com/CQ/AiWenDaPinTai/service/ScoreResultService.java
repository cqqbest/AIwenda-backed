package com.CQ.AiWenDaPinTai.service;

import com.CQ.AiWenDaPinTai.model.dto.scoringResult.ScoreResultQueryRequest;
import com.CQ.AiWenDaPinTai.model.entity.ScoringResult;
import com.CQ.AiWenDaPinTai.model.vo.ScoringResultVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;

/**
 * 评分结果服务
 *
 */
public interface ScoreResultService extends IService<ScoringResult> {

    /**
     * 校验数据
     *
     * @param scoreResult
     * @param add 对创建的数据进行校验
     */
    void validScoreResult(ScoringResult scoreResult, boolean add);

    /**
     * 获取查询条件
     *
     * @param scoreResultQueryRequest
     * @return
     */
    QueryWrapper<ScoringResult> getQueryWrapper(ScoreResultQueryRequest scoreResultQueryRequest);
    
    /**
     * 获取评分结果封装
     *
     * @param scoreResult
     * @param request
     * @return
     */
    ScoringResultVO getScoreResultVO(ScoringResult scoreResult, HttpServletRequest request);

    /**
     * 分页获取评分结果封装
     *
     * @param scoreResultPage
     * @param request
     * @return
     */
    Page<ScoringResultVO> getScoreResultVOPage(Page<ScoringResult> scoreResultPage, HttpServletRequest request);
}

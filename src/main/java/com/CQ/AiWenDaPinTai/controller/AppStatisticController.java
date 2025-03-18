package com.CQ.AiWenDaPinTai.controller;


import com.CQ.AiWenDaPinTai.common.BaseResponse;
import com.CQ.AiWenDaPinTai.common.ErrorCode;
import com.CQ.AiWenDaPinTai.common.ResultUtils;
import com.CQ.AiWenDaPinTai.exception.ThrowUtils;
import com.CQ.AiWenDaPinTai.mapper.UserAnswerMapper;
import com.CQ.AiWenDaPinTai.model.dto.statistic.AppAnswerCountDTO;
import com.CQ.AiWenDaPinTai.model.dto.statistic.AppAnswerResultCountDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/App/statistic")
@Slf4j
public class AppStatisticController {


    @Resource
    private UserAnswerMapper userAnswerMapper;

    @GetMapping("/answer_count")
    public BaseResponse<List<AppAnswerCountDTO>> getAnswerCount(){
        return ResultUtils.success(userAnswerMapper.getAppAnswerCount());
    }



    @GetMapping("/user_result_count")
    public BaseResponse<List<AppAnswerResultCountDTO>> getUserResultCount(Long appId){
        ThrowUtils.throwIf(appId == null, ErrorCode.valueOf("appId不能为空"));
        return ResultUtils.success(userAnswerMapper.getAppAnswerResultCount(appId));
    }

}

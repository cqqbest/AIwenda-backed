package com.CQ.AiWenDaPinTai.model.dto.statistic;


import lombok.Data;

@Data
public class AppAnswerCountDTO {

    private Long appId;

    /**
     * 回答数
     */
    private int  answerCount;

}

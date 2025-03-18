package com.CQ.AiWenDaPinTai.model.dto.statistic;

import lombok.Data;

@Data
public class AppAnswerResultCountDTO {
    /**
     *结果名称
     */
    private String resultName;
    /**
     * 对应个数
     */
    private int  resultCount;
}

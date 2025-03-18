package com.CQ.AiWenDaPinTai.model.dto.question;


import lombok.Data;

import java.io.Serializable;

@Data
public class AiGenerateQuestionRequest implements Serializable {
    /**
     * 应用 id
     */
    private Long AppId;

    /**
     * 题目数量
     */
    private int questionNum = 10;

    /**
     * 选项数量
     */
    private int opinionNum = 2;

    private static final long serialVersionUID = 1L;
}

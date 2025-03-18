package com.CQ.AiWenDaPinTai.scoring;

import com.CQ.AiWenDaPinTai.common.ErrorCode;
import com.CQ.AiWenDaPinTai.exception.BusinessException;
import com.CQ.AiWenDaPinTai.model.entity.App;
import com.CQ.AiWenDaPinTai.model.entity.UserAnswer;
import com.CQ.AiWenDaPinTai.model.enums.AppScoringStrategyEnum;
import com.CQ.AiWenDaPinTai.model.enums.AppTypeEnum;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.CQ.AiWenDaPinTai.model.enums.AppScoringStrategyEnum.AI;
import static com.CQ.AiWenDaPinTai.model.enums.AppScoringStrategyEnum.CUSTOM;

@Service
@Deprecated
public class ScoringStrategyContext {
    @Resource
    private CustomTestScoringStrategy customTestScoringStrategy;
    @Resource
    private CustomScoreScoringStrategy customScoreScoringStrategy;
    /**
     * 评分
     *
     * @param choiceList
     * @param app
     * @return
     * @throws Exception
     */
    public UserAnswer doScore(List<String> choiceList, App app) throws Exception {
        AppTypeEnum appTypeEnum = AppTypeEnum.getEnumByValue(app.getAppType());
        AppScoringStrategyEnum appScoringStrategyEnum = AppScoringStrategyEnum.getEnumByValue(app.getScoringStrategy());
        if (appTypeEnum == null || appScoringStrategyEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
        }
        // 根据不同的应用类别和评分策略，选择对应的策略执行
        switch (appTypeEnum) {
            case SCORE:
                switch (appScoringStrategyEnum) {
                    case CUSTOM:
                        return customScoreScoringStrategy.doScore(choiceList, app);
                    case AI:
                        break;
                }
                break;
            case TEST:
                switch (appScoringStrategyEnum) {
                    case CUSTOM:
                        return customTestScoringStrategy.doScore(choiceList, app);
                    case AI:
                        break;
                }
                break;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
    }
}

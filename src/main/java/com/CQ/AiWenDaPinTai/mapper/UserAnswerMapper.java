package com.CQ.AiWenDaPinTai.mapper;

import com.CQ.AiWenDaPinTai.model.dto.statistic.AppAnswerCountDTO;
import com.CQ.AiWenDaPinTai.model.dto.statistic.AppAnswerResultCountDTO;
import com.CQ.AiWenDaPinTai.model.entity.UserAnswer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author 86198
* @description 针对表【user_answer(用户答题记录)】的数据库操作Mapper
* @createDate 2024-11-25 18:55:07
* @Entity generator.domain.UserAnswer
*/
public interface UserAnswerMapper extends BaseMapper<UserAnswer> {

    @Select("select appId,count(userid) as answercount from user_answer group by appId order by answercount desc limit 1;")
    List<AppAnswerCountDTO> getAppAnswerCount();



    @Select("select resultName,count(resultName) as resultNameCount from user_answer where appId = #{appId} group by resultName order by resultNameCount desc;")
    List<AppAnswerResultCountDTO> getAppAnswerResultCount(Long appId);

}





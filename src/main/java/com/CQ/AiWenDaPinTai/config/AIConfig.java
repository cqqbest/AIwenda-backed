package com.CQ.AiWenDaPinTai.config;


import com.zhipu.oapi.ClientV4;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AIConfig {

    @Value("${ai:ApiKeys}")
    private String ApiKeys;

    @Bean
    public ClientV4 getClientV4() {
        return new ClientV4.Builder(ApiKeys).build();
    }

}

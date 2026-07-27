package com.example.knowyourcolleagues.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transactionMonitoringOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("交易监控与告警系统 API")
                        .version("v1")
                        .description("用于交易查询、规则监控和告警处置的 REST API。"
                                + "前后端开发人员可以通过 Swagger UI 查看接口契约并在线调试。"));
    }
}

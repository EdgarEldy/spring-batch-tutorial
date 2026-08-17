package com.edgareldy.springbatchtutorial.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the springdoc-openapi metadata (title, description, version) shown in the
 * generated Swagger UI for this API.
 * <p>
 * Created by Edgar Muhamyangabo on 8/15/26
 * Author : Edgar Muhamyangabo
 * Date : 8/15/26
 * Project : spring-batch-tutorial
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI springBatchTutorialOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Batch Tutorial API")
                        .description("Monthly payroll processing: timesheet import, aggregation, "
                                + "payslip calculation, and export, orchestrated by Spring Batch.")
                        .version("0.1.0"));
    }
}

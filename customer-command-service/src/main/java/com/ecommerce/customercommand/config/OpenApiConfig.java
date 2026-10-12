package com.ecommerce.customercommand.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI customerCommandOpenAPI() {
        return new OpenAPI().info(new Info().title("Customer Command Service API")
                .version("1.0.0").description("Write-side customer management APIs. Authentication is handled by auth-service."));
    }
}

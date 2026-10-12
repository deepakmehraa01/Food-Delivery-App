package com.ecommerce.customerquery.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customerQueryOpenAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(new Info()
                        .title("Customer Query Service API")
                        .version("v1")
                        .description("Read-only customer APIs for the query side of CQRS.")
                        .license(new License().name("Internal E-Commerce Platform")));
    }
}

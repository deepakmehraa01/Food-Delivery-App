package com.ecommerce.order.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration public class OpenApiConfig { @Bean OpenAPI orderOpenAPI(){return new OpenAPI().info(new Info().title("Order Service API").version("v1").description("Order placement with Product Service inventory reservation"));} }

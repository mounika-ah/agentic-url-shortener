package com.shortforge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI urlShortenerOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title(
                                        "Agentic URL Shortener API"
                                )
                                .description(
                                        """
                                        Production-oriented URL shortener
                                        with Redis caching, Kafka events,
                                        idempotent creation and analytics.
                                        """
                                )
                                .version("1.0.0")
                                .contact(
                                        new Contact()
                                                .name(
                                                        "ShortForge Engineering"
                                                )
                                )
                );
    }
}
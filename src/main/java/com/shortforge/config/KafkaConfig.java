package com.shortforge.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    public static final String URL_EVENTS_TOPIC = "url-events";

    @Bean
    public NewTopic urlEventsTopic() {
        return new NewTopic(URL_EVENTS_TOPIC, 3, (short) 1);
    }
}
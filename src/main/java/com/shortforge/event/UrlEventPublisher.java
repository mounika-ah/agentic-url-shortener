package com.shortforge.event;

import com.shortforge.config.KafkaConfig;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UrlEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UrlEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCreated(
            String shortCode,
            String originalUrl
    ) {

        UrlEvent event = new UrlEvent(
                EventType.URL_CREATED,
                shortCode,
                originalUrl,
                Instant.now()
        );

        kafkaTemplate.send(
                KafkaConfig.URL_EVENTS_TOPIC,
                shortCode,
                event
        );
    }

    public void publishVisited(
            String shortCode,
            String originalUrl
    ) {

        UrlEvent event = new UrlEvent(
                EventType.URL_VISITED,
                shortCode,
                originalUrl,
                Instant.now()
        );

        kafkaTemplate.send(
                KafkaConfig.URL_EVENTS_TOPIC,
                shortCode,
                event
        );
    }

}

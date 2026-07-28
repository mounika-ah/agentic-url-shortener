package com.shortforge.integration.kafka;

import com.shortforge.config.KafkaConfig;
import com.shortforge.event.EventType;
import com.shortforge.event.UrlEvent;
import com.shortforge.event.UrlEventPublisher;
import com.shortforge.integration.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UrlEventPublisherIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UrlEventPublisher urlEventPublisher;

    @Autowired
    private KafkaConnectionDetails kafkaConnectionDetails;

    @Test
    void shouldPublishCreatedEvent() {
        try (Consumer<String, UrlEvent> consumer = createConsumer()) {
            consumer.subscribe(java.util.List.of(KafkaConfig.URL_EVENTS_TOPIC));

            String shortCode = "abc123";
            String originalUrl = "https://example.com/article";

            urlEventPublisher.publishCreated(shortCode, originalUrl);

            ConsumerRecord<String, UrlEvent> record =
                    awaitRecord(consumer);

            assertThat(record.topic())gi
                    .isEqualTo(KafkaConfig.URL_EVENTS_TOPIC);

            assertThat(record.key())
                    .isEqualTo(shortCode);

            assertThat(record.value())
                    .isNotNull();

            assertThat(record.value().type())
                    .isEqualTo(EventType.URL_CREATED);

            assertThat(record.value().shortCode())
                    .isEqualTo(shortCode);

            assertThat(record.value().originalUrl())
                    .isEqualTo(originalUrl);

            assertThat(record.value().timestamp())
                    .isNotNull();
        }
    }

    @Test
    void shouldPublishVisitedEvent() {
        try (Consumer<String, UrlEvent> consumer = createConsumer()) {
            consumer.subscribe(java.util.List.of(KafkaConfig.URL_EVENTS_TOPIC));

            String shortCode = "visit01";
            String originalUrl = "https://example.com/visited";

            urlEventPublisher.publishVisited(shortCode, originalUrl);

            ConsumerRecord<String, UrlEvent> record =
                    awaitRecord(consumer);

            assertThat(record.key())
                    .isEqualTo(shortCode);

            assertThat(record.value())
                    .isNotNull();

            assertThat(record.value().type())
                    .isEqualTo(EventType.URL_VISITED);

            assertThat(record.value().shortCode())
                    .isEqualTo(shortCode);

            assertThat(record.value().originalUrl())
                    .isEqualTo(originalUrl);

            assertThat(record.value().timestamp())
                    .isNotNull();
        }
    }

    private Consumer<String, UrlEvent> createConsumer() {
        Map<String, Object> properties = new HashMap<>();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaConnectionDetails.getBootstrapServers()
        );

        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "url-event-test-" + UUID.randomUUID()
        );

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

        JacksonJsonDeserializer<UrlEvent> valueDeserializer =
                new JacksonJsonDeserializer<>(UrlEvent.class);

        valueDeserializer.addTrustedPackages(
                "com.shortforge.event"
        );

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                valueDeserializer
        ).createConsumer();
    }

    private ConsumerRecord<String, UrlEvent> awaitRecord(
            Consumer<String, UrlEvent> consumer
    ) {
        long deadline =
                System.nanoTime() + Duration.ofSeconds(10).toNanos();

        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(500));

            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }

        throw new AssertionError(
                "No Kafka record received from topic: "
                        + KafkaConfig.URL_EVENTS_TOPIC
        );
    }
}
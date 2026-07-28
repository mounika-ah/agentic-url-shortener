package com.shortforge.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.flyway.enabled=true",

                // Prevent Kafka consumers from starting during these tests.
                "spring.kafka.listener.auto-startup=false"
        }
)
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void cleanIntegrationTestState() {
        clearRedis();
        clearDatabase();
    }

    private void clearRedis() {
        try (RedisConnection connection =
                     redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    private void clearDatabase() {
        /*
         * Change "short_urls" if your Flyway migration uses a different
         * table name.
         *
         * RESTART IDENTITY resets generated numeric IDs.
         */
        jdbcTemplate.execute(
                "TRUNCATE TABLE short_urls RESTART IDENTITY CASCADE"
        );
    }
}
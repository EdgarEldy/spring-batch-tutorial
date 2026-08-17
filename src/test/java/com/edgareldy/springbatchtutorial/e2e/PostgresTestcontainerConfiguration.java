package com.edgareldy.springbatchtutorial.e2e;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Supplies a real, ephemeral PostgreSQL 16 instance via Testcontainers for e2e tests
 * that need the full Spring context (JPA, Flyway, Spring Batch metadata schema), wired
 * through {@code @ServiceConnection} so Spring Boot resolves spring.datasource.* from
 * the running container instead of a hardcoded JDBC URL.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainerConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
    }
}

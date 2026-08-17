package com.edgareldy.springbatchtutorial.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * End-to-end check that the application boots a full Spring context (JPA, Flyway,
 * Spring Batch metadata schema included) against a real PostgreSQL instance and
 * exposes a working GET /actuator/health endpoint, per the feature/core-architecture
 * checklist.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@ActiveProfiles("test")
@Import(PostgresTestcontainerConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ActuatorHealthE2ETest {

    @LocalServerPort
    private int port;

    @Test
    void healthEndpointReturnsOk() {
        RestTestClient client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}

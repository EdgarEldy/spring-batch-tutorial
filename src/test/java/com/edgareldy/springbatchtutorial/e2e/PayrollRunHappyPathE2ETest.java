package com.edgareldy.springbatchtutorial.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.dto.batch.CreatePayrollRunRequest;
import com.edgareldy.springbatchtutorial.dto.batch.PayrollRunResponse;
import com.edgareldy.springbatchtutorial.dto.common.ApiResponse;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Drives the complete happy path purely through the public REST API - the
 * scope the README's feature/export-and-scheduling checklist actually asks
 * for - rather than {@code JobLauncherTestUtils}: {@code POST
 * /api/v1/payroll/runs} creates a {@code PayrollRun} for the real sample
 * CSV's period and launches {@code monthlyPayrollJob}. That real CSV's David
 * Chen genuinely logs 312h, above the default 300h/month anomaly threshold
 * (see {@code MonthlyPayrollJobE2ETest}), so the run first lands in
 * {@code AWAITING_REVIEW} rather than {@code COMPLETED} straight away; this
 * test then calls {@code POST /api/v1/payroll/runs/{id}/resume} - exactly
 * what a human reviewer would call - to launch {@code payrollFinalizeJob} and
 * reach {@code COMPLETED}, the same two-phase path
 * {@link MonthlyPayrollJobAnomalyResumeE2ETest} already covers at the
 * service layer, verified here end to end through the HTTP layer instead,
 * finishing with a real {@code payroll-summary-2026-08.csv} read back off
 * disk.
 * <p>
 * No polling is needed between any of these calls: neither
 * {@code BatchConfig} nor any other bean in this project configures a
 * {@code TaskExecutor} for Spring Batch's {@code JobOperator}, so it defaults
 * to a synchronous {@code SyncTaskExecutor} - by the time each REST call
 * returns, the {@code Job} it launched has already finished running in the
 * same request thread.
 * <p>
 * {@code payroll.export.output-dir} is overridden via
 * {@code @TestPropertySource} to a dedicated {@code target/} directory so
 * this class never reads/writes the application's default {@code exports/}
 * directory; the generated file is deleted in {@code @AfterEach} regardless
 * of test outcome. Uses {@code RestTestClient} against a real embedded server
 * ({@code TestRestTemplate} was removed in Spring Boot 4), the same pattern
 * {@link ActuatorHealthE2ETest} already establishes in this project.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@ActiveProfiles("test")
@Import(PostgresTestcontainerConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "payroll.export.output-dir=target/test-output/payroll-run-happy-path-e2e")
class PayrollRunHappyPathE2ETest {

    private static final int PERIOD_MONTH = 8;
    private static final int PERIOD_YEAR = 2026;
    private static final String PERIOD = "2026-08";
    private static final Path OUTPUT_DIR = Path.of("target", "test-output", "payroll-run-happy-path-e2e");

    private static final List<String> VALID_EMPLOYEE_EMAILS = List.of(
            "alice.martin@example.com",
            "bob.dupont@example.com",
            "carla.silva@example.com",
            "emma.rossi@example.com",
            "david.chen@example.com");

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void deleteGeneratedFile() throws IOException {
        Files.deleteIfExists(OUTPUT_DIR.resolve("payroll-summary-" + PERIOD + ".csv"));
    }

    @Test
    void postCreateRunThenResumeReachesCompletedWithAGeneratedSummaryFile() throws IOException {
        ApiResponse<PayrollRunResponse> createResponse = client.post()
                .uri("/api/v1/payroll/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreatePayrollRunRequest(PERIOD_MONTH, PERIOD_YEAR))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<ApiResponse<PayrollRunResponse>>() { })
                .returnResult()
                .getResponseBody();

        assertThat(createResponse).isNotNull();
        assertThat(createResponse.success()).isTrue();
        Long payrollRunId = createResponse.data().id();
        assertThat(payrollRunId).isNotNull();

        // monthlyPayrollJob already ran synchronously by the time the POST above
        // returned and stopped at flagForReview, since David Chen's real hours
        // exceed the anomaly threshold - not COMPLETED yet.
        PayrollRunResponse afterImport = getRun(payrollRunId).data();
        assertThat(afterImport.status()).isEqualTo(PayrollRunStatus.AWAITING_REVIEW);
        assertThat(afterImport.completedAt()).isNull();

        ApiResponse<PayrollRunResponse> resumeResponse = client.post()
                .uri("/api/v1/payroll/runs/{id}/resume", payrollRunId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<ApiResponse<PayrollRunResponse>>() { })
                .returnResult()
                .getResponseBody();

        assertThat(resumeResponse).isNotNull();
        assertThat(resumeResponse.success()).isTrue();

        // payrollFinalizeJob (calculatePayslips -> exportPayrollSummary) also ran
        // synchronously by the time the resume call above returned.
        PayrollRunResponse completed = getRun(payrollRunId).data();
        assertThat(completed.status()).isEqualTo(PayrollRunStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();

        Path summaryFile = OUTPUT_DIR.resolve("payroll-summary-" + PERIOD + ".csv");
        assertThat(summaryFile).exists();

        List<String> lines = Files.readAllLines(summaryFile);
        assertThat(lines.get(0)).isEqualTo(
                "employee_id,first_name,last_name,email,department,total_hours,gross_pay,deductions,net_pay");
        assertThat(lines).hasSize(1 + VALID_EMPLOYEE_EMAILS.size());
        for (String email : VALID_EMPLOYEE_EMAILS) {
            assertThat(lines).as("a row for %s", email).anyMatch(line -> line.contains(email));
        }
    }

    private ApiResponse<PayrollRunResponse> getRun(Long id) {
        return client.get()
                .uri("/api/v1/payroll/runs/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<ApiResponse<PayrollRunResponse>>() { })
                .returnResult()
                .getResponseBody();
    }
}

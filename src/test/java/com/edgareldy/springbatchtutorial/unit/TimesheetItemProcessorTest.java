package com.edgareldy.springbatchtutorial.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.edgareldy.springbatchtutorial.batch.importstep.TimesheetItemProcessor;
import com.edgareldy.springbatchtutorial.dto.csv.TimesheetCsvRow;
import com.edgareldy.springbatchtutorial.entity.Employee;
import com.edgareldy.springbatchtutorial.exception.InvalidTimesheetRowException;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Exercises {@link TimesheetItemProcessor#process(TimesheetCsvRow)} as pure
 * validation logic, with {@link EmployeeRepository} mocked: no
 * {@code JobLauncher}, no Spring context, no database. Covers the
 * {@code hours_worked} boundary ({@code (0, 24]} inclusive on the upper
 * bound, exclusive on the lower one), an invalid {@code work_date}, an email
 * that does not resolve to a known {@link Employee}, and the nominal
 * successful transformation.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@ExtendWith(MockitoExtension.class)
class TimesheetItemProcessorTest {

    private static final String SAMPLE_CSV_PATH = "sample-data/timesheets-import-sample.csv";
    private static final String VALID_WORK_DATE = "2026-08-03";
    private static final String KNOWN_EMAIL = "alice.martin@example.com";

    @Mock
    private EmployeeRepository employeeRepository;

    private TimesheetItemProcessor processor;

    @BeforeEach
    void setUp() {
        Resource csvResource = new ClassPathResource(SAMPLE_CSV_PATH);
        processor = new TimesheetItemProcessor(employeeRepository, csvResource);
    }

    @Test
    void hoursWorkedExactlyZero_isRejected() {
        TimesheetCsvRow row = new TimesheetCsvRow(KNOWN_EMAIL, VALID_WORK_DATE, "0.00");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(InvalidTimesheetRowException.class)
                .hasMessageContaining("hours_worked must be > 0 and <= 24")
                .hasMessageContaining("0.00");
        // hours_worked is validated before the employee is ever looked up.
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void hoursWorkedNegative_isRejected() {
        TimesheetCsvRow row = new TimesheetCsvRow(KNOWN_EMAIL, VALID_WORK_DATE, "-1.00");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(InvalidTimesheetRowException.class)
                .hasMessageContaining("hours_worked must be > 0 and <= 24");
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void hoursWorkedExactly24_isAccepted() {
        Employee employee = knownEmployee();
        when(employeeRepository.findByEmail(KNOWN_EMAIL)).thenReturn(Optional.of(employee));
        TimesheetCsvRow row = new TimesheetCsvRow(KNOWN_EMAIL, VALID_WORK_DATE, "24.00");

        var entry = processor.process(row);

        assertThat(entry).isNotNull();
        assertThat(entry.getHoursWorked()).isEqualByComparingTo("24.00");
    }

    @Test
    void hoursWorkedAbove24_isRejected() {
        TimesheetCsvRow row = new TimesheetCsvRow(KNOWN_EMAIL, VALID_WORK_DATE, "24.01");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(InvalidTimesheetRowException.class)
                .hasMessageContaining("hours_worked must be > 0 and <= 24")
                .hasMessageContaining("24.01");
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void hoursWorkedWellAbove24_isRejected() {
        TimesheetCsvRow row = new TimesheetCsvRow(KNOWN_EMAIL, VALID_WORK_DATE, "25.00");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(InvalidTimesheetRowException.class)
                .hasMessageContaining("hours_worked must be > 0 and <= 24")
                .hasMessageContaining("25.00");
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void hoursWorkedNotANumber_isRejected() {
        TimesheetCsvRow row = new TimesheetCsvRow(KNOWN_EMAIL, VALID_WORK_DATE, "not-a-number");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(InvalidTimesheetRowException.class)
                .hasMessageContaining("hours_worked is not a valid number");
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void invalidWorkDate_isRejected() {
        TimesheetCsvRow row = new TimesheetCsvRow(KNOWN_EMAIL, "not-a-date", "8.00");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(InvalidTimesheetRowException.class)
                .hasMessageContaining("work_date is not a valid ISO date")
                .hasMessageContaining("not-a-date");
        // work_date is validated before the employee is looked up too.
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void unknownEmployeeEmail_isRejected() {
        String unknownEmail = "unknown.employee@example.com";
        when(employeeRepository.findByEmail(unknownEmail)).thenReturn(Optional.empty());
        TimesheetCsvRow row = new TimesheetCsvRow(unknownEmail, VALID_WORK_DATE, "8.00");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(InvalidTimesheetRowException.class)
                .hasMessageContaining("Unknown employee email")
                .hasMessageContaining(unknownEmail);
    }

    @Test
    void nominalRow_isTransformedIntoAPersistableEntry() {
        Employee employee = knownEmployee();
        when(employeeRepository.findByEmail(KNOWN_EMAIL)).thenReturn(Optional.of(employee));
        TimesheetCsvRow row = new TimesheetCsvRow(KNOWN_EMAIL, VALID_WORK_DATE, "8.00");

        var entry = processor.process(row);

        assertThat(entry).isNotNull();
        assertThat(entry.getEmployee()).isEqualTo(employee);
        assertThat(entry.getWorkDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(entry.getHoursWorked()).isEqualByComparingTo("8.00");
        assertThat(entry.getSourceFile()).isEqualTo("timesheets-import-sample.csv");
        assertThat(entry.getImportedAt()).isNotNull();
    }

    private Employee knownEmployee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setFirstName("Alice");
        employee.setLastName("Martin");
        employee.setEmail(KNOWN_EMAIL);
        employee.setDepartment("Engineering");
        employee.setHourlyRate(new BigDecimal("45.00"));
        employee.setBankAccount("FR7630006000011234567890189");
        return employee;
    }
}

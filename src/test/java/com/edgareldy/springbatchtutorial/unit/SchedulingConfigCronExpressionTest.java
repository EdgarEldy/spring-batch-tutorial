package com.edgareldy.springbatchtutorial.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.config.SchedulingConfig;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

/**
 * Verifies the monthly cron trigger fires at the expected instants by parsing
 * it with {@link CronExpression} and calling {@code next(LocalDateTime)} from
 * a handful of reference dates - no {@code JobLauncher}, no Spring context,
 * and definitely no waiting a real month for {@code SchedulingConfig} to
 * actually fire.
 * <p>
 * The cron literal under test is read via reflection off the
 * {@link Scheduled} annotation on
 * {@link SchedulingConfig#launchScheduledMonthlyPayrollRun()} rather than
 * duplicated as a string constant here: that annotation's placeholder default
 * ({@code ${payroll.scheduling.cron:0 0 3 1 * *}}) is the actual source of
 * truth also mirrored by {@code application.yml}'s {@code payroll.scheduling.cron}
 * property, so this test can't silently drift from what
 * {@code SchedulingConfig} really runs against.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
class SchedulingConfigCronExpressionTest {

    // Matches "${payroll.scheduling.cron:0 0 3 1 * *}" and captures the part
    // after the ':', i.e. the default cron literal Spring resolves to when
    // no payroll.scheduling.cron property is set.
    private static final Pattern PLACEHOLDER_DEFAULT_PATTERN = Pattern.compile("\\$\\{[^:}]+:([^}]+)}");

    private final CronExpression cronExpression = CronExpression.parse(defaultCronLiteral());

    @Test
    void fromMidJanuary_nextFireIsFirstOfFebruaryAtThreeAm() {
        LocalDateTime reference = LocalDateTime.of(2026, 1, 15, 10, 0, 0);

        LocalDateTime next = cronExpression.next(reference);

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 2, 1, 3, 0, 0));
    }

    @Test
    void fromExactlyAFireInstant_nextFireIsOneCalendarMonthLater() {
        LocalDateTime reference = LocalDateTime.of(2026, 2, 1, 3, 0, 0);

        LocalDateTime next = cronExpression.next(reference);

        // CronExpression.next(...) always returns a time strictly after the
        // reference, even when the reference itself already matches the
        // expression - so firing exactly at 03:00 on the 1st still resolves
        // to next month's occurrence, not the same instant again.
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 3, 1, 3, 0, 0));
    }

    @Test
    void fromLastInstantOfJanuary_nextFireIsStillFirstOfFebruaryAtThreeAm() {
        LocalDateTime reference = LocalDateTime.of(2026, 1, 31, 23, 59, 59);

        LocalDateTime next = cronExpression.next(reference);

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 2, 1, 3, 0, 0));
    }

    @Test
    void fromDecember_nextFireRollsOverIntoNextYear() {
        LocalDateTime reference = LocalDateTime.of(2026, 12, 10, 0, 0, 0);

        LocalDateTime next = cronExpression.next(reference);

        assertThat(next).isEqualTo(LocalDateTime.of(2027, 1, 1, 3, 0, 0));
    }

    private static String defaultCronLiteral() {
        try {
            Method scheduledMethod = SchedulingConfig.class.getDeclaredMethod("launchScheduledMonthlyPayrollRun");
            String rawCronAttribute = scheduledMethod.getAnnotation(Scheduled.class).cron();

            Matcher matcher = PLACEHOLDER_DEFAULT_PATTERN.matcher(rawCronAttribute);
            if (!matcher.matches()) {
                throw new IllegalStateException(
                        "Expected a '${property:default}' placeholder on @Scheduled(cron=...), got: " + rawCronAttribute);
            }
            return matcher.group(1);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException("SchedulingConfig#launchScheduledMonthlyPayrollRun not found", ex);
        }
    }
}

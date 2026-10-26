package com.uptimewatch.monitor;

import com.uptimewatch.monitor.Monitor.Transition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final int THRESHOLD = 2;

    private final Monitor monitor = new Monitor("Site", "https://site.example", "Other", 60);

    @Test
    void firstSuccessfulCheckIsNotARecovery() {
        assertThat(apply(T0, true)).isEqualTo(Transition.NONE);
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.UP);
        assertThat(monitor.getStatusSince()).isEqualTo(T0);
    }

    @Test
    void singleFailureIsIgnoredAsABlip() {
        apply(T0, true);

        assertThat(apply(T0.plusSeconds(60), false)).isEqualTo(Transition.NONE);
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.UP);
        assertThat(monitor.getConsecutiveFailures()).isEqualTo(1);

        assertThat(apply(T0.plusSeconds(120), true)).isEqualTo(Transition.NONE);
        assertThat(monitor.getConsecutiveFailures()).isZero();
    }

    @Test
    void secondFailureInARowGoesDownDatedFromTheFirstFailure() {
        apply(T0, true);
        apply(T0.plusSeconds(60), false);

        assertThat(apply(T0.plusSeconds(120), false)).isEqualTo(Transition.WENT_DOWN);
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.DOWN);
        assertThat(monitor.getStatusSince()).isEqualTo(T0.plusSeconds(60));

        assertThat(apply(T0.plusSeconds(180), false)).isEqualTo(Transition.NONE);
        assertThat(apply(T0.plusSeconds(240), true)).isEqualTo(Transition.RECOVERED);
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.UP);
    }

    @Test
    void siteThatIsDownFromTheStartIsReported() {
        assertThat(apply(T0, false)).isEqualTo(Transition.NONE);
        assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.UNKNOWN);
        assertThat(apply(T0.plusSeconds(60), false)).isEqualTo(Transition.WENT_DOWN);
    }

    @Test
    void isDueOnceIntervalHasPassed() {
        assertThat(monitor.isDue(T0)).isTrue();

        apply(T0, true);

        assertThat(monitor.isDue(T0.plusSeconds(59))).isFalse();
        assertThat(monitor.isDue(T0.plusSeconds(60))).isTrue();
    }

    @Test
    void warnsOncePerCertificateInsideTheWindow() {
        Instant farExpiry = T0.plus(Duration.ofDays(60));
        monitor.applyResult(new CheckResult(monitor, T0, true, 200, 100L, null, farExpiry), THRESHOLD);
        assertThat(monitor.needsSslWarning(T0, 14)).isFalse();

        Instant soonExpiry = T0.plus(Duration.ofDays(10));
        monitor.applyResult(new CheckResult(monitor, T0, true, 200, 100L, null, soonExpiry), THRESHOLD);
        assertThat(monitor.needsSslWarning(T0, 14)).isTrue();
        assertThat(monitor.needsSslWarning(T0.plusSeconds(60), 14)).isFalse();

        // A renewed certificate that later nears expiry warns again.
        Instant renewedSoon = T0.plus(Duration.ofDays(5));
        monitor.applyResult(new CheckResult(monitor, T0, true, 200, 100L, null, renewedSoon), THRESHOLD);
        assertThat(monitor.needsSslWarning(T0, 14)).isTrue();
    }

    private Transition apply(Instant at, boolean up) {
        CheckResult result = new CheckResult(monitor, at, up, up ? 200 : 500, 100L, up ? null : "HTTP 500", null);
        return monitor.applyResult(result, THRESHOLD);
    }
}

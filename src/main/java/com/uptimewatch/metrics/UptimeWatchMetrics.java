package com.uptimewatch.metrics;

import com.uptimewatch.monitor.IncidentRepository;
import com.uptimewatch.monitor.Monitor;
import com.uptimewatch.monitor.MonitorRepository;
import com.uptimewatch.monitor.MonitorStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes UptimeWatch's own data as Prometheus metrics:
 * <ul>
 *   <li>{@code uptimewatch_check_duration_seconds} - histogram of every check, by monitor and result</li>
 *   <li>{@code uptimewatch_monitor_up} - 1 when a monitor is UP, 0 when DOWN</li>
 *   <li>{@code uptimewatch_monitor_ssl_days_left} - days until each certificate expires</li>
 *   <li>{@code uptimewatch_incidents_open} - outages currently in progress</li>
 *   <li>{@code uptimewatch_alerts_sent_total} - alerts by type, channel and outcome</li>
 * </ul>
 */
@Component
public class UptimeWatchMetrics {

    /** Few, fixed buckets keep the series count small: 33 monitors x 2 results x 8 buckets. */
    private static final Duration[] SLO_BUCKETS = {
            Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1),
            Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10)
    };

    private final MeterRegistry registry;
    private final MonitorRepository monitors;
    private final IncidentRepository incidents;
    private final MultiGauge upGauge;
    private final MultiGauge sslGauge;
    private final AtomicLong openIncidents = new AtomicLong();

    public UptimeWatchMetrics(MeterRegistry registry, MonitorRepository monitors, IncidentRepository incidents) {
        this.registry = registry;
        this.monitors = monitors;
        this.incidents = incidents;
        this.upGauge = MultiGauge.builder("uptimewatch.monitor.up")
                .description("1 when the monitor is confirmed UP, 0 when DOWN")
                .register(registry);
        this.sslGauge = MultiGauge.builder("uptimewatch.monitor.ssl.days.left")
                .description("Days until the monitor's TLS certificate expires")
                .register(registry);
        registry.gauge("uptimewatch.incidents.open", openIncidents);
    }

    public void recordCheck(String monitorName, String category, boolean up, long responseTimeMs) {
        Timer.builder("uptimewatch.check.duration")
                .description("Time taken by each HTTP check")
                .tags("monitor", monitorName, "category", category, "result", up ? "up" : "down")
                .serviceLevelObjectives(SLO_BUCKETS)
                .register(registry)
                .record(Duration.ofMillis(responseTimeMs));
    }

    public void recordAlert(String type, String channel, boolean delivered) {
        Counter.builder("uptimewatch.alerts.sent")
                .description("Alerts handed to each channel")
                .tags("type", type, "channel", channel, "outcome", delivered ? "success" : "failure")
                .register(registry)
                .increment();
    }

    /** Refreshes the per-monitor gauges; removed monitors disappear thanks to overwrite=true. */
    @Scheduled(fixedDelay = 15000, initialDelay = 5000)
    public void refreshGauges() {
        List<Monitor> all = monitors.findAll();
        Instant now = Instant.now();
        upGauge.register(all.stream()
                .filter(m -> m.getStatus() != MonitorStatus.UNKNOWN)
                .<MultiGauge.Row<?>>map(m -> MultiGauge.Row.of(tags(m), m.getStatus() == MonitorStatus.UP ? 1 : 0))
                .toList(), true);
        sslGauge.register(all.stream()
                .filter(m -> m.getSslExpiresAt() != null)
                .<MultiGauge.Row<?>>map(m -> MultiGauge.Row.of(tags(m), Duration.between(now, m.getSslExpiresAt()).toDays()))
                .toList(), true);
        openIncidents.set(incidents.countOpen());
    }

    private static Tags tags(Monitor m) {
        return Tags.of("monitor", m.getName(), "category", m.getCategory());
    }
}

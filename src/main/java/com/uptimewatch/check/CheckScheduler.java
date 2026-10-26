package com.uptimewatch.check;

import com.uptimewatch.alert.AlertService;
import com.uptimewatch.config.UptimeWatchProperties;
import com.uptimewatch.monitor.Monitor;
import com.uptimewatch.monitor.MonitorRepository;
import com.uptimewatch.monitor.MonitorService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wakes up every few seconds, finds monitors whose interval has elapsed and checks
 * them in parallel, so one slow site never delays the others.
 */
@Component
public class CheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(CheckScheduler.class);

    private final MonitorRepository monitors;
    private final MonitorService monitorService;
    private final HttpChecker checker;
    private final AlertService alerts;
    private final ExecutorService pool;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public CheckScheduler(MonitorRepository monitors, MonitorService monitorService,
                          HttpChecker checker, AlertService alerts, UptimeWatchProperties properties) {
        this.monitors = monitors;
        this.monitorService = monitorService;
        this.checker = checker;
        this.alerts = alerts;
        this.pool = Executors.newFixedThreadPool(properties.checkerThreads());
    }

    @Scheduled(fixedDelayString = "${uptimewatch.scheduler-tick-ms}",
            initialDelayString = "${uptimewatch.scheduler-initial-delay-ms:2000}")
    public void runDueChecks() {
        Instant now = Instant.now();
        for (Monitor monitor : monitors.findByEnabledTrue()) {
            // Skip monitors whose previous check is still running (e.g. a slow timeout).
            if (monitor.isDue(now) && inFlight.add(monitor.getId())) {
                Long id = monitor.getId();
                String url = monitor.getUrl();
                pool.submit(() -> {
                    try {
                        // recordResult has committed by the time it returns, so alerts never
                        // describe data that was rolled back.
                        alerts.dispatch(monitorService.recordResult(id, checker.check(url)));
                    } catch (Exception e) {
                        log.error("Check failed for monitor {} ({})", id, url, e);
                    } finally {
                        inFlight.remove(id);
                    }
                });
            }
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeOldChecks() {
        int deleted = monitorService.purgeOldChecks();
        log.info("Retention cleanup removed {} old check results", deleted);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}

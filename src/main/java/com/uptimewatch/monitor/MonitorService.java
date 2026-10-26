package com.uptimewatch.monitor;

import com.uptimewatch.alert.AlertMessage;
import com.uptimewatch.check.ProbeOutcome;
import com.uptimewatch.config.UptimeWatchProperties;
import com.uptimewatch.monitor.dto.CheckView;
import com.uptimewatch.monitor.dto.CreateMonitorRequest;
import com.uptimewatch.monitor.dto.IncidentView;
import com.uptimewatch.monitor.dto.MonitorSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);
    static final int RECENT_CHECKS = 30;

    private final MonitorRepository monitors;
    private final CheckResultRepository checks;
    private final IncidentRepository incidents;
    private final UptimeWatchProperties properties;

    public MonitorService(MonitorRepository monitors, CheckResultRepository checks,
                          IncidentRepository incidents, UptimeWatchProperties properties) {
        this.monitors = monitors;
        this.checks = checks;
        this.incidents = incidents;
        this.properties = properties;
    }

    /**
     * Stores a check, updates the monitor, opens or resolves incidents, and returns the alerts
     * to send. Alerts are returned rather than sent here so they go out only after the
     * transaction has committed.
     */
    @Transactional
    public List<AlertMessage> recordResult(Long monitorId, ProbeOutcome outcome) {
        Monitor monitor = monitors.findById(monitorId).orElse(null);
        if (monitor == null) {
            return List.of(); // deleted while its check was running
        }
        CheckResult result = checks.save(new CheckResult(monitor, outcome.checkedAt(), outcome.up(),
                outcome.statusCode(), outcome.responseTimeMs(), outcome.error(), outcome.sslExpiresAt()));

        List<AlertMessage> alerts = new ArrayList<>();
        switch (monitor.applyResult(result, properties.failureThreshold())) {
            case WENT_DOWN -> {
                incidents.save(new Incident(monitor, monitor.getStatusSince(), result.getError()));
                alerts.add(alert(AlertMessage.Type.DOWN, monitor, describeFailure(monitor, result)));
            }
            case RECOVERED -> {
                String detail = "Service is back up";
                Incident open = incidents.findOpen(monitor.getId()).orElse(null);
                if (open != null) {
                    open.resolve(result.getCheckedAt());
                    detail = "Back up after " + humanDuration(open.duration(result.getCheckedAt()))
                            + " (" + result.getResponseTimeMs() + " ms)";
                }
                alerts.add(alert(AlertMessage.Type.RECOVERED, monitor, detail));
            }
            case NONE -> { }
        }

        if (monitor.needsSslWarning(result.getCheckedAt(), properties.sslWarningDays())) {
            long days = Duration.between(result.getCheckedAt(), monitor.getSslExpiresAt()).toDays();
            LocalDate expiry = LocalDate.ofInstant(monitor.getSslExpiresAt(), ZoneOffset.UTC);
            alerts.add(alert(AlertMessage.Type.SSL_EXPIRING, monitor,
                    "Certificate expires in " + days + " days (" + expiry + "). Renew it before users see errors."));
        }
        return alerts;
    }

    @Transactional(readOnly = true)
    public List<MonitorSummary> listSummaries() {
        Instant now = Instant.now();
        return monitors.findAllByOrderByNameAsc().stream()
                .map(m -> toSummary(m, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CheckView> recentChecks(Long monitorId, int limit) {
        requireMonitor(monitorId);
        return checks.findRecent(monitorId, PageRequest.of(0, limit)).stream()
                .map(CheckView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentView> recentIncidents(int limit) {
        Instant now = Instant.now();
        return incidents.findRecent(PageRequest.of(0, limit)).stream()
                .map(i -> IncidentView.from(i, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentView> incidentsForMonitor(Long monitorId, int limit) {
        requireMonitor(monitorId);
        Instant now = Instant.now();
        return incidents.findRecentForMonitor(monitorId, PageRequest.of(0, limit)).stream()
                .map(i -> IncidentView.from(i, now))
                .toList();
    }

    @Transactional
    public MonitorSummary create(CreateMonitorRequest request) {
        int interval = request.intervalSeconds() == null ? 60 : request.intervalSeconds();
        String category = request.category() == null || request.category().isBlank()
                ? Monitor.DEFAULT_CATEGORY : request.category().trim();
        Monitor monitor = monitors.save(new Monitor(request.name().trim(), request.url().trim(), category, interval));
        log.info("Added monitor {} -> {}", monitor.getName(), monitor.getUrl());
        return toSummary(monitor, Instant.now());
    }

    @Transactional
    public void delete(Long monitorId) {
        requireMonitor(monitorId);
        incidents.deleteByMonitorId(monitorId);
        checks.deleteByMonitorId(monitorId);
        monitors.deleteById(monitorId);
    }

    @Transactional
    public int purgeOldChecks() {
        return checks.deleteOlderThan(Instant.now().minus(Duration.ofDays(properties.retentionDays())));
    }

    private void requireMonitor(Long monitorId) {
        if (!monitors.existsById(monitorId)) {
            throw new NoSuchElementException("Monitor " + monitorId + " not found");
        }
    }

    private MonitorSummary toSummary(Monitor m, Instant now) {
        Instant dayAgo = now.minus(Duration.ofHours(24));
        List<CheckView> recent = checks.findRecent(m.getId(), PageRequest.of(0, RECENT_CHECKS)).stream()
                .map(CheckView::from)
                .toList();
        Double avg = checks.averageResponseSince(m.getId(), dayAgo);
        return MonitorSummary.of(m, uptimePercent(m.getId(), dayAgo),
                uptimePercent(m.getId(), now.minus(Duration.ofDays(7))),
                avg == null ? null : Math.round(avg), recent, now);
    }

    /** Percentage of successful checks since the given time, or null when there is no data yet. */
    private Double uptimePercent(Long monitorId, Instant since) {
        Object[] row = checks.countChecksSince(monitorId, since).get(0);
        long total = ((Number) row[0]).longValue();
        long up = ((Number) row[1]).longValue();
        if (total == 0) {
            return null;
        }
        return Math.round(up * 10000.0 / total) / 100.0;
    }

    private static AlertMessage alert(AlertMessage.Type type, Monitor m, String detail) {
        return new AlertMessage(type, m.getName(), m.getUrl(), detail);
    }

    private String describeFailure(Monitor monitor, CheckResult result) {
        String cause = result.getError() == null ? "Check failed" : result.getError();
        return cause + " (" + monitor.getConsecutiveFailures() + " failed checks in a row)";
    }

    static String humanDuration(Duration d) {
        long minutes = d.toMinutes();
        if (minutes < 1) {
            return d.toSeconds() + " s";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = d.toHours();
        if (hours < 48) {
            return hours + " h " + (minutes % 60) + " min";
        }
        return d.toDays() + " days";
    }
}

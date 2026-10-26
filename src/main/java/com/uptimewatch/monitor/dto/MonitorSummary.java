package com.uptimewatch.monitor.dto;

import com.uptimewatch.monitor.Monitor;
import com.uptimewatch.monitor.MonitorStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Everything the status page needs for one monitor. recentChecks is ordered oldest first. */
public record MonitorSummary(
        Long id,
        String name,
        String url,
        String category,
        int intervalSeconds,
        MonitorStatus status,
        Instant statusSince,
        Instant lastCheckedAt,
        Long lastResponseTimeMs,
        Integer lastStatusCode,
        String lastError,
        int consecutiveFailures,
        Double uptime24h,
        Double uptime7d,
        Long avgResponseMs24h,
        Instant sslExpiresAt,
        Long sslDaysLeft,
        List<CheckView> recentChecks
) {

    public static MonitorSummary of(Monitor m, Double uptime24h, Double uptime7d, Long avgResponseMs24h,
                                    List<CheckView> newestFirst, Instant now) {
        List<CheckView> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);
        Long sslDaysLeft = m.getSslExpiresAt() == null ? null
                : Duration.between(now, m.getSslExpiresAt()).toDays();
        return new MonitorSummary(m.getId(), m.getName(), m.getUrl(), m.getCategory(), m.getIntervalSeconds(),
                m.getStatus(), m.getStatusSince(), m.getLastCheckedAt(), m.getLastResponseTimeMs(),
                m.getLastStatusCode(), m.getLastError(), m.getConsecutiveFailures(), uptime24h, uptime7d,
                avgResponseMs24h, m.getSslExpiresAt(), sslDaysLeft, oldestFirst);
    }
}

package com.uptimewatch.monitor.dto;

import com.uptimewatch.monitor.Incident;

import java.time.Instant;

public record IncidentView(
        Long id,
        Long monitorId,
        String monitorName,
        String category,
        Instant startedAt,
        Instant resolvedAt,
        boolean ongoing,
        long durationSeconds,
        String cause
) {

    public static IncidentView from(Incident i, Instant now) {
        return new IncidentView(i.getId(), i.getMonitor().getId(), i.getMonitor().getName(),
                i.getMonitor().getCategory(), i.getStartedAt(), i.getResolvedAt(), i.isOngoing(),
                i.duration(now).getSeconds(), i.getCause());
    }
}

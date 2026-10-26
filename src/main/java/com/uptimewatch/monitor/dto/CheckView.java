package com.uptimewatch.monitor.dto;

import com.uptimewatch.monitor.CheckResult;

import java.time.Instant;

public record CheckView(Instant checkedAt, boolean up, Integer statusCode, Long responseTimeMs, String error) {

    public static CheckView from(CheckResult c) {
        return new CheckView(c.getCheckedAt(), c.isUp(), c.getStatusCode(), c.getResponseTimeMs(), c.getError());
    }
}

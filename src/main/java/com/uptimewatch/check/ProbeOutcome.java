package com.uptimewatch.check;

import java.time.Instant;

/** What a single check observed. statusCode is null when no HTTP response arrived. */
public record ProbeOutcome(
        Instant checkedAt,
        boolean up,
        Integer statusCode,
        long responseTimeMs,
        String error,
        Instant sslExpiresAt
) {
}

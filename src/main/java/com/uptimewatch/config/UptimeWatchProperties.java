package com.uptimewatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "uptimewatch")
public record UptimeWatchProperties(
        String apiKey,
        long schedulerTickMs,
        int checkerThreads,
        int defaultTimeoutMs,
        int retentionDays,
        /* Consecutive failed checks before a monitor is declared DOWN (filters one-off blips). */
        int failureThreshold,
        /* Send a warning when a certificate expires within this many days. */
        int sslWarningDays,
        /* Public address of the status page, used as a link in alerts. Optional. */
        String publicUrl,
        Telegram telegram,
        List<SeedMonitor> seedMonitors
) {
    public record SeedMonitor(String name, String url, String category) {
    }

    /** Alerts are sent to Telegram only when both botToken and chatId are set. */
    public record Telegram(String botToken, String chatId, String apiBase) {
        public boolean enabled() {
            return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
        }
    }

    public UptimeWatchProperties {
        if (failureThreshold < 1) {
            failureThreshold = 2;
        }
        if (sslWarningDays < 1) {
            sslWarningDays = 14;
        }
        if (telegram == null) {
            telegram = new Telegram(null, null, null);
        }
        if (seedMonitors == null) {
            seedMonitors = List.of();
        }
    }
}

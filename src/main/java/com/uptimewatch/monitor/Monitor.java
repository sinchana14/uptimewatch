package com.uptimewatch.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * A URL being watched, plus a snapshot of its latest check so the status page
 * can render without scanning the full check history.
 */
@Entity
@Table(name = "monitors")
public class Monitor {

    public static final String DEFAULT_CATEGORY = "Other";

    /** How a check changed the monitor's confirmed status. */
    public enum Transition { NONE, WENT_DOWN, RECOVERED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 60)
    private String category;

    @Column(nullable = false)
    private int intervalSeconds = 60;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MonitorStatus status = MonitorStatus.UNKNOWN;

    private Instant lastCheckedAt;
    private Long lastResponseTimeMs;
    private Integer lastStatusCode;

    @Column(length = 500)
    private String lastError;

    /** Start of the current up/down streak, used for "down for 12 min" on the status page. */
    private Instant statusSince;

    private Instant sslExpiresAt;

    /** Failed checks in a row; the monitor is only declared DOWN once this reaches the threshold. */
    private Integer consecutiveFailures = 0;

    /** When the current run of failures began, so an outage is dated from its first failed check. */
    private Instant failingSince;

    /** The certificate expiry we last warned about, so each certificate triggers one warning. */
    private Instant sslWarnedForExpiry;

    protected Monitor() {
    }

    public Monitor(String name, String url, String category, int intervalSeconds) {
        this.name = name;
        this.url = url;
        this.category = category;
        this.intervalSeconds = intervalSeconds;
    }

    public boolean isDue(Instant now) {
        return enabled && (lastCheckedAt == null
                || Duration.between(lastCheckedAt, now).getSeconds() >= intervalSeconds);
    }

    /**
     * Records a finished check. A success marks the monitor UP straight away; failures only
     * mark it DOWN after {@code failureThreshold} in a row, so a single network blip never
     * pages anyone. Moving from UNKNOWN to UP is not reported as a recovery.
     */
    public Transition applyResult(CheckResult result, int failureThreshold) {
        lastCheckedAt = result.getCheckedAt();
        lastResponseTimeMs = result.getResponseTimeMs();
        lastStatusCode = result.getStatusCode();
        lastError = result.getError();
        if (result.getSslExpiresAt() != null) {
            sslExpiresAt = result.getSslExpiresAt();
        }

        if (result.isUp()) {
            consecutiveFailures = 0;
            failingSince = null;
            if (status == MonitorStatus.UP) {
                return Transition.NONE;
            }
            MonitorStatus previous = status;
            status = MonitorStatus.UP;
            statusSince = result.getCheckedAt();
            return previous == MonitorStatus.DOWN ? Transition.RECOVERED : Transition.NONE;
        }

        int failures = getConsecutiveFailures() + 1;
        consecutiveFailures = failures;
        if (failures == 1) {
            failingSince = result.getCheckedAt();
        }
        if (status != MonitorStatus.DOWN && failures >= failureThreshold) {
            status = MonitorStatus.DOWN;
            statusSince = failingSince;
            return Transition.WENT_DOWN;
        }
        return Transition.NONE;
    }

    /** True once per certificate when it comes within the warning window. */
    public boolean needsSslWarning(Instant now, int warningDays) {
        if (sslExpiresAt == null || sslExpiresAt.equals(sslWarnedForExpiry)) {
            return false;
        }
        if (Duration.between(now, sslExpiresAt).toDays() < warningDays) {
            sslWarnedForExpiry = sslExpiresAt;
            return true;
        }
        return false;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getCategory() { return category == null || category.isBlank() ? DEFAULT_CATEGORY : category; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public MonitorStatus getStatus() { return status; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public Long getLastResponseTimeMs() { return lastResponseTimeMs; }
    public Integer getLastStatusCode() { return lastStatusCode; }
    public String getLastError() { return lastError; }
    public Instant getStatusSince() { return statusSince; }
    public Instant getSslExpiresAt() { return sslExpiresAt; }
    public int getConsecutiveFailures() { return consecutiveFailures == null ? 0 : consecutiveFailures; }
    public Instant getFailingSince() { return failingSince; }
}

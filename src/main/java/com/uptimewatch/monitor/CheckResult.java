package com.uptimewatch.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** One HTTP check of a monitor. These rows are the raw history behind uptime percentages. */
@Entity
@Table(name = "check_results",
        indexes = @Index(name = "idx_check_monitor_time", columnList = "monitor_id, checkedAt"))
public class CheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitor_id")
    private Monitor monitor;

    @Column(nullable = false)
    private Instant checkedAt;

    @Column(nullable = false)
    private boolean up;

    private Integer statusCode;
    private Long responseTimeMs;

    @Column(length = 500)
    private String error;

    private Instant sslExpiresAt;

    protected CheckResult() {
    }

    public CheckResult(Monitor monitor, Instant checkedAt, boolean up, Integer statusCode,
                       Long responseTimeMs, String error, Instant sslExpiresAt) {
        this.monitor = monitor;
        this.checkedAt = checkedAt;
        this.up = up;
        this.statusCode = statusCode;
        this.responseTimeMs = responseTimeMs;
        this.error = error;
        this.sslExpiresAt = sslExpiresAt;
    }

    public Long getId() { return id; }
    public Monitor getMonitor() { return monitor; }
    public Instant getCheckedAt() { return checkedAt; }
    public boolean isUp() { return up; }
    public Integer getStatusCode() { return statusCode; }
    public Long getResponseTimeMs() { return responseTimeMs; }
    public String getError() { return error; }
    public Instant getSslExpiresAt() { return sslExpiresAt; }
}

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

import java.time.Duration;
import java.time.Instant;

/** One outage: opened when a monitor is confirmed DOWN, resolved when it comes back UP. */
@Entity
@Table(name = "incidents",
        indexes = @Index(name = "idx_incident_started", columnList = "startedAt"))
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitor_id")
    private Monitor monitor;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant resolvedAt;

    @Column(length = 500)
    private String cause;

    protected Incident() {
    }

    public Incident(Monitor monitor, Instant startedAt, String cause) {
        this.monitor = monitor;
        this.startedAt = startedAt;
        this.cause = cause;
    }

    public void resolve(Instant at) {
        this.resolvedAt = at;
    }

    public boolean isOngoing() {
        return resolvedAt == null;
    }

    public Duration duration(Instant now) {
        return Duration.between(startedAt, resolvedAt == null ? now : resolvedAt);
    }

    public Long getId() { return id; }
    public Monitor getMonitor() { return monitor; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getCause() { return cause; }
}

package com.chris64233.cc.foodrecall.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "recall_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_recall_events_number", columnNames = "recall_number"))
public class RecallEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recall_number", nullable = false, length = 64)
    private String recallNumber;

    @Column(name = "root_lot_number", nullable = false, length = 64)
    private String rootLotNumber;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecallStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected RecallEvent() {
    }

    public RecallEvent(String recallNumber, String rootLotNumber, String reason) {
        this.recallNumber = recallNumber;
        this.rootLotNumber = rootLotNumber;
        this.reason = reason;
        this.status = RecallStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getRecallNumber() {
        return recallNumber;
    }

    public String getRootLotNumber() {
        return rootLotNumber;
    }

    public String getReason() {
        return reason;
    }

    public RecallStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void close() {
        this.status = RecallStatus.CLOSED;
        this.closedAt = Instant.now();
    }
}

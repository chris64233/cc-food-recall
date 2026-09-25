package com.chris64233.cc.foodrecall.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "recall_events", uniqueConstraints = @UniqueConstraint(columnNames = "recall_number"))
public class RecallEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recall_number", nullable = false, length = 64)
    private String recallNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "root_lot_id", nullable = false)
    private Lot rootLot;

    @Column(nullable = false, length = 512)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecallStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant closedAt;

    protected RecallEvent() {
    }

    public RecallEvent(String recallNumber, Lot rootLot, String reason, Instant createdAt) {
        this.recallNumber = recallNumber;
        this.rootLot = rootLot;
        this.reason = reason;
        this.status = RecallStatus.OPEN;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getRecallNumber() {
        return recallNumber;
    }

    public Lot getRootLot() {
        return rootLot;
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

    public void close(Instant closedAt) {
        this.status = RecallStatus.CLOSED;
        this.closedAt = closedAt;
    }
}

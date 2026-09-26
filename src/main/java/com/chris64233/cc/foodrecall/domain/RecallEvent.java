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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recall_events", uniqueConstraints = {
        @UniqueConstraint(columnNames = "recall_number"),
        @UniqueConstraint(columnNames = "close_number")
})
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

    @Column(name = "close_number", length = 64)
    private String closeNumber;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "unrecovered_quantity", precision = 19, scale = 3)
    private BigDecimal unrecoveredQuantity;

    /**
     * 统计版本：任何影响统计口径的事件（新报告、追加通知、生产转换传播）
     * 都会使其递增。关闭决定若基于旧版本则视为失效。
     */
    @Column(name = "stats_version", nullable = false)
    private long statsVersion;

    @Column(name = "notification_seq", nullable = false)
    private int notificationSeq;

    protected RecallEvent() {
    }

    public RecallEvent(String recallNumber, Lot rootLot, String reason, Instant createdAt) {
        this.recallNumber = recallNumber;
        this.rootLot = rootLot;
        this.reason = reason;
        this.status = RecallStatus.OPEN;
        this.createdAt = createdAt;
        this.statsVersion = 0;
        this.notificationSeq = 0;
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

    public String getCloseNumber() {
        return closeNumber;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public BigDecimal getUnrecoveredQuantity() {
        return unrecoveredQuantity;
    }

    public long getStatsVersion() {
        return statsVersion;
    }

    public void bumpStatsVersion() {
        this.statsVersion++;
    }

    public String nextNotificationNumber() {
        this.notificationSeq++;
        return recallNumber + "-N" + notificationSeq;
    }

    public void close(String closeNumber, String approvedBy, BigDecimal unrecoveredQuantity, Instant closedAt) {
        this.status = RecallStatus.CLOSED;
        this.closeNumber = closeNumber;
        this.approvedBy = approvedBy;
        this.unrecoveredQuantity = unrecoveredQuantity;
        this.closedAt = closedAt;
    }
}

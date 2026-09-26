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

    /** 该召回要求的最低响应率（0~1），关闭时统计的响应率必须达到该门槛。 */
    @Column(name = "min_response_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal minResponseRate;

    /**
     * 统计版本号：每追加一批影响通知、每收到一份下游报告都显式 +1。
     * 关闭时可携带发起方看到的版本号，版本号不一致说明关闭决定基于过期统计，必须失效。
     */
    @Column(name = "statistics_version", nullable = false)
    private long statisticsVersion;

    /** 已经使用过的最大通知批次号：初始清单为 1，每次追加去向递增。 */
    @Column(name = "last_batch_number", nullable = false)
    private int lastBatchNumber;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant closedAt;

    protected RecallEvent() {
    }

    public RecallEvent(String recallNumber, Lot rootLot, String reason,
                       BigDecimal minResponseRate, Instant createdAt) {
        this.recallNumber = recallNumber;
        this.rootLot = rootLot;
        this.reason = reason;
        this.status = RecallStatus.OPEN;
        this.minResponseRate = minResponseRate;
        this.statisticsVersion = 0;
        this.lastBatchNumber = 0;
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

    public BigDecimal getMinResponseRate() {
        return minResponseRate;
    }

    public long getStatisticsVersion() {
        return statisticsVersion;
    }

    public int getLastBatchNumber() {
        return lastBatchNumber;
    }

    public int nextBatchNumber() {
        this.lastBatchNumber++;
        return this.lastBatchNumber;
    }

    /** 统计依据发生变化（追加去向、收到下游报告）时推进版本号。 */
    public void bumpStatistics() {
        this.statisticsVersion++;
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

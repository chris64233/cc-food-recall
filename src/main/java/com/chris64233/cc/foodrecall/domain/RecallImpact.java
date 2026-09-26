package com.chris64233.cc.foodrecall.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 召回影响清单中的一条：受影响批次。只追加、不修改、不删除。
 * batchNumber=1 为发起召回时根据当时谱系生成的初始清单；
 * 之后因新生产转换而新发现的后代批次以更大的批次号追加。
 */
@Entity
@Table(name = "recall_impacts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"recall_id", "lot_id"}))
public class RecallImpact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recall_id", nullable = false)
    private RecallEvent recall;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Column(nullable = false, length = 512)
    private String reason;

    /** 通知批次号：1 表示初始清单，之后每次追加 +1。 */
    @Column(name = "batch_number", nullable = false)
    private int batchNumber;

    /** 发现该批次受影响时，该批次在工厂的库存快照。 */
    @Column(name = "snapshot_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal snapshotQuantity;

    /** 该批次中来自召回根批次的归因比例（多路径汇合时比例相加，0~1）。 */
    @Column(name = "root_fraction", nullable = false, precision = 8, scale = 6)
    private BigDecimal rootFraction;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    protected RecallImpact() {
    }

    public RecallImpact(RecallEvent recall, Lot lot, String reason, int batchNumber,
                        BigDecimal snapshotQuantity, BigDecimal rootFraction, Instant discoveredAt) {
        this.recall = recall;
        this.lot = lot;
        this.reason = reason;
        this.batchNumber = batchNumber;
        this.snapshotQuantity = snapshotQuantity;
        this.rootFraction = rootFraction;
        this.discoveredAt = discoveredAt;
    }

    public Long getId() {
        return id;
    }

    public RecallEvent getRecall() {
        return recall;
    }

    public Lot getLot() {
        return lot;
    }

    public String getReason() {
        return reason;
    }

    public int getBatchNumber() {
        return batchNumber;
    }

    public BigDecimal getSnapshotQuantity() {
        return snapshotQuantity;
    }

    public BigDecimal getRootFraction() {
        return rootFraction;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }
}

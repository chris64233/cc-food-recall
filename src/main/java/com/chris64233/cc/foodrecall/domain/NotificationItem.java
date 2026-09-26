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

/**
 * 通知任务中的一条批次明细及其受影响数量（不可变）。
 * 同一批次即使经多条谱系路径影响到同一持有方，这里也只有一条聚合后的记录，不重复计量。
 */
@Entity
@Table(name = "notification_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"notification_id", "lot_id"}))
public class NotificationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private RecallNotification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /** 通知该持有方需要处置的数量（产品计量单位）。 */
    @Column(name = "affected_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal affectedQuantity;

    /** 该批次中来自召回根批次的归因比例（多路径汇合时比例相加），仅用于影响分析。 */
    @Column(name = "root_fraction", nullable = false, precision = 8, scale = 6)
    private BigDecimal rootFraction;

    protected NotificationItem() {
    }

    public NotificationItem(RecallNotification notification, Lot lot,
                            BigDecimal affectedQuantity, BigDecimal rootFraction) {
        this.notification = notification;
        this.lot = lot;
        this.affectedQuantity = affectedQuantity;
        this.rootFraction = rootFraction;
    }

    public Long getId() {
        return id;
    }

    public RecallNotification getNotification() {
        return notification;
    }

    public Lot getLot() {
        return lot;
    }

    public BigDecimal getAffectedQuantity() {
        return affectedQuantity;
    }

    public BigDecimal getRootFraction() {
        return rootFraction;
    }
}

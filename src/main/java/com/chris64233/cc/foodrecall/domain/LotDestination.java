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
 * 已知去向：某批次从工厂发往下游持有方的一条不可变发货流水。
 * 发起召回时据此生成初始影响清单；召回后新发往（召回已拦截，不能创建）
 * 或经下游报告“已转交”到达的持有方通过追加批次进入清单。
 */
@Entity
@Table(name = "lot_destinations",
        uniqueConstraints = @UniqueConstraint(columnNames = "destination_number"))
public class LotDestination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发货单号，保证发货登记幂等。 */
    @Column(name = "destination_number", nullable = false, length = 64)
    private String destinationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holder_id", nullable = false)
    private DownstreamHolder holder;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false)
    private Instant createdAt;

    protected LotDestination() {
    }

    public LotDestination(String destinationNumber, Lot lot, DownstreamHolder holder,
                          BigDecimal quantity, Instant createdAt) {
        this.destinationNumber = destinationNumber;
        this.lot = lot;
        this.holder = holder;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getDestinationNumber() {
        return destinationNumber;
    }

    public Lot getLot() {
        return lot;
    }

    public DownstreamHolder getHolder() {
        return holder;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

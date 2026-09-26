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
 * 批次发货去向：记录某批次有多少数量流向了哪个下游持有方。
 * 是召回发起时"已知去向"的来源。
 */
@Entity
@Table(name = "shipments", uniqueConstraints = @UniqueConstraint(columnNames = "shipment_key"))
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_key", nullable = false, length = 64)
    private String shipmentKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Column(nullable = false, length = 128)
    private String holder;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false)
    private Instant createdAt;

    protected Shipment() {
    }

    public Shipment(String shipmentKey, Lot lot, String holder, BigDecimal quantity, Instant createdAt) {
        this.shipmentKey = shipmentKey;
        this.lot = lot;
        this.holder = holder;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getShipmentKey() {
        return shipmentKey;
    }

    public Lot getLot() {
        return lot;
    }

    public String getHolder() {
        return holder;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

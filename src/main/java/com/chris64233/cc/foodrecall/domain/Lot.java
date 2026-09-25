package com.chris64233.cc.foodrecall.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(name = "lots", uniqueConstraints = @UniqueConstraint(name = "uk_lots_lot_number", columnNames = "lot_number"))
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_number", nullable = false, length = 64)
    private String lotNumber;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    protected Lot() {
    }

    public Lot(String lotNumber, BigDecimal quantity) {
        this.lotNumber = lotNumber;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}

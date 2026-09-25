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

@Entity
@Table(name = "transformation_inputs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"transformation_id", "lot_id"}))
public class TransformationInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transformation_id", nullable = false)
    private Transformation transformation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    protected TransformationInput() {
    }

    public TransformationInput(Transformation transformation, Lot lot, BigDecimal quantity) {
        this.transformation = transformation;
        this.lot = lot;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Transformation getTransformation() {
        return transformation;
    }

    public Lot getLot() {
        return lot;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }
}

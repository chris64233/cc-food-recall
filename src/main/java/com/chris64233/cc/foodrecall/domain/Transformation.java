package com.chris64233.cc.foodrecall.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transformations",
        uniqueConstraints = @UniqueConstraint(name = "uk_transformations_tx_id", columnNames = "transformation_id"))
public class Transformation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transformation_id", nullable = false, length = 64)
    private String transformationId;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "loss", nullable = false, precision = 19, scale = 3)
    private BigDecimal loss;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transformation() {
    }

    public Transformation(String transformationId, String content, BigDecimal loss) {
        this.transformationId = transformationId;
        this.content = content;
        this.loss = loss;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTransformationId() {
        return transformationId;
    }

    public String getContent() {
        return content;
    }

    public BigDecimal getLoss() {
        return loss;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

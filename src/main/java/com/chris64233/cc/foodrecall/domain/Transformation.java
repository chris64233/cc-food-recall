package com.chris64233.cc.foodrecall.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transformations", uniqueConstraints = @UniqueConstraint(columnNames = "transformation_key"))
public class Transformation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transformation_key", nullable = false, length = 64)
    private String transformationKey;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal loss;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transformation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransformationInput> inputs = new ArrayList<>();

    @OneToMany(mappedBy = "transformation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransformationOutput> outputs = new ArrayList<>();

    protected Transformation() {
    }

    public Transformation(String transformationKey, BigDecimal loss, Instant createdAt) {
        this.transformationKey = transformationKey;
        this.loss = loss;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTransformationKey() {
        return transformationKey;
    }

    public BigDecimal getLoss() {
        return loss;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<TransformationInput> getInputs() {
        return inputs;
    }

    public List<TransformationOutput> getOutputs() {
        return outputs;
    }
}

package com.chris64233.cc.foodrecall.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 下游持有方（经销商、零售商、客户等）。召回通知与响应报告都按持有方归集。
 */
@Entity
@Table(name = "downstream_holders", uniqueConstraints = @UniqueConstraint(columnNames = "holder_code"))
public class DownstreamHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holder_code", nullable = false, length = 64)
    private String holderCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private Instant createdAt;

    protected DownstreamHolder() {
    }

    public DownstreamHolder(String holderCode, String name, Instant createdAt) {
        this.holderCode = holderCode;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getHolderCode() {
        return holderCode;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

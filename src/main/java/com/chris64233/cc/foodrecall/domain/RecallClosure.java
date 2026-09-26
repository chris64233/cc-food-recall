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
 * 召回关闭记录（关闭号幂等）。关闭时明确记录：
 * 当时的统计版本、响应率/完成率、尚未收回数量及批准人。
 * 一条召回最多只有一条生效的关闭记录。
 */
@Entity
@Table(name = "recall_closures",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "closure_number"),
                @UniqueConstraint(columnNames = "recall_id")
        })
public class RecallClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closure_number", nullable = false, length = 64)
    private String closureNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recall_id", nullable = false)
    private RecallEvent recall;

    @Column(name = "approver", nullable = false, length = 64)
    private String approver;

    /** 关闭决定所依据的统计版本号。 */
    @Column(name = "based_on_version", nullable = false)
    private long basedOnVersion;

    /** 关闭时已确认的受影响总量（下游通知量）。 */
    @Column(name = "affected_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal affectedQuantity;

    /** 关闭时尚未收回的数量（受影响量 − 已隔离）。 */
    @Column(name = "unrecovered_quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal unrecoveredQuantity;

    @Column(name = "response_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal responseRate;

    @Column(name = "completion_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal completionRate;

    @Column(nullable = false)
    private Instant createdAt;

    protected RecallClosure() {
    }

    public RecallClosure(String closureNumber, RecallEvent recall, String approver,
                         long basedOnVersion, BigDecimal affectedQuantity,
                         BigDecimal unrecoveredQuantity, BigDecimal responseRate,
                         BigDecimal completionRate, Instant createdAt) {
        this.closureNumber = closureNumber;
        this.recall = recall;
        this.approver = approver;
        this.basedOnVersion = basedOnVersion;
        this.affectedQuantity = affectedQuantity;
        this.unrecoveredQuantity = unrecoveredQuantity;
        this.responseRate = responseRate;
        this.completionRate = completionRate;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getClosureNumber() {
        return closureNumber;
    }

    public RecallEvent getRecall() {
        return recall;
    }

    public String getApprover() {
        return approver;
    }

    public long getBasedOnVersion() {
        return basedOnVersion;
    }

    public BigDecimal getAffectedQuantity() {
        return affectedQuantity;
    }

    public BigDecimal getUnrecoveredQuantity() {
        return unrecoveredQuantity;
    }

    public BigDecimal getResponseRate() {
        return responseRate;
    }

    public BigDecimal getCompletionRate() {
        return completionRate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

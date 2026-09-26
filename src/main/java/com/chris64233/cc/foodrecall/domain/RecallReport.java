package com.chris64233.cc.foodrecall.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 下游持有方提交的处置报告。报告号全局唯一，保证幂等。
 */
@Entity
@Table(name = "recall_reports",
        uniqueConstraints = @UniqueConstraint(columnNames = "report_number"))
public class RecallReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_number", nullable = false, length = 64)
    private String reportNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recall_id", nullable = false)
    private RecallEvent recall;

    @Column(nullable = false, length = 128)
    private String holder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportType type;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "transferred_to", length = 128)
    private String transferredTo;

    @Column(nullable = false)
    private Instant createdAt;

    protected RecallReport() {
    }

    public RecallReport(String reportNumber, RecallEvent recall, String holder, ReportType type,
                        BigDecimal quantity, String transferredTo, Instant createdAt) {
        this.reportNumber = reportNumber;
        this.recall = recall;
        this.holder = holder;
        this.type = type;
        this.quantity = quantity;
        this.transferredTo = transferredTo;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getReportNumber() {
        return reportNumber;
    }

    public RecallEvent getRecall() {
        return recall;
    }

    public String getHolder() {
        return holder;
    }

    public ReportType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getTransferredTo() {
        return transferredTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

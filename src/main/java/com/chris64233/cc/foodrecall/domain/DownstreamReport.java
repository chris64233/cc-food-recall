package com.chris64233.cc.foodrecall.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 下游持有方提交的一份召回响应报告（报告号幂等）。
 * 一次报告可包含多条批次明细，明细可以追加提交，但累计数量必须与接收量守恒。
 */
@Entity
@Table(name = "downstream_reports",
        uniqueConstraints = @UniqueConstraint(columnNames = "report_number"))
public class DownstreamReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_number", nullable = false, length = 64)
    private String reportNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recall_id", nullable = false)
    private RecallEvent recall;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holder_id", nullable = false)
    private DownstreamHolder holder;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "report")
    private List<ReportItem> items = new ArrayList<>();

    protected DownstreamReport() {
    }

    public DownstreamReport(String reportNumber, RecallEvent recall, DownstreamHolder holder,
                            Instant createdAt) {
        this.reportNumber = reportNumber;
        this.recall = recall;
        this.holder = holder;
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

    public DownstreamHolder getHolder() {
        return holder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ReportItem> getItems() {
        return items;
    }
}

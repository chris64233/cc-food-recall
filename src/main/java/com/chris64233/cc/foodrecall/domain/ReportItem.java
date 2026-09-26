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

/**
 * 响应报告中的一条批次处置明细。
 * 各明细累计数量加上尚未处置数量必须等于该持有方对该批次的接收数量（守恒）。
 */
@Entity
@Table(name = "report_items",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"report_id", "lot_id", "disposition", "transferred_to_id"}))
public class ReportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private DownstreamReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportDisposition disposition;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    /** 仅 disposition=TRANSFERRED 时填写：产品转交给了哪个持有方。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferred_to_id")
    private DownstreamHolder transferredTo;

    protected ReportItem() {
    }

    public ReportItem(DownstreamReport report, Lot lot, ReportDisposition disposition,
                      BigDecimal quantity, DownstreamHolder transferredTo) {
        this.report = report;
        this.lot = lot;
        this.disposition = disposition;
        this.quantity = quantity;
        this.transferredTo = transferredTo;
    }

    public Long getId() {
        return id;
    }

    public DownstreamReport getReport() {
        return report;
    }

    public Lot getLot() {
        return lot;
    }

    public ReportDisposition getDisposition() {
        return disposition;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public DownstreamHolder getTransferredTo() {
        return transferredTo;
    }
}

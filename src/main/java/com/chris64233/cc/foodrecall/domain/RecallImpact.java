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

@Entity
@Table(name = "recall_impacts",
        uniqueConstraints = @UniqueConstraint(name = "uk_recall_impacts_recall_lot", columnNames = {"recall_id", "lot_id"}))
public class RecallImpact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recall_id", nullable = false)
    private RecallEvent recall;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    protected RecallImpact() {
    }

    public RecallImpact(RecallEvent recall, Lot lot, String reason) {
        this.recall = recall;
        this.lot = lot;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public RecallEvent getRecall() {
        return recall;
    }

    public Lot getLot() {
        return lot;
    }

    public String getReason() {
        return reason;
    }
}

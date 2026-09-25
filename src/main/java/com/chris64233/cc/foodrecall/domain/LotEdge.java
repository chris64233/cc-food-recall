package com.chris64233.cc.foodrecall.domain;

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
@Table(name = "lot_edges",
        uniqueConstraints = @UniqueConstraint(name = "uk_lot_edges_parent_child", columnNames = {"parent_id", "child_id"}))
public class LotEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Lot parent;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Lot child;

    protected LotEdge() {
    }

    public LotEdge(Lot parent, Lot child) {
        this.parent = parent;
        this.child = child;
    }

    public Long getId() {
        return id;
    }

    public Lot getParent() {
        return parent;
    }

    public Lot getChild() {
        return child;
    }
}

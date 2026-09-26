package com.chris64233.cc.foodrecall.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 召回通知任务：针对一个持有方的一次通知。初始清单为每个下游持有方创建一条，
 * 之后新发现的去向（追加清单批次）为同一持有方创建新的通知记录，原通知永不改写。
 * 通知号全局唯一、保证幂等。
 */
@Entity
@Table(name = "recall_notifications",
        uniqueConstraints = @UniqueConstraint(columnNames = "notification_number"))
public class RecallNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_number", nullable = false, length = 64)
    private String notificationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recall_id", nullable = false)
    private RecallEvent recall;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holder_id", nullable = false)
    private DownstreamHolder holder;

    /** 该通知对应的影响清单批次号（1=初始清单）。 */
    @Column(name = "batch_number", nullable = false)
    private int batchNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotificationItem> items = new ArrayList<>();

    protected RecallNotification() {
    }

    public RecallNotification(String notificationNumber, RecallEvent recall, DownstreamHolder holder,
                              int batchNumber, Instant createdAt) {
        this.notificationNumber = notificationNumber;
        this.recall = recall;
        this.holder = holder;
        this.batchNumber = batchNumber;
        this.status = NotificationStatus.PENDING;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getNotificationNumber() {
        return notificationNumber;
    }

    public RecallEvent getRecall() {
        return recall;
    }

    public DownstreamHolder getHolder() {
        return holder;
    }

    public int getBatchNumber() {
        return batchNumber;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void markResponded() {
        this.status = NotificationStatus.RESPONDED;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<NotificationItem> getItems() {
        return items;
    }
}

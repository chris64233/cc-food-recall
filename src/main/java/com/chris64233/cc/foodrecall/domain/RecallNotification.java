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
 * 召回通知任务。记录创建后不可变：后续新发现的去向只会追加新的通知记录，
 * 不会改写已有通知。同一持有方在同一召回下可能有多条通知（初始 + 追加）。
 */
@Entity
@Table(name = "recall_notifications",
        uniqueConstraints = @UniqueConstraint(columnNames = "notification_number"))
public class RecallNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_number", nullable = false, length = 96)
    private String notificationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recall_id", nullable = false)
    private RecallEvent recall;

    @Column(nullable = false, length = 128)
    private String holder;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationSource source;

    @Column(nullable = false)
    private Instant createdAt;

    protected RecallNotification() {
    }

    public RecallNotification(String notificationNumber, RecallEvent recall, String holder,
                              BigDecimal quantity, NotificationSource source, Instant createdAt) {
        this.notificationNumber = notificationNumber;
        this.recall = recall;
        this.holder = holder;
        this.quantity = quantity;
        this.source = source;
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

    public String getHolder() {
        return holder;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public NotificationSource getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

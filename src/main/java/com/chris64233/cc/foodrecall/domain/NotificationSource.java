package com.chris64233.cc.foodrecall.domain;

/**
 * 通知来源：SHIPMENT 表示由我方发货产生的首级去向，
 * TRANSFER 表示由下游持有方转交报告追加的去向。
 */
public enum NotificationSource {
    SHIPMENT,
    TRANSFER
}

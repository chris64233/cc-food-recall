package com.chris64233.cc.foodrecall.domain;

/**
 * 下游持有方对召回通知的处置类别。
 */
public enum ReportDisposition {

    /** 已隔离：产品仍在持有方手中并被封存，属于已收回数量。 */
    QUARANTINED,

    /** 已消费：产品已被使用/食用，无法收回。 */
    CONSUMED,

    /** 已转交：产品流转给了另一个持有方，需要继续追踪。 */
    TRANSFERRED,

    /** 数量不符：持有方账面上找不到对应数量，无法收回。 */
    DISCREPANCY
}

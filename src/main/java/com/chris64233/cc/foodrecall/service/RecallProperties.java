package com.chris64233.cc.foodrecall.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 召回相关配置：
 * <pre>recall.min-response-rate=1.0</pre>
 * 关闭召回时要求的默认最低响应率（0~1），可在单个召回发起时覆盖。
 */
@Component
@ConfigurationProperties(prefix = "recall")
public class RecallProperties {

    private BigDecimal minResponseRate = BigDecimal.ONE;

    public BigDecimal getMinResponseRate() {
        return minResponseRate;
    }

    public void setMinResponseRate(BigDecimal minResponseRate) {
        this.minResponseRate = minResponseRate;
    }
}

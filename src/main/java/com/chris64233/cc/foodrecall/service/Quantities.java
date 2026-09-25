package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.error.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Quantities {

    public static final int SCALE = 3;
    public static final BigDecimal ZERO = new BigDecimal("0.000");

    private Quantities() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        try {
            return value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("数量精度最多支持 " + SCALE + " 位小数: " + value);
        }
    }

    public static BigDecimal requirePositive(BigDecimal value, String field) {
        BigDecimal normalized = normalize(value);
        if (normalized == null || normalized.signum() <= 0) {
            throw ApiException.badRequest(field + " 必须为正数");
        }
        return normalized;
    }
}

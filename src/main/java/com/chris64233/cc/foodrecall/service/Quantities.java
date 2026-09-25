package com.chris64233.cc.foodrecall.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Quantities {

    public static final int SCALE = 3;

    private Quantities() {
    }

    public static BigDecimal normalize(BigDecimal value, String field) {
        if (value == null) {
            throw ApiException.badRequest(field + " must not be null");
        }
        try {
            return value.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw ApiException.badRequest(field + " supports at most " + SCALE + " decimal places");
        }
    }

    public static BigDecimal normalize(String value) {
        return normalize(new BigDecimal(value), "quantity");
    }
}

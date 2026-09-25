package com.chris64233.cc.foodrecall.service;

import java.math.BigDecimal;
import java.util.List;

public final class Views {

    private Views() {
    }

    public record MutationResult<T>(T view, boolean created) {
    }

    public record LotAmountView(String lotNumber, BigDecimal quantity) {
    }

    public record LotView(String lotNumber, BigDecimal quantity, boolean quarantined) {
    }

    public record RecallReasonView(String recallNumber, String reason, String status) {
    }

    public record LotStockView(String lotNumber, BigDecimal quantity, boolean quarantined,
                               List<RecallReasonView> activeRecalls) {
    }

    public record LineageView(String lotNumber, String direction, List<LotView> relatedLots) {
    }

    public record TransformationView(String transformationId, List<LotAmountView> inputs,
                                     List<LotAmountView> outputs, BigDecimal loss) {
    }

    public record RecallView(String recallNumber, String lotNumber, String reason, String status,
                             List<String> impactedLots) {
    }
}

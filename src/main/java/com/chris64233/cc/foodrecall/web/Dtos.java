package com.chris64233.cc.foodrecall.web;

import java.math.BigDecimal;
import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public record LotAmount(String lotNumber, BigDecimal quantity) {
    }

    public record RegisterLotRequest(String lotNumber, BigDecimal quantity) {
    }

    public record RecallReasonView(String recallNumber, String reason) {
    }

    public record LotResponse(String lotNumber, BigDecimal quantity, boolean quarantined,
                              List<RecallReasonView> recallReasons) {
    }

    public record TransformRequest(String transformationId, List<LotAmount> inputs,
                                   List<LotAmount> outputs, BigDecimal loss) {
    }

    public record TransformResponse(String transformationId, List<LotAmount> inputs,
                                    List<LotAmount> outputs, BigDecimal loss) {
    }

    public record RecallRequest(String recallNumber, String lotNumber, String reason) {
    }

    public record RecallResponse(String recallNumber, String lotNumber, String reason,
                                 String status, long affectedLots) {
    }

    public record GenealogyNode(String lotNumber, int depth) {
    }

    public record GenealogyResponse(String lotNumber, List<GenealogyNode> upstream,
                                    List<GenealogyNode> downstream) {
    }
}

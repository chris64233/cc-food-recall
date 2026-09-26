package com.chris64233.cc.foodrecall.web;

import java.math.BigDecimal;
import java.time.Instant;
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
                                 String status, long affectedLots, long statsVersion,
                                 String closeNumber, String approvedBy,
                                 BigDecimal unrecoveredQuantity) {
    }

    public record CloseRequest(String closeNumber, String approvedBy, Long expectedStatsVersion) {
    }

    public record ShipmentRequest(String shipmentKey, String lotNumber, String holder,
                                  BigDecimal quantity) {
    }

    public record ShipmentResponse(String shipmentKey, String lotNumber, String holder,
                                   BigDecimal quantity) {
    }

    public record ReportRequest(String reportNumber, String holder, String type,
                                BigDecimal quantity, String transferredTo) {
    }

    public record ReportResponse(String reportNumber, String recallNumber, String holder,
                                 String type, BigDecimal quantity, String transferredTo,
                                 String appendedNotificationNumber) {
    }

    public record ImpactView(String lotNumber, String reason) {
    }

    public record ImpactsResponse(String recallNumber, List<ImpactView> impacts) {
    }

    public record NotificationView(String notificationNumber, String holder, BigDecimal quantity,
                                   String source, Instant createdAt) {
    }

    public record NotificationsResponse(String recallNumber, List<NotificationView> notifications) {
    }

    public record HolderResponseView(String holder, BigDecimal receivedQuantity,
                                     BigDecimal isolatedQuantity, BigDecimal consumedQuantity,
                                     BigDecimal transferredQuantity, BigDecimal mismatchQuantity,
                                     BigDecimal outstandingQuantity, boolean responded) {
    }

    public record HolderResponsesView(String recallNumber, List<HolderResponseView> holders) {
    }

    public record DiscrepancyView(String holder, BigDecimal receivedQuantity,
                                  BigDecimal accountedQuantity, BigDecimal differenceQuantity,
                                  BigDecimal mismatchQuantity) {
    }

    public record DiscrepanciesResponse(String recallNumber, List<DiscrepancyView> discrepancies) {
    }

    public record EffectivenessResponse(String recallNumber, String status, long statsVersion,
                                        long notifiedHolders, long respondedHolders,
                                        BigDecimal responseRate, BigDecimal dispatchedQuantity,
                                        BigDecimal isolatedQuantity, BigDecimal consumedQuantity,
                                        BigDecimal transferredQuantity, BigDecimal mismatchQuantity,
                                        BigDecimal unrecoveredQuantity, BigDecimal completionRate,
                                        String closeNumber, String approvedBy) {
    }

    public record GenealogyNode(String lotNumber, int depth) {
    }

    public record GenealogyResponse(String lotNumber, List<GenealogyNode> upstream,
                                    List<GenealogyNode> downstream) {
    }
}

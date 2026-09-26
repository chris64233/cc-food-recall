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

    public record GenealogyNode(String lotNumber, int depth) {
    }

    public record GenealogyResponse(String lotNumber, List<GenealogyNode> upstream,
                                    List<GenealogyNode> downstream) {
    }

    // ---------- 持有方 ----------

    public record RegisterHolderRequest(String holderCode, String name) {
    }

    public record HolderResponse(String holderCode, String name) {
    }

    // ---------- 去向（发货） ----------

    public record RegisterDestinationRequest(String destinationNumber, String lotNumber,
                                             String holderCode, BigDecimal quantity) {
    }

    public record DestinationResponse(String destinationNumber, String lotNumber,
                                      String holderCode, BigDecimal quantity) {
    }

    // ---------- 召回 ----------

    /** minResponseRate 可覆盖配置的默认响应门槛（0~1）。 */
    public record RecallRequest(String recallNumber, String lotNumber, String reason,
                                BigDecimal minResponseRate) {

        /** 兼容不指定门槛的调用，使用配置的默认响应门槛。 */
        public RecallRequest(String recallNumber, String lotNumber, String reason) {
            this(recallNumber, lotNumber, reason, null);
        }
    }

    public record RecallResponse(String recallNumber, String lotNumber, String reason,
                                 String status, long affectedLots,
                                 BigDecimal minResponseRate, long statisticsVersion) {
    }

    /** expectedVersion 为发起方读取统计时看到的版本号；与当前版本不一致时关闭决定失效。 */
    public record CloseRecallRequest(String closureNumber, String approver,
                                     Long expectedVersion) {
    }

    // ---------- 下游报告 ----------

    public record ReportItemRequest(String lotNumber, String disposition, BigDecimal quantity,
                                    String transferredToHolderCode) {
    }

    public record ReportRequest(String reportNumber, String holderCode,
                                List<ReportItemRequest> items) {
    }

    public record ReportItemView(String lotNumber, String disposition, BigDecimal quantity,
                                 String transferredToHolderCode) {
    }

    public record ReportResponse(String reportNumber, String holderCode, Instant createdAt,
                                 List<ReportItemView> items) {
    }

    // ---------- 查询 ----------

    public record ImpactView(String lotNumber, int batchNumber, BigDecimal rootFraction,
                             BigDecimal snapshotQuantity, Instant discoveredAt,
                             boolean quarantined) {
    }

    public record NotificationItemView(String lotNumber, BigDecimal quantity,
                                       BigDecimal rootFraction) {
    }

    public record NotificationView(String notificationNumber, String holderCode, String holderName,
                                   int batchNumber, String status, Instant createdAt,
                                   List<NotificationItemView> items) {
    }

    public record HolderResponseView(String holderCode, String holderName,
                                     BigDecimal notifiedQuantity,
                                     BigDecimal quarantinedQuantity,
                                     BigDecimal consumedQuantity,
                                     BigDecimal transferredQuantity,
                                     BigDecimal discrepancyQuantity,
                                     BigDecimal unreportedQuantity,
                                     BigDecimal responseRate, boolean fullyResponded) {
    }

    /** 数量差异：持有方自报数量不符，以及该持有方批次尚未报告的余量。 */
    public record DiscrepancyView(String holderCode, String holderName, String lotNumber,
                                  BigDecimal notifiedQuantity,
                                  BigDecimal quarantinedQuantity,
                                  BigDecimal consumedQuantity,
                                  BigDecimal transferredQuantity,
                                  BigDecimal reportedDiscrepancyQuantity,
                                  BigDecimal unreportedQuantity) {
    }

    public record ClosureView(String closureNumber, String approver, Instant closedAt,
                              long basedOnVersion, BigDecimal unrecoveredQuantity,
                              BigDecimal responseRate, BigDecimal completionRate) {
    }

    /** 召回完成率统计。 */
    public record EffectivenessResponse(String recallNumber, String status,
                                        BigDecimal minResponseRate, long statisticsVersion,
                                        long affectedLotCount, long notifiedHolderCount,
                                        BigDecimal affectedQuantity,
                                        BigDecimal quarantinedQuantity,
                                        BigDecimal consumedQuantity,
                                        BigDecimal transferredQuantity,
                                        BigDecimal discrepancyQuantity,
                                        BigDecimal unreportedQuantity,
                                        BigDecimal unrecoveredQuantity,
                                        BigDecimal responseRate, BigDecimal completionRate,
                                        ClosureView closure) {
    }
}

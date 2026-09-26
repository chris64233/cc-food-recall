package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.NotificationSource;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallNotification;
import com.chris64233.cc.foodrecall.domain.RecallReport;
import com.chris64233.cc.foodrecall.domain.ReportType;
import com.chris64233.cc.foodrecall.repository.RecallNotificationRepository;
import com.chris64233.cc.foodrecall.repository.RecallReportRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 召回有效性统计：由不可变的通知记录（持有方接收量）和下游报告聚合而成。
 * 受影响数量按通知记录去重累加，同一批次经多条谱系路径到达同一持有方时
 * 只按实际发货/转交记录计一次，不会按路径重复计算。
 */
@Component
public class RecallAnalytics {

    private final RecallNotificationRepository notificationRepository;
    private final RecallReportRepository reportRepository;

    public RecallAnalytics(RecallNotificationRepository notificationRepository,
                           RecallReportRepository reportRepository) {
        this.notificationRepository = notificationRepository;
        this.reportRepository = reportRepository;
    }

    public RecallStats compute(RecallEvent recall) {
        return compute(notificationRepository.findByRecallOrderByIdAsc(recall),
                reportRepository.findByRecall(recall));
    }

    RecallStats compute(List<RecallNotification> notifications, List<RecallReport> reports) {
        Map<String, HolderStats> holders = new TreeMap<>();
        BigDecimal dispatched = Quantities.ZERO;
        for (RecallNotification notification : notifications) {
            HolderStats stats = holders.computeIfAbsent(notification.getHolder(), h -> new HolderStats());
            stats.received = stats.received.add(notification.getQuantity());
            if (notification.getSource() == NotificationSource.SHIPMENT) {
                dispatched = dispatched.add(notification.getQuantity());
            }
        }
        BigDecimal isolated = Quantities.ZERO;
        BigDecimal consumed = Quantities.ZERO;
        BigDecimal transferred = Quantities.ZERO;
        BigDecimal mismatch = Quantities.ZERO;
        for (RecallReport report : reports) {
            HolderStats stats = holders.computeIfAbsent(report.getHolder(), h -> new HolderStats());
            stats.responded = true;
            switch (report.getType()) {
                case ISOLATED -> {
                    stats.isolated = stats.isolated.add(report.getQuantity());
                    isolated = isolated.add(report.getQuantity());
                }
                case CONSUMED -> {
                    stats.consumed = stats.consumed.add(report.getQuantity());
                    consumed = consumed.add(report.getQuantity());
                }
                case TRANSFERRED -> {
                    stats.transferred = stats.transferred.add(report.getQuantity());
                    transferred = transferred.add(report.getQuantity());
                }
                case MISMATCH -> {
                    stats.mismatch = stats.mismatch.add(report.getQuantity());
                    mismatch = mismatch.add(report.getQuantity());
                }
            }
        }
        long notified = holders.values().stream().filter(h -> h.received.signum() > 0).count();
        long responded = holders.values().stream().filter(h -> h.responded).count();
        // 尚未收回 = 首级发货总量 - 已隔离 - 已消费（转交数量会作为接收方的接收量继续跟踪，
        // 数量不符部分仍属未收回，单独作为差异披露）
        BigDecimal unrecovered = dispatched.subtract(isolated).subtract(consumed);
        BigDecimal completionRate = dispatched.signum() == 0
                ? BigDecimal.ONE.setScale(4, RoundingMode.UNNECESSARY)
                : isolated.add(consumed).divide(dispatched, 4, RoundingMode.HALF_UP);
        BigDecimal responseRate = notified == 0
                ? BigDecimal.ONE.setScale(4, RoundingMode.UNNECESSARY)
                : BigDecimal.valueOf(responded)
                        .divide(BigDecimal.valueOf(notified), 4, RoundingMode.HALF_UP);
        return new RecallStats(notified, responded, responseRate, dispatched,
                isolated, consumed, transferred, mismatch, unrecovered, completionRate,
                holders);
    }

    static final class HolderStats {
        BigDecimal received = Quantities.ZERO;
        BigDecimal isolated = Quantities.ZERO;
        BigDecimal consumed = Quantities.ZERO;
        BigDecimal transferred = Quantities.ZERO;
        BigDecimal mismatch = Quantities.ZERO;
        boolean responded;

        BigDecimal accounted() {
            return isolated.add(consumed).add(transferred).add(mismatch);
        }

        BigDecimal outstanding() {
            return received.subtract(accounted());
        }
    }

    record RecallStats(long notifiedHolders, long respondedHolders, BigDecimal responseRate,
                       BigDecimal dispatchedQuantity, BigDecimal isolatedQuantity,
                       BigDecimal consumedQuantity, BigDecimal transferredQuantity,
                       BigDecimal mismatchQuantity, BigDecimal unrecoveredQuantity,
                       BigDecimal completionRate, Map<String, HolderStats> holders) {
        RecallStats {
            holders = new LinkedHashMap<>(holders);
        }
    }
}

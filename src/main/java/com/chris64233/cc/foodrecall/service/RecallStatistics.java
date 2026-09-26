package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.DownstreamHolder;
import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.NotificationItem;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallNotification;
import com.chris64233.cc.foodrecall.domain.ReportItem;
import com.chris64233.cc.foodrecall.repository.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repository.RecallNotificationRepository;
import com.chris64233.cc.foodrecall.repository.ReportItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 召回有效性统计快照：从不可变的通知清单与下游报告汇总。
 *
 * <p>关键口径：
 * <ul>
 *   <li>初始影响量只统计初始通知批次（batchNumber=1）；转交产生的追加通知不再计入分母，
 *       同一批货在持有方链条上只计量一次；</li>
 *   <li>同一持有方+同一批次在多条通知中的数量聚合到一条，菱形汇合不重复计算；</li>
 *   <li>守恒恒等式：初始影响量 = 已隔离 + 已消费 + 数量不符 + 尚未报告（含在途）。</li>
 * </ul>
 */
@Component
public class RecallStatistics {

    static final int RATE_SCALE = 3;

    private final RecallImpactRepository impactRepository;
    private final RecallNotificationRepository notificationRepository;
    private final ReportItemRepository reportItemRepository;

    public RecallStatistics(RecallImpactRepository impactRepository,
                            RecallNotificationRepository notificationRepository,
                            ReportItemRepository reportItemRepository) {
        this.impactRepository = impactRepository;
        this.notificationRepository = notificationRepository;
        this.reportItemRepository = reportItemRepository;
    }

    public record Key(long holderId, long lotId) {
    }

    public static final class Snapshot {
        public RecallEvent recall;
        public List<RecallImpact> impacts = List.of();
        public List<RecallNotification> notifications = List.of();
        public Map<Long, Lot> lots = new LinkedHashMap<>();
        public Map<Long, DownstreamHolder> holders = new LinkedHashMap<>();
        /** 初始通知（batchNumber=1）按持有方+批次聚合的数量。 */
        public Map<Key, BigDecimal> initialReceived = new LinkedHashMap<>();
        /** 转交追加通知（batchNumber>1）按接收方+批次聚合的数量。 */
        public Map<Key, BigDecimal> transferredIn = new LinkedHashMap<>();
        /** 每个持有方+批次的归因比例（同一批次多路径汇合后只存一个值）。 */
        public Map<Key, BigDecimal> rootFractions = new LinkedHashMap<>();
        public Map<Key, BigDecimal> quarantined = new LinkedHashMap<>();
        public Map<Key, BigDecimal> consumed = new LinkedHashMap<>();
        public Map<Key, BigDecimal> transferred = new LinkedHashMap<>();
        public Map<Key, BigDecimal> discrepancy = new LinkedHashMap<>();

        public BigDecimal totalReceived(Key key) {
            return initialReceived.getOrDefault(key, BigDecimal.ZERO)
                    .add(transferredIn.getOrDefault(key, BigDecimal.ZERO));
        }

        public BigDecimal dispositionTotal(Key key) {
            return quarantined.getOrDefault(key, BigDecimal.ZERO)
                    .add(consumed.getOrDefault(key, BigDecimal.ZERO))
                    .add(transferred.getOrDefault(key, BigDecimal.ZERO))
                    .add(discrepancy.getOrDefault(key, BigDecimal.ZERO));
        }

        /** 尚未报告（含在途）= 接收 − 已处置；守恒校验通过时非负。 */
        public BigDecimal unreported(Key key) {
            return totalReceived(key).subtract(dispositionTotal(key));
        }
    }

    @Transactional(readOnly = true)
    public Snapshot build(RecallEvent recall) {
        Snapshot snapshot = new Snapshot();
        snapshot.recall = recall;
        snapshot.impacts = impactRepository.findByRecall(recall);
        snapshot.notifications = notificationRepository.findByRecall(recall);

        for (RecallNotification notification : snapshot.notifications) {
            snapshot.holders.putIfAbsent(notification.getHolder().getId(), notification.getHolder());
            for (NotificationItem item : notification.getItems()) {
                Lot lot = item.getLot();
                snapshot.lots.putIfAbsent(lot.getId(), lot);
                Key key = new Key(notification.getHolder().getId(), lot.getId());
                Map<Key, BigDecimal> target =
                        notification.getBatchNumber() == 1 ? snapshot.initialReceived : snapshot.transferredIn;
                target.merge(key, item.getAffectedQuantity(), BigDecimal::add);
                snapshot.rootFractions.putIfAbsent(key, item.getRootFraction());
            }
        }
        for (ReportItem item : reportItemRepository.findByRecall(recall)) {
            Key key = new Key(item.getReport().getHolder().getId(), item.getLot().getId());
            snapshot.holders.putIfAbsent(item.getReport().getHolder().getId(), item.getReport().getHolder());
            snapshot.lots.putIfAbsent(item.getLot().getId(), item.getLot());
            Map<Key, BigDecimal> target = switch (item.getDisposition()) {
                case QUARANTINED -> snapshot.quarantined;
                case CONSUMED -> snapshot.consumed;
                case TRANSFERRED -> snapshot.transferred;
                case DISCREPANCY -> snapshot.discrepancy;
            };
            target.merge(key, item.getQuantity(), BigDecimal::add);
        }
        return snapshot;
    }

    /** 初始受影响总量（分母，不含转交重复计量）。 */
    public static BigDecimal affectedQuantity(Snapshot snapshot) {
        return sum(snapshot.initialReceived);
    }

    public static BigDecimal quarantinedQuantity(Snapshot snapshot) {
        return sum(snapshot.quarantined);
    }

    public static BigDecimal consumedQuantity(Snapshot snapshot) {
        return sum(snapshot.consumed);
    }

    public static BigDecimal discrepancyQuantity(Snapshot snapshot) {
        return sum(snapshot.discrepancy);
    }

    /** 全部持有方链条上尚未报告/在途的数量合计。 */
    public static BigDecimal unreportedQuantity(Snapshot snapshot) {
        BigDecimal total = BigDecimal.ZERO;
        for (Key key : allKeys(snapshot)) {
            total = total.add(snapshot.unreported(key).max(BigDecimal.ZERO));
        }
        return total;
    }

    /** 尚未收回 = 已消费 + 数量不符 + 尚未报告 = 影响量 − 已隔离。 */
    public static BigDecimal unrecoveredQuantity(Snapshot snapshot) {
        return consumedQuantity(snapshot)
                .add(discrepancyQuantity(snapshot))
                .add(unreportedQuantity(snapshot));
    }

    public static BigDecimal responseRate(Snapshot snapshot) {
        BigDecimal affected = affectedQuantity(snapshot);
        if (affected.signum() == 0) {
            return BigDecimal.ONE;
        }
        BigDecimal responded = affected.subtract(unreportedQuantity(snapshot));
        return divide(responded, affected);
    }

    /** 召回完成率 = 已确认隔离收回量 / 初始影响量。 */
    public static BigDecimal completionRate(Snapshot snapshot) {
        BigDecimal affected = affectedQuantity(snapshot);
        if (affected.signum() == 0) {
            return BigDecimal.ONE;
        }
        return divide(quarantinedQuantity(snapshot), affected);
    }

    private static Iterable<Key> allKeys(Snapshot snapshot) {
        java.util.Set<Key> keys = new java.util.LinkedHashSet<>();
        keys.addAll(snapshot.initialReceived.keySet());
        keys.addAll(snapshot.transferredIn.keySet());
        keys.addAll(snapshot.quarantined.keySet());
        keys.addAll(snapshot.consumed.keySet());
        keys.addAll(snapshot.transferred.keySet());
        keys.addAll(snapshot.discrepancy.keySet());
        return keys;
    }

    private static BigDecimal sum(Map<Key, BigDecimal> values) {
        return values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, RATE_SCALE, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE).max(BigDecimal.ZERO);
    }
}

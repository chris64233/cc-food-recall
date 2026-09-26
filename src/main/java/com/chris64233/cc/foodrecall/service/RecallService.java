package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.DownstreamHolder;
import com.chris64233.cc.foodrecall.domain.DownstreamReport;
import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.LotDestination;
import com.chris64233.cc.foodrecall.domain.NotificationItem;
import com.chris64233.cc.foodrecall.domain.NotificationStatus;
import com.chris64233.cc.foodrecall.domain.RecallClosure;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallNotification;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import com.chris64233.cc.foodrecall.domain.ReportDisposition;
import com.chris64233.cc.foodrecall.domain.ReportItem;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.DownstreamHolderRepository;
import com.chris64233.cc.foodrecall.repository.DownstreamReportRepository;
import com.chris64233.cc.foodrecall.repository.LotDestinationRepository;
import com.chris64233.cc.foodrecall.repository.LotRepository;
import com.chris64233.cc.foodrecall.repository.RecallClosureRepository;
import com.chris64233.cc.foodrecall.repository.RecallEventRepository;
import com.chris64233.cc.foodrecall.repository.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repository.RecallNotificationRepository;
import com.chris64233.cc.foodrecall.repository.ReportItemRepository;
import com.chris64233.cc.foodrecall.service.RecallStatistics.Key;
import com.chris64233.cc.foodrecall.service.RecallStatistics.Snapshot;
import com.chris64233.cc.foodrecall.web.Dtos.CloseRecallRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ClosureView;
import com.chris64233.cc.foodrecall.web.Dtos.DiscrepancyView;
import com.chris64233.cc.foodrecall.web.Dtos.EffectivenessResponse;
import com.chris64233.cc.foodrecall.web.Dtos.HolderResponseView;
import com.chris64233.cc.foodrecall.web.Dtos.ImpactView;
import com.chris64233.cc.foodrecall.web.Dtos.NotificationItemView;
import com.chris64233.cc.foodrecall.web.Dtos.NotificationView;
import com.chris64233.cc.foodrecall.web.Dtos.RecallRequest;
import com.chris64233.cc.foodrecall.web.Dtos.RecallResponse;
import com.chris64233.cc.foodrecall.web.Dtos.ReportItemRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ReportItemView;
import com.chris64233.cc.foodrecall.web.Dtos.ReportRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ReportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecallService {

    private final LotRepository lotRepository;
    private final RecallEventRepository recallRepository;
    private final RecallImpactRepository impactRepository;
    private final RecallNotificationRepository notificationRepository;
    private final DownstreamHolderRepository holderRepository;
    private final DownstreamReportRepository reportRepository;
    private final ReportItemRepository reportItemRepository;
    private final RecallClosureRepository closureRepository;
    private final LotDestinationRepository destinationRepository;
    private final GenealogyCalculator genealogyCalculator;
    private final RecallStatistics statistics;
    private final RecallProperties properties;
    private final Transactions transactions;

    public RecallService(LotRepository lotRepository,
                         RecallEventRepository recallRepository,
                         RecallImpactRepository impactRepository,
                         RecallNotificationRepository notificationRepository,
                         DownstreamHolderRepository holderRepository,
                         DownstreamReportRepository reportRepository,
                         ReportItemRepository reportItemRepository,
                         RecallClosureRepository closureRepository,
                         LotDestinationRepository destinationRepository,
                         GenealogyCalculator genealogyCalculator,
                         RecallStatistics statistics,
                         RecallProperties properties,
                         Transactions transactions) {
        this.lotRepository = lotRepository;
        this.recallRepository = recallRepository;
        this.impactRepository = impactRepository;
        this.notificationRepository = notificationRepository;
        this.holderRepository = holderRepository;
        this.reportRepository = reportRepository;
        this.reportItemRepository = reportItemRepository;
        this.closureRepository = closureRepository;
        this.destinationRepository = destinationRepository;
        this.genealogyCalculator = genealogyCalculator;
        this.statistics = statistics;
        this.properties = properties;
        this.transactions = transactions;
    }

    // ============================ 发起召回 ============================

    public RecallResponse initiate(RecallRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        String recallNumber = LotService.requireText(request.recallNumber(), "召回事件号不能为空");
        String lotNumber = LotService.requireText(request.lotNumber(), "批次号不能为空");
        String reason = LotService.requireText(request.reason(), "召回原因不能为空");
        BigDecimal threshold = resolveThreshold(request.minResponseRate());
        return transactions.idempotent(() -> doInitiate(recallNumber, lotNumber, reason, threshold));
    }

    private RecallResponse doInitiate(String recallNumber, String lotNumber, String reason,
                                      BigDecimal threshold) {
        var existing = recallRepository.findByRecallNumber(recallNumber);
        if (existing.isPresent()) {
            RecallEvent stored = existing.get();
            if (!stored.getRootLot().getLotNumber().equals(lotNumber) || !stored.getReason().equals(reason)
                    || stored.getMinResponseRate().compareTo(threshold) != 0) {
                throw ApiException.conflict("召回事件号已存在且内容不一致: " + recallNumber);
            }
            return toResponse(stored);
        }

        Lot root = lotRepository.findForUpdateByLotNumber(lotNumber)
                .orElseThrow(() -> ApiException.notFound("批次不存在: " + lotNumber));
        RecallEvent recall = new RecallEvent(recallNumber, root, reason, threshold, Instant.now());
        recall = recallRepository.save(recall);

        // 根据当时批次谱系计算根批次归因比例（多路径汇合比例相加，数量不重复计量）。
        Map<Lot, BigDecimal> fractions = genealogyCalculator.rootFractions(root);

        // 初始影响清单：快照时沿谱系锁定所有受影响批次。
        List<Long> lotIds = fractions.keySet().stream().map(Lot::getId).sorted().toList();
        List<Lot> locked = lotRepository.findForUpdateByIdIn(lotIds);
        Instant now = Instant.now();
        int batchNumber = recall.nextBatchNumber();
        for (Lot lot : locked) {
            BigDecimal fraction = fractions.getOrDefault(lot, BigDecimal.ONE);
            impactRepository.save(new RecallImpact(recall, lot, reason, batchNumber,
                    lot.getQuantity(), fraction, now));
            lot.setQuarantined(true);
        }

        // 根据已知去向为每个下游持有方创建初始通知任务（按持有方+批次聚合，天然去重）。
        Map<Key, BigDecimal> received = aggregateDestinations(locked);
        Map<Long, BigDecimal> fractionByLot = new LinkedHashMap<>();
        fractions.forEach((lot, fraction) -> fractionByLot.put(lot.getId(), fraction));
        issueNotifications(recall, received, fractionByLot, batchNumber, now);

        return toResponse(recall);
    }

    /** 把工厂台账中已发往下游的数量按持有方+批次聚合（同一批次多条发货单合并计一条）。 */
    private Map<Key, BigDecimal> aggregateDestinations(List<Lot> lots) {
        Map<Key, BigDecimal> result = new LinkedHashMap<>();
        for (LotDestination destination : destinationRepository.findByLotIn(lots)) {
            Key key = new Key(destination.getHolder().getId(), destination.getLot().getId());
            result.merge(key, destination.getQuantity(), BigDecimal::add);
        }
        return result;
    }

    /**
     * 为一批新发现去向创建通知记录。通知号由召回号+批次号+持有方编码确定性生成，
     * 并发唯一键冲突时经事务重试走幂等路径；原通知记录永不修改。
     */
    private void issueNotifications(RecallEvent recall, Map<Key, BigDecimal> received,
                                    Map<Long, BigDecimal> fractionByLot, int batchNumber, Instant now) {
        Map<Long, RecallNotification> byHolder = new LinkedHashMap<>();
        for (Map.Entry<Key, BigDecimal> entry : received.entrySet()) {
            Key key = entry.getKey();
            RecallNotification notification = byHolder.computeIfAbsent(key.holderId(), holderId -> {
                DownstreamHolder holder = holderRepository.findById(holderId).orElseThrow();
                String number =
                        recall.getRecallNumber() + "-N" + batchNumber + "-" + holder.getHolderCode();
                return notificationRepository.save(
                        new RecallNotification(number, recall, holder, batchNumber, now));
            });
            Lot lot = lotRepository.findById(key.lotId()).orElseThrow();
            BigDecimal fraction = fractionByLot.getOrDefault(lot.getId(), BigDecimal.ONE);
            notification.getItems().add(new NotificationItem(notification, lot, entry.getValue(), fraction));
        }
    }

    // ============================ 下游报告 ============================

    public ReportResponse report(String recallNumber, ReportRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        String reportNumber = LotService.requireText(request.reportNumber(), "报告号不能为空");
        String holderCode = LotService.requireText(request.holderCode(), "持有方编码不能为空");
        List<ValidatedItem> items = validateItems(request.items());
        return transactions.idempotent(() -> doReport(recallNumber, reportNumber, holderCode, items));
    }

    private List<ValidatedItem> validateItems(List<ReportItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest("报告明细不能为空");
        }
        List<ValidatedItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ReportItemRequest item : items) {
            String lotNumber = LotService.requireText(item == null ? null : item.lotNumber(),
                    "报告批次号不能为空");
            BigDecimal quantity = Quantities.requirePositive(item.quantity(), "报告数量");
            ReportDisposition disposition;
            try {
                disposition = ReportDisposition.valueOf(
                        LotService.requireText(item.disposition(), "处置类型不能为空"));
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("未知处置类型: " + item.disposition()
                        + "（支持 QUARANTINED/CONSUMED/TRANSFERRED/DISCREPANCY）");
            }
            String transferTarget = item.transferredToHolderCode();
            if (disposition == ReportDisposition.TRANSFERRED) {
                transferTarget = LotService.requireText(transferTarget,
                        "已转交必须填写接收持有方编码");
            } else if (transferTarget != null && !transferTarget.isBlank()) {
                throw ApiException.badRequest("仅已转交(TRANSFERRED)报告可以填写接收持有方");
            }
            String dedupe = lotNumber + "|" + disposition + "|" + String.valueOf(transferTarget);
            if (!seen.add(dedupe)) {
                throw ApiException.badRequest("同一批次同一处置类型在一份报告中不能重复: " + lotNumber);
            }
            result.add(new ValidatedItem(lotNumber, disposition, quantity, transferTarget));
        }
        return result;
    }

    private ReportResponse doReport(String recallNumber, String reportNumber, String holderCode,
                                    List<ValidatedItem> items) {
        DownstreamHolder holder = holderRepository.findByHolderCode(holderCode)
                .orElseThrow(() -> ApiException.notFound("持有方不存在: " + holderCode));

        // 统一加锁顺序：先批次行（按 id 排序），再召回行，与生产转换保持一致以避免死锁。
        List<String> lotNumbers = items.stream().map(ValidatedItem::lotNumber)
                .distinct().sorted().toList();
        List<Lot> requestedLots = lotRepository.findForUpdateByLotNumberIn(lotNumbers);
        if (requestedLots.size() != lotNumbers.size()) {
            Set<String> found = requestedLots.stream().map(Lot::getLotNumber)
                    .collect(java.util.stream.Collectors.toSet());
            List<String> missing = lotNumbers.stream().filter(n -> !found.contains(n)).toList();
            throw ApiException.notFound("批次不存在: " + String.join(", ", missing));
        }

        RecallEvent recall = recallRepository.findForUpdateByRecallNumber(recallNumber)
                .orElseThrow(() -> ApiException.notFound("召回事件不存在: " + recallNumber));
        if (recall.getStatus() != RecallStatus.OPEN) {
            throw ApiException.conflict("召回已关闭，不能再提交下游报告: " + recallNumber);
        }

        var existingReport = reportRepository.findByReportNumber(reportNumber);
        if (existingReport.isPresent()) {
            return idempotentReport(existingReport.get(), recall, holder, items);
        }

        Snapshot snapshot = statistics.build(recall);

        // 按批次聚合本次报告数量，叠加历史报告后必须满足守恒：累计处置 ≤ 接收量（含转入）。
        Map<Long, BigDecimal> newByLot = new LinkedHashMap<>();
        // 转交：targetId -> (lotId -> quantity)，同一接收方的多批次合并为一条追加通知。
        Map<Long, Map<Long, BigDecimal>> transferAggregated = new LinkedHashMap<>();
        Map<Long, DownstreamHolder> targets = new LinkedHashMap<>();

        DownstreamReport report = new DownstreamReport(reportNumber, recall, holder, Instant.now());
        report = reportRepository.save(report);

        for (ValidatedItem item : items) {
            Lot lot = requestedLots.stream()
                    .filter(l -> l.getLotNumber().equals(item.lotNumber()))
                    .findFirst().orElseThrow();
            if (impactRepository.findOpenByLot(lot, RecallStatus.OPEN).stream()
                    .noneMatch(impact -> impact.getRecall().getId().equals(recall.getId()))) {
                throw ApiException.conflict("批次不在召回影响清单内: " + item.lotNumber());
            }
            Key key = new Key(holder.getId(), lot.getId());
            if (!snapshot.initialReceived.containsKey(key)
                    && !snapshot.transferredIn.containsKey(key)) {
                throw ApiException.conflict(
                        "持有方未接收到该批次，不能报告: " + holderCode + " / " + item.lotNumber());
            }
            newByLot.merge(lot.getId(), item.quantity(), BigDecimal::add);

            DownstreamHolder target = null;
            if (item.disposition() == ReportDisposition.TRANSFERRED) {
                target = holderRepository.findByHolderCode(item.transferTarget())
                        .orElseThrow(() -> ApiException.notFound(
                                "转交接收持有方不存在: " + item.transferTarget()));
                if (target.getId().equals(holder.getId())) {
                    throw ApiException.badRequest("不能转交给持有方自己: " + holderCode);
                }
                targets.put(target.getId(), target);
                transferAggregated
                        .computeIfAbsent(target.getId(), id -> new LinkedHashMap<>())
                        .merge(lot.getId(), item.quantity(), BigDecimal::add);
            }
            reportItemRepository.save(new ReportItem(report, lot, item.disposition(),
                    item.quantity(), target));
        }

        // 守恒：每个批次“历史已报 + 本次报告”不得超过该持有方对该批次的接收量。
        for (Map.Entry<Long, BigDecimal> entry : newByLot.entrySet()) {
            Key key = new Key(holder.getId(), entry.getKey());
            BigDecimal alreadyReported = snapshot.dispositionTotal(key);
            BigDecimal received = snapshot.totalReceived(key);
            if (alreadyReported.add(entry.getValue()).compareTo(received) > 0) {
                Lot lot = snapshot.lots.get(entry.getKey());
                throw ApiException.conflict("报告数量不守恒: 批次 " + lot.getLotNumber()
                        + " 接收 " + received + " 累计已报 "
                        + alreadyReported.add(entry.getValue()));
            }
        }

        // 已转交：为接收方创建追加通知（新通知批次），原通知记录保持不变。
        if (!transferAggregated.isEmpty()) {
            appendTransferNotifications(recall, holder, snapshot, transferAggregated, targets);
        }

        // 该持有方所有批次都已交代清楚时，将其通知任务置为已响应。
        markHolderRespondedIfComplete(holder, snapshot, newByLot);

        recall.bumpStatistics();
        return toReportResponse(report);
    }

    private ReportResponse idempotentReport(DownstreamReport stored, RecallEvent recall,
                                            DownstreamHolder holder, List<ValidatedItem> items) {
        if (!stored.getRecall().getId().equals(recall.getId())
                || !stored.getHolder().getId().equals(holder.getId())) {
            throw ApiException.conflict("报告号已存在且归属不一致: " + stored.getReportNumber());
        }
        if (stored.getItems().size() != items.size()) {
            throw ApiException.conflict("报告号已存在且内容不一致: " + stored.getReportNumber());
        }
        for (ValidatedItem item : items) {
            boolean match = stored.getItems().stream().anyMatch(saved ->
                    saved.getLot().getLotNumber().equals(item.lotNumber())
                            && saved.getDisposition() == item.disposition()
                            && saved.getQuantity().compareTo(item.quantity()) == 0
                            && ((saved.getTransferredTo() == null && item.transferTarget() == null)
                            || (saved.getTransferredTo() != null
                            && saved.getTransferredTo().getHolderCode().equals(item.transferTarget()))));
            if (!match) {
                throw ApiException.conflict("报告号已存在且内容不一致: " + stored.getReportNumber());
            }
        }
        return toReportResponse(stored);
    }

    private void appendTransferNotifications(RecallEvent recall, DownstreamHolder from,
                                             Snapshot snapshot,
                                             Map<Long, Map<Long, BigDecimal>> transferAggregated,
                                             Map<Long, DownstreamHolder> targets) {
        int batchNumber = recall.nextBatchNumber();
        Instant now = Instant.now();
        for (Map.Entry<Long, Map<Long, BigDecimal>> entry : transferAggregated.entrySet()) {
            DownstreamHolder target = targets.get(entry.getKey());
            String number =
                    recall.getRecallNumber() + "-N" + batchNumber + "-" + target.getHolderCode();
            RecallNotification notification =
                    new RecallNotification(number, recall, target, batchNumber, now);
            notification = notificationRepository.save(notification);
            for (Map.Entry<Long, BigDecimal> lotEntry : entry.getValue().entrySet()) {
                Lot lot = lotRepository.findById(lotEntry.getKey()).orElseThrow();
                BigDecimal fraction = snapshot.rootFractions.getOrDefault(
                        new Key(from.getId(), lot.getId()), BigDecimal.ONE);
                notification.getItems()
                        .add(new NotificationItem(notification, lot, lotEntry.getValue(), fraction));
            }
        }
    }

    private void markHolderRespondedIfComplete(DownstreamHolder holder, Snapshot snapshot,
                                               Map<Long, BigDecimal> newByLot) {
        for (RecallNotification notification : snapshot.notifications) {
            if (notification.getHolder().getId() != holder.getId()
                    || notification.getStatus() == NotificationStatus.RESPONDED) {
                continue;
            }
            boolean complete = true;
            for (NotificationItem item : notification.getItems()) {
                Key key = new Key(holder.getId(), item.getLot().getId());
                BigDecimal total = snapshot.dispositionTotal(key)
                        .add(newByLot.getOrDefault(key.lotId(), Quantities.ZERO));
                if (total.compareTo(snapshot.totalReceived(key)) < 0) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                notification.markResponded();
            }
        }
    }

    private ReportResponse toReportResponse(DownstreamReport report) {
        List<ReportItemView> items = report.getItems().stream()
                .map(item -> new ReportItemView(item.getLot().getLotNumber(),
                        item.getDisposition().name(), item.getQuantity(),
                        item.getTransferredTo() == null ? null
                                : item.getTransferredTo().getHolderCode()))
                .toList();
        return new ReportResponse(report.getReportNumber(), report.getHolder().getHolderCode(),
                report.getCreatedAt(), items);
    }

    private record ValidatedItem(String lotNumber, ReportDisposition disposition,
                                 BigDecimal quantity, String transferTarget) {
    }

    // ============================ 关闭 ============================

    public RecallResponse close(String recallNumber, CloseRecallRequest request) {
        String number = LotService.requireText(recallNumber, "召回事件号不能为空");
        // 无请求体（旧式调用）使用默认关闭号与 system 批准人；
        // 一旦提供了请求体，批准人必须显式填写。
        String closureNumber;
        String approver;
        Long expectedVersion;
        if (request == null) {
            closureNumber = number + "-CLS";
            approver = "system";
            expectedVersion = null;
        } else {
            closureNumber = request.closureNumber() == null || request.closureNumber().isBlank()
                    ? number + "-CLS" : request.closureNumber().trim();
            approver = LotService.requireText(request.approver(), "批准人不能为空");
            expectedVersion = request.expectedVersion();
        }
        return transactions.idempotent(
                () -> doClose(number, closureNumber, approver, expectedVersion));
    }

    private RecallResponse doClose(String recallNumber, String closureNumber, String approver,
                                   Long expectedVersion) {
        RecallEvent recall = recallRepository.findForUpdateByRecallNumber(recallNumber)
                .orElseThrow(() -> ApiException.notFound("召回事件不存在: " + recallNumber));
        if (recall.getStatus() == RecallStatus.CLOSED) {
            RecallClosure existing = closureRepository.findByRecall(recall).orElseThrow();
            if (!existing.getClosureNumber().equals(closureNumber)) {
                throw ApiException.conflict("召回已关闭，关闭号不一致: " + closureNumber
                        + "，已有关闭号 " + existing.getClosureNumber());
            }
            if (!existing.getApprover().equals(approver)) {
                throw ApiException.conflict("关闭号已存在且批准人不一致: " + closureNumber);
            }
            return toResponse(recall);
        }

        if (expectedVersion != null && expectedVersion != recall.getStatisticsVersion()) {
            throw ApiException.conflict("关闭决定基于过期统计（期望版本 " + expectedVersion
                    + "，当前版本 " + recall.getStatisticsVersion() + "），请重新确认后再关闭");
        }

        Snapshot snapshot = statistics.build(recall);
        BigDecimal responseRate = RecallStatistics.responseRate(snapshot);
        if (responseRate.compareTo(recall.getMinResponseRate()) < 0) {
            throw ApiException.conflict("响应率 " + responseRate
                    + " 未达到关闭门槛 " + recall.getMinResponseRate());
        }

        BigDecimal affected = RecallStatistics.affectedQuantity(snapshot);
        BigDecimal unrecovered = RecallStatistics.unrecoveredQuantity(snapshot);
        BigDecimal completion = RecallStatistics.completionRate(snapshot);
        Instant now = Instant.now();
        closureRepository.save(new RecallClosure(closureNumber, recall, approver,
                recall.getStatisticsVersion(), affected, unrecovered, responseRate, completion, now));
        recall.close(now);

        // 隔离标记释放需要更新批次行，为避免“召回锁→批次锁”与报告/转换的“批次锁→召回锁”
        // 形成反向加锁，放到关闭事务提交后的新事务中执行。
        List<Long> impactLotIds = impactRepository.findByRecall(recall).stream()
                .map(impact -> impact.getLot().getId()).sorted().toList();
        transactions.afterCommit(() -> releaseQuarantineIfNoOpenRecall(impactLotIds));
        return toResponse(recall);
    }

    private void releaseQuarantineIfNoOpenRecall(List<Long> lotIds) {
        if (lotIds.isEmpty()) {
            return;
        }
        for (Lot lot : lotRepository.findForUpdateByIdIn(lotIds)) {
            lot.setQuarantined(impactRepository.existsOpenByLot(lot, RecallStatus.OPEN));
        }
    }

    // ============================ 查询 ============================

    @Transactional(readOnly = true)
    public RecallResponse getRecall(String recallNumber) {
        return toResponse(requireRecall(recallNumber));
    }

    @Transactional(readOnly = true)
    public List<ImpactView> getImpacts(String recallNumber) {
        RecallEvent recall = requireRecall(recallNumber);
        return impactRepository.findByRecall(recall).stream()
                .map(impact -> new ImpactView(impact.getLot().getLotNumber(),
                        impact.getBatchNumber(), impact.getRootFraction(),
                        impact.getSnapshotQuantity(), impact.getDiscoveredAt(),
                        impact.getLot().isQuarantined()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationView> getNotifications(String recallNumber) {
        RecallEvent recall = requireRecall(recallNumber);
        return notificationRepository.findByRecall(recall).stream()
                .map(notification -> {
                    List<NotificationItemView> items = notification.getItems().stream()
                            .map(item -> new NotificationItemView(item.getLot().getLotNumber(),
                                    item.getAffectedQuantity(), item.getRootFraction()))
                            .toList();
                    return new NotificationView(notification.getNotificationNumber(),
                            notification.getHolder().getHolderCode(),
                            notification.getHolder().getName(),
                            notification.getBatchNumber(), notification.getStatus().name(),
                            notification.getCreatedAt(), items);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HolderResponseView> getHolderResponses(String recallNumber) {
        RecallEvent recall = requireRecall(recallNumber);
        Snapshot snapshot = statistics.build(recall);
        List<HolderResponseView> result = new ArrayList<>();
        for (DownstreamHolder holder : snapshot.holders.values()) {
            BigDecimal notified = BigDecimal.ZERO;
            BigDecimal quarantined = BigDecimal.ZERO;
            BigDecimal consumed = BigDecimal.ZERO;
            BigDecimal transferred = BigDecimal.ZERO;
            BigDecimal discrepancy = BigDecimal.ZERO;
            BigDecimal unreported = BigDecimal.ZERO;
            for (Key key : keysOfHolder(snapshot, holder.getId())) {
                notified = notified.add(snapshot.totalReceived(key));
                quarantined = quarantined.add(snapshot.quarantined.getOrDefault(key, Quantities.ZERO));
                consumed = consumed.add(snapshot.consumed.getOrDefault(key, Quantities.ZERO));
                transferred = transferred.add(snapshot.transferred.getOrDefault(key, Quantities.ZERO));
                discrepancy = discrepancy.add(snapshot.discrepancy.getOrDefault(key, Quantities.ZERO));
                unreported = unreported.add(snapshot.unreported(key).max(BigDecimal.ZERO));
            }
            BigDecimal rate = notified.signum() == 0 ? BigDecimal.ONE
                    : notified.subtract(unreported).divide(notified,
                            RecallStatistics.RATE_SCALE, RoundingMode.HALF_UP)
                            .min(BigDecimal.ONE).max(BigDecimal.ZERO);
            result.add(new HolderResponseView(holder.getHolderCode(), holder.getName(),
                    notified, quarantined, consumed, transferred, discrepancy, unreported,
                    rate, unreported.signum() == 0));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<DiscrepancyView> getDiscrepancies(String recallNumber) {
        RecallEvent recall = requireRecall(recallNumber);
        Snapshot snapshot = statistics.build(recall);
        List<DiscrepancyView> result = new ArrayList<>();
        for (Key key : allKeys(snapshot)) {
            BigDecimal reportedDiscrepancy = snapshot.discrepancy.getOrDefault(key, Quantities.ZERO);
            if (reportedDiscrepancy.signum() == 0) {
                continue;
            }
            DownstreamHolder holder = snapshot.holders.get(key.holderId());
            Lot lot = snapshot.lots.get(key.lotId());
            result.add(new DiscrepancyView(holder.getHolderCode(), holder.getName(),
                    lot.getLotNumber(), snapshot.totalReceived(key),
                    snapshot.quarantined.getOrDefault(key, Quantities.ZERO),
                    snapshot.consumed.getOrDefault(key, Quantities.ZERO),
                    snapshot.transferred.getOrDefault(key, Quantities.ZERO),
                    reportedDiscrepancy,
                    snapshot.totalReceived(key).subtract(snapshot.dispositionTotal(key))));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public EffectivenessResponse getEffectiveness(String recallNumber) {
        RecallEvent recall = requireRecall(recallNumber);
        Snapshot snapshot = statistics.build(recall);
        long notifiedHolders = snapshot.initialReceived.keySet().stream()
                .map(Key::holderId).distinct().count();
        RecallClosure closure = closureRepository.findByRecall(recall).orElse(null);
        ClosureView closureView = closure == null ? null
                : new ClosureView(closure.getClosureNumber(), closure.getApprover(),
                        closure.getCreatedAt(), closure.getBasedOnVersion(),
                        closure.getUnrecoveredQuantity(), closure.getResponseRate(),
                        closure.getCompletionRate());
        return new EffectivenessResponse(recall.getRecallNumber(), recall.getStatus().name(),
                recall.getMinResponseRate(), recall.getStatisticsVersion(),
                snapshot.impacts.size(), notifiedHolders,
                RecallStatistics.affectedQuantity(snapshot),
                RecallStatistics.quarantinedQuantity(snapshot),
                RecallStatistics.consumedQuantity(snapshot),
                sum(snapshot.transferred),
                RecallStatistics.discrepancyQuantity(snapshot),
                RecallStatistics.unreportedQuantity(snapshot),
                RecallStatistics.unrecoveredQuantity(snapshot),
                RecallStatistics.responseRate(snapshot),
                RecallStatistics.completionRate(snapshot), closureView);
    }

    // ============================ 转换追加 ============================

    /**
     * 新生产转换发生时为进行中的召回追加影响：新后代批次追加到清单（新批次号），
     * 并为其已知去向创建追加通知；推进统计版本使任何进行中的关闭决定失效。
     * 在持有召回行锁的情况下调用。
     */
    public void appendNewOutputs(RecallEvent recall, Map<Lot, BigDecimal> outputFractions, Instant now) {
        int batchNumber = recall.nextBatchNumber();
        for (Map.Entry<Lot, BigDecimal> entry : outputFractions.entrySet()) {
            Lot output = entry.getKey();
            impactRepository.save(new RecallImpact(recall, output, recall.getReason(),
                    batchNumber, output.getQuantity(), entry.getValue(), now));
            output.setQuarantined(true);
        }
        List<Lot> outputs = new ArrayList<>(outputFractions.keySet());
        Map<Key, BigDecimal> received = aggregateDestinations(outputs);
        if (!received.isEmpty()) {
            Map<Long, BigDecimal> fractionByLot = new LinkedHashMap<>();
            outputFractions.forEach((lot, fraction) -> fractionByLot.put(lot.getId(), fraction));
            issueNotifications(recall, received, fractionByLot, batchNumber, now);
        }
        recall.bumpStatistics();
    }

    // ============================ 共享 ============================

    private RecallEvent requireRecall(String recallNumber) {
        String number = LotService.requireText(recallNumber, "召回事件号不能为空");
        return recallRepository.findByRecallNumber(number)
                .orElseThrow(() -> ApiException.notFound("召回事件不存在: " + number));
    }

    private List<Key> keysOfHolder(Snapshot snapshot, long holderId) {
        List<Key> result = new ArrayList<>();
        for (Key key : allKeys(snapshot)) {
            if (key.holderId() == holderId) {
                result.add(key);
            }
        }
        return result;
    }

    private Set<Key> allKeys(Snapshot snapshot) {
        Set<Key> keys = new java.util.LinkedHashSet<>();
        keys.addAll(snapshot.initialReceived.keySet());
        keys.addAll(snapshot.transferredIn.keySet());
        keys.addAll(snapshot.quarantined.keySet());
        keys.addAll(snapshot.consumed.keySet());
        keys.addAll(snapshot.transferred.keySet());
        keys.addAll(snapshot.discrepancy.keySet());
        return keys;
    }

    private BigDecimal sum(Map<Key, BigDecimal> values) {
        return values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private RecallResponse toResponse(RecallEvent recall) {
        long affected = impactRepository.countByRecall(recall);
        return new RecallResponse(recall.getRecallNumber(), recall.getRootLot().getLotNumber(),
                recall.getReason(), recall.getStatus().name(), affected,
                recall.getMinResponseRate(), recall.getStatisticsVersion());
    }

    private BigDecimal resolveThreshold(BigDecimal requested) {
        BigDecimal threshold = requested == null
                ? properties.getMinResponseRate() : Quantities.normalize(requested);
        if (threshold.signum() < 0 || threshold.compareTo(BigDecimal.ONE) > 0) {
            throw ApiException.badRequest("响应门槛必须在 0~1 之间");
        }
        return threshold;
    }
}

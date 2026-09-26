package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.NotificationSource;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallNotification;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import com.chris64233.cc.foodrecall.domain.Shipment;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.LotRepository;
import com.chris64233.cc.foodrecall.repository.RecallEventRepository;
import com.chris64233.cc.foodrecall.repository.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repository.RecallNotificationRepository;
import com.chris64233.cc.foodrecall.repository.ShipmentRepository;
import com.chris64233.cc.foodrecall.repository.TransformationOutputRepository;
import com.chris64233.cc.foodrecall.web.Dtos.CloseRequest;
import com.chris64233.cc.foodrecall.web.Dtos.RecallRequest;
import com.chris64233.cc.foodrecall.web.Dtos.RecallResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class RecallService {

    private final LotRepository lotRepository;
    private final RecallEventRepository recallRepository;
    private final RecallImpactRepository impactRepository;
    private final TransformationOutputRepository outputRepository;
    private final ShipmentRepository shipmentRepository;
    private final RecallNotificationRepository notificationRepository;
    private final RecallAnalytics analytics;
    private final Transactions transactions;
    private final BigDecimal requiredResponseRatio;

    @PersistenceContext
    private EntityManager entityManager;

    public RecallService(LotRepository lotRepository,
                         RecallEventRepository recallRepository,
                         RecallImpactRepository impactRepository,
                         TransformationOutputRepository outputRepository,
                         ShipmentRepository shipmentRepository,
                         RecallNotificationRepository notificationRepository,
                         RecallAnalytics analytics,
                         Transactions transactions,
                         @Value("${app.recall.required-response-ratio:1.0}")
                         BigDecimal requiredResponseRatio) {
        this.lotRepository = lotRepository;
        this.recallRepository = recallRepository;
        this.impactRepository = impactRepository;
        this.outputRepository = outputRepository;
        this.shipmentRepository = shipmentRepository;
        this.notificationRepository = notificationRepository;
        this.analytics = analytics;
        this.transactions = transactions;
        this.requiredResponseRatio = requiredResponseRatio;
    }

    public RecallResponse initiate(RecallRequest request) {
        String recallNumber = LotService.requireText(request == null ? null : request.recallNumber(),
                "召回事件号不能为空");
        String lotNumber = LotService.requireText(request == null ? null : request.lotNumber(),
                "批次号不能为空");
        String reason = LotService.requireText(request == null ? null : request.reason(),
                "召回原因不能为空");
        return transactions.idempotent(() -> doInitiate(recallNumber, lotNumber, reason));
    }

    private RecallResponse doInitiate(String recallNumber, String lotNumber, String reason) {
        var existing = recallRepository.findByRecallNumber(recallNumber);
        if (existing.isPresent()) {
            RecallEvent stored = existing.get();
            if (!stored.getRootLot().getLotNumber().equals(lotNumber) || !stored.getReason().equals(reason)) {
                throw ApiException.conflict("召回事件号已存在且内容不一致: " + recallNumber);
            }
            return toResponse(stored);
        }

        Lot root = lotRepository.findForUpdateByLotNumber(lotNumber)
                .orElseThrow(() -> ApiException.notFound("批次不存在: " + lotNumber));
        RecallEvent recall = recallRepository.save(new RecallEvent(recallNumber, root, reason, Instant.now()));

        // 基于发起时刻的批次谱系生成不可变的初始影响清单
        List<Lot> impacted = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        List<Lot> frontier = List.of(root);
        while (!frontier.isEmpty()) {
            List<Long> ids = frontier.stream().map(Lot::getId).sorted().toList();
            List<Lot> locked = lotRepository.findForUpdateByIdIn(ids);
            List<Lot> next = new ArrayList<>();
            for (Lot lot : locked) {
                if (!visited.add(lot.getId())) {
                    continue;
                }
                impactRepository.save(new RecallImpact(recall, lot, reason));
                lot.setQuarantined(true);
                impacted.add(lot);
            }
            for (Lot child : outputRepository.findChildLotsOf(locked)) {
                if (!visited.contains(child.getId())) {
                    next.add(child);
                }
            }
            frontier = next;
        }

        // 按发起时刻的已知去向为每个下游持有方创建通知任务；
        // 同一持有方多条发货记录合并为一条初始通知，避免按谱系路径重复计量
        Map<String, BigDecimal> receivedByHolder = new TreeMap<>();
        for (Shipment shipment : shipmentRepository.findByLotIn(impacted)) {
            receivedByHolder.merge(shipment.getHolder(), shipment.getQuantity(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> entry : receivedByHolder.entrySet()) {
            notificationRepository.save(new RecallNotification(recall.nextNotificationNumber(),
                    recall, entry.getKey(), entry.getValue(), NotificationSource.SHIPMENT, Instant.now()));
        }
        return toResponse(recall);
    }

    public RecallResponse close(String recallNumber, CloseRequest request) {
        String number = LotService.requireText(recallNumber, "召回事件号不能为空");
        String closeNumber = LotService.requireText(request == null ? null : request.closeNumber(),
                "关闭号不能为空");
        String approvedBy = LotService.requireText(request == null ? null : request.approvedBy(),
                "批准人不能为空");
        Long expectedStatsVersion = request == null ? null : request.expectedStatsVersion();
        return transactions.idempotent(
                () -> doClose(number, closeNumber, approvedBy, expectedStatsVersion));
    }

    private RecallResponse doClose(String recallNumber, String closeNumber, String approvedBy,
                                   Long expectedStatsVersion) {
        RecallEvent snapshot = recallRepository.findByRecallNumber(recallNumber)
                .orElseThrow(() -> ApiException.notFound("召回事件不存在: " + recallNumber));
        if (snapshot.getStatus() == RecallStatus.CLOSED) {
            return closedResponse(snapshot, closeNumber);
        }

        // 锁顺序与转换/发货一致：先按主键顺序锁批次，再锁召回事件，
        // 避免与"先批次后召回"的并发事务形成循环等待
        List<RecallImpact> impacts = impactRepository.findByRecall(snapshot);
        lockLots(impacts);
        RecallEvent recall = recallRepository.findForUpdateByRecallNumber(recallNumber)
                .orElseThrow(() -> ApiException.notFound("召回事件不存在: " + recallNumber));
        // 加锁前读取的快照可能仍在持久化上下文中，加行锁后必须刷新才能看到
        // 并发事务已提交的状态（如已被另一方关闭）
        entityManager.refresh(recall);
        if (recall.getStatus() == RecallStatus.CLOSED) {
            return closedResponse(recall, closeNumber);
        }

        // 持有召回行锁后影响清单才稳定（转换传播同样需要先取得召回行锁），
        // 重新读取并补锁新追加的批次（均为更晚创建的批次，主键顺序仍然成立）
        List<RecallImpact> freshImpacts = impactRepository.findByRecall(recall);
        lockLots(freshImpacts);

        if (expectedStatsVersion != null && expectedStatsVersion != recall.getStatsVersion()) {
            throw ApiException.conflict("统计数据已变化，基于旧统计的关闭决定失效: 期望版本 "
                    + expectedStatsVersion + " 当前版本 " + recall.getStatsVersion());
        }

        RecallAnalytics.RecallStats stats = analytics.compute(recall);
        BigDecimal required = requiredResponseRatio.multiply(BigDecimal.valueOf(stats.notifiedHolders()));
        if (BigDecimal.valueOf(stats.respondedHolders()).compareTo(required) < 0) {
            throw ApiException.conflict("响应门槛未满足: 已响应 " + stats.respondedHolders()
                    + "/" + stats.notifiedHolders() + " 要求比例 " + requiredResponseRatio);
        }

        recall.close(closeNumber, approvedBy, stats.unrecoveredQuantity(), Instant.now());
        for (RecallImpact impact : freshImpacts) {
            Lot lot = impact.getLot();
            lot.setQuarantined(impactRepository.existsByLotAndRecall_Status(lot, RecallStatus.OPEN));
        }
        return toResponse(recall);
    }

    private RecallResponse closedResponse(RecallEvent recall, String closeNumber) {
        if (!recall.getCloseNumber().equals(closeNumber)) {
            throw ApiException.conflict("召回事件已关闭且关闭号不一致: " + recall.getRecallNumber());
        }
        return toResponse(recall);
    }

    private void lockLots(List<RecallImpact> impacts) {
        List<Long> lotIds = impacts.stream().map(impact -> impact.getLot().getId()).sorted().toList();
        if (!lotIds.isEmpty()) {
            lotRepository.findForUpdateByIdIn(lotIds);
        }
    }

    private RecallResponse toResponse(RecallEvent recall) {
        return new RecallResponse(recall.getRecallNumber(), recall.getRootLot().getLotNumber(),
                recall.getReason(), recall.getStatus().name(),
                impactRepository.countByRecall(recall), recall.getStatsVersion(),
                recall.getCloseNumber(), recall.getApprovedBy(), recall.getUnrecoveredQuantity());
    }
}

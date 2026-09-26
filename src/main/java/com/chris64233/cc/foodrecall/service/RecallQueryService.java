package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.RecallEventRepository;
import com.chris64233.cc.foodrecall.repository.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repository.RecallNotificationRepository;
import com.chris64233.cc.foodrecall.web.Dtos.DiscrepanciesResponse;
import com.chris64233.cc.foodrecall.web.Dtos.DiscrepancyView;
import com.chris64233.cc.foodrecall.web.Dtos.EffectivenessResponse;
import com.chris64233.cc.foodrecall.web.Dtos.HolderResponseView;
import com.chris64233.cc.foodrecall.web.Dtos.HolderResponsesView;
import com.chris64233.cc.foodrecall.web.Dtos.ImpactView;
import com.chris64233.cc.foodrecall.web.Dtos.ImpactsResponse;
import com.chris64233.cc.foodrecall.web.Dtos.NotificationView;
import com.chris64233.cc.foodrecall.web.Dtos.NotificationsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class RecallQueryService {

    private final RecallEventRepository recallRepository;
    private final RecallImpactRepository impactRepository;
    private final RecallNotificationRepository notificationRepository;
    private final RecallAnalytics analytics;

    public RecallQueryService(RecallEventRepository recallRepository,
                              RecallImpactRepository impactRepository,
                              RecallNotificationRepository notificationRepository,
                              RecallAnalytics analytics) {
        this.recallRepository = recallRepository;
        this.impactRepository = impactRepository;
        this.notificationRepository = notificationRepository;
        this.analytics = analytics;
    }

    @Transactional(readOnly = true)
    public ImpactsResponse impacts(String recallNumber) {
        RecallEvent recall = find(recallNumber);
        List<ImpactView> impacts = impactRepository.findByRecall(recall).stream()
                .map(impact -> new ImpactView(impact.getLot().getLotNumber(), impact.getReason()))
                .sorted(Comparator.comparing(ImpactView::lotNumber))
                .toList();
        return new ImpactsResponse(recall.getRecallNumber(), impacts);
    }

    @Transactional(readOnly = true)
    public NotificationsResponse notifications(String recallNumber) {
        RecallEvent recall = find(recallNumber);
        List<NotificationView> notifications = notificationRepository.findByRecallOrderByIdAsc(recall)
                .stream()
                .map(n -> new NotificationView(n.getNotificationNumber(), n.getHolder(),
                        n.getQuantity(), n.getSource().name(), n.getCreatedAt()))
                .toList();
        return new NotificationsResponse(recall.getRecallNumber(), notifications);
    }

    @Transactional(readOnly = true)
    public HolderResponsesView holderResponses(String recallNumber) {
        RecallEvent recall = find(recallNumber);
        RecallAnalytics.RecallStats stats = analytics.compute(recall);
        List<HolderResponseView> holders = stats.holders().entrySet().stream()
                .map(entry -> {
                    RecallAnalytics.HolderStats h = entry.getValue();
                    return new HolderResponseView(entry.getKey(), h.received, h.isolated,
                            h.consumed, h.transferred, h.mismatch, h.outstanding(), h.responded);
                })
                .toList();
        return new HolderResponsesView(recall.getRecallNumber(), holders);
    }

    @Transactional(readOnly = true)
    public DiscrepanciesResponse discrepancies(String recallNumber) {
        RecallEvent recall = find(recallNumber);
        RecallAnalytics.RecallStats stats = analytics.compute(recall);
        List<DiscrepancyView> discrepancies = stats.holders().entrySet().stream()
                .map(entry -> {
                    RecallAnalytics.HolderStats h = entry.getValue();
                    return new DiscrepancyView(entry.getKey(), h.received, h.accounted(),
                            h.outstanding(), h.mismatch);
                })
                .toList();
        return new DiscrepanciesResponse(recall.getRecallNumber(), discrepancies);
    }

    @Transactional(readOnly = true)
    public EffectivenessResponse effectiveness(String recallNumber) {
        RecallEvent recall = find(recallNumber);
        RecallAnalytics.RecallStats stats = analytics.compute(recall);
        return new EffectivenessResponse(recall.getRecallNumber(), recall.getStatus().name(),
                recall.getStatsVersion(), stats.notifiedHolders(), stats.respondedHolders(),
                stats.responseRate(), stats.dispatchedQuantity(), stats.isolatedQuantity(),
                stats.consumedQuantity(), stats.transferredQuantity(), stats.mismatchQuantity(),
                stats.unrecoveredQuantity(), stats.completionRate(),
                recall.getCloseNumber(), recall.getApprovedBy());
    }

    private RecallEvent find(String recallNumber) {
        String number = LotService.requireText(recallNumber, "召回事件号不能为空");
        return recallRepository.findByRecallNumber(number)
                .orElseThrow(() -> ApiException.notFound("召回事件不存在: " + number));
    }
}

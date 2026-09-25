package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.LotRepository;
import com.chris64233.cc.foodrecall.repository.RecallEventRepository;
import com.chris64233.cc.foodrecall.repository.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repository.TransformationOutputRepository;
import com.chris64233.cc.foodrecall.web.Dtos.RecallRequest;
import com.chris64233.cc.foodrecall.web.Dtos.RecallResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecallService {

    private final LotRepository lotRepository;
    private final RecallEventRepository recallRepository;
    private final RecallImpactRepository impactRepository;
    private final TransformationOutputRepository outputRepository;
    private final Transactions transactions;

    public RecallService(LotRepository lotRepository,
                         RecallEventRepository recallRepository,
                         RecallImpactRepository impactRepository,
                         TransformationOutputRepository outputRepository,
                         Transactions transactions) {
        this.lotRepository = lotRepository;
        this.recallRepository = recallRepository;
        this.impactRepository = impactRepository;
        this.outputRepository = outputRepository;
        this.transactions = transactions;
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
            }
            for (Lot child : outputRepository.findChildLotsOf(locked)) {
                if (!visited.contains(child.getId())) {
                    next.add(child);
                }
            }
            frontier = next;
        }
        return toResponse(recall);
    }

    public RecallResponse close(String recallNumber) {
        String number = LotService.requireText(recallNumber, "召回事件号不能为空");
        return transactions.idempotent(() -> doClose(number));
    }

    private RecallResponse doClose(String recallNumber) {
        RecallEvent recall = recallRepository.findForUpdateByRecallNumber(recallNumber)
                .orElseThrow(() -> ApiException.notFound("召回事件不存在: " + recallNumber));
        if (recall.getStatus() == RecallStatus.CLOSED) {
            return toResponse(recall);
        }
        recall.close(Instant.now());

        List<RecallImpact> impacts = impactRepository.findByRecall(recall);
        List<Long> lotIds = impacts.stream().map(impact -> impact.getLot().getId()).sorted().toList();
        List<Lot> lots = lotIds.isEmpty() ? List.of() : lotRepository.findForUpdateByIdIn(lotIds);
        impactRepository.deleteAll(impacts);
        impactRepository.flush();
        for (Lot lot : lots) {
            lot.setQuarantined(impactRepository.existsByLot(lot));
        }
        return toResponse(recall);
    }

    private RecallResponse toResponse(RecallEvent recall) {
        long affected = recall.getStatus() == RecallStatus.OPEN ? impactRepository.countByRecall(recall) : 0;
        return new RecallResponse(recall.getRecallNumber(), recall.getRootLot().getLotNumber(),
                recall.getReason(), recall.getStatus().name(), affected);
    }
}

package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import com.chris64233.cc.foodrecall.repo.LotEdgeRepository;
import com.chris64233.cc.foodrecall.repo.LotRepository;
import com.chris64233.cc.foodrecall.repo.RecallEventRepository;
import com.chris64233.cc.foodrecall.repo.RecallImpactRepository;
import com.chris64233.cc.foodrecall.service.Views.MutationResult;
import com.chris64233.cc.foodrecall.service.Views.RecallView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecallService {

    private final LotRepository lotRepository;
    private final LotEdgeRepository edgeRepository;
    private final RecallEventRepository recallRepository;
    private final RecallImpactRepository impactRepository;
    private final MutationGate gate;
    private final TransactionTemplate tx;

    public RecallService(LotRepository lotRepository,
                         LotEdgeRepository edgeRepository,
                         RecallEventRepository recallRepository,
                         RecallImpactRepository impactRepository,
                         MutationGate gate,
                         PlatformTransactionManager transactionManager) {
        this.lotRepository = lotRepository;
        this.edgeRepository = edgeRepository;
        this.recallRepository = recallRepository;
        this.impactRepository = impactRepository;
        this.gate = gate;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public MutationResult<RecallView> initiate(String recallNumber, String lotNumber, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("reason must not be blank");
        }
        return gate.execute(() -> tx.execute(status -> doInitiate(recallNumber, lotNumber, reason)));
    }

    private MutationResult<RecallView> doInitiate(String recallNumber, String lotNumber, String reason) {
        var existing = recallRepository.findByRecallNumber(recallNumber);
        if (existing.isPresent()) {
            RecallEvent recall = existing.get();
            if (!recall.getRootLotNumber().equals(lotNumber) || !recall.getReason().equals(reason)) {
                throw ApiException.conflict("recall " + recallNumber + " already exists with different content");
            }
            return new MutationResult<>(toView(recall, impactedLots(recallNumber)), false);
        }

        if (lotRepository.findByLotNumber(lotNumber).isEmpty()) {
            throw ApiException.notFound("lot " + lotNumber + " not found");
        }
        RecallEvent recall = recallRepository.saveAndFlush(new RecallEvent(recallNumber, lotNumber, reason));

        List<String> impacted = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(lotNumber);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            Lot lot = lotRepository.findByLotNumberForUpdate(current)
                    .orElseThrow(() -> ApiException.notFound("lot " + current + " not found"));
            impactRepository.save(new RecallImpact(recall, lot, reason));
            impacted.add(current);
            queue.addAll(edgeRepository.findChildLotNumbers(current));
        }
        return new MutationResult<>(toView(recall, impacted), true);
    }

    public MutationResult<RecallView> close(String recallNumber) {
        return gate.execute(() -> tx.execute(status -> {
            RecallEvent recall = recallRepository.findByRecallNumber(recallNumber)
                    .orElseThrow(() -> ApiException.notFound("recall " + recallNumber + " not found"));
            if (recall.getStatus() == RecallStatus.CLOSED) {
                return new MutationResult<>(toView(recall, impactedLots(recallNumber)), false);
            }
            recall.close();
            return new MutationResult<>(toView(recall, impactedLots(recallNumber)), true);
        }));
    }

    public RecallView detail(String recallNumber) {
        RecallEvent recall = recallRepository.findByRecallNumber(recallNumber)
                .orElseThrow(() -> ApiException.notFound("recall " + recallNumber + " not found"));
        return toView(recall, impactedLots(recallNumber));
    }

    private List<String> impactedLots(String recallNumber) {
        return impactRepository.findByRecallNumber(recallNumber).stream()
                .map(i -> i.getLot().getLotNumber())
                .sorted()
                .toList();
    }

    private RecallView toView(RecallEvent recall, List<String> impactedLots) {
        return new RecallView(recall.getRecallNumber(), recall.getRootLotNumber(), recall.getReason(),
                recall.getStatus().name(), impactedLots);
    }
}

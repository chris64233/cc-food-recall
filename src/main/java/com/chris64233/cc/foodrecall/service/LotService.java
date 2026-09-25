package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import com.chris64233.cc.foodrecall.repo.LotEdgeRepository;
import com.chris64233.cc.foodrecall.repo.LotRepository;
import com.chris64233.cc.foodrecall.repo.RecallImpactRepository;
import com.chris64233.cc.foodrecall.service.Views.LineageView;
import com.chris64233.cc.foodrecall.service.Views.LotStockView;
import com.chris64233.cc.foodrecall.service.Views.LotView;
import com.chris64233.cc.foodrecall.service.Views.MutationResult;
import com.chris64233.cc.foodrecall.service.Views.RecallReasonView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LotService {

    private final LotRepository lotRepository;
    private final LotEdgeRepository edgeRepository;
    private final RecallImpactRepository impactRepository;
    private final MutationGate gate;
    private final TransactionTemplate tx;

    public LotService(LotRepository lotRepository,
                      LotEdgeRepository edgeRepository,
                      RecallImpactRepository impactRepository,
                      MutationGate gate,
                      PlatformTransactionManager transactionManager) {
        this.lotRepository = lotRepository;
        this.edgeRepository = edgeRepository;
        this.impactRepository = impactRepository;
        this.gate = gate;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public MutationResult<LotView> register(String lotNumber, BigDecimal quantity) {
        BigDecimal qty = Quantities.normalize(quantity, "quantity");
        if (qty.signum() <= 0) {
            throw ApiException.badRequest("quantity must be positive");
        }
        return gate.execute(() -> tx.execute(status -> {
            var existing = lotRepository.findByLotNumber(lotNumber);
            if (existing.isPresent()) {
                Lot lot = existing.get();
                if (lot.getQuantity().compareTo(qty) != 0) {
                    throw ApiException.conflict(
                            "lot " + lotNumber + " already exists with a different quantity");
                }
                return new MutationResult<>(new LotView(lot.getLotNumber(), lot.getQuantity(), false), false);
            }
            Lot lot = lotRepository.saveAndFlush(new Lot(lotNumber, qty));
            return new MutationResult<>(new LotView(lot.getLotNumber(), lot.getQuantity(), false), true);
        }));
    }

    public LotStockView stock(String lotNumber) {
        Lot lot = lotRepository.findByLotNumber(lotNumber)
                .orElseThrow(() -> ApiException.notFound("lot " + lotNumber + " not found"));
        List<RecallReasonView> active = impactRepository.findByLotNumber(lotNumber).stream()
                .filter(i -> i.getRecall().getStatus() == RecallStatus.OPEN)
                .map(i -> new RecallReasonView(i.getRecall().getRecallNumber(), i.getReason(),
                        i.getRecall().getStatus().name()))
                .toList();
        return new LotStockView(lot.getLotNumber(), lot.getQuantity(), !active.isEmpty(), active);
    }

    public List<RecallReasonView> recallReasons(String lotNumber) {
        if (lotRepository.findByLotNumber(lotNumber).isEmpty()) {
            throw ApiException.notFound("lot " + lotNumber + " not found");
        }
        return impactRepository.findByLotNumber(lotNumber).stream()
                .map(i -> new RecallReasonView(i.getRecall().getRecallNumber(), i.getReason(),
                        i.getRecall().getStatus().name()))
                .toList();
    }

    public LineageView lineage(String lotNumber, String direction) {
        if (lotRepository.findByLotNumber(lotNumber).isEmpty()) {
            throw ApiException.notFound("lot " + lotNumber + " not found");
        }
        boolean upstream = "upstream".equals(direction);
        Function<String, List<String>> neighbours = upstream
                ? edgeRepository::findParentLotNumbers
                : edgeRepository::findChildLotNumbers;

        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(lotNumber);
        visited.add(lotNumber);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : neighbours.apply(current)) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        visited.remove(lotNumber);

        Map<String, Lot> lots = lotRepository.findAll().stream()
                .collect(Collectors.toMap(Lot::getLotNumber, Function.identity()));
        Set<String> quarantined = quarantinedLots(visited);
        List<LotView> related = new ArrayList<>();
        for (String number : visited) {
            Lot lot = lots.get(number);
            if (lot != null) {
                related.add(new LotView(lot.getLotNumber(), lot.getQuantity(), quarantined.contains(number)));
            }
        }
        return new LineageView(lotNumber, direction, related);
    }

    Set<String> quarantinedLots(Set<String> lotNumbers) {
        if (lotNumbers.isEmpty()) {
            return Set.of();
        }
        return impactRepository.findByLotNumberIn(lotNumbers).stream()
                .filter(i -> i.getRecall().getStatus() == RecallStatus.OPEN)
                .map(i -> i.getLot().getLotNumber())
                .collect(Collectors.toSet());
    }

    List<RecallImpact> activeImpactsOf(String lotNumber) {
        return impactRepository.findByLotNumber(lotNumber).stream()
                .filter(i -> i.getRecall().getStatus() == RecallStatus.OPEN)
                .toList();
    }
}

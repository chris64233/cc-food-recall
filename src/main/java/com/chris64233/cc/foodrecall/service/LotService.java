package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.LotRepository;
import com.chris64233.cc.foodrecall.repository.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repository.TransformationInputRepository;
import com.chris64233.cc.foodrecall.repository.TransformationOutputRepository;
import com.chris64233.cc.foodrecall.web.Dtos.GenealogyNode;
import com.chris64233.cc.foodrecall.web.Dtos.GenealogyResponse;
import com.chris64233.cc.foodrecall.web.Dtos.LotResponse;
import com.chris64233.cc.foodrecall.web.Dtos.RecallReasonView;
import com.chris64233.cc.foodrecall.web.Dtos.RegisterLotRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LotService {

    private final LotRepository lotRepository;
    private final RecallImpactRepository impactRepository;
    private final TransformationInputRepository inputRepository;
    private final TransformationOutputRepository outputRepository;
    private final Transactions transactions;

    public LotService(LotRepository lotRepository,
                      RecallImpactRepository impactRepository,
                      TransformationInputRepository inputRepository,
                      TransformationOutputRepository outputRepository,
                      Transactions transactions) {
        this.lotRepository = lotRepository;
        this.impactRepository = impactRepository;
        this.inputRepository = inputRepository;
        this.outputRepository = outputRepository;
        this.transactions = transactions;
    }

    public LotResponse register(RegisterLotRequest request) {
        String lotNumber = requireText(request == null ? null : request.lotNumber(), "批次号不能为空");
        BigDecimal quantity = Quantities.requirePositive(request == null ? null : request.quantity(), "数量");
        return transactions.idempotent(() -> doRegister(lotNumber, quantity));
    }

    private LotResponse doRegister(String lotNumber, BigDecimal quantity) {
        return lotRepository.findByLotNumber(lotNumber)
                .map(existing -> {
                    if (existing.getQuantity().compareTo(quantity) != 0) {
                        throw ApiException.conflict("批次号已存在且数量不一致: " + lotNumber);
                    }
                    return toResponse(existing, List.of());
                })
                .orElseGet(() -> toResponse(lotRepository.save(new Lot(lotNumber, quantity)), List.of()));
    }

    @Transactional(readOnly = true)
    public LotResponse getLot(String lotNumber) {
        Lot lot = lotRepository.findByLotNumber(lotNumber)
                .orElseThrow(() -> ApiException.notFound("批次不存在: " + lotNumber));
        List<RecallReasonView> reasons = impactRepository.findByLot(lot).stream()
                .map(impact -> new RecallReasonView(impact.getRecall().getRecallNumber(), impact.getReason()))
                .toList();
        return toResponse(lot, reasons);
    }

    @Transactional(readOnly = true)
    public GenealogyResponse getGenealogy(String lotNumber) {
        Lot lot = lotRepository.findByLotNumber(lotNumber)
                .orElseThrow(() -> ApiException.notFound("批次不存在: " + lotNumber));
        return new GenealogyResponse(lot.getLotNumber(), traverse(lot, true), traverse(lot, false));
    }

    private List<GenealogyNode> traverse(Lot start, boolean upstream) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        seen.add(start.getId());
        List<Lot> frontier = List.of(start);
        int depth = 1;
        while (!frontier.isEmpty()) {
            List<Lot> neighbours = upstream
                    ? inputRepository.findParentLotsOf(frontier)
                    : outputRepository.findChildLotsOf(frontier);
            List<Lot> next = new ArrayList<>();
            for (Lot neighbour : neighbours) {
                if (seen.add(neighbour.getId())) {
                    result.put(neighbour.getLotNumber(), depth);
                    next.add(neighbour);
                }
            }
            frontier = next;
            depth++;
        }
        return result.entrySet().stream()
                .map(entry -> new GenealogyNode(entry.getKey(), entry.getValue()))
                .toList();
    }

    private LotResponse toResponse(Lot lot, List<RecallReasonView> reasons) {
        return new LotResponse(lot.getLotNumber(), lot.getQuantity(), lot.isQuarantined(), reasons);
    }

    static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(message);
        }
        return value.trim();
    }
}

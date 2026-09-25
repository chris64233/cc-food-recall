package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.LotEdge;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import com.chris64233.cc.foodrecall.domain.Transformation;
import com.chris64233.cc.foodrecall.repo.LotEdgeRepository;
import com.chris64233.cc.foodrecall.repo.LotRepository;
import com.chris64233.cc.foodrecall.repo.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repo.TransformationRepository;
import com.chris64233.cc.foodrecall.service.Views.LotAmountView;
import com.chris64233.cc.foodrecall.service.Views.MutationResult;
import com.chris64233.cc.foodrecall.service.Views.TransformationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class TransformationService {

    public record Item(String lotNumber, BigDecimal quantity) {
    }

    private final LotRepository lotRepository;
    private final LotEdgeRepository edgeRepository;
    private final RecallImpactRepository impactRepository;
    private final TransformationRepository transformationRepository;
    private final MutationGate gate;
    private final TransactionTemplate tx;

    public TransformationService(LotRepository lotRepository,
                                 LotEdgeRepository edgeRepository,
                                 RecallImpactRepository impactRepository,
                                 TransformationRepository transformationRepository,
                                 MutationGate gate,
                                 PlatformTransactionManager transactionManager) {
        this.lotRepository = lotRepository;
        this.edgeRepository = edgeRepository;
        this.impactRepository = impactRepository;
        this.transformationRepository = transformationRepository;
        this.gate = gate;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public MutationResult<TransformationView> transform(String transformationId,
                                                        List<Item> inputs,
                                                        List<Item> outputs,
                                                        BigDecimal loss) {
        List<Item> in = normalizeItems(inputs, "inputs");
        List<Item> out = normalizeItems(outputs, "outputs");
        BigDecimal normalizedLoss = loss == null
                ? BigDecimal.ZERO.setScale(Quantities.SCALE)
                : Quantities.normalize(loss, "loss");
        if (normalizedLoss.signum() < 0) {
            throw ApiException.badRequest("loss must not be negative");
        }
        return gate.execute(() -> tx.execute(status -> doTransform(transformationId, in, out, normalizedLoss)));
    }

    private MutationResult<TransformationView> doTransform(String transformationId,
                                                           List<Item> inputs,
                                                           List<Item> outputs,
                                                           BigDecimal loss) {
        String content = canonicalContent(inputs, outputs, loss);
        var existing = transformationRepository.findByTransformationId(transformationId);
        if (existing.isPresent()) {
            Transformation stored = existing.get();
            if (!stored.getContent().equals(content)) {
                throw ApiException.conflict("transformation " + transformationId
                        + " already exists with different content");
            }
            return new MutationResult<>(toView(stored), false);
        }

        Map<String, Lot> inputLots = lockInputs(inputs);

        for (Item output : outputs) {
            if (lotRepository.findByLotNumber(output.lotNumber()).isPresent()) {
                throw ApiException.conflict("output lot " + output.lotNumber() + " already exists");
            }
        }

        BigDecimal totalIn = sum(inputs);
        BigDecimal totalOut = sum(outputs).add(loss);
        if (totalIn.compareTo(totalOut) != 0) {
            throw ApiException.unprocessable("mass not conserved: inputs=" + totalIn
                    + " but outputs+loss=" + totalOut);
        }

        for (Item output : outputs) {
            for (Item input : inputs) {
                if (hasPath(output.lotNumber(), input.lotNumber())) {
                    throw ApiException.unprocessable("transformation would create a cycle between "
                            + input.lotNumber() + " and " + output.lotNumber());
                }
            }
        }

        for (Item input : inputs) {
            Lot lot = inputLots.get(input.lotNumber());
            if (lot.getQuantity().compareTo(input.quantity()) < 0) {
                throw ApiException.conflict("insufficient stock in lot " + input.lotNumber()
                        + ": available=" + lot.getQuantity() + " required=" + input.quantity());
            }
        }
        for (Item input : inputs) {
            Lot lot = inputLots.get(input.lotNumber());
            lot.setQuantity(lot.getQuantity().subtract(input.quantity()));
        }

        List<Lot> outputLots = new ArrayList<>();
        for (Item output : outputs) {
            outputLots.add(lotRepository.save(new Lot(output.lotNumber(), output.quantity())));
        }
        for (Lot inputLot : inputLots.values()) {
            for (Lot outputLot : outputLots) {
                edgeRepository.save(new LotEdge(inputLot, outputLot));
            }
        }

        propagateOpenRecalls(inputLots.keySet(), outputLots);

        Transformation transformation = transformationRepository.saveAndFlush(
                new Transformation(transformationId, content, loss));
        return new MutationResult<>(toView(transformation), true);
    }

    private Map<String, Lot> lockInputs(List<Item> inputs) {
        Map<String, Lot> lots = new LinkedHashMap<>();
        List<String> numbers = inputs.stream().map(Item::lotNumber).sorted().toList();
        for (String number : numbers) {
            Lot lot = lotRepository.findByLotNumberForUpdate(number)
                    .orElseThrow(() -> ApiException.notFound("input lot " + number + " not found"));
            lots.put(number, lot);
        }
        return lots;
    }

    private void propagateOpenRecalls(Set<String> inputLotNumbers, List<Lot> outputLots) {
        Map<String, RecallEvent> openRecalls = new LinkedHashMap<>();
        for (RecallImpact impact : impactRepository.findByLotNumberIn(inputLotNumbers)) {
            RecallEvent recall = impact.getRecall();
            if (recall.getStatus() == RecallStatus.OPEN) {
                openRecalls.putIfAbsent(recall.getRecallNumber(), recall);
            }
        }
        for (RecallEvent recall : openRecalls.values()) {
            for (Lot outputLot : outputLots) {
                impactRepository.save(new RecallImpact(recall, outputLot, recall.getReason()));
            }
        }
    }

    private boolean hasPath(String fromLotNumber, String toLotNumber) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(fromLotNumber);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(toLotNumber)) {
                return true;
            }
            if (visited.add(current)) {
                queue.addAll(edgeRepository.findChildLotNumbers(current));
            }
        }
        return false;
    }

    private List<Item> normalizeItems(List<Item> items, String field) {
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest(field + " must not be empty");
        }
        Map<String, BigDecimal> merged = new HashMap<>();
        for (Item item : items) {
            if (item.lotNumber() == null || item.lotNumber().isBlank()) {
                throw ApiException.badRequest(field + " contains a blank lotNumber");
            }
            BigDecimal qty = Quantities.normalize(item.quantity(), field + ".quantity");
            if (qty.signum() <= 0) {
                throw ApiException.badRequest(field + " quantities must be positive");
            }
            if (merged.put(item.lotNumber(), qty) != null) {
                throw ApiException.badRequest("duplicate lot " + item.lotNumber() + " in " + field);
            }
        }
        return merged.entrySet().stream()
                .map(e -> new Item(e.getKey(), e.getValue()))
                .toList();
    }

    private static BigDecimal sum(List<Item> items) {
        return items.stream().map(Item::quantity).reduce(BigDecimal.ZERO.setScale(Quantities.SCALE), BigDecimal::add);
    }

    private static String canonicalContent(List<Item> inputs, List<Item> outputs, BigDecimal loss) {
        return "in:" + canonicalItems(inputs) + "|out:" + canonicalItems(outputs) + "|loss:" + loss.toPlainString();
    }

    private static String canonicalItems(List<Item> items) {
        Map<String, BigDecimal> sorted = new TreeMap<>();
        for (Item item : items) {
            sorted.put(item.lotNumber(), item.quantity());
        }
        StringBuilder sb = new StringBuilder();
        sorted.forEach((number, qty) -> {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(number).append('=').append(qty.toPlainString());
        });
        return sb.toString();
    }

    private TransformationView toView(Transformation transformation) {
        String content = transformation.getContent();
        return new TransformationView(
                transformation.getTransformationId(),
                parseItems(content, "in:"),
                parseItems(content, "out:"),
                transformation.getLoss());
    }

    private static List<LotAmountView> parseItems(String content, String section) {
        int start = content.indexOf(section) + section.length();
        int end = content.indexOf('|', start);
        String body = content.substring(start, end < 0 ? content.length() : end);
        List<LotAmountView> items = new ArrayList<>();
        if (!body.isEmpty()) {
            for (String pair : body.split(",")) {
                String[] parts = pair.split("=", 2);
                items.add(new LotAmountView(parts[0], Quantities.normalize(parts[1])));
            }
        }
        return items;
    }
}

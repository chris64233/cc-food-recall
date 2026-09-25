package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.RecallEvent;
import com.chris64233.cc.foodrecall.domain.RecallImpact;
import com.chris64233.cc.foodrecall.domain.RecallStatus;
import com.chris64233.cc.foodrecall.domain.Transformation;
import com.chris64233.cc.foodrecall.domain.TransformationInput;
import com.chris64233.cc.foodrecall.domain.TransformationOutput;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.LotRepository;
import com.chris64233.cc.foodrecall.repository.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repository.TransformationInputRepository;
import com.chris64233.cc.foodrecall.repository.TransformationOutputRepository;
import com.chris64233.cc.foodrecall.repository.TransformationRepository;
import com.chris64233.cc.foodrecall.web.Dtos.LotAmount;
import com.chris64233.cc.foodrecall.web.Dtos.TransformRequest;
import com.chris64233.cc.foodrecall.web.Dtos.TransformResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class TransformationService {

    private final LotRepository lotRepository;
    private final TransformationRepository transformationRepository;
    private final TransformationInputRepository inputRepository;
    private final TransformationOutputRepository outputRepository;
    private final RecallImpactRepository impactRepository;
    private final Transactions transactions;

    public TransformationService(LotRepository lotRepository,
                                 TransformationRepository transformationRepository,
                                 TransformationInputRepository inputRepository,
                                 TransformationOutputRepository outputRepository,
                                 RecallImpactRepository impactRepository,
                                 Transactions transactions) {
        this.lotRepository = lotRepository;
        this.transformationRepository = transformationRepository;
        this.inputRepository = inputRepository;
        this.outputRepository = outputRepository;
        this.impactRepository = impactRepository;
        this.transactions = transactions;
    }

    public TransformResponse transform(TransformRequest request) {
        Validated validated = validate(request);
        return transactions.idempotent(() -> doTransform(validated));
    }

    private TransformResponse doTransform(Validated validated) {
        var existing = transformationRepository.findByTransformationKey(validated.transformationId());
        if (existing.isPresent()) {
            Transformation stored = existing.get();
            if (!contentMatches(stored, validated)) {
                throw ApiException.conflict("转换编号已存在且内容不一致: " + validated.transformationId());
            }
            return toResponse(stored);
        }

        List<Lot> inputs = lockInputs(validated);
        ensureOutputsAbsent(validated);
        ensureNoCycle(inputs, validated.outputs().keySet());

        Transformation transformation = transformationRepository.save(
                new Transformation(validated.transformationId(), validated.loss(), Instant.now()));

        for (Map.Entry<String, BigDecimal> entry : validated.inputs().entrySet()) {
            Lot lot = findLot(inputs, entry.getKey());
            lot.setQuantity(lot.getQuantity().subtract(entry.getValue()));
            TransformationInput input = new TransformationInput(transformation, lot, entry.getValue());
            transformation.getInputs().add(input);
        }

        List<Lot> outputLots = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : validated.outputs().entrySet()) {
            Lot output = lotRepository.save(new Lot(entry.getKey(), entry.getValue()));
            transformation.getOutputs().add(new TransformationOutput(transformation, output, entry.getValue()));
            outputLots.add(output);
        }

        propagateRecalls(inputs, outputLots);
        return toResponse(transformation);
    }

    private List<Lot> lockInputs(Validated validated) {
        List<String> numbers = new ArrayList<>(new TreeSet<>(validated.inputs().keySet()));
        List<Lot> locked = lotRepository.findForUpdateByLotNumberIn(numbers);
        if (locked.size() != numbers.size()) {
            Set<String> found = locked.stream().map(Lot::getLotNumber).collect(Collectors.toSet());
            List<String> missing = numbers.stream().filter(n -> !found.contains(n)).toList();
            throw ApiException.notFound("输入批次不存在: " + String.join(", ", missing));
        }
        for (Lot lot : locked) {
            BigDecimal required = validated.inputs().get(lot.getLotNumber());
            if (lot.getQuantity().compareTo(required) < 0) {
                throw ApiException.conflict("批次库存不足: " + lot.getLotNumber()
                        + " 现有 " + lot.getQuantity() + " 需要 " + required);
            }
        }
        return locked;
    }

    private void ensureOutputsAbsent(Validated validated) {
        List<Lot> conflicts = lotRepository.findByLotNumberIn(validated.outputs().keySet());
        if (!conflicts.isEmpty()) {
            String names = conflicts.stream().map(Lot::getLotNumber).sorted().collect(Collectors.joining(", "));
            throw ApiException.conflict("输出批次编号已存在: " + names);
        }
    }

    private void ensureNoCycle(List<Lot> inputs, Set<String> outputNumbers) {
        Set<Long> visited = new HashSet<>();
        List<Lot> frontier = new ArrayList<>(inputs);
        while (!frontier.isEmpty()) {
            List<Lot> parents = inputRepository.findParentLotsOf(frontier);
            List<Lot> next = new ArrayList<>();
            for (Lot parent : parents) {
                if (outputNumbers.contains(parent.getLotNumber())) {
                    throw ApiException.conflict("转换会形成谱系环路: " + parent.getLotNumber());
                }
                if (visited.add(parent.getId())) {
                    next.add(parent);
                }
            }
            frontier = next;
        }
    }

    private void propagateRecalls(List<Lot> inputs, List<Lot> outputLots) {
        List<RecallEvent> openRecalls = impactRepository.findByLotIn(inputs).stream()
                .map(RecallImpact::getRecall)
                .filter(recall -> recall.getStatus() == RecallStatus.OPEN)
                .distinct()
                .toList();
        for (Lot output : outputLots) {
            for (RecallEvent recall : openRecalls) {
                impactRepository.save(new RecallImpact(recall, output, recall.getReason()));
                output.setQuarantined(true);
            }
        }
    }

    private boolean contentMatches(Transformation stored, Validated validated) {
        if (stored.getLoss().compareTo(validated.loss()) != 0) {
            return false;
        }
        Map<String, BigDecimal> storedInputs = stored.getInputs().stream()
                .collect(Collectors.toMap(i -> i.getLot().getLotNumber(), TransformationInput::getQuantity));
        Map<String, BigDecimal> storedOutputs = stored.getOutputs().stream()
                .collect(Collectors.toMap(o -> o.getLot().getLotNumber(), TransformationOutput::getQuantity));
        return amountsEqual(storedInputs, validated.inputs())
                && amountsEqual(storedOutputs, validated.outputs());
    }

    private boolean amountsEqual(Map<String, BigDecimal> left, Map<String, BigDecimal> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (Map.Entry<String, BigDecimal> entry : left.entrySet()) {
            BigDecimal other = right.get(entry.getKey());
            if (other == null || entry.getValue().compareTo(other) != 0) {
                return false;
            }
        }
        return true;
    }

    private TransformResponse toResponse(Transformation transformation) {
        List<LotAmount> inputs = transformation.getInputs().stream()
                .map(i -> new LotAmount(i.getLot().getLotNumber(), i.getQuantity()))
                .toList();
        List<LotAmount> outputs = transformation.getOutputs().stream()
                .map(o -> new LotAmount(o.getLot().getLotNumber(), o.getQuantity()))
                .toList();
        return new TransformResponse(transformation.getTransformationKey(), inputs, outputs,
                transformation.getLoss());
    }

    private Lot findLot(List<Lot> lots, String lotNumber) {
        return lots.stream()
                .filter(lot -> lot.getLotNumber().equals(lotNumber))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("批次不存在: " + lotNumber));
    }

    private Validated validate(TransformRequest request) {
        if (request == null) {
            throw ApiException.badRequest("请求体不能为空");
        }
        String transformationId = LotService.requireText(request.transformationId(), "转换编号不能为空");
        Map<String, BigDecimal> inputs = validateAmounts(request.inputs(), "输入");
        Map<String, BigDecimal> outputs = validateAmounts(request.outputs(), "输出");
        BigDecimal loss = request.loss() == null ? Quantities.ZERO : Quantities.normalize(request.loss());
        if (loss.signum() < 0) {
            throw ApiException.badRequest("损耗不能为负数");
        }
        BigDecimal inputSum = inputs.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outputSum = outputs.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (inputSum.compareTo(outputSum.add(loss)) != 0) {
            throw ApiException.badRequest("质量不守恒: 输入总量 " + inputSum
                    + " 必须等于输出总量 " + outputSum + " 加损耗 " + loss);
        }
        return new Validated(transformationId, inputs, outputs, loss);
    }

    private Map<String, BigDecimal> validateAmounts(List<LotAmount> amounts, String label) {
        if (amounts == null || amounts.isEmpty()) {
            throw ApiException.badRequest(label + "批次列表不能为空");
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (LotAmount amount : amounts) {
            String lotNumber = LotService.requireText(amount == null ? null : amount.lotNumber(),
                    label + "批次号不能为空");
            BigDecimal quantity = Quantities.requirePositive(amount.quantity(), label + "数量");
            if (result.putIfAbsent(lotNumber, quantity) != null) {
                throw ApiException.badRequest(label + "批次号重复: " + lotNumber);
            }
        }
        return result;
    }

    private record Validated(String transformationId, Map<String, BigDecimal> inputs,
                             Map<String, BigDecimal> outputs, BigDecimal loss) {
    }
}

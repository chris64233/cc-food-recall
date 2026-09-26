package com.chris64233.cc.foodrecall.service;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.domain.Transformation;
import com.chris64233.cc.foodrecall.repository.TransformationOutputRepository;
import com.chris64233.cc.foodrecall.repository.TransformationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 批次谱系归因：计算根批次物质在每个后代批次中所占比例。
 *
 * <p>对一次转换 T（输入 i、输出 o），每个输出批次中来自根批次的比例为
 * <pre>受污染输入质量之和 / 输出总质量</pre>
 * 即假设损耗按比例携带根批次物质。同一批次经由多条谱系路径到达时，
 * 各路径贡献在批次节点上相加（而不是把数量重复计一遍），菱形汇合天然去重。
 */
@Component
public class GenealogyCalculator {

    private final TransformationRepository transformationRepository;
    private final TransformationOutputRepository outputRepository;

    public GenealogyCalculator(TransformationRepository transformationRepository,
                               TransformationOutputRepository outputRepository) {
        this.transformationRepository = transformationRepository;
        this.outputRepository = outputRepository;
    }

    /**
     * 返回根批次（含自身，比例=1）及其全部后代批次的归因比例。
     */
    @Transactional(readOnly = true)
    public Map<Lot, BigDecimal> rootFractions(Lot root) {
        // 1. 沿输出边收集所有可达后代（谱系图在转换创建时已做无环校验）。
        Set<Long> reachableIds = new LinkedHashSet<>();
        Map<Long, Lot> lotsById = new LinkedHashMap<>();
        reachableIds.add(root.getId());
        lotsById.put(root.getId(), root);
        List<Lot> frontier = List.of(root);
        while (!frontier.isEmpty()) {
            List<Lot> next = new ArrayList<>();
            for (Lot child : outputRepository.findChildLotsOf(frontier)) {
                if (reachableIds.add(child.getId())) {
                    lotsById.put(child.getId(), child);
                    next.add(child);
                }
            }
            frontier = next;
        }

        // 2. 加载所有触及可达批次的转换。
        List<Transformation> transformations =
                transformationRepository.findByInputLotIds(lotsById.values());

        // 3. 不动点推进：一个转换的所有可达输入比例都已知时才计算输出比例。
        Map<Long, BigDecimal> fractions = new LinkedHashMap<>();
        fractions.put(root.getId(), BigDecimal.ONE);
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Transformation transformation : transformations) {
                if (transformation.getOutputs().stream()
                        .allMatch(o -> fractions.containsKey(o.getLot().getId()))) {
                    continue;
                }
                boolean ready = true;
                BigDecimal contaminated = BigDecimal.ZERO;
                for (var input : transformation.getInputs()) {
                    long inputId = input.getLot().getId();
                    if (reachableIds.contains(inputId)) {
                        BigDecimal fraction = fractions.get(inputId);
                        if (fraction == null) {
                            ready = false;
                            break;
                        }
                        contaminated = contaminated.add(input.getQuantity().multiply(fraction));
                    }
                }
                if (!ready) {
                    continue;
                }
                BigDecimal outputTotal = transformation.getOutputs().stream()
                        .map(o -> o.getQuantity())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (outputTotal.signum() == 0) {
                    continue;
                }
                BigDecimal mixture = contaminated.divide(outputTotal, 6, RoundingMode.HALF_UP);
                for (var output : transformation.getOutputs()) {
                    long outputId = output.getLot().getId();
                    if (!reachableIds.contains(outputId)) {
                        continue;
                    }
                    BigDecimal previous = fractions.get(outputId);
                    BigDecimal merged = previous == null ? mixture : previous.add(mixture);
                    fractions.put(outputId, merged.min(BigDecimal.ONE));
                    progress = true;
                }
            }
        }

        Map<Lot, BigDecimal> result = new LinkedHashMap<>();
        fractions.forEach((id, fraction) -> result.put(lotsById.get(id), fraction));
        return result;
    }
}

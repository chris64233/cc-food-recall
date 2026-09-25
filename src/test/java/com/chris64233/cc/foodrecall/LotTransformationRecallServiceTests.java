package com.chris64233.cc.foodrecall;

import com.chris64233.cc.foodrecall.repo.LotEdgeRepository;
import com.chris64233.cc.foodrecall.repo.LotRepository;
import com.chris64233.cc.foodrecall.repo.RecallEventRepository;
import com.chris64233.cc.foodrecall.repo.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repo.TransformationRepository;
import com.chris64233.cc.foodrecall.service.ApiException;
import com.chris64233.cc.foodrecall.service.LotService;
import com.chris64233.cc.foodrecall.service.RecallService;
import com.chris64233.cc.foodrecall.service.TransformationService;
import com.chris64233.cc.foodrecall.service.Views.LotStockView;
import com.chris64233.cc.foodrecall.service.Views.MutationResult;
import com.chris64233.cc.foodrecall.service.Views.RecallView;
import com.chris64233.cc.foodrecall.service.Views.TransformationView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LotTransformationRecallServiceTests {

    @Autowired
    LotService lotService;
    @Autowired
    TransformationService transformationService;
    @Autowired
    RecallService recallService;
    @Autowired
    LotRepository lotRepository;
    @Autowired
    LotEdgeRepository edgeRepository;
    @Autowired
    RecallEventRepository recallRepository;
    @Autowired
    RecallImpactRepository impactRepository;
    @Autowired
    TransformationRepository transformationRepository;

    @BeforeEach
    void cleanUp() {
        impactRepository.deleteAll();
        recallRepository.deleteAll();
        edgeRepository.deleteAll();
        transformationRepository.deleteAll();
        lotRepository.deleteAll();
    }

    private static BigDecimal qty(String value) {
        return new BigDecimal(value);
    }

    private static TransformationService.Item item(String lotNumber, String quantity) {
        return new TransformationService.Item(lotNumber, qty(quantity));
    }

    private void register(String lotNumber, String quantity) {
        lotService.register(lotNumber, qty(quantity));
    }

    private MutationResult<TransformationView> transform(String id, List<TransformationService.Item> inputs,
                                                         List<TransformationService.Item> outputs, String loss) {
        return transformationService.transform(id, inputs, outputs, loss == null ? null : qty(loss));
    }

    @Test
    void registerIsIdempotentAndRejectsConflictingQuantity() {
        assertThat(lotService.register("L1", qty("10")).created()).isTrue();
        MutationResult<?> replay = lotService.register("L1", qty("10.0"));
        assertThat(replay.created()).isFalse();

        assertThatThrownBy(() -> lotService.register("L1", qty("11")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void transformationDeductsInputsAndCreatesOutputsAtomically() {
        register("A", "10");
        register("B", "5");
        MutationResult<TransformationView> result = transform("T1",
                List.of(item("A", "7"), item("B", "5")),
                List.of(item("C", "8"), item("D", "3")), "1");

        assertThat(result.created()).isTrue();
        assertThat(lotService.stock("A").quantity()).isEqualByComparingTo("3.000");
        assertThat(lotService.stock("B").quantity()).isEqualByComparingTo("0.000");
        assertThat(lotService.stock("C").quantity()).isEqualByComparingTo("8.000");
        assertThat(lotService.stock("D").quantity()).isEqualByComparingTo("3.000");
        assertThat(lotService.lineage("D", "upstream").relatedLots())
                .extracting("lotNumber").containsExactlyInAnyOrder("A", "B");
        assertThat(lotService.lineage("A", "downstream").relatedLots())
                .extracting("lotNumber").containsExactlyInAnyOrder("C", "D");
    }

    @Test
    void transformationFailsWhenMassIsNotConserved() {
        register("A", "10");
        assertThatThrownBy(() -> transform("T1", List.of(item("A", "10")),
                List.of(item("B", "9")), "0"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(lotService.stock("A").quantity()).isEqualByComparingTo("10.000");
        assertThat(lotRepository.findByLotNumber("B")).isEmpty();
    }

    @Test
    void transformationFailsAtomicallyWhenStockIsInsufficient() {
        register("A", "10");
        register("B", "1");
        assertThatThrownBy(() -> transform("T1",
                List.of(item("A", "4"), item("B", "4")),
                List.of(item("C", "8")), "0"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(lotService.stock("A").quantity()).isEqualByComparingTo("10.000");
        assertThat(lotService.stock("B").quantity()).isEqualByComparingTo("1.000");
        assertThat(lotRepository.findByLotNumber("C")).isEmpty();
        assertThat(edgeRepository.findAll()).isEmpty();
    }

    @Test
    void transformationFailsOnDuplicateOutputLot() {
        register("A", "10");
        register("C", "1");
        assertThatThrownBy(() -> transform("T1", List.of(item("A", "10")),
                List.of(item("C", "10")), "0"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void transformationIsIdempotentAndRejectsConflictingContent() {
        register("A", "10");
        transform("T1", List.of(item("A", "10")), List.of(item("B", "10")), "0");

        MutationResult<TransformationView> replay = transform("T1",
                List.of(item("A", "10.0")), List.of(item("B", "10")), "0");
        assertThat(replay.created()).isFalse();
        assertThat(lotService.stock("A").quantity()).isEqualByComparingTo("0.000");

        assertThatThrownBy(() -> transform("T1", List.of(item("A", "10")),
                List.of(item("B", "9")), "1"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void concurrentTransformationsNeverOverConsume() throws InterruptedException {
        register("S", "10");
        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            int n = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    transform("TX" + n, List.of(item("S", "4")), List.of(item("O" + n, "4")), "0");
                    successes.incrementAndGet();
                } catch (ApiException e) {
                    if (e.getStatus() == HttpStatus.CONFLICT) {
                        conflicts.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(2);
        assertThat(conflicts.get()).isEqualTo(3);
        assertThat(lotService.stock("S").quantity()).isEqualByComparingTo("2.000");
        BigDecimal total = lotRepository.findAll().stream()
                .map(l -> l.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("10.000");
    }

    @Test
    void recallPropagatesThroughDiamondWithoutDuplicateImpacts() {
        register("A", "10");
        transform("T1", List.of(item("A", "10")), List.of(item("B", "5"), item("C", "5")), "0");
        transform("T2", List.of(item("B", "5"), item("C", "5")), List.of(item("D", "10")), "0");

        MutationResult<RecallView> recall = recallService.initiate("R1", "A", "salmonella");
        assertThat(recall.created()).isTrue();
        assertThat(recall.view().impactedLots()).containsExactlyInAnyOrder("A", "B", "C", "D");

        assertThat(impactRepository.findByRecallNumber("R1")).hasSize(4);
        assertThat(lotService.stock("D").quarantined()).isTrue();
        assertThat(lotService.stock("D").activeRecalls())
                .extracting("recallNumber").containsExactly("R1");
    }

    @Test
    void closingOneRecallKeepsLotQuarantinedByAnother() {
        register("A", "5");
        register("B", "5");
        transform("T1", List.of(item("A", "5"), item("B", "5")), List.of(item("C", "10")), "0");

        recallService.initiate("R1", "A", "reason-a");
        recallService.initiate("R2", "B", "reason-b");

        LotStockView stock = lotService.stock("C");
        assertThat(stock.quarantined()).isTrue();
        assertThat(stock.activeRecalls()).extracting("recallNumber")
                .containsExactlyInAnyOrder("R1", "R2");

        recallService.close("R1");
        LotStockView afterClose = lotService.stock("C");
        assertThat(afterClose.quarantined()).isTrue();
        assertThat(afterClose.activeRecalls()).extracting("recallNumber").containsExactly("R2");

        recallService.close("R2");
        assertThat(lotService.stock("C").quarantined()).isFalse();
        assertThat(lotService.stock("C").activeRecalls()).isEmpty();
    }

    @Test
    void recallInitiationAndCloseAreIdempotentAndConflictOnDifferentContent() {
        register("A", "5");
        recallService.initiate("R1", "A", "reason-a");

        MutationResult<RecallView> replay = recallService.initiate("R1", "A", "reason-a");
        assertThat(replay.created()).isFalse();
        assertThat(impactRepository.findByRecallNumber("R1")).hasSize(1);

        assertThatThrownBy(() -> recallService.initiate("R1", "A", "other-reason"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(recallService.close("R1").created()).isTrue();
        MutationResult<RecallView> secondClose = recallService.close("R1");
        assertThat(secondClose.created()).isFalse();
        assertThat(secondClose.view().status()).isEqualTo("CLOSED");
    }

    @Test
    void newOutputsInheritOpenRecallsFromInputs() {
        register("A", "10");
        recallService.initiate("R1", "A", "reason-a");

        transform("T1", List.of(item("A", "10")), List.of(item("B", "10")), "0");

        assertThat(lotService.stock("B").quarantined()).isTrue();
        assertThat(lotService.stock("B").activeRecalls())
                .extracting("recallNumber").containsExactly("R1");
        assertThat(recallService.detail("R1").impactedLots()).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void concurrentRecallAndTransformationNeverLetDescendantsEscape() throws InterruptedException {
        for (int round = 0; round < 10; round++) {
            cleanUp();
            register("R", "10");
            transform("T0", List.of(item("R", "10")), List.of(item("A", "10")), "0");

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            pool.submit(() -> {
                try {
                    go.await();
                    recallService.initiate("R1", "R", "reason");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            pool.submit(() -> {
                try {
                    go.await();
                    transform("T1", List.of(item("A", "10")), List.of(item("B", "10")), "0");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            assertThat(lotService.stock("B").quarantined())
                    .as("round %s: descendant B must be quarantined", round)
                    .isTrue();
            assertThat(recallService.detail("R1").impactedLots())
                    .containsExactlyInAnyOrder("R", "A", "B");
        }
    }
}

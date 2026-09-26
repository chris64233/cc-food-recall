package com.chris64233.cc.foodrecall;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.LotRepository;
import com.chris64233.cc.foodrecall.service.RecallReportService;
import com.chris64233.cc.foodrecall.service.RecallService;
import com.chris64233.cc.foodrecall.service.ShipmentService;
import com.chris64233.cc.foodrecall.service.TransformationService;
import com.chris64233.cc.foodrecall.web.Dtos.CloseRequest;
import com.chris64233.cc.foodrecall.web.Dtos.LotAmount;
import com.chris64233.cc.foodrecall.web.Dtos.RecallRequest;
import com.chris64233.cc.foodrecall.web.Dtos.RegisterLotRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ReportRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ShipmentRequest;
import com.chris64233.cc.foodrecall.web.Dtos.TransformRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyTests {

    @Autowired
    private TransformationService transformationService;

    @Autowired
    private RecallService recallService;

    @Autowired
    private com.chris64233.cc.foodrecall.service.LotService lotService;

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private RecallReportService reportService;

    @Autowired
    private com.chris64233.cc.foodrecall.service.RecallQueryService queryService;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM recall_reports");
        jdbcTemplate.update("DELETE FROM recall_notifications");
        jdbcTemplate.update("DELETE FROM shipments");
        jdbcTemplate.update("DELETE FROM recall_impacts");
        jdbcTemplate.update("DELETE FROM transformation_inputs");
        jdbcTemplate.update("DELETE FROM transformation_outputs");
        jdbcTemplate.update("DELETE FROM recall_events");
        jdbcTemplate.update("DELETE FROM transformations");
        jdbcTemplate.update("DELETE FROM lots");
    }

    @Test
    void concurrentTransformsCannotConsumeMoreThanAvailableStock() throws Exception {
        lotRepository.save(new Lot("A", new BigDecimal("100.000")));

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(2, i -> {
            TransformRequest request = new TransformRequest("T-" + i,
                    List.of(new LotAmount("A", new BigDecimal("60"))),
                    List.of(new LotAmount("B-" + i, new BigDecimal("60"))),
                    BigDecimal.ZERO);
            try {
                transformationService.transform(request);
                successes.incrementAndGet();
            } catch (ApiException ex) {
                assertThat(ex.getStatus().value()).isEqualTo(409);
                conflicts.incrementAndGet();
            }
        });

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(lotRepository.findByLotNumber("A").orElseThrow().getQuantity())
                .isEqualByComparingTo("40.000");
        // 两个并发转换恰好一个成功，但哪一个获胜是不确定的
        boolean b0Exists = lotRepository.findByLotNumber("B-0").isPresent();
        boolean b1Exists = lotRepository.findByLotNumber("B-1").isPresent();
        assertThat(b0Exists).isNotEqualTo(b1Exists);
    }

    @Test
    void concurrentTransformsPartitionStockExactly() throws Exception {
        lotRepository.save(new Lot("S", new BigDecimal("100.000")));

        AtomicInteger successes = new AtomicInteger();
        runConcurrently(10, i -> {
            transformationService.transform(new TransformRequest("TT-" + i,
                    List.of(new LotAmount("S", new BigDecimal("10"))),
                    List.of(new LotAmount("P-" + i, new BigDecimal("10"))),
                    BigDecimal.ZERO));
            successes.incrementAndGet();
        });

        assertThat(successes.get()).isEqualTo(10);
        assertThat(lotRepository.findByLotNumber("S").orElseThrow().getQuantity())
                .isEqualByComparingTo("0.000");
        for (int i = 0; i < 10; i++) {
            assertThat(lotRepository.findByLotNumber("P-" + i)).isPresent();
        }
    }

    @Test
    void concurrentIdenticalRegistrationsAreIdempotent() throws Exception {
        AtomicInteger failures = new AtomicInteger();
        runConcurrently(5, i -> {
            try {
                lotService.register(new RegisterLotRequest("LOT-X", new BigDecimal("50")));
            } catch (RuntimeException ex) {
                failures.incrementAndGet();
            }
        });
        assertThat(failures.get()).isZero();
        assertThat(lotRepository.findAll()).hasSize(1);
        assertThat(lotRepository.findByLotNumber("LOT-X").orElseThrow().getQuantity())
                .isEqualByComparingTo("50.000");
    }

    @Test
    void recallAndTransformRaceCannotLetNewDescendantEscape() throws Exception {
        lotRepository.save(new Lot("R", new BigDecimal("100.000")));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> {
                ready.countDown();
                await(start);
                recallService.initiate(new RecallRequest("RC-1", "R", "contamination"));
            });
            executor.submit(() -> {
                ready.countDown();
                await(start);
                transformationService.transform(new TransformRequest("TR-1",
                        List.of(new LotAmount("R", new BigDecimal("30"))),
                        List.of(new LotAmount("CHILD", new BigDecimal("30"))),
                        BigDecimal.ZERO));
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }

        Lot child = lotRepository.findByLotNumber("CHILD").orElseThrow();
        assertThat(child.isQuarantined()).isTrue();
        assertThat(lotRepository.findByLotNumber("R").orElseThrow().isQuarantined()).isTrue();
    }

    @Test
    void concurrentClosesWithDifferentCloseNumbersYieldSingleWinner() throws Exception {
        lotRepository.save(new Lot("A", new BigDecimal("100.000")));
        recallService.initiate(new RecallRequest("RC-2", "A", "contamination"));

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(2, i -> {
            try {
                recallService.close("RC-2", new CloseRequest("C-" + i, "approver-" + i, null));
                successes.incrementAndGet();
            } catch (ApiException ex) {
                assertThat(ex.getStatus().value()).isEqualTo(409);
                conflicts.incrementAndGet();
            }
        });

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
    }

    @Test
    void concurrentIdenticalReportsAreIdempotent() throws Exception {
        lotRepository.save(new Lot("A", new BigDecimal("100.000")));
        shipmentService.ship(new ShipmentRequest("S-1", "A", "H1", new BigDecimal("40")));
        recallService.initiate(new RecallRequest("RC-3", "A", "contamination"));

        AtomicInteger successes = new AtomicInteger();
        runConcurrently(5, i -> {
            reportService.report("RC-3", new ReportRequest("RP-1", "H1", "ISOLATED",
                    new BigDecimal("10"), null));
            successes.incrementAndGet();
        });

        assertThat(successes.get()).isEqualTo(5);
        // 同一报告号只计量一次
        var holders = queryService.holderResponses("RC-3");
        assertThat(holders.holders()).hasSize(1);
        assertThat(holders.holders().get(0).isolatedQuantity()).isEqualByComparingTo("10.000");
    }

    @Test
    void closeRacingWithReportInvalidatesStaleDecision() throws Exception {
        lotRepository.save(new Lot("A", new BigDecimal("100.000")));
        shipmentService.ship(new ShipmentRequest("S-1", "A", "H1", new BigDecimal("40")));
        recallService.initiate(new RecallRequest("RC-4", "A", "contamination"));
        reportService.report("RC-4", new ReportRequest("RP-1", "H1", "ISOLATED",
                new BigDecimal("30"), null));
        long decidedVersion = queryService.effectiveness("RC-4").statsVersion();

        // 关闭（基于 decidedVersion）与新的下游报告并发：序列化后恰好一个成功
        AtomicInteger closeSucceeded = new AtomicInteger();
        AtomicInteger reportSucceeded = new AtomicInteger();
        runConcurrently(2, i -> {
            try {
                if (i == 0) {
                    recallService.close("RC-4",
                            new CloseRequest("C-1", "qa-lead", decidedVersion));
                    closeSucceeded.incrementAndGet();
                } else {
                    reportService.report("RC-4", new ReportRequest("RP-2", "H1", "CONSUMED",
                            new BigDecimal("10"), null));
                    reportSucceeded.incrementAndGet();
                }
            } catch (ApiException ex) {
                assertThat(ex.getStatus().value()).isEqualTo(409);
            }
        });

        // 报告先提交则关闭因统计版本过旧而失效；关闭先提交则报告因召回已关闭被拒绝
        assertThat(closeSucceeded.get() + reportSucceeded.get()).isEqualTo(1);
    }

    private void runConcurrently(int threads, ThrowingConsumer<Integer> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    task.accept(index);
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value);
    }
}

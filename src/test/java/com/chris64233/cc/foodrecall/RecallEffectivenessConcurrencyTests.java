package com.chris64233.cc.foodrecall;

import com.chris64233.cc.foodrecall.domain.Lot;
import com.chris64233.cc.foodrecall.error.ApiException;
import com.chris64233.cc.foodrecall.repository.LotRepository;
import com.chris64233.cc.foodrecall.service.DestinationService;
import com.chris64233.cc.foodrecall.service.HolderService;
import com.chris64233.cc.foodrecall.service.RecallService;
import com.chris64233.cc.foodrecall.service.TransformationService;
import com.chris64233.cc.foodrecall.web.Dtos.CloseRecallRequest;
import com.chris64233.cc.foodrecall.web.Dtos.LotAmount;
import com.chris64233.cc.foodrecall.web.Dtos.RecallRequest;
import com.chris64233.cc.foodrecall.web.Dtos.RegisterDestinationRequest;
import com.chris64233.cc.foodrecall.web.Dtos.RegisterHolderRequest;
import com.chris64233.cc.foodrecall.web.Dtos.RegisterLotRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ReportItemRequest;
import com.chris64233.cc.foodrecall.web.Dtos.ReportRequest;
import com.chris64233.cc.foodrecall.web.Dtos.TransformRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecallEffectivenessConcurrencyTests {

    @Autowired
    private RecallService recallService;
    @Autowired
    private TransformationService transformationService;
    @Autowired
    private HolderService holderService;
    @Autowired
    private DestinationService destinationService;
    @Autowired
    private com.chris64233.cc.foodrecall.service.LotService lotService;
    @Autowired
    private LotRepository lotRepository;
    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void clean() {
        databaseCleaner.clean();
    }

    /**
     * 关闭确认（携带旧统计版本 0、门槛放宽到 0）与新的下游报告并发：
     * 报告先提交则关闭必须因版本过期失效；关闭先提交则报告必须被拒绝。
     * 绝不允许出现“已关闭但有一份未计入统计的报告”。
     */
    @RepeatedTest(20)
    void closeRacingWithNewReportEitherStopsReportOrIsInvalidated() throws Exception {
        seedRecallWithOneDestination("RC-RP", 100, 50);

        AtomicInteger closeConflicts = new AtomicInteger();
        AtomicInteger reportConflicts = new AtomicInteger();
        runInParallel(
                () -> {
                    try {
                        recallService.close("RC-RP",
                                new CloseRecallRequest("C-RP", "qa", 0L));
                    } catch (ApiException ex) {
                        assertThat(ex.getStatus().value()).isEqualTo(409);
                        closeConflicts.incrementAndGet();
                    }
                },
                () -> {
                    try {
                        recallService.report("RC-RP", new ReportRequest("RP-RACE", "H1",
                                List.of(new ReportItemRequest("A", "CONSUMED", new BigDecimal("10"), null))));
                    } catch (ApiException ex) {
                        assertThat(ex.getStatus().value()).isEqualTo(409);
                        reportConflicts.incrementAndGet();
                    }
                });

        // 恰好一方失败：关闭失效（报告生效，召回仍 OPEN）或报告被拒（召回已 CLOSED）。
        assertThat(closeConflicts.get() + reportConflicts.get()).isEqualTo(1);
        var effectiveness = recallService.getEffectiveness("RC-RP");
        if (closeConflicts.get() == 1) {
            assertThat(effectiveness.status()).isEqualTo("OPEN");
            assertThat(effectiveness.statisticsVersion()).isGreaterThan(0);
            assertThat(effectiveness.consumedQuantity()).isEqualByComparingTo("10");
        } else {
            assertThat(effectiveness.status()).isEqualTo("CLOSED");
            assertThat(effectiveness.closure().basedOnVersion()).isZero();
            assertThat(effectiveness.consumedQuantity()).isEqualByComparingTo("0");
        }
    }

    /**
     * 关闭确认与新生产转换并发：转换先提交则追加影响并推进版本，关闭决定失效；
     * 关闭先提交则转换产出的新批次不被追加到已关闭召回。两种结局都必须状态自洽。
     */
    @RepeatedTest(20)
    void closeRacingWithNewTransformationIsInvalidatedWhenTransformLandsFirst() throws Exception {
        seedRecallWithOneDestination("RC-TX", 200, 50);

        AtomicInteger closeConflicts = new AtomicInteger();
        AtomicInteger transformFailures = new AtomicInteger();
        runInParallel(
                () -> {
                    try {
                        recallService.close("RC-TX",
                                new CloseRecallRequest("C-TX", "qa", 0L));
                    } catch (ApiException ex) {
                        assertThat(ex.getStatus().value()).isEqualTo(409);
                        closeConflicts.incrementAndGet();
                    }
                },
                () -> {
                    try {
                        transformationService.transform(new TransformRequest("TR-RACE",
                                List.of(new LotAmount("A", new BigDecimal("30"))),
                                List.of(new LotAmount("G", new BigDecimal("30"))),
                                BigDecimal.ZERO));
                    } catch (ApiException ex) {
                        transformFailures.incrementAndGet();
                    }
                });

        var effectiveness = recallService.getEffectiveness("RC-TX");
        Lot child = lotRepository.findByLotNumber("G").orElseThrow();
        if (closeConflicts.get() == 1) {
            // 转换先落地：G 追加进影响清单（第 2 批），召回仍 OPEN。
            assertThat(effectiveness.status()).isEqualTo("OPEN");
            assertThat(effectiveness.statisticsVersion()).isGreaterThan(0);
            assertThat(effectiveness.affectedLotCount()).isEqualTo(2);
            assertThat(child.isQuarantined()).isTrue();
        } else {
            // 关闭先落地：G 是关闭后才出现的新批次，不进入该召回，转换自身成功。
            assertThat(effectiveness.status()).isEqualTo("CLOSED");
            assertThat(transformFailures.get()).isZero();
            assertThat(effectiveness.affectedLotCount()).isEqualTo(1);
            assertThat(child.isQuarantined()).isFalse();
        }
    }

    /**
     * 同一持有方就同一批次并发提交两份互斥的完整报告（各 50，接收量仅 50）：
     * 守恒校验必须保证只有一份生效，另一份 409，绝不会两者都入账。
     */
    @Test
    void concurrentReportsCannotBothExceedReceivedQuantity() throws Exception {
        seedRecallWithOneDestination("RC-CONS", 100, 50);

        AtomicInteger conflicts = new AtomicInteger();
        runInParallel(
                () -> submitFullReport("RP-C1", conflicts),
                () -> submitFullReport("RP-C2", conflicts));

        assertThat(conflicts.get()).isEqualTo(1);
        var effectiveness = recallService.getEffectiveness("RC-CONS");
        assertThat(effectiveness.quarantinedQuantity())
                .usingComparator(BigDecimal::compareTo).isEqualByComparingTo("50");
    }

    private void submitFullReport(String reportNumber, AtomicInteger conflicts) {
        try {
            recallService.report("RC-CONS", new ReportRequest(reportNumber, "H1",
                    List.of(new ReportItemRequest("A", "QUARANTINED", new BigDecimal("50"), null))));
        } catch (ApiException ex) {
            assertThat(ex.getStatus().value()).isEqualTo(409);
            conflicts.incrementAndGet();
        }
    }

    private void seedRecallWithOneDestination(String recallNumber, int produced, int shipped) {
        lotService.register(new RegisterLotRequest("A", new BigDecimal(produced + ".000")));
        holderService.register(new RegisterHolderRequest("H1", "distributor-1"));
        destinationService.register(new RegisterDestinationRequest(
                "DS-" + recallNumber, "A", "H1", new BigDecimal(shipped + ".000")));
        // 门槛放宽到 0：并发场景里仅由统计版本决定关闭是否失效，与响应率无关。
        recallService.initiate(new RecallRequest(recallNumber, "A", "contamination", BigDecimal.ZERO));
    }

    private void runInParallel(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> {
                ready.countDown();
                await(start);
                first.run();
            });
            executor.submit(() -> {
                ready.countDown();
                await(start);
                second.run();
            });
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
}

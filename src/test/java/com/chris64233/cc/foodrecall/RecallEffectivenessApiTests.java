package com.chris64233.cc.foodrecall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecallEffectivenessApiTests extends TestSupport {

    private void register(String lotNumber, double quantity) throws Exception {
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"" + lotNumber + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private void transform(String body) throws Exception {
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void ship(String key, String lot, String holder, double quantity) throws Exception {
        mockMvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentKey\":\"" + key + "\",\"lotNumber\":\"" + lot
                                + "\",\"holder\":\"" + holder + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private void recall(String number, String lot, String reason) throws Exception {
        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallNumber\":\"" + number + "\",\"lotNumber\":\"" + lot
                                + "\",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk());
    }

    private void reportOk(String recall, String body) throws Exception {
        mockMvc.perform(post("/api/recalls/" + recall + "/reports")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void reportConflict(String recall, String body) throws Exception {
        mockMvc.perform(post("/api/recalls/" + recall + "/reports")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    private void closeOk(String recall, String body) throws Exception {
        mockMvc.perform(post("/api/recalls/" + recall + "/close")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void closeConflict(String recall, String body) throws Exception {
        mockMvc.perform(post("/api/recalls/" + recall + "/close")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void initiationCreatesImmutableNotificationsAndAppendsNewDestinations() throws Exception {
        register("A", 100);
        transform("""
                {"transformationId":"T-1",
                 "inputs":[{"lotNumber":"A","quantity":40}],
                 "outputs":[{"lotNumber":"C","quantity":40}],"loss":0}
                """);
        ship("S-1", "C", "H1", 10);
        ship("S-2", "C", "H2", 25);
        ship("S-3", "A", "H1", 5);

        recall("R-1", "A", "a-contamination");

        // 初始通知：同一持有方的多条发货合并为一条通知
        mockMvc.perform(get("/api/recalls/R-1/notifications"))
                .andExpect(jsonPath("$.notifications.length()").value(2))
                .andExpect(jsonPath("$.notifications[0].notificationNumber").value("R-1-N1"))
                .andExpect(jsonPath("$.notifications[0].holder").value("H1"))
                .andExpect(jsonPath("$.notifications[0].quantity").value(15.0))
                .andExpect(jsonPath("$.notifications[0].source").value("SHIPMENT"))
                .andExpect(jsonPath("$.notifications[1].notificationNumber").value("R-1-N2"))
                .andExpect(jsonPath("$.notifications[1].holder").value("H2"))
                .andExpect(jsonPath("$.notifications[1].quantity").value(25.0));

        // 重复发起同一召回：幂等，不产生重复通知
        recall("R-1", "A", "a-contamination");
        mockMvc.perform(get("/api/recalls/R-1/notifications"))
                .andExpect(jsonPath("$.notifications.length()").value(2));

        // 召回后新发现的去向：追加新通知，原通知记录不变
        ship("S-4", "C", "H1", 5);
        mockMvc.perform(get("/api/recalls/R-1/notifications"))
                .andExpect(jsonPath("$.notifications.length()").value(3))
                .andExpect(jsonPath("$.notifications[0].notificationNumber").value("R-1-N1"))
                .andExpect(jsonPath("$.notifications[0].quantity").value(15.0))
                .andExpect(jsonPath("$.notifications[2].notificationNumber").value("R-1-N3"))
                .andExpect(jsonPath("$.notifications[2].holder").value("H1"))
                .andExpect(jsonPath("$.notifications[2].quantity").value(5.0));

        // 影响批次查询
        mockMvc.perform(get("/api/recalls/R-1/impacts"))
                .andExpect(jsonPath("$.impacts.length()").value(2))
                .andExpect(jsonPath("$.impacts[0].lotNumber").value("A"))
                .andExpect(jsonPath("$.impacts[1].lotNumber").value("C"));

        // 持有方接收量 = 初始通知 + 追加通知
        mockMvc.perform(get("/api/recalls/R-1/holders"))
                .andExpect(jsonPath("$.holders[0].holder").value("H1"))
                .andExpect(jsonPath("$.holders[0].receivedQuantity").value(20.0))
                .andExpect(jsonPath("$.holders[0].responded").value(false))
                .andExpect(jsonPath("$.holders[1].holder").value("H2"))
                .andExpect(jsonPath("$.holders[1].receivedQuantity").value(25.0));
    }

    @Test
    void multiPathDeliveryToSameHolderIsNotDoubleCounted() throws Exception {
        // A -> C -> D -> F 与 A -> E -> F 两条路径汇合到 F
        register("A", 100);
        register("B", 50);
        transform("""
                {"transformationId":"T-1",
                 "inputs":[{"lotNumber":"A","quantity":30},{"lotNumber":"B","quantity":10}],
                 "outputs":[{"lotNumber":"C","quantity":40}],"loss":0}
                """);
        transform("""
                {"transformationId":"T-2",
                 "inputs":[{"lotNumber":"C","quantity":30}],
                 "outputs":[{"lotNumber":"D","quantity":30}],"loss":0}
                """);
        transform("""
                {"transformationId":"T-3",
                 "inputs":[{"lotNumber":"A","quantity":20}],
                 "outputs":[{"lotNumber":"E","quantity":20}],"loss":0}
                """);
        transform("""
                {"transformationId":"T-4",
                 "inputs":[{"lotNumber":"D","quantity":30},{"lotNumber":"E","quantity":20}],
                 "outputs":[{"lotNumber":"F","quantity":50}],"loss":0}
                """);
        ship("S-1", "F", "H1", 50);
        ship("S-2", "C", "H1", 10);

        recall("R-1", "A", "a-contamination");

        // F 经两条谱系路径携带 A 物料，但持有方接收量只按发货记录计一次
        mockMvc.perform(get("/api/recalls/R-1/holders"))
                .andExpect(jsonPath("$.holders.length()").value(1))
                .andExpect(jsonPath("$.holders[0].receivedQuantity").value(60.0));
        mockMvc.perform(get("/api/recalls/R-1/effectiveness"))
                .andExpect(jsonPath("$.dispatchedQuantity").value(60.0))
                .andExpect(jsonPath("$.notifiedHolders").value(1));
    }

    @Test
    void reportsMustConserveWithReceivedQuantity() throws Exception {
        register("A", 100);
        ship("S-1", "A", "H1", 40);
        recall("R-1", "A", "a-contamination");

        reportOk("R-1", """
                {"reportNumber":"RP-1","holder":"H1","type":"ISOLATED","quantity":30}
                """);
        // 30 + 15 > 40，超出接收量
        reportConflict("R-1", """
                {"reportNumber":"RP-2","holder":"H1","type":"ISOLATED","quantity":15}
                """);
        reportOk("R-1", """
                {"reportNumber":"RP-3","holder":"H1","type":"CONSUMED","quantity":10}
                """);
        // 已累计报告 40，再报任何数量都破坏守恒
        reportConflict("R-1", """
                {"reportNumber":"RP-4","holder":"H1","type":"MISMATCH","quantity":1}
                """);
        // 未收到通知的持有方不能报告
        reportConflict("R-1", """
                {"reportNumber":"RP-5","holder":"H9","type":"ISOLATED","quantity":1}
                """);

        mockMvc.perform(get("/api/recalls/R-1/holders"))
                .andExpect(jsonPath("$.holders[0].isolatedQuantity").value(30.0))
                .andExpect(jsonPath("$.holders[0].consumedQuantity").value(10.0))
                .andExpect(jsonPath("$.holders[0].outstandingQuantity").value(0.0))
                .andExpect(jsonPath("$.holders[0].responded").value(true));

        // 关闭后不再接受报告
        closeOk("R-1", """
                {"closeNumber":"C-1","approvedBy":"qa-lead"}
                """);
        reportConflict("R-1", """
                {"reportNumber":"RP-6","holder":"H1","type":"MISMATCH","quantity":0.5}
                """);
    }

    @Test
    void reportNumberIsIdempotent() throws Exception {
        register("A", 100);
        ship("S-1", "A", "H1", 40);
        recall("R-1", "A", "a-contamination");

        String body = """
                {"reportNumber":"RP-1","holder":"H1","type":"ISOLATED","quantity":10}
                """;
        reportOk("R-1", body);
        // 相同报告号相同内容：幂等返回，不重复计量
        reportOk("R-1", body);
        mockMvc.perform(get("/api/recalls/R-1/holders"))
                .andExpect(jsonPath("$.holders[0].isolatedQuantity").value(10.0));
        // 相同报告号不同内容：409
        reportConflict("R-1", """
                {"reportNumber":"RP-1","holder":"H1","type":"ISOLATED","quantity":20}
                """);
        // 报告类型非法：400
        mockMvc.perform(post("/api/recalls/R-1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportNumber\":\"RP-9\",\"holder\":\"H1\",\"type\":\"BURNED\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferAppendsNotificationAndChainsConservation() throws Exception {
        register("A", 100);
        ship("S-1", "A", "H1", 40);
        recall("R-1", "A", "a-contamination");

        // H1 转交 15 给 H2：为 H2 追加通知，原通知不变
        reportOk("R-1", """
                {"reportNumber":"RP-1","holder":"H1","type":"TRANSFERRED","quantity":15,"transferredTo":"H2"}
                """);
        mockMvc.perform(get("/api/recalls/R-1/notifications"))
                .andExpect(jsonPath("$.notifications.length()").value(2))
                .andExpect(jsonPath("$.notifications[0].quantity").value(40.0))
                .andExpect(jsonPath("$.notifications[1].notificationNumber").value("R-1-N2"))
                .andExpect(jsonPath("$.notifications[1].holder").value("H2"))
                .andExpect(jsonPath("$.notifications[1].quantity").value(15.0))
                .andExpect(jsonPath("$.notifications[1].source").value("TRANSFER"));

        // H2 只能按接收到的 15 报告
        reportConflict("R-1", """
                {"reportNumber":"RP-2","holder":"H2","type":"ISOLATED","quantity":16}
                """);
        reportOk("R-1", """
                {"reportNumber":"RP-3","holder":"H2","type":"ISOLATED","quantity":15}
                """);
        reportOk("R-1", """
                {"reportNumber":"RP-4","holder":"H1","type":"ISOLATED","quantity":25}
                """);

        mockMvc.perform(get("/api/recalls/R-1/effectiveness"))
                .andExpect(jsonPath("$.dispatchedQuantity").value(40.0))
                .andExpect(jsonPath("$.isolatedQuantity").value(40.0))
                .andExpect(jsonPath("$.transferredQuantity").value(15.0))
                .andExpect(jsonPath("$.unrecoveredQuantity").value(0.0))
                .andExpect(jsonPath("$.completionRate").value(1.0))
                .andExpect(jsonPath("$.notifiedHolders").value(2))
                .andExpect(jsonPath("$.respondedHolders").value(2));
    }

    @Test
    void closeRequiresResponseThresholdAndRecordsApproverAndUnrecovered() throws Exception {
        register("A", 100);
        ship("S-1", "A", "H1", 40);
        ship("S-2", "A", "H2", 10);
        recall("R-1", "A", "a-contamination");

        reportOk("R-1", """
                {"reportNumber":"RP-1","holder":"H1","type":"ISOLATED","quantity":35}
                """);
        // H2 未响应，默认门槛 100% 不满足
        closeConflict("R-1", """
                {"closeNumber":"C-1","approvedBy":"qa-lead"}
                """);
        // 缺少批准人：400
        mockMvc.perform(post("/api/recalls/R-1/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closeNumber\":\"C-1\"}"))
                .andExpect(status().isBadRequest());

        reportOk("R-1", """
                {"reportNumber":"RP-2","holder":"H2","type":"CONSUMED","quantity":10}
                """);
        closeOk("R-1", """
                {"closeNumber":"C-1","approvedBy":"qa-lead"}
                """);

        // 关闭记录未收回数量与批准人
        mockMvc.perform(get("/api/recalls/R-1/effectiveness"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.unrecoveredQuantity").value(5.0))
                .andExpect(jsonPath("$.approvedBy").value("qa-lead"))
                .andExpect(jsonPath("$.closeNumber").value("C-1"))
                .andExpect(jsonPath("$.completionRate").value(0.9));

        // 关闭号幂等：相同关闭号返回原结果，不同关闭号冲突
        closeOk("R-1", """
                {"closeNumber":"C-1","approvedBy":"qa-lead"}
                """);
        closeConflict("R-1", """
                {"closeNumber":"C-2","approvedBy":"qa-lead"}
                """);
    }

    @Test
    void staleCloseIsInvalidatedByNewReport() throws Exception {
        register("A", 100);
        ship("S-1", "A", "H1", 40);
        recall("R-1", "A", "a-contamination");
        reportOk("R-1", """
                {"reportNumber":"RP-1","holder":"H1","type":"ISOLATED","quantity":30}
                """);

        // 客户端基于版本 1 的统计决定关闭
        mockMvc.perform(get("/api/recalls/R-1/effectiveness"))
                .andExpect(jsonPath("$.statsVersion").value(1));

        // 决定之后又有新报告，统计版本前进
        reportOk("R-1", """
                {"reportNumber":"RP-2","holder":"H1","type":"CONSUMED","quantity":10}
                """);

        // 基于旧版本的关闭决定失效
        closeConflict("R-1", """
                {"closeNumber":"C-1","approvedBy":"qa-lead","expectedStatsVersion":1}
                """);
        // 基于最新版本可以关闭
        closeOk("R-1", """
                {"closeNumber":"C-1","approvedBy":"qa-lead","expectedStatsVersion":2}
                """);
        mockMvc.perform(get("/api/recalls/R-1/effectiveness"))
                .andExpect(jsonPath("$.unrecoveredQuantity").value(0.0));
    }

    @Test
    void staleCloseIsInvalidatedByNewTransformation() throws Exception {
        register("A", 100);
        recall("R-1", "A", "a-contamination");
        mockMvc.perform(get("/api/recalls/R-1/effectiveness"))
                .andExpect(jsonPath("$.statsVersion").value(0));

        // 关闭决定作出后，新的生产转换把召回传播到新批次
        transform("""
                {"transformationId":"T-1",
                 "inputs":[{"lotNumber":"A","quantity":30}],
                 "outputs":[{"lotNumber":"G","quantity":30}],"loss":0}
                """);

        closeConflict("R-1", """
                {"closeNumber":"C-1","approvedBy":"qa-lead","expectedStatsVersion":0}
                """);
        closeOk("R-1", """
                {"closeNumber":"C-1","approvedBy":"qa-lead","expectedStatsVersion":1}
                """);
        // 影响清单包含追加的批次，关闭后仍保留
        mockMvc.perform(get("/api/recalls/R-1/impacts"))
                .andExpect(jsonPath("$.impacts.length()").value(2));
    }

    @Test
    void effectivenessAndDiscrepancyQueries() throws Exception {
        register("A", 100);
        ship("S-1", "A", "H1", 40);
        ship("S-2", "A", "H2", 20);
        recall("R-1", "A", "a-contamination");

        reportOk("R-1", """
                {"reportNumber":"RP-1","holder":"H1","type":"ISOLATED","quantity":30}
                """);
        reportOk("R-1", """
                {"reportNumber":"RP-2","holder":"H1","type":"MISMATCH","quantity":5}
                """);
        reportOk("R-1", """
                {"reportNumber":"RP-3","holder":"H2","type":"CONSUMED","quantity":20}
                """);

        mockMvc.perform(get("/api/recalls/R-1/effectiveness"))
                .andExpect(jsonPath("$.dispatchedQuantity").value(60.0))
                .andExpect(jsonPath("$.isolatedQuantity").value(30.0))
                .andExpect(jsonPath("$.consumedQuantity").value(20.0))
                .andExpect(jsonPath("$.mismatchQuantity").value(5.0))
                .andExpect(jsonPath("$.unrecoveredQuantity").value(10.0))
                .andExpect(jsonPath("$.completionRate").value(0.8333))
                .andExpect(jsonPath("$.responseRate").value(1.0));

        // 数量差异：H1 接收 40 已说明 35（含 5 数量不符），差异 5
        mockMvc.perform(get("/api/recalls/R-1/discrepancies"))
                .andExpect(jsonPath("$.discrepancies[0].holder").value("H1"))
                .andExpect(jsonPath("$.discrepancies[0].receivedQuantity").value(40.0))
                .andExpect(jsonPath("$.discrepancies[0].accountedQuantity").value(35.0))
                .andExpect(jsonPath("$.discrepancies[0].differenceQuantity").value(5.0))
                .andExpect(jsonPath("$.discrepancies[0].mismatchQuantity").value(5.0))
                .andExpect(jsonPath("$.discrepancies[1].holder").value("H2"))
                .andExpect(jsonPath("$.discrepancies[1].differenceQuantity").value(0.0));
    }

    @Test
    void shipmentIsIdempotentAndChecksStock() throws Exception {
        register("A", 100);
        ship("S-1", "A", "H1", 40);
        // 相同发货编号相同内容：幂等
        ship("S-1", "A", "H1", 40);
        mockMvc.perform(get("/api/lots/A"))
                .andExpect(jsonPath("$.quantity").value(60.0));
        // 相同编号不同内容：409
        mockMvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentKey\":\"S-1\",\"lotNumber\":\"A\",\"holder\":\"H1\",\"quantity\":41}"))
                .andExpect(status().isConflict());
        // 库存不足：409
        mockMvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentKey\":\"S-2\",\"lotNumber\":\"A\",\"holder\":\"H1\",\"quantity\":61}"))
                .andExpect(status().isConflict());
        // 批次不存在：404
        mockMvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentKey\":\"S-3\",\"lotNumber\":\"GHOST\",\"holder\":\"H1\",\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }
}

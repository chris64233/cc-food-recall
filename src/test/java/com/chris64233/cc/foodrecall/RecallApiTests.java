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
class RecallApiTests extends TestSupport {

    private void register(String lotNumber, double quantity) throws Exception {
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"" + lotNumber + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private void transform(String body) throws Exception {
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void buildConvergingGraph() throws Exception {
        register("A", 100);
        register("B", 50);
        transform("""
                {"transformationId":"T-1",
                 "inputs":[{"lotNumber":"A","quantity":30},{"lotNumber":"B","quantity":10}],
                 "outputs":[{"lotNumber":"C","quantity":40}],"loss":0}
                """);
        transform("""
                {"transformationId":"T-2",
                 "inputs":[{"lotNumber":"C","quantity":40}],
                 "outputs":[{"lotNumber":"D","quantity":40}],"loss":0}
                """);
        transform("""
                {"transformationId":"T-3",
                 "inputs":[{"lotNumber":"A","quantity":20}],
                 "outputs":[{"lotNumber":"E","quantity":20}],"loss":0}
                """);
        transform("""
                {"transformationId":"T-4",
                 "inputs":[{"lotNumber":"D","quantity":40},{"lotNumber":"E","quantity":20}],
                 "outputs":[{"lotNumber":"F","quantity":60}],"loss":0}
                """);
    }

    private void recall(String number, String lot, String reason) throws Exception {
        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallNumber\":\"" + number + "\",\"lotNumber\":\"" + lot
                                + "\",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk());
    }

    private void assertQuarantined(String lot, boolean expected) throws Exception {
        mockMvc.perform(get("/api/lots/" + lot))
                .andExpect(jsonPath("$.quarantined").value(expected));
    }

    @Test
    void recallPropagatesThroughMultipleLevelsParentsAndConvergence() throws Exception {
        buildConvergingGraph();

        String firstRecall = """
                {"recallNumber":"R-1","lotNumber":"A","reason":"a-contamination"}
                """;
        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON).content(firstRecall))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedLots").value(5))
                .andExpect(jsonPath("$.status").value("OPEN"));
        // 重复发起同一召回：幂等且不产生重复影响记录
        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON).content(firstRecall))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedLots").value(5));

        for (String lot : new String[]{"A", "C", "D", "E", "F"}) {
            assertQuarantined(lot, true);
            mockMvc.perform(get("/api/lots/" + lot))
                    .andExpect(jsonPath("$.recallReasons.length()").value(1))
                    .andExpect(jsonPath("$.recallReasons[0].recallNumber").value("R-1"));
        }
        assertQuarantined("B", false);
    }

    @Test
    void closingRecallRemovesOnlyItsReasonAndKeepsOtherQuarantine() throws Exception {
        buildConvergingGraph();
        recall("R-1", "A", "a-contamination");
        recall("R-2", "B", "b-contamination");

        // C/D/F 汇合了两条路径，各有两个召回原因
        mockMvc.perform(get("/api/lots/C"))
                .andExpect(jsonPath("$.quarantined").value(true))
                .andExpect(jsonPath("$.recallReasons.length()").value(2));

        mockMvc.perform(post("/api/recalls/R-1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        // 再次关闭幂等
        mockMvc.perform(post("/api/recalls/R-1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertQuarantined("A", false);
        assertQuarantined("E", false);
        mockMvc.perform(get("/api/lots/A"))
                .andExpect(jsonPath("$.recallReasons").isEmpty());

        for (String lot : new String[]{"B", "C", "D", "F"}) {
            assertQuarantined(lot, true);
            mockMvc.perform(get("/api/lots/" + lot))
                    .andExpect(jsonPath("$.recallReasons.length()").value(1))
                    .andExpect(jsonPath("$.recallReasons[0].recallNumber").value("R-2"));
        }

        mockMvc.perform(post("/api/recalls/R-2/close")).andExpect(status().isOk());
        for (String lot : new String[]{"A", "B", "C", "D", "E", "F"}) {
            assertQuarantined(lot, false);
        }
    }

    @Test
    void newProductionFromQuarantinedLotInheritsRecall() throws Exception {
        register("A", 100);
        recall("R-3", "A", "a-contamination");

        transform("""
                {"transformationId":"T-X",
                 "inputs":[{"lotNumber":"A","quantity":30}],
                 "outputs":[{"lotNumber":"G","quantity":30}],"loss":0}
                """);
        mockMvc.perform(get("/api/lots/G"))
                .andExpect(jsonPath("$.quarantined").value(true))
                .andExpect(jsonPath("$.recallReasons[0].recallNumber").value("R-3"))
                .andExpect(jsonPath("$.recallReasons[0].reason").value("a-contamination"));

        transform("""
                {"transformationId":"T-Y",
                 "inputs":[{"lotNumber":"G","quantity":30}],
                 "outputs":[{"lotNumber":"H","quantity":30}],"loss":0}
                """);
        mockMvc.perform(get("/api/lots/H"))
                .andExpect(jsonPath("$.quarantined").value(true))
                .andExpect(jsonPath("$.recallReasons[0].recallNumber").value("R-3"));
    }

    @Test
    void recallContentConflictReturns409AndUnknownResourcesReturn404() throws Exception {
        register("A", 100);
        recall("R-4", "A", "reason-one");
        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallNumber\":\"R-4\",\"lotNumber\":\"A\",\"reason\":\"reason-two\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallNumber\":\"R-5\",\"lotNumber\":\"GHOST\",\"reason\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/recalls/R-9/close"))
                .andExpect(status().isNotFound());
    }
}

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
class TransformationApiTests extends TestSupport {

    private void register(String lotNumber, double quantity) throws Exception {
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"" + lotNumber + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    private void transform(String body) throws Exception {
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void consumesInputsAtomicallyAndCreatesOutputs() throws Exception {
        register("A", 100);
        register("B", 50);
        transform("""
                {"transformationId":"T-1",
                 "inputs":[{"lotNumber":"A","quantity":40},{"lotNumber":"B","quantity":10}],
                 "outputs":[{"lotNumber":"C","quantity":40},{"lotNumber":"D","quantity":5}],
                 "loss":5}
                """);

        mockMvc.perform(get("/api/lots/A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(60.0));
        mockMvc.perform(get("/api/lots/B")).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(40.0));
        mockMvc.perform(get("/api/lots/C")).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(40.0))
                .andExpect(jsonPath("$.quarantined").value(false));
        mockMvc.perform(get("/api/lots/D")).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5.0));

        mockMvc.perform(get("/api/lots/C/genealogy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upstream[?(@.lotNumber=='A')]").exists())
                .andExpect(jsonPath("$.upstream[?(@.lotNumber=='B')]").exists())
                .andExpect(jsonPath("$.downstream").isEmpty());
        mockMvc.perform(get("/api/lots/A/genealogy"))
                .andExpect(jsonPath("$.downstream[?(@.lotNumber=='C')].depth").value(1))
                .andExpect(jsonPath("$.downstream[?(@.lotNumber=='D')].depth").value(1));
    }

    @Test
    void rejectsUnbalancedMassWith400() throws Exception {
        register("A", 100);
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transformationId":"T-BAD",
                                 "inputs":[{"lotNumber":"A","quantity":10}],
                                 "outputs":[{"lotNumber":"X","quantity":9}],
                                 "loss":0}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/lots/X")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/lots/A")).andExpect(jsonPath("$.quantity").value(100.0));
    }

    @Test
    void rejectsInsufficientStockWith409AndKeepsState() throws Exception {
        register("A", 10);
        register("B", 10);
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transformationId":"T-LOW",
                                 "inputs":[{"lotNumber":"A","quantity":10},{"lotNumber":"B","quantity":20}],
                                 "outputs":[{"lotNumber":"X","quantity":30}],
                                 "loss":0}
                                """))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/lots/X")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/lots/A")).andExpect(jsonPath("$.quantity").value(10.0));
        mockMvc.perform(get("/api/lots/B")).andExpect(jsonPath("$.quantity").value(10.0));
    }

    @Test
    void rejectsDuplicateOutputNumberAndDuplicateWithinRequest() throws Exception {
        register("A", 100);
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transformationId":"T-DUP",
                                 "inputs":[{"lotNumber":"A","quantity":10}],
                                 "outputs":[{"lotNumber":"A","quantity":10}],
                                 "loss":0}
                                """))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transformationId":"T-DUP2",
                                 "inputs":[{"lotNumber":"A","quantity":10}],
                                 "outputs":[{"lotNumber":"Y","quantity":5},{"lotNumber":"Y","quantity":5}],
                                 "loss":0}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/lots/Y")).andExpect(status().isNotFound());
    }

    @Test
    void transformationIsIdempotentButContentConflictReturns409() throws Exception {
        register("A", 100);
        String body = """
                {"transformationId":"T-IDEM",
                 "inputs":[{"lotNumber":"A","quantity":10}],
                 "outputs":[{"lotNumber":"Z","quantity":10}],
                 "loss":0}
                """;
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/lots/A")).andExpect(jsonPath("$.quantity").value(90.0));
        mockMvc.perform(get("/api/lots/Z")).andExpect(jsonPath("$.quantity").value(10.0));

        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transformationId":"T-IDEM",
                                 "inputs":[{"lotNumber":"A","quantity":11}],
                                 "outputs":[{"lotNumber":"Z","quantity":11}],
                                 "loss":0}
                                """))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/lots/A")).andExpect(jsonPath("$.quantity").value(90.0));
    }

    @Test
    void rejectsUnknownInputLotWith404() throws Exception {
        register("A", 10);
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transformationId":"T-MISS",
                                 "inputs":[{"lotNumber":"A","quantity":5},{"lotNumber":"GHOST","quantity":5}],
                                 "outputs":[{"lotNumber":"Q","quantity":10}],
                                 "loss":0}
                                """))
                .andExpect(status().isNotFound());
    }
}

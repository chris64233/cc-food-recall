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
class LotApiTests extends TestSupport {

    @Test
    void registersLotAndReadsInventory() throws Exception {
        mockMvc.perform(post("/api/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"L-1\",\"quantity\":100.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotNumber").value("L-1"))
                .andExpect(jsonPath("$.quantity").value(100.5))
                .andExpect(jsonPath("$.quarantined").value(false))
                .andExpect(jsonPath("$.recallReasons").isArray());

        mockMvc.perform(get("/api/lots/L-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(100.5))
                .andExpect(jsonPath("$.quarantined").value(false));
    }

    @Test
    void registerIsIdempotentForSameContent() throws Exception {
        String body = "{\"lotNumber\":\"L-2\",\"quantity\":10}";
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(10.0));
    }

    @Test
    void registerRejectsConflictingContentWith409() throws Exception {
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"L-3\",\"quantity\":10}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"L-3\",\"quantity\":20}"))
                .andExpect(status().isConflict());
    }

    @Test
    void registerRejectsInvalidQuantity() throws Exception {
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"L-4\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"L-4\",\"quantity\":1.0001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnknownLotReturns404() throws Exception {
        mockMvc.perform(get("/api/lots/NOPE"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/lots/NOPE/genealogy"))
                .andExpect(status().isNotFound());
    }
}

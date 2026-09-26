package com.chris64233.cc.foodrecall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 响应门槛可配置：比例为 0.5 时，两个持有方中一个响应即可关闭。
 */
@SpringBootTest(properties = "app.recall.required-response-ratio=0.5")
@AutoConfigureMockMvc
class RecallThresholdOverrideTests extends TestSupport {

    @Test
    void closeAllowedWhenHalfOfHoldersResponded() throws Exception {
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"A\",\"quantity\":100}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentKey\":\"S-1\",\"lotNumber\":\"A\",\"holder\":\"H1\",\"quantity\":40}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentKey\":\"S-2\",\"lotNumber\":\"A\",\"holder\":\"H2\",\"quantity\":10}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallNumber\":\"R-1\",\"lotNumber\":\"A\",\"reason\":\"x\"}"))
                .andExpect(status().isOk());

        // 2 个持有方中 1 个响应，达到 0.5 门槛
        mockMvc.perform(post("/api/recalls/R-1/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportNumber\":\"RP-1\",\"holder\":\"H1\",\"type\":\"ISOLATED\",\"quantity\":40}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/recalls/R-1/close").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closeNumber\":\"C-1\",\"approvedBy\":\"qa-lead\"}"))
                .andExpect(status().isOk());
    }
}

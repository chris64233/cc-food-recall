package com.chris64233.cc.foodrecall;

import com.chris64233.cc.foodrecall.repo.LotEdgeRepository;
import com.chris64233.cc.foodrecall.repo.LotRepository;
import com.chris64233.cc.foodrecall.repo.RecallEventRepository;
import com.chris64233.cc.foodrecall.repo.RecallImpactRepository;
import com.chris64233.cc.foodrecall.repo.TransformationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiWebTests {

    @Autowired
    MockMvc mockMvc;
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

    @Test
    void registerLotIsIdempotentAndConflictsOnDifferentQuantity() throws Exception {
        String body = "{\"lotNumber\":\"L1\",\"quantity\":10}";
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lotNumber").value("L1"))
                .andExpect(jsonPath("$.quantity").value(10.0));

        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"L1\",\"quantity\":11}"))
                .andExpect(status().isConflict());
    }

    @Test
    void registerLotRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"L2\",\"quantity\":1.0001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transformationEndpointsEnforceConservationAndIdempotency() throws Exception {
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lotNumber\":\"A\",\"quantity\":10}"))
                .andExpect(status().isCreated());

        String tx = "{\"transformationId\":\"T1\",\"inputs\":[{\"lotNumber\":\"A\",\"quantity\":10}],"
                + "\"outputs\":[{\"lotNumber\":\"B\",\"quantity\":9}],\"loss\":1}";
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON).content(tx))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transformationId").value("T1"));

        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON).content(tx))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transformationId\":\"T1\",\"inputs\":[{\"lotNumber\":\"A\",\"quantity\":10}],"
                                + "\"outputs\":[{\"lotNumber\":\"B\",\"quantity\":8}],\"loss\":2}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transformationId\":\"T2\",\"inputs\":[{\"lotNumber\":\"B\",\"quantity\":9}],"
                                + "\"outputs\":[{\"lotNumber\":\"C\",\"quantity\":8}],\"loss\":0}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transformationId\":\"T3\",\"inputs\":[{\"lotNumber\":\"B\",\"quantity\":100}],"
                                + "\"outputs\":[{\"lotNumber\":\"C\",\"quantity\":100}],\"loss\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void recallFlowQuarantinesDescendantsAndCloseLiftsIt() throws Exception {
        mockMvc.perform(post("/api/lots").contentType(MediaType.APPLICATION_JSON)
                .content("{\"lotNumber\":\"A\",\"quantity\":10}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/transformations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transformationId\":\"T1\",\"inputs\":[{\"lotNumber\":\"A\",\"quantity\":10}],"
                                + "\"outputs\":[{\"lotNumber\":\"B\",\"quantity\":10}],\"loss\":0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallNumber\":\"R1\",\"lotNumber\":\"A\",\"reason\":\"salmonella\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.impactedLots.length()").value(2));

        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallNumber\":\"R1\",\"lotNumber\":\"A\",\"reason\":\"salmonella\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallNumber\":\"R1\",\"lotNumber\":\"A\",\"reason\":\"other\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/lots/B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quarantined").value(true))
                .andExpect(jsonPath("$.activeRecalls[0].recallNumber").value("R1"));

        mockMvc.perform(get("/api/lots/B/lineage/upstream"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedLots[0].lotNumber").value("A"));

        mockMvc.perform(post("/api/recalls/R1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(get("/api/lots/B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quarantined").value(false));
    }

    @Test
    void unknownResourcesReturn404() throws Exception {
        mockMvc.perform(get("/api/lots/NOPE")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/recalls/NOPE")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/recalls/NOPE/close")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/recalls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recallNumber\":\"R9\",\"lotNumber\":\"NOPE\",\"reason\":\"x\"}"))
                .andExpect(status().isNotFound());
    }
}

package com.forfun.codel_ineage.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: all REST endpoints return 200/202.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiSmokeIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());
    }

    @Test
    void entryPointsWithAppId() throws Exception {
        mockMvc.perform(get("/api/v1/lineage/entry-points").param("appId", "nonexistent"))
                .andExpect(status().isOk());
    }

    @Test
    void scanProgressEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/scans/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scans").isArray());
    }

    @Test
    void triggerScanReturnsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/scans")
                        .contentType("application/json")
                        .content("{\"appId\":\"test\",\"repoUrl\":\".\",\"scanType\":\"FULL\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.traceId").exists());
    }

    @Test
    void governanceAnalyzeReturnsMetrics() throws Exception {
        mockMvc.perform(post("/api/v1/governance/analyze")
                        .contentType("application/json")
                        .content("{\"baseDir\":\".\",\"appId\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classCount").isNumber());
    }

    @Test
    void missingEntryPointsParamReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/lineage/entry-points"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void impactEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/lineage/impact")
                        .param("changedMethods", "test1", "test2")
                        .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changedCount").value(2));
    }
}

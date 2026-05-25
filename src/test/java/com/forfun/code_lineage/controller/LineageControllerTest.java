package com.forfun.code_lineage.controller;

import com.forfun.code_lineage.service.LineageQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;
import java.util.List;

@WebMvcTest(LineageController.class)
class LineageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LineageQueryService queryService;

    @Test
    void shouldReturnEntryPoints() throws Exception {
        when(queryService.getEntryPoints("test-app", null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/lineage/entry-points")
                        .param("appId", "test-app"))
                .andExpect(status().isOk());
    }
}

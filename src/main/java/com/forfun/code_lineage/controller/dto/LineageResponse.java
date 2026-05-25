package com.forfun.code_lineage.controller.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class LineageResponse {
    private boolean success;
    private Map<String, Object> data;
    private String error;
    private long durationMs;
}

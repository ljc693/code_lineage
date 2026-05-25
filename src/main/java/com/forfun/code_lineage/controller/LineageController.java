package com.forfun.code_lineage.controller;

import com.forfun.code_lineage.controller.dto.LineageResponse;
import com.forfun.code_lineage.service.LineageQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/lineage")
public class LineageController {

    private final LineageQueryService queryService;

    public LineageController(LineageQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/entry/{methodId}")
    public LineageResponse getDownstream(
            @PathVariable String methodId,
            @RequestParam(defaultValue = "10") int depth,
            @RequestParam(defaultValue = "g6") String format,
            @RequestParam(defaultValue = "method") String view) {

        long start = System.currentTimeMillis();
        Map<String, Object> data = queryService.getDownstreamLineage(methodId, depth, format, view);
        return LineageResponse.builder()
                .success(true)
                .data(data)
                .durationMs(System.currentTimeMillis() - start)
                .build();
    }

    @GetMapping("/upstream/{methodId}")
    public LineageResponse getUpstream(
            @PathVariable String methodId,
            @RequestParam(defaultValue = "10") int depth,
            @RequestParam(defaultValue = "g6") String format) {

        Map<String, Object> data = queryService.getUpstreamLineage(methodId, depth, format);
        return LineageResponse.builder()
                .success(true)
                .data(data)
                .build();
    }

    @GetMapping("/full-path")
    public LineageResponse getFullPath(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "10") int depth,
            @RequestParam(defaultValue = "g6") String format) {

        Map<String, Object> data = queryService.getFullPath(from, to, depth, format);
        return LineageResponse.builder()
                .success(true)
                .data(data)
                .build();
    }

    @GetMapping("/entry-points")
    public LineageResponse getEntryPoints(
            @RequestParam String appId,
            @RequestParam(required = false) String type) {

        List<Map<String, Object>> entries = queryService.getEntryPoints(appId, type);
        return LineageResponse.builder()
                .success(true)
                .data(Map.of("entries", entries))
                .build();
    }

    @GetMapping("/impact")
    public LineageResponse getImpact(
            @RequestParam List<String> changedMethods,
            @RequestParam(defaultValue = "10") int depth,
            @RequestParam(defaultValue = "g6") String format) {

        Map<String, Object> data = queryService.getImpact(changedMethods, depth, format);
        return LineageResponse.builder()
                .success(true)
                .data(data)
                .build();
    }

    @GetMapping("/topology")
    public LineageResponse getSystemTopology(
            @RequestParam(defaultValue = "g6") String format) {
        Map<String, Object> data = queryService.getSystemTopology(format);
        return LineageResponse.builder()
                .success(true)
                .data(data)
                .build();
    }
}

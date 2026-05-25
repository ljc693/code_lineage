package com.forfun.code_lineage.controller;

import com.forfun.code_lineage.controller.dto.LineageResponse;
import com.forfun.code_lineage.controller.dto.ScanRequest;
import com.forfun.code_lineage.service.ScanHistoryRepository;
import com.forfun.code_lineage.service.ScanOrchestrator;
import com.forfun.code_lineage.service.ScanProgressTracker;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/scans")
public class ScanController {

    private final ScanOrchestrator orchestrator;
    private final ScanProgressTracker progressTracker;
    private final ScanHistoryRepository historyRepo;

    public ScanController(ScanOrchestrator orchestrator,
                          ScanProgressTracker progressTracker,
                          ScanHistoryRepository historyRepo) {
        this.orchestrator = orchestrator;
        this.progressTracker = progressTracker;
        this.historyRepo = historyRepo;
    }

    /** Trigger a new scan. Returns traceId for progress polling. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LineageResponse triggerScan(@RequestBody ScanRequest request) {
        String traceId = UUID.randomUUID().toString();
        orchestrator.startScan(
                traceId,
                request.getAppId(),
                request.getRepoUrl(),
                request.getBranch(),
                request.getScanType() != null ? request.getScanType() : "FULL",
                request.getCommitSha()
        );

        return LineageResponse.builder()
                .success(true)
                .data(Map.of(
                        "traceId", traceId,
                        "status", "accepted",
                        "progressUrl", "/api/v1/scans/progress/" + traceId
                ))
                .build();
    }

    /** List all active scan progresses. */
    @GetMapping("/progress")
    public LineageResponse getActiveProgress() {
        List<ScanProgressTracker.Progress> all = progressTracker.getAllActive();
        return LineageResponse.builder()
                .success(true)
                .data(Map.of("scans", all.stream().map(this::toProgressMap).toList()))
                .build();
    }

    /** Get detailed progress for a specific scan (active or recently completed). */
    @GetMapping("/progress/{traceId}")
    public LineageResponse getProgressByTrace(@PathVariable String traceId) {
        var p = progressTracker.get(traceId);
        if (p == null) {
            // Fall back to MySQL history for completed scans
            var row = historyRepo.findByTraceId(traceId);
            if (row == null) {
                return LineageResponse.builder().success(false).error("not found").build();
            }
            return LineageResponse.builder()
                    .success(true)
                    .data(Map.of(
                            "traceId", traceId,
                            "stage", "COMPLETED",
                            "percent", 100,
                            "completed", true,
                            "fromHistory", true,
                            "stats", row
                    ))
                    .build();
        }
        return LineageResponse.builder()
                .success(true)
                .data(toProgressMap(p))
                .build();
    }

    /** Query scan history from MySQL. */
    @GetMapping("/history")
    public LineageResponse getHistory(
            @RequestParam(required = false) String appId,
            @RequestParam(defaultValue = "20") int limit) {
        var rows = appId != null
                ? historyRepo.findByAppId(appId, limit)
                : historyRepo.findRecent(limit);
        return LineageResponse.builder()
                .success(true)
                .data(Map.of("scans", rows))
                .build();
    }

    /** Get latest scan result for an app (convenience for CI/CD pipelines). */
    @GetMapping("/latest/{appId}")
    public LineageResponse getLatestByApp(@PathVariable String appId) {
        var row = historyRepo.findLatestByAppId(appId);
        if (row == null) {
            return LineageResponse.builder()
                    .success(false)
                    .error("No scan history for app: " + appId)
                    .build();
        }
        return LineageResponse.builder()
                .success(true)
                .data(row)
                .build();
    }

    /** Convert Progress to a CI/CD-friendly map. */
    private Map<String, Object> toProgressMap(ScanProgressTracker.Progress p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("traceId", p.traceId);
        m.put("appId", p.appId);
        m.put("stage", p.stage.name());
        m.put("percent", p.getPercent());
        m.put("totalFiles", p.totalFiles);
        m.put("analyzedFiles", p.analyzedFiles);
        if (p.currentFile != null) m.put("currentFile", p.currentFile);
        m.put("methods", p.methods);
        m.put("relations", p.relations);
        m.put("sqlRelations", p.sqlRelations);
        if (p.entryPoints > 0) m.put("entryPoints", p.entryPoints);
        if (p.tables > 0) m.put("tables", p.tables);
        m.put("elapsedMs", p.getElapsedMs());
        m.put("completed", p.completed);
        if (p.error != null) m.put("error", p.error);

        // Per-stage timing
        Map<String, Long> timings = new LinkedHashMap<>();
        for (var entry : p.stageTimings.entrySet()) {
            timings.put(entry.getKey().name(), entry.getValue());
        }
        if (!timings.isEmpty()) m.put("stageTimingsMs", timings);

        return m;
    }
}

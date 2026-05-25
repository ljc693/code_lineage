package com.forfun.code_lineage.controller;

import com.forfun.code_lineage.controller.dto.LineageResponse;
import com.forfun.code_lineage.graph.ColumnLineageRepository;
import com.forfun.code_lineage.service.NodeValueAnalyzer;
import org.neo4j.driver.Driver;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final NodeValueAnalyzer valueAnalyzer;
    private final ColumnLineageRepository columnLineageRepo;
    private final Driver neo4jDriver;

    public AnalysisController(NodeValueAnalyzer valueAnalyzer,
                              ColumnLineageRepository columnLineageRepo,
                              Driver neo4jDriver) {
        this.valueAnalyzer = valueAnalyzer;
        this.columnLineageRepo = columnLineageRepo;
        this.neo4jDriver = neo4jDriver;
    }

    @GetMapping("/values")
    public LineageResponse getValueLabels(
            @RequestParam(required = false) String appId,
            @RequestParam(defaultValue = "5") int threshold) {

        NodeValueAnalyzer.ValueReport report = valueAnalyzer.analyze(threshold, appId);

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (var label : report.labels()) {
            grouped.computeIfAbsent(label.label(), k -> new ArrayList<>())
                    .add(Map.of(
                            "methodId", label.methodId(),
                            "signature", label.signature(),
                            "className", label.className(),
                            "score", label.score(),
                            "detail", label.detail()
                    ));
        }

        return LineageResponse.builder()
                .success(true)
                .data(Map.of(
                        "threshold", report.threshold(),
                        "highImpact", grouped.getOrDefault("HIGH_IMPACT", List.of()),
                        "deprecatedSuspect", grouped.getOrDefault("DEPRECATED_SUSPECT", List.of()),
                        "criticalPath", grouped.getOrDefault("CRITICAL_PATH", List.of())
                ))
                .build();
    }

    @GetMapping("/field-impact")
    public LineageResponse getFieldImpact(
            @RequestParam String tableName,
            @RequestParam String columnName) {

        var impacted = columnLineageRepo.findImpactedMethods(tableName, columnName);
        List<Map<String, Object>> results = new ArrayList<>();
        for (var m : impacted) {
            results.add(Map.of(
                    "methodId", m.getOrDefault("method_id", ""),
                    "signature", m.getOrDefault("method_signature", ""),
                    "className", m.getOrDefault("class_name", ""),
                    "appId", m.getOrDefault("app_id", ""),
                    "operation", m.getOrDefault("operation", "SELECT"),
                    "accessCount", m.getOrDefault("access_count", 1)
            ));
        }
        return LineageResponse.builder()
                .success(true)
                .data(Map.of(
                        "table", tableName,
                        "column", columnName,
                        "impactedMethods", results,
                        "count", results.size()
                ))
                .build();
    }

    /** List all tables with column stats */
    @GetMapping("/table-stats")
    public LineageResponse getTableStats(@RequestParam(required = false) String appId) {
        var tables = columnLineageRepo.findTablesByApp(appId != null ? appId : "%");
        return LineageResponse.builder()
                .success(true)
                .data(Map.of("tables", tables))
                .build();
    }

    /** List columns for a table */
    @GetMapping("/columns")
    public LineageResponse getColumns(@RequestParam String tableName) {
        List<String> columns = columnLineageRepo.findColumnsByTable(tableName);
        return LineageResponse.builder()
                .success(true)
                .data(Map.of("table", tableName, "columns", columns))
                .build();
    }

    /** Find dead columns (not accessed in N days) */
    @GetMapping("/dead-columns")
    public LineageResponse getDeadColumns(
            @RequestParam String tableName,
            @RequestParam(defaultValue = "30") int days) {

        var dead = columnLineageRepo.findUnusedColumns(tableName, days);
        return LineageResponse.builder()
                .success(true)
                .data(Map.of("table", tableName, "days", days, "deadColumns", dead))
                .build();
    }

    /** Full endpoint impact: field change → HTTP endpoints via Neo4j upstream */
    @GetMapping("/field-impact-upstream")
    public LineageResponse getFieldImpactUpstream(
            @RequestParam String tableName,
            @RequestParam String columnName) {

        // Step 1: Find directly impacted methods from MySQL
        var directMethods = columnLineageRepo.findImpactedMethods(tableName, columnName);
        if (directMethods.isEmpty()) {
            return LineageResponse.builder().success(true)
                    .data(Map.of("table", tableName, "column", columnName,
                            "impactedEndpoints", List.of(), "count", 0))
                    .build();
        }

        // Step 2: For each direct method, walk upstream in Neo4j to find HTTP entries
        List<Map<String, Object>> endpoints = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        try (var session = neo4jDriver.session()) {
            for (var m : directMethods) {
                String methodId = (String) m.get("method_id");
                String appId = (String) m.getOrDefault("app_id", "");
                if (methodId == null) continue;

                // Walk upstream CALLS chain (1..6 hops) to find entry points.
                // Uses exact methodId match + appId filtering to avoid cross-app contamination.
                var result = session.run(
                    "MATCH (start:Method {methodId: $methodId, appId: $appId}) " +
                    "MATCH (start)<-[:CALLS*1..6]-(caller:Method {appId: $appId}) " +
                    "WHERE caller.isEntry = true " +
                    "RETURN DISTINCT caller.methodId AS id, caller.signature AS sig, " +
                    "caller.className AS cls, caller.httpPath AS path, " +
                    "caller.httpMethod AS method, caller.appId AS app " +
                    "LIMIT 50",
                    Map.of("methodId", methodId, "appId", appId));

                while (result.hasNext()) {
                    var rec = result.next();
                    String eid = rec.get("id").asString();
                    if (seen.add(eid)) {
                        endpoints.add(Map.of(
                            "methodId", eid,
                            "signature", rec.get("sig").asString(""),
                            "className", rec.get("cls").asString(""),
                            "httpPath", rec.get("path").asString(""),
                            "httpMethod", rec.get("method").asString(""),
                            "appId", rec.get("app").asString(""),
                            "viaMethod", methodId
                        ));
                    }
                }
            }
        }

        return LineageResponse.builder()
                .success(true)
                .data(Map.of(
                        "table", tableName,
                        "column", columnName,
                        "directMethods", directMethods.size(),
                        "impactedEndpoints", endpoints,
                        "count", endpoints.size()
                ))
                .build();
    }

    /** Seed demo data for field impact visualization */
    @Deprecated
    @PostMapping("/seed-demo")
    public LineageResponse seedDemoData() {
        // Example data — replace with your own project's scan data in production
        String[][] demo = {
            {"clawer", "clawer:com.clawer.application.service.impl.AuthApplicationServiceImpl.listPlatforms(PlatformQuery)", "listPlatforms(PlatformQuery)", "AuthApplicationServiceImpl", "platform_config", "platform_code", "SELECT", "MYSQL"},
            {"clawer", "clawer:com.clawer.application.service.impl.AuthApplicationServiceImpl.listPlatforms(PlatformQuery)", "listPlatforms(PlatformQuery)", "AuthApplicationServiceImpl", "platform_config", "auth_type", "SELECT", "MYSQL"},
            {"clawer", "clawer:com.clawer.application.service.impl.AuthApplicationServiceImpl.createPlatform(CreatePlatformCommand)", "createPlatform(CreatePlatformCommand)", "AuthApplicationServiceImpl", "platform_config", "platform_code", "INSERT", "MYSQL"},
            {"clawer", "clawer:com.clawer.application.service.impl.AuthApplicationServiceImpl.createPlatform(CreatePlatformCommand)", "createPlatform(CreatePlatformCommand)", "AuthApplicationServiceImpl", "platform_config", "auth_type", "INSERT", "MYSQL"},
            {"clawer", "clawer:com.clawer.application.service.impl.AuthApplicationServiceImpl.createPlatform(CreatePlatformCommand)", "createPlatform(CreatePlatformCommand)", "AuthApplicationServiceImpl", "platform_config", "status", "INSERT", "MYSQL"},
            {"clawer", "clawer:com.clawer.domain.repository.AuthEventLogRepository.save(AuthEvent)", "save(AuthEvent)", "AuthEventLogRepository", "audit_log", "user_id", "INSERT", "MYSQL"},
            {"clawer", "clawer:com.clawer.domain.repository.AuthEventLogRepository.save(AuthEvent)", "save(AuthEvent)", "AuthEventLogRepository", "audit_log", "event_type", "INSERT", "MYSQL"},
            {"clawer", "clawer:com.clawer.domain.repository.AuthEventLogRepository.findByPlatformCode(String,int)", "findByPlatformCode(String,int)", "AuthEventLogRepository", "audit_log", "platform_code", "SELECT", "MYSQL"},
            {"clawer", "clawer:com.clawer.application.service.impl.ScriptConfigApplicationServiceImpl.createScriptConfig(CreateScriptConfigCommand)", "createScriptConfig(CreateScriptConfigCommand)", "ScriptConfigApplicationServiceImpl", "script_config", "script_code", "INSERT", "MYSQL"},
            {"clawer", "clawer:com.clawer.application.service.impl.ScriptConfigApplicationServiceImpl.createScriptConfig(CreateScriptConfigCommand)", "createScriptConfig(CreateScriptConfigCommand)", "ScriptConfigApplicationServiceImpl", "script_config", "cron_expression", "INSERT", "MYSQL"},
        };
        for (String[] row : demo) {
            columnLineageRepo.upsert(row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7],
                    "SELECT * FROM " + row[4] + " WHERE " + row[5] + "=?");
        }
        return LineageResponse.builder().success(true)
                .data(Map.of("seeded", demo.length)).build();
    }
}

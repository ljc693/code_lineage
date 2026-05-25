package com.forfun.codel_ineage.analysis.report;

import com.forfun.codel_ineage.analysis.rule.AnalysisFindingsRepository;
import com.forfun.codel_ineage.graph.ColumnLineageRepository;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class LineageContextBuilder {

    private final Driver neo4jDriver;
    private final ColumnLineageRepository columnRepo;
    private final AnalysisFindingsRepository findingsRepo;

    public LineageContextBuilder(Driver neo4jDriver, ColumnLineageRepository columnRepo,
                                 AnalysisFindingsRepository findingsRepo) {
        this.neo4jDriver = neo4jDriver;
        this.columnRepo = columnRepo;
        this.findingsRepo = findingsRepo;
    }

    public LineageContext buildDeadColumnContext(String tableName, String columnName, int days,
                                                  boolean includeFindings) {
        var methods = columnRepo.findImpactedMethods(tableName, columnName);
        var columns = columnRepo.findColumnsByTable(tableName);
        var builder = LineageContext.builder()
                .queryDescription("Dead column analysis for " + tableName + "." + columnName)
                .targetTable(tableName)
                .columns(columns)
                .impactedMethods(methods)
                .upstreamEndpoints(findUpstreamEndpoints(methods))
                .callChainExample("N/A")
                .deadColumnAnalysis(Map.of("days", days));

        if (includeFindings) {
            builder.findingsSummary(buildFindingsSummary(tableName));
        }

        return builder.build();
    }

    public LineageContext buildFieldImpactContext(String tableName, String columnName,
                                                   boolean includeFindings) {
        var methods = columnRepo.findImpactedMethods(tableName, columnName);
        var columns = columnRepo.findColumnsByTable(tableName);
        var builder = LineageContext.builder()
                .queryDescription("Field impact analysis for " + tableName + "." + columnName)
                .targetTable(tableName)
                .columns(columns)
                .impactedMethods(methods)
                .upstreamEndpoints(findUpstreamEndpoints(methods))
                .callChainExample("N/A");

        if (includeFindings) {
            builder.findingsSummary(buildFindingsSummary(tableName));
        }

        return builder.build();
    }

    private String buildFindingsSummary(String tableName) {
        var findings = findingsRepo.findByAppId("clawer", 50);
        return findings.stream()
                .filter(f -> f.get("title") != null)
                .map(f -> "- [" + f.get("severity") + "] [" + f.get("rule_id") + "] " + f.get("title"))
                .limit(15)
                .collect(Collectors.joining("\n"));
    }

    private List<Map<String, Object>> findUpstreamEndpoints(List<Map<String, Object>> methods) {
        List<Map<String, Object>> endpoints = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        try (var session = neo4jDriver.session()) {
            for (var m : methods) {
                String methodId = (String) m.get("method_id");
                String appId = (String) m.getOrDefault("app_id", "");
                if (methodId == null) continue;

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
                                "appId", rec.get("app").asString("")
                        ));
                    }
                }
            }
        }
        return endpoints;
    }
}

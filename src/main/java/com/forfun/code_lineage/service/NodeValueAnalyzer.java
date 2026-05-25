package com.forfun.code_lineage.service;

import org.neo4j.driver.Driver;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * PRD 3.5: Code value identification.
 * Labels methods as high-impact, deprecated-suspect, or critical-path.
 */
@Component
public class NodeValueAnalyzer {

    private final Driver neo4jDriver;

    public NodeValueAnalyzer(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    /**
     * Analyze all methods and return value labels.
     */
    public ValueReport analyze(int highImpactThreshold, String appId) {
        List<ValueLabel> labels = new ArrayList<>();

        try (var session = neo4jDriver.session()) {
            // 1. High-impact: out-degree + in-degree > threshold
            var highImpact = session.run(
                    "MATCH (m:Method) " +
                    (appId != null ? "WHERE m.appId = $appId " : "") +
                    "OPTIONAL MATCH (m)-[out:CALLS]->() " +
                    "OPTIONAL MATCH ()-[in:CALLS]->(m) " +
                    "WITH m, count(DISTINCT out) AS outDeg, count(DISTINCT in) AS inDeg " +
                    "WHERE (outDeg + inDeg) > $threshold " +
                    "RETURN m.methodId AS id, m.signature AS sig, m.className AS cls, " +
                    "outDeg, inDeg, (outDeg + inDeg) AS total " +
                    "ORDER BY total DESC LIMIT 100",
                    Map.of("threshold", highImpactThreshold,
                           "appId", appId != null ? appId : ""));
            while (highImpact.hasNext()) {
                var r = highImpact.next();
                labels.add(new ValueLabel(r.get("id").asString(),
                        r.get("sig").asString(), r.get("cls").asString(),
                        "HIGH_IMPACT", r.get("total").asInt(),
                        "out=" + r.get("outDeg").asInt() + " in=" + r.get("inDeg").asInt()));
            }

            // 2. Deprecated-suspect: 0 in-degree AND 0 out-degree
            var deprecated = session.run(
                    "MATCH (m:Method) " +
                    (appId != null ? "WHERE m.appId = $appId " : "") +
                    "OPTIONAL MATCH (m)-[out:CALLS]->() " +
                    "OPTIONAL MATCH ()-[in:CALLS]->(m) " +
                    "WITH m, count(DISTINCT out) AS outDeg, count(DISTINCT in) AS inDeg " +
                    "WHERE outDeg = 0 AND inDeg = 0 AND NOT m.isEntry " +
                    "RETURN m.methodId AS id, m.signature AS sig, m.className AS cls " +
                    "LIMIT 100",
                    Map.of("appId", appId != null ? appId : ""));
            while (deprecated.hasNext()) {
                var r = deprecated.next();
                labels.add(new ValueLabel(r.get("id").asString(),
                        r.get("sig").asString(), r.get("cls").asString(),
                        "DEPRECATED_SUSPECT", 0, "no callers or callees"));
            }

            // 3. Critical-path: methods that appear on multiple entry-point paths
            // (simplified: high in-degree AND high out-degree)
            var critical = session.run(
                    "MATCH (m:Method) " +
                    (appId != null ? "WHERE m.appId = $appId " : "") +
                    "OPTIONAL MATCH (m)-[out:CALLS]->() " +
                    "OPTIONAL MATCH ()-[in:CALLS]->(m) " +
                    "WITH m, count(DISTINCT out) AS outDeg, count(DISTINCT in) AS inDeg " +
                    "WHERE outDeg >= 3 AND inDeg >= 3 " +
                    "RETURN m.methodId AS id, m.signature AS sig, m.className AS cls, " +
                    "outDeg, inDeg " +
                    "ORDER BY (outDeg + inDeg) DESC LIMIT 100",
                    Map.of("appId", appId != null ? appId : ""));
            while (critical.hasNext()) {
                var r = critical.next();
                labels.add(new ValueLabel(r.get("id").asString(),
                        r.get("sig").asString(), r.get("cls").asString(),
                        "CRITICAL_PATH", r.get("outDeg").asInt() + r.get("inDeg").asInt(),
                        "out=" + r.get("outDeg").asInt() + " in=" + r.get("inDeg").asInt()));
            }
        }

        return new ValueReport(labels, highImpactThreshold);
    }

    public record ValueLabel(String methodId, String signature, String className,
                             String label, int score, String detail) {}
    public record ValueReport(List<ValueLabel> labels, int threshold) {}
}

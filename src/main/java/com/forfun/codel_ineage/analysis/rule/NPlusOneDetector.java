package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class NPlusOneDetector extends AbstractAnalysisRule {

    public NPlusOneDetector(Driver neo4jDriver) {
        super(neo4jDriver);
    }

    @Override public String id() { return "n-plus-one"; }
    @Override public String name() { return "N+1 Query Detector"; }
    @Override public String severity() { return "HIGH"; }
    @Override public String category() { return "performance"; }

    @Override
    protected Object gatherContext(Session session, Target target) {
        return query(session, """
            MATCH (m:Method {appId: $appId})-[a:ACCESSES]->(t:Table)
            WHERE EXISTS { MATCH (m)-[:CALLS*1..3]->(m) }
            RETURN m.methodId AS methodId, m.signature AS signature, m.className AS className,
                   t.tableName AS tableName, a.operation AS operation
            """, Map.of("appId", target.appId()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session session, Target target, Object context) {
        return ((List<Map<String, Object>>) context).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "N+1 risk: " + r.get("className") + "." + r.get("signature"),
                    "Method accesses " + r.get("tableName") +
                    " inside a self-calling loop. Each iteration may issue a separate query.",
                    "Replace per-iteration " + r.get("operation") +
                    " with batch operation (e.g. MyBatis-Plus selectBatchIds or collecting IDs first).",
                    Map.of("raw", r)))
                .toList();
    }
}

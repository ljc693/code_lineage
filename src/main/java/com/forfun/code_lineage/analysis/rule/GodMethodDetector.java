package com.forfun.code_lineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class GodMethodDetector extends AbstractAnalysisRule {

    private static final int TABLE_THRESHOLD = 5;

    public GodMethodDetector(Driver neo4jDriver) { super(neo4jDriver); }

    @Override public String id() { return "god-method"; }
    @Override public String name() { return "God Method Detector"; }
    @Override public String severity() { return "MEDIUM"; }
    @Override public String category() { return "architecture"; }

    @Override
    protected Object gatherContext(Session session, Target target) {
        return query(session, """
            MATCH (m:Method {appId: $appId})-[a:ACCESSES]->(t:Table)
            WITH m, collect(DISTINCT t.tableName) AS tables, count(DISTINCT t) AS tblCount
            WHERE tblCount >= $threshold
            RETURN m.methodId AS methodId, m.signature AS signature, m.className AS className, tables, tblCount
            """, Map.of("appId", target.appId(), "threshold", TABLE_THRESHOLD));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session s, Target t, Object ctx) {
        return ((List<Map<String, Object>>) ctx).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "God method: " + r.get("className") + "." + r.get("signature"),
                    "Method accesses " + r.get("tblCount") + " tables (" + r.get("tables") +
                    "). Consider splitting into focused methods, one per aggregate.",
                    "Extract table-specific logic into separate service methods.",
                    Map.of("raw", r)))
                .toList();
    }
}

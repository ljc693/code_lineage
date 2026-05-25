package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class OrphanCodeDetector extends AbstractAnalysisRule {

    public OrphanCodeDetector(Driver neo4jDriver) { super(neo4jDriver); }

    @Override public String id() { return "orphan-code"; }
    @Override public String name() { return "Orphan Code Detector"; }
    @Override public String severity() { return "LOW"; }
    @Override public String category() { return "dead_code"; }

    @Override
    protected Object gatherContext(Session session, Target target) {
        return query(session, """
            MATCH (m:Method {appId: $appId})
            WHERE NOT (()-[:CALLS]->(m)) AND m.isEntry = false
            RETURN m.methodId AS methodId, m.signature AS signature, m.className AS className, m.packageName AS packageName
            """, Map.of("appId", target.appId()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session s, Target t, Object ctx) {
        return ((List<Map<String, Object>>) ctx).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "Orphan method: " + r.get("className") + "." + r.get("signature"),
                    "Method has no callers and is not an entry point. " +
                    "Package: " + r.get("packageName"),
                    "Verify if this method is still needed. If dead code, remove it. " +
                    "If it should be reachable, ensure it's wired into a call chain.",
                    Map.of("raw", r)))
                .toList();
    }
}

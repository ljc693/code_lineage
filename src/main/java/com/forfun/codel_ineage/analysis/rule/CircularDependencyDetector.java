package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class CircularDependencyDetector extends AbstractAnalysisRule {

    public CircularDependencyDetector(Driver neo4jDriver) { super(neo4jDriver); }

    @Override public String id() { return "circular-dep"; }
    @Override public String name() { return "Circular Dependency Detector"; }
    @Override public String severity() { return "HIGH"; }
    @Override public String category() { return "architecture"; }

    @Override
    protected Object gatherContext(Session session, Target target) {
        return query(session, """
            MATCH path = (m:Method {appId: $appId})-[:CALLS*2..5]->(m)
            RETURN m.methodId AS methodId, m.signature AS signature, m.className AS className,
                   [n IN nodes(path) | n.className + '.' + n.signature] AS cycle,
                   length(path) AS depth
            LIMIT 50
            """, Map.of("appId", target.appId()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session s, Target t, Object ctx) {
        return ((List<Map<String, Object>>) ctx).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "Circular dependency involving " + r.get("className") + "." + r.get("signature"),
                    "Cycle detected (depth " + r.get("depth") + "): " + r.get("cycle"),
                    "Break the cycle by extracting shared logic into a separate class " +
                    "or using an event-driven approach.",
                    Map.of("raw", r)))
                .toList();
    }
}

package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class LayerViolationDetector extends AbstractAnalysisRule {

    public LayerViolationDetector(Driver neo4jDriver) { super(neo4jDriver); }

    @Override public String id() { return "layer-violation"; }
    @Override public String name() { return "Layer Violation Detector"; }
    @Override public String severity() { return "MEDIUM"; }
    @Override public String category() { return "architecture"; }

    @Override
    protected Object gatherContext(Session session, Target target) {
        return query(session, """
            MATCH (m:Method {appId: $appId})-[a:ACCESSES]->(t:Table)
            WHERE m.isEntry = true AND m.className CONTAINS 'Controller'
            RETURN m.methodId AS methodId, m.signature AS signature, m.className AS className,
                   t.tableName AS tableName, a.operation AS operation
            """, Map.of("appId", target.appId()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session s, Target t, Object ctx) {
        return ((List<Map<String, Object>>) ctx).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "Layer violation: " + r.get("className") + "." + r.get("signature"),
                    "Controller directly accesses table " + r.get("tableName") +
                    ". Should go through Service → Repository layers.",
                    "Move DB access to a Repository, inject it into a Service, " +
                    "and call the Service from the Controller.",
                    Map.of("raw", r)))
                .toList();
    }
}

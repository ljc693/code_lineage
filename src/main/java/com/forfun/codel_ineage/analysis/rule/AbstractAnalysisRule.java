package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import java.util.*;

public abstract class AbstractAnalysisRule implements AnalysisRule {

    protected final Driver neo4jDriver;

    protected AbstractAnalysisRule(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public final List<Finding> analyze(Target target) {
        List<Finding> results = new ArrayList<>();
        try (var session = neo4jDriver.session()) {
            Object ctx = gatherContext(session, target);
            if (ctx == null) return results;
            results.addAll(detect(session, target, ctx));
        }
        return results;
    }

    protected abstract Object gatherContext(Session session, Target target);
    protected abstract List<Finding> detect(Session session, Target target, Object context);

    protected List<Map<String, Object>> query(Session session,
            String cypher, Map<String, Object> params) {
        return session.run(cypher, params).list(r ->
                r.asMap(org.neo4j.driver.Value::asObject));
    }
}

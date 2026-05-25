package com.forfun.codel_ineage.graph;

import com.forfun.codel_ineage.model.*;
import com.forfun.codel_ineage.pathfinder.TraversalSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Neo4jAdapter implements GraphAdapter {

    private static final Logger log = LoggerFactory.getLogger(Neo4jAdapter.class);

    private final Neo4jMethodRepository methodRepo;
    private final org.neo4j.driver.Driver neo4jDriver;

    public Neo4jAdapter(Neo4jMethodRepository methodRepo,
                        org.neo4j.driver.Driver neo4jDriver) {
        this.methodRepo = methodRepo;
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public void write(SubGraph subGraph) {
        if (subGraph.getMethods() == null || subGraph.getMethods().isEmpty()) return;

        // Batch save all methods
        List<Neo4jMethodEntity> entities = new ArrayList<>();
        for (MethodNode m : subGraph.getMethods()) {
            entities.add(Neo4jMethodEntity.builder()
                    .methodId(m.getMethodId())
                    .signature(m.getSignature())
                    .returnType(m.getReturnType())
                    .paramTypes(paramsToString(m))
                    .annotations(m.getAnnotations() != null
                            ? String.join(",", m.getAnnotations()) : "")
                    .lineNumber(m.getLineNumber())
                    .isEntry(m.isEntry())
                    .httpPath(m.getHttpPath())
                    .httpMethod(m.getHttpMethod())
                    .appId(m.getAppId())
                    .systemId(m.getSystemId())
                    .className(m.getClassName())
                    .packageName(m.getPackageName())
                    .isAbstract(m.isAbstract())
                    .build());
        }
        methodRepo.saveAll(entities);

        // Batch create CALLS edges via Cypher UNWIND (single query)
        if (subGraph.getCallsEdges() != null && !subGraph.getCallsEdges().isEmpty()) {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (CallsEdge edge : subGraph.getCallsEdges()) {
                Neo4jMethodEntity source = findMethod(edge.getSourceMethodId());
                if (source == null) continue;
                Neo4jMethodEntity target = resolveCallee(edge, source,
                        edge.getCallType() != null ? edge.getCallType().name() : "INTERNAL");
                if (target == null) continue;
                batch.add(Map.of(
                        "srcId", source.getMethodId(),
                        "tgtId", target.getMethodId(),
                        "id", edge.getId(),
                        "callType", edge.getCallType().name(),
                        "lineNumber", edge.getLineNumber(),
                        "expression", edge.getCallExpression() != null ? edge.getCallExpression() : ""));
            }
            if (!batch.isEmpty()) {
                try (var session = neo4jDriver.session()) {
                    session.run(
                        "UNWIND $edges AS edge " +
                        "MATCH (src:Method {methodId: edge.srcId}) " +
                        "MATCH (tgt:Method {methodId: edge.tgtId}) " +
                        "MERGE (src)-[c:CALLS {id: edge.id}]->(tgt) " +
                        "SET c.callType = edge.callType, " +
                        "    c.lineNumber = edge.lineNumber, " +
                        "    c.callExpression = edge.expression",
                        Map.of("edges", batch));
                }
            }
            if (log.isDebugEnabled() && subGraph.getCallsEdges() != null) {
                int total = subGraph.getCallsEdges().size();
                double pct = total > 0 ? 100.0 * batch.size() / total : 0;
                log.debug("CALLS edges resolved: {}/{} matched ({}%)",
                        batch.size(), total, String.format("%.1f", pct));
            }
        }

        // Create ACCESSES edges from methods to tables
        if (subGraph.getAccessesEdges() != null && !subGraph.getAccessesEdges().isEmpty()) {
            try (var session = neo4jDriver.session()) {
                for (AccessesEdge edge : subGraph.getAccessesEdges()) {
                    String appId = extractAppId(edge.getSourceMethodId());
                    String tableId = "table:" + appId + ":" + edge.getTargetTableId();
                    String edgeId = edge.getId();
                    if (edgeId == null || edgeId.isBlank()) {
                        edgeId = "acc-" + appId + "-" + edge.getTargetTableId();
                    }
                    session.run(
                        "MERGE (t:Table {tableId: $tableId}) SET t.tableName = $tableName " +
                        "WITH t " +
                        "MATCH (m:Method) WHERE m.methodId = $methodId " +
                        "MERGE (m)-[a:ACCESSES {id: $edgeId}]->(t) " +
                        "SET a.operation = $op, a.rawSql = $sql",
                        Map.of("tableId", tableId,
                               "tableName", edge.getTargetTableId(),
                               "methodId", edge.getSourceMethodId(),
                               "edgeId", edgeId,
                               "op", edge.getOperation() != null ? edge.getOperation().name() : "SELECT",
                               "sql", edge.getRawSql() != null ? edge.getRawSql() : ""));
                }
            }
        }
    }

    @Override
    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
        return List.of();
    }

    @Override
    public TraversalResult traversal(TraversalSpec spec) {
        List<MethodNode> methods = new ArrayList<>();
        List<CallsEdge> callsEdges = new ArrayList<>();
        List<AccessesEdge> accessesEdges = new ArrayList<>();

        Neo4jMethodEntity source = methodRepo.findSingleByMethodId(spec.getStartMethodId());
        if (source != null) {
            methods.add(toMethodNode(source));
        }

        boolean upstream = "UPSTREAM".equals(spec.getDirection());
        List<Neo4jMethodEntity> related = upstream
                ? methodRepo.findCallerNodes(spec.getStartMethodId())
                : methodRepo.findCalleeNodes(spec.getStartMethodId());

        int edgeCounter = 0;
        for (Neo4jMethodEntity target : related) {
            methods.add(toMethodNode(target));
            callsEdges.add(CallsEdge.builder()
                    .id("ce-" + (edgeCounter++))
                    .sourceMethodId(upstream ? target.getMethodId() : spec.getStartMethodId())
                    .targetMethodId(upstream ? spec.getStartMethodId() : target.getMethodId())
                    .callType(CallType.INTERNAL)
                    .lineNumber(0)
                    .callExpression(target.getMethodId())
                    .build());
        }

        // Query ACCESSES edges via Neo4j Driver directly (bypasses SDN projection issues)
        if (!upstream) {
            try (var session = neo4jDriver.session()) {
                var result = session.run(
                        "MATCH (m:Method {methodId: $methodId})-[a:ACCESSES]->(t:Table) " +
                        "RETURN a.id AS edgeId, a.operation AS op, a.rawSql AS sql, " +
                        "t.tableName AS tableName",
                        Map.of("methodId", spec.getStartMethodId()));
                while (result.hasNext()) {
                    var record = result.next();
                    accessesEdges.add(AccessesEdge.builder()
                            .id(record.get("edgeId").asString())
                            .sourceMethodId(spec.getStartMethodId())
                            .targetTableId(record.get("tableName").asString())
                            .operation(record.get("op").asString() != null
                                    ? SqlOperation.valueOf(record.get("op").asString())
                                    : SqlOperation.SELECT)
                            .rawSql(record.get("sql").asString(""))
                            .build());
                }
            }
        }
        return new TraversalResult(methods, callsEdges, accessesEdges);
    }

    private String extractAppId(String methodId) {
        if (methodId == null) return "unknown";
        int colon = methodId.indexOf(':');
        return colon > 0 ? methodId.substring(0, colon) : "unknown";
    }

    /**
     * Resolves a callee from a partial methodId (e.g. "configService.deleteScriptConfig")
     * by matching signature prefix, preferring methods in the same app as the caller.
     */
    private Neo4jMethodEntity resolveCallee(CallsEdge edge, Neo4jMethodEntity source, String callType) {
        // Try exact methodId match first (without signature fallback)
        String targetId = edge.getTargetMethodId();
        Neo4jMethodEntity exact = methodRepo.findByMethodId(targetId);
        if (exact != null && !exact.getMethodId().equals(source.getMethodId())) {
            return exact;
        }

        // Smart signature prefix matching: prefer same app, non-self
        String methodName = extractMethodName(targetId);
        if (methodName.isEmpty()) return null;

        String appId = source.getAppId();
        List<Neo4jMethodEntity> candidates = methodRepo.findCandidatesBySignature(methodName);

        // Prefer same app, non-self match
        for (Neo4jMethodEntity c : candidates) {
            if (appId.equals(c.getAppId()) && !c.getMethodId().equals(source.getMethodId())) {
                return c;
            }
        }
        // Fall back: same app, even if self (e.g. recursive)
        for (Neo4jMethodEntity c : candidates) {
            if (appId.equals(c.getAppId())) return c;
        }
        // Only cross-app for EXTERNAL calls (restTemplate, feign, etc.)
        if ("EXTERNAL".equals(callType)) {
            for (Neo4jMethodEntity c : candidates) {
                if (!c.getMethodId().equals(source.getMethodId())) return c;
            }
            return candidates.isEmpty() ? null : candidates.get(0);
        }
        return null;
    }

    private Neo4jMethodEntity findMethod(String methodId) {
        Neo4jMethodEntity entity = methodRepo.findByMethodId(methodId);
        if (entity != null) return entity;

        String methodName = extractMethodName(methodId);
        if (methodName.isEmpty()) return null;

        List<Neo4jMethodEntity> matches = methodRepo.findBySignaturePrefix(methodName);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private String extractMethodName(String methodId) {
        int parenIdx = methodId.indexOf('(');
        String withoutParams = parenIdx > 0 ? methodId.substring(0, parenIdx) : methodId;
        int lastDot = withoutParams.lastIndexOf('.');
        return lastDot > 0 ? withoutParams.substring(lastDot + 1) : withoutParams;
    }

    private MethodNode toMethodNode(Neo4jMethodEntity entity) {
        return MethodNode.builder()
                .methodId(entity.getMethodId())
                .signature(entity.getSignature())
                .returnType(entity.getReturnType())
                .appId(entity.getAppId())
                .systemId(entity.getSystemId())
                .className(entity.getClassName())
                .packageName(entity.getPackageName())
                .isEntry(entity.isEntry())
                .httpPath(entity.getHttpPath())
                .httpMethod(entity.getHttpMethod())
                .lineNumber(entity.getLineNumber())
                .build();
    }

    private String paramsToString(MethodNode m) {
        if (m.getParams() == null) return "";
        return m.getParams().stream()
                .map(MethodNode.Param::getType)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
}

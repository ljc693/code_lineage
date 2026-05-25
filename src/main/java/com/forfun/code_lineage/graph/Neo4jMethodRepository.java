package com.forfun.code_lineage.graph;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface Neo4jMethodRepository extends Neo4jRepository<Neo4jMethodEntity, String> {

    @Query("MATCH (m:Method {appId: $appId}) WHERE m.isEntry = true RETURN m")
    List<Neo4jMethodEntity> findEntryMethods(@Param("appId") String appId);

    @Query("MATCH (m:Method {methodId: $methodId}) RETURN m")
    Neo4jMethodEntity findSingleByMethodId(@Param("methodId") String methodId);

    @Query("MATCH (m:Method {methodId: $methodId}) RETURN m")
    Neo4jMethodEntity findByMethodId(@Param("methodId") String methodId);

    @Query("MATCH (m:Method) WHERE m.signature STARTS WITH $methodPrefix RETURN m LIMIT 1")
    List<Neo4jMethodEntity> findBySignaturePrefix(@Param("methodPrefix") String methodPrefix);

    @Query("MATCH (m:Method) WHERE m.signature STARTS WITH $methodPrefix RETURN m LIMIT 50")
    List<Neo4jMethodEntity> findCandidatesBySignature(@Param("methodPrefix") String methodPrefix);

    @Query("MATCH (m:Method {methodId: $methodId})-[c:CALLS]->(t:Method) RETURN t")
    List<Neo4jMethodEntity> findCalleeNodes(@Param("methodId") String methodId);

    @Query("MATCH (m:Method {methodId: $methodId})-[a:ACCESSES]->(t:Table) " +
           "RETURN a.id AS accEdgeId, a.operation AS accOperation, a.rawSql AS accRawSql, " +
           "t.tableId AS accTableId, t.tableName AS accTableName")
    List<AccessesProjection> findAccesses(@Param("methodId") String methodId);

    interface AccessesProjection {
        String getAccEdgeId();
        String getAccOperation();
        String getAccRawSql();
        String getAccTableId();
        String getAccTableName();
    }

    @Query("MATCH (s:Method)-[c:CALLS]->(m:Method {methodId: $methodId}) RETURN s")
    List<Neo4jMethodEntity> findCallerNodes(@Param("methodId") String methodId);

    @Query("MATCH ()-[c:CALLS]->() RETURN count(c)")
    long countCallsEdges();

    @Query("MATCH (src:Method {methodId: $srcId}) " +
           "MATCH (tgt:Method {methodId: $tgtId}) " +
           "MERGE (src)-[c:CALLS {id: $edgeId}]->(tgt) " +
           "SET c.callType = $callType, c.lineNumber = $lineNumber, c.callExpression = $expression")
    void createCallsEdge(@Param("srcId") String srcId, @Param("tgtId") String tgtId,
                         @Param("edgeId") String edgeId, @Param("callType") String callType,
                         @Param("lineNumber") int lineNumber, @Param("expression") String expression);

    @Query("MERGE (t:Table {tableId: $tableId}) " +
           "SET t.tableName = $tableName " +
           "WITH t " +
           "MATCH (m:Method {methodId: $methodId}) " +
           "MERGE (m)-[a:ACCESSES {id: $edgeId}]->(t) " +
           "SET a.operation = $operation, a.rawSql = $rawSql")
    void createAccessesEdge(@Param("methodId") String methodId, @Param("tableId") String tableId,
                            @Param("tableName") String tableName, @Param("edgeId") String edgeId,
                            @Param("operation") String operation, @Param("rawSql") String rawSql);
}

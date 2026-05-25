package com.forfun.code_lineage.sql.collector;

import com.forfun.code_lineage.sql.model.CapturedSql;
import com.forfun.code_lineage.sql.model.ColumnRef;
import com.forfun.code_lineage.sql.model.DbType;
import com.forfun.code_lineage.sql.collector.ParserFactory;
import com.forfun.code_lineage.sql.collector.SqlDialectParser;
import com.forfun.code_lineage.graph.ColumnLineageRepository;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service that captures SQL from the Agent hook and writes lineage to Neo4j.
 *
 * V2.5: Test-mode — captures SQL via manual feed.
 * V3: Agent-mode — auto-intercepts via java.lang.instrument.
 */
@Service
public class SqlCaptureService {

    private static final Logger log = LoggerFactory.getLogger(SqlCaptureService.class);

    private final Driver neo4jDriver;
    private final ColumnLineageRepository columnLineageRepo;
    private final Queue<CapturedSql> buffer = new ConcurrentLinkedQueue<>();
    private final Set<String> seenTables = Collections.synchronizedSet(new LinkedHashSet<>());

    public SqlCaptureService(Driver neo4jDriver, ColumnLineageRepository columnLineageRepo) {
        this.neo4jDriver = neo4jDriver;
        this.columnLineageRepo = columnLineageRepo;
    }

    /**
     * Agent hook point. Called by the JDBC Agent (or manually in V2.5 tests).
     * Thread-safe, non-blocking.
     */
    public void onSqlExecute(String sql, List<?> params, String jdbcUrl,
                              String callerClass, String callerMethod) {
        CapturedSql captured = CapturedSql.capture(sql, params, jdbcUrl, callerClass, callerMethod);
        buffer.offer(captured);
        processImmediate(captured);
    }

    /**
     * Immediate synchronous processing for single captures.
     * For batch mode, use flush().
     */
    private void processImmediate(CapturedSql cs) {
        DbType dbType = cs.getDbType();
        SqlDialectParser parser = ParserFactory.get(dbType);

        var tables = parser.extractTables(cs.getRawSql(), cs.getParams());
        if (tables.isEmpty()) return;

        String callerId = cs.getCallerClassName() + "." + cs.getCallerMethodName();

        try (var session = neo4jDriver.session()) {
            for (var pt : tables) {
                seenTables.add(pt.tableName());
                String tableId = "table:" + dbType.name().toLowerCase() + ":" + pt.tableName();
                session.run(
                    "MERGE (t:Table {tableId: $tid}) SET t.tableName = $tn, t.schema = $sc " +
                    "WITH t " +
                    "MATCH (m:Method) WHERE m.methodId CONTAINS $caller " +
                       "OR m.methodId ENDS WITH $cSig " +
                       "OR m.className = $cCls " +
                    "MERGE (m)-[a:ACCESSES {id: $eid}]->(t) " +
                    "SET a.operation = $op, a.rawSql = $sql, a.dbType = $db",
                    Map.of("tid", tableId,
                           "tn", pt.tableName(), "sc", pt.schema() != null ? pt.schema() : "",
                           "caller", callerId,
                           "cSig", cs.getCallerMethodName(),
                           "cCls", cs.getCallerClassName(),
                           "eid", UUID.randomUUID().toString(),
                           "op", pt.operation().name(),
                           "sql", pt.rawSql().length() > 500
                                   ? pt.rawSql().substring(0, 500) : pt.rawSql(),
                           "db", dbType.name()));

                // Persist column-level references to column_lineage table
                for (ColumnRef col : pt.columnRefs()) {
                    columnLineageRepo.upsert(
                            cs.getCallerClassName(),  // appId (from class name)
                            callerId,
                            cs.getCallerMethodName() != null ? cs.getCallerMethodName() : "",
                            cs.getCallerClassName(),
                            pt.tableName(),
                            col.getColumnName(),
                            pt.operation().name(),
                            dbType.name(),
                            cs.getRawSql().length() > 500
                                    ? cs.getRawSql().substring(0, 500) : cs.getRawSql());
                }
            }
        }

        log.debug("Captured SQL → {} tables ({}): {}",
                tables.size(), dbType, cs.getRawSql().substring(0, Math.min(80, cs.getRawSql().length())));
    }

    /** Batch flush buffered captures (for high-throughput scenarios). */
    public int flush() {
        int count = 0;
        CapturedSql cs;
        while ((cs = buffer.poll()) != null) {
            processImmediate(cs);
            count++;
        }
        return count;
    }

    public int bufferSize() { return buffer.size(); }
    public Set<String> getSeenTables() { return new LinkedHashSet<>(seenTables); }
}

package com.forfun.code_lineage.config;

import com.forfun.code_lineage.analyzer.AnalyzeResult;
import com.forfun.code_lineage.analyzer.AnalyzeTask;
import com.forfun.code_lineage.analyzer.JavaCodeAnalyzer;
import com.forfun.code_lineage.analyzer.governance.GovernanceAnalyzer;
import com.forfun.code_lineage.analyzer.governance.GovernanceMetrics;
import com.forfun.code_lineage.analyzer.fetch.FetchedCode;
import com.forfun.code_lineage.graph.ColumnLineageRepository;
import com.forfun.code_lineage.graph.Neo4jAdapter;
import com.forfun.code_lineage.graph.Neo4jMethodRepository;
import com.forfun.code_lineage.model.*;
import com.forfun.code_lineage.model.graph.*;
import com.forfun.code_lineage.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;

@Component
@ConditionalOnProperty(name = "lineage.dataloader.enabled", havingValue = "true", matchIfMissing = true)
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
    private static final String CLAWER_ROOT = "../clawer";
    private static final String SHENYU_ROOT = "../shenyu/shenyu-master";

    private final Neo4jMethodRepository methodRepo;
    private final GovernanceAnalyzer governanceAnalyzer;
    private final SqlParser sqlParser;
    private final org.neo4j.driver.Driver neo4jDriver;
    private final ColumnLineageRepository columnLineageRepo;

    public DataLoader(Neo4jMethodRepository methodRepo,
                      GovernanceAnalyzer governanceAnalyzer,
                      SqlParser sqlParser,
                      org.neo4j.driver.Driver neo4jDriver,
                      ColumnLineageRepository columnLineageRepo) {
        this.methodRepo = methodRepo;
        this.governanceAnalyzer = governanceAnalyzer;
        this.sqlParser = sqlParser;
        this.neo4jDriver = neo4jDriver;
        this.columnLineageRepo = columnLineageRepo;
    }

    @Override
    public void run(String... args) throws Exception {
        Path clawerPath = Path.of(CLAWER_ROOT).toAbsolutePath().normalize();
        if (!Files.exists(clawerPath)) {
            log.info("Clawer project not found at {}, skipping data load", CLAWER_ROOT);
            return;
        }

        // Scan clawer if not loaded
        try {
            if (methodRepo.findEntryMethods("clawer").isEmpty()) {
                scanProject(clawerPath, "clawer");
            } else {
                log.info("Clawer already loaded, skipping");
            }
        } catch (Exception e) {
            log.error("Failed to scan clawer: {}", e.getMessage());
        }

        // Scan ShenYu if available and not loaded
        try {
            Path shenyuPath = Path.of(SHENYU_ROOT).toAbsolutePath().normalize();
            if (Files.exists(shenyuPath) && methodRepo.findEntryMethods("shenyu").isEmpty()) {
                scanProject(shenyuPath, "shenyu");
            } else if (Files.exists(shenyuPath)) {
                log.info("ShenYu already loaded, skipping");
            }
        } catch (Exception e) {
            log.error("Failed to scan ShenYu: {}", e.getMessage());
        }
    }

    private void scanProject(Path projectPath, String appId) throws Exception {
        log.info("=== Scanning {} ({}) ===", appId, projectPath);

        List<String> javaFiles = Files.walk(projectPath)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().contains("/src/main/java/"))
                .filter(p -> !p.toString().contains("/test/"))
                .map(p -> projectPath.relativize(p).toString())
                .toList();

        log.info("Found {} Java files", javaFiles.size());

        // Phase 1: Scan all files at once (collect methods + relations in memory)
        JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer();
        AnalyzeResult result = analyzer.analyze(AnalyzeTask.builder()
                .fetchedCode(FetchedCode.builder()
                        .baseDir(projectPath.toString())
                        .changedFiles(javaFiles)
                        .appId(appId)
                        .build())
                .appId(appId)
                .build());

        log.info("Scanned: {} methods, {} relations",
                result.getMethods().size(), result.getRelations().size());

        // Phase 2: Run SQL parsing (before Neo4j write, independent)
        log.info("Running SQL parser...");
        ParseTask sqlTask = ParseTask.builder()
                .baseDir(projectPath.toString())
                .methods(result.getMethods())
                .appId(appId)
                .rawRelations(result.getRelations())
                .build();
        List<SqlRelations> sqlRelations = sqlParser.parse(sqlTask);
        if (!sqlRelations.isEmpty()) {
            log.info("SQL parsed: {} table references", sqlRelations.size());
        }

        // Phase 3: Build edges and write ALL at once to Neo4j
        List<CallsEdge> edges = new ArrayList<>();
        int counter = 0;
        for (RawRelation r : result.getRelations()) {
            edges.add(CallsEdge.builder()
                    .id("c-" + appId + "-" + (counter++))
                    .sourceMethodId(r.getCaller().getMethodId())
                    .targetMethodId(r.getCallee().getMethodId())
                    .callType(r.getCallType())
                    .lineNumber(r.getLineNumber())
                    .callExpression(r.getCallExpression())
                    .build());
        }

        Neo4jAdapter neo4j = new Neo4jAdapter(methodRepo, neo4jDriver);
        SubGraph sg = SubGraph.builder()
                .methods(result.getMethods())
                .callsEdges(edges)
                .build();
        neo4j.write(sg);
        log.info("Written to Neo4j: {} CALLS edges", methodRepo.countCallsEdges());

        // Phase 4: Write ACCESSES edges from SQL parse results
        int accessCounter = 0;
        for (SqlRelations sr : sqlRelations) {
            String tableId = "table:" + appId + ":" + sr.getTableName();
            String methodSignature = sr.getSourceMethodId();
            // Try exact match first, fall back to signature-prefix match
            try (var session = neo4jDriver.session()) {
                session.run(
                    "MERGE (t:Table {tableId: $tableId}) SET t.tableName = $tableName " +
                    "WITH t " +
                    "MATCH (m:Method {appId: $appId}) WHERE m.methodId = $methodId " +
                       "OR m.signature STARTS WITH $sigPrefix " +
                    "MERGE (m)-[a:ACCESSES {id: $edgeId}]->(t) " +
                    "SET a.operation = $op, a.rawSql = $sql",
                    Map.of("tableId", tableId, "tableName", sr.getTableName(),
                           "appId", appId,
                           "methodId", methodSignature,
                           "sigPrefix", extractSigPrefix(methodSignature),
                           "edgeId", "acc-" + appId + "-" + (accessCounter++),
                           "op", sr.getOperation() != null ? sr.getOperation().name() : "SELECT",
                           "sql", sr.getRawSql() != null ? sr.getRawSql() : ""));
            }
        }

        if (!sqlRelations.isEmpty()) {
            log.info("SQL parsed: {} table accesses found", sqlRelations.size());
        }

        // Phase 5: Populate MySQL column_lineage from SQL analysis results
        int colCount = 0;
        for (SqlRelations sr : sqlRelations) {
            String srcMethodId = sr.getSourceMethodId();
            if (srcMethodId == null || srcMethodId.startsWith(MyBatisPlusSqlParser.STUB_METHOD_PREFIX)) continue;
            String className = extractClassName(srcMethodId);
            String sig = extractSignature(srcMethodId);
            List<String> columns = sr.getColumnNames();
            if (columns == null || columns.isEmpty()) {
                columns = List.of("*"); // fallback: no column detail available
            }
            for (String col : columns) {
                columnLineageRepo.upsert(appId, srcMethodId, sig, className,
                        sr.getTableName(), col, sr.getOperation() != null ? sr.getOperation().name() : "SELECT",
                        "MYSQL", sr.getRawSql() != null ? sr.getRawSql() : "static-analysis");
                colCount++;
            }
        }
        if (colCount > 0) {
            log.info("Column lineage: {} entries written to MySQL", colCount);
        }

        long edgeCount = methodRepo.countCallsEdges();
        long entryCount = methodRepo.findEntryMethods(appId).size();

        log.info("Scan complete: {} methods, {} edges (incl. DB), {} entry points",
                result.getMethods().size(), edgeCount, entryCount);

        // Auto-run governance analysis
        GovernanceMetrics gov = governanceAnalyzer.analyze(
                FetchedCode.builder().baseDir(projectPath.toString())
                        .appId(appId).changedFiles(List.of()).build());

        log.info("Governance: {} classes, {} methods, complexity={}, duplication={}%, comments={}%",
                gov.getClassCount(), gov.getMethodCount(),
                gov.getCyclomaticComplexity(), gov.getDuplicationRate(),
                gov.getCommentCoverage());
    }

    private String extractClassName(String methodId) {
        if (methodId == null) return "";
        // "appId:package.ClassName.method(params)" -> "ClassName"
        int colon = methodId.indexOf(':');
        String after = colon > 0 ? methodId.substring(colon + 1) : methodId;
        int paren = after.indexOf('(');
        String beforeParams = paren > 0 ? after.substring(0, paren) : after;
        // beforeParams = "pkg1.pkg2.ClassName.methodName"
        String[] parts = beforeParams.split("\\.");
        // Second-to-last part is the class name
        return parts.length >= 2 ? parts[parts.length - 2] : beforeParams;
    }

    private String extractSignature(String methodId) {
        if (methodId == null) return "";
        // "appId:package.ClassName.method(params)" -> "method(params)"
        int lastDot = methodId.lastIndexOf('.');
        return lastDot > 0 ? methodId.substring(lastDot + 1) : methodId;
    }

    /** Extract method name prefix from a methodId for fuzzy matching. */
    private String extractSigPrefix(String methodId) {
        if (methodId == null) return "";
        // "appId:pkg.Class.method(params)" → "method"
        int colon = methodId.indexOf(':');
        String afterApp = colon > 0 ? methodId.substring(colon + 1) : methodId;
        int paren = afterApp.indexOf('(');
        String withoutParams = paren > 0 ? afterApp.substring(0, paren) : afterApp;
        int lastDot = withoutParams.lastIndexOf('.');
        return lastDot > 0 ? withoutParams.substring(lastDot + 1) : withoutParams;
    }
}

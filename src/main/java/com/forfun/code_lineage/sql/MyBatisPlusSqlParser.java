package com.forfun.code_lineage.sql;

import com.forfun.code_lineage.model.graph.MethodNode;
import com.forfun.code_lineage.model.SqlOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;

/**
 * Orchestrates MyBatis-Plus SQL relation discovery by delegating to
 * {@link MapperDiscoveryService} and {@link OperationInferenceService}.
 * <p>
 * Extends {@link AbstractSqlParser} to inherit file-walking, error logging,
 * and deduplication utilities, but overrides {@link #parse(ParseTask)} entirely
 * because MyBatis-Plus discovery requires a full-project scan before any
 * per-method matching can occur.
 */
@Component
public class MyBatisPlusSqlParser extends AbstractSqlParser {

    private static final Logger log = LoggerFactory.getLogger(MyBatisPlusSqlParser.class);

    /** Prefix used for stub SqlRelations that ensure Table nodes are created
     *  even when no application method directly injects a mapper. */
    public static final String STUB_METHOD_PREFIX = "mapper:";

    private final MapperDiscoveryService discoveryService;
    private final OperationInferenceService operationService;

    public MyBatisPlusSqlParser(MapperDiscoveryService discoveryService,
                                OperationInferenceService operationService) {
        this.discoveryService = discoveryService;
        this.operationService = operationService;
    }

    // ── AbstractSqlParser hooks (per-file parsing not used here) ─────

    @Override
    protected String fileSuffix() { return ".java"; }

    @Override
    protected String pathFilter() { return "/src/main/"; }

    /**
     * Per-file parsing is not used because MyBatis-Plus requires a
     * full-project scan before matching.  All work is done in
     * {@link #parse(ParseTask)}.
     */
    @Override
    protected List<SqlRelations> parseFile(Path path, String content, ParseTask task) {
        return null; // defer to parse()
    }

    // ── Override template method ─────────────────────────────────────

    @Override
    public List<SqlRelations> parse(ParseTask task) {
        Path baseDir = Paths.get(task.getBaseDir());
        if (!Files.exists(baseDir)) return List.of();

        // Step 1: single-pass project scan (once, not per-file)
        ProjectModel model = discoveryService.scan(baseDir);
        if (model.isEmpty()) return List.of();

        // Step 2: build mapper call signatures from raw relations
        Map<String, Set<String>> callSignatures = operationService.buildCallSignatures(
                task.getRawRelations(), model);
        Map<String, List<String>> simpleNameLookup = model.simpleNameToMappers();

        // Step 3: match methods to mapper-using classes
        List<SqlRelations> results = new ArrayList<>();
        Set<String> seenTables = new LinkedHashSet<>();

        for (MethodNode method : task.getMethods()) {
            List<String> mapperFqcns = resolveMappers(method.getClassName(),
                    model.classToMappers(), simpleNameLookup);
            if (mapperFqcns == null) continue;

            String poName = null;
            for (String mfq : mapperFqcns) {
                String tableName = model.mapperToTable().get(mfq);
                if (tableName == null) continue;
                seenTables.add(tableName);
                if (poName == null) poName = model.mapperToPo().get(mfq);
                List<String> columns = poName != null
                        ? model.poColumns().getOrDefault(poName, List.of()) : List.of();

                results.add(SqlRelations.builder()
                        .sourceMethodId(method.getMethodId())
                        .tableName(tableName)
                        .columnNames(columns)
                        .operation(operationService.infer(
                                method, task.getRawRelations(), callSignatures))
                        .rawSql("mybatis-plus:" + mfq)
                        .mapperInterface(mfq)
                        .build());
            }
        }

        // Step 4: stub entries for mappers that no app method injects
        for (var entry : model.mapperToTable().entrySet()) {
            if (!seenTables.contains(entry.getValue())) {
                results.add(SqlRelations.builder()
                        .sourceMethodId(STUB_METHOD_PREFIX + entry.getKey())
                        .tableName(entry.getValue())
                        .columnNames(List.of())
                        .operation(SqlOperation.SELECT)
                        .rawSql("mybatis-plus:" + entry.getKey())
                        .mapperInterface(entry.getKey())
                        .build());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("MyBatis-Plus: {} relations, {} tables ({} stubs)",
                    results.size(), seenTables.size(),
                    results.size() - seenTables.size());
        }
        return results;
    }

    // ── Internal helpers ─────────────────────────────────────────────

    /**
     * Resolves zero or more mapper FQCNs for a given class name.
     * Tries FQCN match, then suffix match (".$className"), then simple-name lookup.
     */
    private static List<String> resolveMappers(String className,
                                                Map<String, List<String>> classToMappers,
                                                Map<String, List<String>> simpleNameLookup) {
        if (className == null) return null;
        List<String> mappers = classToMappers.get(className);
        if (mappers != null) return mappers;
        for (var entry : classToMappers.entrySet()) {
            if (entry.getKey().endsWith("." + className)) return entry.getValue();
        }
        return simpleNameLookup.get(className);
    }
}

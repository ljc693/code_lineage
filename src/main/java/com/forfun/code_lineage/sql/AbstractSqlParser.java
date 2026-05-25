package com.forfun.code_lineage.sql;

import com.forfun.code_lineage.model.SqlOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.*;

/**
 * Template-method base for SQL parsers.  Subclasses define what files to scan
 * ({@link #fileSuffix}, {@link #pathFilter}) and how to parse each file
 * ({@link #parseFile}).  This class handles file walking, error logging,
 * and result deduplication.
 *
 * <p>Shared pattern across MyBatis-Plus (Java source), MyBatis XML, and
 * future JPA / raw SQL parsers.</p>
 */
public abstract class AbstractSqlParser implements SqlParser {

    private static final Logger log = LoggerFactory.getLogger(AbstractSqlParser.class);

    // ── hooks for subclasses ───────────────────────────────────────

    /** File extension to scan, e.g. {@code ".java"} or {@code ".xml"}. */
    protected abstract String fileSuffix();

    /**
     * Substring that file paths must contain to be included.
     * Return {@code ""} to include all files.
     * {@code "mapper"} for MyBatis XML; {@code "/src/main/"} for Java.
     */
    protected abstract String pathFilter();

    /**
     * Parse a single file and return any discovered SqlRelations.
     * @param path     the file being parsed
     * @param content  its full text content
     * @param task     the original ParseTask (for methods list, appId, etc.)
     * @return relations found in this file, or empty list / null
     */
    protected abstract List<SqlRelations> parseFile(Path path, String content, ParseTask task);

    // ── template method ─────────────────────────────────────────────

    @Override
    public List<SqlRelations> parse(ParseTask task) {
        Path baseDir = Paths.get(task.getBaseDir());
        if (!Files.exists(baseDir)) return List.of();

        List<SqlRelations> results = new ArrayList<>();
        walkProjectFiles(baseDir, (path, content) -> {
            List<SqlRelations> found = parseFile(path, content, task);
            if (found != null) results.addAll(found);
        });
        return deduplicate(results);
    }

    // ── shared utilities ────────────────────────────────────────────

    /** Walks the project tree and calls consumer for each matching file. */
    protected void walkProjectFiles(Path baseDir, BiConsumer<Path, String> consumer) {
        try (var walk = Files.walk(baseDir)) {
            walk.filter(p -> p.toString().endsWith(fileSuffix())
                            && (pathFilter().isEmpty() || p.toString().contains(pathFilter()))
                            && !p.toString().contains("/test/"))
                    .forEach(f -> {
                        String content = readFile(f);
                        if (content != null) consumer.accept(f, content);
                    });
        } catch (Exception e) {
            log.warn("Failed to walk project directory: {}", baseDir, e);
        }
    }

    /** Reads a file to a string, returning null (with log) on failure. */
    protected String readFile(Path f) {
        try { return Files.readString(f); }
        catch (Exception e) { log.warn("Failed to read file: {}", f, e); return null; }
    }

    /** Extracts the package name from Java source content, or null. */
    protected static String extractPackage(String content) {
        Matcher m = Pattern.compile("package\\s+([\\w.]+)\\s*;").matcher(content);
        return m.find() ? m.group(1) : null;
    }

    /** Deduplicates by (sourceMethodId, tableName) keeping the first entry. */
    protected List<SqlRelations> deduplicate(List<SqlRelations> raw) {
        Map<String, SqlRelations> seen = new LinkedHashMap<>();
        for (SqlRelations r : raw) {
            String key = r.getSourceMethodId() + "|" + r.getTableName();
            seen.putIfAbsent(key, r);
        }
        return new ArrayList<>(seen.values());
    }

    /** Infers SQL operation from a method signature name. */
    protected static SqlOperation operationFromName(String name) {
        if (name == null) return SqlOperation.SELECT;
        String lower = name.toLowerCase();
        if (lower.startsWith("delete") || lower.startsWith("remove")) return SqlOperation.DELETE;
        if (lower.startsWith("update") || lower.startsWith("modify")) return SqlOperation.UPDATE;
        if (lower.startsWith("insert") || lower.startsWith("save")
                || lower.startsWith("create") || lower.startsWith("add")) return SqlOperation.INSERT;
        return SqlOperation.SELECT;
    }
}

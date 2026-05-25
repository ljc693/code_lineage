package com.forfun.codel_ineage.sql;

import java.util.*;

/**
 * Value object holding the results of a single-pass project scan for
 * MyBatis-Plus mapper/PO discovery.  Replaces the three output-parameter
 * Maps previously threaded through discoverMappers / discoverMapperUsages /
 * buildVarToMapperMap.
 */
public class ProjectModel {

    /** mapperFqcn → tableName */
    private final Map<String, String> mapperToTable = new LinkedHashMap<>();
    /** poClassName → field names (column names) */
    private final Map<String, List<String>> poColumns = new LinkedHashMap<>();
    /** mapperFqcn → poClassName */
    private final Map<String, String> mapperToPo = new LinkedHashMap<>();
    /** classFqcn → list of mapper FQCNs used by that class */
    private final Map<String, List<String>> classToMappers = new LinkedHashMap<>();
    /** variable name → set of mapper FQCNs (for resolving variable→type) */
    private final Map<String, Set<String>> varToMappers = new LinkedHashMap<>();

    // ── builder-style setters ──────────────────────────────────────

    public void putMapperTable(String mapperFqcn, String tableName) {
        mapperToTable.put(mapperFqcn, tableName);
    }

    public void putPoColumns(String poClass, List<String> columns) {
        poColumns.put(poClass, columns);
    }

    public void putMapperPo(String mapperFqcn, String poClass) {
        mapperToPo.put(mapperFqcn, poClass);
    }

    public void addClassMapper(String classFqcn, String mapperFqcn) {
        classToMappers.computeIfAbsent(classFqcn, k -> new ArrayList<>()).add(mapperFqcn);
    }

    public void addVarMapper(String varName, String mapperFqcn) {
        varToMappers.computeIfAbsent(varName, k -> new LinkedHashSet<>()).add(mapperFqcn);
    }

    // ── simple-name lookup helpers ─────────────────────────────────

    public Map<String, List<String>> simpleNameToMappers() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var entry : classToMappers.entrySet()) {
            String simple = extractSimpleName(entry.getKey());
            result.computeIfAbsent(simple, k -> new ArrayList<>()).addAll(entry.getValue());
        }
        return result;
    }

    public boolean isEmpty() { return mapperToTable.isEmpty(); }

    // ── getters ────────────────────────────────────────────────────

    public Map<String, String> mapperToTable() { return mapperToTable; }
    public Map<String, List<String>> poColumns() { return poColumns; }
    public Map<String, String> mapperToPo() { return mapperToPo; }
    public Map<String, List<String>> classToMappers() { return classToMappers; }
    public Map<String, Set<String>> varToMappers() { return varToMappers; }

    // ── internal ───────────────────────────────────────────────────

    private static String extractSimpleName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }
}

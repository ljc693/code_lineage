package com.forfun.code_lineage.sql;

import java.util.List;

/**
 * Extracts column references from SQL text or PO definitions.
 * Different implementations handle MyBatis XML, JPQL, raw SQL, etc.
 */
@FunctionalInterface
public interface ColumnExtractor {
    List<String> extractColumns(String raw);
}

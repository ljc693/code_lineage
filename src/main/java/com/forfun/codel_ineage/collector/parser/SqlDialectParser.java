package com.forfun.codel_ineage.collector.parser;

import com.forfun.codel_ineage.collector.model.ColumnRef;
import com.forfun.codel_ineage.model.SqlOperation;
import java.util.*;

/**
 * Pluggable SQL dialect parser.
 * Each database type provides its own implementation.
 */
public interface SqlDialectParser {

    /** Which database this parser handles */
    String dialect();

    /**
     * Extract table references from a SQL statement.
     * @param sql raw SQL string
     * @param params parameter values (for prepared statements)
     * @return list of (tableName, columnRefs, operation) tuples
     */
    List<ParsedTable> extractTables(String sql, List<String> params);

    record ParsedTable(String tableName, List<ColumnRef> columnRefs,
                        SqlOperation operation, String schema, String rawSql) {}
}

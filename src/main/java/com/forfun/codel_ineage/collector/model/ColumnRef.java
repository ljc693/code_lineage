package com.forfun.codel_ineage.collector.model;

import lombok.Builder;
import lombok.Data;

/**
 * A column reference extracted from SQL text or JDBC metadata.
 */
@Data
@Builder
public class ColumnRef {
    private String columnName;
    private String tableName;       // from alias or metadata lookup
    private String alias;           // AS alias in SQL
    private String dataType;        // from JDBC metadata
    private boolean isPrimaryKey;
    private boolean isNullable;
    private ColumnSource source;    // how this column was identified

    public enum ColumnSource {
        SQL_SELECT,     // extracted from SELECT clause
        SQL_WHERE,      // extracted from WHERE clause
        SQL_JOIN,       // extracted from JOIN ON clause
        SQL_INSERT,     // extracted from INSERT column list
        SQL_UPDATE,     // extracted from UPDATE SET clause
        JDBC_METADATA,  // from ResultSetMetaData
        DB_SCHEMA       // from information_schema
    }

    public String toQualifiedName() {
        if (tableName != null && !tableName.isEmpty()) {
            return tableName + "." + columnName;
        }
        return columnName;
    }
}

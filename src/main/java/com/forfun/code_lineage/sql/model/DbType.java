package com.forfun.code_lineage.sql.model;

public enum DbType {
    MYSQL,
    POSTGRESQL,
    DB2,
    ORACLE,
    SQLSERVER,
    GENERIC;

    public static DbType fromJdbcUrl(String url) {
        if (url == null) return GENERIC;
        String u = url.toLowerCase();
        if (u.contains("mysql")) return MYSQL;
        if (u.contains("postgresql") || u.contains("pgsql")) return POSTGRESQL;
        if (u.contains("db2")) return DB2;
        if (u.contains("oracle")) return ORACLE;
        if (u.contains("sqlserver")) return SQLSERVER;
        return GENERIC;
    }

    public static DbType fromProductName(String name) {
        if (name == null) return GENERIC;
        String n = name.toLowerCase();
        if (n.contains("mysql")) return MYSQL;
        if (n.contains("postgresql")) return POSTGRESQL;
        if (n.contains("db2")) return DB2;
        if (n.contains("oracle")) return ORACLE;
        if (n.contains("sql server") || n.contains("microsoft")) return SQLSERVER;
        return GENERIC;
    }
}

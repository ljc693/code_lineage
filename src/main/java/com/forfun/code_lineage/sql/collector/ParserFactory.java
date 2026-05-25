package com.forfun.code_lineage.sql.collector;

import com.forfun.code_lineage.sql.model.DbType;
import java.util.*;

public class ParserFactory {

    private static final Map<DbType, SqlDialectParser> PARSERS = Map.of(
            DbType.MYSQL, new MySqlParser(),
            DbType.POSTGRESQL, new PostgreSqlParser(),
            DbType.DB2, new GenericSqlParser(),
            DbType.ORACLE, new GenericSqlParser(),
            DbType.SQLSERVER, new GenericSqlParser(),
            DbType.GENERIC, new GenericSqlParser()
    );

    public static SqlDialectParser get(DbType dbType) {
        return PARSERS.getOrDefault(dbType, new GenericSqlParser());
    }

    public static SqlDialectParser getFromUrl(String jdbcUrl) {
        return get(DbType.fromJdbcUrl(jdbcUrl));
    }
}

package com.forfun.code_lineage.sql;

import java.util.List;

public interface SqlParser {
    List<SqlRelations> parse(ParseTask task);
}

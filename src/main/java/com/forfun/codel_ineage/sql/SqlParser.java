package com.forfun.codel_ineage.sql;

import java.util.List;

public interface SqlParser {
    List<SqlRelations> parse(ParseTask task);
}

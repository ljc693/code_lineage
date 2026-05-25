package com.forfun.codel_ineage.event;

import lombok.Getter;
import com.forfun.codel_ineage.sql.SqlRelations;
import java.util.List;

@Getter
public class SqlParsedEvent extends LineageEvent {
    private final List<SqlRelations> sqlRelations;

    public SqlParsedEvent(String traceId, List<SqlRelations> sqlRelations) {
        super(traceId);
        this.sqlRelations = sqlRelations;
    }
}

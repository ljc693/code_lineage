package com.forfun.codel_ineage.sql;

import com.forfun.codel_ineage.model.SqlOperation;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SqlRelations {
    private String sourceMethodId;
    private String tableName;
    private List<String> columnNames;
    private SqlOperation operation;
    private String rawSql;
    private String mapperInterface;
    private String mapperMethod;
}

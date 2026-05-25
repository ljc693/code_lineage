package com.forfun.code_lineage.sql;

import com.forfun.code_lineage.model.SqlOperation;
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

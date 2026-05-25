package com.forfun.codel_ineage.model.graph;

import com.forfun.codel_ineage.model.SqlOperation;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccessesEdge {
    private String id;
    private String sourceMethodId;
    private String targetTableId;
    private String targetColumnId;
    private SqlOperation operation;
    private String rawSql;
}

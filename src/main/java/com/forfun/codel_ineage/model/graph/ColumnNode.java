package com.forfun.codel_ineage.model.graph;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ColumnNode {
    private String columnId;
    private String columnName;
    private String dataType;
    private boolean isPrimaryKey;
    private boolean nullable;
    private String tableId;
}

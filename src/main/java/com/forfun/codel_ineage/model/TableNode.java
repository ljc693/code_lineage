package com.forfun.codel_ineage.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableNode {
    private String tableId;
    private String tableName;
    private String catalog;
    private String schema;
    private String appId;
}

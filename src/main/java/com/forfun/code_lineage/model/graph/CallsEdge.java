package com.forfun.code_lineage.model.graph;

import com.forfun.code_lineage.model.CallType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CallsEdge {
    private String id;
    private String sourceMethodId;
    private String targetMethodId;
    private CallType callType;
    private int lineNumber;
    private String callExpression;
}

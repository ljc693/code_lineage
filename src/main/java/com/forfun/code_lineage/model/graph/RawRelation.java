package com.forfun.code_lineage.model.graph;

import com.forfun.code_lineage.model.CallType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RawRelation {
    private MethodNode caller;
    private MethodNode callee;
    private CallType callType;
    private int lineNumber;
    private String callExpression;
}

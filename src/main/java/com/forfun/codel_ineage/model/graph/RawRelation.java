package com.forfun.codel_ineage.model.graph;

import com.forfun.codel_ineage.model.CallType;
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

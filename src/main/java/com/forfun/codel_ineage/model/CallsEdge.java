package com.forfun.codel_ineage.model;

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

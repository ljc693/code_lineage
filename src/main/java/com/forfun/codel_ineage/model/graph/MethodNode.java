package com.forfun.codel_ineage.model.graph;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MethodNode {
    private String methodId;
    private String signature;
    private String returnType;
    private List<Param> params;
    private List<String> annotations;
    private int lineNumber;
    private boolean isEntry;
    private String httpPath;
    private String httpMethod;
    private String appId;
    private String systemId;         // V2 reserved
    private String className;
    private String packageName;
    private boolean isAbstract;
    private boolean isGetterOrSetter;

    @Data
    @Builder
    public static class Param {
        private String name;
        private String type;
    }
}

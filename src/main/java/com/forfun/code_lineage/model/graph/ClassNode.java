package com.forfun.code_lineage.model.graph;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ClassNode {
    private String classId;
    private String packageName;
    private String className;
    private String superClass;
    private List<String> interfaces;
    private String appId;
}

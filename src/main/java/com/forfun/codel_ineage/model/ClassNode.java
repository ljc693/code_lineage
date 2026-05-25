package com.forfun.codel_ineage.model;

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

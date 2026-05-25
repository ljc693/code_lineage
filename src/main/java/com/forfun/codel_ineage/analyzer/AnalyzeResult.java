package com.forfun.codel_ineage.analyzer;

import com.forfun.codel_ineage.model.MethodNode;
import com.forfun.codel_ineage.model.RawRelation;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AnalyzeResult {
    private String appId;
    private List<MethodNode> methods;
    private List<RawRelation> relations;
    private String techStack;
}

package com.forfun.codel_ineage.analyzer;

import com.forfun.codel_ineage.model.graph.MethodNode;
import com.forfun.codel_ineage.model.graph.RawRelation;
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

package com.forfun.code_lineage.analyzer;

import com.forfun.code_lineage.model.graph.MethodNode;
import com.forfun.code_lineage.model.graph.RawRelation;
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

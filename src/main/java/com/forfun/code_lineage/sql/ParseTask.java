package com.forfun.code_lineage.sql;

import com.forfun.code_lineage.model.graph.MethodNode;
import com.forfun.code_lineage.model.graph.RawRelation;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ParseTask {
    private String baseDir;
    private List<MethodNode> methods;
    private String appId;
    /** Optional raw relations from AST analysis, used to infer accurate SQL operation types. */
    @Builder.Default
    private List<RawRelation> rawRelations = List.of();
}

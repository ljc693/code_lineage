package com.forfun.codel_ineage.sql;

import com.forfun.codel_ineage.model.MethodNode;
import com.forfun.codel_ineage.model.RawRelation;
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

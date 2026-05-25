package com.forfun.code_lineage.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallsRelationship {
    @RelationshipId
    private String id;
    private String callType;
    private int lineNumber;
    private String callExpression;
    @TargetNode
    private Neo4jMethodEntity target;
}

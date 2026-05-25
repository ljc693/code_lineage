package com.forfun.code_lineage.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("Method")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Neo4jMethodEntity {
    @Id
    private String methodId;
    private String signature;
    private String returnType;
    private String paramTypes;
    private String annotations;
    private int lineNumber;
    private boolean isEntry;
    private String httpPath;
    private String httpMethod;
    private String appId;
    private String systemId;
    private String className;
    private String packageName;
    private boolean isAbstract;

    @Relationship(type = "CALLS")
    @Builder.Default
    private Set<CallsRelationship> calls = new HashSet<>();

    @Relationship(type = "ACCESSES")
    @Builder.Default
    private Set<AccessesRelationship> accesses = new HashSet<>();
}

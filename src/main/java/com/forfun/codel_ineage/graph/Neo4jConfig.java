package com.forfun.codel_ineage.graph;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@Configuration
@EnableNeo4jRepositories(basePackages = "com.forfun.codel_ineage.graph")
public class Neo4jConfig {
}

# Code Lineage Analysis Platform

Code lineage analysis platform that scans Java projects, extracts method-level call relationships, SQL database access patterns, and persists them as a graph for query, visualization, and LLM-powered analysis.

## Quick Start

```bash
# Prerequisites: Docker (MySQL + Neo4j)
docker-compose up -d mysql neo4j

# Run
./gradlew bootRun
# → http://localhost:8080

# Run tests
./gradlew test                    # unit tests (<5s)
./gradlew integrationTest         # integration tests (needs Docker)
```

## Features

- **Code Analysis**: AST (JavaParser) + ASM bytecode extraction for Java methods, call graphs, and SQL access
- **Graph Storage**: Neo4j (Method/Table nodes, CALLS/ACCESSES edges) for lineage traversal
- **Column-Level Lineage**: MySQL `column_lineage` table for field impact analysis
- **Frontend**: AntV G6 graph visualization + field impact analysis UI
- **MyBatis-Plus Support**: BaseMapper pattern detection, operation type inference (INSERT/UPDATE/DELETE/SELECT)
- **Analysis Engine**: 5 pluggable detectors (N+1 queries, god methods, circular dependencies, orphan code, layer violations)
- **LLM Reports**: Structured prompt generation for external LLM analysis (dead columns, field impact, table governance)

## API

```
# Lineage
GET  /api/v1/lineage/entry/{methodId}?depth=5&format=g6
GET  /api/v1/lineage/entry-points?appId=clawer
GET  /api/v1/lineage/topology

# Field Analysis
GET  /api/v1/analysis/field-impact?tableName=&columnName=
GET  /api/v1/analysis/field-impact-upstream?tableName=&columnName=
GET  /api/v1/analysis/table-stats
GET  /api/v1/analysis/dead-columns?tableName=&days=30

# Analysis Engine (5 detectors)
POST /api/v1/analysis/rules/run  {"appId":"clawer"}
GET  /api/v1/analysis/rules
GET  /api/v1/analysis/findings?appId=clawer

# LLM Reports
POST /api/v1/llm/report/dead-columns
POST /api/v1/llm/report/field-impact
POST /api/v1/llm/report/table-governance

# CI/CD
POST /api/v1/scans  {"appId":"myapp","repoUrl":"..."}
GET  /api/v1/scans/progress/{traceId}
GET  /api/v1/scans/history?appId=myapp
```

## Project Structure

```
src/main/java/com/forfun/codel_ineage/
  analyzer/       AST + ASM code analysis
  sql/            MyBatis/MyBatis-Plus/JPA SQL parsing
  graph/          Neo4j adapter + MySQL column lineage
  pathfinder/     BFS lineage traversal
  collector/      JDBC Agent SQL capture
  analysis/rule/  Analysis engine (strategy+template-method detectors)
  llm/            LLM report infrastructure
  controller/     REST API
  model/          Domain model
  event/          Event bus (Spring/Kafka)
  fetcher/        Git code fetcher
  service/        Lineage query, progress tracking, orchestration
```

## Tech Stack

- Java 21, Spring Boot 3.5, Gradle
- Neo4j 5.26 (Bolt), MySQL 8.0 (JdbcTemplate)
- JavaParser 3.26.3, ASM 9.7.1
- JGit 7.1.0, MyBatis 3.5.17
- AntV G6 v4 (frontend visualization)

## Docs

- `docs/specs/` — Design specifications
- `docs/code-audit-v4.md` — Code quality audit report
- `findings.md` — Project decisions and remaining todos

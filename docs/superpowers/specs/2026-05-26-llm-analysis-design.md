# Analysis System Design (Algorithm + LLM)

> **Goal:** Two independent subsystems — (C) pluggable algorithm rules for code analysis via strategy+template-method, (A) LLM-powered report generation that builds prompts from lineage data and optionally enriches them with C's findings. B (interactive query assistant) deferred.

**C is pure algorithm (Cypher → structured facts). A is the LLM layer (prompts → external LLM → natural language reports). No LLM runs inside C.**

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                       REST API Layer                             │
│                                                                  │
│  POST /api/v1/analysis/rules/run       ← trigger C               │
│  GET  /api/v1/analysis/rules           ← list C rules            │
│  POST /api/v1/llm/report/:type         ← generate A prompt       │
│  GET  /api/v1/llm/results              ← query A results         │
│  GET  /api/v1/analysis/results         ← query C findings        │
└──────────────┬──────────────────────────┬────────────────────────┘
               │                          │
    ┌──────────▼──────────┐    ┌──────────▼──────────────────────┐
    │  SUBSYSTEM C        │    │  SUBSYSTEM A                    │
    │  (pure algorithm)   │    │  (LLM integration)              │
    │                     │    │                                 │
    │  AnalysisEngine     │    │  LineageContextBuilder          │
    │  ├─ List<Rule>      │    │  PromptTemplate (built-in)      │
    │  ├─ runAll(target)  │    │  AnalysisExporter → JSON        │
    │  └─ runOne(id,tgt)  │    │                                 │
    │                     │    │  ┌─ optional enrichment ────┐   │
    │  AnalysisRule (I)   │    │  │  GET findings by table/   │   │
    │  AbstractRule (A)   │    │  │  method → include in      │   │
    │  ├─ NPlusOne        │    │  │  prompt context           │   │
    │  ├─ GodMethod       │    │  └──────────────────────────┘   │
    │  ├─ CircularDep     │    │                                 │
    │  ├─ OrphanCode      │    └────────────┬────────────────────┘
    │  └─ LayerViolation  │                 │
    │                     │    External LLM (ChatGPT / Claude API)
    │  Finding (record)   │         ↑ prompt JSON
    └──────────┬──────────┘         ↓ response text
               │              ┌──────────────────────────┐
               │              │  AnalysisResultRepository │
               └──────────────┤  (MySQL)                  │
                              │  analysis_findings (C)    │
                              │  llm_reports (A)          │
                              └──────────────────────────┘
```

**Key principle:** C detects facts. A explains them. No LLM in C.

---

## 2. Subsystem C — Algorithm Rules (Strategy + Template Method)

### 2.1 Design Pattern

```
AnalysisRule (interface)         ← strategy
  └─ analyze(Target) → List<Finding>

AbstractAnalysisRule (abstract)  ← template method
  ├─ 模板: analyze() = gatherCtx() → detect() → buildFindings()
  └─ shared: query() helper for Cypher

Concrete Rules (@Component, auto-discovered)
  ├─ NPlusOneDetector       ← loop内DB调用
  ├─ GodMethodDetector      ← 单方法访问过多表
  ├─ CircularDependencyDetector ← CALLS环
  ├─ OrphanCodeDetector     ← 无入度+非入口点
  └─ LayerViolationDetector ← Controller直接ACCESSES表
```

### 2.2 Core Interfaces

```java
package com.forfun.codel_ineage.analysis.rule;

// ── strategy ──────────────────────────────────────────────────
public interface AnalysisRule {
    String id();          // "n-plus-one"
    String name();        // "N+1 Query Detector"
    String severity();    // "HIGH" | "MEDIUM" | "LOW"
    String category();    // "performance" | "architecture" | "dead_code" | "security"
    List<Finding> analyze(Target target);

    record Target(String appId, String methodId, String tableName, String columnName) {
        public static Target app(String appId) {
            return new Target(appId, null, null, null);
        }
        public static Target method(String methodId) {
            return new Target(null, methodId, null, null);
        }
        public static Target table(String tableName) {
            return new Target(null, null, tableName, null);
        }
    }
}

// ── value object ──────────────────────────────────────────────
public record Finding(
        String ruleId,              // "n-plus-one"
        String severity,            // "HIGH"
        String category,            // "performance"
        String title,               // "N+1 query risk in Foo.bar()"
        String description,         // human-readable explanation
        String suggestion,          // fix recommendation
        Map<String, Object> evidence // Cypher result rows, metrics
) {}
```

### 2.3 Template Method Base Class

```java
public abstract class AbstractAnalysisRule implements AnalysisRule {

    protected final Driver neo4jDriver;
    protected final ColumnLineageRepository columnRepo;

    public AbstractAnalysisRule(Driver neo4jDriver, ColumnLineageRepository columnRepo) {
        this.neo4jDriver = columnRepo; // FIXME in impl: use both
    }

    // ── template method ───────────────────────────────────────
    @Override
    public final List<Finding> analyze(Target target) {
        List<Finding> results = new ArrayList<>();
        try (var session = neo4jDriver.session()) {
            Object ctx = gatherContext(session, target);
            if (ctx == null) return results;
            results.addAll(detect(session, target, ctx));
        }
        return results;
    }

    // ── hooks ─────────────────────────────────────────────────
    protected abstract Object gatherContext(Session session, Target target);
    protected abstract List<Finding> detect(Session session, Target target, Object context);

    // ── utility ───────────────────────────────────────────────
    protected List<Map<String, Object>> query(Session session,
            String cypher, Map<String, Object> params) {
        return session.run(cypher, params).list(r ->
                r.asMap(org.neo4j.driver.Values.ofObject()));
    }
}
```

### 2.4 Concrete Rule Example

```java
@Component
public class NPlusOneDetector extends AbstractAnalysisRule {

    public NPlusOneDetector(Driver d, ColumnLineageRepository r) { super(d, r); }

    @Override public String id() { return "n-plus-one"; }
    @Override public String name() { return "N+1 Query Detector"; }
    @Override public String severity() { return "HIGH"; }
    @Override public String category() { return "performance"; }

    @Override
    protected Object gatherContext(Session s, Target t) {
        return query(s, """
            MATCH (m:Method {appId: $appId})-[a:ACCESSES]->(t:Table)
            WHERE EXISTS { MATCH (m)-[:CALLS*1..3]->(m) }
            RETURN m.methodId, m.signature, m.className,
                   t.tableName, a.operation
            """, Map.of("appId", t.appId()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session s, Target t, Object ctx) {
        return ((List<Map<String, Object>>) ctx).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "N+1 risk: " + r.get("className") + "." + r.get("signature"),
                    "Method accesses " + r.get("tableName") +
                    " inside a self-calling loop. Each iteration may issue a separate query.",
                    "Replace per-iteration " + r.get("operation") +
                    " with batch operation (e.g. MyBatis-Plus selectBatchIds).",
                    Map.of("raw", r)))
                .toList();
    }
}
```

### 2.5 AnalysisEngine — Orchestrator

```java
@Component
public class AnalysisEngine {

    private final List<AnalysisRule> rules;
    private final AnalysisFindingsRepository findingsRepo;

    public AnalysisEngine(List<AnalysisRule> rules, AnalysisFindingsRepository findingsRepo) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(AnalysisRule::id))
                .toList();
        this.findingsRepo = findingsRepo;
    }

    /** Run all rules, store findings to MySQL */
    public List<Finding> runAll(AnalysisRule.Target target) {
        List<Finding> all = new ArrayList<>();
        for (var rule : rules) {
            for (Finding f : rule.analyze(target)) {
                findingsRepo.save(
                    target.appId() != null ? target.appId() : "",
                    rule.id(), rule.name(), f.severity(), f.category(),
                    f.title(), f.description(), f.suggestion(),
                    new ObjectMapper().writeValueAsString(f.evidence()));
                all.add(f);
            }
        }
        return all;
    }

    /** Run a single named rule (skip disabled) */
    public List<Finding> runOne(String ruleId, AnalysisRule.Target target) {
        return rules.stream()
                .filter(r -> r.id().equals(ruleId))
                .findFirst()
                .map(r -> r.analyze(target))
                .orElse(List.of());
    }

    public List<AnalysisRule> listRules() { return rules; }
}
```

### 2.6 Custom Rule Registration

```java
// 1. Extend AbstractAnalysisRule
@Component
public class MyDetector extends AbstractAnalysisRule {
    public MyDetector(Driver d, ColumnLineageRepository r) { super(d, r); }

    @Override public String id() { return "my-detector"; }
    @Override public String name() { return "My Custom Detector"; }
    @Override public String severity() { return "MEDIUM"; }
    @Override public String category() { return "custom"; }

    @Override protected Object gatherContext(Session s, Target t) { ... }
    @Override protected List<Finding> detect(Session s, Target t, Object ctx) { ... }
}
// 2. Spring auto-discovers @Component → injected into AnalysisEngine
// 3. Available immediately at GET /api/v1/analysis/rules
```

---

## 3. Subsystem A — LLM Report Generation

### 3.1 How LLM Is Used

```
┌────────────┐    prompt JSON     ┌──────────────┐    natural language   ┌────────────┐
│ Subsystem A │ ─────────────────>│ External LLM  │ ────────────────────>│ Subsystem A │
│ (builds     │   { template,     │ (ChatGPT /    │   "Table crawler_    │ (stores     │
│  prompt)    │     context,      │  Claude API)  │    task has 3 dead   │  response)  │
│             │     columns... }  │               │    columns..."       │             │
└────────────┘                    └──────────────┘                       └────────────┘
```

LLM 不在项目中运行。A 的职责是：构建准确的 prompt（含结构化血缘上下文）、发送给外部 LLM、存储响应。A 与 C 完全解耦，但可以在构建 prompt 时引用 C 的 findings。

### 3.2 Report Types

| Report | Data Sources | LLM Does |
|--------|-------------|----------|
| Dead Column Report | `column_lineage` access stats + column names | 结合列名语义判断风险："`backup_config` 可能是灾备字段，删除需谨慎" |
| Field Impact Report | `field-impact-upstream` + CALLS chain | output blast radius + 测试范围建议 |
| Table Governance | all of above per-table + C findings optionally | 综合评分 + 重构优先级排序 + 自然语言摘要 |
| Analysis Summary | C's `analysis_findings` table (rule results) | 把结构化 findings 翻译为可读报告 |

### 3.3 Optional C Enrichment

When building a report prompt, A can query `analysis_findings` for relevant facts:

```
POST /api/v1/llm/report/table-governance
     { "tableName": "crawler_task", "includeFindings": true }
```

This adds to the prompt context:
> "Analysis Engine detected: 2 N+1 query risks, 1 god method, 0 layer violations involving this table."

### 3.4 Prompt Template Example

```java
PromptTemplate.builtin("table_governance") → """
You are reviewing database table governance for {{table}}.

## Table Stats
- Columns: {{columns}}
- Read/Write ratio: {{readWriteRatio}}
- Accessing methods: {{methodCount}}

## Analysis Engine Findings
{{findings}}

## Questions
1. Does this table have too many responsibilities?
2. Are there columns that should be moved to separate tables?
3. What is the blast radius of modifying this table's schema?
4. Are there security concerns (e.g., sensitive columns accessed too broadly)?
"""
```

---

## 4. Data Model — Two MySQL Tables

### 4.1 `analysis_findings` (C subsystem — pure algorithm results)

```sql
CREATE TABLE IF NOT EXISTS analysis_findings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id VARCHAR(128) NOT NULL,
    rule_id VARCHAR(64) NOT NULL,          -- 'n-plus-one'
    rule_name VARCHAR(128),                 -- 'N+1 Query Detector'
    severity VARCHAR(16) NOT NULL,          -- HIGH | MEDIUM | LOW
    category VARCHAR(32) NOT NULL,          -- performance | architecture | dead_code | security
    title VARCHAR(512) NOT NULL,
    description TEXT,
    suggestion TEXT,
    evidence JSON,                          -- Cypher result rows
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_rule (app_id, rule_id),
    INDEX idx_severity (severity),
    INDEX idx_created (created_at)
);
```

### 4.2 `llm_reports` (A subsystem — LLM responses)

```sql
CREATE TABLE IF NOT EXISTS llm_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_type VARCHAR(64) NOT NULL,       -- 'dead-columns' | 'field-impact' | 'table-governance'
    app_id VARCHAR(128),
    target_table VARCHAR(128),
    target_column VARCHAR(128),
    prompt TEXT NOT NULL,
    response TEXT,                           -- LLM response
    model VARCHAR(64),                       -- 'gpt-4' | 'claude-opus-4-7'
    confidence DOUBLE DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type (report_type),
    INDEX idx_app (app_id),
    INDEX idx_created (created_at)
);
```

---

## 5. REST API

```
C subsystem (algorithm — no LLM):
  POST   /api/v1/analysis/rules/run
           body: { "appId": "clawer", "ruleIds": ["n-plus-one"] }
           → 200 { "findings": [...], "count": N }

  GET    /api/v1/analysis/rules
           → 200 { "rules": [{"id":"n-plus-one","name":"N+1 Query Detector",...}] }

  GET    /api/v1/analysis/findings
           ?appId=clawer&ruleId=n-plus-one&severity=HIGH&limit=20
           → 200 { "findings": [...] }

A subsystem (LLM):
  POST   /api/v1/llm/report/:type
           type: dead-columns | field-impact | table-governance | analysis-summary
           body: { "tableName": "crawler_task", "includeFindings": true, "days": 180 }
           → 200 { "prompt": "...", "context": {...} }

  GET    /api/v1/llm/reports
           ?type=table-governance&appId=clawer&limit=20
           → 200 { "reports": [...] }
```

---

## 6. File Map

```
New files (C subsystem — pure algorithm):
  src/main/java/com/forfun/codel_ineage/analysis/
    └── rule/
        ├── AnalysisRule.java
        ├── AbstractAnalysisRule.java
        ├── AnalysisEngine.java
        ├── Finding.java
        ├── AnalysisFindingsRepository.java   ← MySQL persistence
        ├── NPlusOneDetector.java
        ├── GodMethodDetector.java
        ├── CircularDependencyDetector.java
        ├── OrphanCodeDetector.java
        └── LayerViolationDetector.java

New files (A subsystem — LLM integration):
  src/main/java/com/forfun/codel_ineage/llm/
    ├── LineageContext.java
    ├── LineageContextBuilder.java
    ├── PromptTemplate.java
    ├── AnalysisExporter.java
    └── LlmReportRepository.java             ← MySQL persistence

Controllers:
  src/main/java/com/forfun/codel_ineage/controller/
    ├── AnalysisRuleController.java           ← /api/v1/analysis/*
    └── LlmReportController.java              ← /api/v1/llm/*
```

---

## 7. Scope & Phases

| Phase | Content | Files |
|-------|---------|-------|
| 1 | C core: AnalysisRule + AbstractAnalysisRule + Finding + AnalysisEngine + 1 detector (N+1) + findings repo + controller | ~8 new |
| 2 | C: remaining 4 detectors | ~4 new |
| 3 | A: LineageContext + PromptTemplate + Exporter + LlmReportRepository + controller | ~5 new |
| 4 | A↔C bridge (includeFindings enrichment) | 1 modify |

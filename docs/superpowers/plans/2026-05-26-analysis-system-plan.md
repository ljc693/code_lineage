# Analysis System Implementation Plan (C + A)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build C (pluggable algorithm rules via strategy+template-method) and A (LLM report generation) as independent subsystems sharing only MySQL storage.

**Architecture:** C — `analysis/rule/` package with `AnalysisRule` interface, `AbstractAnalysisRule` template base, `AnalysisEngine` orchestrator (injects `List<AnalysisRule>`), and 5 concrete detectors. A — `llm/` package with `LineageContextBuilder`, `PromptTemplate`, `AnalysisExporter`, `LlmReportRepository`. Two separate controllers. A can optionally read C's findings to enrich prompts.

**Tech Stack:** Java 21, Spring Boot 3.5, Neo4j Driver, JdbcTemplate, Mockito, AssertJ, SLF4J

---

## Phase 1: C Core (Strategy + Template Method + 1 Detector)

### Task 1: Finding value object + AnalysisFindingsRepository

**Files:**
- Create: `src/main/java/com/forfun/codel_ineage/analysis/rule/Finding.java`
- Create: `src/main/java/com/forfun/codel_ineage/analysis/rule/AnalysisFindingsRepository.java`

- [ ] **Step 1: Write failing test for Finding**

Create `src/test/java/com/forfun/codel_ineage/analysis/rule/FindingTest.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class FindingTest {

    @Test
    void createsFindingWithAllFields() {
        Finding f = new Finding("n-plus-one", "HIGH", "performance",
                "N+1 risk in Foo.bar()",
                "Method accesses table inside a self-calling loop.",
                "Replace with batch operation.",
                Map.of("tableName", "crawler_task", "operation", "SELECT"));

        assertThat(f.ruleId()).isEqualTo("n-plus-one");
        assertThat(f.severity()).isEqualTo("HIGH");
        assertThat(f.category()).isEqualTo("performance");
        assertThat(f.title()).contains("N+1");
        assertThat(f.description()).contains("self-calling");
        assertThat(f.suggestion()).contains("batch");
        assertThat(f.evidence()).containsKey("tableName");
    }
}
```

Run: `./gradlew test --tests "*FindingTest"` — FAIL.

- [ ] **Step 2: Implement Finding and AnalysisFindingsRepository**

`Finding.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import java.util.Map;

public record Finding(
        String ruleId,
        String severity,
        String category,
        String title,
        String description,
        String suggestion,
        Map<String, Object> evidence
) {}
```

`AnalysisFindingsRepository.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class AnalysisFindingsRepository {

    private final JdbcTemplate jdbc;

    public AnalysisFindingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initTable();
    }

    private void initTable() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS analysis_findings (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                app_id VARCHAR(128) NOT NULL,
                rule_id VARCHAR(64) NOT NULL,
                rule_name VARCHAR(128),
                severity VARCHAR(16) NOT NULL,
                category VARCHAR(32) NOT NULL,
                title VARCHAR(512) NOT NULL,
                description TEXT,
                suggestion TEXT,
                evidence JSON,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_app_rule (app_id, rule_id),
                INDEX idx_severity (severity),
                INDEX idx_created (created_at)
            )
            """);
    }

    public void save(String appId, String ruleId, String ruleName,
                     String severity, String category, String title,
                     String description, String suggestion, String evidence) {
        jdbc.update(
            "INSERT INTO analysis_findings (app_id, rule_id, rule_name, severity, " +
            "category, title, description, suggestion, evidence) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            appId, ruleId, ruleName, severity, category, title, description, suggestion, evidence);
    }

    public List<Map<String, Object>> findByAppId(String appId, int limit) {
        return jdbc.queryForList(
            "SELECT * FROM analysis_findings WHERE app_id = ? ORDER BY created_at DESC LIMIT ?",
            appId, limit);
    }

    public List<Map<String, Object>> findByRule(String ruleId, int limit) {
        return jdbc.queryForList(
            "SELECT * FROM analysis_findings WHERE rule_id = ? ORDER BY created_at DESC LIMIT ?",
            ruleId, limit);
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "*FindingTest"` — PASS. `./gradlew test` — PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/forfun/codel_ineage/analysis/rule/Finding.java \
        src/main/java/com/forfun/codel_ineage/analysis/rule/AnalysisFindingsRepository.java \
        src/test/java/com/forfun/codel_ineage/analysis/rule/FindingTest.java
git commit -m "feat: add Finding value object + AnalysisFindingsRepository with MySQL persistence"
```

---

### Task 2: AnalysisRule interface + AbstractAnalysisRule template base

**Files:**
- Create: `src/main/java/com/forfun/codel_ineage/analysis/rule/AnalysisRule.java`
- Create: `src/main/java/com/forfun/codel_ineage/analysis/rule/AbstractAnalysisRule.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/forfun/codel_ineage/analysis/rule/AbstractAnalysisRuleTest.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AbstractAnalysisRuleTest {

    @Test
    void templateMethodCallsHooksInOrder() {
        // Stub rule that records call order
        var recorder = new ArrayList<String>();
        var rule = new AbstractAnalysisRule(null, null) {
            @Override public String id() { return "test"; }
            @Override public String name() { return "Test Rule"; }
            @Override public String severity() { return "LOW"; }
            @Override public String category() { return "test"; }
            @Override protected Object gatherContext(Session s, Target t) {
                recorder.add("gather"); return List.of(Map.of("k", "v"));
            }
            @Override protected List<Finding> detect(Session s, Target t, Object ctx) {
                recorder.add("detect");
                @SuppressWarnings("unchecked")
                var rows = (List<Map<String, Object>>) ctx;
                return rows.stream()
                    .map(r -> new Finding(id(), severity(), category(),
                        "T", "D", "S", r))
                    .toList();
            }
        };

        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        when(driver.session()).thenReturn(session);
        // Inject driver via reflection for test
        var field = AbstractAnalysisRule.class.getDeclaredField("neo4jDriver");
        field.setAccessible(true);
        field.set(rule, driver);

        List<Finding> findings = rule.analyze(AnalysisRule.Target.app("test-app"));

        assertThat(recorder).containsExactly("gather", "detect");
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).title()).isEqualTo("T");
    }
}
```

Run: `./gradlew test --tests "*AbstractAnalysisRuleTest"` — FAIL.

- [ ] **Step 2: Implement AnalysisRule + AbstractAnalysisRule**

`AnalysisRule.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import java.util.List;

public interface AnalysisRule {
    String id();
    String name();
    String severity();
    String category();
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
```

`AbstractAnalysisRule.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import java.util.*;

public abstract class AbstractAnalysisRule implements AnalysisRule {

    protected final Driver neo4jDriver;

    protected AbstractAnalysisRule(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

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

    protected abstract Object gatherContext(Session session, Target target);
    protected abstract List<Finding> detect(Session session, Target target, Object context);

    protected List<Map<String, Object>> query(Session session,
            String cypher, Map<String, Object> params) {
        return session.run(cypher, params).list(r ->
                r.asMap(Values.ofObject()));
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "*AbstractAnalysisRuleTest"` — PASS. `./gradlew test` — PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/forfun/codel_ineage/analysis/rule/AnalysisRule.java \
        src/main/java/com/forfun/codel_ineage/analysis/rule/AbstractAnalysisRule.java \
        src/test/java/com/forfun/codel_ineage/analysis/rule/AbstractAnalysisRuleTest.java
git commit -m "feat: add AnalysisRule interface + AbstractAnalysisRule template method base"
```

---

### Task 3: NPlusOneDetector (first concrete rule)

**Files:**
- Create: `src/main/java/com/forfun/codel_ineage/analysis/rule/NPlusOneDetector.java`

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/forfun/codel_ineage/analysis/rule/NPlusOneDetectorTest.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NPlusOneDetectorTest {

    private Driver driver;
    private Session session;
    private NPlusOneDetector detector;

    @BeforeEach
    void setUp() {
        driver = mock(Driver.class);
        session = mock(Session.class);
        when(driver.session()).thenReturn(session);
        detector = new NPlusOneDetector(driver);
    }

    @Test
    void detectsNPlusOnePattern() {
        Result mockResult = mock(Result.class);
        Record record = mock(Record.class);
        when(session.run(anyString(), any(Map.class))).thenReturn(mockResult);
        when(mockResult.list(any())).thenReturn(List.of(
                Map.of("methodId", "app:com.Foo.bar()",
                       "signature", "bar()",
                       "className", "Foo",
                       "tableName", "crawler_task",
                       "operation", "SELECT")));

        List<Finding> findings = detector.analyze(AnalysisRule.Target.app("test-app"));

        assertThat(findings).hasSize(1);
        Finding f = findings.get(0);
        assertThat(f.ruleId()).isEqualTo("n-plus-one");
        assertThat(f.severity()).isEqualTo("HIGH");
        assertThat(f.category()).isEqualTo("performance");
        assertThat(f.title()).contains("N+1").contains("Foo.bar()");
        assertThat(f.description()).contains("crawler_task");
        assertThat(f.suggestion()).contains("batch");
    }

    @Test
    void emptyResultProducesNoFindings() {
        Result mockResult = mock(Result.class);
        when(session.run(anyString(), any(Map.class))).thenReturn(mockResult);
        when(mockResult.list(any())).thenReturn(List.of());

        List<Finding> findings = detector.analyze(AnalysisRule.Target.app("test-app"));
        assertThat(findings).isEmpty();
    }
}
```

Run: `./gradlew test --tests "*NPlusOneDetectorTest"` — FAIL.

- [ ] **Step 2: Implement NPlusOneDetector**

```java
package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class NPlusOneDetector extends AbstractAnalysisRule {

    public NPlusOneDetector(Driver neo4jDriver) {
        super(neo4jDriver);
    }

    @Override public String id() { return "n-plus-one"; }
    @Override public String name() { return "N+1 Query Detector"; }
    @Override public String severity() { return "HIGH"; }
    @Override public String category() { return "performance"; }

    @Override
    protected Object gatherContext(Session session, Target target) {
        return query(session, """
            MATCH (m:Method {appId: $appId})-[a:ACCESSES]->(t:Table)
            WHERE EXISTS { MATCH (m)-[:CALLS*1..3]->(m) }
            RETURN m.methodId, m.signature, m.className,
                   t.tableName, a.operation
            """, Map.of("appId", target.appId()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session session, Target target, Object context) {
        return ((List<Map<String, Object>>) context).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "N+1 risk: " + r.get("className") + "." + r.get("signature"),
                    "Method accesses " + r.get("tableName") +
                    " inside a self-calling loop. Each iteration may issue a separate query.",
                    "Replace per-iteration " + r.get("operation") +
                    " with batch operation (e.g. MyBatis-Plus selectBatchIds or collecting IDs first).",
                    Map.of("raw", r)))
                .toList();
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "*NPlusOneDetectorTest"` — PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/forfun/codel_ineage/analysis/rule/NPlusOneDetector.java \
        src/test/java/com/forfun/codel_ineage/analysis/rule/NPlusOneDetectorTest.java
git commit -m "feat: add NPlusOneDetector — detects DB calls inside self-calling loops"
```

---

### Task 4: AnalysisEngine + AnalysisRuleController

**Files:**
- Create: `src/main/java/com/forfun/codel_ineage/analysis/rule/AnalysisEngine.java`
- Create: `src/main/java/com/forfun/codel_ineage/controller/AnalysisRuleController.java`

- [ ] **Step 1: Write failing test for AnalysisEngine**

Create `src/test/java/com/forfun/codel_ineage/analysis/rule/AnalysisEngineTest.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnalysisEngineTest {

    @Test
    void runAllExecutesAllRules() {
        var repo = mock(AnalysisFindingsRepository.class);
        var rule1 = mock(AnalysisRule.class);
        when(rule1.id()).thenReturn("a");
        when(rule1.analyze(any())).thenReturn(List.of(
                new Finding("a", "LOW", "test", "T1", "D1", "S1", Map.of())));

        var rule2 = mock(AnalysisRule.class);
        when(rule2.id()).thenReturn("b");
        when(rule2.analyze(any())).thenReturn(List.of(
                new Finding("b", "HIGH", "test", "T2", "D2", "S2", Map.of())));

        var engine = new AnalysisEngine(List.of(rule1, rule2), repo);
        var target = AnalysisRule.Target.app("test-app");
        List<Finding> results = engine.runAll(target);

        assertThat(results).hasSize(2);
        verify(repo, times(2)).save(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void runOneExecutesOnlyMatchingRule() {
        var repo = mock(AnalysisFindingsRepository.class);
        var rule = mock(AnalysisRule.class);
        when(rule.id()).thenReturn("n-plus-one");
        when(rule.analyze(any())).thenReturn(List.of());

        var engine = new AnalysisEngine(List.of(rule), repo);
        engine.runOne("n-plus-one", AnalysisRule.Target.app("test-app"));
        verify(rule).analyze(any());
    }

    @Test
    void listRulesReturnsAllRegistered() {
        var repo = mock(AnalysisFindingsRepository.class);
        var rule = mock(AnalysisRule.class);
        when(rule.id()).thenReturn("test-rule");

        var engine = new AnalysisEngine(List.of(rule), repo);
        assertThat(engine.listRules()).hasSize(1);
    }
}
```

Run: `./gradlew test --tests "*AnalysisEngineTest"` — FAIL.

- [ ] **Step 2: Implement AnalysisEngine + AnalysisRuleController**

`AnalysisEngine.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AnalysisEngine {

    private final List<AnalysisRule> rules;
    private final AnalysisFindingsRepository findingsRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnalysisEngine(List<AnalysisRule> rules, AnalysisFindingsRepository findingsRepo) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(AnalysisRule::id))
                .toList();
        this.findingsRepo = findingsRepo;
    }

    public List<Finding> runAll(AnalysisRule.Target target) {
        List<Finding> all = new ArrayList<>();
        String appId = target.appId() != null ? target.appId() : "";
        for (AnalysisRule rule : rules) {
            for (Finding f : rule.analyze(target)) {
                try {
                    findingsRepo.save(appId, rule.id(), rule.name(),
                            f.severity(), f.category(), f.title(),
                            f.description(), f.suggestion(),
                            mapper.writeValueAsString(f.evidence()));
                } catch (Exception ignored) {}
                all.add(f);
            }
        }
        return all;
    }

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

`AnalysisRuleController.java`:

```java
package com.forfun.codel_ineage.controller;

import com.forfun.codel_ineage.analysis.rule.AnalysisEngine;
import com.forfun.codel_ineage.analysis.rule.AnalysisFindingsRepository;
import com.forfun.codel_ineage.analysis.rule.AnalysisRule;
import com.forfun.codel_ineage.controller.dto.LineageResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisRuleController {

    private final AnalysisEngine engine;
    private final AnalysisFindingsRepository findingsRepo;

    public AnalysisRuleController(AnalysisEngine engine, AnalysisFindingsRepository findingsRepo) {
        this.engine = engine;
        this.findingsRepo = findingsRepo;
    }

    @PostMapping("/rules/run")
    public LineageResponse runRules(@RequestBody Map<String, Object> body) {
        String appId = (String) body.getOrDefault("appId", "");
        @SuppressWarnings("unchecked")
        List<String> ruleIds = (List<String>) body.getOrDefault("ruleIds", List.of());

        var target = AnalysisRule.Target.app(appId);
        List<?> findings = ruleIds.isEmpty()
                ? engine.runAll(target)
                : engine.runOne(ruleIds.get(0), target); // single rule support for now
        return LineageResponse.builder().success(true)
                .data(Map.of("findings", findings, "count", findings.size())).build();
    }

    @GetMapping("/rules")
    public LineageResponse listRules() {
        var rules = engine.listRules().stream()
                .map(r -> Map.of("id", r.id(), "name", r.name(),
                        "severity", r.severity(), "category", r.category()))
                .toList();
        return LineageResponse.builder().success(true)
                .data(Map.of("rules", rules)).build();
    }

    @GetMapping("/findings")
    public LineageResponse getFindings(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String ruleId,
            @RequestParam(defaultValue = "20") int limit) {
        var results = ruleId != null
                ? findingsRepo.findByRule(ruleId, limit)
                : findingsRepo.findByAppId(appId != null ? appId : "%", limit);
        return LineageResponse.builder().success(true)
                .data(Map.of("findings", results)).build();
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "*AnalysisEngineTest"` — PASS. `./gradlew test` — PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/forfun/codel_ineage/analysis/rule/AnalysisEngine.java \
        src/main/java/com/forfun/codel_ineage/controller/AnalysisRuleController.java \
        src/test/java/com/forfun/codel_ineage/analysis/rule/AnalysisEngineTest.java
git commit -m "feat: add AnalysisEngine orchestrator + AnalysisRuleController REST API"
```

---

## Phase 2: Remaining 4 Detectors

### Task 5: GodMethodDetector + LayerViolationDetector

**Files:**
- Create: `src/main/java/com/forfun/codel_ineage/analysis/rule/GodMethodDetector.java`
- Create: `src/main/java/com/forfun/codel_ineage/analysis/rule/LayerViolationDetector.java`

- [ ] **Step 1: Write tests for both detectors**

Create `src/test/java/com/forfun/codel_ineage/analysis/rule/GodMethodDetectorTest.java` and `LayerViolationDetectorTest.java`. Both follow the same mock pattern as NPlusOneDetectorTest.

GodMethodDetector test: mock session returning methods with >5 ACCESSES edges → verify findings produced with severity HIGH.

LayerViolationDetector test: mock session returning Controller methods that directly ACCESS tables → verify architecture category.

Run: `./gradlew test --tests "*GodMethodDetectorTest" --tests "*LayerViolationDetectorTest"` — FAIL.

- [ ] **Step 2: Implement both detectors**

`GodMethodDetector.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class GodMethodDetector extends AbstractAnalysisRule {

    private static final int TABLE_THRESHOLD = 5;

    public GodMethodDetector(Driver neo4jDriver) { super(neo4jDriver); }

    @Override public String id() { return "god-method"; }
    @Override public String name() { return "God Method Detector"; }
    @Override public String severity() { return "MEDIUM"; }
    @Override public String category() { return "architecture"; }

    @Override
    protected Object gatherContext(Session session, Target target) {
        return query(session, """
            MATCH (m:Method {appId: $appId})-[a:ACCESSES]->(t:Table)
            WITH m, collect(DISTINCT t.tableName) AS tables, count(DISTINCT t) AS tblCount
            WHERE tblCount >= $threshold
            RETURN m.methodId, m.signature, m.className, tables, tblCount
            """, Map.of("appId", target.appId(), "threshold", TABLE_THRESHOLD));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session s, Target t, Object ctx) {
        return ((List<Map<String, Object>>) ctx).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "God method: " + r.get("className") + "." + r.get("signature"),
                    "Method accesses " + r.get("tblCount") + " tables (" + r.get("tables") +
                    "). Consider splitting into focused methods, one per aggregate.",
                    "Extract table-specific logic into separate service methods.",
                    Map.of("raw", r)))
                .toList();
    }
}
```

`LayerViolationDetector.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class LayerViolationDetector extends AbstractAnalysisRule {

    public LayerViolationDetector(Driver neo4jDriver) { super(neo4jDriver); }

    @Override public String id() { return "layer-violation"; }
    @Override public String name() { return "Layer Violation Detector"; }
    @Override public String severity() { return "MEDIUM"; }
    @Override public String category() { return "architecture"; }

    @Override
    protected Object gatherContext(Session session, Target target) {
        return query(session, """
            MATCH (m:Method {appId: $appId})-[a:ACCESSES]->(t:Table)
            WHERE m.isEntry = true AND m.className CONTAINS 'Controller'
            RETURN m.methodId, m.signature, m.className,
                   t.tableName, a.operation
            """, Map.of("appId", target.appId()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session s, Target t, Object ctx) {
        return ((List<Map<String, Object>>) ctx).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "Layer violation: " + r.get("className") + "." + r.get("signature"),
                    "Controller directly accesses table " + r.get("tableName") +
                    ". Should go through Service → Repository layers.",
                    "Move DB access to a Repository, inject it into a Service, " +
                    "and call the Service from the Controller.",
                    Map.of("raw", r)))
                .toList();
    }
}
```

- [ ] **Step 3: Run tests** — PASS. `./gradlew test` — PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/forfun/codel_ineage/analysis/rule/GodMethodDetector.java \
        src/main/java/com/forfun/codel_ineage/analysis/rule/LayerViolationDetector.java \
        src/test/java/com/forfun/codel_ineage/analysis/rule/
git commit -m "feat: add GodMethodDetector + LayerViolationDetector"
```

---

### Task 6: CircularDependencyDetector + OrphanCodeDetector

**Files:**
- Create: `src/main/java/com/forfun/codel_ineage/analysis/rule/CircularDependencyDetector.java`
- Create: `src/main/java/com/forfun/codel_ineage/analysis/rule/OrphanCodeDetector.java`

- [ ] **Step 1: Write tests** — same mock pattern as previous detectors.

CircularDependencyDetector: Cypher detects `(m)-[:CALLS*2..5]->(m)` cycles.
OrphanCodeDetector: Cypher finds methods with 0 CALLS in-degree AND isEntry=false.

- [ ] **Step 2: Implement both detectors**

`CircularDependencyDetector.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class CircularDependencyDetector extends AbstractAnalysisRule {

    public CircularDependencyDetector(Driver neo4jDriver) { super(neo4jDriver); }

    @Override public String id() { return "circular-dep"; }
    @Override public String name() { return "Circular Dependency Detector"; }
    @Override public String severity() { return "HIGH"; }
    @Override public String category() { return "architecture"; }

    @Override
    protected Object gatherContext(Session session, Target target) {
        return query(session, """
            MATCH path = (m:Method {appId: $appId})-[:CALLS*2..5]->(m)
            RETURN m.methodId, m.signature, m.className,
                   [n IN nodes(path) | n.className + '.' + n.signature] AS cycle,
                   length(path) AS depth
            LIMIT 50
            """, Map.of("appId", target.appId()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session s, Target t, Object ctx) {
        return ((List<Map<String, Object>>) ctx).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "Circular dependency involving " + r.get("className") + "." + r.get("signature"),
                    "Cycle detected (depth " + r.get("depth") + "): " + r.get("cycle"),
                    "Break the cycle by extracting shared logic into a separate class " +
                    "or using an event-driven approach.",
                    Map.of("raw", r)))
                .toList();
    }
}
```

`OrphanCodeDetector.java`:

```java
package com.forfun.codel_ineage.analysis.rule;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class OrphanCodeDetector extends AbstractAnalysisRule {

    public OrphanCodeDetector(Driver neo4jDriver) { super(neo4jDriver); }

    @Override public String id() { return "orphan-code"; }
    @Override public String name() { return "Orphan Code Detector"; }
    @Override public String severity() { return "LOW"; }
    @Override public String category() { return "dead_code"; }

    @Override
    protected Object gatherContext(Session session, Target target) {
        return query(session, """
            MATCH (m:Method {appId: $appId})
            WHERE NOT (()-[:CALLS]->(m)) AND m.isEntry = false
            RETURN m.methodId, m.signature, m.className, m.packageName
            """, Map.of("appId", target.appId()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Finding> detect(Session s, Target t, Object ctx) {
        return ((List<Map<String, Object>>) ctx).stream()
                .map(r -> new Finding(id(), severity(), category(),
                    "Orphan method: " + r.get("className") + "." + r.get("signature"),
                    "Method has no callers and is not an entry point. " +
                    "Package: " + r.get("packageName"),
                    "Verify if this method is still needed. If dead code, remove it. " +
                    "If it should be reachable, ensure it's wired into a call chain.",
                    Map.of("raw", r)))
                .toList();
    }
}
```

- [ ] **Step 3: Run tests** — PASS. `./gradlew test` — PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/forfun/codel_ineage/analysis/rule/CircularDependencyDetector.java \
        src/main/java/com/forfun/codel_ineage/analysis/rule/OrphanCodeDetector.java \
        src/test/java/com/forfun/codel_ineage/analysis/rule/
git commit -m "feat: add CircularDependencyDetector + OrphanCodeDetector"
```

---

## Phase 3: A — LLM Report Generation

### Task 7: LineageContext + PromptTemplate + AnalysisExporter + LlmReportRepository

**Files:**
- Create: `src/main/java/com/forfun/codel_ineage/llm/LineageContext.java`
- Create: `src/main/java/com/forfun/codel_ineage/llm/LineageContextBuilder.java`
- Create: `src/main/java/com/forfun/codel_ineage/llm/PromptTemplate.java`
- Create: `src/main/java/com/forfun/codel_ineage/llm/AnalysisExporter.java`
- Create: `src/main/java/com/forfun/codel_ineage/llm/LlmReportRepository.java`

- [ ] **Step 1: Write tests and implement each component**

Follow the same pattern as the LLM infrastructure plan (`docs/superpowers/plans/2026-05-26-llm-infrastructure-and-fixes.md`) Tasks 3-7, but using the updated package `llm/` instead of `llm/rule/`. The `LlmReportRepository` uses the `llm_reports` table schema from the design doc.

Test files:
- `LineageContextTest.java` — value object with `toPromptContext()` rendering
- `PromptTemplateTest.java` — variable substitution + builtin templates
- `AnalysisExporterTest.java` — JSON export
- `LlmReportRepositoryTest.java` — `@JdbcTest` integration

- [ ] **Step 2: Implement LlmReportController**

Create `src/main/java/com/forfun/codel_ineage/controller/LlmReportController.java` with endpoints from the design doc section 5.

- [ ] **Step 3: Run all tests** — PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/forfun/codel_ineage/llm/ \
        src/main/java/com/forfun/codel_ineage/controller/LlmReportController.java \
        src/test/java/com/forfun/codel_ineage/llm/
git commit -m "feat: add LLM report infrastructure — context builder, templates, exporter, controller"
```

---

## Phase 4: A↔C Bridge

### Task 8: includeFindings enrichment

**Files:**
- Modify: `src/main/java/com/forfun/codel_ineage/llm/LineageContextBuilder.java`

- [ ] **Step 1: Add enrichment logic**

When `includeFindings=true`, query `analysis_findings` table for findings related to the target table and append them to the prompt context as a "Analysis Engine Findings" section.

```java
public LineageContext buildTableGovernanceContext(String tableName, boolean includeFindings) {
    // ... existing context building ...
    
    if (includeFindings) {
        var findings = findingsRepo.findByAppId(appId, 50);
        List<String> relevant = findings.stream()
                .filter(f -> f.get("evidence") != null) // simplified — real impl checks JSON
                .map(f -> "- [" + f.get("severity") + "] " + f.get("title"))
                .limit(10)
                .toList();
        builder.findingsSummary(String.join("\n", relevant));
    }
    return builder.build();
}
```

- [ ] **Step 2: Test** — verify that `includeFindings=true` adds findings to the rendered prompt.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/forfun/codel_ineage/llm/LineageContextBuilder.java
git commit -m "feat: add includeFindings enrichment — bridge C findings into A report prompts"
```

---

## Verification

- [ ] `./gradlew test` — all unit tests PASS (<5s)
- [ ] `./gradlew integrationTest --tests "*AnalysisFindingsRepository*" --tests "*LlmReportRepository*"` — PASS
- [ ] Start app: `POST /api/v1/analysis/rules/run {"appId":"clawer"}` → returns findings
- [ ] `GET /api/v1/analysis/rules` → lists 5 detectors
- [ ] `POST /api/v1/llm/report/table-governance {"tableName":"crawler_task"}` → returns prompt JSON

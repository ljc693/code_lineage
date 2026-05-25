# Performance Optimization Design

> **Goal:** Reduce test execution from 5-8 minutes to <1 minute (CI) and <3 seconds (local TDD), plus accelerate Neo4j writes by 50-70%.

**Approach:** Layered testing (unit vs integration), conditional DataLoader, Neo4j harness for in-memory tests, batch Cypher UNWIND.

---

## 1. Test Tier Separation

### Current State (Problem)
```
./gradlew test
  ├─ 25 unit tests (3s)     ← mixed with
  └─ 6 @SpringBootTest (20-90s each) ← starts full Neo4j+MySQL+Kafka+Tomcat
Total: 5-8 min
```

### Target State
```
./gradlew test                     ./gradlew integrationTest
  ├─ 25+ unit tests (2-3s)           ├─ 6 integration tests (30-60s)
  ├─ No Spring context                ├─ @SpringBootTest
  └─ CI: every commit                 └─ CI: PR merge / release only
Total: <1 min                     Total: ~1 min
```

### Implementation

**a) Gradle config** (`build.gradle`):

```groovy
// Existing unit test task (unchanged)
tasks.named('test') {
    useJUnitPlatform()
    maxHeapSize = "4g"
    // Exclude integration tests from unit test run
    filter {
        excludeTestsMatching "*IT"
    }
}

// New integration test task
tasks.register('integrationTest', Test) {
    useJUnitPlatform()
    maxHeapSize = "4g"
    group = 'verification'
    description = 'Runs integration tests (requires Docker services)'
    shouldRunAfter test

    filter {
        includeTestsMatching "*IT"
    }
    // integration tests from the integration/ package
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
}

// Wire into check lifecycle
check.dependsOn integrationTest
```

**b) Test naming convention:**
- Unit: `*Test.java` in `src/test/java/.../`
- Integration: `*IT.java` renamed from current `*Test.java` files that use `@SpringBootTest`

**c) Files to rename (integration → *IT.java):**

| Current file | New file |
|---|---|
| `integration/FullPipelineE2ETest.java` | `integration/FullPipelineE2EIT.java` |
| `integration/ApiSmokeTest.java` | `integration/ApiSmokeIT.java` |
| `integration/CrossAppLineageTest.java` | `integration/CrossAppLineageIT.java` |
| `integration/ShenyuNeo4jLoadTest.java` | `integration/ShenyuNeo4jLoadIT.java` |
| `graph/ColumnLineageRepositoryTest.java` | `graph/ColumnLineageRepositoryIT.java` |
| `CodelIneageApplicationTests.java` | `CodelIneageApplicationIT.java` |

---

## 2. Conditional DataLoader

### Problem
DataLoader (`CommandLineRunner`) runs on every `@SpringBootTest` startup, scanning clawer project (AST+ASM+SQL+Neo4j write). This adds 30-60s to each test.

### Design
Add `@ConditionalOnProperty` to DataLoader so it only runs when explicitly enabled:

```java
@Component
@ConditionalOnProperty(name = "lineage.dataloader.enabled", havingValue = "true", matchIfMissing = true)
public class DataLoader implements CommandLineRunner {
    // ... existing code unchanged
}
```

In `application-test.properties`:
```properties
lineage.dataloader.enabled=false
```

### Files
- Modify: `DataLoader.java` — add `@ConditionalOnProperty`
- Create: `src/test/resources/application-test.properties` — test-specific config

---

## 3. Test Application Properties

### Design
All integration tests use `@ActiveProfiles("test")` to load a lightweight configuration:

**`src/test/resources/application-test.properties`:**
```properties
# Disable heavy startup components
lineage.dataloader.enabled=false
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,\
  org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration

# Use Neo4j harness (in-memory) when available
# Falls back to real Neo4j when harness not configured

# MySQL: keep real connection for ColumnLineageRepository tests
# (these test the actual MySQL column_lineage table)
```

### Files
- Create: `src/test/resources/application-test.properties`
- Modify: 6 integration test classes — add `@ActiveProfiles("test")`

---

## 4. Neo4j Harness for Integration Tests

### Problem
`@SpringBootTest` always connects to real Neo4j at `bolt://localhost:7687`. If Docker isn't running, tests hang on connection timeout.

### Design
Use `neo4j-harness` (already in `build.gradle` as `testImplementation`) to spin up an in-memory Neo4j for integration tests. The harness provides a `Neo4j` instance with configurable Bolt URI.

```java
// In integration tests that need Neo4j:
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.neo4j.uri=${test.neo4j.uri}",
    "spring.neo4j.authentication.username=neo4j",
    "spring.neo4j.authentication.password=password"
})
class Neo4jHarnessIT {

    private static Neo4j server;

    @BeforeAll
    static void startNeo4j() {
        server = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .build();
        System.setProperty("test.neo4j.uri", server.boltURI().toString());
    }

    @AfterAll
    static void stopNeo4j() {
        if (server != null) server.close();
    }
}
```

**Trade-off:** In-process Neo4j is faster (no network) but uses more memory. Acceptable for 6 integration tests.

**Decision:** Implement for `FullPipelineE2EIT` and `CrossAppLineageIT` which need Neo4j traversal. `ColumnLineageRepositoryIT` needs MySQL only — use `@JdbcTest` instead of `@SpringBootTest`.

### Files
- Modify: integration tests that need Neo4j — add harness setup
- Modify: `ColumnLineageRepositoryIT` — convert to `@JdbcTest`

---

## 5. ColumnLineageRepository Test Optimization

### Problem
`ColumnLineageRepositoryTest` uses `@SpringBootTest` but only needs `JdbcTemplate`.

### Design
Convert to `@JdbcTest` which auto-configures only JDBC-related beans:

```java
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ColumnLineageRepositoryIT {

    @Autowired
    private JdbcTemplate jdbc;

    private ColumnLineageRepository repo;

    @BeforeEach
    void setUp() {
        repo = new ColumnLineageRepository(jdbc);
    }

    // ... existing tests
}
```

This eliminates Neo4j + Tomcat + Kafka startup for this test, reducing it from 90s to ~3s.

### Files
- Modify: `graph/ColumnLineageRepositoryTest.java` → `graph/ColumnLineageRepositoryIT.java` with `@JdbcTest`

---

## 6. Runtime: Batch Cypher UNWIND

Already designed in `docs/superpowers/plans/2026-05-25-core-audit-fixes.md` Task 6.

### Impact
Reduces Neo4j CALLS edge creation from N+1 queries (21000 per scan) to 1 query with UNWIND. Projected 50-70% reduction in Phase 3 write time.

---

## 7. CI Pipeline Configuration

### Recommended split

```yaml
# Unit tests — every commit
./gradlew test

# Integration tests — PR merge or release only
./gradlew integrationTest
```

### Files
No code changes — documented for CI setup.

---

## File Change Summary

| Action | File | Purpose |
|--------|------|---------|
| Create | `src/test/resources/application-test.properties` | Lightweight test profile |
| Modify | `build.gradle` | `integrationTest` task, filter rules |
| Modify | `DataLoader.java` | `@ConditionalOnProperty` |
| Rename | 6 integration tests → `*IT.java` | Naming convention |
| Modify | 6 integration tests | Add `@ActiveProfiles("test")` |
| Modify | `ColumnLineageRepositoryIT.java` | `@JdbcTest` instead of `@SpringBootTest` |
| Modify | Neo4j-dependent ITs | Add harness setup |
| Modify | `Neo4jAdapter.java` | Batch UNWIND (audit plan Task 6) |

## Expected Performance

| Metric | Before | After |
|--------|--------|-------|
| Unit test suite | ~3s (mixed) | **2-3s** (pure unit) |
| Single integration test | 20-90s | **10-30s** |
| Full CI (unit + integration) | 5-8 min | **<2 min** |
| Local TDD cycle | 60-90s | **2-3s** |
| Scan Phase 3 (Neo4j write) | ~90s | **~30s** |

package com.forfun.codel_ineage.sql;

import com.forfun.codel_ineage.model.CallType;
import com.forfun.codel_ineage.model.graph.MethodNode;
import com.forfun.codel_ineage.model.graph.RawRelation;
import com.forfun.codel_ineage.model.SqlOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test for {@link MyBatisPlusSqlParser}.
 * Exercises the FULL pipeline: parse() -> discovers mappers -> matches methods -> infers operations.
 */
class MyBatisPlusSqlParserTest {

    private MapperDiscoveryService discoveryService;
    private OperationInferenceService operationService;
    private MyBatisPlusSqlParser parser;

    @BeforeEach
    void setUp() {
        discoveryService = new MapperDiscoveryService();
        operationService = new OperationInferenceService();
        parser = new MyBatisPlusSqlParser(discoveryService, operationService);
    }

    @Test
    void shouldProduceIdenticalOutputForFullPipeline(@TempDir Path tempDir) throws Exception {
        // ── Step 1: Create temp project structure ─────────────────────
        Path srcDir = tempDir.resolve("src/main/java/com/clawer/xxx");
        Files.createDirectories(srcDir);

        // AuthConfigMapper extends BaseMapper<AuthConfigPO>
        Files.writeString(srcDir.resolve("AuthConfigMapper.java"),
                "package com.clawer.xxx;\n" +
                "import com.baomidou.mybatisplus.core.mapper.BaseMapper;\n" +
                "public interface AuthConfigMapper extends BaseMapper<AuthConfigPO> {}\n");

        // AuthConfigPO with @TableName and fields
        Files.writeString(srcDir.resolve("AuthConfigPO.java"),
                "package com.clawer.xxx;\n" +
                "import com.baomidou.mybatisplus.annotation.TableName;\n" +
                "@TableName(\"auth_config\")\n" +
                "public class AuthConfigPO {\n" +
                "    private Long id;\n" +
                "    private String key;\n" +
                "    private String value;\n" +
                "    private String description;\n" +
                "}\n");

        // CrawlerTaskMapper extends BaseMapper<CrawlerTaskPO> (no method uses this)
        Files.writeString(srcDir.resolve("CrawlerTaskMapper.java"),
                "package com.clawer.xxx;\n" +
                "import com.baomidou.mybatisplus.core.mapper.BaseMapper;\n" +
                "public interface CrawlerTaskMapper extends BaseMapper<CrawlerTaskPO> {}\n");

        // CrawlerTaskPO with @TableName (no methods reference this mapper)
        Files.writeString(srcDir.resolve("CrawlerTaskPO.java"),
                "package com.clawer.xxx;\n" +
                "import com.baomidou.mybatisplus.annotation.TableName;\n" +
                "@TableName(\"crawler_task\")\n" +
                "public class CrawlerTaskPO {\n" +
                "    private Long id;\n" +
                "    private String taskName;\n" +
                "    private String status;\n" +
                "}\n");

        // AuthConfigRepositoryImpl injects mapper via field AND constructor
        Files.writeString(srcDir.resolve("AuthConfigRepositoryImpl.java"),
                "package com.clawer.xxx;\n" +
                "public class AuthConfigRepositoryImpl {\n" +
                "    private final AuthConfigMapper authConfigMapper;\n" +
                "    public AuthConfigRepositoryImpl(AuthConfigMapper authConfigMapper) {\n" +
                "        this.authConfigMapper = authConfigMapper;\n" +
                "    }\n" +
                "    public AuthConfigPO findByKey(String key) { return null; }\n" +
                "    public void saveConfig(AuthConfigPO config) {}\n" +
                "}\n");

        // SomeService with NO mapper fields (should NOT get relations)
        Files.writeString(srcDir.resolve("SomeService.java"),
                "package com.clawer.xxx;\n" +
                "public class SomeService {\n" +
                "    public String doSomething() { return \"hello\"; }\n" +
                "}\n");

        // ── Step 2: Prepare methods (as if from AST analysis) ────────
        MethodNode findByKey = MethodNode.builder()
                .methodId("clawer:com.clawer.xxx.AuthConfigRepositoryImpl.findByKey(String)")
                .signature("findByKey(String)")
                .className("AuthConfigRepositoryImpl")
                .packageName("com.clawer.xxx")
                .returnType("AuthConfigPO")
                .isAbstract(false)
                .build();

        MethodNode saveConfig = MethodNode.builder()
                .methodId("clawer:com.clawer.xxx.AuthConfigRepositoryImpl.saveConfig(AuthConfigPO)")
                .signature("saveConfig(AuthConfigPO)")
                .className("AuthConfigRepositoryImpl")
                .packageName("com.clawer.xxx")
                .returnType("void")
                .isAbstract(false)
                .build();

        MethodNode doSomething = MethodNode.builder()
                .methodId("clawer:com.clawer.xxx.SomeService.doSomething()")
                .signature("doSomething()")
                .className("SomeService")
                .packageName("com.clawer.xxx")
                .returnType("String")
                .isAbstract(false)
                .build();

        List<MethodNode> methods = List.of(findByKey, saveConfig, doSomething);

        // ── Step 3: Prepare raw relations (as if from AST analysis) ──
        RawRelation rel1 = RawRelation.builder()
                .caller(findByKey)
                .callee(MethodNode.builder()
                        .className("authConfigMapper")
                        .signature("selectOne")
                        .build())
                .callType(CallType.INTERNAL)
                .lineNumber(10)
                .build();

        RawRelation rel2 = RawRelation.builder()
                .caller(saveConfig)
                .callee(MethodNode.builder()
                        .className("authConfigMapper")
                        .signature("insert")
                        .build())
                .callType(CallType.INTERNAL)
                .lineNumber(15)
                .build();

        List<RawRelation> rawRelations = List.of(rel1, rel2);

        // ── Step 4: Build ParseTask ──────────────────────────────────
        ParseTask task = ParseTask.builder()
                .baseDir(tempDir.toString())
                .methods(methods)
                .appId("clawer")
                .rawRelations(rawRelations)
                .build();

        // ── Step 5: Execute the FULL pipeline ────────────────────────
        List<SqlRelations> results = parser.parse(task);

        // ── Step 6: Assertions ───────────────────────────────────────

        // 1. Correct total number of SqlRelations
        //    findByKey, saveConfig (both map to auth_config) + stub for crawler_task = 3
        assertThat(results).hasSize(3);

        // 2. Verify table names by grouping
        Map<String, List<SqlRelations>> byTable = results.stream()
                .collect(Collectors.groupingBy(SqlRelations::getTableName));
        assertThat(byTable).containsOnlyKeys("auth_config", "crawler_task");
        assertThat(byTable.get("auth_config")).hasSize(2);
        assertThat(byTable.get("crawler_task")).hasSize(1);

        // 3. Verify sourceMethodId matches expected methods
        List<String> methodIds = results.stream()
                .map(SqlRelations::getSourceMethodId)
                .toList();

        // findByKey's methodId should be present
        assertThat(methodIds)
                .anyMatch(id -> id.equals("clawer:com.clawer.xxx.AuthConfigRepositoryImpl.findByKey(String)"));
        // saveConfig's methodId should be present
        assertThat(methodIds)
                .anyMatch(id -> id.equals("clawer:com.clawer.xxx.AuthConfigRepositoryImpl.saveConfig(AuthConfigPO)"));
        // SomeService should NOT appear
        assertThat(methodIds)
                .noneMatch(id -> id.contains("SomeService"));

        // 4. Stub entry for mappers without matching methods use STUB_METHOD_PREFIX
        List<String> stubEntries = results.stream()
                .filter(r -> r.getSourceMethodId().startsWith("mapper:"))
                .map(SqlRelations::getSourceMethodId)
                .toList();
        assertThat(stubEntries).hasSize(1);
        assertThat(stubEntries.get(0)).startsWith(MyBatisPlusSqlParser.STUB_METHOD_PREFIX);
        assertThat(stubEntries.get(0)).contains("CrawlerTaskMapper");

        // 5. Operation type is inferred correctly from raw relations
        Map<String, SqlRelations> byMethodId = results.stream()
                .filter(r -> !r.getSourceMethodId().startsWith("mapper:"))
                .collect(Collectors.toMap(SqlRelations::getSourceMethodId, r -> r));

        SqlRelations findByKeyResult = byMethodId.get("clawer:com.clawer.xxx.AuthConfigRepositoryImpl.findByKey(String)");
        assertThat(findByKeyResult).isNotNull();
        assertThat(findByKeyResult.getOperation()).isEqualTo(SqlOperation.SELECT);

        SqlRelations saveConfigResult = byMethodId.get("clawer:com.clawer.xxx.AuthConfigRepositoryImpl.saveConfig(AuthConfigPO)");
        assertThat(saveConfigResult).isNotNull();
        assertThat(saveConfigResult.getOperation()).isEqualTo(SqlOperation.INSERT);

        // Stub operation is SELECT
        SqlRelations stubResult = results.stream()
                .filter(r -> r.getSourceMethodId().startsWith("mapper:"))
                .findFirst()
                .orElseThrow();
        assertThat(stubResult.getOperation()).isEqualTo(SqlOperation.SELECT);

        // 6. Column names from PO fields
        assertThat(findByKeyResult.getColumnNames()).containsExactlyInAnyOrder("key", "value", "description");
        assertThat(saveConfigResult.getColumnNames()).containsExactlyInAnyOrder("key", "value", "description");
        assertThat(stubResult.getColumnNames()).isEmpty();

        // 7. Mapper interface set correctly
        assertThat(findByKeyResult.getMapperInterface()).isEqualTo("com.clawer.xxx.AuthConfigMapper");
        assertThat(saveConfigResult.getMapperInterface()).isEqualTo("com.clawer.xxx.AuthConfigMapper");
        assertThat(stubResult.getMapperInterface()).isEqualTo("com.clawer.xxx.CrawlerTaskMapper");

        // 8. rawSql contains mybatis-plus prefix
        assertThat(findByKeyResult.getRawSql()).startsWith("mybatis-plus:");
        assertThat(saveConfigResult.getRawSql()).startsWith("mybatis-plus:");
        assertThat(stubResult.getRawSql()).startsWith("mybatis-plus:");
    }
}

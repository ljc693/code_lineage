package com.forfun.code_lineage.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapperDiscoveryServiceTest {

    private final MapperDiscoveryService service = new MapperDiscoveryService();

    /**
     * Creates a standard test project structure with mappers, POs, and a repository
     * that uses field injection and constructor injection.
     */
    private Path createStandardProject(Path tempDir) throws Exception {
        Path mainSrc = tempDir.resolve("src/main/java/com/example");

        // AuthConfigMapper
        Files.createDirectories(mainSrc.resolve("mapper"));
        Files.writeString(mainSrc.resolve("mapper/AuthConfigMapper.java"),
                "package com.example.mapper;\n" +
                        "import com.baomidou.mybatisplus.core.mapper.BaseMapper;\n" +
                        "import com.example.po.AuthConfigPO;\n" +
                        "public interface AuthConfigMapper extends BaseMapper<AuthConfigPO> {}\n");

        // ScriptConfigMapper
        Files.writeString(mainSrc.resolve("mapper/ScriptConfigMapper.java"),
                "package com.example.mapper;\n" +
                        "import com.baomidou.mybatisplus.core.mapper.BaseMapper;\n" +
                        "import com.example.po.ScriptConfigPO;\n" +
                        "public interface ScriptConfigMapper extends BaseMapper<ScriptConfigPO> {}\n");

        // AuthConfigPO
        Files.createDirectories(mainSrc.resolve("po"));
        Files.writeString(mainSrc.resolve("po/AuthConfigPO.java"),
                "package com.example.po;\n" +
                        "import com.baomidou.mybatisplus.annotation.TableName;\n" +
                        "@TableName(\"auth_config\")\n" +
                        "public class AuthConfigPO {\n" +
                        "    private String platformCode;\n" +
                        "    private String authType;\n" +
                        "    private Integer status;\n" +
                        "}\n");

        // ScriptConfigPO
        Files.writeString(mainSrc.resolve("po/ScriptConfigPO.java"),
                "package com.example.po;\n" +
                        "import com.baomidou.mybatisplus.annotation.TableName;\n" +
                        "@TableName(\"script_config\")\n" +
                        "public class ScriptConfigPO {\n" +
                        "    private String scriptCode;\n" +
                        "    private String taskName;\n" +
                        "}\n");

        // PlatformConfigRepositoryImpl — has field injection + constructor param
        Files.createDirectories(mainSrc.resolve("repo"));
        Files.writeString(mainSrc.resolve("repo/PlatformConfigRepositoryImpl.java"),
                "package com.example.repo;\n" +
                        "import com.example.mapper.AuthConfigMapper;\n" +
                        "import com.example.mapper.ScriptConfigMapper;\n" +
                        "public class PlatformConfigRepositoryImpl {\n" +
                        "    private final AuthConfigMapper mapper;\n" +
                        "    private final ScriptConfigMapper scriptConfigMapper;\n" +
                        "    public PlatformConfigRepositoryImpl(ScriptConfigMapper scriptConfigMapper) {\n" +
                        "        this.mapper = null;\n" +
                        "        this.scriptConfigMapper = scriptConfigMapper;\n" +
                        "    }\n" +
                        "}\n");

        // AppService — no mapper fields
        Files.createDirectories(mainSrc.resolve("service"));
        Files.writeString(mainSrc.resolve("service/AppService.java"),
                "package com.example.service;\n" +
                        "public class AppService {\n" +
                        "    private final String name;\n" +
                        "    public AppService(String name) {\n" +
                        "        this.name = name;\n" +
                        "    }\n" +
                        "}\n");

        // src/test/java/com/example/ — should be SKIPPED
        Path testSrc = tempDir.resolve("src/test/java/com/example");
        Files.createDirectories(testSrc);
        Files.writeString(testSrc.resolve("TestMapper.java"),
                "package com.example;\n" +
                        "import com.baomidou.mybatisplus.core.mapper.BaseMapper;\n" +
                        "import com.example.po.TestPO;\n" +
                        "public interface TestMapper extends BaseMapper<TestPO> {}\n");

        return tempDir;
    }

    @Test
    void scan_discoversMappersToTables(@TempDir Path tempDir) throws Exception {
        ProjectModel model = service.scan(createStandardProject(tempDir));

        assertThat(model.mapperToTable())
                .containsEntry("com.example.mapper.AuthConfigMapper", "auth_config")
                .containsEntry("com.example.mapper.ScriptConfigMapper", "script_config");
    }

    @Test
    void scan_discoversPOColumns(@TempDir Path tempDir) throws Exception {
        ProjectModel model = service.scan(createStandardProject(tempDir));

        assertThat(model.poColumns())
                .containsEntry("AuthConfigPO", List.of("platformCode", "authType", "status"))
                .containsEntry("ScriptConfigPO", List.of("scriptCode", "taskName"));
    }

    @Test
    void scan_discoversFieldInjections(@TempDir Path tempDir) throws Exception {
        ProjectModel model = service.scan(createStandardProject(tempDir));

        assertThat(model.classToMappers().get("com.example.repo.PlatformConfigRepositoryImpl"))
                .contains("com.example.mapper.AuthConfigMapper");
    }

    @Test
    void scan_discoversConstructorParams(@TempDir Path tempDir) throws Exception {
        ProjectModel model = service.scan(createStandardProject(tempDir));

        assertThat(model.classToMappers().get("com.example.repo.PlatformConfigRepositoryImpl"))
                .contains("com.example.mapper.ScriptConfigMapper");
    }

    @Test
    void scan_discoversVarToTypeMappings(@TempDir Path tempDir) throws Exception {
        ProjectModel model = service.scan(createStandardProject(tempDir));

        assertThat(model.varToMappers().get("mapper"))
                .contains("com.example.mapper.AuthConfigMapper");
    }

    @Test
    void scan_skipsTestDirectories(@TempDir Path tempDir) throws Exception {
        ProjectModel model = service.scan(createStandardProject(tempDir));

        assertThat(model.mapperToTable()).doesNotContainKey("com.example.TestMapper");
        assertThat(model.mapperToPo()).doesNotContainKey("com.example.TestMapper");
    }

    @Test
    void scan_returnsEmptyModelForNonExistentDirectory() {
        ProjectModel model = service.scan(Paths.get("/nonexistent/path/xyzzy_" + System.nanoTime()));

        assertThat(model.isEmpty()).isTrue();
    }

    @Test
    void scan_returnsEmptyModelWhenNoMappersFound(@TempDir Path tempDir) throws Exception {
        Path noMapperDir = tempDir.resolve("project");
        Files.createDirectories(noMapperDir.resolve("src/main/java/com/example"));
        Files.writeString(noMapperDir.resolve("src/main/java/com/example/Hello.java"),
                "package com.example;\n" +
                        "public class Hello {\n" +
                        "    private final String greeting;\n" +
                        "    public Hello(String greeting) { this.greeting = greeting; }\n" +
                        "    public String greet() { return greeting; }\n" +
                        "}\n");

        ProjectModel model = service.scan(noMapperDir);
        assertThat(model.isEmpty()).isTrue();
    }
}

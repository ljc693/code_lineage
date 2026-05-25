package com.forfun.code_lineage.sql;

import com.forfun.code_lineage.model.SqlOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractSqlParserTest {

    /**
     * Concrete test subclass of AbstractSqlParser.
     * Scans .txt files and parses lines in format: methodId|tableName
     */
    static class TestSqlParser extends AbstractSqlParser {
        @Override
        protected String fileSuffix() { return ".txt"; }

        @Override
        protected String pathFilter() { return ""; }

        @Override
        protected List<SqlRelations> parseFile(Path path, String content, ParseTask task) {
            List<SqlRelations> results = new ArrayList<>();
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 2) {
                    results.add(SqlRelations.builder()
                            .sourceMethodId(parts[0].trim())
                            .tableName(parts[1].trim())
                            .columnNames(List.of())
                            .operation(SqlOperation.SELECT)
                            .rawSql("test:" + parts[0])
                            .build());
                }
            }
            return results;
        }
    }

    @Test
    void parse_shouldFindRelationsFromTxtFiles(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "method1|users\nmethod2|orders\n");

        Path otherFile = tempDir.resolve("test.xml");
        Files.writeString(otherFile, "<xml/>");

        TestSqlParser parser = new TestSqlParser();
        ParseTask task = ParseTask.builder()
                .baseDir(tempDir.toString())
                .methods(List.of())
                .appId("test")
                .build();

        List<SqlRelations> results = parser.parse(task);

        assertThat(results).hasSize(2);
        assertThat(results).anyMatch(r ->
                r.getSourceMethodId().equals("method1") && r.getTableName().equals("users"));
        assertThat(results).anyMatch(r ->
                r.getSourceMethodId().equals("method2") && r.getTableName().equals("orders"));
    }

    @Test
    void parse_shouldReturnEmpty_whenBaseDirDoesNotExist() {
        TestSqlParser parser = new TestSqlParser();
        ParseTask task = ParseTask.builder()
                .baseDir("/nonexistent/path/xyzzy")
                .methods(List.of())
                .appId("test")
                .build();

        List<SqlRelations> results = parser.parse(task);
        assertThat(results).isEmpty();
    }

    @Test
    void operationFromName_shouldMapCorrectly() {
        assertThat(AbstractSqlParser.operationFromName("deleteUser")).isEqualTo(SqlOperation.DELETE);
        assertThat(AbstractSqlParser.operationFromName("removeUser")).isEqualTo(SqlOperation.DELETE);
        assertThat(AbstractSqlParser.operationFromName("updateUser")).isEqualTo(SqlOperation.UPDATE);
        assertThat(AbstractSqlParser.operationFromName("modifyUser")).isEqualTo(SqlOperation.UPDATE);
        assertThat(AbstractSqlParser.operationFromName("insertUser")).isEqualTo(SqlOperation.INSERT);
        assertThat(AbstractSqlParser.operationFromName("saveUser")).isEqualTo(SqlOperation.INSERT);
        assertThat(AbstractSqlParser.operationFromName("createUser")).isEqualTo(SqlOperation.INSERT);
        assertThat(AbstractSqlParser.operationFromName("addUser")).isEqualTo(SqlOperation.INSERT);
        assertThat(AbstractSqlParser.operationFromName("findUser")).isEqualTo(SqlOperation.SELECT);
        assertThat(AbstractSqlParser.operationFromName("byName")).isEqualTo(SqlOperation.SELECT);
        assertThat(AbstractSqlParser.operationFromName(null)).isEqualTo(SqlOperation.SELECT);
    }

    @Test
    void deduplicate_shouldRemoveDuplicateMethodIdAndTableName() {
        TestSqlParser parser = new TestSqlParser();

        List<SqlRelations> raw = Arrays.asList(
                SqlRelations.builder().sourceMethodId("m1").tableName("users").build(),
                SqlRelations.builder().sourceMethodId("m1").tableName("users").build(),
                SqlRelations.builder().sourceMethodId("m1").tableName("orders").build(),
                SqlRelations.builder().sourceMethodId("m2").tableName("users").build(),
                SqlRelations.builder().sourceMethodId("m2").tableName("users").build()
        );

        List<SqlRelations> deduped = parser.deduplicate(raw);

        assertThat(deduped).hasSize(3);
        assertThat(deduped.get(0).getSourceMethodId()).isEqualTo("m1");
        assertThat(deduped.get(0).getTableName()).isEqualTo("users");
        assertThat(deduped.get(1).getSourceMethodId()).isEqualTo("m1");
        assertThat(deduped.get(1).getTableName()).isEqualTo("orders");
        assertThat(deduped.get(2).getSourceMethodId()).isEqualTo("m2");
        assertThat(deduped.get(2).getTableName()).isEqualTo("users");
    }

    @Test
    void extractPackage_shouldExtractPackageName() {
        String content = "package com.example.myapp;\n\npublic class Test {}";
        assertThat(AbstractSqlParser.extractPackage(content)).isEqualTo("com.example.myapp");
    }

    @Test
    void extractPackage_shouldReturnNull_whenNoPackage() {
        String content = "public class Test {}";
        assertThat(AbstractSqlParser.extractPackage(content)).isNull();
    }

    @Test
    void walkProjectFiles_shouldSkipTestDirectories(@TempDir Path tempDir) throws Exception {
        Path mainDir = tempDir.resolve("src/main");
        Path testDir = tempDir.resolve("src/test");
        Files.createDirectories(mainDir);
        Files.createDirectories(testDir);

        Path mainFile = mainDir.resolve("test.txt");
        Path testFile = testDir.resolve("test.txt");
        Files.writeString(mainFile, "main|main_table");
        Files.writeString(testFile, "test|test_table");

        TestSqlParser parser = new TestSqlParser();
        List<String> visited = new ArrayList<>();
        parser.walkProjectFiles(tempDir, (path, content) -> visited.add(path.toString()));

        assertThat(visited).hasSize(1);
        assertThat(visited.get(0)).contains("main");
        assertThat(visited.get(0)).doesNotContain("/test/");
    }
}

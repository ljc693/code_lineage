package com.forfun.codel_ineage.sql;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.assertj.core.api.Assertions.assertThat;

class MyBatisSqlParserTest {

    @Test
    void shouldParseShenyuMappers() {
        Path root = Path.of("../shenyu/shenyu-master").toAbsolutePath();
        MyBatisSqlParser parser = new MyBatisSqlParser();
        ParseTask task = ParseTask.builder()
                .baseDir(root.toString()).methods(List.of()).appId("shenyu").build();
        List<SqlRelations> results = parser.parse(task);

        System.out.println("ShenYu MyBatis tables: " + results.size());

        Map<String, Long> tableCounts = results.stream()
                .collect(Collectors.groupingBy(SqlRelations::getTableName, Collectors.counting()));
        tableCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .forEach(e -> System.out.printf("  %-30s %d refs\n", e.getKey(), e.getValue()));

        assertThat(results).isNotEmpty(); // ShenYu has MyBatis mappers
    }
}

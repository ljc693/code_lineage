package com.forfun.code_lineage.controller;

import com.forfun.code_lineage.controller.dto.LineageResponse;
import com.forfun.code_lineage.graph.Neo4jMethodRepository;
import org.neo4j.driver.Driver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final Driver neo4jDriver;
    private final Neo4jMethodRepository methodRepo;

    public DashboardController(Driver neo4jDriver, Neo4jMethodRepository methodRepo) {
        this.neo4jDriver = neo4jDriver;
        this.methodRepo = methodRepo;
    }

    @GetMapping("/summary")
    public LineageResponse summary() {
        Map<String, Object> data = new LinkedHashMap<>();

        // DB stats
        try (var session = neo4jDriver.session()) {
            var r = session.run(
                "MATCH (m:Method) " +
                "WITH m.appId AS app, count(m) AS methods " +
                "RETURN app, methods ORDER BY methods DESC");
            List<Map<String, Object>> apps = new ArrayList<>();
            while (r.hasNext()) {
                var rec = r.next();
                apps.add(Map.of("appId", rec.get("app").asString(),
                        "methods", rec.get("methods").asInt()));
            }
            data.put("apps", apps);

            var t = session.run("MATCH (t:Table) RETURN count(t) AS cnt");
            data.put("tables", t.hasNext() ? t.next().get("cnt").asInt() : 0);

            var e = session.run("MATCH ()-[c:CALLS]->() RETURN count(c) AS cnt");
            data.put("callsEdges", e.hasNext() ? e.next().get("cnt").asInt() : 0);

            var a = session.run("MATCH ()-[a:ACCESSES]->() RETURN count(a) AS cnt");
            data.put("accessesEdges", a.hasNext() ? a.next().get("cnt").asInt() : 0);
        }

        return LineageResponse.builder().success(true).data(data).build();
    }

    @GetMapping("/discover")
    public LineageResponse discoverProjects() {
        List<Map<String, Object>> projects = new ArrayList<>();
        Path workspace = Path.of("..").toAbsolutePath().normalize();

        try (var stream = Files.list(workspace)) {
            stream.filter(Files::isDirectory)
                .filter(p -> !p.getFileName().toString().startsWith("."))
                .forEach(dir -> {
                    boolean hasJava = Files.exists(dir.resolve("pom.xml"))
                            || Files.exists(dir.resolve("build.gradle"))
                            || Files.exists(dir.resolve("build.gradle.kts"))
                            || hasJavaFiles(dir);
                    if (hasJava) {
                        long javaCount = countJavaFiles(dir);
                        projects.add(Map.of(
                            "name", dir.getFileName().toString(),
                            "path", dir.toString(),
                            "javaFiles", javaCount,
                            "maven", Files.exists(dir.resolve("pom.xml")),
                            "gradle", Files.exists(dir.resolve("build.gradle"))
                                    || Files.exists(dir.resolve("build.gradle.kts"))
                        ));
                    }
                });
        } catch (Exception ignored) {}

        return LineageResponse.builder()
                .success(true)
                .data(Map.of("projects", projects))
                .build();
    }

    private boolean hasJavaFiles(Path dir) {
        try (var s = Files.walk(dir, 2)) {
            return s.anyMatch(p -> p.toString().endsWith(".java"));
        } catch (Exception e) { return false; }
    }

    private long countJavaFiles(Path dir) {
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".java")
                    && p.toString().contains("/src/main/java/")).count();
        } catch (Exception e) { return 0; }
    }
}

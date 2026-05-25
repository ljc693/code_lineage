package com.forfun.codel_ineage.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Single-pass scanner that discovers MyBatis-Plus mappers, PO classes,
 * field injections, and variable-to-type mappings from a Java project.
 */
@Component
public class MapperDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(MapperDiscoveryService.class);

    private static final Pattern MAPPER_EXTENDS = Pattern.compile(
            "interface\\s+(\\w+)\\s+extends\\s+BaseMapper<\\s*(\\w+)\\s*>");
    private static final Pattern TABLE_NAME = Pattern.compile(
            "@TableName\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");
    private static final Pattern MAPPER_FIELD = Pattern.compile(
            "(?:private|protected|public)\\s+(?:final\\s+)?(\\w+Mapper)\\s+(\\w+)");
    private static final Pattern CONSTRUCTOR_PARAM = Pattern.compile(
            "(\\w+Mapper)\\s+\\w+\\s*[,\\)]");
    private static final Pattern CLASS_DECL = Pattern.compile(
            "(?:class|interface|record)\\s+(\\w+)");
    private static final Pattern PO_FIELD = Pattern.compile(
            "private\\s+\\S+\\s+(\\w+)\\s*;");
    private static final Pattern PKG = Pattern.compile(
            "package\\s+([\\w.]+)\\s*;");

    private static final Set<String> SKIP_FIELDS = Set.of("serialVersionUID", "id");

    public ProjectModel scan(Path baseDir) {
        ProjectModel model = new ProjectModel();
        if (!Files.exists(baseDir)) return model;

        // Pass 1: collect mapper → PO and PO class names
        Map<String, String> mapperToPoPass1 = new LinkedHashMap<>();
        Set<String> poNames = new LinkedHashSet<>();

        try (var walk = Files.walk(baseDir)) {
            List<Path> javaFiles = walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/test/"))
                    .toList();

            for (Path f : javaFiles) {
                String content = readFile(f);
                if (content == null) continue;
                String pkg = extractPackage(content);

                Matcher mm = MAPPER_EXTENDS.matcher(content);
                if (mm.find()) {
                    String mapperClass = mm.group(1);
                    String poClass = mm.group(2);
                    String mapperFqcn = (pkg != null ? pkg + "." : "") + mapperClass;
                    mapperToPoPass1.put(mapperFqcn, poClass);
                    model.putMapperPo(mapperFqcn, poClass);
                    poNames.add(poClass);
                }
            }

            if (mapperToPoPass1.isEmpty()) return model;

            // Pass 2a: process PO files — populate mapperToTable and poColumns
            // (must be before injection detection so mapperToTable is available)
            for (Path f : javaFiles) {
                String content = readFile(f);
                if (content == null) continue;

                Matcher cm = CLASS_DECL.matcher(content);
                if (!cm.find()) continue;
                String className = cm.group(1);

                if (poNames.contains(className)) {
                    Matcher tm = TABLE_NAME.matcher(content);
                    if (tm.find()) {
                        String tableName = tm.group(1);
                        for (var entry : mapperToPoPass1.entrySet()) {
                            if (entry.getValue().equals(className)) {
                                model.putMapperTable(entry.getKey(), tableName);
                            }
                        }
                    }
                    // Extract column names from fields
                    List<String> fields = new ArrayList<>();
                    Matcher fm = PO_FIELD.matcher(content);
                    while (fm.find()) {
                        String fieldName = fm.group(1);
                        if (!SKIP_FIELDS.contains(fieldName)) {
                            fields.add(fieldName);
                        }
                    }
                    if (!fields.isEmpty()) {
                        model.putPoColumns(className, fields);
                    }
                }
            }

            // Pass 2b: field injections, constructor params, var → type
            // (model.mapperToTable() is guaranteed to be fully populated now)
            for (Path f : javaFiles) {
                String content = readFile(f);
                if (content == null) continue;
                String pkg = extractPackage(content);

                Matcher cm = CLASS_DECL.matcher(content);
                if (!cm.find()) continue;
                String className = cm.group(1);
                String classFqcn = (pkg != null ? pkg + "." : "") + className;

                // Skip PO files — already handled above
                if (poNames.contains(className)) continue;

                // Field injection detection
                Set<String> foundMappers = new LinkedHashSet<>();
                Matcher fm2 = MAPPER_FIELD.matcher(content);
                while (fm2.find()) {
                    String mapperType = fm2.group(1);
                    String varName = fm2.group(2);
                    for (String mapperFqcn : model.mapperToTable().keySet()) {
                        if (mapperFqcn.endsWith("." + mapperType) || mapperFqcn.equals(mapperType)) {
                            foundMappers.add(mapperFqcn);
                            model.addVarMapper(varName, mapperFqcn);
                        }
                    }
                }
                // Constructor parameter detection
                Matcher pm = CONSTRUCTOR_PARAM.matcher(content);
                while (pm.find()) {
                    String paramType = pm.group(1);
                    for (String mapperFqcn : model.mapperToTable().keySet()) {
                        if (mapperFqcn.endsWith("." + paramType) || mapperFqcn.equals(paramType)) {
                            foundMappers.add(mapperFqcn);
                        }
                    }
                }
                for (String mfq : foundMappers) {
                    model.addClassMapper(classFqcn, mfq);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to walk project directory: {}", baseDir, e);
        }
        return model;
    }

    private String readFile(Path f) {
        try {
            return Files.readString(f);
        } catch (Exception e) {
            log.debug("Failed to read {}", f, e);
            return null;
        }
    }

    private String extractPackage(String content) {
        Matcher m = PKG.matcher(content);
        return m.find() ? m.group(1) : null;
    }
}

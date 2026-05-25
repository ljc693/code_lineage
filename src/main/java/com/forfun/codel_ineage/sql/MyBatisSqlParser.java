package com.forfun.codel_ineage.sql;

import com.forfun.codel_ineage.model.graph.MethodNode;
import com.forfun.codel_ineage.model.SqlOperation;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

@Component
public class MyBatisSqlParser implements SqlParser {

    private static final Pattern TABLE_PATTERN =
            Pattern.compile("(?:FROM|JOIN|INTO|UPDATE|INSERT\\s+INTO)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN_PATTERN =
            Pattern.compile("(?:SELECT|SET)\\s+(.+?)\\s+(?:FROM|WHERE|SET|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public List<SqlRelations> parse(ParseTask task) {
        List<SqlRelations> results = new ArrayList<>();
        Path baseDir = Paths.get(task.getBaseDir());

        try {
            List<Path> xmlFiles = findMapperXmls(baseDir);

            for (Path xmlFile : xmlFiles) {
                Document doc = parseXml(xmlFile);
                String namespace = doc.getDocumentElement().getAttribute("namespace");
                NodeList statements = doc.getDocumentElement().getChildNodes();

                for (int i = 0; i < statements.getLength(); i++) {
                    Node node = statements.item(i);
                    if (node.getNodeType() != Node.ELEMENT_NODE) continue;

                    String tagName = node.getNodeName();
                    String statementId = ((Element) node).getAttribute("id");
                    String sql = node.getTextContent().trim();

                    SqlOperation op = detectOperation(tagName);
                    Set<String> tables = extractTables(sql);
                    List<String> columns = extractColumns(sql);

                    String mapperMethod = namespace + "." + statementId;
                    for (String table : tables) {
                        results.add(SqlRelations.builder()
                                .sourceMethodId(mapperMethod)
                                .tableName(table)
                                .columnNames(columns)
                                .operation(op)
                                .rawSql(sql)
                                .mapperInterface(namespace)
                                .mapperMethod(statementId)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            // XML parsing failure is non-fatal
        }
        return results;
    }

    private SqlOperation detectOperation(String tagName) {
        return switch (tagName.toLowerCase()) {
            case "select" -> SqlOperation.SELECT;
            case "insert" -> SqlOperation.INSERT;
            case "update" -> SqlOperation.UPDATE;
            case "delete" -> SqlOperation.DELETE;
            default -> SqlOperation.SELECT;
        };
    }

    private Set<String> extractTables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = TABLE_PATTERN.matcher(sql);
        while (m.find()) tables.add(m.group(1));
        return tables;
    }

    private List<String> extractColumns(String sql) {
        List<String> columns = new ArrayList<>();
        Matcher m = COLUMN_PATTERN.matcher(sql);
        if (m.find()) {
            for (String col : m.group(1).split(",")) {
                columns.add(col.trim().split("\\s+")[0]);
            }
        }
        return columns;
    }

    private List<Path> findMapperXmls(Path baseDir) throws Exception {
        List<Path> xmls = new ArrayList<>();
        if (!Files.exists(baseDir)) return xmls;
        try (var walk = Files.walk(baseDir)) {
            walk.filter(p -> p.toString().endsWith(".xml")
                            && p.toString().contains("mapper"))
                    .forEach(xmls::add);
        }
        return xmls;
    }

    private Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        } catch (Exception ignored) {}
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {}
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(file.toFile());
    }
}

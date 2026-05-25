package com.forfun.codel_ineage.analysis.report;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PromptTemplate(String name, String template) {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public String render(Map<String, String> variables) {
        Set<String> missing = new java.util.LinkedHashSet<>();
        String result = VARIABLE_PATTERN.matcher(template).replaceAll(mr -> {
            String varName = mr.group(1);
            String value = variables.get(varName);
            if (value == null) {
                missing.add(varName);
                return mr.group();
            }
            return Matcher.quoteReplacement(value);
        });
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing template variables: " + String.join(", ", missing));
        }
        return result;
    }

    public static PromptTemplate builtin(String name) {
        return switch (name) {
            case "dead_column_analysis" -> new PromptTemplate("dead_column_analysis", """
You are reviewing database columns for dead columns analysis.

## Target Table
- Table: {{table}}
- Columns: {{columns}}

## Analysis
Review each column and determine if it is dead (unused), including:
1. Columns that have no recent access in column_lineage
2. Columns whose names suggest deprecated functionality (e.g., "backup_", "_old", "_v1")
3. Columns that are only written but never read

## Analysis Engine Findings
{{findings}}

## Questions
1. Which columns are candidates for removal?
2. What is the risk level for removing each column?
3. Are there any columns that should be kept for compliance or historical reasons?
""");
            case "field_impact_analysis" -> new PromptTemplate("field_impact_analysis", """
You are reviewing field impact analysis for database schema changes.

## Target Table
- Table: {{table}}
- Columns: {{columns}}
- Impacted Methods: {{methods}}
- Upstream Endpoints: {{endpoints}}

## Analysis
Determine the blast radius of modifying or removing each field:
1. How many methods access this field?
2. What HTTP endpoints would be affected?
3. What is the recommended test coverage for safe changes?

## Questions
1. What is the blast radius for each field?
2. Which fields have the highest risk of breaking changes?
3. What test scenarios should be run before modifying each field?

## Analysis Engine Findings
{{findings}}
""");
            case "table_governance" -> new PromptTemplate("table_governance", """
You are reviewing database table governance for {{table}}.

## Table Stats
- Columns: {{columns}}
- Impacted Methods: {{methods}}
- Upstream Endpoints: {{endpoints}}
- Call Chain Example: {{call_chain}}

## Questions
1. Does this table have too many responsibilities?
2. Are there columns that should be moved to separate tables?
3. What is the blast radius of modifying this table's schema?
4. Are there security concerns (e.g., sensitive columns accessed too broadly)?

## Analysis Engine Findings
{{findings}}
""");
            default -> throw new IllegalArgumentException(
                    "Unknown builtin template: " + name + ". Available: dead_column_analysis, field_impact_analysis, table_governance");
        };
    }
}

package com.forfun.codel_ineage.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateTest {

    @Test
    void renderSubstitutesVariables() {
        var template = new PromptTemplate("test", "Hello {{name}}, your role is {{role}}.");
        String result = template.render(Map.of("name", "Alice", "role", "admin"));

        assertThat(result).isEqualTo("Hello Alice, your role is admin.");
    }

    @Test
    void renderThrowsOnMissingVariable() {
        var template = new PromptTemplate("test", "Hello {{name}}.");

        assertThatThrownBy(() -> template.render(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void renderThrowsOnMissingVariableInMultiple() {
        var template = new PromptTemplate("test", "{{a}} and {{b}}");

        assertThatThrownBy(() -> template.render(Map.of("a", "1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("b");
    }

    @Test
    void renderHandlesNoVariables() {
        var template = new PromptTemplate("static", "No variables here.");
        String result = template.render(Map.of());

        assertThat(result).isEqualTo("No variables here.");
    }

    @Test
    void builtinReturnsDeadColumnAnalysis() {
        PromptTemplate template = PromptTemplate.builtin("dead_column_analysis");
        assertThat(template.name()).isEqualTo("dead_column_analysis");
        assertThat(template.template()).contains("dead columns");
        assertThat(template.template()).contains("{{table}}");
    }

    @Test
    void builtinReturnsFieldImpactAnalysis() {
        PromptTemplate template = PromptTemplate.builtin("field_impact_analysis");
        assertThat(template.name()).isEqualTo("field_impact_analysis");
        assertThat(template.template()).contains("field impact");
        assertThat(template.template()).contains("{{table}}");
    }

    @Test
    void builtinReturnsTableGovernance() {
        PromptTemplate template = PromptTemplate.builtin("table_governance");
        assertThat(template.name()).isEqualTo("table_governance");
        assertThat(template.template()).contains("governance");
        assertThat(template.template()).contains("{{table}}");
    }

    @Test
    void builtinThrowsOnUnknownName() {
        assertThatThrownBy(() -> PromptTemplate.builtin("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void accessorsWork() {
        var template = new PromptTemplate("my-template", "Content {{x}}.");
        assertThat(template.name()).isEqualTo("my-template");
        assertThat(template.template()).isEqualTo("Content {{x}}.");
    }

    @Test
    void renderSubstitutesMultipleOccurrences() {
        var template = new PromptTemplate("multi", "{{x}} and {{x}} again.");
        String result = template.render(Map.of("x", "same"));

        assertThat(result).isEqualTo("same and same again.");
    }
}

package com.forfun.code_lineage.analysis.rule;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class FindingTest {

    @Test
    void createsFindingWithAllFields() {
        Finding f = new Finding("n-plus-one", "HIGH", "performance",
                "N+1 risk in Foo.bar()",
                "Method accesses table inside a self-calling loop.",
                "Replace with batch operation.",
                Map.of("tableName", "crawler_task", "operation", "SELECT"));

        assertThat(f.ruleId()).isEqualTo("n-plus-one");
        assertThat(f.severity()).isEqualTo("HIGH");
        assertThat(f.category()).isEqualTo("performance");
        assertThat(f.title()).contains("N+1");
        assertThat(f.description()).contains("self-calling");
        assertThat(f.suggestion()).contains("batch");
        assertThat(f.evidence()).containsKey("tableName");
    }
}

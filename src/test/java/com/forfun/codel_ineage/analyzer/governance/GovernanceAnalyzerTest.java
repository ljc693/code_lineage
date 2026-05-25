package com.forfun.codel_ineage.analyzer.governance;

import com.forfun.codel_ineage.fetcher.FetchedCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class GovernanceAnalyzerTest {

    @Test
    void shouldCalculateMetrics(@TempDir Path projectDir) throws Exception {
        Files.writeString(projectDir.resolve("UserService.java"), """
            package com.example;
            // This is a comment line
            public class UserService {
                /* Block comment */
                public int calculate(int x) {
                    if (x > 0) {       // +1 complexity
                        return x * 2;
                    } else if (x < 0) { // +1 complexity
                        return -x;
                    }
                    for (int i = 0; i < 10; i++) { // +1 complexity
                        // do something
                    }
                    return 0;
                }
                public int calculate(int x) { // duplicate signature
                    if (x > 0) return x * 2;
                    return 0;
                }
            }
            """);

        GovernanceAnalyzer analyzer = new GovernanceAnalyzer();
        GovernanceMetrics metrics = analyzer.analyze(FetchedCode.builder()
                .baseDir(projectDir.toString())
                .appId("test-app")
                .changedFiles(List.of())
                .build());

        assertThat(metrics.getAppId()).isEqualTo("test-app");
        assertThat(metrics.getClassCount()).isEqualTo(1);
        assertThat(metrics.getMethodCount()).isEqualTo(2);
        assertThat(metrics.getCyclomaticComplexity()).isGreaterThanOrEqualTo(3);
        assertThat(metrics.getDuplicationRate()).isGreaterThan(0); // duplicate signature
        assertThat(metrics.getCommentCoverage()).isGreaterThan(0);  // has comments
    }
}

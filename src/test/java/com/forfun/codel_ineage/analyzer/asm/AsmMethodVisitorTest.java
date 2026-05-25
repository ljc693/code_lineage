package com.forfun.codel_ineage.analyzer.asm;

import com.forfun.codel_ineage.model.graph.MethodNode;
import com.forfun.codel_ineage.model.graph.RawRelation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

class AsmMethodVisitorTest {

    @Test
    void shouldParseBytecodeAndExtractMethod(@TempDir Path tempDir) throws Exception {
        String code = """
            package com.test;
            public class Calculator {
                public int add(int a, int b) {
                    return helper(a) + b;
                }
                private int helper(int x) {
                    return x * 2;
                }
            }
            """;

        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Calculator.java"), code);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null,
                "-d", tempDir.toString(),
                srcDir.resolve("Calculator.java").toString());

        Path classFile = tempDir.resolve("com/test/Calculator.class");
        assertThat(classFile).exists();

        AsmMethodVisitor visitor = new AsmMethodVisitor("app-test");
        visitor.analyze(classFile);

        List<MethodNode> methods = visitor.getMethods();
        List<RawRelation> relations = visitor.getRelations();

        assertThat(methods).hasSize(2);
        assertThat(methods).extracting(MethodNode::getSignature)
                .anyMatch(s -> s.startsWith("add"))
                .anyMatch(s -> s.startsWith("helper"));
    }
}

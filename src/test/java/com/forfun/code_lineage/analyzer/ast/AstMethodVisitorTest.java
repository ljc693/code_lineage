package com.forfun.code_lineage.analyzer.ast;

import com.forfun.code_lineage.model.graph.MethodNode;
import com.forfun.code_lineage.model.graph.RawRelation;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;

class AstMethodVisitorTest {

    @Test
    void shouldExtractMethodAndCalls() {
        String code = """
            package com.example;
            import org.springframework.web.bind.annotation.*;
            @RestController
            public class UserController {
                @PostMapping("/api/login")
                public Result login(String username, String password) {
                    userService.authenticate(username, password);
                    return Result.ok();
                }
            }
            """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        AstMethodVisitor visitor = new AstMethodVisitor("app-1", null, null);
        visitor.visit(cu, null);

        List<MethodNode> methods = visitor.getMethods();
        List<RawRelation> relations = visitor.getRelations();

        assertThat(methods).hasSize(1);
        MethodNode m = methods.get(0);
        assertThat(m.getSignature()).isEqualTo("login(String, String)");
        assertThat(m.isEntry()).isTrue();
        assertThat(m.getHttpPath()).isEqualTo("/api/login");
        assertThat(m.getHttpMethod()).isEqualTo("POST");

        assertThat(relations).hasSize(2);
        assertThat(relations).anyMatch(r -> r.getCallExpression().contains("authenticate"));
    }
}

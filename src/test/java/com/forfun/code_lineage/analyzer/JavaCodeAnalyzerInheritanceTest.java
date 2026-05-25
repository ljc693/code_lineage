package com.forfun.code_lineage.analyzer;

import com.forfun.code_lineage.model.CallType;
import com.forfun.code_lineage.model.graph.MethodNode;
import com.forfun.code_lineage.model.graph.RawRelation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class JavaCodeAnalyzerInheritanceTest {

    @Test
    void resolvesSameNamedClassesInDifferentPackages() {
        MethodNode ifaceA = MethodNode.builder()
                .methodId("app:pkg1.Foo.bar()").signature("bar()")
                .className("Foo").packageName("pkg1").isAbstract(true).build();
        MethodNode ifaceB = MethodNode.builder()
                .methodId("app:pkg2.Foo.bar()").signature("bar()")
                .className("Foo").packageName("pkg2").isAbstract(true).build();
        MethodNode implA = MethodNode.builder()
                .methodId("app:pkg1.FooImpl.bar()").signature("bar()")
                .className("FooImpl").packageName("pkg1").build();
        MethodNode implB = MethodNode.builder()
                .methodId("app:pkg2.FooImpl.bar()").signature("bar()")
                .className("FooImpl").packageName("pkg2").build();

        Map<String, MethodNode> methods = new LinkedHashMap<>();
        for (var m : List.of(ifaceA, ifaceB, implA, implB))
            methods.put(m.getMethodId(), m);

        // pkg1.FooImpl implements pkg1.Foo; pkg2.FooImpl implements pkg2.Foo
        Map<String, List<String>> classImplements = Map.of(
                "pkg1.FooImpl", List.of("Foo"),
                "pkg2.FooImpl", List.of("Foo"));

        List<RawRelation> relations = new ArrayList<>();
        JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer();
        analyzer.resolveInheritanceCallsForTest(methods, relations, "app", classImplements, Map.of());

        // Should create 2 edges: pkg1.Foo→pkg1.FooImpl and pkg2.Foo→pkg2.FooImpl
        assertThat(relations).hasSize(2);
        assertThat(relations).anyMatch(r ->
                r.getCaller().getMethodId().equals("app:pkg1.Foo.bar()") &&
                r.getCallee().getMethodId().equals("app:pkg1.FooImpl.bar()"));
        assertThat(relations).anyMatch(r ->
                r.getCaller().getMethodId().equals("app:pkg2.Foo.bar()") &&
                r.getCallee().getMethodId().equals("app:pkg2.FooImpl.bar()"));
    }

    @Test
    void extendsAbstractClassResolvesCorrectly() {
        MethodNode parent = MethodNode.builder()
                .methodId("app:pkg.BaseProcessor.execute()").signature("execute()")
                .className("BaseProcessor").packageName("pkg").isAbstract(true).build();
        MethodNode child = MethodNode.builder()
                .methodId("app:pkg.MyProcessor.execute()").signature("execute()")
                .className("MyProcessor").packageName("pkg").build();

        Map<String, MethodNode> methods = new LinkedHashMap<>();
        methods.put(parent.getMethodId(), parent);
        methods.put(child.getMethodId(), child);

        Map<String, String> classExtends = Map.of("pkg.MyProcessor", "BaseProcessor");

        List<RawRelation> relations = new ArrayList<>();
        JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer();
        analyzer.resolveInheritanceCallsForTest(methods, relations, "app", Map.of(), classExtends);

        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).getCaller().getMethodId()).isEqualTo("app:pkg.BaseProcessor.execute()");
        assertThat(relations.get(0).getCallee().getMethodId()).isEqualTo("app:pkg.MyProcessor.execute()");
    }
}

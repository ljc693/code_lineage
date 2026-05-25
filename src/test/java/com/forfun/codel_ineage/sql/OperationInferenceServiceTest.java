package com.forfun.codel_ineage.sql;

import com.forfun.codel_ineage.model.CallType;
import com.forfun.codel_ineage.model.graph.MethodNode;
import com.forfun.codel_ineage.model.graph.RawRelation;
import com.forfun.codel_ineage.model.SqlOperation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OperationInferenceServiceTest {

    private final OperationInferenceService service = new OperationInferenceService();

    // ── infer() tests ────────────────────────────────────────────────

    @Test
    void infer_returnsInsert_whenMethodCallsMapperInsert() {
        MethodNode method = MethodNode.builder()
                .methodId("app:com.example.Foo.bar()")
                .signature("bar()")
                .className("Foo")
                .build();

        MethodNode callee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.insert()")
                .signature("insert")
                .className("taskMapper")
                .build();

        RawRelation relation = RawRelation.builder()
                .caller(method)
                .callee(callee)
                .callType(CallType.INTERNAL)
                .build();

        Map<String, Set<String>> callSignatures = Map.of(
                "com.example.mapper.CrawlerTaskMapper", Set.of("insert"));

        SqlOperation result = service.infer(method, List.of(relation), callSignatures);

        assertThat(result).isEqualTo(SqlOperation.INSERT);
    }

    @Test
    void infer_returnsUpdate_whenMethodCallsMapperUpdate() {
        MethodNode method = MethodNode.builder()
                .methodId("app:com.example.Foo.bar()")
                .signature("bar()")
                .className("Foo")
                .build();

        MethodNode callee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.update()")
                .signature("update")
                .className("taskMapper")
                .build();

        RawRelation relation = RawRelation.builder()
                .caller(method)
                .callee(callee)
                .callType(CallType.INTERNAL)
                .build();

        Map<String, Set<String>> callSignatures = Map.of(
                "com.example.mapper.CrawlerTaskMapper", Set.of("update"));

        SqlOperation result = service.infer(method, List.of(relation), callSignatures);

        assertThat(result).isEqualTo(SqlOperation.UPDATE);
    }

    @Test
    void infer_returnsDelete_whenMethodCallsMapperDelete() {
        MethodNode method = MethodNode.builder()
                .methodId("app:com.example.Foo.bar()")
                .signature("bar()")
                .className("Foo")
                .build();

        MethodNode callee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.delete()")
                .signature("delete")
                .className("taskMapper")
                .build();

        RawRelation relation = RawRelation.builder()
                .caller(method)
                .callee(callee)
                .callType(CallType.INTERNAL)
                .build();

        Map<String, Set<String>> callSignatures = Map.of(
                "com.example.mapper.CrawlerTaskMapper", Set.of("delete"));

        SqlOperation result = service.infer(method, List.of(relation), callSignatures);

        assertThat(result).isEqualTo(SqlOperation.DELETE);
    }

    @Test
    void infer_returnsSelect_whenMethodCallsMapperSelect() {
        MethodNode method = MethodNode.builder()
                .methodId("app:com.example.Foo.bar()")
                .signature("bar()")
                .className("Foo")
                .build();

        MethodNode callee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.selectOne()")
                .signature("selectOne")
                .className("taskMapper")
                .build();

        RawRelation relation = RawRelation.builder()
                .caller(method)
                .callee(callee)
                .callType(CallType.INTERNAL)
                .build();

        Map<String, Set<String>> callSignatures = Map.of(
                "com.example.mapper.CrawlerTaskMapper", Set.of("selectOne"));

        SqlOperation result = service.infer(method, List.of(relation), callSignatures);

        assertThat(result).isEqualTo(SqlOperation.SELECT);
    }

    @Test
    void infer_returnsInsert_overUpdate_whenMethodCallsBoth() {
        MethodNode method = MethodNode.builder()
                .methodId("app:com.example.Foo.bar()")
                .signature("bar()")
                .className("Foo")
                .build();

        MethodNode insertCallee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.insert()")
                .signature("insert")
                .className("taskMapper")
                .build();

        MethodNode updateCallee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.updateById()")
                .signature("updateById")
                .className("taskMapper")
                .build();

        RawRelation relInsert = RawRelation.builder()
                .caller(method)
                .callee(insertCallee)
                .callType(CallType.INTERNAL)
                .build();

        RawRelation relUpdate = RawRelation.builder()
                .caller(method)
                .callee(updateCallee)
                .callType(CallType.INTERNAL)
                .build();

        Map<String, Set<String>> callSignatures = Map.of(
                "com.example.mapper.CrawlerTaskMapper", Set.of("insert", "updateById"));

        SqlOperation result = service.infer(method, List.of(relInsert, relUpdate), callSignatures);

        assertThat(result).isEqualTo(SqlOperation.INSERT);
    }

    @Test
    void infer_returnsDelete_overUpdate_whenMethodCallsBoth() {
        MethodNode method = MethodNode.builder()
                .methodId("app:com.example.Foo.bar()")
                .signature("bar()")
                .className("Foo")
                .build();

        MethodNode deleteCallee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.delete()")
                .signature("delete")
                .className("taskMapper")
                .build();

        MethodNode updateCallee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.update()")
                .signature("update")
                .className("taskMapper")
                .build();

        RawRelation relDelete = RawRelation.builder()
                .caller(method)
                .callee(deleteCallee)
                .callType(CallType.INTERNAL)
                .build();

        RawRelation relUpdate = RawRelation.builder()
                .caller(method)
                .callee(updateCallee)
                .callType(CallType.INTERNAL)
                .build();

        Map<String, Set<String>> callSignatures = Map.of(
                "com.example.mapper.CrawlerTaskMapper", Set.of("delete", "update"));

        SqlOperation result = service.infer(method, List.of(relDelete, relUpdate), callSignatures);

        assertThat(result).isEqualTo(SqlOperation.DELETE);
    }

    @Test
    void infer_returnsSelect_whenNoMapperCalls() {
        MethodNode method = MethodNode.builder()
                .methodId("app:com.example.Foo.bar()")
                .signature("bar()")
                .className("Foo")
                .build();

        // No relations means no mapper calls detected
        Map<String, Set<String>> callSignatures = Map.of(
                "com.example.mapper.CrawlerTaskMapper", Set.of("insert"));

        SqlOperation result = service.infer(method, List.of(), callSignatures);

        assertThat(result).isEqualTo(SqlOperation.SELECT);
    }

    @Test
    void infer_returnsSelect_whenMethodIdIsNull() {
        MethodNode method = MethodNode.builder()
                .methodId(null)
                .signature("bar()")
                .build();

        MethodNode callee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.insert()")
                .signature("insert")
                .build();

        RawRelation relation = RawRelation.builder()
                .caller(method)
                .callee(callee)
                .callType(CallType.INTERNAL)
                .build();

        Map<String, Set<String>> callSignatures = Map.of(
                "com.example.mapper.CrawlerTaskMapper", Set.of("insert"));

        SqlOperation result = service.infer(method, List.of(relation), callSignatures);

        assertThat(result).isEqualTo(SqlOperation.SELECT);
    }

    // ── buildCallSignatures() tests ───────────────────────────────────

    @Test
    void buildCallSignatures_mapsMapperFqcnToCalledMethods() {
        // Set up model with variable→mapper resolution
        ProjectModel model = new ProjectModel();
        model.addVarMapper("taskMapper", "com.example.mapper.CrawlerTaskMapper");
        model.putMapperTable("com.example.mapper.CrawlerTaskMapper", "crawler_task");

        MethodNode caller = MethodNode.builder()
                .methodId("app:com.example.Foo.bar()")
                .signature("bar()")
                .className("Foo")
                .build();

        MethodNode callee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.insert()")
                .signature("insert")
                .className("taskMapper")
                .build();

        RawRelation relation = RawRelation.builder()
                .caller(caller)
                .callee(callee)
                .callType(CallType.INTERNAL)
                .build();

        Map<String, Set<String>> result = service.buildCallSignatures(
                List.of(relation), model);

        assertThat(result)
                .containsKey("com.example.mapper.CrawlerTaskMapper")
                .containsEntry("com.example.mapper.CrawlerTaskMapper", Set.of("insert"));
    }

    @Test
    void buildCallSignatures_resolvesVariableNamesViaModel() {
        // Using only direct class name match (no varToMapper entry)
        ProjectModel model = new ProjectModel();
        model.putMapperTable("com.example.mapper.CrawlerTaskMapper", "crawler_task");

        MethodNode caller = MethodNode.builder()
                .methodId("app:com.example.Foo.bar()")
                .signature("bar()")
                .className("Foo")
                .build();

        MethodNode callee = MethodNode.builder()
                .methodId("app:com.example.mapper.CrawlerTaskMapper.update()")
                .signature("update")
                .className("CrawlerTaskMapper")
                .build();

        RawRelation relation = RawRelation.builder()
                .caller(caller)
                .callee(callee)
                .callType(CallType.INTERNAL)
                .build();

        Map<String, Set<String>> result = service.buildCallSignatures(
                List.of(relation), model);

        // Should resolve via direct class name match since CrawlerTaskMapper
        // is the simple name of the mapper FQCN
        assertThat(result)
                .containsKey("com.example.mapper.CrawlerTaskMapper")
                .containsEntry("com.example.mapper.CrawlerTaskMapper", Set.of("update"));
    }
}

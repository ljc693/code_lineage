package com.forfun.codel_ineage.graph;

import com.forfun.codel_ineage.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class Neo4jAdapterBatchWriteTest {

    private Neo4jMethodRepository methodRepo;
    private Driver neo4jDriver;
    private Session session;
    private Neo4jAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        methodRepo = mock(Neo4jMethodRepository.class);
        neo4jDriver = mock(Driver.class);
        session = mock(Session.class);
        when(neo4jDriver.session()).thenReturn(session);
        when(session.run(anyString(), any(Map.class))).thenReturn(mock(Result.class));

        // Stub methodRepo.findByMethodId() for 5 methods
        for (int i = 0; i < 5; i++) {
            String methodId = "method-" + i;
            when(methodRepo.findByMethodId(methodId)).thenReturn(
                    Neo4jMethodEntity.builder()
                            .methodId(methodId)
                            .signature("com.example.Test.method" + i + "()")
                            .appId("test-app")
                            .build()
            );
        }

        adapter = new Neo4jAdapter(methodRepo, neo4jDriver);
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchWriteUsesSingleUnwindQuery() {
        // Given: 5 methods and 10 CALLS edges
        List<MethodNode> methods = createMethods(5);
        List<CallsEdge> edges = createCallsEdges(10);

        SubGraph subGraph = SubGraph.builder()
                .methods(methods)
                .callsEdges(edges)
                .build();

        // When
        adapter.write(subGraph);

        // Then: session.run() called exactly ONCE with UNWIND batch
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(session, times(1)).run(queryCaptor.capture(), paramsCaptor.capture());

        String query = queryCaptor.getValue();
        assertThat(query).contains("UNWIND");
        assertThat(query).contains("$edges");

        Map<String, Object> params = paramsCaptor.getValue();
        assertThat(params).containsKey("edges");
        assertThat(params.get("edges")).isInstanceOf(List.class);

        List<Map<String, Object>> batch = (List<Map<String, Object>>) params.get("edges");
        assertThat(batch).hasSize(10);

        // Each edge map should have expected keys
        for (Map<String, Object> edgeMap : batch) {
            assertThat(edgeMap)
                    .containsKeys("srcId", "tgtId", "id", "callType", "lineNumber", "expression");
        }
    }

    @Test
    void writeWithNoCallsEdgesDoesNotInvokeSession() {
        // Given: 5 methods but no CALLS edges
        List<MethodNode> methods = createMethods(5);
        SubGraph subGraph = SubGraph.builder()
                .methods(methods)
                .callsEdges(null)
                .build();

        // When
        adapter.write(subGraph);

        // Then: session.run() should never be called
        verify(session, never()).run(anyString(), any(Map.class));
    }

    private static List<MethodNode> createMethods(int count) {
        List<MethodNode> methods = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            methods.add(MethodNode.builder()
                    .methodId("method-" + i)
                    .signature("com.example.Test.method" + i + "()")
                    .returnType("void")
                    .appId("test-app")
                    .build());
        }
        return methods;
    }

    private static List<CallsEdge> createCallsEdges(int count) {
        List<CallsEdge> edges = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int sourceIdx = i % 5;
            int targetIdx = (i + 1) % 5;
            edges.add(CallsEdge.builder()
                    .id("edge-" + i)
                    .sourceMethodId("method-" + sourceIdx)
                    .targetMethodId("method-" + targetIdx)
                    .callType(CallType.INTERNAL)
                    .lineNumber(100 + i)
                    .callExpression("method" + targetIdx + "()")
                    .build());
        }
        return edges;
    }
}

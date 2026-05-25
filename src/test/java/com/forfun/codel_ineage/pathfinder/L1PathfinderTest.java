package com.forfun.codel_ineage.pathfinder;

import com.forfun.codel_ineage.graph.GraphAdapter;
import com.forfun.codel_ineage.graph.GraphAdapter.TraversalResult;
import com.forfun.codel_ineage.model.*;
import com.forfun.codel_ineage.model.graph.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;

class L1PathfinderTest {

    @Test
    void shouldTraceDownstreamMethodCallsToTable() {
        GraphAdapter stubAdapter = new GraphAdapter() {
            @Override
            public void write(SubGraph subGraph) {}
            @Override
            public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
                return List.of();
            }
            @Override
            public TraversalResult traversal(TraversalSpec spec) {
                MethodNode entry = MethodNode.builder()
                        .methodId("app1:com.example.UserController.login(String,String)")
                        .signature("login(String, String)")
                        .className("UserController")
                        .packageName("com.example")
                        .appId("app1")
                        .isEntry(true)
                        .build();
                MethodNode service = MethodNode.builder()
                        .methodId("app1:com.example.UserService.authenticate(String,String)")
                        .signature("authenticate(String, String)")
                        .className("UserService")
                        .packageName("com.example")
                        .appId("app1")
                        .build();
                MethodNode dao = MethodNode.builder()
                        .methodId("app1:com.example.UserDao.findByName(String)")
                        .signature("findByName(String)")
                        .className("UserDao")
                        .packageName("com.example")
                        .appId("app1")
                        .build();
                CallsEdge e1 = CallsEdge.builder()
                        .id("e1").sourceMethodId(entry.getMethodId())
                        .targetMethodId(service.getMethodId())
                        .callType(CallType.INTERNAL).build();
                CallsEdge e2 = CallsEdge.builder()
                        .id("e2").sourceMethodId(service.getMethodId())
                        .targetMethodId(dao.getMethodId())
                        .callType(CallType.INTERNAL).build();
                AccessesEdge ae1 = AccessesEdge.builder()
                        .id("ae1").sourceMethodId(dao.getMethodId())
                        .targetTableId("users")
                        .operation(SqlOperation.SELECT).build();

                return new TraversalResult(
                        List.of(entry, service, dao),
                        List.of(e1, e2),
                        List.of(ae1)
                );
            }
        };

        L1Pathfinder finder = new L1Pathfinder(stubAdapter, null);
        TraceResult result = finder.trace(TraceRequest.builder()
                .entryMethodId("app1:com.example.UserController.login(String,String)")
                .maxDepth(10)
                .endNodeType("TABLE")
                .build());

        assertThat(result.getGraph().getNodes()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.getGraph().getEdges()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.getPaths()).isNotEmpty();
    }
}

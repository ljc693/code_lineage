package com.forfun.codel_ineage.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ModelCompileTest {
    @Test
    void nodeTypesExist() {
        assertThat(NodeType.values()).contains(NodeType.METHOD, NodeType.TABLE);
    }

    @Test
    void edgeTypesExist() {
        assertThat(EdgeType.values()).contains(EdgeType.CALLS, EdgeType.ACCESSES);
    }

    @Test
    void callTypesExist() {
        assertThat(CallType.values()).contains(CallType.INTERNAL, CallType.EXTERNAL);
    }

    @Test
    void sqlOperationsExist() {
        assertThat(SqlOperation.values()).contains(SqlOperation.SELECT, SqlOperation.INSERT);
    }
}

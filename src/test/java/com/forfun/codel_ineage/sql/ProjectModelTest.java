package com.forfun.codel_ineage.sql;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectModelTest {

    private final ProjectModel model = new ProjectModel();

    @Test
    void isEmpty_shouldReturnTrue_forNewModel() {
        assertThat(model.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_shouldReturnFalse_afterAddingMapper() {
        model.putMapperTable("com.example.UserMapper", "user");
        assertThat(model.isEmpty()).isFalse();
    }

    @Test
    void putMapperTable_and_getter_roundTrip() {
        model.putMapperTable("com.example.UserMapper", "user");
        model.putMapperTable("com.example.OrderMapper", "orders");

        assertThat(model.mapperToTable())
                .containsEntry("com.example.UserMapper", "user")
                .containsEntry("com.example.OrderMapper", "orders")
                .hasSize(2);
    }

    @Test
    void addClassMapper_shouldAccumulate_forSameKey() {
        model.addClassMapper("com.example.UserService", "com.example.UserMapper");
        model.addClassMapper("com.example.UserService", "com.example.OrderMapper");

        assertThat(model.classToMappers().get("com.example.UserService"))
                .containsExactly("com.example.UserMapper", "com.example.OrderMapper");
    }

    @Test
    void addClassMapper_shouldCreateNewList_forDifferentKeys() {
        model.addClassMapper("com.example.UserService", "com.example.UserMapper");
        model.addClassMapper("com.example.OrderService", "com.example.OrderMapper");

        assertThat(model.classToMappers().get("com.example.UserService"))
                .containsExactly("com.example.UserMapper");
        assertThat(model.classToMappers().get("com.example.OrderService"))
                .containsExactly("com.example.OrderMapper");
    }

    @Test
    void addVarMapper_shouldAccumulateWithoutDuplicates() {
        model.addVarMapper("userMapper", "com.example.UserMapper");
        model.addVarMapper("userMapper", "com.example.UserMapper");
        model.addVarMapper("userMapper", "com.example.OrderMapper");

        assertThat(model.varToMappers().get("userMapper"))
                .containsExactlyInAnyOrder("com.example.UserMapper", "com.example.OrderMapper");
    }

    @Test
    void addVarMapper_shouldCreateNewSet_forDifferentKeys() {
        model.addVarMapper("userMapper", "com.example.UserMapper");
        model.addVarMapper("orderMapper", "com.example.OrderMapper");

        assertThat(model.varToMappers().get("userMapper"))
                .containsExactly("com.example.UserMapper");
        assertThat(model.varToMappers().get("orderMapper"))
                .containsExactly("com.example.OrderMapper");
    }

    @Test
    void simpleNameToMappers_shouldExtractSimpleNames_fromFqcnKeys() {
        model.addClassMapper("com.example.UserService", "com.example.UserMapper");
        model.addClassMapper("com.example.UserService", "com.example.OrderMapper");
        model.addClassMapper("com.example.admin.AdminService", "com.example.AdminMapper");

        Map<String, List<String>> result = model.simpleNameToMappers();

        assertThat(result).containsKey("UserService");
        assertThat(result.get("UserService"))
                .containsExactlyInAnyOrder("com.example.UserMapper", "com.example.OrderMapper");
        assertThat(result).containsKey("AdminService");
        assertThat(result.get("AdminService"))
                .containsExactly("com.example.AdminMapper");
    }

    @Test
    void simpleNameToMappers_shouldHandle_simpleNameWithoutPackage() {
        model.addClassMapper("SimpleClass", "com.example.SimpleMapper");

        Map<String, List<String>> result = model.simpleNameToMappers();

        assertThat(result).containsKey("SimpleClass");
        assertThat(result.get("SimpleClass")).containsExactly("com.example.SimpleMapper");
    }

    @Test
    void putPoColumns_and_getter_roundTrip() {
        model.putPoColumns("User", List.of("id", "name", "email"));
        model.putPoColumns("Order", List.of("orderId", "amount"));

        assertThat(model.poColumns())
                .containsEntry("User", List.of("id", "name", "email"))
                .containsEntry("Order", List.of("orderId", "amount"))
                .hasSize(2);
    }

    @Test
    void putMapperPo_and_getter_roundTrip() {
        model.putMapperPo("com.example.UserMapper", "User");
        model.putMapperPo("com.example.OrderMapper", "Order");

        assertThat(model.mapperToPo())
                .containsEntry("com.example.UserMapper", "User")
                .containsEntry("com.example.OrderMapper", "Order")
                .hasSize(2);
    }
}

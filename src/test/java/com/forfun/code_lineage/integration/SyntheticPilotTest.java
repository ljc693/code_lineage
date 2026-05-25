package com.forfun.code_lineage.integration;

import com.forfun.code_lineage.analyzer.AnalyzeResult;
import com.forfun.code_lineage.analyzer.AnalyzeTask;
import com.forfun.code_lineage.analyzer.JavaCodeAnalyzer;
import com.forfun.code_lineage.analyzer.fetch.FetchedCode;
import com.forfun.code_lineage.model.CallType;
import com.forfun.code_lineage.model.graph.MethodNode;
import com.forfun.code_lineage.model.graph.RawRelation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4th + 5th pilot projects: synthetic Dubbo RPC service + MyBatis DAO project.
 */
class SyntheticPilotTest {

    // --- Pilot 4: Dubbo RPC microservice ---
    @Test
    void shouldScanDubboRPCProject(@TempDir Path projectDir) throws Exception {
        createFile(projectDir, "com/example/rpc/OrderRpcServiceImpl.java", """
            package com.example.rpc;
            import org.apache.dubbo.config.annotation.DubboService;
            @DubboService
            public class OrderRpcServiceImpl implements OrderRpcService {
                private final OrderService orderService = new OrderService();
                public OrderDTO createOrder(CreateOrderRequest request) {
                    orderService.validate(request);
                    OrderDTO order = orderService.create(request);
                    orderService.sendNotification(order);
                    return order;
                }
                public OrderDTO getOrder(Long orderId) {
                    return orderService.findById(orderId);
                }
            }
            """);

        createFile(projectDir, "com/example/rpc/OrderRpcService.java", """
            package com.example.rpc;
            public interface OrderRpcService {
                OrderDTO createOrder(CreateOrderRequest request);
                OrderDTO getOrder(Long orderId);
            }
            """);

        createFile(projectDir, "com/example/service/OrderService.java", """
            package com.example.service;
            public class OrderService {
                private final OrderDao orderDao = new OrderDao();
                public void validate(CreateOrderRequest request) {}
                public OrderDTO create(CreateOrderRequest request) {
                    return orderDao.insert(request);
                }
                public OrderDTO findById(Long id) {
                    return orderDao.selectById(id);
                }
                public void sendNotification(OrderDTO order) {}
            }
            """);

        createFile(projectDir, "com/example/dao/OrderDao.java", """
            package com.example.dao;
            public class OrderDao {
                public OrderDTO insert(CreateOrderRequest req) {
                    // INSERT INTO orders ...
                    return new OrderDTO();
                }
                public OrderDTO selectById(Long id) {
                    // SELECT * FROM orders WHERE id = ?
                    return new OrderDTO();
                }
            }
            """);

        createFile(projectDir, "com/example/dto/OrderDTO.java", """
            package com.example.dto;
            public class OrderDTO {
                private Long id;
                private String status;
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                public String getStatus() { return status; }
            }
            """);

        createFile(projectDir, "com/example/dto/CreateOrderRequest.java", """
            package com.example.dto;
            public class CreateOrderRequest {
                private String productCode;
                private int quantity;
            }
            """);

        Files.createFile(projectDir.resolve("pom.xml"));

        JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer();
        AnalyzeResult result = analyzer.analyze(AnalyzeTask.builder()
                .fetchedCode(FetchedCode.builder().baseDir(projectDir.toString())
                        .changedFiles(List.of()).appId("order-rpc").build())
                .appId("order-rpc").build());

        System.out.println("\n=== Pilot 4: Dubbo RPC Service ===");
        System.out.println("Methods: " + result.getMethods().size());
        System.out.println("Relations: " + result.getRelations().size());

        List<MethodNode> entries = result.getMethods().stream()
                .filter(MethodNode::isEntry).toList();
        System.out.println("RPC Entries: " + entries.size());
        entries.forEach(e -> System.out.println("  RPC: " + e.getClassName() + "." + e.getSignature()
                + " [" + e.getAnnotations() + "]"));

        assertThat(result.getMethods()).isNotEmpty();
        assertThat(entries).isNotEmpty(); // @DubboService detected
    }

    // --- Pilot 5: MyBatis + Spring Boot project ---
    @Test
    void shouldScanMyBatisProject(@TempDir Path projectDir) throws Exception {
        createFile(projectDir, "com/example/controller/UserController.java", """
            package com.example.controller;
            import org.springframework.web.bind.annotation.*;
            @RestController
            public class UserController {
                private final UserService userService = new UserService();
                @GetMapping("/api/users/{id}")
                public UserDTO getUser(@PathVariable Long id) {
                    return userService.getUser(id);
                }
                @PostMapping("/api/users")
                public UserDTO createUser(@RequestBody CreateUserRequest req) {
                    return userService.createUser(req);
                }
            }
            """);

        createFile(projectDir, "com/example/service/UserService.java", """
            package com.example.service;
            public class UserService {
                private final UserMapper userMapper = new UserMapperImpl();
                public UserDTO getUser(Long id) {
                    return userMapper.selectById(id);
                }
                public UserDTO createUser(CreateUserRequest req) {
                    userMapper.insert(req.getName(), req.getEmail());
                    return userMapper.selectByEmail(req.getEmail());
                }
            }
            """);

        createFile(projectDir, "com/example/mapper/UserMapper.java", """
            package com.example.mapper;
            public interface UserMapper {
                UserDTO selectById(Long id);
                UserDTO selectByEmail(String email);
                int insert(String name, String email);
            }
            """);

        createFile(projectDir, "com/example/mapper/UserMapperImpl.java", """
            package com.example.mapper;
            public class UserMapperImpl implements UserMapper {
                public UserDTO selectById(Long id) {
                    // SELECT * FROM users WHERE id = #{id}
                    return new UserDTO();
                }
                public UserDTO selectByEmail(String email) {
                    // SELECT * FROM users WHERE email = #{email}
                    return new UserDTO();
                }
                public int insert(String name, String email) {
                    // INSERT INTO users(name, email) VALUES(#{name}, #{email})
                    return 1;
                }
            }
            """);

        createFile(projectDir, "com/example/dto/UserDTO.java", """
            package com.example.dto;
            public class UserDTO {
                private Long id;
                private String name;
                private String email;
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
            }
            """);

        createFile(projectDir, "com/example/dto/CreateUserRequest.java", """
            package com.example.dto;
            public class CreateUserRequest {
                private String name;
                private String email;
                public String getName() { return name; }
                public String getEmail() { return email; }
            }
            """);

        Files.createFile(projectDir.resolve("pom.xml"));

        JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer();
        AnalyzeResult result = analyzer.analyze(AnalyzeTask.builder()
                .fetchedCode(FetchedCode.builder().baseDir(projectDir.toString())
                        .changedFiles(List.of()).appId("user-service").build())
                .appId("user-service").build());

        System.out.println("\n=== Pilot 5: MyBatis + Spring Boot ===");
        System.out.println("Methods: " + result.getMethods().size());
        System.out.println("Relations: " + result.getRelations().size());

        List<MethodNode> entries = result.getMethods().stream()
                .filter(MethodNode::isEntry).toList();
        System.out.println("HTTP Entries: " + entries.size());
        entries.forEach(e -> System.out.println("  " + e.getHttpMethod() + " " + e.getHttpPath()
                + " → " + e.getClassName() + "." + e.getSignature()));

        // Verify path completeness: Controller → Service → Mapper → DB
        List<RawRelation> relations = result.getRelations();
        long internalCount = relations.stream()
                .filter(r -> r.getCallType() == CallType.INTERNAL).count();
        System.out.println("Internal calls: " + internalCount);

        assertThat(entries).hasSize(2); // GET + POST
        assertThat(internalCount).isGreaterThanOrEqualTo(4); // controller→service→mapper
    }

    private void createFile(Path projectDir, String relativePath, String content) throws Exception {
        Path filePath = projectDir.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}

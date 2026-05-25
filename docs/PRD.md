# 产品需求文档：Code Lineage 程序血缘分析平台

| 属性 | 内容 |
|------|------|
| 版本 | V1.0 |
| 最后更新 | 2026-05-26 |
| 状态 | 核心功能已交付，V4-V5 扩展中 |

## 1. 产品目标

构建代码级调用关系分析平台，自动提取 Java 项目的方法调用链和数据库访问关系，以图数据持久化，为影响评估、代码治理和 LLM 辅助分析提供可视化、可量化的决策依据。

## 2. 已交付功能

### 2.1 代码分析引擎

- **双引擎扫描**：AST (JavaParser) 精确提取方法签名/注解/行号，ASM 字节码补全依赖 jar 包
- **SQL 解析**：MyBatis XML、MyBatis-Plus BaseMapper、JPA @Query 三种模式的表/列提取
- **操作类型推断**：基于实际 Mapper 方法调用推断 INSERT/UPDATE/DELETE/SELECT
- **入口点检测**：SPI 插件机制，内置 Spring MVC、Dubbo、gRPC、Kafka、ShenYu 检测器
- **治理分析**：圈复杂度、重复率、注释覆盖率统计

### 2.2 血缘图谱

- **Neo4j 图存储**：Method/Table 节点，CALLS/ACCESSES 关系边
- **三级链路**：L1 应用内 → L2 跨应用 → L3 系统拓扑
- **字段级血缘**：MySQL column_lineage 表记录方法→表→列的映射关系
- **上游追溯**：从数据库字段逆向追溯到 HTTP 入口端点

### 2.3 API 服务

- **谱系查询**：下游/上游 BFS 遍历，支持 G6/树形/原始三种输出格式
- **字段影响分析**：表/列级别的影响方法和上游 HTTP 端点查询
- **死列检测**：基于访问时间阈值的未使用列识别
- **节点价值标签**：HIGH_IMPACT / DEPRECATED_SUSPECT / CRITICAL_PATH 自动分类
- **CI/CD 集成**：扫描触发 → 进度轮询 → 历史查询的完整接口

### 2.4 分析引擎 (V5)

- **5 个可插拔检测器**：N+1 查询、上帝方法、循环依赖、孤立代码、层级违规
- **策略+模板方法设计**：自定义检测器只需继承 `AbstractAnalysisRule` + `@Component`
- **LLM 报告基础设施**：3 种内置模板（死列分析、字段影响、表治理），prompt-ready JSON 导出
- **A↔C 桥接**：分析引擎发现可注入 LLM 报告上下文

### 2.5 前端

- **AntV G6 力导向图**：血缘图谱可视化，节点展开/搜索/详情
- **字段影响分析页**：表→列→方法→上游端点三层级联面板
- **仪表盘**：项目自动发现、方法统计概览

## 3. 未来规划

### 3.1 V5 待完成

- [ ] 剩余 4 个检测器的端到端验证
- [ ] LLM 实际 API 调用集成（当前仅构建 prompt 结构）
- [ ] 自定义检测器文档和示例

### 3.2 远期规划 (V6+)

| 功能 | 说明 |
|------|------|
| 增量扫描 | Git diff 变更文件范围扫描 |
| 多语言支持 | Python/Go 的 AST 分析模块 |
| CI 插件 | Jenkins/GitHub Actions 原生集成 |
| 变更影响报告 | 代码提交 → 自动生成受影响方法/接口清单 |
| 实时 JDBC Agent | 运行时 SQL 捕获（`SqlCaptureService` 已预留接口） |
| 交互式查询助手 (B) | 自然语言查询血缘关系 |
| HugeGraph 适配 | 切换到 HugeGraph 图数据库（`HugeGraphAdapter` 已预留接口，当前默认 Neo4j） |

## 4. 技术架构

```
采集层: Git (JGit) → AST (JavaParser) + ASM (bytecode) → RawRelation
加工层: SQL Parsing → SubGraph → Neo4jAdapter (batch UNWIND)
存储层: Neo4j 5.26 (Bolt) + MySQL 8.0 (JdbcTemplate)
服务层: Spring Boot 3.5 REST API (G6/raw/tree formats)
应用层: AntV G6 frontend + LLM report generation
```

## 5. 性能指标

| 指标 | 目标 | 实际 |
|------|------|------|
| 单应用扫描 (clawer, 141 文件) | <5s | <1s |
| 大项目扫描 (ShenYu, 2000+ 文件) | <30s | 4.3s |
| API 查询响应 | <500ms | <100ms |
| 单元测试 | — | <3s |
| Neo4j 边写入 (21000 edges) | — | 1 次 UNWIND 查询 |

## 6. 部署

```bash
docker-compose up -d mysql neo4j    # 基础设施
./gradlew bootRun                   # 应用启动 :8080
```

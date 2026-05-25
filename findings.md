# 发现与决策

## 当前状态 (2026-05-26)

**已交付：** 代码分析引擎 (AST+ASM)、Neo4j 图存储、MySQL 列级血缘、G6 可视化、MyBatis-Plus 解析、5 个分析检测器、LLM 报告基础设施、性能优化（测试 <3s、批量 UNWIND）、CI/CD 进度 API。

**包结构：** `com.forfun.code_lineage` | **项目名：** `code-lineage` | **数据库：** `lineage`

## 需求
- V1 核心：应用内方法级调用链还原 (HTTP/RPC 入口 → DB 操作)，路径完整性 ≥85%
- 5 个试点 Java 项目，需覆盖各类型技术栈
- 前端 AntV G6，API 需原生兼容 G6 数据格式
- 为后续大模型分析库表合理性预留扩展点
- 为后续跨应用/跨系统链路拼接预留字段

## 研究发现
- Neo4j Spring Data 支持 `@Node` 和 `@RelationshipProperties` 注解，适合图建模
- Neo4j Spring Data 支持 `@Node` 和 `@RelationshipProperties` 注解，适合图建模
- JavaParser 3.26.3 支持 Java 21 语法
- ASM 9.7.1 支持 Java 21 字节码
- JGit 7.1.0 支持增量 diff (TreeWalk 比较 commit tree)
- AntV G6 v5 数据格式：`{nodes: [{id, type, data, style, comboId}], edges: [{source, target, type, style}], combos: [{id, type, label}]}`
- MyBatis XML 通过 `namespace` 属性绑定 Mapper 接口，statement `id` 映射方法名
- JPA `@Query` 注解可直接提取 SQL 但需 AST 解析注解值

## 技术决策
| 决策 | 理由 |
|------|------|
| AST 为主 + ASM 补全 | AST 精确拿签名/注解/行号，ASM 补 jar 包 |
| SQL 解析精确到字段级 | V2 LLM 字段影响分析需要列级粒度 |
| V1 Neo4j + GraphAdapter 抽象 | 后续切 HugeGraph 只需加 Adapter |
| V1 Spring Events 同步 + EventBus 抽象 | 后续切 Kafka 只需换实现 |
| V1 L1 单应用内 | EXTERNAL 边和 system_id 已预留 |
| CodeFetcher 与 CodeAnalyzer 分离 | 拉取和分析独立可替换 |
| methodId 格式：`appId:package.Class.method(params)` | 全局唯一，可直接作为 Neo4j @Id |

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| MyBatis Mapper 方法到 SQL 的匹配 | 通过 XML namespace + statement id 对应 Mapper 接口全限定名 |
| 外部调用检测 (HTTP/RPC) | AST 识别 `restTemplate`/`webClient`/`kafkaTemplate` 等 scope 名 |
| 接口/抽象类方法无实现 | 图查询 IMPLEMENTS 边 + ASM 字节码查找具体实现类 |
| ShenYu 2K+ 文件扫描 OOM | 根因: @SpringBootTest + Neo4j 上下文 (~2GB)，非核心分析代码。纯分析代码仅需 177MB |
| Gradle maxHeapSize 不生效 | Gradle Daemon 有独立 JVM，需通过 gradle.properties 的 org.gradle.jvmargs 控制 |

## 试点扫描结果

| 项目 | 文件数 | 方法数 | 关系数 | HTTP入口 | RPC服务 | 内存 | 耗时 |
|------|--------|--------|--------|----------|---------|------|------|
| clawer | 141 | 748 | 1,711 | 29 | 0 | ~50MB | <1s |
| ShenYu | 2,076 | 14,343 | 44,442 | 355 | 63 | ~177MB | 4.3s |

---

## V5 分析系统

> Spec: `docs/superpowers/specs/2026-05-26-llm-analysis-design.md`
> Plan: `docs/superpowers/plans/2026-05-26-analysis-system-plan.md`

**架构**: C 子系统（纯算法，策略+模板方法）负责检测，A 子系统（LLM 集成）负责解释。两者独立，共享 MySQL 存储，通过 REST API 触发。

### C — Algorithm Rules (策略 + 模板方法)

| 检测器 | 状态 | 说明 |
|--------|------|------|
| NPlusOneDetector | ⏳ planned | 循环内 DB 调用 → N+1 风险 |
| GodMethodDetector | ⏳ planned | 单方法访问 >5 张表 |
| CircularDependencyDetector | ⏳ planned | CALLS 环检测 |
| OrphanCodeDetector | ⏳ planned | 无入度 + 非入口点 |
| LayerViolationDetector | ⏳ planned | Controller 直接 ACCESSES 表 |
| Custom (user-defined) | ⏳ planned | 继承 AbstractAnalysisRule + @Component 即可 |

### A — LLM Reports

| 报告 | 状态 | LLM 做什么 |
|------|------|-----------|
| Dead Column Report | ⏳ planned | 结合列名语义判断删除风险 |
| Field Impact Report | ⏳ planned | blast radius + 测试范围建议 |
| Table Governance | ⏳ planned | 综合评分 + 重构优先级 + 自然语言摘要 |
| Analysis Summary | ⏳ planned | 把 C 的 findings 翻译为可读报告 |

### APIs

```
C: POST /api/v1/analysis/rules/run | GET /rules | GET /findings
A: POST /api/v1/llm/report/:type     | GET /reports
```

---

## V4 后续待办

### 🔴 P0 — 性能 / 正确性

| 事项 | 说明 |
|------|------|
| ShenYu 重扫验证 | 当前 Neo4j 中 ShenYu 数据缺失，需清库后全量重扫 clawer + ShenYu，验证批量 UNWIND 在大数据量下的表现 |
| AstMethodVisitor 类名多义性 | `byClassAndSig.get(implSimple)` 仅用简单类名匹配，不同包的同名类会冲突，需升级为 FQCN 索引 |
| raw relations→CALLS 匹配率追踪 | 当前 2609 条 raw relations → 1588 条 CALLS 边 (61%)，需分析未匹配率根因 |

### 🟡 P1 — 功能完善

| 事项 | 说明 |
|------|------|
| JPA @Query JPQL 解析 | `JpaSqlParser` 当前返回空，需实现注解值提取 + JPQL→表名解析 |
| ColumnLineage 列名来源统一 | 当前列名来自 PO 字段（静态）和 `*` 占位符（fallback）；运行时 JDBC Agent (`SqlCaptureService`) 未接入 DataLoader 流程 |
| ShenYu 入口点检测验证 | 试点报告称 ShenYu HTTP 入口 355→1018 (+187%)，需确认当前检测器覆盖率 |
| `CONSTRUCTOR_PARAM` 正则死代码 | 已声明未使用，需清理或实现构造器参数注入检测 |
| Neo4jAdapter `query()` 存根 | 返回 `List.of()`，GraphAdapter 接口未完成 |

### 🟢 P2 — 代码质量 / 规范

| 事项 | 说明 |
|------|------|
| 通配导入清理 | `import java.util.*` 改为单类导入 |
| 输出参数→值对象 | `discoverMappers` 的 3 个 Map 输出参数改为返回值对象 |
| StaticJavaParser 全局状态 | `static { setLanguageLevel }` 改为实例级配置 |
| CompositeSqlParser 责任链 | 硬编码 3 个解析器 → 注入 `List<SqlParser>` + `@Order` |
| DataLoader 绕过 IoC | `new JavaCodeAnalyzer()` / `new Neo4jAdapter()` → Spring Bean 注入 |
| column_name `*` 清理 | ColumnLineageRepository 中 `<> '*'` 是临时修补，应从源头避免 |

### 🔵 P3 — 扩展 / 未来

| 事项 | 说明 |
|------|------|
| 反射/动态代理支持 | `Method.invoke()`、JDK/CGLIB proxy 不可见，需运行时 Agent 补充 |
| Lambda invokedynamic | ASM 未处理 `invokedynamic`，lambda 体丢失 |
| Spring @Async/@EventListener/@Scheduled | 隐式调用关系不可见 |
| 多模块项目测试 | clawer 是多模块 Gradle 项目，单模块项目已覆盖 |
| CI/CD 集成 | 进度 API + ScanHistory 已有，需实际接入 Jenkins/GitHub Actions |
| 前端字段分析 UI 操作列 | fields.html 现已返回 operation 字段，需验证前端 Tag 颜色正确显示 |

---

*每执行2次查看/浏览器/搜索操作后更新此文件*
*防止视觉信息丢失*

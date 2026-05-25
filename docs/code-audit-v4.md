# V4 核心模块代码审计报告

## 审计范围

| 模块 | 文件 | 行数 |
|------|------|------|
| SQL 解析 | MyBatisPlusSqlParser, MyBatisSqlParser, JpaSqlParser, CompositeSqlParser | ~450 |
| AST 分析 | AstMethodVisitor | ~220 |
| ASM 分析 | AsmMethodVisitor | ~110 |
| 分析编排 | JavaCodeAnalyzer | ~230 |
| 图适配器 | Neo4jAdapter | ~245 |
| 数据加载 | DataLoader | ~200 |

---

## 一、设计原则违规 (SOLID)

### 1.1 SRP 单一职责 — MyBatisPlusSqlParser :red_circle:

`MyBatisPlusSqlParser` (354行) 承担了 6 项职责：
- Mapper 接口发现 (`discoverMappers`)
- PO 类解析 (表名 + 字段列名)
- 字段注入检测 (`discoverMapperUsages`)
- 变量名→类型映射 (`buildVarToMapperMap`)
- 操作类型推断 (`inferOperation` + `buildMapperCallSignatures`)
- SqlRelations 生成

**违反**: 单一职责原则。一个类变更的原因太多。

**建议**: 拆分为 `MapperDiscoveryService`、`PoClassAnalyzer`、`OperationInferenceService`。

### 1.2 SRP — Neo4jAdapter :yellow_circle:

同时负责：写操作 (方法节点 + CALLS 边 + ACCESSES 边)、读操作 (lineage traversal)、方法匹配/解析 (`resolveCallee`, `findMethod`)。

**建议**: 读写分离为 `Neo4jWriter` 和 `Neo4jReader`。

### 1.3 OCP 开闭原则 — CompositeSqlParser :yellow_circle:

硬编码了 3 个解析器的调用顺序。新增解析器需要修改 `CompositeSqlParser` 源码。

**建议**: 注入 `List<SqlParser>`，按 `@Order` 排序执行，或使用责任链模式。

### 1.4 DIP 依赖倒置 — DataLoader :yellow_circle:

`scanProject()` 直接 `new JavaCodeAnalyzer()` 和 `new Neo4jAdapter()`，绕过了 Spring IoC 容器。

**建议**: 注入这些 Bean，DataLoader 不应负责对象创建。

---

## 二、设计模式问题

### 2.1 模板方法缺失 — MyBatisPlusSqlParser :yellow_circle:

`discoverMappers`、`discoverMapperUsages`、`buildVarToMapperMap` 三个方法共享相同的文件遍历模式：
```
try (walk baseDir) {
    filter .java non-test files
    for each file: read content → match patterns → collect results
}
```
**建议**: 提取 `walkJavaFiles(baseDir, consumer)` 模板方法。

### 2.2 策略模式缺失 — Operation Inference :yellow_circle:

操作类型推断从「方法名前缀」→「方法体扫描」→「RawRelations 分析」迭代了三次。接口一致但硬编码切换。

**建议**: `OperationInferenceStrategy` 接口 + 多个实现，按优先级链式调用。

### 2.3 Builder 映射重复 — Neo4jAdapter :yellow_circle:

`Neo4jAdapter.write()` 和 `toMethodNode()` 两处手写 MethodNode→Neo4jMethodEntity 字段映射，完全重复。

**建议**: 提取 `MethodNodeConverter` 或使用 MapStruct。

### 2.4 Null 对象模式缺失 — JpaSqlParser :yellow_circle:

`JpaSqlParser` 返回 `tableName("unknown")` 作为占位符，会在 Neo4j 中创建无意义的垃圾 Table 节点。

**建议**: 无法解析时返回空列表，让调用方决定如何处理。

---

## 三、Java 开发规范问题

### 3.1 异常处理 — 全局 :red_circle:

| 文件 | 问题 | 位置 |
|------|------|------|
| `MyBatisPlusSqlParser` | `catch (Exception ignored) {}` × 7 处 | L152,187,196,238,240,315,317 |
| `JavaCodeAnalyzer` | `catch (Exception e) {}` 无日志 × 4 处 | L69,71,84,86 |
| `JavaCodeAnalyzer` | `catch (Exception ignored) {}` × 2 处 | L140,221 |

**违反**: 《阿里巴巴 Java 开发手册》— 异常不应被忽略。单个文件解析失败至少应记录 WARN 级别日志，以便排查数据缺失。

### 3.2 文件被多次遍历 — MyBatisPlusSqlParser :red_circle:

`parse()` 方法内整个 baseDir 的 Java 文件被遍历 3 次：
1. `discoverMappers()` — 扫描 BaseMapper 接口和 PO 类
2. `discoverMapperUsages()` — 扫描 Mapper 字段注入
3. `buildVarToMapperMap()` — 扫描变量名到类型映射

I/O 开销 ×3，大项目（如 ShenYu 2000+ 文件）影响显著。

**建议**: 一次遍历收集所有信息，存入 `ProjectModel` 中间对象。

### 3.3 魔法值 — MyBatisPlusSqlParser :yellow_circle:

```java
.sourceMethodId("mapper:" + entry.getKey())   // L115 — 魔法前缀
.rawSql("mybatis-plus:" + mapperFqcn)          // L104 — 魔法前缀
```

而 DataLoader 中通过 `srcMethodId.startsWith("mapper:")` 跳过这些条目。这是跨文件的**字符串契约耦合**。

**建议**: 定义常量 `STUB_METHOD_PREFIX`，或在 SqlRelations 中增加 `isStub()` 标记字段。

### 3.4 输出参数模式 — MyBatisPlusSqlParser :yellow_circle:

```java
void discoverMappers(Path baseDir,
    Map<String, String> mapperToTable,      // 输出
    Map<String, List<String>> poColumns,     // 输出
    Map<String, String> mapperToPo)          // 输出
```

方法签名中 3 个 Map 参数既是输入又是输出，混淆调用意图。

**建议**: 返回 `MapperDiscoveryResult` 值对象，包含 `mapperToTable`、`poColumns`、`mapperToPo`。

### 3.5 全局静态状态 — JavaCodeAnalyzer :yellow_circle:

```java
static {
    StaticJavaParser.getParserConfiguration()
        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
}
```

**违反**: 修改全局状态影响所有使用 `StaticJavaParser` 的代码，包括第三方库。

**建议**: 创建独立 `JavaParser` 实例，或使用 `StaticJavaParser.setConfiguration()`。

### 3.6 N+1 查询 — Neo4jAdapter :red_circle:

`write()` 方法中每条 CALLS 边单独执行一次 Cypher MERGE（L57-67）。以 clawer 21000 条边为例 = 21000 次网络往返。

**建议**: 使用批量 UNWIND 模式：
```cypher
UNWIND $edges AS edge
MATCH (src:Method {methodId: edge.src})
MATCH (tgt:Method {methodId: edge.tgt})
MERGE (src)-[c:CALLS {id: edge.id}]->(tgt)
SET c += edge.props
```

### 3.7 通配导入 — 全局 :white_circle:

`import java.util.*` 在多处使用。虽然方便但降低可读性。

**建议**: 配置 IDE 自动展开为单类导入，或设置 `imports_on_demand` 阈值为 999。

---

## 四、具体代码缺陷清单

| # | 文件:行 | 问题 | 严重度 |
|---|---------|------|--------|
| 1 | MyBatisPlusSqlParser:35 | `CONSTRUCTOR_PARAM` 正则已声明但从未使用（死代码） | 低 |
| 2 | MyBatisPlusSqlParser:59,63,70 | 步骤注释编号错误 — Step 3 缺失，两个 Step 5 | 低 |
| 3 | MyBatisPlusSqlParser:179-181 | `serialVersionUID` 和 `id` 硬编码跳过，应使用可配置的排除集合 | 低 |
| 4 | MyBatisPlusSqlParser:342 | `return SqlOperation.SELECT` 在 mapper 方法匹配时返回 SELECT 而非继续遍历 — 如果 `selectOne` 先于 `delete` 被匹配，会错误返回 SELECT | 中 |
| 5 | JpaSqlParser:28 | `tableName("unknown")` 创建垃圾 Table 节点 | 中 |
| 6 | JpaSqlParser:25-32 | `@Query` 的值未被提取，没有真正解析 JPQL/SQL | 中 |
| 7 | CompositeSqlParser:27-29 | 三个解析器串行调用但无结果去重 — 同一方法→表的映射可能被多个解析器重复生成 | 中 |
| 8 | JavaCodeAnalyzer:197 | `parentMethod.getReturnType() == null` 作为接口判断 hack — 应使用 `isAbstract()` | 中 |
| 9 | Neo4jAdapter:100-102 | `query()` 方法返回 `List.of()` — 存根实现，接口契约未完成 | 低 |
| 10 | Neo4jAdapter:210 | 旧版 `findMethod` 使用 `LIMIT 1` 签名匹配，结果不确定 | 中 |
| 11 | DataLoader:74-77 | 文件收集逻辑在 DataLoader 和 JavaCodeAnalyzer 中重复实现 | 中 |
| 12 | DataLoader:127 | 每次扫描 `new Neo4jAdapter()` 绕过 Spring 管理 | 中 |
| 13 | AstMethodVisitor:165 | `bodyStr.replaceAll("\\s+", " ")` 每次调用编译正则 | 低 |
| 14 | ColumnLineageRepository | `column_name <> '*'` 过滤是临时修补 — 应从源头避免 `*` 条目 | 中 |

---

## 五、修复优先级

| 优先级 | 问题 | 影响范围 |
|--------|------|----------|
| **P0** | MyBatisPlusSqlParser 3x 文件遍历 | 大项目扫描时间 |
| **P0** | Neo4jAdapter N+1 Cypher 查询 | CALLS 边写入占 90% 扫描时间 |
| **P0** | 全局 `catch(Exception)` 吞异常 | 数据静默丢失，无法排查 |
| **P1** | MyBatisPlusSqlParser SRP 拆分 | 可维护性、可测试性 |
| **P1** | JpaSqlParser "unknown" 表 | 数据质量 |
| **P1** | DataLoader 绕过 IoC | 可测试性 |
| **P1** | Operation inference 策略模式 | 可扩展性 |
| **P2** | 魔法字符串常量化 | 代码规范 |
| **P2** | 通配导入、死代码清理 | 代码规范 |
| **P2** | 输出参数 → 值对象 | 代码可读性 |
| **P3** | StaticJavaParser 全局状态 | 潜在并发问题 |
| **P3** | `query()` 存根实现 | 接口契约 |

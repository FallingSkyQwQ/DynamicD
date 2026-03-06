# DynamicD 实现设计与计划（v0.1）

## 1. 目标与交付标准
本计划用于把 `docs/spec/dynamicd-spec.md` 与 `docs/requirements/dynamicd-ears.md` 直接转为可执行开发任务。

完成标准：
- 所有 `P0` 需求具备实现与验收用例。
- 核心路径（编译 -> 加载 -> 运行 -> 热重载 -> 回滚）可端到端验证。
- Agent/REPL/权限审计具备最小可用闭环。

## 2. 关键技术决策
- Runtime：Java 21 + Paper API。
- 构建：Gradle Kotlin DSL。
- 语言实现：前端（lexer/parser/AST）+ 语义（name/type/effect）+ IR + 解释执行/可选字节码后端。
- 命令与交互：Brigadier + Adventure。
- 配置与数据：YAML + SQLite/H2（可替换）。
- 缓存：Caffeine。

## 3. 子系统设计
### 3.1 编译系统
- 输入：模块目录内 `.yuz` 与可选 `module.yml`。
- 输出：`*.dyc` 编译产物 + 诊断对象 + 注册表（事件/命令/权限/placeholder）。
- 关键接口：
  - `compileModule(moduleId): CompileResult`
  - `diagnose(moduleId): List<Diagnostic>`
  - `buildSymbolIndex(moduleId): SymbolIndex`
- 失败策略：编译失败模块不得加载；保留上次可用产物用于回滚。

### 3.2 运行时系统
- 模块状态机：`PREPARED -> COMPILED -> LOADED -> ENABLED -> DISABLED`。
- 事件桥：将 Bukkit/Paper 事件绑定到编译生成 handler 表。
- 调度：区分 `sync/async`，执行时验证效果约束。
- 卸载：解除监听、取消任务、释放模块局部资源。

### 3.3 集成层
- PAPI：声明式 namespace + placeholder 注册与注销。
- LuckPerms：读能力默认允许；写能力默认危险。
- SPI：允许独立桥接包向语言注册类型/函数/事件。

### 3.4 Agent 系统
- 工具集：`list/read/search/create/patch/compile/load/unload/run/rollback`。
- 变更策略：AST 优先，文本兜底需显式标记风险。
- 会话输出：统一结构体（需求、变更文件、编译结果、加载结果、回滚点）。

### 3.5 安全与审计
- 权限模型：`dynamicd.agent.*`、`dynamicd.repl`、模块操作节点。
- 危险操作：命令执行、权限写入、模块删除、核心配置变更。
- 审计字段：`requestId`、`operator`、`action`、`target`、`decision`、`timestamp`、`snapshotId`。

## 4. 分阶段实施
### Phase 1（MVP）
覆盖需求：`PLG P0`、`YUZ P0(基础)`、`RT P0(基础)`、`OPS P0(基础)`。

交付项：
1. 模块加载/卸载/重载命令与生命周期框架。
2. 基础语法（变量、函数、事件、命令、every/after）。
3. 编译链最小闭环与结构化诊断。
4. 热重载与任务清理。
5. 快照创建/回滚基础能力。

退出准则：
- 可实现 welcome 模块示例并通过端到端验收。

### Phase 2（可用版）
覆盖需求：`INT P0/P1`、`AGT P0(核心)`、`RPL P0`、`SEC P0`。

交付项：
1. PlaceholderAPI/LuckPerms 桥接。
2. Agent 工具化流程（生成、补丁、编译、加载、回滚）。
3. REPL 会话隔离与超时。
4. 危险操作确认和审计全链路。

退出准则：
- 管理员可通过 Agent 完成一个需求从描述到上线。

### Phase 3（成熟版）
覆盖需求：`YUZ P1`、`RT P1`、`AGT P1`、`SEC P1`。

交付项：
1. 增量编译与更强诊断建议。
2. AST Patch 稳定化与冲突修复策略。
3. 性能优化（谓词编译、缓存、热点路径）。
4. 扩展 SPI 生态化与更多桥接包。

退出准则：
- 高并发场景下稳定运行并具备可追踪性能指标。

## 5. 测试与验收策略
- 单元测试：词法、语法、类型、效果检查、序列化。
- 集成测试：模块生命周期、事件触发、命令执行、热重载。
- 合约测试：PAPI/LuckPerms 桥接接口与降级路径。
- 安全测试：权限绕过、危险命令确认、未授权拒绝。
- 回归测试：以 `DD-*` -> `AT-*` 映射驱动，每次发布自动跑 P0 全量。

## 6. 风险与缓解
- 风险：语言特性过多导致 MVP 失控。  
  缓解：严格按 `P0` 子集先落地，P1/P2 延后。
- 风险：Agent 自动补丁破坏语义。  
  缓解：AST 优先 + 编译闸门 + 快照回滚强制。
- 风险：线程/效果违规造成主线程阻塞。  
  缓解：效果检查 + REPL/Agent 超时与熔断。

## 7. 交付清单
- 规范：`docs/spec/dynamicd-spec.md`
- 需求：`docs/requirements/dynamicd-ears.md`
- 计划：本文件
- 协作治理：仓库根 `AGENTS.md`

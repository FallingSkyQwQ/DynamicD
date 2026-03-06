# DynamicD 主规范（v0.1）

## 1. 概述
DynamicD 是运行于 Paper `1.21.11+` 的插件内平台，提供：

1. `yuz` 强类型、预编译、事件优先语言。
2. 模块化运行时（加载、卸载、热重载、隔离执行）。
3. 受控 Agent 能力（生成、补丁、编译、加载、回滚、审计）。
4. REPL 与运维控制面（调试、诊断、状态检查）。

本规范基于 `DD.txt` 与 `yuz.txt` 统一整理，采用规范性关键词：

- `MUST`：必须。
- `SHOULD`：应当，除非有明确替代方案。
- `MAY`：可选能力。

## 2. 兼容与边界
- 宿主平台 `MUST` 为 Paper `1.21.11+`，Java `21`。
- 宿主实现 `MUST` 优先使用 Paper/Bukkit 官方 API，`MUST NOT` 依赖 NMS 作为核心路径。
- DynamicD `MUST` 支持插件可选集成：PlaceholderAPI、LuckPerms、Vault。
- 若集成插件不存在，相关桥接能力 `MUST` 退化为显式禁用或诊断提示，不得导致主系统崩溃。

## 3. 系统上下文
DynamicD 由以下子系统组成：

- 编译链：`lexer -> parser -> AST -> typer/effect checker -> IR/codegen -> diagnostics`。
- 运行时：`vm/executor + scheduler + event bridge + module manager + sandbox`。
- 集成层：`papi`、`luckperms`、`vault`、可扩展桥接 SPI。
- Agent 层：任务规划、结构化补丁、工具调用、权限守卫、会话与审计。
- REPL：控制台、游戏内、scratch 单元执行。
- 存储：配置、KV/持久化状态、编译缓存、快照。

## 4. 目录与工件
运行目录（`plugins/DynamicD/`）应包含：

- `config.yml`：运行时、Agent、REPL、集成开关。
- `modules/<module>/`：`mod.yuz`、子文件、`module.yml`（可选）。
- `cache/*.dyc`：编译产物。
- `workspace/agent`：Agent 工作区。
- `logs/`：编译、Agent、REPL 日志。
- `data/`：KV 与快照。

## 5. 生命周期
### 5.1 插件生命周期
- `onLoad`：版本检查、编译器初始化、集成探测。
- `onEnable`：事件桥挂载、模块恢复、Agent 与 REPL 启动。
- `onDisable`：模块有序卸载、任务取消、状态落盘、注册清理。

### 5.2 模块生命周期
模块应具备阶段：
`prepare -> compile -> load -> enable -> disable -> reload`。

模块 `MUST` 支持 `on load/on enable/on disable` 生命周期钩子。

## 6. yuz 语言规范落地摘要
详见 `yuz.txt`，此处定义宿主落实要求：

- `.yuz` 源文件 `MUST` 在加载前完成词法、语法、名称解析、类型检查、效果检查。
- 类型系统 `MUST` 默认非空，`T?`、`Option<T>` 语义区分明确。
- 事件、命令、调度、权限声明 `MUST` 进入可查询注册表。
- 编译与运行诊断 `MUST` 提供错误码、位置、期望/实际、建议修复。
- 宿主扩展语法 `MAY` 增加能力，但 `MUST` 不破坏核心 EBNF 兼容。

## 7. 插件互操作规范
### 7.1 PlaceholderAPI
- 系统 `MUST` 支持声明式 placeholder 注册（如 `papi namespace`）。
- 未安装 PAPI 时，编译或加载阶段 `MUST` 提示可操作错误，禁止静默失败。

### 7.2 LuckPerms
- 系统 `MUST` 支持权限查询、授予、元数据读写的受控桥接。
- 权限写操作 `MUST` 归类为危险能力并受授权策略约束。

### 7.3 命令执行
- Agent/REPL 命令执行 `MUST` 区分普通与危险命令。
- 危险命令 `MUST` 触发二次确认并记录审计。

## 8. Agent 规范摘要
- Agent `MUST` 通过工具链执行，不得直接绕过流程写入。
- 默认补丁顺序 `MUST` 为：AST Patch -> 结构化 Token Patch -> 文本补丁兜底。
- 每次变更 `MUST` 产生回滚点与变更摘要（文件、行为、风险、回滚指令）。
- Agent 权限节点 `MUST` 最小化授权（`dynamicd.agent.*`）。

## 9. REPL 规范摘要
- REPL `MUST` 会话隔离、超时控制、权限门禁。
- 默认仅 `OP` 或 `dynamicd.repl` 可用。
- 主线程危险操作 `MUST` 明确确认。

## 10. 安全与审计
- 沙箱分级：`SAFE/TRUSTED/ADMIN/SYSTEM`。
- 高危能力（权限写入、外部命令、核心模块变更）`MUST` 进行授权检查。
- 审计日志 `MUST` 至少记录：操作者、时间、动作、目标、结果、回滚点、关联请求。

## 11. 性能与可靠性
- 高频事件 `SHOULD` 编译为快速谓词并支持节流。
- 模块错误 `MUST` 局部隔离，单模块故障不得拖垮宿主。
- 调度任务 `MUST` 在模块卸载时自动取消。

## 12. 运维接口
`/dd` 命令面至少包括：

- 模块：`modules/load/unload/reload/compile/diag`
- Agent：`agent`、`agent patch`
- 快照：`snapshot create/rollback`
- 工具：`repl`、`papi list`、`perms sync`

## 13. 可追踪性
本规范由 EARS 需求文档实现可追踪约束：

- 需求文档：`docs/requirements/dynamicd-ears.md`
- 实施文档：`docs/design/implementation-plan.md`
- Agent 协作规则：仓库根 `AGENTS.md`

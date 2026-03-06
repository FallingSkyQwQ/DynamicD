# DynamicD 完整 EARS 需求（v0.1）

## 1. 约定
- 需求 ID：`DD-<子系统>-<序号>`。
- 验收 ID：`AT-<子系统>-<序号>`。
- 优先级：`P0`（必须）/`P1`（应有）/`P2`（可延后）。
- 子系统：`PLG` 插件平台，`YUZ` 语言编译，`RT` 运行时，`INT` 集成，`AGT` Agent，`RPL` REPL，`SEC` 安全审计，`OPS` 运维。

EARS 模板：
- Ubiquitous: `系统应始终...`
- Event-driven: `当 <事件> 发生时，系统应...`
- State-driven: `当处于 <状态> 时，系统应...`
- Optional-feature: `若启用 <功能>，系统应...`
- Unwanted behavior: `若发生 <异常>，系统应...`

## 2. 插件平台（PLG）
### DD-PLG-001 (P0, Ubiquitous)
系统应始终在启动前校验 Java 与 Paper 版本兼容性。  
验收：`AT-PLG-001` 不满足版本时拒绝启用并给出结构化诊断。

### DD-PLG-002 (P0, Event-driven)
当插件进入 `onLoad` 时，系统应初始化编译器、缓存与集成探测器。  
验收：`AT-PLG-002` 启动日志含初始化阶段与耗时。

### DD-PLG-003 (P0, Event-driven)
当插件进入 `onEnable` 时，系统应恢复可用模块并挂载事件桥。  
验收：`AT-PLG-003` 重启后已启用模块自动恢复。

### DD-PLG-004 (P0, Event-driven)
当插件进入 `onDisable` 时，系统应按依赖顺序卸载模块并取消任务。  
验收：`AT-PLG-004` 停服后无残留计划任务或重复监听器。

### DD-PLG-005 (P1, Optional-feature)
若启用 `compile-on-startup`，系统应在模块恢复前完成预编译。  
验收：`AT-PLG-005` 编译失败模块不进入 enable 状态。

## 3. yuz 语言与编译（YUZ）
### DD-YUZ-001 (P0, Ubiquitous)
系统应始终在加载前完成词法、语法、名称解析、类型与效果检查。  
验收：`AT-YUZ-001` 任一阶段失败时阻止加载并产出错误码。

### DD-YUZ-002 (P0, Ubiquitous)
系统应始终保证 `yuz` 默认非空类型规则。  
验收：`AT-YUZ-002` 对 `T?` 未判空访问时报编译错误。

### DD-YUZ-003 (P0, Ubiquitous)
系统应始终支持事件、命令、调度、权限声明的编译注册。  
验收：`AT-YUZ-003` 编译产物含对应注册表项。

### DD-YUZ-004 (P1, State-driven)
当模块只变更部分文件时，系统应执行增量编译并复用未变更产物。  
验收：`AT-YUZ-004` 增量编译耗时低于全量且结果一致。

### DD-YUZ-005 (P0, Unwanted behavior)
若语义检查发现线程效果不兼容，系统应拒绝编译并提示修复建议。  
验收：`AT-YUZ-005` 在 async 调用 sync-only API 时报错。

### DD-YUZ-006 (P1, Ubiquitous)
系统应始终产出结构化诊断（错误码、位置、期望/实际、建议）。  
验收：`AT-YUZ-006` 诊断字段完整且可供 Agent 自动修复。

## 4. 运行时（RT）
### DD-RT-001 (P0, Ubiquitous)
系统应始终支持模块生命周期 `prepare/compile/load/enable/disable/reload`。  
验收：`AT-RT-001` 生命周期事件按顺序触发。

### DD-RT-002 (P0, Event-driven)
当模块被热重载时，系统应原子替换事件绑定与命令注册。  
验收：`AT-RT-002` 重载后无重复监听和重复命令。

### DD-RT-003 (P0, Event-driven)
当模块卸载时，系统应自动取消其调度任务。  
验收：`AT-RT-003` 卸载后任务不再执行。

### DD-RT-004 (P0, Unwanted behavior)
若单模块运行时异常，系统应隔离故障并保持宿主继续运行。  
验收：`AT-RT-004` 其余模块功能不受影响。

### DD-RT-005 (P1, State-driven)
当模块包含 `persist` 状态时，系统应在重载后恢复兼容数据。  
验收：`AT-RT-005` 状态跨重启保持一致。

## 5. 插件集成（INT）
### DD-INT-001 (P0, Optional-feature)
若检测到 PlaceholderAPI，系统应注册声明式 placeholder。  
验收：`AT-INT-001` `/papi parse` 可读到 `dd_*` 值。

### DD-INT-002 (P0, Optional-feature)
若检测到 LuckPerms，系统应提供查询/授予/元数据桥接。  
验收：`AT-INT-002` `luckperms.has/grant/meta` 行为符合策略。

### DD-INT-003 (P0, Unwanted behavior)
若集成插件缺失，系统应给出可操作诊断并降级。  
验收：`AT-INT-003` 缺失集成不导致 DynamicD 启动失败。

### DD-INT-004 (P1, Optional-feature)
若启用桥接扩展 SPI，系统应支持第三方桥接包安全注册。  
验收：`AT-INT-004` 扩展包可注册类型/函数/事件。

## 6. Agent（AGT）
### DD-AGT-001 (P0, Ubiquitous)
系统应始终要求 Agent 通过显式工具链执行任务。  
验收：`AT-AGT-001` 审计中可追踪 `read/patch/compile/load` 调用链。

### DD-AGT-002 (P0, Event-driven)
当管理员提交自然语言需求时，系统应生成变更计划并可执行补丁流程。  
验收：`AT-AGT-002` 输出包含需求理解、变更清单、风险与回滚点。

### DD-AGT-003 (P0, Event-driven)
当 Agent 修改模块后，系统应自动编译并在成功后热加载。  
验收：`AT-AGT-003` 编译失败则禁止加载并返回诊断。

### DD-AGT-004 (P0, Ubiquitous)
系统应始终优先 AST 补丁，其次结构化 Token 补丁，最后文本兜底。  
验收：`AT-AGT-004` 审计记录补丁策略级别。

### DD-AGT-005 (P0, State-driven)
当 Agent 执行危险命令或高危改动时，系统应要求二次确认。  
验收：`AT-AGT-005` 未确认时命令不执行。

### DD-AGT-006 (P1, Unwanted behavior)
若 Agent 变更导致模块不可用，系统应支持一键回滚最近快照。  
验收：`AT-AGT-006` 回滚后恢复到上一个可用版本。

### DD-AGT-007 (P0, Ubiquitous)
系统应始终基于最小权限节点控制 Agent 功能范围。  
验收：`AT-AGT-007` 缺失节点时拒绝对应工具动作。

## 7. REPL（RPL）
### DD-RPL-001 (P0, Optional-feature)
若启用 REPL，系统应提供会话隔离、超时和权限门禁。  
验收：`AT-RPL-001` 不同会话互不污染且超时可回收。

### DD-RPL-002 (P0, State-driven)
当执行主线程敏感操作时，系统应弹出明确确认。  
验收：`AT-RPL-002` 未确认操作不生效。

### DD-RPL-003 (P1, Unwanted behavior)
若 REPL 执行超时或阻塞，系统应中断执行并记录诊断。  
验收：`AT-RPL-003` 超时后宿主线程无长期阻塞。

## 8. 安全与审计（SEC）
### DD-SEC-001 (P0, Ubiquitous)
系统应始终按 `SAFE/TRUSTED/ADMIN/SYSTEM` 沙箱等级执行能力检查。  
验收：`AT-SEC-001` 低等级不可调用高危接口。

### DD-SEC-002 (P0, Ubiquitous)
系统应始终对权限写入、命令执行、模块删除进行审计。  
验收：`AT-SEC-002` 审计日志含操作者、目标、结果、时间、回滚点。

### DD-SEC-003 (P0, Unwanted behavior)
若发生未授权访问，系统应拒绝动作并给出拒绝原因。  
验收：`AT-SEC-003` 返回权限节点与缺失详情。

### DD-SEC-004 (P1, Ubiquitous)
系统应始终支持资源限制（CPU 步数、内存、任务数、IO 配额）。  
验收：`AT-SEC-004` 超限后触发熔断与告警。

## 9. 运维与可观测（OPS）
### DD-OPS-001 (P0, Ubiquitous)
系统应始终提供 `/dd modules/load/unload/reload/compile/diag`。  
验收：`AT-OPS-001` 命令权限与行为符合定义。

### DD-OPS-002 (P0, Event-driven)
当执行 `/dd snapshot create` 时，系统应生成可回滚快照。  
验收：`AT-OPS-002` 快照可列出且具唯一 ID。

### DD-OPS-003 (P0, Event-driven)
当执行 `/dd snapshot rollback <id>` 时，系统应恢复对应状态并重载。  
验收：`AT-OPS-003` 回滚后行为与快照一致。

### DD-OPS-004 (P1, Ubiquitous)
系统应始终输出编译、运行时、Agent、REPL 的结构化日志。  
验收：`AT-OPS-004` 支持按模块/请求 ID 检索。

## 10. 需求覆盖与追踪
- `DD-PLG-*` 对应主规范第 2/5/12 节。
- `DD-YUZ-*` 对应主规范第 6 节与 `yuz.txt` 章节 4-34。
- `DD-RT-*` 对应主规范第 5/11 节。
- `DD-INT-*` 对应主规范第 7 节。
- `DD-AGT-*` 对应主规范第 8 节。
- `DD-RPL-*` 对应主规范第 9 节。
- `DD-SEC-*` 对应主规范第 10 节。
- `DD-OPS-*` 对应主规范第 12 节。

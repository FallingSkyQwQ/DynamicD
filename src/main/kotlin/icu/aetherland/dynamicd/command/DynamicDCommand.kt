package icu.aetherland.dynamicd.command

import icu.aetherland.dynamicd.agent.AgentToolAction
import icu.aetherland.dynamicd.agent.loop.AgentService
import icu.aetherland.dynamicd.integration.LuckPermsBridge
import icu.aetherland.dynamicd.integration.PlaceholderRegistrar
import icu.aetherland.dynamicd.integration.spi.ExtensionSnapshot
import icu.aetherland.dynamicd.module.ModuleManager
import icu.aetherland.dynamicd.repl.ReplEvaluator
import icu.aetherland.dynamicd.repl.ReplSessionManager
import icu.aetherland.dynamicd.security.ConfirmationManager
import icu.aetherland.dynamicd.security.DangerousActionGuard
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.time.Duration

class DynamicDCommand(
    private val moduleManager: ModuleManager,
    private val dangerousActionGuard: DangerousActionGuard,
    private val confirmationManager: ConfirmationManager,
    private val agentService: AgentService,
    private val replSessionManager: ReplSessionManager,
    private val replEvaluator: ReplEvaluator,
    private val placeholderBridge: PlaceholderRegistrar,
    private val luckPermsBridge: LuckPermsBridge,
    private val extensionSnapshotProvider: () -> ExtensionSnapshot,
) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("/dd modules <list|load|unload|reload|compile|diag|graph>")
            sender.sendMessage("/dd snapshot <list|create|rollback>")
            sender.sendMessage("/dd agent <prompt...>")
            sender.sendMessage("/dd repl <open|exec|close>")
            sender.sendMessage("/dd papi list")
            sender.sendMessage("/dd perms sync")
            sender.sendMessage("/dd doctor")
            sender.sendMessage("/dd confirm <token>")
            return true
        }
        val operator = sender.name
        val permissions = collectPermissions(sender)
        return when (args[0].lowercase()) {
            "modules" -> handleModules(sender, operator, permissions, args.drop(1))
            "snapshot" -> handleSnapshot(sender, operator, permissions, args.drop(1))
            "agent" -> handleAgent(sender, operator, permissions, args.drop(1))
            "repl" -> handleRepl(sender, args.drop(1))
            "papi" -> handlePapi(sender, args.drop(1))
            "perms" -> handlePerms(sender, args.drop(1))
            "doctor" -> handleDoctor(sender)
            "confirm" -> handleConfirm(sender, operator, permissions, args.drop(1))
            else -> {
                sender.sendMessage("Unknown subcommand")
                true
            }
        }
    }

    private fun handleModules(
        sender: CommandSender,
        operator: String,
        permissions: Set<String>,
        args: List<String>,
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /dd modules <list|load|unload|reload|compile|diag|graph>")
            return true
        }
        return when (args[0].lowercase()) {
            "list" -> {
                val modules = moduleManager.listModules()
                sender.sendMessage("Modules: " + modules.joinToString(", ") { "${it.id}(${it.state})" })
                true
            }
            "load" -> withModuleArg(sender, args) { moduleId ->
                sender.sendMessage("load $moduleId => ${moduleManager.loadModule(moduleId, operator, permissions)}")
            }
            "unload" -> withModuleArg(sender, args) { moduleId ->
                sender.sendMessage("unload $moduleId => ${moduleManager.unloadModule(moduleId, operator)}")
            }
            "reload" -> withModuleArg(sender, args) { moduleId ->
                sender.sendMessage("reload $moduleId => ${moduleManager.reloadModule(moduleId, operator, permissions)}")
            }
            "compile" -> withModuleArg(sender, args) { moduleId ->
                val result = moduleManager.compileModule(moduleId, operator, permissions)
                sender.sendMessage(
                    "compile $moduleId => ${result.success} mode=${result.metrics.mode} " +
                        "compiled=${result.metrics.filesCompiled} reused=${result.metrics.filesReused} " +
                        "predicates=${result.metrics.compiledPredicates} throttles=${result.metrics.throttledEvents} " +
                        "ms=${result.metrics.totalMillis}",
                )
                result.diagnostics.forEach {
                    sender.sendMessage("[${it.code}] ${it.message} (${it.file}:${it.line}:${it.column})")
                }
            }
            "diag" -> withModuleArg(sender, args) { moduleId ->
                val result = moduleManager.compileModule(moduleId, operator, permissions)
                result.diagnostics.forEach {
                    sender.sendMessage("[${it.code}] ${it.message} expected=${it.expected} actual=${it.actual} suggestion=${it.suggestion}")
                }
                if (result.diagnostics.isEmpty()) {
                    sender.sendMessage("No diagnostics")
                }
            }
            "graph" -> {
                val graph = moduleManager.moduleDependencyGraph()
                val order = moduleManager.moduleLoadOrder()
                sender.sendMessage("LoadOrder: ${order.joinToString(" -> ")}")
                if (graph.isEmpty()) {
                    sender.sendMessage("No modules")
                    return true
                }
                graph.forEach { (module, deps) ->
                    sender.sendMessage("graph $module -> ${if (deps.isEmpty()) "[]" else deps.joinToString(",")}")
                }
                true
            }
            else -> {
                sender.sendMessage("Unknown modules action")
                true
            }
        }
    }

    private fun handleSnapshot(
        sender: CommandSender,
        operator: String,
        permissions: Set<String>,
        args: List<String>,
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /dd snapshot <list|create|rollback>")
            return true
        }
        return when (args[0].lowercase()) {
            "list" -> {
                val snapshots = moduleManager.listSnapshots()
                sender.sendMessage(if (snapshots.isEmpty()) "No snapshots" else "Snapshots: ${snapshots.joinToString(", ")}")
                true
            }
            "create" -> {
                val id = moduleManager.createSnapshot(operator, permissions)
                sender.sendMessage(if (id == null) "snapshot create denied" else "snapshot created: $id")
                true
            }
            "rollback" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /dd snapshot rollback <id> [--confirm]")
                    return true
                }
                val snapshotId = args[1]
                val confirmed = args.contains("--confirm")
                if (!dangerousActionGuard.isConfirmed(sender, confirmed)) {
                    val conf = confirmationManager.create(operator, "snapshot.rollback", snapshotId)
                    sender.sendMessage("Rollback pending confirmation token=${conf.token}. Run /dd confirm ${conf.token}")
                    return true
                }
                sender.sendMessage("rollback $snapshotId => ${moduleManager.rollback(snapshotId, operator, permissions)}")
                true
            }
            else -> {
                sender.sendMessage("Unknown snapshot action")
                true
            }
        }
    }

    private fun handleAgent(
        sender: CommandSender,
        operator: String,
        permissions: Set<String>,
        args: List<String>,
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /dd agent <prompt...> | /dd agent run <cmd> [--confirm]")
            return true
        }

        if (args[0].equals("run", ignoreCase = true)) {
            if (args.size < 2) {
                sender.sendMessage("Usage: /dd agent run <cmd> [--confirm]")
                return true
            }
            val confirmed = args.contains("--confirm")
            val cmd = args.filterNot { it == "--confirm" }.drop(1).joinToString(" ")
            if (!dangerousActionGuard.isConfirmed(sender, confirmed)) {
                val conf = confirmationManager.create(operator, "agent.run", cmd)
                sender.sendMessage("Dangerous command pending token=${conf.token}. Run /dd confirm ${conf.token}")
                return true
            }
            sender.sendMessage("agent run => ${moduleManager.runDangerousCommand(cmd, operator, permissions).allowed}")
            return true
        }

        val prompt = args.joinToString(" ")
        val result = agentService.runPrompt(operator, permissions, prompt)
        sender.sendMessage("[Agent] requestId=${result.requestId} success=${result.success}")
        result.events.takeLast(8).forEach { event ->
            sender.sendMessage("[${event.type}] ${event.message}")
        }
        sender.sendMessage("[Summary] ${result.summary}")
        return true
    }

    private fun handleRepl(sender: CommandSender, args: List<String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /dd repl <open|exec|close>")
            return true
        }
        if (!sender.hasPermission("dynamicd.repl") && !sender.isOp) {
            sender.sendMessage("Missing permission dynamicd.repl")
            return true
        }
        return when (args[0].lowercase()) {
            "open" -> {
                val session = replSessionManager.open(sender.name)
                sender.sendMessage("repl opened session=${session.sessionId}")
                true
            }
            "exec" -> {
                if (args.size < 2) {
                    sender.sendMessage("Usage: /dd repl exec <code>")
                    return true
                }
                val session = replSessionManager.get(sender.name)
                if (session == null) {
                    sender.sendMessage("No active REPL session, run /dd repl open")
                    return true
                }
                val input = args.drop(1).joinToString(" ")
                if (isSensitiveReplInput(input)) {
                    val conf = confirmationManager.create(sender.name, "repl.exec", input)
                    sender.sendMessage("Sensitive REPL input pending token=${conf.token}. Run /dd confirm ${conf.token}")
                    return true
                }
                sender.sendMessage(replEvaluator.eval(session, input))
                true
            }
            "close" -> {
                sender.sendMessage("repl close => ${replSessionManager.close(sender.name)}")
                true
            }
            else -> {
                sender.sendMessage("Usage: /dd repl <open|exec|close>")
                true
            }
        }
    }

    private fun handlePapi(sender: CommandSender, args: List<String>): Boolean {
        if (args.firstOrNull()?.equals("list", ignoreCase = true) != true) {
            sender.sendMessage("Usage: /dd papi list")
            return true
        }
        val keys = placeholderBridge.listRegisteredKeys()
        sender.sendMessage(if (keys.isEmpty()) "No placeholders" else "Placeholders: ${keys.joinToString(", ")}")
        return true
    }

    private fun handlePerms(sender: CommandSender, args: List<String>): Boolean {
        if (args.firstOrNull()?.equals("sync", ignoreCase = true) != true) {
            sender.sendMessage("Usage: /dd perms sync")
            return true
        }
        if (sender is Player) {
            val has = luckPermsBridge.has(sender, "dynamicd.ops")
            sender.sendMessage("luckperms.has(dynamicd.ops)=$has")
            val metaSet = luckPermsBridge.setMeta(sender.uniqueId, "dd-sync", "ok")
            sender.sendMessage("luckperms.meta_set=$metaSet")
            val grant = luckPermsBridge.grant(sender.uniqueId, "default", Duration.ofMinutes(10))
            sender.sendMessage("luckperms.grant(default,10m)=$grant")
        } else {
            sender.sendMessage("Run /dd perms sync in-game for bridge verification")
        }
        return true
    }

    private fun handleDoctor(sender: CommandSender): Boolean {
        val modules = moduleManager.listModules()
        sender.sendMessage("doctor modules=${modules.size} enabled=${modules.count { it.state.name == "ENABLED" }}")
        moduleManager.integrationDiagnostics().forEach {
            sender.sendMessage("doctor integration=${it.integration} available=${it.available} enabled=${it.enabled} msg=${it.message}")
        }
        val ext = extensionSnapshotProvider()
        sender.sendMessage(
            "doctor extensions=${ext.extensions.size} types=${ext.types.size} functions=${ext.functions.size} events=${ext.events.size}",
        )
        return true
    }

    private fun handleConfirm(
        sender: CommandSender,
        operator: String,
        permissions: Set<String>,
        args: List<String>,
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("Usage: /dd confirm <token>")
            return true
        }
        val token = args[0]
        val pending = confirmationManager.consume(operator, token)
        if (pending == null) {
            sender.sendMessage("Invalid or expired token")
            return true
        }
        when (pending.action) {
            "agent.run" -> {
                sender.sendMessage("agent run (confirmed) => ${moduleManager.runDangerousCommand(pending.payload, operator, permissions).allowed}")
            }
            "snapshot.rollback" -> {
                sender.sendMessage("rollback (confirmed) => ${moduleManager.rollback(pending.payload, operator, permissions)}")
            }
            "repl.exec" -> {
                val session = replSessionManager.get(operator)
                if (session == null) {
                    sender.sendMessage("No active REPL session")
                } else {
                    sender.sendMessage(replEvaluator.eval(session, pending.payload))
                }
            }
            else -> sender.sendMessage("Unknown confirmation action")
        }
        return true
    }

    private fun collectPermissions(sender: CommandSender): Set<String> {
        val all = mutableSetOf<String>()
        AgentToolAction.entries.forEach { action ->
            val node = when (action) {
                AgentToolAction.READ,
                AgentToolAction.SEARCH,
                -> "dynamicd.agent.use"
                AgentToolAction.CREATE -> "dynamicd.agent.codegen"
                AgentToolAction.PATCH -> "dynamicd.agent.patch"
                AgentToolAction.COMPILE -> "dynamicd.agent.compile"
                AgentToolAction.LOAD,
                AgentToolAction.UNLOAD,
                -> "dynamicd.agent.load"
                AgentToolAction.RUN -> "dynamicd.agent.command"
                AgentToolAction.ROLLBACK -> "dynamicd.agent.rollback"
            }
            if (sender.hasPermission(node)) {
                all.add(node)
            }
        }
        if (sender.hasPermission("dynamicd.agent.command.dangerous")) {
            all.add("dynamicd.agent.command.dangerous")
        }
        if (sender.hasPermission("dynamicd.ops")) {
            all.addAll(
                setOf(
                    "dynamicd.agent.use",
                    "dynamicd.agent.codegen",
                    "dynamicd.agent.patch",
                    "dynamicd.agent.compile",
                    "dynamicd.agent.load",
                    "dynamicd.agent.command",
                    "dynamicd.agent.command.dangerous",
                    "dynamicd.agent.rollback",
                ),
            )
        }
        return all
    }

    private fun withModuleArg(sender: CommandSender, args: List<String>, op: (String) -> Unit): Boolean {
        if (args.size < 2) {
            sender.sendMessage("Module id required")
            return true
        }
        op(args[1])
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): MutableList<String> {
        if (args.size == 1) {
            return mutableListOf("modules", "snapshot", "agent", "repl", "papi", "perms", "doctor", "confirm")
                .filter { it.startsWith(args[0], ignoreCase = true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].equals("modules", ignoreCase = true)) {
            return mutableListOf("list", "load", "unload", "reload", "compile", "diag", "graph")
                .filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].equals("snapshot", ignoreCase = true)) {
            return mutableListOf("list", "create", "rollback")
                .filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].equals("repl", ignoreCase = true)) {
            return mutableListOf("open", "exec", "close")
                .filter { it.startsWith(args[1], ignoreCase = true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].equals("papi", ignoreCase = true)) {
            return mutableListOf("list").filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
        }
        if (args.size == 2 && args[0].equals("perms", ignoreCase = true)) {
            return mutableListOf("sync").filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
        }
        return mutableListOf()
    }

    private fun isSensitiveReplInput(input: String): Boolean {
        val lowered = input.lowercase()
        return lowered.contains("run ") || lowered.contains("rollback") || lowered.contains("grant")
    }
}

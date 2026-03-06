package icu.aetherland.dynamicd.agent

import icu.aetherland.dynamicd.audit.AuditLogger

enum class AgentToolAction {
    READ,
    SEARCH,
    CREATE,
    PATCH,
    COMPILE,
    LOAD,
    UNLOAD,
    RUN,
    ROLLBACK,
}

data class AuthorizationResult(val allowed: Boolean, val missingPermission: String? = null)

class AgentToolchain(private val auditLogger: AuditLogger) {
    companion object {
        val SYSTEM_PERMISSIONS = setOf(
            "dynamicd.agent.use",
            "dynamicd.agent.codegen",
            "dynamicd.agent.patch",
            "dynamicd.agent.compile",
            "dynamicd.agent.load",
            "dynamicd.agent.command",
            "dynamicd.agent.rollback",
        )
    }

    fun authorize(action: AgentToolAction, grantedPermissions: Set<String>): AuthorizationResult {
        val required = when (action) {
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
        return if (required in grantedPermissions) {
            AuthorizationResult(true)
        } else {
            AuthorizationResult(false, required)
        }
    }

    fun recordAction(
        operator: String,
        action: AgentToolAction,
        target: String,
        decision: String,
        snapshotId: String? = null,
    ) {
        auditLogger.record(
            operator = operator,
            action = action.name.lowercase(),
            target = target,
            decision = decision,
            snapshotId = snapshotId,
        )
    }

    fun recordPatchDecision(
        operator: String,
        target: String,
        decision: PatchDecision,
    ) {
        val message = buildString {
            append("strategy=")
            append(decision.actual.name)
            if (decision.downgradeReason != null) {
                append(" downgradeReason=")
                append(decision.downgradeReason)
            }
        }
        auditLogger.record(
            operator = operator,
            action = AgentToolAction.PATCH.name.lowercase(),
            target = target,
            decision = message,
        )
    }

    fun selectPatchStrategy(astAvailable: Boolean, tokenAvailable: Boolean): PatchDecision {
        return when {
            astAvailable -> PatchDecision(PatchStrategy.AST, PatchStrategy.AST)
            tokenAvailable -> PatchDecision(
                desired = PatchStrategy.AST,
                actual = PatchStrategy.TOKEN,
                downgradeReason = "AST patcher unavailable",
            )
            else -> PatchDecision(
                desired = PatchStrategy.AST,
                actual = PatchStrategy.TEXT,
                downgradeReason = "AST/TOKEN patchers unavailable",
            )
        }
    }
}

package icu.aetherland.dynamicd.agent

import icu.aetherland.dynamicd.audit.AuditLogger

enum class AgentToolAction {
    READ,
    SEARCH,
    PATCH,
    COMPILE,
    LOAD,
    ROLLBACK,
}

class AgentToolchain(private val auditLogger: AuditLogger) {
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

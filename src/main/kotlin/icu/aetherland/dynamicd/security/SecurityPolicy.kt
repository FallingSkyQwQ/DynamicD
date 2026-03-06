package icu.aetherland.dynamicd.security

enum class Capability {
    MODULE_READ,
    MODULE_PATCH,
    MODULE_COMPILE,
    MODULE_LOAD,
    MODULE_UNLOAD,
    SNAPSHOT_ROLLBACK,
    COMMAND_RUN,
    LUCKPERMS_WRITE,
    REPL_EXECUTE,
}

data class SecurityDecision(
    val allowed: Boolean,
    val missing: String? = null,
)

class SecurityPolicy {
    fun check(level: SandboxLevel, capability: Capability): SecurityDecision {
        val required = when (capability) {
            Capability.MODULE_READ,
            Capability.REPL_EXECUTE,
            -> SandboxLevel.SAFE
            Capability.MODULE_PATCH,
            Capability.MODULE_COMPILE,
            Capability.MODULE_LOAD,
            Capability.MODULE_UNLOAD,
            -> SandboxLevel.TRUSTED
            Capability.SNAPSHOT_ROLLBACK,
            Capability.COMMAND_RUN,
            Capability.LUCKPERMS_WRITE,
            -> SandboxLevel.ADMIN
        }
        return if (level.ordinal >= required.ordinal) {
            SecurityDecision(true)
        } else {
            SecurityDecision(false, "sandbox:$level lacks $capability (requires $required)")
        }
    }
}

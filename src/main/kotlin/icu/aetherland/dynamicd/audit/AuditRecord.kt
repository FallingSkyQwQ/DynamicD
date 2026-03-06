package icu.aetherland.dynamicd.audit

data class AuditRecord(
    val requestId: String,
    val operator: String,
    val action: String,
    val target: String,
    val decision: String,
    val timestamp: String,
    val snapshotId: String? = null,
)

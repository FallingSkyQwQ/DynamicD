package icu.aetherland.dynamicd.audit

import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

class AuditLogger(private val auditFile: File) {
    init {
        auditFile.parentFile.mkdirs()
        if (!auditFile.exists()) {
            auditFile.createNewFile()
        }
    }

    fun record(
        operator: String,
        action: String,
        target: String,
        decision: String,
        snapshotId: String? = null,
        requestId: String = UUID.randomUUID().toString(),
    ): AuditRecord {
        val record = AuditRecord(
            requestId = requestId,
            operator = operator,
            action = action,
            target = target,
            decision = decision,
            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            snapshotId = snapshotId,
        )
        append(record)
        return record
    }

    private fun append(record: AuditRecord) {
        val line = listOf(
            "requestId=${record.requestId}",
            "operator=${record.operator}",
            "action=${record.action}",
            "target=${record.target}",
            "decision=${record.decision}",
            "timestamp=${record.timestamp}",
            "snapshotId=${record.snapshotId ?: ""}",
        ).joinToString(" ")
        auditFile.appendText("$line\n")
    }
}

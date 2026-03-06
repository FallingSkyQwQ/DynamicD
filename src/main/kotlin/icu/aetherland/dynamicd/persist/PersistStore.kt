package icu.aetherland.dynamicd.persist

import java.io.File
import java.sql.DriverManager

data class PersistEntry(
    val moduleId: String,
    val key: String,
    val value: String,
    val schemaVersion: Int,
)

class PersistStore(dbFile: File) {
    private val url: String = "jdbc:sqlite:${dbFile.absolutePath}"

    init {
        dbFile.parentFile.mkdirs()
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS module_state (
                      module_id TEXT NOT NULL,
                      state_key TEXT NOT NULL,
                      value TEXT NOT NULL,
                      schema_version INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL,
                      PRIMARY KEY(module_id, state_key)
                    )
                    """.trimIndent(),
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS module_schema (
                      module_id TEXT PRIMARY KEY,
                      schema_version INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }

    fun getModuleState(moduleId: String): List<PersistEntry> {
        val sql = "SELECT module_id, state_key, value, schema_version FROM module_state WHERE module_id=?"
        val rows = mutableListOf<PersistEntry>()
        DriverManager.getConnection(url).use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, moduleId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        rows += PersistEntry(
                            moduleId = rs.getString(1),
                            key = rs.getString(2),
                            value = rs.getString(3),
                            schemaVersion = rs.getInt(4),
                        )
                    }
                }
            }
        }
        return rows
    }

    fun upsert(entry: PersistEntry) {
        val sql =
            "INSERT INTO module_state(module_id,state_key,value,schema_version,updated_at) VALUES(?,?,?,?,strftime('%s','now')) " +
                "ON CONFLICT(module_id,state_key) DO UPDATE SET value=excluded.value,schema_version=excluded.schema_version,updated_at=excluded.updated_at"
        DriverManager.getConnection(url).use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, entry.moduleId)
                ps.setString(2, entry.key)
                ps.setString(3, entry.value)
                ps.setInt(4, entry.schemaVersion)
                ps.executeUpdate()
            }
            conn.prepareStatement(
                "INSERT INTO module_schema(module_id,schema_version) VALUES(?,?) ON CONFLICT(module_id) DO UPDATE SET schema_version=excluded.schema_version",
            ).use { ps ->
                ps.setString(1, entry.moduleId)
                ps.setInt(2, entry.schemaVersion)
                ps.executeUpdate()
            }
        }
    }

    fun schemaVersion(moduleId: String): Int {
        val sql = "SELECT schema_version FROM module_schema WHERE module_id=?"
        DriverManager.getConnection(url).use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, moduleId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getInt(1)
                    }
                }
            }
        }
        return 0
    }

    fun migrateModule(moduleId: String, toVersion: Int, transform: (PersistEntry) -> PersistEntry) {
        val current = getModuleState(moduleId)
        current.forEach { entry ->
            val updated = transform(entry).copy(schemaVersion = toVersion)
            upsert(updated)
        }
    }
}

package icu.aetherland.dynamicd.compiler

sealed interface AstNode

data class AstModule(
    val moduleName: String?,
    val versionLiteral: String?,
    val declarations: List<AstDeclaration>,
) : AstNode

sealed interface AstDeclaration : AstNode

data class UseDeclaration(
    val path: String,
    val alias: String? = null,
) : AstDeclaration

data class FunctionDeclaration(
    val name: String,
    val exported: Boolean,
) : AstDeclaration

data class StateDeclaration(
    val name: String,
    val persistent: Boolean,
) : AstDeclaration

data class PlaceholderDeclaration(
    val namespace: String?,
    val key: String?,
) : AstDeclaration

data class EventDeclaration(
    val eventPath: String,
    val whereClause: String? = null,
    val throttleLiteral: String? = null,
) : AstDeclaration

data class CommandDeclaration(val raw: String) : AstDeclaration

data class PermissionDeclaration(val node: String) : AstDeclaration

data class TimerDeclaration(val timerType: String, val durationLiteral: String) : AstDeclaration

package icu.aetherland.dynamicd.compiler

sealed interface AstNode

data class AstModule(
    val moduleName: String?,
    val declarations: List<AstDeclaration>,
) : AstNode

sealed interface AstDeclaration : AstNode

data class EventDeclaration(val eventPath: String) : AstDeclaration

data class CommandDeclaration(val raw: String) : AstDeclaration

data class PermissionDeclaration(val node: String) : AstDeclaration

data class TimerDeclaration(val timerType: String, val durationLiteral: String) : AstDeclaration

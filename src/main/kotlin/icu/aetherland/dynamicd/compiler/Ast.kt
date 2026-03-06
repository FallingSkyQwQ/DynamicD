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

data class FunctionSignature(
    val name: String,
    val paramCount: Int,
    val returnType: String?,
)

data class FunctionDeclaration(
    val name: String,
    val exported: Boolean,
    val signature: FunctionSignature,
) : AstDeclaration

data class RecordDeclaration(
    val name: String,
) : AstDeclaration

data class EnumDeclaration(
    val name: String,
    val variants: List<String>,
) : AstDeclaration

data class TraitDeclaration(
    val name: String,
    val methods: List<FunctionSignature>,
) : AstDeclaration

data class ImplDeclaration(
    val traitName: String,
    val targetType: String,
    val methods: List<FunctionSignature>,
) : AstDeclaration

data class MatchDeclaration(
    val targetExpression: String,
    val hasElseBranch: Boolean,
    val caseCount: Int,
    val caseLabels: List<String>,
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

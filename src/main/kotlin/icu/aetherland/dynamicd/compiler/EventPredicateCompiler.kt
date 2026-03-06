package icu.aetherland.dynamicd.compiler

class EventPredicateCompiler {
    fun compile(whereClause: String): (Map<String, String>) -> Boolean {
        val expression = whereClause.trim()
        if (expression.isBlank()) {
            return { true }
        }
        val andParts = expression.split(Regex("\\s+and\\s+"))
        val terms = andParts.mapNotNull { parseTerm(it.trim()) }
        if (terms.isEmpty()) {
            return { true }
        }
        return { ctx -> terms.all { it(ctx) } }
    }

    private fun parseTerm(term: String): ((Map<String, String>) -> Boolean)? {
        val containsMatch = Regex("([\\w.]+)\\s+contains\\s+\"([^\"]+)\"").matchEntire(term)
        if (containsMatch != null) {
            val key = containsMatch.groupValues[1]
            val expected = containsMatch.groupValues[2]
            return { ctx -> (ctx[key] ?: "").contains(expected) }
        }

        val equalsMatch = Regex("([\\w.]+)\\s*(==|!=)\\s*\"?([^\"\\s]+)\"?").matchEntire(term)
        if (equalsMatch != null) {
            val key = equalsMatch.groupValues[1]
            val op = equalsMatch.groupValues[2]
            val expected = equalsMatch.groupValues[3]
            return { ctx ->
                val actual = ctx[key]
                if (op == "==") actual == expected else actual != expected
            }
        }
        return null
    }
}

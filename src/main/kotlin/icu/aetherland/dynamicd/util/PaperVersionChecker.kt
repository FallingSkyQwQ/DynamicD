package icu.aetherland.dynamicd.util

object PaperVersionChecker {
    fun isSupportedVersion(version: String, min: String = "1.21.11"): Boolean {
        return compare(version, min) >= 0
    }

    private fun compare(a: String, b: String): Int {
        val va = a.split(".").mapNotNull { it.toIntOrNull() }
        val vb = b.split(".").mapNotNull { it.toIntOrNull() }
        val max = maxOf(va.size, vb.size)
        for (i in 0 until max) {
            val ai = va.getOrElse(i) { 0 }
            val bi = vb.getOrElse(i) { 0 }
            if (ai != bi) {
                return ai.compareTo(bi)
            }
        }
        return 0
    }
}

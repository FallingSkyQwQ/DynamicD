package icu.aetherland.dynamicd.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaperVersionCheckerTest {
    @Test
    fun `supports minimum version`() {
        assertTrue(PaperVersionChecker.isSupportedVersion("1.21.11"))
        assertTrue(PaperVersionChecker.isSupportedVersion("1.21.12"))
    }

    @Test
    fun `rejects lower version`() {
        assertFalse(PaperVersionChecker.isSupportedVersion("1.21.10"))
    }
}

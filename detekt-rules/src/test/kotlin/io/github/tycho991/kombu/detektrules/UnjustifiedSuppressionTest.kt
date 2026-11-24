package io.github.tycho991.kombu.detektrules

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnjustifiedSuppressionTest {
    private val rule = UnjustifiedSuppression(Config.empty)

    private fun findings(code: String) = rule.lint(code.trimIndent())

    @Test
    fun `reports suppression without comment`() {
        val code =
            """
            @Suppress("MagicNumber")
            fun answer() = 42
            """
        assertEquals(1, findings(code).size)
    }

    @Test
    fun `reports SuppressWarnings without comment`() {
        val code =
            """
            @SuppressWarnings("unused")
            fun answer() = 42
            """
        assertEquals(1, findings(code).size)
    }

    @Test
    fun `accepts line comment directly above`() {
        val code =
            """
            // The value is defined by the specification.
            @Suppress("MagicNumber")
            fun answer() = 42
            """
        assertEquals(0, findings(code).size)
    }

    @Test
    fun `accepts block comment directly above`() {
        val code =
            """
            /* The value is defined by the specification. */
            @Suppress("MagicNumber")
            fun answer() = 42
            """
        assertEquals(0, findings(code).size)
    }

    @Test
    fun `accepts comment at the end of the same line`() {
        val code =
            """
            @Suppress("MagicNumber") // defined by the specification
            fun answer() = 42
            """
        assertEquals(0, findings(code).size)
    }

    @Test
    fun `rejects comment separated by a blank line`() {
        val code =
            """
            // Unrelated comment.

            @Suppress("MagicNumber")
            fun answer() = 42
            """
        assertEquals(1, findings(code).size)
    }

    @Test
    fun `rejects KDoc as justification`() {
        val code =
            """
            /** Returns the answer. */
            @Suppress("MagicNumber")
            fun answer() = 42
            """
        assertEquals(1, findings(code).size)
    }

    @Test
    fun `rejects comment on the following line`() {
        val code =
            """
            @Suppress("MagicNumber")
            // too late
            fun answer() = 42
            """
        assertEquals(1, findings(code).size)
    }

    @Test
    fun `accepts justified file-level suppression`() {
        val code =
            """
            // Generated-style constants, checked by the parser tests.
            @file:Suppress("MagicNumber")

            package example
            """
        assertEquals(0, findings(code).size)
    }

    @Test
    fun `reports unjustified suppression on a local declaration`() {
        val code =
            """
            fun cast(value: Any): List<String> {
                @Suppress("UNCHECKED_CAST")
                val list = value as List<String>
                return list
            }
            """
        assertEquals(1, findings(code).size)
    }

    @Test
    fun `ignores other annotations`() {
        val code =
            """
            @Deprecated("old")
            fun answer() = 42
            """
        assertEquals(0, findings(code).size)
    }

    @Test
    fun `reports at the last line of the file`() {
        assertEquals(1, findings("@Suppress(\"MagicNumber\") val answer = 42").size)
    }
}

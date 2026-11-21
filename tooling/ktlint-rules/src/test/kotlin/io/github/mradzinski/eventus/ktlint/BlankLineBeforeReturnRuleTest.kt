package io.github.mradzinski.eventus.ktlint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlankLineBeforeReturnRuleTest {

    private fun format(code: String) = format(code.trimIndent()) { BlankLineBeforeReturnRule() }

    @Test
    fun `adds a blank line before a return`() {
        val code =
            """
            fun f(): Int {
                print(1)
                return 1
            }
            """

        val expected =
            """
            fun f(): Int {
                print(1)

                return 1
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `adds a blank line before a labeled return in a lambda`() {
        val code =
            """
            fun f() {
                list.forEach {
                    print(it)
                    return@forEach
                }
            }
            """

        val expected =
            """
            fun f() {
                list.forEach {
                    print(it)

                    return@forEach
                }
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `puts the blank line before a comment above the return`() {
        val code =
            """
            fun f(): Int {
                print(1)
                // Done.
                return 1
            }
            """

        val expected =
            """
            fun f(): Int {
                print(1)

                // Done.
                return 1
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `leaves alone a first statement, returns inside expressions and returns on the same line`() {
        val code =
            """
            fun f(x: Int?): Int {
                if (x == 0) {
                    return 0
                }
                list.forEach { return@forEach }
                val y = x ?: return 1
                if (y < 0) return -1
                print(y); return y
            }
            """.trimIndent()

        assertTrue(lint(code) { BlankLineBeforeReturnRule() }.isEmpty())
    }
}

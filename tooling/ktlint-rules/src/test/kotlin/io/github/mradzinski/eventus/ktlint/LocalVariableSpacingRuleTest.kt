package io.github.mradzinski.eventus.ktlint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalVariableSpacingRuleTest {

    private fun format(code: String) = format(code.trimIndent()) { LocalVariableSpacingRule() }

    @Test
    fun `adds a blank line after a declaration followed by a call`() {
        val code =
            """
            fun f() {
                val a = 1
                print(a)
            }
            """

        val expected =
            """
            fun f() {
                val a = 1

                print(a)
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `removes blank lines between short consecutive declarations`() {
        val code =
            """
            fun f() {
                val a = 1

                var b = 2


                val (c, d) = 3 to 4

                print(a + b + c + d)
            }
            """

        val expected =
            """
            fun f() {
                val a = 1
                var b = 2
                val (c, d) = 3 to 4

                print(a + b + c + d)
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `sets a declaration that spans several lines apart from the ones around it`() {
        val code =
            """
            fun f() {
                val a = 1
                val b = listOf(1, 2)
                    .map { it + a }
                val c = 3

                print(a + b.size + c)
            }
            """

        val expected =
            """
            fun f() {
                val a = 1

                val b = listOf(1, 2)
                    .map { it + a }

                val c = 3

                print(a + b.size + c)
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `sets a declaration wider than a hundred columns apart from the ones around it`() {
        // One line wider than 100 columns, built here so this file's own lines stay short.
        val items = List(11) { "\"item\"" }.joinToString()

        val wide = "val wide = listOf($items)"

        val code =
            """
            fun f() {
                val a = 1
                $wide
                val c = 3

                print(a + wide.size + c)
            }
            """

        val expected =
            """
            fun f() {
                val a = 1

                $wide

                val c = 3

                print(a + wide.size + c)
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `sets a declaration with a comment above it apart from the ones around it`() {
        val code =
            """
            fun f() {
                val a = 1
                // Why b is two.
                val b = 2
                val c = 3 // three
                val d = 4

                print(a + b + c + d)
            }
            """

        val expected =
            """
            fun f() {
                val a = 1

                // Why b is two.
                val b = 2

                val c = 3 // three
                val d = 4

                print(a + b + c + d)
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `leaves alone declarations that are already set apart`() {
        val code =
            """
            fun f() {
                val a = 1
                val b = 2

                val c = listOf(a, b)
                    .map { it * 2 }

                // Why d is four.
                val d = 4

                print(c.size + d)
            }
            """.trimIndent()

        assertTrue(lint(code) { LocalVariableSpacingRule() }.isEmpty())
    }

    @Test
    fun `formats the example from the style guide`() {
        val code =
            """
            fun f() {
                launch {
                    toResolve.receiveAsFlow().collect { service ->
                        val resolved = resolveService(service) ?: return@collect

                        val address = resolved.host ?: return@collect
                        resolvedAddresses[normalize(resolved.serviceName)] = address

                        val authority = address.hostAddress
                        val host = DeviceHost.parse(authority) ?: return@collect
                        send(Candidate(resolved.serviceName, host))
                    }
                }
            }
            """

        val expected =
            """
            fun f() {
                launch {
                    toResolve.receiveAsFlow().collect { service ->
                        val resolved = resolveService(service) ?: return@collect
                        val address = resolved.host ?: return@collect

                        resolvedAddresses[normalize(resolved.serviceName)] = address

                        val authority = address.hostAddress
                        val host = DeviceHost.parse(authority) ?: return@collect

                        send(Candidate(resolved.serviceName, host))
                    }
                }
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `puts the blank line before a comment that leads the next statement`() {
        val code =
            """
            fun f() {
                val a = 1 // one
                // Print it.
                print(a)
            }
            """

        val expected =
            """
            fun f() {
                val a = 1 // one

                // Print it.
                print(a)
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `leaves alone the last statement, statements on one line and class properties`() {
        val code =
            """
            class C {
                val a = 1
                fun f() {
                    val b = 2; print(b)
                    run {
                        val c = 3
                    }
                }
            }
            """.trimIndent()

        assertTrue(lint(code) { LocalVariableSpacingRule() }.isEmpty())
    }

    @Test
    fun `reports what it would fix`() {
        val code =
            """
            fun f() {
                val a = 1

                val b = 2
                print(a + b)
            }
            """.trimIndent()

        val errors = lint(code) { LocalVariableSpacingRule() }

        assertEquals(
            listOf(
                2 to "Unexpected blank line between short variable declarations",
                4 to "Expected a blank line after a variable declaration",
            ),
            errors.map { it.line to it.detail },
        )
    }
}

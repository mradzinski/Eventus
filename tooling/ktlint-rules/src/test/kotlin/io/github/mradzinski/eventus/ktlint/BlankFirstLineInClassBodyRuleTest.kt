package io.github.mradzinski.eventus.ktlint

import kotlin.test.Test
import kotlin.test.assertEquals

class BlankFirstLineInClassBodyRuleTest {

    @Test
    fun `reports where the blank line is missing and says it can be fixed`() {
        val errors = lint("class A {\n    val x = 1\n\n    val y = 2\n}") { BlankFirstLineInClassBodyRule() }

        assertEquals(1, errors.size)
        assertEquals("Expected a blank line at the start of the class body", errors.single().detail)
        assertEquals(1, errors.single().line)
    }

    @Test
    fun `adds a blank line at the start of a class body`() {
        val code =
            """
            class A(val x: Int) {
                val y = x

                fun z() = y
            }
            """.trimIndent()
        val expected =
            """
            class A(val x: Int) {

                val y = x

                fun z() = y
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `adds a blank line after a header that spans several lines`() {
        val code =
            """
            class A(
                val x: Int,
                val y: Int,
            ) : B {
                val z = x

                val w = y
            }
            """.trimIndent()
        val expected =
            """
            class A(
                val x: Int,
                val y: Int,
            ) : B {

                val z = x

                val w = y
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `adds a blank line when the only member spans several lines`() {
        val code =
            """
            class A {
                fun z() {
                    print(1)
                }
            }
            """.trimIndent()
        val expected =
            """
            class A {

                fun z() {
                    print(1)
                }
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `adds a blank line in an interface an object and a companion object`() {
        val code =
            """
            interface I {
                val a: Int

                val b: Int
            }

            object O {
                val a = 1

                val b = 2
            }

            class C {

                companion object {
                    const val A = 1

                    const val B = 2
                }
            }
            """.trimIndent()
        val expected =
            """
            interface I {

                val a: Int

                val b: Int
            }

            object O {

                val a = 1

                val b = 2
            }

            class C {

                companion object {

                    const val A = 1

                    const val B = 2
                }
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `adds a blank line before the entries of an enum class`() {
        val code =
            """
            enum class E {
                A,
                B,
            }
            """.trimIndent()
        val expected =
            """
            enum class E {

                A,
                B,
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `adds a blank line in an anonymous object`() {
        val code =
            """
            val o = object : I {
                override val a = 1

                override val b = 2
            }
            """.trimIndent()
        val expected =
            """
            val o = object : I {

                override val a = 1

                override val b = 2
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `adds a blank line before a comment that starts the body`() {
        val code =
            """
            class A {
                // First.
                val a = 1
            }
            """.trimIndent()
        val expected =
            """
            class A {

                // First.
                val a = 1
            }
            """.trimIndent()

        assertEquals(expected, format(code))
    }

    @Test
    fun `leaves an empty body alone`() {
        val code =
            """
            class A {
            }
            """.trimIndent()

        assertEquals(code, format(code))
    }

    @Test
    fun `leaves a body with one short member alone`() {
        val code =
            """
            class A : B {
                override val x = 1
            }
            """.trimIndent()

        assertEquals(code, format(code))
    }

    @Test
    fun `leaves a body written on one line alone`() {
        val code =
            """
            class A { val x = 1 }
            """.trimIndent()

        assertEquals(code, format(code))
    }

    @Test
    fun `leaves an enum with one entry alone`() {
        val code =
            """
            enum class E {
                A,
            }
            """.trimIndent()

        assertEquals(code, format(code))
    }

    @Test
    fun `leaves a body that already starts with a blank line alone`() {
        val code =
            """
            class A {

                val x = 1

                val y = 2
            }
            """.trimIndent()

        assertEquals(code, format(code))
    }

    @Test
    fun `leaves function bodies and lambdas alone`() {
        val code =
            """
            fun f() {
                val a = 1
                val b = 2
                list.forEach {
                    print(a)
                    print(b)
                }
            }
            """.trimIndent()

        assertEquals(code, format(code))
    }

    @Test
    fun `leaves a class without a body alone`() {
        val code =
            """
            data class A(val x: Int)
            """.trimIndent()

        assertEquals(code, format(code))
    }

    private fun format(code: String) = format(code) { BlankFirstLineInClassBodyRule() }
}

package de.pflugradts.passbirdupdater

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTest {

    @Test
    fun `parseJson reads objects arrays and scalars`() {
        val result = parseJsonObject(
            """{"name": "passbird.jar", "draft": false, "none": null, "size": 12.5e1, "assets": [{"id": 1}, {"id": 2}]}""",
        )

        assertEquals("passbird.jar", result["name"])
        assertEquals(false, result["draft"])
        assertEquals(null, result["none"])
        assertEquals(125, (result["size"] as java.math.BigDecimal).toInt())
        assertEquals(2, result.requiredArray("assets").size)
    }

    // COMP-0001
    @Test
    fun `parseJson decodes string escapes`() {
        val result = parseJsonObject("""{"text": "a\"b\\c\/d\b\f\n\r\t\u00e4\u20AC"}""")

        assertEquals("a\"b\\c/d\b\u000C\n\r\tä€", result["text"])
    }

    @Test
    fun `parseJson accepts empty containers and surrounding whitespace`() {
        assertEquals(emptyMap(), parseJsonObject(" \n{ }\t"))
        assertEquals(emptyList(), parseJsonObjects("[ ]"))
    }

    @Test
    fun `parseJson rejects malformed documents`() {
        listOf(
            "",
            "{",
            "{\"a\": 1,}",
            "[1,]",
            "{\"a\" 1}",
            "{a: 1}",
            "{\"a\": 1} trailing",
            "{\"a\": \"unterminated}",
            "{\"a\": \"bad\\x\"}",
            "{\"a\": \"\\u12G4\"}",
            "{\"a\": \"line\nbreak\"}",
            "{\"a\": 01}",
            "{\"a\": tru}",
            "{\"a\": 1, \"a\": 2}",
        ).forEach { document ->
            assertFailsWith<IllegalStateException>(document) { parseJson(document) }
        }
    }

    @Test
    fun `parseJson rejects excessive nesting`() {
        assertFailsWith<IllegalStateException> { parseJson("[".repeat(100) + "]".repeat(100)) }
    }

    // COMP-0001
    @Test
    fun `parseJson preserves negative and fractional numbers without rounding`() {
        assertEquals(BigDecimal("-1.25E-8"), parseJson("-1.25e-8"))
        assertEquals(BigDecimal("9007199254740993"), parseJson("9007199254740993"))
    }

    // COMP-0001
    @Test
    fun `parseJson reports why damaged release metadata cannot be read`() {
        val cases = mapOf(
            """{"release":"unfinished""" to "Unterminated string",
            "\"unfinished\\" to "Unterminated escape sequence",
            "\"\\u12" to "Invalid unicode escape",
            """{"draft":false "assets":[]}""" to "Expected ',' or '}'",
            """[{"tag_name":"v1.0.0"} {"tag_name":"v2.0.0"}]""" to "Expected ',' or ']'",
            "-" to "Invalid number",
        )

        for ((document, diagnostic) in cases) {
            assertContains(assertFailsWith<IllegalStateException> { parseJson(document) }.message.orEmpty(), diagnostic)
        }
    }

    @Test
    fun `typed readers reject unexpected shapes`() {
        assertFailsWith<IllegalStateException> { parseJsonObject("[]") }
        assertFailsWith<IllegalStateException> { parseJsonObjects("{}") }
        assertFailsWith<IllegalStateException> { parseJsonObjects("[1]") }
        assertFailsWith<IllegalStateException> { parseJsonObject("""{"name": 1}""").requiredString("name") }
        assertFailsWith<IllegalStateException> { parseJsonObject("""{"assets": [1]}""").requiredArray("assets") }
    }
}

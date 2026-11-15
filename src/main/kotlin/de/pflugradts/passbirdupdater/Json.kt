package de.pflugradts.passbirdupdater

import java.math.BigDecimal

typealias JsonObject = Map<String, Any?>

private const val MAX_JSON_DEPTH = 32
private val JSON_NUMBER = Regex("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?")

fun parseJsonObject(text: String): JsonObject = parseJson(text).asJsonObject() ?: error("Expected a JSON object")

fun parseJsonObjects(text: String): List<JsonObject> = (parseJson(text) as? List<*>)
    ?.map { it.asJsonObject() ?: error("Expected a JSON array of objects") }
    ?: error("Expected a JSON array")

@Suppress("UNCHECKED_CAST")
fun Any?.asJsonObject(): JsonObject? = (this as? Map<*, *>)?.takeIf { map -> map.keys.all { it is String } } as JsonObject?

fun parseJson(text: String): Any? = JsonReader(text).readDocument()

private class JsonReader(private val text: String) {
    private var position = 0

    fun readDocument(): Any? {
        val value = readValue(0)
        skipWhitespace()
        if (position != text.length) fail("Unexpected content after the JSON value")
        return value
    }

    private fun readValue(depth: Int): Any? {
        if (depth > MAX_JSON_DEPTH) fail("JSON nesting is too deep")
        skipWhitespace()
        return when (val character = peek()) {
            '{' -> readObject(depth)
            '[' -> readArray(depth)
            '"' -> readString()
            't' -> readLiteral("true", true)
            'f' -> readLiteral("false", false)
            'n' -> readLiteral("null", null)
            else -> if (character == '-' || character in '0'..'9') readNumber() else fail("Unexpected character '$character'")
        }
    }

    private fun readObject(depth: Int): JsonObject {
        position++
        val result = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') return result.also { position++ }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("Expected a string as object key")
            val key = readString()
            if (key in result) fail("Duplicate object key '$key'")
            skipWhitespace()
            expect(':')
            result[key] = readValue(depth + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                '}' -> return result.also { position++ }
                else -> fail("Expected ',' or '}'")
            }
        }
    }

    private fun readArray(depth: Int): List<Any?> {
        position++
        val result = ArrayList<Any?>()
        skipWhitespace()
        if (peek() == ']') return result.also { position++ }
        while (true) {
            result.add(readValue(depth + 1))
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                ']' -> return result.also { position++ }
                else -> fail("Expected ',' or ']'")
            }
        }
    }

    private fun readString(): String {
        position++
        val result = StringBuilder()
        while (true) {
            if (position >= text.length) fail("Unterminated string")
            when (val character = text[position++]) {
                '"' -> return result.toString()
                '\\' -> result.append(readEscape())
                else -> if (character < ' ') fail("Control character in string") else result.append(character)
            }
        }
    }

    private fun readEscape(): Char {
        if (position >= text.length) fail("Unterminated escape sequence")
        return when (val character = text[position++]) {
            '"', '\\', '/' -> character
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                val hex = text.substring(position, minOf(position + 4, text.length))
                if (hex.length != 4 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("Invalid unicode escape")
                position += 4
                hex.toInt(16).toChar()
            }
            else -> fail("Invalid escape sequence")
        }
    }

    private fun readNumber(): BigDecimal {
        val match = JSON_NUMBER.matchAt(text, position) ?: fail("Invalid number")
        position += match.value.length
        return BigDecimal(match.value)
    }

    private fun readLiteral(literal: String, value: Any?): Any? {
        if (!text.startsWith(literal, position)) fail("Invalid literal")
        position += literal.length
        return value
    }

    private fun peek(): Char = if (position < text.length) text[position] else fail("Unexpected end of JSON")

    private fun expect(character: Char) {
        if (peek() != character) fail("Expected '$character'")
        position++
    }

    private fun skipWhitespace() {
        while (position < text.length && text[position] in " \t\r\n") position++
    }

    private fun fail(message: String): Nothing = error("Invalid JSON at position $position: $message")
}

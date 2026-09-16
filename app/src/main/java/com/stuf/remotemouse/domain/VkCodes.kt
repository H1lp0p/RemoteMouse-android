package com.stuf.remotemouse.domain

data class KeyToken(
    val label: String,
    val vkCode: Int,
) {
    val chipLabel: String
        get() = if (label.length == 1 && label[0].isLetterOrDigit()) {
            "\"$label\""
        } else {
            label
        }
}

object VkCodes {
    val namedKeys: List<KeyToken> = listOf(
        KeyToken("Ctrl", 17),
        KeyToken("Win", 91),
        KeyToken("Alt", 18),
        KeyToken("Shift", 16),
        KeyToken("Tab", 9),
        KeyToken("CapsLock", 20),
        KeyToken("Backspace", 8),
        KeyToken("Enter", 13),
        KeyToken("Del", 46),
        KeyToken("Esc", 27),
        KeyToken("Space", 32),
    )

    /** Hint chips under “works for:” in the editor. */
    val hintNamedKeys: List<KeyToken> = namedKeys.filter {
        it.label in setOf(
            "Ctrl", "Win", "Alt", "Shift", "Tab",
            "CapsLock", "Backspace", "Enter", "Del",
        )
    }

    private val aliasToLabel: Map<String, String> = buildMap {
        fun alias(vararg keys: String, label: String) {
            keys.forEach { put(it.lowercase(), label) }
        }
        namedKeys.forEach { put(it.label.lowercase(), it.label) }
        alias("control", "ctl", label = "Ctrl")
        alias("windows", "meta", "cmd", "command", "super", label = "Win")
        alias("option", label = "Alt")
        alias("caps", "capslock", label = "CapsLock")
        alias("bs", "back", label = "Backspace")
        alias("return", "ret", label = "Enter")
        alias("delete", label = "Del")
        alias("escape", label = "Esc")
        alias("spc", label = "Space")
        for (i in 1..12) {
            val label = "Fn$i"
            put("fn$i", label)
            put("f$i", label)
        }
    }

    private val labelToCode: Map<String, Int> = buildMap {
        namedKeys.forEach { put(it.label, it.vkCode) }
        for (i in 1..12) {
            put("Fn$i", 111 + i)
        }
    }

    /** Ordered dangerous combos (VK codes). */
    val dangerousCombos: List<List<Int>> = listOf(
        listOf(18, 115), // Alt+F4
        listOf(17, 18, 46), // Ctrl+Alt+Del
        listOf(91, 76), // Win+L
        listOf(17, 16, 27), // Ctrl+Shift+Esc
    )

    fun parse(stepStringCode: String): List<Int> =
        stepStringCode
            .split(',', '+', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }

    fun label(code: Int): String = when (code) {
        8 -> "Backspace"
        9 -> "Tab"
        13 -> "Enter"
        16 -> "Shift"
        17 -> "Ctrl"
        18 -> "Alt"
        20 -> "CapsLock"
        27 -> "Esc"
        32 -> "Space"
        46 -> "Del"
        91 -> "Win"
        in 112..123 -> "Fn${code - 111}"
        in 65..90 -> code.toChar().toString()
        in 97..122 -> code.toChar().uppercaseChar().toString()
        in 48..57 -> code.toChar().toString()
        else -> code.toString()
    }

    fun labels(stepStringCode: String): List<String> =
        parse(stepStringCode).map(::label)

    fun formatSequence(stepStringCode: String): String =
        labels(stepStringCode).joinToString(" + ")

    fun formatSequence(codes: List<Int>): String =
        codes.joinToString("+") { label(it) }

    fun tokenFromCode(code: Int): KeyToken =
        KeyToken(label = label(code), vkCode = code)

    fun tokensFromCodes(codes: List<Int>): List<KeyToken> =
        codes.map(::tokenFromCode)

    /**
     * Resolve a typed token (without trailing space) into a key, or null if unknown.
     * Accepts named keys, Fn/F numbers, and single letters/digits.
     */
    fun resolveToken(raw: String): KeyToken? {
        val trimmed = raw.trim().trim('"', '\'')
        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()
        aliasToLabel[lower]?.let { canonical ->
            val code = labelToCode[canonical] ?: return null
            return KeyToken(canonical, code)
        }

        if (trimmed.length == 1) {
            val ch = trimmed[0]
            return when {
                ch in 'A'..'Z' -> KeyToken(ch.toString(), ch.code)
                ch in 'a'..'z' -> {
                    val upper = ch.uppercaseChar()
                    KeyToken(upper.toString(), upper.code)
                }
                ch in '0'..'9' -> KeyToken(ch.toString(), ch.code)
                else -> null
            }
        }
        return null
    }

    fun encode(tokens: List<KeyToken>): String =
        tokens.joinToString(",") { it.vkCode.toString() }

    fun isDangerous(tokens: List<KeyToken>): Boolean {
        val codes = tokens.map { it.vkCode }
        return dangerousCombos.any { danger ->
            codes == danger
        }
    }

    fun dangerMessageCombo(tokens: List<KeyToken>): String =
        tokens.joinToString("+") { token ->
            val label = token.label
            if (label.startsWith("Fn") && label.removePrefix("Fn").toIntOrNull() != null) {
                "F${label.removePrefix("Fn")}"
            } else {
                label
            }
        }
}

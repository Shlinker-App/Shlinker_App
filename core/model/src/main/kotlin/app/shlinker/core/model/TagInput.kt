package app.shlinker.core.model

fun parseTagInput(input: String): List<String> =
    input.split(',', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

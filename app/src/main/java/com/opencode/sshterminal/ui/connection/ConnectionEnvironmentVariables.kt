package com.opencode.sshterminal.ui.connection

internal fun parseEnvironmentVariablesInput(input: String): Map<String, String> {
    val parsed = linkedMapOf<String, String>()
    input.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach
        val separator = line.indexOf('=')
        if (separator <= 0) return@forEach
        val key = line.substring(0, separator).trim()
        if (key.isEmpty()) return@forEach
        val value = line.substring(separator + 1).trim()
        parsed[key] = value
    }
    return parsed.toMap()
}

internal fun formatEnvironmentVariablesInput(environmentVariables: Map<String, String>): String {
    if (environmentVariables.isEmpty()) return ""
    return environmentVariables
        .toList()
        .sortedBy { (key, _) -> key.lowercase() }
        .joinToString("\n") { (key, value) -> "$key=$value" }
}

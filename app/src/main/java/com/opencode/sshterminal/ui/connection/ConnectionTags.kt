package com.opencode.sshterminal.ui.connection

internal fun parseConnectionTagsInput(input: String): List<String> {
    val deduped = linkedSetOf<String>()
    input
        .split(',', '\n')
        .map { part -> part.trim() }
        .filter { part -> part.isNotEmpty() }
        .forEach { tag ->
            deduped += tag
        }
    return deduped.toList()
}

internal fun formatConnectionTagsInput(tags: List<String>): String {
    if (tags.isEmpty()) return ""
    return tags.joinToString(", ")
}

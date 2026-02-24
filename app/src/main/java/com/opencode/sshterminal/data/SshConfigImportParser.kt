package com.opencode.sshterminal.data

internal data class SshConfigImportHost(
    val alias: String,
    val hostName: String,
    val port: Int,
    val user: String,
    val identityFile: String?,
    val forwardAgent: Boolean,
    val proxyJump: String?,
    val portForwards: List<PortForwardRule>,
)

internal data class SshConfigImportParseResult(
    val hosts: List<SshConfigImportHost>,
    val skippedHostEntries: Int,
)

internal fun parseSshConfig(content: String): SshConfigImportParseResult {
    val parsedHosts = linkedMapOf<String, MutableSshHost>()
    val globalDefaults = MutableSshHost(alias = "")
    var currentHosts: List<MutableSshHost>? = null
    var skipped = 0

    content.lineSequence().forEach { rawLine ->
        val line = stripInlineComment(rawLine).trim()
        if (line.isEmpty()) return@forEach

        val segments = line.split(Regex("\\s+"), limit = 2)
        val keyword = segments.first().lowercase()
        val value = segments.getOrNull(1)?.trim().orEmpty()

        when (keyword) {
            "host" -> {
                val aliases =
                    value
                        .split(Regex("\\s+"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                val concreteAliases = aliases.filter(::isConcreteHostAlias)
                skipped += aliases.size - concreteAliases.size
                currentHosts =
                    concreteAliases.map { alias ->
                        MutableSshHost(
                            alias = alias,
                            hostName = globalDefaults.hostName,
                            user = globalDefaults.user,
                            port = globalDefaults.port,
                            identityFile = globalDefaults.identityFile,
                            forwardAgent = globalDefaults.forwardAgent,
                            proxyJump = globalDefaults.proxyJump,
                            portForwards = globalDefaults.portForwards.toMutableList(),
                        ).also { parsedHosts[alias] = it }
                    }
            }

            "match" -> {
                // Match rules are context-specific; ignore them for deterministic import.
                currentHosts = null
            }

            else -> {
                val targets = currentHosts ?: listOf(globalDefaults)
                targets.forEach { host -> applyOption(host, keyword, value) }
            }
        }
    }

    val hosts =
        parsedHosts.values.mapNotNull { host ->
            val user =
                host.user?.takeIf { it.isNotBlank() } ?: run {
                    skipped += 1
                    return@mapNotNull null
                }
            val resolvedHost = host.hostName?.takeIf { it.isNotBlank() } ?: host.alias
            SshConfigImportHost(
                alias = host.alias,
                hostName = resolvedHost,
                port = host.port ?: DEFAULT_SSH_PORT,
                user = user,
                identityFile = host.identityFile,
                forwardAgent = host.forwardAgent,
                proxyJump = host.proxyJump,
                portForwards = host.portForwards.toList(),
            )
        }

    return SshConfigImportParseResult(
        hosts = hosts,
        skippedHostEntries = skipped,
    )
}

private fun applyOption(
    host: MutableSshHost,
    keyword: String,
    rawValue: String,
) {
    val value = rawValue.unquote()
    when (keyword) {
        "hostname" -> host.hostName = value
        "user" -> host.user = value
        "port" -> host.port = value.toIntOrNull() ?: host.port
        "identityfile" -> host.identityFile = value
        "forwardagent" -> parseSshBoolean(value)?.let { host.forwardAgent = it }
        "proxyjump" -> host.proxyJump = value
        "localforward" -> parseLocalOrRemoteForward(value, PortForwardType.LOCAL)?.let(host.portForwards::add)
        "remoteforward" -> parseLocalOrRemoteForward(value, PortForwardType.REMOTE)?.let(host.portForwards::add)
        "dynamicforward" -> parseDynamicForward(value)?.let(host.portForwards::add)
    }
}

private fun parseSshBoolean(value: String): Boolean? =
    when (value.lowercase()) {
        "yes", "true", "on", "1" -> true
        "no", "false", "off", "0" -> false
        else -> null
    }

private fun parseLocalOrRemoteForward(
    value: String,
    type: PortForwardType,
): PortForwardRule? {
    val tokens = value.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.size < 2) return null
    val bind = parsePortEndpoint(tokens[0]) ?: return null
    val target = parseHostPort(tokens[1]) ?: return null
    return PortForwardRule(
        type = type,
        bindHost = bind.host,
        bindPort = bind.port,
        targetHost = target.host,
        targetPort = target.port,
    )
}

private fun parseDynamicForward(value: String): PortForwardRule? {
    val token = value.split(Regex("\\s+")).firstOrNull()?.trim().orEmpty()
    if (token.isEmpty()) return null
    val bind = parsePortEndpoint(token) ?: return null
    return PortForwardRule(
        type = PortForwardType.DYNAMIC,
        bindHost = bind.host,
        bindPort = bind.port,
    )
}

private fun parseHostPort(token: String): HostPort? {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("[") && "]:" in trimmed) {
        val hostEnd = trimmed.indexOf("]:")
        val host = trimmed.substring(1, hostEnd).takeIf { it.isNotBlank() } ?: return null
        val port = trimmed.substring(hostEnd + 2).toIntOrNull() ?: return null
        return HostPort(host = host, port = port)
    }
    val lastColon = trimmed.lastIndexOf(':')
    if (lastColon <= 0 || lastColon == trimmed.lastIndex) return null
    val host = trimmed.substring(0, lastColon).takeIf { it.isNotBlank() } ?: return null
    val port = trimmed.substring(lastColon + 1).toIntOrNull() ?: return null
    return HostPort(host = host, port = port)
}

private fun parsePortEndpoint(token: String): HostPort? {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("[") && "]:" in trimmed) {
        val hostEnd = trimmed.indexOf("]:")
        val host = trimmed.substring(1, hostEnd).takeIf { it.isNotBlank() }
        val port = trimmed.substring(hostEnd + 2).toIntOrNull() ?: return null
        return HostPort(host = host, port = port)
    }
    val plainPort = trimmed.toIntOrNull()
    if (plainPort != null) return HostPort(host = null, port = plainPort)

    val lastColon = trimmed.lastIndexOf(':')
    if (lastColon <= 0 || lastColon == trimmed.lastIndex) return null
    val host = trimmed.substring(0, lastColon).takeIf { it.isNotBlank() }
    val port = trimmed.substring(lastColon + 1).toIntOrNull() ?: return null
    return HostPort(host = host, port = port)
}

private fun isConcreteHostAlias(alias: String): Boolean {
    return alias.isNotBlank() &&
        '*' !in alias &&
        '?' !in alias &&
        !alias.startsWith('!')
}

private fun stripInlineComment(line: String): String {
    val index = line.indexOf('#')
    return if (index >= 0) line.substring(0, index) else line
}

private fun String.unquote(): String {
    if (length >= 2 && first() == '"' && last() == '"') {
        return substring(1, lastIndex)
    }
    return this
}

private data class MutableSshHost(
    val alias: String,
    var hostName: String? = null,
    var user: String? = null,
    var port: Int? = null,
    var identityFile: String? = null,
    var forwardAgent: Boolean = false,
    var proxyJump: String? = null,
    val portForwards: MutableList<PortForwardRule> = mutableListOf(),
)

private data class HostPort(
    val host: String?,
    val port: Int,
)

private const val DEFAULT_SSH_PORT = 22

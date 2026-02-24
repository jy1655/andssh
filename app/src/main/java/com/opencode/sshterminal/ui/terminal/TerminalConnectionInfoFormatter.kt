package com.opencode.sshterminal.ui.terminal

import com.opencode.sshterminal.data.ConnectionProfile
import com.opencode.sshterminal.data.PortForwardRule
import com.opencode.sshterminal.data.PortForwardType
import com.opencode.sshterminal.data.parseProxyJumpEntries
import com.opencode.sshterminal.session.SessionSnapshot

internal data class TerminalConnectionInfo(
    val endpoint: String,
    val proxyJumpHopCount: Int,
    val forwardCount: Int,
    val forwardPreviewLines: List<String>,
    val remainingForwardCount: Int,
)

internal fun buildTerminalConnectionInfo(
    snapshot: SessionSnapshot?,
    profile: ConnectionProfile?,
): TerminalConnectionInfo? {
    val current = snapshot ?: return null
    if (current.host.isBlank()) return null

    val proxyJumpHopCount =
        profile?.proxyJump
            ?.takeIf { value -> value.isNotBlank() }
            ?.let(::parseProxyJumpEntries)
            ?.size ?: 0
    val forwardRules = profile?.portForwards.orEmpty()
    val forwardCount = forwardRules.size
    val forwardPreviewLines = forwardRules.take(FORWARD_PREVIEW_LIMIT).map(::formatForwardPreview)
    val remainingForwardCount = (forwardCount - forwardPreviewLines.size).coerceAtLeast(0)

    return TerminalConnectionInfo(
        endpoint = "${current.username}@${current.host}:${current.port}",
        proxyJumpHopCount = proxyJumpHopCount,
        forwardCount = forwardCount,
        forwardPreviewLines = forwardPreviewLines,
        remainingForwardCount = remainingForwardCount,
    )
}

internal fun TerminalConnectionInfo.toDisplayText(
    proxyJumpFormatter: (Int) -> String,
    forwardFormatter: (Int) -> String,
    moreForwardFormatter: (Int) -> String,
): String {
    val lines = mutableListOf(endpoint)
    if (proxyJumpHopCount > 0) {
        lines += proxyJumpFormatter(proxyJumpHopCount)
    }
    if (forwardCount > 0) {
        lines += forwardFormatter(forwardCount)
        forwardPreviewLines.forEach { line -> lines += "  - $line" }
        if (remainingForwardCount > 0) {
            lines += moreForwardFormatter(remainingForwardCount)
        }
    }
    return lines.joinToString("\n")
}

private fun formatForwardPreview(rule: PortForwardRule): String {
    val bindHost = rule.bindHost ?: "127.0.0.1"
    return when (rule.type) {
        PortForwardType.LOCAL -> {
            val targetHost = rule.targetHost ?: "?"
            val targetPort = rule.targetPort?.toString() ?: "?"
            "L $bindHost:${rule.bindPort} -> $targetHost:$targetPort"
        }
        PortForwardType.REMOTE -> {
            val targetHost = rule.targetHost ?: "?"
            val targetPort = rule.targetPort?.toString() ?: "?"
            "R $bindHost:${rule.bindPort} -> $targetHost:$targetPort"
        }
        PortForwardType.DYNAMIC -> "D $bindHost:${rule.bindPort}"
    }
}

private const val FORWARD_PREVIEW_LIMIT = 3

package com.opencode.sshterminal.ui.connection

import com.opencode.sshterminal.data.PortForwardRule
import com.opencode.sshterminal.data.PortForwardType

internal fun buildPortForwardRule(
    type: PortForwardType,
    bindHostInput: String,
    bindPortInput: String,
    targetHostInput: String,
    targetPortInput: String,
): PortForwardRule? {
    val bindPort = bindPortInput.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    val bindHost = bindHostInput.trim().ifBlank { null }
    return when (type) {
        PortForwardType.LOCAL,
        PortForwardType.REMOTE,
        -> {
            val targetHost = targetHostInput.trim().takeIf { it.isNotBlank() } ?: return null
            val targetPort = targetPortInput.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            PortForwardRule(
                type = type,
                bindHost = bindHost,
                bindPort = bindPort,
                targetHost = targetHost,
                targetPort = targetPort,
            )
        }
        PortForwardType.DYNAMIC ->
            PortForwardRule(
                type = type,
                bindHost = bindHost,
                bindPort = bindPort,
            )
    }
}

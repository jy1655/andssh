package com.opencode.sshterminal.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.opencode.sshterminal.R
import com.opencode.sshterminal.data.ConnectionProtocol

@Suppress("LongMethod")
@Composable
fun QuickConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (host: String, port: Int, username: String, password: String, protocol: ConnectionProtocol) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var protocol by remember { mutableStateOf(ConnectionProtocol.SSH) }
    var protocolMenuExpanded by remember { mutableStateOf(false) }
    val canConnect = host.isNotBlank() && username.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_connect_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.quick_connect_host_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(R.string.connection_label_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.connection_label_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.connection_label_password_optional)) },
                    singleLine = true,
                    visualTransformation =
                        if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            val contentDescription =
                                if (showPassword) {
                                    stringResource(R.string.common_hide_password)
                                } else {
                                    stringResource(R.string.common_show_password)
                                }
                            Icon(
                                imageVector =
                                    if (showPassword) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                contentDescription = contentDescription,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { protocolMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${stringResource(R.string.connection_label_protocol)}: ${
                                when (protocol) {
                                    ConnectionProtocol.SSH -> stringResource(R.string.connection_protocol_ssh)
                                    ConnectionProtocol.MOSH -> stringResource(R.string.connection_protocol_mosh)
                                }
                            }",
                        )
                    }
                    DropdownMenu(
                        expanded = protocolMenuExpanded,
                        onDismissRequest = { protocolMenuExpanded = false },
                    ) {
                        ConnectionProtocol.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (candidate) {
                                            ConnectionProtocol.SSH -> stringResource(R.string.connection_protocol_ssh)
                                            ConnectionProtocol.MOSH -> stringResource(R.string.connection_protocol_mosh)
                                        },
                                    )
                                },
                                onClick = {
                                    protocol = candidate
                                    protocolMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portValue = port.toIntOrNull().takeIf { it in 1..65535 } ?: 22
                    onConnect(host.trim(), portValue, username.trim(), password, protocol)
                },
                enabled = canConnect,
            ) {
                Text(stringResource(R.string.quick_connect_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

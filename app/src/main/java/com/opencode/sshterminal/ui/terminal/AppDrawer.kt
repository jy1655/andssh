package com.opencode.sshterminal.ui.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.opencode.sshterminal.R
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    drawerState: DrawerState,
    connectionInfo: String,
    onTerminal: () -> Unit,
    onSftp: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val close: (() -> Unit) -> Unit = { action ->
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (connectionInfo.isNotBlank()) {
                Text(
                    text = connectionInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
            label = { Text(stringResource(R.string.drawer_terminal)) },
            selected = true,
            onClick = { close(onTerminal) },
            modifier = Modifier.fillMaxWidth().padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
            label = { Text(stringResource(R.string.drawer_sftp_browser)) },
            selected = false,
            onClick = { close(onSftp) },
            modifier = Modifier.fillMaxWidth().padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
            label = { Text(stringResource(R.string.drawer_disconnect_tab)) },
            selected = false,
            onClick = { close(onDisconnect) },
            modifier = Modifier.fillMaxWidth().padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

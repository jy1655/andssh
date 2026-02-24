@file:Suppress("TooManyFunctions")

package com.opencode.sshterminal.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencode.sshterminal.BuildConfig
import com.opencode.sshterminal.R
import com.opencode.sshterminal.terminal.TerminalColorSchemePreset
import com.opencode.sshterminal.terminal.TerminalFontPreset
import com.opencode.sshterminal.ui.theme.ClassicPurple
import com.opencode.sshterminal.ui.theme.OceanBlue
import com.opencode.sshterminal.ui.theme.SunsetOrange
import com.opencode.sshterminal.ui.theme.TerminalGreen
import com.opencode.sshterminal.ui.theme.ThemePreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToCrashLogs: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.sftp_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp),
        ) {
            LanguageSection(
                selected = state.languageTag,
                onSelect = viewModel::setLanguageTag,
            )
            Spacer(modifier = Modifier.height(24.dp))
            ThemeSection(
                selected = state.themePreset,
                onSelect = viewModel::setThemePreset,
            )
            Spacer(modifier = Modifier.height(24.dp))
            SecuritySection(state = state, viewModel = viewModel)
            Spacer(modifier = Modifier.height(24.dp))
            TerminalSection(state = state, viewModel = viewModel)
            Spacer(modifier = Modifier.height(24.dp))
            AdvancedSection(state = state, onNavigateToCrashLogs = onNavigateToCrashLogs)
            Spacer(modifier = Modifier.height(24.dp))
            AboutSection()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun LanguageSection(
    selected: String,
    onSelect: (String) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_language_title))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            LanguageOption(
                label = stringResource(R.string.settings_language_system),
                tag = "",
                selected = selected,
                onSelect = onSelect,
            )
            LanguageOption(
                label = stringResource(R.string.settings_language_english),
                tag = "en",
                selected = selected,
                onSelect = onSelect,
            )
            LanguageOption(
                label = stringResource(R.string.settings_language_korean),
                tag = "ko",
                selected = selected,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    tag: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val isSelected = selected == tag
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onSelect(tag) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ThemeSection(
    selected: ThemePreset,
    onSelect: (ThemePreset) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_theme_title))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ThemeCircle(
            preset = ThemePreset.GREEN,
            color = TerminalGreen,
            label = stringResource(R.string.settings_theme_green),
            isSelected = selected == ThemePreset.GREEN,
            onSelect = onSelect,
        )
        ThemeCircle(
            preset = ThemePreset.OCEAN,
            color = OceanBlue,
            label = stringResource(R.string.settings_theme_ocean),
            isSelected = selected == ThemePreset.OCEAN,
            onSelect = onSelect,
        )
        ThemeCircle(
            preset = ThemePreset.SUNSET,
            color = SunsetOrange,
            label = stringResource(R.string.settings_theme_sunset),
            isSelected = selected == ThemePreset.SUNSET,
            onSelect = onSelect,
        )
        ThemeCircle(
            preset = ThemePreset.PURPLE,
            color = ClassicPurple,
            label = stringResource(R.string.settings_theme_purple),
            isSelected = selected == ThemePreset.PURPLE,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun ThemeCircle(
    preset: ThemePreset,
    color: Color,
    label: String,
    isSelected: Boolean,
    onSelect: (ThemePreset) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onSelect(preset) },
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            border =
                if (isSelected) {
                    BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                } else {
                    null
                },
            modifier = Modifier.size(48.dp),
        ) {
            if (isSelected) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SecuritySection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var showAutoLockDialog by remember { mutableStateOf(false) }
    val autoLockOptions =
        listOf(
            30 to stringResource(R.string.settings_timeout_30s),
            60 to stringResource(R.string.settings_timeout_1m),
            120 to stringResource(R.string.settings_timeout_2m),
            300 to stringResource(R.string.settings_timeout_5m),
            0 to stringResource(R.string.settings_disabled),
        )
    val autoLockLabel =
        autoLockOptions.firstOrNull { it.first == state.autoLockTimeoutSeconds }?.second
            ?: autoLockOptions.first().second

    SectionHeader(stringResource(R.string.settings_security_title))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_app_lock),
                checked = state.isAppLockEnabled,
                onToggle = viewModel::setAppLockEnabled,
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_biometric_unlock),
                checked = state.isBiometricEnabled,
                enabled = state.isAppLockEnabled,
                onToggle = viewModel::setBiometricEnabled,
            )
            SettingsDivider()
            SettingsValueRow(
                title = stringResource(R.string.settings_auto_lock),
                value = autoLockLabel,
                onClick = { showAutoLockDialog = true },
            )
        }
    }

    SelectionDialog(
        show = showAutoLockDialog,
        title = stringResource(R.string.settings_auto_lock),
        options = autoLockOptions,
        selected = state.autoLockTimeoutSeconds,
        onSelected = viewModel::setAutoLockTimeout,
        onDismiss = { showAutoLockDialog = false },
    )
}

@Suppress("LongMethod")
@Composable
private fun TerminalSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var showSchemeDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showKeepaliveDialog by remember { mutableStateOf(false) }

    val schemeOptions = TerminalColorSchemePreset.entries.map { it.id to it.displayName }
    val fontOptions = TerminalFontPreset.entries.map { it.id to it.displayName }
    val clipboardOptions =
        listOf(
            15 to stringResource(R.string.settings_timeout_15s),
            30 to stringResource(R.string.settings_timeout_30s),
            60 to stringResource(R.string.settings_timeout_1m),
            0 to stringResource(R.string.settings_disabled),
        )
    val clipboardLabel =
        clipboardOptions.firstOrNull { it.first == state.clipboardTimeoutSeconds }?.second
            ?: clipboardOptions[1].second
    val keepaliveOptions =
        listOf(
            0 to stringResource(R.string.settings_disabled),
            15 to stringResource(R.string.settings_timeout_15s),
            30 to stringResource(R.string.settings_timeout_30s),
            60 to stringResource(R.string.settings_timeout_1m),
        )
    val keepaliveLabel =
        keepaliveOptions.firstOrNull { it.first == state.sshKeepaliveIntervalSeconds }?.second
            ?: keepaliveOptions[1].second

    SectionHeader(stringResource(R.string.settings_terminal_title))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column {
            SettingsValueRow(
                title = stringResource(R.string.settings_color_scheme),
                value = TerminalColorSchemePreset.fromId(state.terminalColorScheme).displayName,
                onClick = { showSchemeDialog = true },
            )
            SettingsDivider()
            SettingsValueRow(
                title = stringResource(R.string.settings_font),
                value = TerminalFontPreset.fromId(state.terminalFont).displayName,
                onClick = { showFontDialog = true },
            )
            SettingsDivider()
            SettingsValueRow(
                title = stringResource(R.string.settings_clipboard_clear),
                value = clipboardLabel,
                onClick = { showClipboardDialog = true },
            )
            SettingsDivider()
            SettingsValueRow(
                title = stringResource(R.string.settings_ssh_keepalive),
                value = keepaliveLabel,
                onClick = { showKeepaliveDialog = true },
            )
        }
    }

    SelectionDialog(
        show = showSchemeDialog,
        title = stringResource(R.string.settings_color_scheme),
        options = schemeOptions,
        selected = state.terminalColorScheme,
        onSelected = viewModel::setTerminalColorScheme,
        onDismiss = { showSchemeDialog = false },
    )
    SelectionDialog(
        show = showFontDialog,
        title = stringResource(R.string.settings_font),
        options = fontOptions,
        selected = state.terminalFont,
        onSelected = viewModel::setTerminalFont,
        onDismiss = { showFontDialog = false },
    )
    SelectionDialog(
        show = showClipboardDialog,
        title = stringResource(R.string.settings_clipboard_clear),
        options = clipboardOptions,
        selected = state.clipboardTimeoutSeconds,
        onSelected = viewModel::setClipboardTimeout,
        onDismiss = { showClipboardDialog = false },
    )
    SelectionDialog(
        show = showKeepaliveDialog,
        title = stringResource(R.string.settings_ssh_keepalive),
        options = keepaliveOptions,
        selected = state.sshKeepaliveIntervalSeconds,
        onSelected = viewModel::setSshKeepaliveInterval,
        onDismiss = { showKeepaliveDialog = false },
    )
}

@Composable
private fun AdvancedSection(
    state: SettingsUiState,
    onNavigateToCrashLogs: () -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_advanced_title))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        SettingsValueRow(
            title = stringResource(R.string.settings_crash_logs),
            value = stringResource(R.string.settings_crash_logs_count, state.crashReportCount),
            onClick = onNavigateToCrashLogs,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
        )
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsDivider() {
    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
}

@Suppress("LongParameterList")
@Composable
private fun <T> SelectionDialog(
    show: Boolean,
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(value)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = {
                                onSelected(value)
                                onDismiss()
                            },
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AboutSection() {
    SectionHeader(stringResource(R.string.settings_about_title))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        stringResource(
                            R.string.settings_about_version,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

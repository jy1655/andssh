@file:Suppress("TooManyFunctions")

package com.opencode.sshterminal.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencode.sshterminal.BuildConfig
import com.opencode.sshterminal.R
import com.opencode.sshterminal.data.DEFAULT_TERMINAL_SHORTCUT_LAYOUT_ITEMS
import com.opencode.sshterminal.data.SettingsRepository
import com.opencode.sshterminal.data.TerminalShortcutLayoutItem
import com.opencode.sshterminal.data.parseTerminalHardwareKeyBindings
import com.opencode.sshterminal.data.parseTerminalShortcutLayout
import com.opencode.sshterminal.data.serializeTerminalHardwareKeyBindings
import com.opencode.sshterminal.data.serializeTerminalShortcutLayout
import com.opencode.sshterminal.terminal.TerminalColorSchemePreset
import com.opencode.sshterminal.terminal.TerminalFontPreset
import com.opencode.sshterminal.ui.theme.ClassicPurple
import com.opencode.sshterminal.ui.theme.OceanBlue
import com.opencode.sshterminal.ui.theme.SunsetOrange
import com.opencode.sshterminal.ui.theme.TerminalGreen
import com.opencode.sshterminal.ui.theme.ThemePreset
import com.termux.terminal.TerminalEmulator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToCrashLogs: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val onExportBackup = rememberBackupExportAction(viewModel)
    val onImportBackup = rememberBackupImportAction(viewModel)

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
            AdvancedSection(
                state = state,
                onNavigateToCrashLogs = onNavigateToCrashLogs,
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
            )
            Spacer(modifier = Modifier.height(24.dp))
            AboutSection()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun rememberBackupExportAction(viewModel: SettingsViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                runCatching {
                    val backupJson = viewModel.exportEncryptedBackup()
                    val output =
                        context.contentResolver.openOutputStream(uri)
                            ?: error("Cannot open backup destination")
                    output.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(backupJson)
                    }
                }.onSuccess {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_backup_export_success),
                        Toast.LENGTH_SHORT,
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.settings_backup_export_failed,
                            error.message ?: "unknown error",
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    return {
        launcher.launch("andssh-backup-${System.currentTimeMillis()}.json")
    }
}

@Composable
private fun rememberBackupImportAction(viewModel: SettingsViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                runCatching {
                    val input =
                        context.contentResolver.openInputStream(uri)
                            ?: error("Cannot open selected backup")
                    val backupJson =
                        input.bufferedReader(Charsets.UTF_8).use { reader ->
                            reader.readText()
                        }
                    viewModel.importEncryptedBackup(backupJson)
                }.onSuccess { summary ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.settings_backup_import_success,
                            summary.profileCount,
                            summary.identityCount,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.settings_backup_import_failed,
                            error.message ?: "unknown error",
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    return {
        launcher.launch(arrayOf("application/json", "text/plain", "*/*"))
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
            SettingsDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_prevent_screenshots),
                checked = state.isScreenshotProtectionEnabled,
                onToggle = viewModel::setScreenshotProtectionEnabled,
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
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showCursorDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showKeepaliveDialog by remember { mutableStateOf(false) }
    var showShortcutLayoutDialog by remember { mutableStateOf(false) }
    var showHardwareBindingsDialog by remember { mutableStateOf(false) }

    val schemeOptions = TerminalColorSchemePreset.entries.map { it.id to it.displayName }
    val fontOptions = TerminalFontPreset.entries.map { it.id to it.displayName }
    val fontSizeOptions =
        (SettingsRepository.MIN_TERMINAL_FONT_SIZE_SP..SettingsRepository.MAX_TERMINAL_FONT_SIZE_SP step 2)
            .map { size -> size to stringResource(R.string.settings_font_size_value, size) }
    val cursorStyleOptions =
        listOf(
            TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK to stringResource(R.string.settings_cursor_style_block),
            TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE to
                stringResource(R.string.settings_cursor_style_underline),
            TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR to stringResource(R.string.settings_cursor_style_bar),
        )
    val cursorStyleLabel =
        cursorStyleOptions.firstOrNull { it.first == state.terminalCursorStyle }?.second
            ?: cursorStyleOptions.first().second
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
    val shortcutLayoutItems = parseTerminalShortcutLayout(state.terminalShortcutLayout)
    val shortcutLayoutLabel =
        stringResource(
            R.string.settings_terminal_shortcut_layout_value,
            shortcutLayoutItems.size,
        )
    val hardwareBindingCount = parseTerminalHardwareKeyBindings(state.terminalHardwareKeyBindings).size
    val hardwareBindingsLabel =
        stringResource(
            R.string.settings_terminal_hardware_key_bindings_value,
            hardwareBindingCount,
        )

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
                title = stringResource(R.string.settings_font_size),
                value = stringResource(R.string.settings_font_size_value, state.terminalFontSizeSp),
                onClick = { showFontSizeDialog = true },
            )
            SettingsDivider()
            SettingsValueRow(
                title = stringResource(R.string.settings_cursor_style),
                value = cursorStyleLabel,
                onClick = { showCursorDialog = true },
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
            SettingsDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_ssh_compression),
                checked = state.sshCompressionEnabled,
                onToggle = viewModel::setSshCompressionEnabled,
            )
            SettingsDivider()
            SettingsValueRow(
                title = stringResource(R.string.settings_terminal_shortcut_layout),
                value = shortcutLayoutLabel,
                onClick = { showShortcutLayoutDialog = true },
            )
            SettingsDivider()
            SettingsValueRow(
                title = stringResource(R.string.settings_terminal_hardware_key_bindings),
                value = hardwareBindingsLabel,
                onClick = { showHardwareBindingsDialog = true },
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_haptic_feedback),
                checked = state.terminalHapticFeedbackEnabled,
                onToggle = viewModel::setTerminalHapticFeedbackEnabled,
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
        show = showFontSizeDialog,
        title = stringResource(R.string.settings_font_size),
        options = fontSizeOptions,
        selected = state.terminalFontSizeSp,
        onSelected = viewModel::setTerminalFontSizeSp,
        onDismiss = { showFontSizeDialog = false },
    )
    SelectionDialog(
        show = showCursorDialog,
        title = stringResource(R.string.settings_cursor_style),
        options = cursorStyleOptions,
        selected = state.terminalCursorStyle,
        onSelected = viewModel::setTerminalCursorStyle,
        onDismiss = { showCursorDialog = false },
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
    ShortcutLayoutDialog(
        show = showShortcutLayoutDialog,
        currentLayout = state.terminalShortcutLayout,
        onSave = viewModel::setTerminalShortcutLayout,
        onDismiss = { showShortcutLayoutDialog = false },
    )
    HardwareKeyBindingsDialog(
        show = showHardwareBindingsDialog,
        currentBindings = state.terminalHardwareKeyBindings,
        onSave = viewModel::setTerminalHardwareKeyBindings,
        onDismiss = { showHardwareBindingsDialog = false },
    )
}

@Suppress("LongMethod")
@Composable
private fun ShortcutLayoutDialog(
    show: Boolean,
    currentLayout: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) {
        return
    }
    var draftItems by remember(currentLayout) { mutableStateOf(parseTerminalShortcutLayout(currentLayout)) }
    val availableItems =
        TerminalShortcutLayoutItem.entries.filterNot { item ->
            draftItems.contains(item)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_terminal_shortcut_layout_edit_title)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(serializeTerminalShortcutLayout(draftItems))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { draftItems = DEFAULT_TERMINAL_SHORTCUT_LAYOUT_ITEMS }) {
                    Text(stringResource(R.string.settings_terminal_shortcut_layout_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (draftItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_terminal_shortcut_layout_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                draftItems.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = shortcutLayoutItemLabel(item),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = index > 0,
                            onClick = {
                                draftItems = moveShortcutLayoutItem(draftItems, index, index - 1)
                            },
                        ) {
                            Text(stringResource(R.string.connection_move_up_short))
                        }
                        TextButton(
                            enabled = index < draftItems.lastIndex,
                            onClick = {
                                draftItems = moveShortcutLayoutItem(draftItems, index, index + 1)
                            },
                        ) {
                            Text(stringResource(R.string.connection_move_down_short))
                        }
                        TextButton(
                            onClick = {
                                draftItems =
                                    draftItems.toMutableList().apply {
                                        removeAt(index)
                                    }
                            },
                        ) {
                            Text(stringResource(R.string.common_delete))
                        }
                    }
                }
                if (availableItems.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_terminal_shortcut_layout_add_key),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    availableItems.forEach { item ->
                        TextButton(
                            onClick = {
                                draftItems = draftItems + item
                            },
                        ) {
                            Text("+ ${shortcutLayoutItemLabel(item)}")
                        }
                    }
                }
            }
        },
    )
}

@Suppress("LongMethod")
@Composable
private fun HardwareKeyBindingsDialog(
    show: Boolean,
    currentBindings: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) {
        return
    }
    var draft by remember(currentBindings) { mutableStateOf(currentBindings) }
    val validCount = parseTerminalHardwareKeyBindings(draft).size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_terminal_hardware_key_bindings_edit_title)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(serializeTerminalHardwareKeyBindings(parseTerminalHardwareKeyBindings(draft)))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { draft = "" }) {
                    Text(stringResource(R.string.settings_terminal_hardware_key_bindings_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_terminal_hardware_key_bindings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_terminal_hardware_key_bindings_examples),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 8,
                )
                Text(
                    text =
                        stringResource(
                            R.string.settings_terminal_hardware_key_bindings_valid_count,
                            validCount,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun shortcutLayoutItemLabel(item: TerminalShortcutLayoutItem): String {
    return when (item) {
        TerminalShortcutLayoutItem.MENU -> "\u2630"
        TerminalShortcutLayoutItem.SNIPPETS -> stringResource(R.string.terminal_snippets_short)
        TerminalShortcutLayoutItem.HISTORY -> stringResource(R.string.terminal_history_short)
        TerminalShortcutLayoutItem.ESC -> "ESC"
        TerminalShortcutLayoutItem.TAB -> "TAB"
        TerminalShortcutLayoutItem.CTRL -> "Ctrl"
        TerminalShortcutLayoutItem.ALT -> "Alt"
        TerminalShortcutLayoutItem.ARROW_UP -> "\u2191"
        TerminalShortcutLayoutItem.ARROW_DOWN -> "\u2193"
        TerminalShortcutLayoutItem.ARROW_LEFT -> "\u2190"
        TerminalShortcutLayoutItem.ARROW_RIGHT -> "\u2192"
        TerminalShortcutLayoutItem.BACKSPACE -> "\u232B"
        TerminalShortcutLayoutItem.PAGE_UP -> "PgUp"
        TerminalShortcutLayoutItem.PAGE_DOWN -> "PgDn"
        TerminalShortcutLayoutItem.CTRL_C -> "^C"
        TerminalShortcutLayoutItem.CTRL_D -> "^D"
        TerminalShortcutLayoutItem.PASTE -> stringResource(R.string.terminal_paste)
    }
}

private fun moveShortcutLayoutItem(
    items: List<TerminalShortcutLayoutItem>,
    fromIndex: Int,
    toIndex: Int,
): List<TerminalShortcutLayoutItem> {
    return if (fromIndex !in items.indices || toIndex !in items.indices) {
        items
    } else {
        items.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }
    }
}

@Composable
private fun AdvancedSection(
    state: SettingsUiState,
    onNavigateToCrashLogs: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_advanced_title))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column {
            SettingsValueRow(
                title = stringResource(R.string.settings_backup_export),
                value = stringResource(R.string.settings_backup_value_encrypted),
                onClick = onExportBackup,
            )
            SettingsDivider()
            SettingsValueRow(
                title = stringResource(R.string.settings_backup_import),
                value = stringResource(R.string.settings_backup_value_encrypted),
                onClick = onImportBackup,
            )
            SettingsDivider()
            SettingsValueRow(
                title = stringResource(R.string.settings_crash_logs),
                value = stringResource(R.string.settings_crash_logs_count, state.crashReportCount),
                onClick = onNavigateToCrashLogs,
            )
        }
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

package com.opencode.sshterminal.ui.connection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.opencode.sshterminal.R
import com.opencode.sshterminal.security.SshKeyAlgorithm
import com.opencode.sshterminal.security.SshKeyGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
internal fun rememberConnectionPrivateKeyPicker(onImported: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val privateKeyPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                val importedPath = importPrivateKeyToInternalStorage(context, uri)
                if (importedPath != null) {
                    onImported(importedPath)
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.connection_private_key_import_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    return { privateKeyPicker.launch("*/*") }
}

@Composable
internal fun rememberConnectionPrivateKeyGenerator(onGenerated: (String) -> Unit): (SshKeyAlgorithm) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { algorithm ->
        scope.launch {
            val generatedPath =
                withContext(Dispatchers.Default) {
                    generatePrivateKeyInInternalStorage(context, algorithm)
                }
            if (generatedPath != null) {
                onGenerated(generatedPath)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.connection_private_key_generate_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

@Composable
internal fun rememberConnectionSshConfigPicker(
    onImported: (String) -> Unit,
    onFailed: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val configPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val content =
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                        reader?.readText()
                    }
                }.getOrNull()
            if (content.isNullOrBlank()) {
                onFailed()
            } else {
                onImported(content)
            }
        }
    return { configPicker.launch("*/*") }
}

@Composable
internal fun ConnectionPrivateKeyField(
    privateKeyPath: String,
    privateKeyPassphrase: String,
    onPrivateKeyPassphraseChange: (String) -> Unit,
    onPickPrivateKey: () -> Unit,
    onClearPrivateKey: () -> Unit,
    onGeneratePrivateKey: (SshKeyAlgorithm) -> Unit,
) {
    var showGenerateDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    OutlinedTextField(
        value = privateKeyPath,
        onValueChange = {},
        label = { Text(stringResource(R.string.connection_label_private_key_path)) },
        placeholder = { Text(stringResource(R.string.connection_private_key_placeholder)) },
        readOnly = true,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPickPrivateKey) {
            Text(stringResource(R.string.connection_pick_private_key))
        }
        OutlinedButton(onClick = { showGenerateDialog = true }) {
            Text(stringResource(R.string.connection_generate_private_key))
        }
        if (privateKeyPath.isNotBlank()) {
            TextButton(onClick = onClearPrivateKey) {
                Text(stringResource(R.string.connection_clear_private_key))
            }
        }
    }
    if (privateKeyPath.isNotBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    val publicKey = readPublicKeyText(privateKeyPath)
                    if (publicKey.isNullOrBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.connection_public_key_not_found),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        copyPublicKey(context, publicKey)
                    }
                },
            ) {
                Text(stringResource(R.string.connection_copy_public_key))
            }
            OutlinedButton(
                onClick = {
                    val publicKey = readPublicKeyText(privateKeyPath)
                    if (publicKey.isNullOrBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.connection_public_key_not_found),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        sharePublicKey(context, publicKey)
                    }
                },
            ) {
                Text(stringResource(R.string.connection_share_public_key))
            }
        }
    }
    OutlinedTextField(
        value = privateKeyPassphrase,
        onValueChange = onPrivateKeyPassphraseChange,
        label = { Text(stringResource(R.string.connection_label_private_key_passphrase_optional)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )

    if (showGenerateDialog) {
        GeneratePrivateKeyDialog(
            onDismiss = { showGenerateDialog = false },
            onSelectAlgorithm = { algorithm ->
                showGenerateDialog = false
                onGeneratePrivateKey(algorithm)
            },
        )
    }
}

@Composable
private fun GeneratePrivateKeyDialog(
    onDismiss: () -> Unit,
    onSelectAlgorithm: (SshKeyAlgorithm) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connection_generate_private_key_title)) },
        text = {
            Column {
                TextButton(onClick = { onSelectAlgorithm(SshKeyAlgorithm.ED25519) }) {
                    Text(stringResource(R.string.connection_generate_private_key_ed25519))
                }
                TextButton(onClick = { onSelectAlgorithm(SshKeyAlgorithm.RSA) }) {
                    Text(stringResource(R.string.connection_generate_private_key_rsa))
                }
                TextButton(onClick = { onSelectAlgorithm(SshKeyAlgorithm.ECDSA) }) {
                    Text(stringResource(R.string.connection_generate_private_key_ecdsa))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private fun importPrivateKeyToInternalStorage(
    context: Context,
    uri: Uri,
): String? {
    return runCatching {
        val fileName = sanitizeFileName(resolveDisplayName(context, uri) ?: "private_key")
        val targetDir = File(context.filesDir, "private_keys").apply { mkdirs() }
        val targetFile = File(targetDir, "${UUID.randomUUID()}_$fileName")
        val source = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        source.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        targetFile.absolutePath
    }.getOrNull()
}

private fun generatePrivateKeyInInternalStorage(
    context: Context,
    algorithm: SshKeyAlgorithm,
): String? {
    return runCatching {
        val targetDir = File(context.filesDir, "private_keys").apply { mkdirs() }
        val fileName = "${UUID.randomUUID()}_id_${algorithm.fileNameSuffix}"
        val targetFile = File(targetDir, fileName)
        val keyMaterial = SshKeyGenerator.generateSshKeyMaterial(algorithm)
        targetFile.writeText(keyMaterial.privateKeyPem)
        File(targetDir, "$fileName.pub").writeText(keyMaterial.publicKeyAuthorized + "\n")
        targetFile.absolutePath
    }.getOrNull()
}

private fun readPublicKeyText(privateKeyPath: String): String? {
    val sidecarFile = File("$privateKeyPath.pub")
    if (!sidecarFile.exists() || !sidecarFile.isFile) return null
    return runCatching { sidecarFile.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun copyPublicKey(
    context: Context,
    publicKey: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("ssh-public-key", publicKey))
    Toast.makeText(
        context,
        context.getString(R.string.connection_public_key_copy_success),
        Toast.LENGTH_SHORT,
    ).show()
}

private fun sharePublicKey(
    context: Context,
    publicKey: String,
) {
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, publicKey)
        }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.connection_share_public_key),
        ),
    )
}

private fun resolveDisplayName(
    context: Context,
    uri: Uri,
): String? =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }

private fun sanitizeFileName(name: String): String {
    val sanitized = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return sanitized.ifBlank { "private_key" }
}

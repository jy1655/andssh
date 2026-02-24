package com.opencode.sshterminal.ssh

import com.hierynomus.sshj.common.KeyDecryptionFailedException
import com.opencode.sshterminal.security.withZeroizedChars
import com.opencode.sshterminal.session.ConnectRequest
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.File
import java.security.PublicKey
import java.util.Base64

internal fun SSHClient.authenticate(request: ConnectRequest) {
    when {
        !request.password.isNullOrEmpty() ->
            withZeroizedChars(request.password) { passwordChars ->
                authPassword(request.username, requireNotNull(passwordChars))
            }
        !request.privateKeyPath.isNullOrEmpty() -> {
            val keyProvider = loadKeyProviderForAuth(request)
            try {
                authPublickey(request.username, keyProvider)
            } catch (authError: UserAuthException) {
                if (authError.hasCause<KeyDecryptionFailedException>()) {
                    error("Private key passphrase is required or incorrect")
                }
                throw authError
            }
        }
        else -> error("Either password or privateKeyPath must be provided")
    }
}

private fun SSHClient.loadKeyProviderForAuth(request: ConnectRequest) =
    with(request) {
        val privateKeyPath = requireNotNull(privateKeyPath)
        val certificatePath = certificatePath
        when {
            certificatePath.isNullOrEmpty() -> {
                if (privateKeyPassphrase.isNullOrEmpty()) {
                    loadKeys(privateKeyPath)
                } else {
                    withZeroizedChars(privateKeyPassphrase) { passphraseChars ->
                        loadKeys(privateKeyPath, requireNotNull(passphraseChars))
                    }
                }
            }

            privateKeyPassphrase.isNullOrEmpty() -> {
                loadKeys(
                    privateKeyPath,
                    certificatePath,
                    null as PasswordFinder?,
                )
            }

            else -> {
                withZeroizedChars(privateKeyPassphrase) { passphraseChars ->
                    loadKeys(
                        privateKeyPath,
                        certificatePath,
                        PasswordUtils.createOneOff(requireNotNull(passphraseChars)),
                    )
                }
            }
        }
    }

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return true
        current = current.cause
    }
    return false
}

internal fun ensureKnownHostsFile(path: String): File {
    val file = File(path)
    file.parentFile?.mkdirs()
    if (!file.exists()) {
        file.createNewFile()
    }
    return file
}

internal fun readKnownHostsLines(file: File): List<String> = if (file.exists()) file.readLines() else emptyList()

internal fun upsertKnownHostEntry(
    knownHostsFile: File,
    hostToken: String,
    key: PublicKey,
) {
    val keyType = KeyType.fromKey(key).toString()
    if (keyType == KeyType.UNKNOWN.toString()) return

    val keyBlob = Buffer.PlainBuffer().putPublicKey(key).compactData
    val keyBase64 = Base64.getEncoder().encodeToString(keyBlob)
    val newLine = "$hostToken $keyType $keyBase64"

    val existingLines = if (knownHostsFile.exists()) knownHostsFile.readLines() else emptyList()
    val updatedLines =
        existingLines.filterNot { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@filterNot false
            val parts = trimmed.split(Regex("\\s+"), limit = 3)
            if (parts.size < 2) return@filterNot false
            val hosts = parts[0].split(',')
            hosts.contains(hostToken) && parts[1] == keyType
        } + newLine

    knownHostsFile.writeText(updatedLines.joinToString(separator = "\n", postfix = "\n"))
}

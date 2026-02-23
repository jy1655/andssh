package com.opencode.sshterminal.ssh

import com.opencode.sshterminal.session.ConnectRequest
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import java.io.File
import java.security.PublicKey
import java.util.Base64

internal fun SSHClient.authenticate(request: ConnectRequest) {
    when {
        !request.password.isNullOrEmpty() -> authPassword(request.username, request.password)
        !request.privateKeyPath.isNullOrEmpty() -> {
            val keyProvider =
                if (request.privateKeyPassphrase.isNullOrEmpty()) {
                    loadKeys(request.privateKeyPath)
                } else {
                    loadKeys(request.privateKeyPath, request.privateKeyPassphrase)
                }
            authPublickey(request.username, keyProvider)
        }
        else -> error("Either password or privateKeyPath must be provided")
    }
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

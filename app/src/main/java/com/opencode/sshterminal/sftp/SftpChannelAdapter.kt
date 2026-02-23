package com.opencode.sshterminal.sftp

import com.opencode.sshterminal.session.ConnectRequest
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

interface SftpChannelAdapter : Closeable {
    suspend fun connect(request: ConnectRequest)

    override fun close()

    val isConnected: Boolean

    suspend fun list(remotePath: String): List<RemoteEntry>

    suspend fun exists(remotePath: String): Boolean

    suspend fun upload(
        localPath: String,
        remotePath: String,
    )

    suspend fun uploadStream(
        input: InputStream,
        remotePath: String,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)? = null,
    )

    suspend fun download(
        remotePath: String,
        localPath: String,
    )

    suspend fun downloadStream(
        remotePath: String,
        output: OutputStream,
        onProgress: ((Long, Long) -> Unit)? = null,
    )

    suspend fun mkdir(remotePath: String)

    suspend fun rm(remotePath: String)

    suspend fun rename(
        oldPath: String,
        newPath: String,
    )
}

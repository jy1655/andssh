package com.opencode.sshterminal.sftp

data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedEpochSec: Long,
)

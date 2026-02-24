package com.opencode.sshterminal.security

internal fun CharArray.zeroize() {
    fill('\u0000')
}

internal fun ByteArray.zeroize() {
    fill(0)
}

internal inline fun <T> withZeroizedChars(
    value: String?,
    block: (CharArray?) -> T,
): T {
    val chars = value?.toCharArray()
    return try {
        block(chars)
    } finally {
        chars?.zeroize()
    }
}

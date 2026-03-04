package com.opencode.sshterminal.crash

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        runCatching {
            writeReport(thread, throwable)
            trimReports()
        }.onFailure { reportError ->
            android.util.Log.e("CrashHandler", "Failed to write crash report", reportError)
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun writeReport(
        thread: Thread,
        throwable: Throwable,
    ) {
        val timestamp = System.currentTimeMillis()
        val reportFile = File(reportsDir(), "crash_$timestamp.txt")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersionName = packageInfo.versionName ?: "unknown"
        val appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        val stackTrace = StringWriter().also { writer -> throwable.printStackTrace(PrintWriter(writer)) }.toString()

        val body =
            buildString {
                appendLine("Timestamp: $timestamp")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("SDK: ${Build.VERSION.SDK_INT}")
                appendLine("App Version: $appVersionName ($appVersionCode)")
                appendLine("Thread: ${thread.name}")
                appendLine()
                appendLine("Stacktrace:")
                append(stackTrace)
            }

        reportFile.writeText(body)
    }

    private fun trimReports() {
        val files = reportsDir().listFiles { file -> file.isFile }?.sortedBy { it.lastModified() } ?: return
        val overflow = files.size - MAX_REPORTS
        if (overflow <= 0) return
        files.take(overflow).forEach { it.delete() }
    }

    private fun reportsDir(): File {
        return File(context.filesDir, REPORTS_DIR).apply { mkdirs() }
    }

    companion object {
        private const val REPORTS_DIR = "crash_reports"
        private const val MAX_REPORTS = 20
    }
}

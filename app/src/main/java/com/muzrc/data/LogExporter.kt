package com.muzrc.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.muzrc.model.ActivityLogEntry
import com.muzrc.model.LogActionType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale("ms", "MY"))

    fun formatCsv(dateLabel: String, entries: List<ActivityLogEntry>): String {
        val sb = StringBuilder()
        // UTF-8 BOM for clean Excel/Sheets opening
        sb.append('\uFEFF')
        sb.appendLine("Tarikh,Masa,Aplikasi,Nama Pakej,Tindakan,Kelantangan Sebelum (%),Kelantangan Diterapkan (%),Sebab")
        for (entry in entries) {
            val timeStr = timeFormatter.format(Date(entry.timestampMillis))
            sb.appendLine(
                listOf(
                    csvEscape(entry.dateKey),
                    csvEscape(timeStr),
                    csvEscape(entry.appName),
                    csvEscape(entry.packageName),
                    csvEscape(entry.actionType.labelMs),
                    entry.previousVolumePercent.toString(),
                    entry.appliedVolumePercent.toString(),
                    csvEscape(entry.reasonLabel)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun formatTextSummary(dateLabel: String, entries: List<ActivityLogEntry>): String {
        val mutedCount = entries.count { it.actionType != LogActionType.DIPULIHKAN }
        val restoredCount = entries.count { it.actionType == LogActionType.DIPULIHKAN }
        val topApp = entries
            .filter { it.actionType != LogActionType.DIPULIHKAN }
            .groupingBy { it.appName }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: "-"

        return buildString {
            appendLine("Laporan Aktiviti Harian SenyapPintar ($dateLabel)")
            appendLine("========================================")
            appendLine("Jumlah Pelarasan/Senyap : $mutedCount kali")
            appendLine("Jumlah Pemulihan Keluar : $restoredCount kali")
            appendLine("Aplikasi Paling Kerap   : $topApp")
            appendLine()
            appendLine("Butiran Aktiviti:")
            if (entries.isEmpty()) {
                appendLine("- Tiada rekod aktiviti untuk tarikh ini.")
            } else {
                entries.take(50).forEach { entry ->
                    val timeStr = timeFormatter.format(Date(entry.timestampMillis))
                    appendLine(
                        "• [$timeStr] ${entry.appName}: ${entry.actionType.labelMs} " +
                            "(${entry.previousVolumePercent}% -> ${entry.appliedVolumePercent}%) — ${entry.reasonLabel}"
                    )
                }
            }
        }
    }

    fun shareCsv(context: Context, dateKey: String, entries: List<ActivityLogEntry>): Result<Unit> {
        return try {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeDate = dateKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val file = File(exportsDir, "senyappintar_log_$safeDate.csv")
            file.writeText(formatCsv(dateKey, entries), Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Log Aktiviti SenyapPintar ($dateKey)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Eksport Fail CSV Log Aktiviti").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareTextSummary(context: Context, dateKey: String, entries: List<ActivityLogEntry>): Result<Unit> {
        return try {
            val text = formatTextSummary(dateKey, entries)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Ringkasan Aktiviti SenyapPintar ($dateKey)")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(shareIntent, "Kongsi Ringkasan Aktiviti").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}

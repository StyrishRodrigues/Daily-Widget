package com.example.dailywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MonthlyBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sharedPrefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)

        // Safety check: Abort if user turned the switch off
        if (!sharedPrefs.getBoolean("monthlyBackupEnabled", false)) return

        val folderUriStr = sharedPrefs.getString("backup_folder_uri", null) ?: return

        Thread {
            try {
                val folderUri = Uri.parse(folderUriStr)
                val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)

                if (documentFile != null && documentFile.canWrite()) {
                    var backupDir = documentFile.findFile("DailyWidget_Backups")
                    if (backupDir == null) backupDir = documentFile.createDirectory("DailyWidget_Backups")

                    var backupFile = backupDir?.findFile("events_backup.dwbak")
                    if (backupFile == null) backupFile = backupDir?.createFile("application/octet-stream", "events_backup.dwbak")

                    if (backupFile != null) {
                        val prefsJson = JSONObject()
                        for ((key, value) in sharedPrefs.all) { prefsJson.put(key, value) }
                        File(context.filesDir, "prefs_backup.json").writeText(prefsJson.toString())

                        context.contentResolver.openOutputStream(backupFile.uri, "wt")?.use { output ->
                            ZipOutputStream(output).use { zos ->
                                context.filesDir.listFiles()?.forEach { file ->
                                    if (file.isFile) {
                                        zos.putNextEntry(ZipEntry(file.name))
                                        file.inputStream().use { input -> input.copyTo(zos) }
                                        zos.closeEntry()
                                    }
                                }
                            }
                        }
                        File(context.filesDir, "prefs_backup.json").delete() // Cleanup
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // After backing up, automatically schedule the NEXT month's backup
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextIntent = Intent(context, MonthlyBackupReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 999, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, 1) // Jump to next month
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) // Set to the last day of that month
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 50)
            calendar.set(Calendar.SECOND, 0)

            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } catch (e: SecurityException) { e.printStackTrace() }

        }.start()
    }
}
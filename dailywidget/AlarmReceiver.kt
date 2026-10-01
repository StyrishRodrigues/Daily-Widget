package com.example.dailywidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sharedPrefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        if (!sharedPrefs.getBoolean("notifyEnabled", false)) return

        val currentDate = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())
        var eventTitle = ""
        var eventDesc = ""

        val combinedJson = JSONObject()
        if (sharedPrefs.getBoolean("showPresets", true)) {
            try {
                val presetObj = JSONObject(context.assets.open("highlights.json").bufferedReader().use { it.readText() })
                presetObj.keys().forEach { combinedJson.put(it, presetObj.get(it)) }
            } catch (e: Exception) { }
        }
        try {
            val userFile = File(context.filesDir, "user_events.json")
            if (userFile.exists()) {
                val userObj = JSONObject(userFile.readText())
                userObj.keys().forEach { combinedJson.put(it, userObj.get(it)) }
            }
        } catch (e: Exception) { }

        if (combinedJson.has(currentDate)) {
            val event = combinedJson.getJSONObject(currentDate)
            eventTitle = event.getString("title")
            eventDesc = event.optString("desc", "")
            if (eventTitle == "__DELETED__") eventTitle = ""
        }

        if (eventTitle.isEmpty()) return

        // If "Selected Events" is chosen, verify the checkbox was ticked
        if (sharedPrefs.getInt("notifyType", 0) == 1) {
            try {
                val notifyFile = File(context.filesDir, "notifications.json")
                if (!notifyFile.exists() || !JSONObject(notifyFile.readText()).optBoolean(currentDate, false)) return
            } catch (e: Exception) { return }
        }

        // FIRE THE NOTIFICATION!
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_widget_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Daily Events", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val appIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(eventTitle)
            .setContentText(eventDesc)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        manager.notify(currentDate.hashCode(), builder.build())
    }
}
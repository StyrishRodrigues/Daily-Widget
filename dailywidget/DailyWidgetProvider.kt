package com.example.dailywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class DailyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleMidnightUpdate(context)
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_daily_highlight)
        val sharedPrefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)

        val designChoice = sharedPrefs.getInt("clockDesign", 0)

        val backgroundRes = when (designChoice) {
            1 -> R.drawable.widget_bg_neon
            2 -> R.drawable.widget_bg_sunset
            3 -> R.drawable.widget_bg_glass
            else -> R.drawable.widget_bg_glass
        }

        val opacity = sharedPrefs.getInt("bgOpacity", 100)
        val alphaInt = (opacity * 2.55).toInt()

        views.setImageViewResource(R.id.widget_bg_image, backgroundRes)
        views.setInt(R.id.widget_bg_image, "setImageAlpha", alphaInt)
        views.setInt(R.id.widget_root, "setBackgroundResource", 0)

        val clockDesign = sharedPrefs.getInt("clockDesign", 0)
        for (i in 0..10) {
            val layoutId = context.resources.getIdentifier("clock_design_$i", "id", context.packageName)
            if (layoutId != 0) {
                views.setViewVisibility(layoutId, if (i == clockDesign) View.VISIBLE else View.GONE)
            }
        }

        val clockSize = sharedPrefs.getInt("clockSize", 44).toFloat()
        val titleSize = sharedPrefs.getInt("titleSize", 16).toFloat()
        val descSize = sharedPrefs.getInt("descSize", 13).toFloat()

        views.setTextViewTextSize(R.id.tv_title, TypedValue.COMPLEX_UNIT_SP, titleSize)
        views.setTextViewTextSize(R.id.tv_description, TypedValue.COMPLEX_UNIT_SP, descSize)

        views.setTextViewTextSize(R.id.clock_time_0, TypedValue.COMPLEX_UNIT_SP, clockSize)
        views.setTextViewTextSize(R.id.clock_time_1, TypedValue.COMPLEX_UNIT_SP, clockSize)
        views.setTextViewTextSize(R.id.clock_time_2, TypedValue.COMPLEX_UNIT_SP, clockSize)
        views.setTextViewTextSize(R.id.clock_time_3, TypedValue.COMPLEX_UNIT_SP, clockSize)
        views.setTextViewTextSize(R.id.clock_time_4, TypedValue.COMPLEX_UNIT_SP, clockSize)

        // --- BATTERY ENGINE ---
        val showTemp = sharedPrefs.getBoolean("showTemp", true)
        if (showTemp) {
            views.setViewVisibility(R.id.tv_temperature, View.VISIBLE)
            var tempInt = 0

            try {
                val file = File("/sys/class/power_supply/battery/temp")
                if (file.exists()) {
                    tempInt = file.readText().trim().toInt()
                }
            } catch (e: Exception) { }

            if (tempInt == 0 || tempInt == 250) {
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    context.registerReceiver(null, ifilter)
                }
                val intentTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                if (intentTemp != 0) {
                    tempInt = intentTemp
                }
            }

            val tempCelsius = when {
                tempInt > 1000 -> tempInt / 1000.0f
                tempInt > 100 || tempInt < -100 -> tempInt / 10.0f
                else -> tempInt.toFloat()
            }

            val formattedTemp = String.format(Locale.US, "%.1f", tempCelsius)
            views.setTextViewText(R.id.tv_temperature, "Phone: $formattedTemp°C")
        } else {
            views.setViewVisibility(R.id.tv_temperature, View.GONE)
        }

        // --- WEATHER ENGINE ---
        val showWeather = sharedPrefs.getBoolean("showWeather", true)
        if (showWeather) {
            views.setViewVisibility(R.id.tv_weather, View.VISIBLE)
            Thread {
                try {
                    val rawCity = sharedPrefs.getString("weatherCity", "Mangalore") ?: "Mangalore"
                    val targetCity = rawCity.trim().replace(" ", "%20")
                    val apiKey = "a4b2a2ae3c41d4f3ffb801e951bc429f"
                    val cacheBuster = System.currentTimeMillis()

                    var urlString = "https://api.openweathermap.org/data/2.5/weather?q=$targetCity&units=metric&appid=$apiKey&cb=$cacheBuster"
                    var connection = URL(urlString).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.useCaches = false
                    connection.setRequestProperty("Cache-Control", "no-cache")

                    if (connection.responseCode == 404) {
                        urlString = "https://api.openweathermap.org/data/2.5/weather?q=$targetCity,IN&units=metric&appid=$apiKey&cb=$cacheBuster"
                        connection = URL(urlString).openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.useCaches = false
                        connection.setRequestProperty("Cache-Control", "no-cache")
                    }

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().readText()
                        val json = JSONObject(response)

                        val mainNode = json.getJSONObject("main")
                        val temp = mainNode.getDouble("temp").toInt()
                        val humidity = mainNode.optInt("humidity", 0)

                        val weatherDesc = json.getJSONArray("weather").getJSONObject(0).getString("description")
                        val formattedDesc = weatherDesc.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

                        views.setTextViewText(R.id.tv_weather, "☁️ $temp°C   💧 $humidity%\n$formattedDesc")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    } else if (connection.responseCode == 404) {
                        views.setTextViewText(R.id.tv_weather, "City not found\n(Tap to retry)")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    } else {
                        views.setTextViewText(R.id.tv_weather, "API Error: ${connection.responseCode}\n(Tap to retry)")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e: Exception) {
                    // This now lets the user know they can tap to manually trigger a refresh
                    views.setTextViewText(R.id.tv_weather, "No Internet\n(Tap to retry)")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }.start()
        } else {
            views.setViewVisibility(R.id.tv_weather, View.GONE)
        }

        populateEvents(context, views)

        val deadIntent = PendingIntent.getBroadcast(
            context, appWidgetId, Intent("IGNORE_TOUCH_ACTION"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, deadIntent)

        val isEscapeEnabled = sharedPrefs.getBoolean("escapeHatchEnabled", false)
        if (isEscapeEnabled) {
            val tapIntent = Intent(context, DailyWidgetProvider::class.java).apply {
                action = "ESCAPE_HATCH_TAP"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val tapPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_escape_hatch, tapPendingIntent)
        } else {
            views.setOnClickPendingIntent(R.id.btn_escape_hatch, deadIntent)
        }

        val isTimerRunning = sharedPrefs.getBoolean("fake_call_running", false)
        views.setViewVisibility(R.id.iv_fake_call_indicator, if (isTimerRunning) View.VISIBLE else View.GONE)

        // --- TAP-TO-REFRESH WEATHER FIX ---
        val refreshIntent = Intent(context, DailyWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val refreshPending = PendingIntent.getBroadcast(
            context, appWidgetId + 555, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.tv_weather, refreshPending)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun populateEvents(context: Context, views: RemoteViews) {
        val sharedPrefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        var userJson = JSONObject()
        try {
            val userFile = File(context.filesDir, "user_events.json")
            if (userFile.exists()) userJson = JSONObject(userFile.readText())
        } catch (e: Exception) {}

        var presetJson = JSONObject()
        try {
            presetJson = JSONObject(context.assets.open("highlights.json").bufferedReader().use { it.readText() })
        } catch (e: Exception) {}

        val calendar = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("MM-dd", Locale.getDefault())

        var todayTitle: String? = null
        var todayDesc: String? = null
        var nextTitle: String? = null
        var nextDateStr: String? = null

        val todayStr = dateFormat.format(calendar.time)
        var todayIsDeleted = false

        if (userJson.has(todayStr)) {
            val obj = userJson.getJSONObject(todayStr)
            val tempTitle = obj.optString("title", "")
            if (tempTitle == "__DELETED__") {
                todayIsDeleted = true
            } else if (tempTitle.isNotEmpty()) {
                todayTitle = tempTitle
                todayDesc = obj.optString("desc", "")
            }
        }

        if (todayTitle == null && !todayIsDeleted && presetJson.has(todayStr)) {
            val obj = presetJson.getJSONObject(todayStr)
            todayTitle = obj.optString("title", "")
            todayDesc = obj.optString("desc", "")
        }

        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)

        for (i in 1..365) {
            val dateStr = dateFormat.format(calendar.time)
            var title = ""
            var isDeleted = false

            if (userJson.has(dateStr)) {
                val obj = userJson.getJSONObject(dateStr)
                val tempTitle = obj.optString("title", "")
                if (tempTitle == "__DELETED__") {
                    isDeleted = true
                } else if (tempTitle.isNotEmpty()) {
                    title = tempTitle
                }
            }

            if (title.isEmpty() && !isDeleted && presetJson.has(dateStr)) {
                val obj = presetJson.getJSONObject(dateStr)
                title = obj.optString("title", "")
            }

            if (title.isNotEmpty()) {
                nextTitle = title
                nextDateStr = if (i == 1) "Tomorrow" else dateStr
                break
            }
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        val showTitle = sharedPrefs.getBoolean("showTitle", true)
        val showDesc = sharedPrefs.getBoolean("showDesc", true)
        val showUpcoming = sharedPrefs.getBoolean("showUpcoming", true)

        if (todayTitle != null && showTitle) {
            views.setViewVisibility(R.id.tv_title, View.VISIBLE)
            views.setTextViewText(R.id.tv_title, todayTitle)

            if (!todayDesc.isNullOrEmpty() && showDesc) {
                views.setViewVisibility(R.id.tv_description, View.VISIBLE)
                views.setTextViewText(R.id.tv_description, todayDesc)
            } else {
                views.setViewVisibility(R.id.tv_description, View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.tv_title, View.GONE)
            views.setViewVisibility(R.id.tv_description, View.GONE)
        }

        if (nextTitle != null && showUpcoming) {
            views.setViewVisibility(R.id.tv_upcoming_event, View.VISIBLE)
            views.setTextViewText(R.id.tv_upcoming_event, "Next: $nextTitle ($nextDateStr)")
        } else {
            views.setViewVisibility(R.id.tv_upcoming_event, View.GONE)
        }
    }

    private fun scheduleMidnightUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, DailyWidgetProvider::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 888, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 5)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == Intent.ACTION_DATE_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, DailyWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }

        if (intent.action == "ESCAPE_HATCH_TAP") {
            val prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("escapeHatchEnabled", false)) return

            val now = System.currentTimeMillis()
            val tapsStr = prefs.getString("widget_taps", "") ?: ""
            val taps = tapsStr.split(",").mapNotNull { it.toLongOrNull() }.toMutableList()

            taps.add(now)
            taps.removeAll { now - it > 3500 }
            prefs.edit().putString("widget_taps", taps.joinToString(",")).apply()

            val isTimerRunning = prefs.getBoolean("fake_call_running", false)
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val callIntent = Intent(context, FakeCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 999, callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (isTimerRunning && taps.size >= 3) {
                prefs.edit().putBoolean("fake_call_running", false).putString("widget_taps", "").apply()
                alarmManager.cancel(pendingIntent)
                Toast.makeText(context, "Fake Call Aborted", Toast.LENGTH_SHORT).show()
                updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId)

            } else if (!isTimerRunning && taps.size >= 5) {
                prefs.edit().putBoolean("fake_call_running", true).putString("widget_taps", "").apply()
                val delay = prefs.getInt("fake_call_delay", 10) * 1000L

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + delay, pendingIntent)
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + delay, pendingIntent)
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }

                updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
            }
        }

        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, DailyWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
}
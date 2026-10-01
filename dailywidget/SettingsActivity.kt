package com.example.dailywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SettingsActivity : AppCompatActivity() {

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val pfpFile = File(filesDir, "fake_caller_pfp.jpg")
                    contentResolver.openInputStream(uri)?.use { input -> pfpFile.outputStream().use { output -> input.copyTo(output) } }
                    getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE).edit().putString("fake_caller_pfp", Uri.fromFile(pfpFile).toString()).apply()
                    Toast.makeText(this, "Profile Picture Saved Permanently!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(this, "Error saving picture", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val audioFile = File(filesDir, "fake_caller_audio.mp3")
                    contentResolver.openInputStream(uri)?.use { input -> audioFile.outputStream().use { output -> input.copyTo(output) } }
                    getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE).edit().putString("fake_caller_audio", Uri.fromFile(audioFile).toString()).apply()
                    Toast.makeText(this, "Voice Note Saved Permanently!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(this, "Error saving audio", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // =========================================================
    // MODERN LOADING PROGRESS UI
    // =========================================================
    private var progressDialog: AlertDialog? = null

    private fun showLoading(message: String) {
        val cardView = androidx.cardview.widget.CardView(this).apply {
            setCardBackgroundColor(android.graphics.Color.parseColor("#22272E"))
            radius = 30f
            cardElevation = 20f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(60, 60, 60, 60)
            gravity = android.view.Gravity.CENTER_VERTICAL

            addView(ProgressBar(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 40, 0) }
                indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#238636"))
            })

            addView(TextView(this@SettingsActivity).apply {
                text = message
                textSize = 18f
                setTextColor(android.graphics.Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
        }

        cardView.addView(layout)

        progressDialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setCancelable(false)
            .setView(cardView)
            .create()

        progressDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        progressDialog?.show()
    }

    private fun hideLoading() {
        progressDialog?.dismiss()
    }

    // =========================================================
    // SILENT BACKUP & RESTORE ENGINE (Folder Linking & ZIP Archive)
    // =========================================================
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE).edit().putString("backup_folder_uri", it.toString()).apply()
            Toast.makeText(this, "Folder Linked! You can now Backup or Sync.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSilentBackup() {
        val sharedPrefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        val folderUriStr = sharedPrefs.getString("backup_folder_uri", null)

        if (folderUriStr == null) {
            Toast.makeText(this, "Please link a Backup Folder first!", Toast.LENGTH_LONG).show()
            folderPickerLauncher.launch(null)
            return
        }

        showLoading("Creating Secure Backup...")

        Thread {
            try {
                val folderUri = Uri.parse(folderUriStr)
                val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, folderUri)

                if (documentFile != null && documentFile.canWrite()) {
                    var backupDir = documentFile.findFile("DailyWidget_Backups")
                    if (backupDir == null) backupDir = documentFile.createDirectory("DailyWidget_Backups")

                    var backupFile = backupDir?.findFile("events_backup.dwbak")
                    if (backupFile == null) backupFile = backupDir?.createFile("application/octet-stream", "events_backup.dwbak")

                    if (backupFile != null) {
                        // Dump all slider positions, deleted presets, and settings into a temp JSON file
                        val prefsJson = JSONObject()
                        for ((key, value) in sharedPrefs.all) { prefsJson.put(key, value) }
                        File(filesDir, "prefs_backup.json").writeText(prefsJson.toString())

                        // Zip everything (settings, events, fake call media) into the .dwbak file
                        contentResolver.openOutputStream(backupFile.uri, "wt")?.use { output ->
                            ZipOutputStream(output).use { zos ->
                                filesDir.listFiles()?.forEach { file ->
                                    if (file.isFile) {
                                        zos.putNextEntry(ZipEntry(file.name))
                                        file.inputStream().use { input -> input.copyTo(zos) }
                                        zos.closeEntry()
                                    }
                                }
                            }
                        }

                        File(filesDir, "prefs_backup.json").delete() // Cleanup temp file

                        runOnUiThread {
                            hideLoading()
                            Toast.makeText(this, "Full App Backup Secured!", Toast.LENGTH_LONG).show()
                        }
                    } else throw Exception("Cannot create backup file")
                } else throw Exception("Folder access lost")
            } catch (e: Exception) {
                runOnUiThread {
                    hideLoading()
                    Toast.makeText(this, "Backup Failed. Please re-link folder.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun performSilentSync() {
        val sharedPrefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        val folderUriStr = sharedPrefs.getString("backup_folder_uri", null)

        if (folderUriStr == null) {
            Toast.makeText(this, "Please link a Backup Folder first!", Toast.LENGTH_LONG).show()
            folderPickerLauncher.launch(null)
            return
        }

        showLoading("Restoring App Data...")

        Thread {
            try {
                val folderUri = Uri.parse(folderUriStr)
                val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, folderUri)

                val backupDir = documentFile?.findFile("DailyWidget_Backups")
                val backupFile = backupDir?.findFile("events_backup.dwbak")

                if (backupFile != null && backupFile.canRead()) {

                    // Unzip everything directly into the internal files directory
                    contentResolver.openInputStream(backupFile.uri)?.use { input ->
                        ZipInputStream(input).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val outFile = File(filesDir, entry.name)
                                outFile.outputStream().use { output -> zis.copyTo(output) }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }

                    // Re-apply all sliders, fake call paths, and widget preferences
                    val prefsFile = File(filesDir, "prefs_backup.json")
                    if (prefsFile.exists()) {
                        val prefsJson = JSONObject(prefsFile.readText())
                        val editor = sharedPrefs.edit()
                        val keys = prefsJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            when (val value = prefsJson.get(key)) {
                                is Boolean -> editor.putBoolean(key, value)
                                is Int -> editor.putInt(key, value)
                                is Double -> editor.putFloat(key, value.toFloat())
                                is Long -> editor.putLong(key, value)
                                is String -> editor.putString(key, value)
                            }
                        }
                        editor.apply()
                        prefsFile.delete() // Cleanup temp file
                    }

                    runOnUiThread {
                        hideLoading()
                        Toast.makeText(this, "App Restored Successfully!", Toast.LENGTH_SHORT).show()
                        pushWidgetUpdate()
                        recreate() // Forces Settings UI to refresh its toggles and sliders immediately
                    }
                } else {
                    runOnUiThread {
                        hideLoading()
                        Toast.makeText(this, "No backup file found. Make a backup first!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    hideLoading()
                    Toast.makeText(this, "Restore Failed: Data Corrupt", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun pushWidgetUpdate() {
        val intent = Intent(this, DailyWidgetProvider::class.java).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE }
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, DailyWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val sharedPrefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)

        // UI Bindings mapping directly to your provided XML
        val btnClockDesign = findViewById<TextView>(R.id.btn_clock_design)
        val btnClockMinus = findViewById<View>(R.id.btn_clock_minus)
        val btnClockPlus = findViewById<View>(R.id.btn_clock_plus)
        val seekBarClock = findViewById<SeekBar>(R.id.seekbar_font_size)

        val switchTitle = findViewById<Switch>(R.id.switch_title_display)
        val switchDesc = findViewById<Switch>(R.id.switch_desc_display)
        val layoutTitleSize = findViewById<LinearLayout>(R.id.layout_title_size)
        val seekTitleSize = findViewById<SeekBar>(R.id.seekbar_title_size)
        val layoutDescSize = findViewById<LinearLayout>(R.id.layout_desc_size)
        val seekDescSize = findViewById<SeekBar>(R.id.seekbar_desc_size)

        val seekOpacity = findViewById<SeekBar>(R.id.seekbar_opacity)
        val switchTemp = findViewById<Switch>(R.id.switch_temp_display)
        val switchWeather = findViewById<Switch>(R.id.switch_weather_display)
        val btnWeatherCity = findViewById<TextView>(R.id.btn_weather_city)
        val switchUpcoming = findViewById<Switch>(R.id.switch_upcoming_display)

        val switchNotify = findViewById<Switch>(R.id.switch_notifications)
        val layoutNotifySettings = findViewById<LinearLayout>(R.id.layout_notification_settings)
        val btnNotifyType = findViewById<TextView>(R.id.btn_notify_type)
        val btnTimePicker = findViewById<Button>(R.id.btn_time_picker)

        val switchEscape = findViewById<Switch>(R.id.switch_escape_hatch)
        val layoutEscape = findViewById<LinearLayout>(R.id.layout_escape_settings)
        val etCallerName = findViewById<EditText>(R.id.et_fake_caller_name)
        val etCallerNumber = findViewById<EditText>(R.id.et_fake_caller_number)
        val seekDelay = findViewById<SeekBar>(R.id.seekbar_call_delay)
        val tvDelay = findViewById<TextView>(R.id.tv_delay_timer)
        val btnUploadPfp = findViewById<Button>(R.id.btn_upload_pfp)
        val btnUploadAudio = findViewById<Button>(R.id.btn_upload_audio)

        // Initializing UI from SharedPrefs
        val showTitle = sharedPrefs.getBoolean("showTitle", true)
        val showDesc = sharedPrefs.getBoolean("showDesc", true)
        switchTitle.isChecked = showTitle
        switchDesc.isChecked = showDesc
        layoutTitleSize.visibility = if (showTitle) View.VISIBLE else View.GONE
        layoutDescSize.visibility = if (showDesc) View.VISIBLE else View.GONE

        switchTitle.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("showTitle", isChecked).apply()
            layoutTitleSize.visibility = if (isChecked) View.VISIBLE else View.GONE
            pushWidgetUpdate()
        }

        switchDesc.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("showDesc", isChecked).apply()
            layoutDescSize.visibility = if (isChecked) View.VISIBLE else View.GONE
            pushWidgetUpdate()
        }

        val designs = arrayOf("Digital: Default", "Digital: Stacked", "Digital: Minimal", "Digital: Tech", "Digital: Green", "Analog: Classic Light", "Analog: Modern Dark", "Analog: Mint Accent", "Analog: Deep Blue", "Analog: Minimalist", "Card: Split Weather & Event")
        val notifyTypes = arrayOf("Notify All Events", "Notify Selected Events Only")
        val cities = arrayOf("Mangalore", "Udupi", "Manipal", "Moodbidri", "Kasaragod", "Surathkal")

        btnClockDesign.text = "${designs[sharedPrefs.getInt("clockDesign", 0)]} ▼"
        btnNotifyType.text = "${notifyTypes[sharedPrefs.getInt("notifyType", 0)]} ▼"

        val currentCity = sharedPrefs.getString("weatherCity", "Mangalore")
        val cityIndex = cities.indexOf(currentCity).takeIf { it >= 0 } ?: 0
        btnWeatherCity.text = "${cities[cityIndex]} ▼"
        btnWeatherCity.isEnabled = sharedPrefs.getBoolean("showWeather", true)
        btnWeatherCity.alpha = if (sharedPrefs.getBoolean("showWeather", true)) 1.0f else 0.5f

        seekBarClock.progress = sharedPrefs.getInt("clockSize", 44)
        seekTitleSize.progress = sharedPrefs.getInt("titleSize", 16)
        seekDescSize.progress = sharedPrefs.getInt("descSize", 13)
        seekOpacity.progress = sharedPrefs.getInt("bgOpacity", 100)
        switchTemp.isChecked = sharedPrefs.getBoolean("showTemp", true)
        switchWeather.isChecked = sharedPrefs.getBoolean("showWeather", true)
        switchUpcoming.isChecked = sharedPrefs.getBoolean("showUpcoming", true)

        switchNotify.isChecked = sharedPrefs.getBoolean("notifyEnabled", false)
        layoutNotifySettings.visibility = if (switchNotify.isChecked) View.VISIBLE else View.GONE

        val savedHour = sharedPrefs.getInt("notifyHour", 8)
        val savedMin = sharedPrefs.getInt("notifyMinute", 0)
        btnTimePicker.text = String.format("Notify At: %02d:%02d", savedHour, savedMin)

        // Listeners for Dialogs
        btnClockDesign.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_modern_picker, null)
            val listView = dialogView.findViewById<ListView>(R.id.lv_modern_options)
            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, designs) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(android.graphics.Color.WHITE)
                    view.textSize = 15f
                    view.setPadding(16, 20, 16, 20)
                    return view
                }
            }
            listView.adapter = adapter
            listView.choiceMode = ListView.CHOICE_MODE_SINGLE
            listView.setItemChecked(sharedPrefs.getInt("clockDesign", 0), true)
            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar).setView(dialogView).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            listView.setOnItemClickListener { _, _, which, _ ->
                sharedPrefs.edit().putInt("clockDesign", which).apply(); btnClockDesign.text = "${designs[which]} ▼"; pushWidgetUpdate(); dialog.dismiss()
            }
            dialog.show()
        }

        btnNotifyType.setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Notification Mode").setSingleChoiceItems(notifyTypes, sharedPrefs.getInt("notifyType", 0)) { dialog, which ->
                sharedPrefs.edit().putInt("notifyType", which).apply(); btnNotifyType.text = "${notifyTypes[which]} ▼"; dialog.dismiss()
            }.show()
        }

        btnWeatherCity.setOnClickListener {
            val currentIndex = cities.indexOf(sharedPrefs.getString("weatherCity", "Mangalore")).takeIf { it >= 0 } ?: 0
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Weather Location").setSingleChoiceItems(cities, currentIndex) { dialog, which ->
                sharedPrefs.edit().putString("weatherCity", cities[which]).apply(); btnWeatherCity.text = "${cities[which]} ▼"; pushWidgetUpdate(); dialog.dismiss()
            }.show()
        }

        switchNotify.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("notifyEnabled", isChecked).apply(); layoutNotifySettings.visibility = if (isChecked) View.VISIBLE else View.GONE
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(this, 100, Intent(this, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (!isChecked) { alarmManager.cancel(pendingIntent) } else {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis(); set(Calendar.HOUR_OF_DAY, sharedPrefs.getInt("notifyHour", 8)); set(Calendar.MINUTE, sharedPrefs.getInt("notifyMinute", 0)); set(Calendar.SECOND, 0)
                    if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
                }
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
            }
        }

        btnTimePicker.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                sharedPrefs.edit().putInt("notifyHour", h).putInt("notifyMinute", m).apply(); btnTimePicker.text = String.format("Notify At: %02d:%02d", h, m); switchNotify.isChecked = false; switchNotify.isChecked = true
            }, sharedPrefs.getInt("notifyHour", 8), sharedPrefs.getInt("notifyMinute", 0), false).show()
        }

        // Listeners for Sliders & Toggles
        btnClockMinus.setOnClickListener { val currentSize = sharedPrefs.getInt("clockSize", 44); if (currentSize > 20) { seekBarClock.progress = currentSize - 1; sharedPrefs.edit().putInt("clockSize", currentSize - 1).apply(); pushWidgetUpdate() } }
        btnClockPlus.setOnClickListener { val currentSize = sharedPrefs.getInt("clockSize", 44); if (currentSize < seekBarClock.max) { seekBarClock.progress = currentSize + 1; sharedPrefs.edit().putInt("clockSize", currentSize + 1).apply(); pushWidgetUpdate() } }
        seekBarClock.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { sharedPrefs.edit().putInt("clockSize", if (p<20) 20 else p).apply() } override fun onStartTrackingTouch(s: SeekBar?) {} override fun onStopTrackingTouch(s: SeekBar?) { pushWidgetUpdate() } })
        seekTitleSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { sharedPrefs.edit().putInt("titleSize", if (p<10) 10 else p).apply() } override fun onStartTrackingTouch(s: SeekBar?) {} override fun onStopTrackingTouch(s: SeekBar?) { pushWidgetUpdate() } })
        seekDescSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { sharedPrefs.edit().putInt("descSize", if (p<8) 8 else p).apply() } override fun onStartTrackingTouch(s: SeekBar?) {} override fun onStopTrackingTouch(s: SeekBar?) { pushWidgetUpdate() } })
        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { sharedPrefs.edit().putInt("bgOpacity", p).apply() } override fun onStartTrackingTouch(s: SeekBar?) {} override fun onStopTrackingTouch(s: SeekBar?) { pushWidgetUpdate() } })

        switchTemp.setOnCheckedChangeListener { _, isChecked -> sharedPrefs.edit().putBoolean("showTemp", isChecked).apply(); pushWidgetUpdate() }
        switchWeather.setOnCheckedChangeListener { _, isChecked -> sharedPrefs.edit().putBoolean("showWeather", isChecked).apply(); btnWeatherCity.isEnabled = isChecked; btnWeatherCity.alpha = if (isChecked) 1.0f else 0.5f; pushWidgetUpdate() }
        switchUpcoming.setOnCheckedChangeListener { _, isChecked -> sharedPrefs.edit().putBoolean("showUpcoming", isChecked).apply(); pushWidgetUpdate() }

        // Fake Call Setup
        val isEscapeEnabled = sharedPrefs.getBoolean("escapeHatchEnabled", false)
        switchEscape.isChecked = isEscapeEnabled
        layoutEscape.visibility = if (isEscapeEnabled) View.VISIBLE else View.GONE
        etCallerName.setText(sharedPrefs.getString("fake_caller_name", ""))
        etCallerNumber.setText(sharedPrefs.getString("fake_caller_number", ""))

        etCallerName.addTextChangedListener(object : android.text.TextWatcher { override fun afterTextChanged(s: android.text.Editable?) { sharedPrefs.edit().putString("fake_caller_name", s.toString().trim()).apply() } override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {} })
        etCallerNumber.addTextChangedListener(object : android.text.TextWatcher { override fun afterTextChanged(s: android.text.Editable?) { sharedPrefs.edit().putString("fake_caller_number", s.toString().trim()).apply() } override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {} })

        val savedDelay = sharedPrefs.getInt("fake_call_delay", 10)
        seekDelay.progress = savedDelay - 1
        tvDelay.text = "Call Delay Timer: ${savedDelay}s"

        switchEscape.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(this)) {
                switchEscape.isChecked = false; Toast.makeText(this, "Please allow 'Display over other apps' first!", Toast.LENGTH_LONG).show(); startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                return@setOnCheckedChangeListener
            }
            sharedPrefs.edit().putBoolean("escapeHatchEnabled", isChecked).apply()
            layoutEscape.visibility = if (isChecked) View.VISIBLE else View.GONE
            pushWidgetUpdate()
            if (isChecked) {
                val dialogView = layoutInflater.inflate(R.layout.dialog_fake_call_tutorial, null)
                val videoView = dialogView.findViewById<VideoView>(R.id.video_tutorial)
                videoView.setVideoURI(android.net.Uri.parse("android.resource://$packageName/${R.raw.fake_call_tutorial}"))
                videoView.setOnPreparedListener { mediaPlayer -> mediaPlayer.isLooping = true; videoView.start() }
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("📞 Fake Call Feature Armed").setView(dialogView).setPositiveButton("OKAY") { dialog, _ -> dialog.dismiss() }.show()
            }
        }

        seekDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { tvDelay.text = "Call Delay Timer: ${p + 1}s"; sharedPrefs.edit().putInt("fake_call_delay", p + 1).apply() } override fun onStartTrackingTouch(s: SeekBar?) {} override fun onStopTrackingTouch(s: SeekBar?) {} })
        btnUploadPfp.setOnClickListener { pickImageLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }) }
        btnUploadAudio.setOnClickListener { pickAudioLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*" }) }

        // Backup & Sync Setup
        findViewById<Button>(R.id.btn_backup_data).setOnClickListener { performSilentBackup() }
        findViewById<Button>(R.id.btn_restore_data).setOnClickListener { performSilentSync() }
        findViewById<Button>(R.id.btn_change_folder).setOnClickListener { folderPickerLauncher.launch(null) }

        // --- NEW MONTHLY BACKUP TOGGLE LOGIC ---
        val switchMonthlyBackup = findViewById<Switch>(R.id.switch_monthly_backup)
        switchMonthlyBackup.isChecked = sharedPrefs.getBoolean("monthlyBackupEnabled", false)

        switchMonthlyBackup.setOnCheckedChangeListener { _, isChecked ->
            // If user checks the box, ensure a folder is actually linked first
            if (isChecked && sharedPrefs.getString("backup_folder_uri", null) == null) {
                switchMonthlyBackup.isChecked = false
                Toast.makeText(this, "Please link a Backup Folder first!", Toast.LENGTH_LONG).show()
                folderPickerLauncher.launch(null)
                return@setOnCheckedChangeListener
            }

            sharedPrefs.edit().putBoolean("monthlyBackupEnabled", isChecked).apply()

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, MonthlyBackupReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(this, 999, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            if (isChecked) {
                // Calculate the exact end of the current month
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 50)
                calendar.set(Calendar.SECOND, 0)

                // If it is somehow past 11:50 PM on the last day, bump it to next month
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.MONTH, 1)
                    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                }

                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    Toast.makeText(this, "Monthly Auto-Backup Enabled!", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) { e.printStackTrace() }
            } else {
                alarmManager.cancel(pendingIntent)
                Toast.makeText(this, "Monthly Auto-Backup Disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
package com.example.dailywidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var containerEvents: LinearLayout
    private lateinit var userFile: File
    private lateinit var notifyFile: File
    private lateinit var drawerLayout: DrawerLayout
    private var currentFilter = 0

    private val monthViewMap = mutableMapOf<String, View>()

    private val currentAppVersionCode = 4
    private val firebaseUpdateUrl = "https://dailywidget-865ec-default-rtdb.asia-southeast1.firebasedatabase.app/app_update.json"

    // =========================================================
    // SILENT SYNC ENGINE (Folder Linking with .dwbak armor)
    // =========================================================
    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE).edit().putString("backup_folder_uri", it.toString()).apply()
            performSilentSync()
        }
    }

    private fun performSilentSync() {
        val sharedPrefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        val folderUriStr = sharedPrefs.getString("backup_folder_uri", null)

        if (folderUriStr == null) {
            Toast.makeText(this, "Please link a Backup Folder first! Opening picker...", Toast.LENGTH_LONG).show()
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
                    contentResolver.openInputStream(backupFile.uri)?.use { input ->
                        java.util.zip.ZipInputStream(input).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val outFile = File(filesDir, entry.name)
                                outFile.outputStream().use { output -> zis.copyTo(output) }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }

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
                        prefsFile.delete()
                    }

                    runOnUiThread {
                        hideLoading()
                        Toast.makeText(this, "Data Synced Automatically!", Toast.LENGTH_SHORT).show()
                        refreshDynamicList()
                        pushWidgetUpdate()
                    }
                } else {
                    runOnUiThread {
                        hideLoading()
                        Toast.makeText(this, "No backup file found. Make a backup in Settings first!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    hideLoading()
                    Toast.makeText(this, "Sync Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        checkForUpdates()

        drawerLayout = findViewById(R.id.drawer_layout)
        findViewById<ImageView>(R.id.btn_menu).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        // --- UPDATED NAVIGATION LISTENER ---
        findViewById<NavigationView>(R.id.nav_view).setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_how_to_use -> startActivity(Intent(this, HowToUseActivity::class.java))
                R.id.nav_contact -> startActivity(Intent(this, ContactUsActivity::class.java))
                R.id.nav_reviews -> startActivity(Intent(this, ReviewsActivity::class.java))

                // Brings back your QR / Payment details page
                R.id.nav_support_us -> startActivity(Intent(this, AboutActivity::class.java))

                // The new legal/credits page we just made
                R.id.nav_about_app -> startActivity(Intent(this, AboutAppActivity::class.java))
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        val etDate = findViewById<TextView>(R.id.et_date)
        val etTitle = findViewById<EditText>(R.id.et_title)
        val etDesc = findViewById<EditText>(R.id.et_desc)
        val layoutActionButtons = findViewById<LinearLayout>(R.id.layout_action_buttons)
        val btnSyncData = findViewById<ImageView>(R.id.btn_sync_data)
        containerEvents = findViewById(R.id.container_events)

        btnSyncData.setOnClickListener {
            performSilentSync()
        }

        etDate.setOnClickListener {
            val dialog = android.app.Dialog(this)
            dialog.setContentView(R.layout.dialog_modern_date_picker)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

            val monthPicker = dialog.findViewById<NumberPicker>(R.id.picker_month)
            val dayPicker = dialog.findViewById<NumberPicker>(R.id.picker_day)
            val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel_date)
            val btnConfirm = dialog.findViewById<Button>(R.id.btn_confirm_date)

            monthPicker.minValue = 0
            monthPicker.maxValue = 11
            monthPicker.displayedValues = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            monthPicker.value = Calendar.getInstance().get(Calendar.MONTH)

            dayPicker.minValue = 1
            dayPicker.maxValue = 31
            dayPicker.value = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

            monthPicker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            dayPicker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

            dialog.setOnShowListener {
                styleNumberPickerCompletely(monthPicker)
                styleNumberPickerCompletely(dayPicker)
            }

            monthPicker.setOnValueChangedListener { _, _, newVal ->
                val maxDays = when (newVal) {
                    1 -> 29
                    3, 5, 8, 10 -> 30
                    else -> 31
                }
                if (dayPicker.value > maxDays) dayPicker.value = maxDays
                dayPicker.maxValue = maxDays
                dayPicker.post { styleNumberPickerCompletely(dayPicker) }
            }

            dayPicker.setOnValueChangedListener { _, _, _ ->
                dayPicker.post { styleNumberPickerCompletely(dayPicker) }
            }

            val initialMax = when (monthPicker.value) {
                1 -> 29
                3, 5, 8, 10 -> 30
                else -> 31
            }
            dayPicker.maxValue = initialMax

            btnCancel.setOnClickListener { dialog.dismiss() }

            btnConfirm.setOnClickListener {
                val formattedDate = String.format("%02d-%02d", monthPicker.value + 1, dayPicker.value)
                etDate.text = formattedDate
                layoutActionButtons.visibility = if (etTitle.text.toString().trim().isNotEmpty() && formattedDate.isNotEmpty() && formattedDate != "Tap to Select Date (MM-dd) ▼") View.VISIBLE else View.GONE
                dialog.dismiss()
            }

            dialog.show()
        }

        etTitle.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                layoutActionButtons.visibility = if (s.toString().trim().isNotEmpty() && etDate.text.toString().isNotEmpty() && etDate.text.toString() != "Tap to Select Date (MM-dd) ▼") View.VISIBLE else View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        userFile = File(filesDir, "user_events.json")
        if (!userFile.exists()) userFile.writeText("{}")
        notifyFile = File(filesDir, "notifications.json")
        if (!notifyFile.exists()) notifyFile.writeText("{}")

        findViewById<RadioGroup>(R.id.rg_filter).setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) { R.id.rb_presets -> 1; R.id.rb_personal -> 2; else -> 0 }
            refreshDynamicList()
        }

        setupMonthIndexer()
        refreshDynamicList()

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val dateInput = etDate.text.toString().trim()
            if (dateInput.length != 5 || !dateInput.contains("-")) {
                Toast.makeText(this, "Please select a valid date!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val jsonDatabase = JSONObject(userFile.readText())
                val newEvent = JSONObject()
                newEvent.put("title", etTitle.text.toString().trim())
                newEvent.put("desc", etDesc.text.toString().trim())
                jsonDatabase.put(dateInput, newEvent)
                userFile.writeText(jsonDatabase.toString(4))

                etDate.text = ""
                etDate.hint = "Tap to Select Date (MM-dd) ▼"
                etTitle.text.clear()
                etDesc.text.clear()
                layoutActionButtons.visibility = View.GONE

                Toast.makeText(this, "Event Saved!", Toast.LENGTH_SHORT).show()
                refreshDynamicList()
                pushWidgetUpdate()
            } catch (e: Exception) {
                Toast.makeText(this, "Error saving", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_delete).setOnClickListener {
            deleteEventByDate(etDate.text.toString().trim())
            pushWidgetUpdate()
        }
    }

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

            addView(ProgressBar(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 40, 0) }
                indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#238636"))
            })

            addView(TextView(this@MainActivity).apply {
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

    private fun styleNumberPickerCompletely(numberPicker: NumberPicker) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            numberPicker.textColor = android.graphics.Color.WHITE
        }
        try {
            val paintField = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
            paintField.isAccessible = true
            val paint = paintField.get(numberPicker) as android.graphics.Paint
            paint.color = android.graphics.Color.WHITE
        } catch (e: Exception) {
            e.printStackTrace()
        }
        for (i in 0 until numberPicker.childCount) {
            val child = numberPicker.getChildAt(i)
            if (child is EditText) {
                child.setTextColor(android.graphics.Color.WHITE)
            }
        }
        numberPicker.invalidate()
    }

    private fun setupMonthIndexer() {
        val layoutIndexer = findViewById<LinearLayout>(R.id.layout_month_indexer) ?: return
        val indexerPill = findViewById<View>(R.id.cv_indexer_pill)
        val bubbleCard = findViewById<View>(R.id.cv_scroll_bubble)
        val bubbleText = findViewById<TextView>(R.id.tv_scroll_bubble_letter)
        val scrollView = findViewById<ScrollView>(R.id.main_scroll_view)

        indexerPill.alpha = 0f
        indexerPill.visibility = View.GONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val filterGroup = findViewById<View>(R.id.rg_filter)
                val threshold = (filterGroup.top - 400).coerceAtLeast(100)

                if (scrollY > threshold) {
                    if (indexerPill.visibility == View.GONE) {
                        indexerPill.visibility = View.VISIBLE
                        indexerPill.animate().alpha(1f).setDuration(250).start()
                    }
                } else {
                    if (indexerPill.visibility == View.VISIBLE) {
                        indexerPill.animate().alpha(0f).setDuration(250).withEndAction {
                            indexerPill.visibility = View.GONE
                        }.start()
                    }
                }
            }
        }

        val letters = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        val monthKeys = listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12")

        layoutIndexer.removeAllViews()

        for (i in letters.indices) {
            val tv = TextView(this).apply {
                text = letters[i]
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#8B949E"))
                setPadding(0, 10, 0, 10)
                gravity = android.view.Gravity.CENTER
            }
            layoutIndexer.addView(tv)
        }

        layoutIndexer.setOnTouchListener { view, event ->
            val y = event.y
            val index = ((y / view.height) * letters.size).toInt().coerceIn(0, letters.size - 1)

            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                    bubbleCard.visibility = View.VISIBLE
                    bubbleText.text = letters[index]

                    val indexerTop = (view.parent as View).top
                    bubbleCard.y = indexerTop + y - (bubbleCard.height / 2)

                    val targetView = monthViewMap[monthKeys[index]]
                    if (targetView != null) {
                        val exactY = containerEvents.top + targetView.top
                        scrollView?.scrollTo(0, exactY)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    bubbleCard.visibility = View.GONE
                    true
                }
                else -> false
            }
        }
    }

    private fun pushWidgetUpdate() {
        val intent = Intent(this, DailyWidgetProvider::class.java).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE }
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, DailyWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun checkForUpdates() {
        Thread {
            try {
                val conn = URL(firebaseUpdateUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    if (response != "null") {
                        val json = JSONObject(response)
                        val latestVersion = json.getInt("latest_version")
                        val apkUrl = json.getString("apk_url")

                        if (latestVersion > currentAppVersionCode) {
                            runOnUiThread {
                                if (!isDestroyed && !isFinishing) {
                                    val btnUpdate = findViewById<Button>(R.id.btn_update_app)
                                    btnUpdate.visibility = View.VISIBLE

                                    AlertDialog.Builder(this@MainActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                        .setTitle("Update Available!")
                                        .setMessage("A new version of DailyWidget is ready. Update now to get the latest features.")
                                        .setPositiveButton("DOWNLOAD") { _, _ ->
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
                                        }
                                        .setNegativeButton("LATER", null)
                                        .show()

                                    btnUpdate.setOnClickListener {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun deleteEventByDate(date: String) {
        if (date.length != 5 || !date.contains("-")) { Toast.makeText(this, "Invalid date format!", Toast.LENGTH_SHORT).show(); return }
        try {
            val jsonDatabase = JSONObject(userFile.readText())
            val deleteFlag = JSONObject().apply { put("title", "__DELETED__"); put("desc", "") }
            jsonDatabase.put(date, deleteFlag)
            userFile.writeText(jsonDatabase.toString(4))
            Toast.makeText(this, "Event Deleted!", Toast.LENGTH_SHORT).show(); refreshDynamicList()
        } catch (e: Exception) {}
    }

    override fun onResume() { super.onResume(); refreshDynamicList() }

    private fun refreshDynamicList() {
        containerEvents.removeAllViews()
        monthViewMap.clear()

        try {
            val combinedJson = JSONObject()
            if (currentFilter == 0 || currentFilter == 1) {
                try {
                    val presetObj = JSONObject(assets.open("highlights.json").bufferedReader().use { it.readText() })
                    presetObj.keys().forEach { combinedJson.put(it, presetObj.get(it)) }
                } catch (e: Exception) { }
            }
            if (currentFilter == 0 || currentFilter == 2) {
                try {
                    val userObj = JSONObject(userFile.readText())
                    userObj.keys().forEach { combinedJson.put(it, userObj.get(it)) }
                } catch (e: Exception) { }
            }

            val keys = mutableListOf<String>(); val iterator = combinedJson.keys()
            while (iterator.hasNext()) { keys.add(iterator.next()) }
            keys.sort()

            val inflater = LayoutInflater.from(this)
            val sharedPrefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
            val notifyEnabled = sharedPrefs.getBoolean("notifyEnabled", false)
            val notifyType = sharedPrefs.getInt("notifyType", 0)
            val notifyJson = JSONObject(notifyFile.readText())

            for (date in keys) {
                val eventObj = combinedJson.getJSONObject(date)
                val title = eventObj.getString("title")
                val desc = eventObj.optString("desc", "")

                if (title != "__DELETED__") {
                    val eventView = inflater.inflate(R.layout.item_event, containerEvents, false)
                    eventView.findViewById<TextView>(R.id.tv_item_title).text = title
                    eventView.findViewById<TextView>(R.id.tv_item_date).text = "Date: $date"
                    val tvDesc = eventView.findViewById<TextView>(R.id.tv_item_desc)
                    tvDesc.text = desc
                    tvDesc.visibility = if (desc.isNotEmpty()) View.VISIBLE else View.GONE
                    eventView.findViewById<ImageView>(R.id.btn_item_delete).setOnClickListener { deleteEventByDate(date) }

                    val cbNotify = eventView.findViewById<CheckBox>(R.id.cb_notify)
                    if (!notifyEnabled) { cbNotify.visibility = View.GONE } else {
                        cbNotify.visibility = View.VISIBLE
                        if (notifyType == 0) {
                            cbNotify.isChecked = true; cbNotify.isEnabled = false; cbNotify.alpha = 0.5f
                        } else {
                            cbNotify.isEnabled = true; cbNotify.alpha = 1.0f; cbNotify.setOnCheckedChangeListener(null)
                            cbNotify.isChecked = notifyJson.optBoolean(date, false)
                            cbNotify.setOnCheckedChangeListener { _, isChecked ->
                                try {
                                    val updatedJson = JSONObject(notifyFile.readText())
                                    updatedJson.put(date, isChecked)
                                    notifyFile.writeText(updatedJson.toString(4))
                                } catch (e: Exception) {}
                            }
                        }
                    }
                    containerEvents.addView(eventView)

                    val monthStr = date.substring(0, 2)
                    if (!monthViewMap.containsKey(monthStr)) {
                        monthViewMap[monthStr] = eventView
                    }
                }
            }
            if (containerEvents.childCount == 0) containerEvents.addView(TextView(this).apply { text = "No events to show."; setTextColor(android.graphics.Color.parseColor("#8B949E")) })
        } catch (e: Exception) { containerEvents.addView(TextView(this).apply { text = "Error loading list."; setTextColor(android.graphics.Color.RED) }) }
    }
}
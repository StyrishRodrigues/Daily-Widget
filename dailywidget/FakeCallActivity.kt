package com.example.dailywidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class FakeCallActivity : AppCompatActivity() {

    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var ringtonePlayer: MediaPlayer? = null
    private var voiceMediaPlayer: MediaPlayer? = null
    private var secondsElapsed = 0
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isCallActive = false
    private var isSpeakerphoneActive = false

    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        setContentView(R.layout.activity_fake_call)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "DailyWidget:FakeCallProximity"
            )
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val sharedPrefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        val callerName = sharedPrefs.getString("fake_caller_name", "")?.takeIf { it.isNotBlank() } ?: "Boss"
        val callerNumber = sharedPrefs.getString("fake_caller_number", "")?.takeIf { it.isNotBlank() } ?: "WhatsApp audio..."

        findViewById<TextView>(R.id.tv_caller_name).text = callerName
        findViewById<TextView>(R.id.tv_call_status).text = callerNumber

        val pfpUriString = sharedPrefs.getString("fake_caller_pfp", "")
        if (!pfpUriString.isNullOrBlank()) {
            try {
                val imageUri = Uri.parse(pfpUriString)
                val inputStream = contentResolver.openInputStream(imageUri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                val pfpView = findViewById<ImageView>(R.id.iv_fake_caller_pfp)
                pfpView.setImageBitmap(bitmap)
                pfpView.imageTintList = null
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // --- Clear the widget taps and icon instantly ---
        sharedPrefs.edit().putBoolean("fake_call_running", false).putString("widget_taps", "").apply()
        val updateIntent = Intent(this, DailyWidgetProvider::class.java).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE }
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, DailyWidgetProvider::class.java))
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(updateIntent)

        // --- Start incoming Ringtone ---
        try {
            val defaultRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = MediaPlayer.create(this, defaultRingtoneUri)
            ringtonePlayer?.isLooping = true
            ringtonePlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val btnAnswer = findViewById<CardView>(R.id.btn_answer_card)
        val btnDecline = findViewById<CardView>(R.id.btn_decline_card)
        val layoutOngoing = findViewById<View>(R.id.layout_ongoing_actions)
        val btnEndCallGrid = findViewById<CardView>(R.id.btn_end_call_grid)
        val layoutIncoming = findViewById<LinearLayout>(R.id.layout_incoming_actions)
        val tvStatus = findViewById<TextView>(R.id.tv_call_status)
        val btnSpeaker = findViewById<CardView>(R.id.btn_speaker)

        val callTimerRunnable = object : Runnable {
            override fun run() {
                if (isCallActive) {
                    val mins = secondsElapsed / 60
                    val secs = secondsElapsed % 60
                    tvStatus.text = String.format("%02d:%02d", mins, secs)
                    secondsElapsed++
                    timerHandler.postDelayed(this, 1000)
                }
            }
        }

        btnDecline.setOnClickListener {
            stopAllAudio()
            finish()
        }

        btnAnswer.setOnClickListener {
            stopRinging()

            layoutIncoming.visibility = View.GONE
            layoutOngoing.visibility = View.VISIBLE

            val pfpImage = findViewById<ImageView>(R.id.iv_fake_caller_pfp)
            val pfpCard = pfpImage.parent as View
            val shiftUpPixels = -70f * resources.displayMetrics.density
            pfpCard.animate().translationY(shiftUpPixels).setDuration(350).start()

            isCallActive = true
            secondsElapsed = 0
            timerHandler.post(callTimerRunnable)

            // Start voice note in earpiece
            isSpeakerphoneActive = false
            toggleAudioEngine(false)
        }

        btnSpeaker.setOnClickListener {
            isSpeakerphoneActive = !isSpeakerphoneActive
            btnSpeaker.setCardBackgroundColor(if (isSpeakerphoneActive) android.graphics.Color.parseColor("#58A6FF") else android.graphics.Color.parseColor("#2C2F33"))
            toggleAudioEngine(isSpeakerphoneActive)
        }

        btnEndCallGrid.setOnClickListener {
            isCallActive = false
            timerHandler.removeCallbacksAndMessages(null)
            stopAllAudio()
            finish()
        }
    }

    private fun toggleAudioEngine(useLoudspeaker: Boolean) {
        val currentPosition = voiceMediaPlayer?.currentPosition ?: 0

        try {
            if (voiceMediaPlayer?.isPlaying == true) {
                voiceMediaPlayer?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            voiceMediaPlayer?.release()
            voiceMediaPlayer = null
        }

        val audioUriString = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE).getString("fake_caller_audio", "")
        if (audioUriString.isNullOrBlank()) {
            val defaultAudioFile = java.io.File(filesDir, "fake_caller_audio.mp3")
            if (!defaultAudioFile.exists()) return
        }

        try {
            val audioUri = if (audioUriString.isNullOrBlank()) {
                Uri.fromFile(java.io.File(filesDir, "fake_caller_audio.mp3"))
            } else {
                Uri.parse(audioUriString)
            }

            voiceMediaPlayer = MediaPlayer().apply {
                setDataSource(this@FakeCallActivity, audioUri)

                if (useLoudspeaker) {
                    audioManager.mode = AudioManager.MODE_NORMAL
                    audioManager.isSpeakerphoneOn = false
                    volumeControlStream = AudioManager.STREAM_MUSIC
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                } else {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.isSpeakerphoneOn = false
                    volumeControlStream = AudioManager.STREAM_VOICE_CALL
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                }

                prepare()
                if (currentPosition > 0 && currentPosition < duration) {
                    seekTo(currentPosition)
                }
                isLooping = true
                start()
            }

            if (!useLoudspeaker) {
                val maxCallVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCallVol, 0)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            voiceMediaPlayer?.release()
            voiceMediaPlayer = null
        }
    }

    private fun stopRinging() {
        try {
            if (ringtonePlayer?.isPlaying == true) {
                ringtonePlayer?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ringtonePlayer?.release()
            ringtonePlayer = null
        }
    }

    private fun stopAllAudio() {
        stopRinging()
        try {
            if (voiceMediaPlayer?.isPlaying == true) {
                voiceMediaPlayer?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            voiceMediaPlayer?.release()
            voiceMediaPlayer = null
        }

        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    override fun onResume() {
        super.onResume()
        if (proximityWakeLock?.isHeld == false) {
            proximityWakeLock?.acquire(10 * 60 * 1000L)
        }
    }

    override fun onPause() {
        super.onPause()
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock?.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isCallActive = false
        timerHandler.removeCallbacksAndMessages(null)
        stopAllAudio()
    }
}
package com.yor.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // ── UI references ─────────────────────────────────────────────────────────
    private lateinit var pickerContainer: LinearLayout
    private lateinit var pickerHours: NumberPicker
    private lateinit var pickerMinutes: NumberPicker
    private lateinit var pickerSeconds: NumberPicker

    private lateinit var timerRingView: TimerRingView
    private lateinit var tvTimerDisplay: TextView
    private lateinit var tvTotalTime: TextView

    private lateinit var btnPlayPause: MaterialButton
    private lateinit var btnStopReset: MaterialButton
    private lateinit var btnSoundSettings: MaterialButton

    // ── Timer state ───────────────────────────────────────────────────────────
    private var countDownTimer: CountDownTimer? = null
    private var timeInMillis: Long = 0L
    private var originalTimeInMillis: Long = 0L

    private enum class TimerState { STOPPED, RUNNING, PAUSED }
    private var currentState = TimerState.STOPPED

    // ── Companion: static alarm helpers accessed by the BroadcastReceiver ─────
    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID      = "timer_alarm_channel"
        const val ACTION_SILENCE          = "com.yor.timer.ACTION_SILENCE"

        /**
         * Held statically so [AlarmReceiver] can silence the alarm even when
         * the Activity is in the background or has been recreated.
         */
        var ringtone: Ringtone? = null
            private set

        fun silenceAlarm(context: Context) {
            ringtone?.stop()
            ringtone = null
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        }
    }

    // ── BroadcastReceiver ─────────────────────────────────────────────────────
    /**
     * Static nested class (no `inner` keyword) — the system must be able to
     * instantiate it without a live Activity reference.
     *
     * Register in AndroidManifest.xml:
     *   <receiver android:name=".MainActivity$AlarmReceiver"
     *             android:exported="false">
     *       <intent-filter>
     *           <action android:name="com.yor.timer.ACTION_SILENCE"/>
     *       </intent-filter>
     *   </receiver>
     */
    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == ACTION_SILENCE) {
                silenceAlarm(context)
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupPickers()
        setupButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        // Do NOT silence the alarm here — user may have just rotated the screen.
    }

    // ── Initialisation helpers ────────────────────────────────────────────────
    private fun bindViews() {
        pickerContainer  = findViewById(R.id.pickerContainer)
        pickerHours      = findViewById(R.id.pickerHours)
        pickerMinutes    = findViewById(R.id.pickerMinutes)
        pickerSeconds    = findViewById(R.id.pickerSeconds)

        timerRingView    = findViewById(R.id.timerRingView)
        tvTimerDisplay   = findViewById(R.id.tvTimerDisplay)
        tvTotalTime      = findViewById(R.id.tvTotalTime)

        btnPlayPause     = findViewById(R.id.btnPlayPause)
        btnStopReset     = findViewById(R.id.btnStopReset)
        btnSoundSettings = findViewById(R.id.btnSoundSettings)
    }

    private fun setupPickers() {
        val twoDigit = NumberPicker.Formatter { v -> String.format(Locale.US, "%02d", v) }
        pickerHours.apply   { minValue = 0; maxValue = 23; setFormatter(twoDigit) }
        pickerMinutes.apply { minValue = 0; maxValue = 59; setFormatter(twoDigit) }
        pickerSeconds.apply { minValue = 0; maxValue = 59; setFormatter(twoDigit) }
    }

    private fun setupButtons() {
        btnPlayPause.setOnClickListener {
            when (currentState) {
                TimerState.STOPPED -> startTimerFromPickers()
                TimerState.RUNNING -> pauseTimer()
                TimerState.PAUSED  -> resumeTimer()
            }
        }

        btnStopReset.setOnClickListener {
            if (currentState != TimerState.STOPPED) stopTimer()
        }

        btnSoundSettings.setOnClickListener {
            Toast.makeText(this, "Sound settings clicked", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Timer control ─────────────────────────────────────────────────────────
    private fun startTimerFromPickers() {
        val totalSeconds =
            pickerHours.value * 3600 + pickerMinutes.value * 60 + pickerSeconds.value
        if (totalSeconds == 0) {
            Toast.makeText(this, "Please select a time greater than zero", Toast.LENGTH_SHORT).show()
            return
        }
        originalTimeInMillis = totalSeconds * 1_000L
        timeInMillis = originalTimeInMillis
        tvTotalTime.text = formatTotalLabel(totalSeconds)
        startCountdown()
    }

    private fun startCountdown() {
        showRunningUi()

        countDownTimer = object : CountDownTimer(timeInMillis, 100L) {  // 100 ms for smooth ring
            override fun onTick(millisUntilFinished: Long) {
                timeInMillis = millisUntilFinished
                updateTimerText()
                updateRing()
            }

            override fun onFinish() {
                timeInMillis = 0L
                updateTimerText()
                timerRingView.progress = 0f
                playAlarmSound()
                stopTimer()
            }
        }.start()

        currentState = TimerState.RUNNING
        btnPlayPause.setIconResource(android.R.drawable.ic_media_pause)
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        currentState = TimerState.PAUSED
        btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
    }

    private fun resumeTimer() = startCountdown()

    private fun stopTimer() {
        countDownTimer?.cancel()
        currentState = TimerState.STOPPED
        showPickerUi()
        btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
    }

    // ── UI switching ──────────────────────────────────────────────────────────
    private fun showRunningUi() {
        pickerContainer.visibility  = View.GONE
        btnSoundSettings.visibility = View.INVISIBLE

        timerRingView.visibility  = View.VISIBLE
        tvTimerDisplay.visibility = View.VISIBLE
        tvTotalTime.visibility    = View.VISIBLE
        btnStopReset.visibility   = View.VISIBLE
    }

    private fun showPickerUi() {
        timerRingView.visibility  = View.GONE
        tvTimerDisplay.visibility = View.GONE
        tvTotalTime.visibility    = View.GONE
        btnStopReset.visibility   = View.INVISIBLE

        pickerContainer.visibility  = View.VISIBLE
        btnSoundSettings.visibility = View.VISIBLE
    }

    // ── Display helpers ───────────────────────────────────────────────────────
    private fun updateTimerText() {
        val total   = timeInMillis / 1_000L
        val hours   = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        tvTimerDisplay.text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun updateRing() {
        if (originalTimeInMillis > 0) {
            timerRingView.progress = timeInMillis.toFloat() / originalTimeInMillis.toFloat()
        }
    }

    private fun formatTotalLabel(totalSeconds: Int): String = when {
        totalSeconds >= 3600 -> {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            if (m == 0 && s == 0) "Total $h hour${if (h > 1) "s" else ""}"
            else "Total ${String.format(Locale.US, "%02d:%02d:%02d", h, m, s)}"
        }
        totalSeconds >= 60 -> {
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            if (s == 0) "Total $m minute${if (m > 1) "s" else ""}"
            else "Total $m min $s sec"
        }
        else -> "Total $totalSeconds second${if (totalSeconds > 1) "s" else ""}"
    }

    // ── Alarm ─────────────────────────────────────────────────────────────────
    private fun playAlarmSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.also { r ->
                r.audioAttributes = audioAttributes
                r.play()
            }

            showSilenceNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showSilenceNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shown when a countdown timer finishes"
                setSound(null, null)   // Our ringtone IS the alarm; avoid double-sound
            }
            nm.createNotificationChannel(channel)
        }

        // SILENCE action button — BroadcastReceiver is correct inside addAction()
        val silencePi = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, AlarmReceiver::class.java).apply { action = ACTION_SILENCE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification body tap → opens the Activity (lint rule: setContentIntent
        // must NOT target a BroadcastReceiver; use getActivity instead)
        val openAppPi = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Timer Finished")
            .setContentText("Tap SILENCE to stop the alarm")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)        // Cannot be swiped away
            .setAutoCancel(false)
            .setSound(null)
            .setContentIntent(openAppPi)   // Activity — satisfies lint rule
            .addAction(
                android.R.drawable.ic_lock_silent_mode,
                "SILENCE",
                silencePi              // BroadcastReceiver inside addAction — correct
            )
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }
}
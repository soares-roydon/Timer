package com.yor.timer

import android.media.RingtoneManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    // UI Variables
    private lateinit var tvTimerDisplay: TextView
    private lateinit var pickerContainer: LinearLayout
    private lateinit var pickerHours: NumberPicker
    private lateinit var pickerMinutes: NumberPicker
    private lateinit var pickerSeconds: NumberPicker

    private lateinit var btnPlayPause: MaterialButton
    private lateinit var btnStopReset: MaterialButton // Mapped to btnList in XML
    private lateinit var btnSoundSettings: MaterialButton

    // Timer Variables
    private var countDownTimer: CountDownTimer? = null
    private var timeInMillis: Long = 0
    private var originalTimeInMillis: Long = 0

    // State Machine
    private enum class TimerState { STOPPED, RUNNING, PAUSED }
    private var currentState = TimerState.STOPPED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Link XML Views
        tvTimerDisplay = findViewById(R.id.tvTimerDisplay)
        pickerContainer = findViewById(R.id.pickerContainer)
        pickerHours = findViewById(R.id.pickerHours)
        pickerMinutes = findViewById(R.id.pickerMinutes)
        pickerSeconds = findViewById(R.id.pickerSeconds)

        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnStopReset = findViewById(R.id.btnList) // Using your right-side button for Stop
        btnSoundSettings = findViewById(R.id.btnSoundSettings)

        // 2. Setup Pickers (Limits & "00" Formatting)
        val formatter = NumberPicker.Formatter { i -> String.format("%02d", i) }

        pickerHours.apply { minValue = 0; maxValue = 23; setFormatter(formatter) }
        pickerMinutes.apply { minValue = 0; maxValue = 59; setFormatter(formatter) }
        pickerSeconds.apply { minValue = 0; maxValue = 59; setFormatter(formatter) }

        // 3. Play/Pause Button Logic
        btnPlayPause.setOnClickListener {
            when (currentState) {
                TimerState.STOPPED -> startTimerSetup()
                TimerState.RUNNING -> pauseTimer()
                TimerState.PAUSED -> resumeTimer()
            }
        }

        // 4. Stop/Cancel Button Logic
        btnStopReset.setOnClickListener {
            if (currentState != TimerState.STOPPED) {
                stopTimer()
            } else {
                Toast.makeText(this, "Timer is already stopped", Toast.LENGTH_SHORT).show()
            }
        }

        // Sound button placeholder
        btnSoundSettings.setOnClickListener {
            Toast.makeText(this, "Sound settings clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTimerSetup() {
        val totalSeconds = (pickerHours.value * 3600) + (pickerMinutes.value * 60) + pickerSeconds.value
        if (totalSeconds == 0) {
            Toast.makeText(this, "Please select a time", Toast.LENGTH_SHORT).show()
            return
        }

        originalTimeInMillis = totalSeconds * 1000L
        timeInMillis = originalTimeInMillis
        startTimer()
    }

    private fun startTimer() {
        // Swap UI: Hide pickers and sound button, show big text
        pickerContainer.visibility = View.GONE
        btnSoundSettings.visibility = View.INVISIBLE
        tvTimerDisplay.visibility = View.VISIBLE

        countDownTimer = object : CountDownTimer(timeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeInMillis = millisUntilFinished
                updateTimerText()
            }

            override fun onFinish() {
                playAlarmSound()
                stopTimer()
            }
        }.start()

        // Update state and icons
        currentState = TimerState.RUNNING
        btnPlayPause.setIconResource(android.R.drawable.ic_media_pause)
        btnStopReset.setIconResource(android.R.drawable.ic_menu_close_clear_cancel) // Turn list icon into an 'X'
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        currentState = TimerState.PAUSED
        btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
    }

    private fun resumeTimer() {
        startTimer()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        currentState = TimerState.STOPPED

        // Reset UI back to selection mode
        pickerContainer.visibility = View.VISIBLE
        btnSoundSettings.visibility = View.VISIBLE
        tvTimerDisplay.visibility = View.GONE

        btnPlayPause.setIconResource(android.R.drawable.ic_media_play)
        btnStopReset.setIconResource(android.R.drawable.ic_menu_sort_by_size) // Turn 'X' back to list icon
    }

    private fun updateTimerText() {
        val hours = (timeInMillis / 1000) / 3600
        val minutes = ((timeInMillis / 1000) % 3600) / 60
        val seconds = (timeInMillis / 1000) % 60

        tvTimerDisplay.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun playAlarmSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
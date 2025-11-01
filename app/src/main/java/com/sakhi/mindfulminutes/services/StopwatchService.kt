package com.sakhi.mindfulminutes.services

/**
 * Author: Arihant Jain
 * Date: 01-11-2025
 * Time: 23:46
 * Year: 2025
 * Month: November (Nov)
 * Day: 01 (Saturday)
 * Hour: 23
 * Minute: 46
 * Project: Let Me Focus
 * Package: com.sakhi.mindfulminutes.services
 */
import kotlin.jvm.java
import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.activities.MainActivity
import kotlinx.coroutines.*
import java.util.*

class StopwatchService : Service() {
    private val binder = StopwatchBinder()
    private var notificationManager: NotificationManager? = null
    private var isRunning = false
    private var startTime = 0L
    private var elapsedTime = 0L
    private var currentActivityId: String? = null
    private var currentActivityName: String? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var listeners = mutableListOf<(Long, String) -> Unit>()

    inner class StopwatchBinder : Binder() {
        fun getService(): StopwatchService = this@StopwatchService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            currentActivityId = it.getStringExtra("activityId")
            currentActivityName = it.getStringExtra("activityName")
            when (it.action) {
                ACTION_START -> startStopwatch()
                ACTION_PAUSE -> pauseStopwatch()
                ACTION_STOP -> stopStopwatch()
                ACTION_RESET -> resetStopwatch()
            }
        }
        return START_STICKY
    }

    fun startStopwatch(activityId: String? = null, activityName: String? = null) {
        if (activityId != null) {
            currentActivityId = activityId
            currentActivityName = activityName
        }

        if (!isRunning) {
            isRunning = true
            startTime = System.currentTimeMillis() - elapsedTime
            startForeground(NOTIFICATION_ID, createNotification())
            startTimer()
        }
    }

    fun pauseStopwatch() {
        if (isRunning) {
            isRunning = false
            elapsedTime = System.currentTimeMillis() - startTime
            updateNotification()
            stopTimer()
        }
    }

    fun stopStopwatch() {
        isRunning = false
        elapsedTime = 0L
        stopTimer()
        stopForeground(true)
        currentActivityId = null
        currentActivityName = null
    }

    fun resetStopwatch() {
        elapsedTime = 0L
        if (isRunning) {
            startTime = System.currentTimeMillis()
        }
        notifyListeners()
    }

    fun getCurrentTime(): Long = if (isRunning) {
        System.currentTimeMillis() - startTime
    } else {
        elapsedTime
    }

    fun isTimerRunning(): Boolean = isRunning

    fun getCurrentActivityId(): String? = currentActivityId

    fun addListener(listener: (Long, String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Long, String) -> Unit) {
        listeners.remove(listener)
    }

    private fun startTimer() {
        coroutineScope.launch {
            while (isRunning) {
                delay(1000)
                notifyListeners()
            }
        }
    }

    private fun stopTimer() {
        coroutineScope.coroutineContext.cancelChildren()
    }

    private fun notifyListeners() {
        val currentTime = getCurrentTime()
        val formattedTime = formatTime(currentTime)
        listeners.forEach { it(currentTime, formattedTime) }
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stopwatch Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing stopwatch timing"
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StopwatchService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentActivityName ?: "Activity Tracker")
            .setContentText("Time: ${formatTime(getCurrentTime())}")
            .setSmallIcon(R.drawable.ic_stopwatch)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        notificationManager?.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        stopTimer()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET = "ACTION_RESET"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "stopwatch_channel"
    }
}
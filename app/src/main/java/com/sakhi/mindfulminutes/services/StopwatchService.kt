package com.sakhi.mindfulminutes.services

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.activities.MainActivity
import com.sakhi.mindfulminutes.repository.ActivityRepository
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class StopwatchService : Service() {
    private val binder = StopwatchBinder()
    private var notificationManager: NotificationManager? = null
    private var isRunning = false
    private var startTime = 0L
    private var elapsedTime = 0L
    private var currentActivityId: String? = null
    private var currentActivityName: String? = null
    private val repository = ActivityRepository()

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
            when (it.action) {
                ACTION_START -> {
                    val activityId = it.getStringExtra("activityId")
                    val activityName = it.getStringExtra("activityName")
                    startStopwatch(activityId, activityName)
                }
                ACTION_PAUSE -> pauseStopwatch()
                ACTION_RESUME -> resumeStopwatch()
                ACTION_STOP -> {
                    // Ensure instance is saved when stopping from notification
                    stopStopwatch()
                }
                ACTION_RESET -> resetStopwatch()
            }
        }
        return START_STICKY
    }

    fun startStopwatch(activityId: String? = null, activityName: String? = null) {
        if (activityId != null && activityName != null) {
            // If a different activity is being started, stop the current one first
            if (currentActivityId != null && currentActivityId != activityId) {
                coroutineScope.launch {
                    // Save current instance before switching
                    if (isRunning) {
                        saveCurrentInstance()
                    }
                    resetStopwatchInternal()
                    startNewActivity(activityId, activityName)
                }
                return
            }

            currentActivityId = activityId
            currentActivityName = activityName
        }

        if (!isRunning && currentActivityId != null) {
            isRunning = true
            startTime = System.currentTimeMillis() - elapsedTime
            startForeground(NOTIFICATION_ID, createNotification())
            startTimer()
            updateNotification()
        }
    }

    private fun startNewActivity(activityId: String, activityName: String) {
        currentActivityId = activityId
        currentActivityName = activityName
        elapsedTime = 0L
        isRunning = true
        startTime = System.currentTimeMillis()
        startForeground(NOTIFICATION_ID, createNotification())
        startTimer()
        updateNotification()
    }

    fun pauseStopwatch() {
        if (isRunning) {
            isRunning = false
            elapsedTime = System.currentTimeMillis() - startTime
            updateNotification()
            stopTimer()

            // Save paused state
            coroutineScope.launch {
                saveCurrentInstance()
            }
        }
    }

    fun resumeStopwatch() {
        if (!isRunning && currentActivityId != null) {
            isRunning = true
            startTime = System.currentTimeMillis() - elapsedTime
            startTimer()
            updateNotification()
        }
    }

    fun stopStopwatch() {
        if (currentActivityId != null) {
            val finalElapsedTime = if (isRunning) {
                System.currentTimeMillis() - startTime
            } else {
                elapsedTime
            }

            // Stop timer immediately
            isRunning = false
            stopTimer()

            // Save instance to database before stopping service
            coroutineScope.launch {
                try {
                    // Ensure we save the instance with the correct time
                    if (finalElapsedTime > 0) {
                        saveInstanceToDatabase(finalElapsedTime)
                        Log.d("StopwatchService", "Instance saved for ${currentActivityName}: $finalElapsedTime ms")
                    }

                    // Reset and stop service
                    resetStopwatchInternal()
                    stopForeground(true)
                    currentActivityId = null
                    currentActivityName = null

                } catch (e: Exception) {
                    Log.e("StopwatchService", "Error saving instance: ${e.message}")
                    e.printStackTrace()
                    // Even if there's an error, still stop the service
                    resetStopwatchInternal()
                    stopForeground(true)
                    currentActivityId = null
                    currentActivityName = null
                }
            }
        } else {
            // No current activity, just stop the service
            resetStopwatchInternal()
            stopForeground(true)
        }
    }

    fun resetStopwatch() {
        elapsedTime = 0L
        if (isRunning) {
            startTime = System.currentTimeMillis()
        }
        notifyListeners()
        updateNotification()
    }

    private fun resetStopwatchInternal() {
        elapsedTime = 0L
        isRunning = false
        notifyListeners()
    }

    fun getCurrentTime(): Long = if (isRunning) {
        System.currentTimeMillis() - startTime
    } else {
        elapsedTime
    }

    fun isTimerRunning(): Boolean = isRunning

    fun getCurrentActivityId(): String? = currentActivityId

    fun getCurrentActivityName(): String? = currentActivityName

    fun addListener(listener: (Long, String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Long, String) -> Unit) {
        listeners.remove(listener)
    }

    private suspend fun saveCurrentInstance() {
        try {
            val currentTime = getCurrentTime()
            if (currentTime > 0 && currentActivityId != null) {
                saveInstanceToDatabase(currentTime)
                Log.d("StopwatchService", "Current instance saved, time: $currentTime ms")
            }
        } catch (e: Exception) {
            Log.e("StopwatchService", "Error saving current instance: ${e.message}")
            e.printStackTrace()
        }
    }

    private suspend fun saveInstanceToDatabase(timeInMillis: Long) {
        try {
            val totalSpentTime = timeInMillis / 1000 // Convert to seconds
            val endTime = System.currentTimeMillis()
            val startTime = endTime - timeInMillis

            // Format times for Indian timezone
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val startTimeIndian = dateFormat.format(Date(startTime))
            val stopTimeIndian = dateFormat.format(Date(endTime))

            // Save using the new repository method with numerical instance IDs
            repository.addActivityInstance(
                activityId = currentActivityId!!,
                totalSpentTime = totalSpentTime,
                startTime = startTimeIndian,
                stopTime = stopTimeIndian
            )

            Log.d("StopwatchService", "Instance saved: $totalSpentTime seconds for ${currentActivityName}")

        } catch (e: Exception) {
            Log.e("StopwatchService", "Error in saveInstanceToDatabase: ${e.message}")
            throw e
        }
    }

    private fun startTimer() {
        coroutineScope.launch {
            while (isRunning) {
                delay(1000)
                notifyListeners()
                updateNotification()
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
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("fragment", "activities")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause/Resume Action based on current state
        val pauseResumeIntent = Intent(this, StopwatchService::class.java).apply {
            action = if (isRunning) ACTION_PAUSE else ACTION_RESUME
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 2, pauseResumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action - This will save to database
        val stopIntent = Intent(this, StopwatchService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentActivityName ?: "Activity Tracker")
            .setContentText("Time: ${formatTime(getCurrentTime())}")
            .setSmallIcon(R.drawable.ic_stopwatch)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        // Add actions based on timer state
        if (isRunning) {
            notification.addAction(R.drawable.ic_pause, "Pause", pauseResumePendingIntent)
        } else {
            notification.addAction(R.drawable.ic_play, "Resume", pauseResumePendingIntent)
        }
        notification.addAction(R.drawable.ic_stop, "Stop & Save", stopPendingIntent)

        return notification.build()
    }

    private fun updateNotification() {
        notificationManager?.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        stopTimer()
        // Save current instance before destroying if running
        coroutineScope.launch {
            if (isRunning && currentActivityId != null) {
                try {
                    saveCurrentInstance()
                    Log.d("StopwatchService", "Instance saved on service destroy")
                } catch (e: Exception) {
                    Log.e("StopwatchService", "Error saving instance on destroy: ${e.message}")
                }
            }
        }
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET = "ACTION_RESET"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "stopwatch_channel"
    }
}
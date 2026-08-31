package com.sakhi.mindfulminutes.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
    private lateinit var prefs: SharedPreferences

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val listeners = mutableListOf<(Long, String) -> Unit>()

    inner class StopwatchBinder : Binder() {
        fun getService(): StopwatchService = this@StopwatchService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        prefs = getSharedPreferences(PREFS_STOPWATCH, Context.MODE_PRIVATE)
        createNotificationChannel()
        restoreServiceState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Service was restarted by Android OS after app was killed
            restoreServiceState()
            if (isRunning || currentActivityId != null) {
                startForegroundServiceNotification()
                if (isRunning) startTimer()
                updateNotification()
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val activityId = intent.getStringExtra("activityId")
                val activityName = intent.getStringExtra("activityName")
                startStopwatch(activityId, activityName)
            }
            ACTION_PAUSE -> pauseStopwatch()
            ACTION_RESUME -> resumeStopwatch()
            ACTION_STOP -> stopStopwatch()
            ACTION_RESET -> resetStopwatch()
        }

        return START_STICKY
    }

    /**
     * Keeps the service running and the notification visible when the app is swiped away from Recents
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (currentActivityId != null) {
            saveServiceState()
            startForegroundServiceNotification()
            updateNotification()
        }
    }

    fun startStopwatch(activityId: String? = null, activityName: String? = null) {
        if (activityId != null && activityName != null) {
            if (currentActivityId != null && currentActivityId != activityId) {
                coroutineScope.launch {
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
            saveServiceState()
            startForegroundServiceNotification()
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
        saveServiceState()
        startForegroundServiceNotification()
        startTimer()
        updateNotification()
    }

    private fun startForegroundServiceNotification() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun pauseStopwatch() {
        if (isRunning) {
            isRunning = false
            elapsedTime = System.currentTimeMillis() - startTime
            saveServiceState()
            updateNotification()
            stopTimer()

            coroutineScope.launch {
                saveCurrentInstance()
            }
        }
    }

    fun resumeStopwatch() {
        if (!isRunning && currentActivityId != null) {
            isRunning = true
            startTime = System.currentTimeMillis() - elapsedTime
            saveServiceState()
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

            isRunning = false
            stopTimer()

            coroutineScope.launch {
                try {
                    if (finalElapsedTime > 0) {
                        saveInstanceToDatabase(finalElapsedTime)
                        Log.d("StopwatchService", "Instance saved for $currentActivityName: $finalElapsedTime ms")
                    }
                    clearServiceState()
                    resetStopwatchInternal()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    currentActivityId = null
                    currentActivityName = null
                } catch (e: Exception) {
                    Log.e("StopwatchService", "Error saving instance: ${e.message}")
                    e.printStackTrace()
                    clearServiceState()
                    resetStopwatchInternal()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    currentActivityId = null
                    currentActivityName = null
                }
            }
        } else {
            clearServiceState()
            resetStopwatchInternal()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    fun resetStopwatch() {
        elapsedTime = 0L
        if (isRunning) {
            startTime = System.currentTimeMillis()
        }
        saveServiceState()
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

    private fun saveServiceState() {
        prefs.edit()
            .putBoolean(KEY_IS_RUNNING, isRunning)
            .putLong(KEY_START_TIME, startTime)
            .putLong(KEY_ELAPSED_TIME, elapsedTime)
            .putString(KEY_ACTIVITY_ID, currentActivityId)
            .putString(KEY_ACTIVITY_NAME, currentActivityName)
            .apply()
    }

    private fun restoreServiceState() {
        isRunning = prefs.getBoolean(KEY_IS_RUNNING, false)
        startTime = prefs.getLong(KEY_START_TIME, 0L)
        elapsedTime = prefs.getLong(KEY_ELAPSED_TIME, 0L)
        currentActivityId = prefs.getString(KEY_ACTIVITY_ID, null)
        currentActivityName = prefs.getString(KEY_ACTIVITY_NAME, null)
    }

    private fun clearServiceState() {
        prefs.edit().clear().apply()
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
            val totalSpentTime = timeInMillis / 1000
            val endTime = System.currentTimeMillis()
            val startTime = endTime - timeInMillis

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val startTimeIndian = dateFormat.format(Date(startTime))
            val stopTimeIndian = dateFormat.format(Date(endTime))

            repository.addActivityInstance(
                activityId = currentActivityId!!,
                totalSpentTime = totalSpentTime,
                startTime = startTimeIndian,
                stopTime = stopTimeIndian
            )

            Log.d("StopwatchService", "Instance saved: $totalSpentTime seconds for $currentActivityName")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mindful Minutes Stopwatch",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live activity tracking notification"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val activityName = currentActivityName ?: getString(R.string.app_name)
        val formattedTime = formatTime(getCurrentTime())

        // Tap notification -> Navigate directly into MainActivity
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "active_activities")
            putExtra("activity_id", currentActivityId)
            putExtra("activity_name", currentActivityName)
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause/Resume PendingIntent
        val pauseResumeIntent = Intent(this, StopwatchService::class.java).apply {
            action = if (isRunning) ACTION_PAUSE else ACTION_RESUME
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 2, pauseResumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop PendingIntent
        val stopIntent = Intent(this, StopwatchService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1. Collapsed RemoteViews
        val collapsedView = RemoteViews(packageName, R.layout.notification_stopwatch_collapsed).apply {
            setTextViewText(R.id.tvNotificationTitle, activityName)
            setTextViewText(R.id.tvNotificationTimer, formattedTime)
            setImageViewResource(
                R.id.btnNotificationPauseResume,
                if (isRunning) R.drawable.ic_pause else R.drawable.ic_play
            )
            setOnClickPendingIntent(R.id.btnNotificationPauseResume, pauseResumePendingIntent)
            setOnClickPendingIntent(R.id.btnNotificationStop, stopPendingIntent)
            setOnClickPendingIntent(R.id.notificationRootCollapsed, mainPendingIntent)
        }

        // 2. Expanded RemoteViews
        val expandedView = RemoteViews(packageName, R.layout.notification_stopwatch_expanded).apply {
            setTextViewText(R.id.tvExpandedActivityName, activityName)
            setTextViewText(R.id.tvExpandedTimer, formattedTime)

            if (isRunning) {
                setTextViewText(R.id.tvStatusTag, "● ACTIVE")
                setTextViewText(R.id.tvSessionSubtitle, "Live tracking session")
                setTextViewText(R.id.tvExpandedPauseResumeLabel, "Pause")
                setImageViewResource(R.id.ivExpandedPauseResumeIcon, R.drawable.ic_pause)
                setInt(R.id.tvStatusTag, "setBackgroundResource", R.drawable.notification_badge_active)
                setTextColor(R.id.tvStatusTag, ContextCompat.getColor(this@StopwatchService, R.color.white))
                setTextColor(R.id.tvExpandedTimer, ContextCompat.getColor(this@StopwatchService, R.color.modern_secondary))
            } else {
                setTextViewText(R.id.tvStatusTag, "⏸ PAUSED")
                setTextViewText(R.id.tvSessionSubtitle, "Session is paused")
                setTextViewText(R.id.tvExpandedPauseResumeLabel, "Resume")
                setImageViewResource(R.id.ivExpandedPauseResumeIcon, R.drawable.ic_play)
                setInt(R.id.tvStatusTag, "setBackgroundResource", R.drawable.notification_badge_paused)
                setTextColor(R.id.tvStatusTag, ContextCompat.getColor(this@StopwatchService, R.color.white))
                setTextColor(R.id.tvExpandedTimer, ContextCompat.getColor(this@StopwatchService, R.color.warning_light))
            }

            setOnClickPendingIntent(R.id.btnExpandedPauseResume, pauseResumePendingIntent)
            setOnClickPendingIntent(R.id.btnExpandedStop, stopPendingIntent)
            setOnClickPendingIntent(R.id.notificationRootExpanded, mainPendingIntent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stopwatch)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(mainPendingIntent)
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
            .setOngoing(currentActivityId != null) // Keep notification pinned while session is active or paused
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        notificationManager?.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        stopTimer()
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

        private const val PREFS_STOPWATCH = "stopwatch_service_prefs"
        private const val KEY_IS_RUNNING = "key_is_running"
        private const val KEY_START_TIME = "key_start_time"
        private const val KEY_ELAPSED_TIME = "key_elapsed_time"
        private const val KEY_ACTIVITY_ID = "key_activity_id"
        private const val KEY_ACTIVITY_NAME = "key_activity_name"
    }
}
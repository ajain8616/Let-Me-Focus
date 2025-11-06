package com.sakhi.mindfulminutes.adapters

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.databinding.ActivityCardItemBinding
import com.sakhi.mindfulminutes.model.Activity
import com.sakhi.mindfulminutes.services.StopwatchService
import com.sakhi.mindfulminutes.repository.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActiveActivityAdapter(
    private var activityList: MutableList<Activity>,
    private val stopwatchService: StopwatchService?,
    private val onActivityUpdate: () -> Unit
) : RecyclerView.Adapter<ActiveActivityAdapter.ActivityViewHolder>() {

    private val repository = ActivityRepository()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun updateList(newList: List<Activity>) {
        activityList.clear()
        activityList.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val binding = ActivityCardItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ActivityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val activity = activityList[position]
        holder.bind(activity)
    }

    override fun getItemCount(): Int = activityList.size

    inner class ActivityViewHolder(private val binding: ActivityCardItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val context: Context = binding.root.context
        private var currentActivity: Activity? = null
        private var isTimerRunning = false
        private var isCurrentActivity = false

        init {
            setupClickListeners()
            setupStopwatchListener()
        }

        fun bind(activity: Activity) {
            currentActivity = activity

            binding.activityNameTextView.text = activity.name
            binding.activityNameTextView1.text = activity.name

            // Check if this activity is currently being tracked
            isCurrentActivity = stopwatchService?.getCurrentActivityId() == activity.id

            if (isCurrentActivity) {
                val currentTime = stopwatchService?.getCurrentTime() ?: 0L
                updateStopwatchUI(currentTime)
                isTimerRunning = stopwatchService?.isTimerRunning() ?: false
                updateButtonStates()
                updateStatusIndicator("active")
            } else {
                binding.stopwatchTextView.text = "00:00:00"
                isTimerRunning = false
                updateButtonStates()
                // Set status based on activity status
                updateStatusIndicator(activity.status)
            }

            loadActivityStats(activity.id)
        }

        private fun setupClickListeners() {
            binding.playPauseButton.setOnClickListener {
                currentActivity?.let { activity ->
                    if (isCurrentActivity) {
                        // This activity is currently active
                        if (isTimerRunning) {
                            pauseTimer()
                        } else {
                            resumeTimer()
                        }
                    } else {
                        // Start timer for this activity
                        startTimer(activity)
                    }
                }
            }

            binding.stopButton.setOnClickListener {
                currentActivity?.let { activity ->
                    if (isCurrentActivity) {
                        stopTimer()
                    }
                }
            }

            binding.flipButton.setOnClickListener {
                flipCard()
            }

            binding.closeButton.setOnClickListener {
                currentActivity?.let { activity ->
                    showDeleteConfirmation(activity)
                }
            }

            binding.backButton.setOnClickListener {
                flipCard()
            }

            binding.resetButton.setOnClickListener {
                currentActivity?.let { activity ->
                    showResetConfirmation(activity)
                }
            }
        }

        private fun setupStopwatchListener() {
            stopwatchService?.addListener { time, formattedTime ->
                currentActivity?.let { activity ->
                    if (stopwatchService.getCurrentActivityId() == activity.id) {
                        updateStopwatchUI(time)
                        isTimerRunning = stopwatchService.isTimerRunning()
                        isCurrentActivity = true
                        updateButtonStates()
                        updateStatusIndicator("active")
                    } else {
                        isCurrentActivity = false
                        isTimerRunning = false
                        updateButtonStates()
                        updateStatusIndicator(activity.status)
                    }
                }
            }
        }

        private fun startTimer(activity: Activity) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_START
                putExtra("activityId", activity.id)
                putExtra("activityName", activity.name)
            }
            context.startService(intent)

            isCurrentActivity = true
            isTimerRunning = true
            updateButtonStates()
            updateStatusIndicator("active")

            // Update activity status in database
            updateActivityStatus(activity.id, "active")
        }

        private fun pauseTimer() {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_PAUSE
            }
            context.startService(intent)

            isTimerRunning = false
            updateButtonStates()
            updateStatusIndicator("paused")

            // Update activity status in database
            currentActivity?.let { activity ->
                updateActivityStatus(activity.id, "paused")
            }
        }

        private fun resumeTimer() {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_RESUME
            }
            context.startService(intent)

            isTimerRunning = true
            updateButtonStates()
            updateStatusIndicator("active")

            // Update activity status in database
            currentActivity?.let { activity ->
                updateActivityStatus(activity.id, "active")
            }
        }

        private fun stopTimer() {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_STOP
            }
            context.startService(intent)

            isTimerRunning = false
            isCurrentActivity = false
            updateButtonStates()
            binding.stopwatchTextView.text = "00:00:00"
            updateStatusIndicator("inactive")

            // Update activity status in database
            currentActivity?.let { activity ->
                updateActivityStatus(activity.id, "inactive")
            }
        }

        private fun updateStopwatchUI(time: Long) {
            val seconds = (time / 1000) % 60
            val minutes = (time / (1000 * 60)) % 60
            val hours = (time / (1000 * 60 * 60))
            binding.stopwatchTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

        private fun updateButtonStates() {
            if (isCurrentActivity) {
                if (isTimerRunning) {
                    // Timer is running - show pause button
                    binding.playPauseButton.setImageResource(R.drawable.ic_pause)
                    binding.playPauseButton.setBackgroundResource(R.drawable.rounded_button_outline_red)
                    binding.playPauseButton.imageTintList = ContextCompat.getColorStateList(context, R.color.error)
                    binding.stopButton.visibility = View.VISIBLE
                } else {
                    // Timer is paused - show play button
                    binding.playPauseButton.setImageResource(R.drawable.ic_play)
                    binding.playPauseButton.setBackgroundResource(R.drawable.rounded_button_outline_green)
                    binding.playPauseButton.imageTintList = ContextCompat.getColorStateList(context, R.color.success_dark)
                    binding.stopButton.visibility = View.VISIBLE
                }
            } else {
                // This activity is not active - show play button to start
                binding.playPauseButton.setImageResource(R.drawable.ic_play)
                binding.playPauseButton.setBackgroundResource(R.drawable.rounded_button_outline_green)
                binding.playPauseButton.imageTintList = ContextCompat.getColorStateList(context, R.color.success_dark)
                binding.stopButton.visibility = View.GONE
            }
        }

        private fun updateStatusIndicator(status: String) {
            when (status) {
                "active" -> {
                    binding.statusIndicator.setBackgroundResource(R.drawable.status_indicator_active)
                }
                "paused" -> {
                    binding.statusIndicator.setBackgroundResource(R.drawable.status_indicator_paused)
                }
                "inactive" -> {
                    binding.statusIndicator.setBackgroundResource(R.drawable.status_indicator_inactive)
                }
                else -> {
                    binding.statusIndicator.setBackgroundResource(R.drawable.status_indicator_inactive)
                }
            }
        }

        private fun updateActivityStatus(activityId: String, status: String) {
            coroutineScope.launch {
                try {
                    repository.updateActivityStatus(activityId, status)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun loadActivityStats(activityId: String) {
            coroutineScope.launch {
                try {
                    val totalTime = repository.getTotalActivityTime(activityId)
                    val sessionCount = repository.getActivitySessionCount(activityId)
                    val lastInstance = repository.getLastActivityInstance(activityId)

                    val averageTime = if (sessionCount > 0) totalTime / sessionCount else 0

                    // Update back side stats
                    binding.backTextView1.text = formatTime(totalTime)
                    binding.backTextView2.text = sessionCount.toString()
                    binding.backTextView3.text = formatTime(averageTime)
                    binding.backTextView4.text = lastInstance?.let {
                        formatTime(it.duration)
                    } ?: "00:00:00"

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun formatTime(seconds: Long): String {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, secs)
        }

        private fun flipCard() {
            val scale = context.resources.displayMetrics.density
            val cameraDistance = 8000 * scale
            binding.cardFront.cameraDistance = cameraDistance
            binding.cardBack.cameraDistance = cameraDistance

            val animIn = AnimatorInflater.loadAnimator(context, R.animator.card_flip_in) as AnimatorSet
            val animOut = AnimatorInflater.loadAnimator(context, R.animator.card_flip_out) as AnimatorSet

            if (binding.cardFront.visibility == View.VISIBLE) {
                animOut.setTarget(binding.cardFront)
                animIn.setTarget(binding.cardBack)
                animOut.start()
                animIn.start()
                binding.cardFront.visibility = View.GONE
                binding.cardBack.visibility = View.VISIBLE
            } else {
                animOut.setTarget(binding.cardBack)
                animIn.setTarget(binding.cardFront)
                animOut.start()
                animIn.start()
                binding.cardBack.visibility = View.GONE
                binding.cardFront.visibility = View.VISIBLE
            }
        }

        private fun showDeleteConfirmation(activity: Activity) {
            AlertDialog.Builder(context)
                .setTitle("Delete Activity")
                .setMessage("Are you sure you want to delete ${activity.name}? This will remove all associated data.")
                .setPositiveButton("Delete") { _, _ ->
                    coroutineScope.launch {
                        try {
                            // Stop timer if this activity is currently active
                            if (isCurrentActivity) {
                                stopTimer()
                            }
                            repository.deleteActivity(activity.id)
                            onActivityUpdate()
                            showSnackbar("Activity '${activity.name}' deleted successfully")
                        } catch (e: Exception) {
                            showSnackbar("Failed to delete activity: ${e.message}")
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun showResetConfirmation(activity: Activity) {
            AlertDialog.Builder(context)
                .setTitle("Reset Statistics")
                .setMessage("Are you sure you want to reset all statistics for ${activity.name}? This cannot be undone.")
                .setPositiveButton("Reset") { _, _ ->
                    coroutineScope.launch {
                        try {
                            repository.resetActivityInstances(activity.id)
                            loadActivityStats(activity.id)
                            onActivityUpdate()
                            showSnackbar("Statistics reset for '${activity.name}'")
                        } catch (e: Exception) {
                            showSnackbar("Failed to reset statistics: ${e.message}")
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun showSnackbar(message: String) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
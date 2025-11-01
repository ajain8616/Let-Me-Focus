package com.sakhi.mindfulminutes.adapters

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.databinding.ActivityCardItemBinding
import com.sakhi.mindfulminutes.model.Activity
import com.sakhi.mindfulminutes.model.ActivityInstance
import com.sakhi.mindfulminutes.repository.ActivityRepository
import com.sakhi.mindfulminutes.services.StopwatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.jvm.java

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

        init {
            setupClickListeners()
            setupStopwatchListener()
        }

        fun bind(activity: Activity) {
            currentActivity = activity

            binding.activityNameTextView.text = activity.name
            binding.activityNameTextView1.text = activity.name

            // Update stopwatch if this activity is currently being tracked
            if (stopwatchService?.getCurrentActivityId() == activity.id) {
                val currentTime = stopwatchService.getCurrentTime()
                updateStopwatchUI(currentTime)
                isTimerRunning = stopwatchService.isTimerRunning()
                updateButtonStates()
            } else {
                binding.stopwatchTextView.text = "00:00:00"
                isTimerRunning = false
                updateButtonStates()
            }

            loadActivityStats(activity.id)
        }

        private fun setupClickListeners() {
            binding.playPauseButton.setOnClickListener {
                currentActivity?.let { activity ->
                    if (isTimerRunning) {
                        pauseTimer(activity)
                    } else {
                        startTimer(activity)
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

            isTimerRunning = true
            updateButtonStates()
        }

        private fun pauseTimer(activity: Activity) {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_PAUSE
            }
            context.startService(intent)

            coroutineScope.launch {
                // Save the current session
                saveActivityInstance(activity.id, stopwatchService?.getCurrentTime() ?: 0)
            }

            isTimerRunning = false
            updateButtonStates()
        }

        private fun stopTimer() {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_STOP
            }
            context.startService(intent)

            isTimerRunning = false
            updateButtonStates()
            binding.stopwatchTextView.text = "00:00:00"
        }

        private suspend fun saveActivityInstance(activityId: String, duration: Long) {
            val instance = ActivityInstance(
                activityId = activityId,
                duration = duration / 1000, // Convert to seconds
                endTime = Date()
            )
            repository.addActivityInstance(instance)

            // Update activity stats
            val totalTime = repository.getTotalActivityTime(activityId)
            val sessionCount = repository.getActivitySessionCount(activityId)

            repository.updateActivity(activityId, mapOf(
                "totalTime" to totalTime,
                "sessionCount" to sessionCount
            ))

            loadActivityStats(activityId)
        }

        private fun updateStopwatchUI(time: Long) {
            val seconds = (time / 1000) % 60
            val minutes = (time / (1000 * 60)) % 60
            val hours = (time / (1000 * 60 * 60))
            binding.stopwatchTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

        private fun updateButtonStates() {
            if (isTimerRunning) {
                binding.playPauseButton.setImageResource(R.drawable.ic_pause)
                binding.playPauseButton.setBackgroundResource(R.drawable.rounded_button_outline_red)
                binding.playPauseButton.imageTintList = context.getColorStateList(R.color.error)
            } else {
                binding.playPauseButton.setImageResource(R.drawable.ic_play)
                binding.playPauseButton.setBackgroundResource(R.drawable.rounded_button_outline_green)
                binding.playPauseButton.imageTintList = context.getColorStateList(R.color.success_dark)
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
                .setMessage("Are you sure you want to delete ${activity.name}?")
                .setPositiveButton("Delete") { _, _ ->
                    coroutineScope.launch {
                        try {
                            repository.deleteActivity(activity.id)
                            onActivityUpdate()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun showResetConfirmation(activity: Activity) {
            AlertDialog.Builder(context)
                .setTitle("Reset Stats")
                .setMessage("Are you sure you want to reset statistics for ${activity.name}?")
                .setPositiveButton("Reset") { _, _ ->
                    coroutineScope.launch {
                        try {
                            // Implementation for resetting stats
                            onActivityUpdate()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun shareActivityStats() {
            // Implement share functionality
            currentActivity?.let { activity ->
                val shareMessage = "Check out my activity: ${activity.name}\n" +
                        "Total Time: ${binding.backTextView1.text}\n" +
                        "Sessions: ${binding.backTextView2.text}"

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                }
                context.startActivity(Intent.createChooser(intent, "Share Activity Stats"))
            }
        }


    }
}
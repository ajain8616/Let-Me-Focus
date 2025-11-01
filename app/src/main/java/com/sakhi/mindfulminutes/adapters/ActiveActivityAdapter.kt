package com.sakhi.mindfulminutes.adapters

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.databinding.ActivityCardItemBinding
import com.sakhi.mindfulminutes.model.Activity
import com.sakhi.mindfulminutes.models.ActivityInstance
import com.sakhi.mindfulminutes.services.StopwatchService
import com.sakhi.mindfulminutes.repository.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

            binding.shareButton.setOnClickListener {
                currentActivity?.let { activity ->
                    showShareOptions(activity)
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
                    } else {
                        isCurrentActivity = false
                        isTimerRunning = false
                        updateButtonStates()
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
        }

        private fun pauseTimer() {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_PAUSE
            }
            context.startService(intent)

            isTimerRunning = false
            updateButtonStates()
        }

        private fun resumeTimer() {
            val intent = Intent(context, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_RESUME
            }
            context.startService(intent)

            isTimerRunning = true
            updateButtonStates()
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
        }

        private fun updateStopwatchUI(time: Long) {
            val seconds = (time / 1000) % 60
            val minutes = (time / (1000 * 60)) % 60
            val hours = (time / (1000 * 60 * 60))
            binding.stopwatchTextView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

        private fun updateButtonStates() {
            if (isCurrentActivity) {
                // This activity is currently active
                if (isTimerRunning) {
                    // Timer is running - show pause button
                    binding.playPauseButton.setImageResource(R.drawable.ic_pause)
                    binding.playPauseButton.setBackgroundResource(R.drawable.rounded_button_outline_red)
                    binding.playPauseButton.imageTintList = context.getColorStateList(R.color.error)
                    binding.stopButton.visibility = View.VISIBLE
                } else {
                    // Timer is paused - show play button
                    binding.playPauseButton.setImageResource(R.drawable.ic_play)
                    binding.playPauseButton.setBackgroundResource(R.drawable.rounded_button_outline_green)
                    binding.playPauseButton.imageTintList = context.getColorStateList(R.color.success_dark)
                    binding.stopButton.visibility = View.VISIBLE
                }
            } else {
                // This activity is not active - show play button to start
                binding.playPauseButton.setImageResource(R.drawable.ic_play)
                binding.playPauseButton.setBackgroundResource(R.drawable.rounded_button_outline_green)
                binding.playPauseButton.imageTintList = context.getColorStateList(R.color.success_dark)
                binding.stopButton.visibility = View.GONE
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
                            // Use the new reset functionality from repository
                            repository.resetActivityInstances(activity.id)
                            loadActivityStats(activity.id) // Refresh stats
                            onActivityUpdate()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun showShareOptions(activity: Activity) {
            val options = arrayOf("Share as Image", "Share as PDF", "Share as Text")

            AlertDialog.Builder(context)
                .setTitle("Share Activity Stats")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> shareActivityAsImage(activity)
                        1 -> shareActivityAsPdf(activity)
                        2 -> shareActivityStats(activity)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun shareActivityAsImage(activity: Activity) {
            coroutineScope.launch {
                try {
                    // Get activity stats using repository methods
                    val totalTime = repository.getTotalActivityTime(activity.id)
                    val sessionCount = repository.getActivitySessionCount(activity.id)
                    val averageTime = if (sessionCount > 0) totalTime / sessionCount else 0
                    val lastInstance = repository.getLastActivityInstance(activity.id)

                    // Create share message
                    val shareMessage = """
                        🎯 Activity: ${activity.name}
                        
                        📊 Statistics:
                        ⏱️ Total Time: ${formatTime(totalTime)}
                        📈 Sessions Completed: $sessionCount
                        📊 Average Time/Session: ${formatTime(averageTime)}
                        ⏰ Last Session: ${lastInstance?.let { formatTime(it.duration) } ?: "00:00:00"}
                        
                        🚀 Tracked via Let Me Focus App
                        #Productivity #TimeTracking #MindfulMinutes
                    """.trimIndent()

                    // Create bitmap from card view
                    val bitmap = createBitmapFromView(binding.mainCardView)

                    // Save bitmap to file
                    val imageFile = saveBitmapToFile(bitmap, "${activity.name}_stats.png")

                    // Share both image and text
                    shareActivityContent(shareMessage, imageFile, activity.name)

                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback to text sharing if image sharing fails
                    shareActivityStats(activity)
                }
            }
        }

        private fun shareActivityAsPdf(activity: Activity) {
            coroutineScope.launch {
                try {
                    // Get activity stats using repository methods
                    val totalTime = repository.getTotalActivityTime(activity.id)
                    val sessionCount = repository.getActivitySessionCount(activity.id)
                    val averageTime = if (sessionCount > 0) totalTime / sessionCount else 0
                    val lastInstance = repository.getLastActivityInstance(activity.id)

                    // Create PDF file
                    val pdfFile = createActivityPdf(activity, totalTime, sessionCount, averageTime, lastInstance)

                    // Share PDF
                    sharePdfFile(pdfFile, activity.name)

                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback to text sharing if PDF sharing fails
                    shareActivityStats(activity)
                }
            }
        }

        private fun createActivityPdf(
            activity: Activity,
            totalTime: Long,
            sessionCount: Int,
            averageTime: Long,
            lastInstance: ActivityInstance?
        ): File {
            val pdfFile = File(context.externalCacheDir ?: context.cacheDir, "${activity.name}_stats.pdf")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val currentDate = dateFormat.format(Date())

            val pdfContent = """
                ACTIVITY STATISTICS REPORT
                ==========================
                
                Activity Name: ${activity.name}
                Generated On: $currentDate
                
                STATISTICS:
                -----------
                • Total Time: ${formatTime(totalTime)}
                • Sessions Completed: $sessionCount
                • Average Time/Session: ${formatTime(averageTime)}
                • Last Session Duration: ${lastInstance?.let { formatTime(it.duration) } ?: "00:00:00"}
                • Last Session Start: ${lastInstance?.startTime ?: "N/A"}
                • Last Session End: ${lastInstance?.stopTime ?: "N/A"}
                
                SUMMARY:
                --------
                This report shows your productivity statistics for "${activity.name}".
                Keep tracking your time to improve your focus and productivity!
                
                Generated by Let Me Focus App
                #Productivity #TimeTracking
            """.trimIndent()

            FileOutputStream(pdfFile).use { out ->
                out.write(pdfContent.toByteArray())
            }

            return pdfFile
        }

        private fun sharePdfFile(pdfFile: File, activityName: String) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    pdfFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Activity Stats Report: $activityName")
                    putExtra(Intent.EXTRA_TEXT, "Here are my activity statistics for $activityName")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(intent, "Share Activity Stats as PDF"))

            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to text sharing
                shareActivityStatsText("Failed to share PDF. Here are my stats for $activityName", activityName)
            }
        }

        private fun createBitmapFromView(view: View): Bitmap {
            view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            view.draw(canvas)
            return bitmap
        }

        private fun saveBitmapToFile(bitmap: Bitmap, fileName: String): File {
            val filesDir = context.externalCacheDir ?: context.cacheDir
            val imageFile = File(filesDir, fileName)

            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            return imageFile
        }

        private fun shareActivityContent(shareMessage: String, imageFile: File, activityName: String) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    imageFile
                )

                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_SUBJECT, "My Activity Stats: $activityName")
                    putExtra(Intent.EXTRA_TEXT, shareMessage)

                    // Add image
                    val uris = ArrayList<android.net.Uri>()
                    uris.add(uri)
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)

                    // Grant read permission to the receiving app
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(intent, "Share Activity Stats"))

            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to text sharing
                shareActivityStatsText(shareMessage, activityName)
            }
        }

        private fun shareActivityStats(activity: Activity) {
            coroutineScope.launch {
                try {
                    val totalTime = repository.getTotalActivityTime(activity.id)
                    val sessionCount = repository.getActivitySessionCount(activity.id)
                    val averageTime = if (sessionCount > 0) totalTime / sessionCount else 0
                    val lastInstance = repository.getLastActivityInstance(activity.id)

                    val shareMessage = """
                        📊 Activity Statistics: ${activity.name}
                        
                        ⏱️ Total Time: ${formatTime(totalTime)}
                        📈 Sessions Completed: $sessionCount
                        📊 Average Time/Session: ${formatTime(averageTime)}
                        ⏰ Last Session: ${lastInstance?.let {
                        "Duration: ${formatTime(it.duration)}\nStart: ${it.startTime}\nEnd: ${it.stopTime}"
                    } ?: "00:00:00"}
                        
                        Tracked via Let Me Focus App
                        #Productivity #TimeTracking
                    """.trimIndent()

                    shareActivityStatsText(shareMessage, activity.name)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun shareActivityStatsText(shareMessage: String, activityName: String) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareMessage)
                putExtra(Intent.EXTRA_SUBJECT, "My Activity Stats: $activityName")
            }
            context.startActivity(Intent.createChooser(intent, "Share Activity Stats"))
        }
    }
}
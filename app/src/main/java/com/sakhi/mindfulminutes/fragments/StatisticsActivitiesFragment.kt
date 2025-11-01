package com.sakhi.mindfulminutes.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.sakhi.mindfulminutes.databinding.FragmentStatisticsActivitiesBinding
import com.sakhi.mindfulminutes.models.ActivityInstance
import com.sakhi.mindfulminutes.repository.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.map
import kotlin.collections.mapNotNull

class StatisticsActivitiesFragment : Fragment() {

    private var _binding: FragmentStatisticsActivitiesBinding? = null
    private val binding get() = _binding!!

    private val repository = ActivityRepository()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var activityId: String? = null
    private var activityName: String? = null

    companion object {
        private const val TAG = "StatisticsActivitiesFragment"
        private const val ARG_ACTIVITY_ID = "activity_id"
        private const val ARG_ACTIVITY_NAME = "activity_name"

        fun newInstance(activityId: String, activityName: String): StatisticsActivitiesFragment {
            val fragment = StatisticsActivitiesFragment()
            val args = Bundle().apply {
                putString(ARG_ACTIVITY_ID, activityId)
                putString(ARG_ACTIVITY_NAME, activityName)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            activityId = it.getString(ARG_ACTIVITY_ID)
            activityName = it.getString(ARG_ACTIVITY_NAME)
        }
        Log.d(TAG, "onCreate: Activity ID: $activityId, Name: $activityName")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsActivitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadStatistics()
    }

    private fun setupUI() {
        binding.intentActivityView.text = activityName ?: "Activity Statistics"
        // Setup back button
        binding.backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadStatistics() {
        activityId?.let { id ->
            coroutineScope.launch {
                try {
                    showLoading(true)
                    loadActivityStatistics(id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading statistics: ${e.message}", e)
                    showError("Failed to load statistics: ${e.message}")
                } finally {
                    showLoading(false)
                }
            }
        } ?: showError("Activity ID not found")
    }

    private suspend fun loadActivityStatistics(activityId: String) {
        try {
            Log.d(TAG, "Loading statistics for activity: $activityId")

            val instances = repository.getActivityInstances(activityId)
            Log.d(TAG, "Loaded ${instances.size} instances")

            loadAdditionalActivityData(activityId, instances)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load activity data: ${e.message}", e)
            showError("Failed to load activity data: ${e.message}")
        }
    }

    private suspend fun loadAdditionalActivityData(activityId: String, instances: List<ActivityInstance>) {
        try {
            val totalTime = repository.getTotalActivityTime(activityId)
            val sessionCount = repository.getActivitySessionCount(activityId)
            val lastInstance = repository.getLastActivityInstance(activityId)
            val todayInstances = repository.getTodayActivityInstances(activityId)
            val todayTotalTime = todayInstances.sumOf { it.duration }
            val activityWithStats = repository.getActivityWithStats(activityId)

            Log.d(TAG, """
                Stats Loaded ->
                Total Time: $totalTime sec
                Sessions: $sessionCount
                Today Time: $todayTotalTime sec
                Activity: ${activityWithStats?.name}
            """.trimIndent())

            calculateStatistics(instances, totalTime, sessionCount, lastInstance, todayTotalTime)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading additional activity data: ${e.message}")
            calculateStatistics(instances)
        }
    }

    private fun calculateStatistics(
        instances: List<ActivityInstance>,
        totalTime: Long? = null,
        sessionCount: Int? = null,
        lastInstance: ActivityInstance? = null,
        todayTotalTime: Long? = null
    ) {

        val instanceTimes = instances.map { it.duration }
        val startTimes = instances.mapNotNull { parseTimeString(it.startTime) }
        val stopTimes = instances.mapNotNull { parseTimeString(it.stopTime) }

        // Safe calculations
        val calculatedTotalTime = totalTime ?: instanceTimes.sum()
        val calculatedSessionCount = sessionCount ?: instances.size
        val meanTime = if (calculatedSessionCount > 0) calculatedTotalTime / calculatedSessionCount else 0
        val maxTime = instanceTimes.maxOrNull() ?: 0
        val minTime = instanceTimes.minOrNull() ?: 0
        val medianTime = calculateMedian(instanceTimes)

        // Fixed: safe min/max for dates
        val earliestStart = startTimes.minOrNull()
        val latestStop = stopTimes.maxOrNull()
        val lastPauseTime = stopTimes.lastOrNull()

        updateStatisticsUI(
            totalTime = calculatedTotalTime,
            sessionCount = calculatedSessionCount,
            meanTime = meanTime,
            medianTime = medianTime,
            maxTime = maxTime,
            minTime = minTime,
            earliestStart = earliestStart,
            latestStop = latestStop,
            lastPauseTime = lastPauseTime,
            lastInstance = lastInstance,
            todayTotalTime = todayTotalTime ?: 0
        )
    }

    private fun parseTimeString(timeString: String): Date? {
        return try {
            val formats = arrayOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "MM/dd/yyyy HH:mm:ss",
                "MM/dd/yyyy HH:mm"
            )
            for (format in formats) {
                try {
                    val formatter = SimpleDateFormat(format, Locale.getDefault())
                    return formatter.parse(timeString)
                } catch (_: Exception) { }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing time string: $timeString", e)
            null
        }
    }

    private fun calculateMedian(times: List<Long>): Long {
        if (times.isEmpty()) return 0
        val sortedTimes = times.sorted()
        return if (sortedTimes.size % 2 == 0) {
            val mid = sortedTimes.size / 2
            (sortedTimes[mid - 1] + sortedTimes[mid]) / 2
        } else {
            sortedTimes[sortedTimes.size / 2]
        }
    }

    private fun updateStatisticsUI(
        totalTime: Long,
        sessionCount: Int,
        meanTime: Long,
        medianTime: Long,
        maxTime: Long,
        minTime: Long,
        earliestStart: Date?,
        latestStop: Date?,
        lastPauseTime: Date?,
        lastInstance: ActivityInstance? = null,
        todayTotalTime: Long = 0
    ) {
        try {
            binding.totalTimeTextView.text = formatTime(totalTime)
            binding.countTimeTextView.text = sessionCount.toString()
            binding.meanTimeTextView.text = formatTime(meanTime)
            binding.medianTimeTextView.text = formatTime(medianTime)
            binding.maximumTimeTextView.text = formatTime(maxTime)
            binding.minimumTimeTextView.text = formatTime(minTime)
            binding.sTimeTextView.text = earliestStart?.let { formatDateTime(it) } ?: "N/A"
            binding.endTimeTextView.text = latestStop?.let { formatDateTime(it) } ?: "N/A"
            binding.pauseTimeTextView.text = lastPauseTime?.let { formatDateTime(it) } ?: "N/A"

            updateAdditionalStatisticsUI(lastInstance, todayTotalTime, sessionCount)
            Log.d(TAG, "Statistics UI updated successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating statistics UI: ${e.message}", e)
            showError("Error displaying statistics")
        }
    }

    private fun updateAdditionalStatisticsUI(
        lastInstance: ActivityInstance?,
        todayTotalTime: Long,
        totalSessions: Int
    ) {
        binding.lastSessionTextView?.text = lastInstance?.let {
            formatTime(it.duration)
        } ?: "N/A"

        binding.todayTimeTextView?.text = formatTime(todayTotalTime)
        binding.totalSessionsTextView?.text = totalSessions.toString()
    }

    private fun formatTime(seconds: Long): String {
        return if (seconds <= 0) {
            "00:00:00"
        } else {
            val hours = TimeUnit.SECONDS.toHours(seconds)
            val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
            val remainingSeconds = seconds % 60
            String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
        }
    }

    private fun formatDateTime(date: Date): String {
        val formatter = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        return formatter.format(date)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.contentLayout.visibility = if (show) View.GONE else View.VISIBLE
    }



    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Refreshing statistics")
        loadStatistics()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

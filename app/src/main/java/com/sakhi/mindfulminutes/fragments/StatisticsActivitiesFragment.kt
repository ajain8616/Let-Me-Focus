package com.sakhi.mindfulminutes.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.sakhi.mindfulminutes.databinding.FragmentStatisticsActivitiesBinding
import com.sakhi.mindfulminutes.repository.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class StatisticsActivitiesFragment : Fragment() {

    private var _binding: FragmentStatisticsActivitiesBinding? = null
    private val binding get() = _binding!!

    private val repository = ActivityRepository()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var activityId: String? = null
    private var activityName: String? = null

    companion object {
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
        // Set activity name
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
                    showError("Failed to load statistics: ${e.message}")
                } finally {
                    showLoading(false)
                }
            }
        } ?: showError("Activity ID not found")
    }

    private suspend fun loadActivityStatistics(activityId: String) {
        try {
            // Load activity instances from Firestore
            val instances = repository.getActivityInstances(activityId)

            if (instances.isEmpty()) {
                showEmptyState()
                return
            }

            // Calculate statistics
            calculateStatistics(instances)

        } catch (e: Exception) {
            showError("Failed to load activity data: ${e.message}")
        }
    }

    private fun calculateStatistics(instances: List<com.sakhi.mindfulminutes.model.ActivityInstance>) {
        if (instances.isEmpty()) {
            showEmptyState()
            return
        }

        val instanceTimes = instances.map { it.duration }
        val startTimes = instances.map { it.startTime }
        val endTimes = instances.mapNotNull { it.endTime }

        // Calculate basic statistics
        val totalTime = instanceTimes.sum()
        val sessionCount = instances.size
        val meanTime = if (sessionCount > 0) totalTime / sessionCount else 0
        val maxTime = instanceTimes.maxOrNull() ?: 0
        val minTime = instanceTimes.minOrNull() ?: 0

        // Calculate median
        val medianTime = calculateMedian(instanceTimes)

        // Find earliest start and latest end times
        val earliestStart = startTimes.minOrNull()
        val latestEnd = endTimes.maxOrNull()

        // Get last pause time (assuming last instance's end time as pause time)
        val lastPauseTime = endTimes.lastOrNull()

        // Update UI
        updateStatisticsUI(
            totalTime = totalTime,
            sessionCount = sessionCount,
            meanTime = meanTime,
            medianTime = medianTime,
            maxTime = maxTime,
            minTime = minTime,
            earliestStart = earliestStart,
            latestEnd = latestEnd,
            lastPauseTime = lastPauseTime
        )
    }

    private fun calculateMedian(times: List<Long>): Long {
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
        latestEnd: Date?,
        lastPauseTime: Date?
    ) {
        // Update time values
        binding.totalTimeTextView.text = formatTime(totalTime)
        binding.countTimeTextView.text = sessionCount.toString()
        binding.meanTimeTextView.text = formatTime(meanTime)
        binding.medianTimeTextView.text = formatTime(medianTime)
        binding.maximumTimeTextView.text = formatTime(maxTime)
        binding.minimumTimeTextView.text = formatTime(minTime)

        // Update date/time values
        binding.sTimeTextView.text = earliestStart?.let { formatDateTime(it) } ?: "NA"
        binding.endTimeTextView.text = latestEnd?.let { formatDateTime(it) } ?: "NA"
        binding.pauseTimeTextView.text = lastPauseTime?.let { formatDateTime(it) } ?: "NA"
    }

    private fun formatTime(seconds: Long): String {
        return if (seconds <= 0) {
            "00:00:00"
        } else {
            val hours = TimeUnit.SECONDS.toHours(seconds)
            val minutes = TimeUnit.SECONDS.toMinutes(seconds - TimeUnit.HOURS.toSeconds(hours))
            val remainingSeconds = seconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes)
            String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
        }
    }

    private fun formatDateTime(date: Date): String {
        val formatter = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        return formatter.format(date)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState() {
        // You can add an empty state view if needed
        Snackbar.make(binding.root, "No activity data found", Snackbar.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
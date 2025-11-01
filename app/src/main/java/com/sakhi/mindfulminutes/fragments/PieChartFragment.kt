package com.sakhi.mindfulminutes.fragments

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.snackbar.Snackbar
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.databinding.FragmentPieChartBinding
import com.sakhi.mindfulminutes.repository.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PieChartFragment : Fragment() {

    private var _binding: FragmentPieChartBinding? = null
    private val binding get() = _binding!!

    private lateinit var pieChartView: PieChart
    private val repository = ActivityRepository()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Add tag for debugging
    companion object {
        const val TAG = "PieChartFragment"
    }

    // Color palette for the chart
    private val colors = intArrayOf(
        Color.parseColor("#FF6B6B"),
        Color.parseColor("#4ECDC4"),
        Color.parseColor("#45B7D1"),
        Color.parseColor("#96CEB4"),
        Color.parseColor("#FFEAA7"),
        Color.parseColor("#DDA0DD"),
        Color.parseColor("#98D8C8"),
        Color.parseColor("#F7DC6F"),
        Color.parseColor("#BB8FCE"),
        Color.parseColor("#85C1E9")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentPieChartBinding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView: Fragment created")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Setting up views")
        initializeViews()
        setupClickListeners()
        setupSwipeRefresh()
        setupChart()

        // Load data
        fetchDataAndCreatePieChart()
    }

    private fun initializeViews() {
        pieChartView = binding.pieChartView
        Log.d(TAG, "initializeViews: Pie chart view initialized")
    }

    private fun setupChart() {
        // Configure the pie chart
        pieChartView.setDrawHoleEnabled(true)
        pieChartView.setHoleColor(Color.TRANSPARENT)
        pieChartView.setTransparentCircleColor(Color.TRANSPARENT)
        pieChartView.setTransparentCircleAlpha(110)
        pieChartView.holeRadius = 40f
        pieChartView.transparentCircleRadius = 45f
        pieChartView.setDrawCenterText(true)
        pieChartView.centerText = "Activity\nDistribution"
        pieChartView.setCenterTextSize(16f)
        pieChartView.setCenterTextColor(Color.parseColor("#333333"))

        // Enable rotation
        pieChartView.isRotationEnabled = true
        pieChartView.setDrawEntryLabels(true)
        pieChartView.setEntryLabelColor(Color.parseColor("#333333"))
        pieChartView.setEntryLabelTextSize(12f)

        // Configure legend
        val legend = pieChartView.legend
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.textSize = 12f
        legend.textColor = Color.parseColor("#333333")
        legend.formSize = 12f
        legend.xEntrySpace = 10f
        legend.yEntrySpace = 5f

        // Configure description
        pieChartView.description.isEnabled = false

        // Enable touch interactions
        pieChartView.isHighlightPerTapEnabled = true

        Log.d(TAG, "Chart setup completed")
    }

    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            Log.d(TAG, "Refresh button clicked")
            fetchDataAndCreatePieChart()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Swipe to refresh triggered")
            fetchDataAndCreatePieChart()
        }
    }

    private fun fetchDataAndCreatePieChart() {
        showLoading(true)
        binding.emptyState.visibility = View.GONE

        Log.d(TAG, "fetchDataAndCreatePieChart: Starting data fetch")

        coroutineScope.launch {
            try {
                // Method 1: Using getAllActivities() and getTotalActivityTime() for each activity
                val activities = repository.getAllActivities()
                Log.d(TAG, "Fetched ${activities.size} activities using getAllActivities()")

                if (activities.isNotEmpty()) {
                    val pieEntries = mutableListOf<PieEntry>()
                    var totalActivitiesWithTime = 0
                    var totalTimeAllActivities = 0L

                    // Process each activity using repository functions
                    for (activity in activities) {
                        try {
                            // Use getTotalActivityTime() which internally calls getActivityInstances()
                            val totalTime = repository.getTotalActivityTime(activity.id)
                            Log.d(TAG, "Activity '${activity.name}': Total time = $totalTime seconds")

                            if (totalTime > 0) {
                                totalActivitiesWithTime++
                                totalTimeAllActivities += totalTime

                                val formattedTime = formatTime(totalTime.toInt())
                                val sessionCount = repository.getActivitySessionCount(activity.id)
                                val label = "${activity.name}\n$formattedTime\n$sessionCount sessions"

                                pieEntries.add(PieEntry(totalTime.toFloat(), label))

                                Log.d(TAG, "Added to chart: ${activity.name} - $totalTime seconds, $sessionCount sessions")

                                // Also get the last instance for additional info
                                val lastInstance = repository.getLastActivityInstance(activity.id)
                                lastInstance?.let {
                                    Log.d(TAG, "Last instance for '${activity.name}': Duration=${it.duration}s, Start=${it.startTime}")
                                }
                            } else {
                                Log.d(TAG, "Skipped activity '${activity.name}': No time spent")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing activity '${activity.name}': ${e.message}")
                            continue
                        }
                    }

                    Log.d(TAG, "Total activities with time data: $totalActivitiesWithTime")
                    Log.d(TAG, "Total time across all activities: $totalTimeAllActivities seconds")
                    Log.d(TAG, "Pie entries prepared: ${pieEntries.size}")

                    if (pieEntries.isNotEmpty()) {
                        createPieChart(pieEntries, totalTimeAllActivities)
                        updateEmptyState(false)
                        Log.d(TAG, "Pie chart created successfully with ${pieEntries.size} entries")
                    } else {
                        updateEmptyState(true)
                        Log.d(TAG, "No activities with time data available")
                    }
                } else {
                    updateEmptyState(true)
                    Log.d(TAG, "No activities found in repository")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load chart data: ${e.message}", e)
                showError("Failed to load chart data: ${e.message}")
                updateEmptyState(true)
            } finally {
                showLoading(false)
                binding.swipeRefreshLayout.isRefreshing = false
                Log.d(TAG, "Data fetch completed")
            }
        }
    }

    /**
     * Alternative method: Get detailed instance data for debugging
     */
    private suspend fun getDetailedActivityData() {
        try {
            val activities = repository.getAllActivities()
            Log.d(TAG, "=== DETAILED ACTIVITY DATA ===")

            for (activity in activities) {
                Log.d(TAG, "Activity: ${activity.name} (ID: ${activity.id})")

                // Get activity with stats
                val activityWithStats = repository.getActivityWithStats(activity.id)
                Log.d(TAG, "Activity stats - TotalTime: ${activityWithStats?.totalTime}, SessionCount: ${activityWithStats?.sessionCount}")

                // Get all instances
                val instances = repository.getActivityInstances(activity.id)
                Log.d(TAG, "Number of instances: ${instances.size}")

                instances.forEachIndexed { index, instance ->
                    Log.d(TAG, "  Instance $index: Duration=${instance.duration}s, " +
                            "Start=${instance.startTime}, End=${instance.endTime}, " +
                            "Status=${instance.status}")
                }

                // Get total time using repository function
                val totalTime = repository.getTotalActivityTime(activity.id)
                Log.d(TAG, "Calculated total time: $totalTime seconds")

                // Get session count
                val sessionCount = repository.getActivitySessionCount(activity.id)
                Log.d(TAG, "Session count: $sessionCount")

                Log.d(TAG, "---")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting detailed activity data: ${e.message}")
        }
    }

    private fun createPieChart(pieEntries: MutableList<PieEntry>, totalTime: Long) {
        try {
            Log.d(TAG, "Creating pie chart with ${pieEntries.size} entries")

            val dataSet = PieDataSet(pieEntries, "")
            dataSet.colors = colors.toList()
            dataSet.sliceSpace = 3f
            dataSet.selectionShift = 5f
            dataSet.valueTextSize = 12f
            dataSet.valueTextColor = Color.WHITE

            // Custom value formatter
            dataSet.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()}s"
                }
            }

            val pieData = PieData(dataSet)
            pieData.setValueTextSize(12f)
            pieData.setValueTextColor(Color.WHITE)

            pieChartView.data = pieData

            // Update center text with total information
            val totalFormattedTime = formatTime(totalTime.toInt())
            pieChartView.centerText = "Total Time\n$totalFormattedTime\n${pieEntries.size} activities"

            // Animate the chart
            pieChartView.animateY(1000, Easing.EaseInOutCubic)
            pieChartView.invalidate()

            Log.d(TAG, "Pie chart created successfully with total time: $totalTime seconds")

        } catch (e: Exception) {
            Log.e(TAG, "Error creating pie chart: ${e.message}", e)
            showError("Error creating chart: ${e.message}")
        }
    }

    private fun formatTime(seconds: Int): String {
        val hours = TimeUnit.SECONDS.toHours(seconds.toLong())
        val minutes = TimeUnit.SECONDS.toMinutes(seconds.toLong() - TimeUnit.HOURS.toSeconds(hours))
        val remainingSeconds = seconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes)

        return if (hours > 0) {
            String.format("%dh %02dm %02ds", hours, minutes, remainingSeconds)
        } else if (minutes > 0) {
            String.format("%dm %02ds", minutes, remainingSeconds)
        } else {
            String.format("%ds", remainingSeconds)
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.emptyState.visibility = View.GONE
            pieChartView.visibility = View.GONE
            Log.d(TAG, "Loading state: SHOW")
        } else {
            pieChartView.visibility = View.VISIBLE
            Log.d(TAG, "Loading state: HIDE")
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyState.visibility = View.VISIBLE
            pieChartView.visibility = View.GONE
            Log.d(TAG, "Empty state: SHOW")
        } else {
            binding.emptyState.visibility = View.GONE
            pieChartView.visibility = View.VISIBLE
            Log.d(TAG, "Empty state: HIDE")
        }
    }

    private fun showError(message: String) {
        Log.e(TAG, "Error: $message")
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Fragment resumed")
        // Refresh chart when fragment becomes visible
        fetchDataAndCreatePieChart()

        // Also get detailed data for debugging
        coroutineScope.launch {
            getDetailedActivityData()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Fragment destroyed")
        _binding = null
    }
}
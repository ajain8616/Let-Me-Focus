package com.sakhi.mindfulminutes.fragments

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.snackbar.Snackbar
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.databinding.FragmentPieChartBinding
import com.sakhi.mindfulminutes.model.Activity
import com.sakhi.mindfulminutes.models.ChartData
import com.sakhi.mindfulminutes.repository.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class PieChartFragment : Fragment() {

    private var _binding: FragmentPieChartBinding? = null
    private val binding get() = _binding!!

    private lateinit var pieChart: PieChart
    private val repository = ActivityRepository()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

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
        Color.parseColor("#85C1E9"),
        Color.parseColor("#F8B195"),
        Color.parseColor("#F67280"),
        Color.parseColor("#C06C84"),
        Color.parseColor("#6C5B7B"),
        Color.parseColor("#355C7D"),
        Color.parseColor("#99B898"),
        Color.parseColor("#FECEAB"),
        Color.parseColor("#FF847C"),
        Color.parseColor("#E84A5F"),
        Color.parseColor("#2A363B")
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

        // Load data
        fetchDataAndCreatePieChart()
    }

    private fun initializeViews() {
        pieChart = binding.pieChart
        setupPieChart()
        Log.d(TAG, "initializeViews: PieChart view initialized")
    }

    private fun setupPieChart() {
        // Configure the pie chart appearance
        pieChart.setUsePercentValues(false)
        pieChart.description.isEnabled = false
        pieChart.setExtraOffsets(10f, 0f, 10f, 10f) // Reduced offsets since we have card padding
        pieChart.dragDecelerationFrictionCoef = 0.95f
        pieChart.isDrawHoleEnabled = true
        pieChart.holeRadius = 40f
        pieChart.setHoleColor(Color.WHITE)
        pieChart.setTransparentCircleColor(Color.WHITE)
        pieChart.transparentCircleRadius= 12f
        pieChart.setDrawCenterText(false)
        pieChart.rotationAngle = 0f
        pieChart.isRotationEnabled = true
        pieChart.isHighlightPerTapEnabled = true

        // No data text configuration
        pieChart.setNoDataText("No activity data available")
        pieChart.setNoDataTextColor(Color.parseColor("#666666"))

        // Configure legend
        val legend = pieChart.legend
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.xEntrySpace = 7f
        legend.yEntrySpace = 0f
        legend.yOffset = 15f // Increased yOffset for better spacing
        legend.textSize = 12f
        legend.textColor = Color.parseColor("#333333")

        // Configure entry labels
        pieChart.setEntryLabelColor(Color.WHITE)
        pieChart.setEntryLabelTextSize(12f)

        // Add animation
        pieChart.animateY(1400, Easing.EaseInOutQuad)
    }
    private fun setupClickListeners() {
        binding.refreshButton.setOnClickListener {
            Log.d(TAG, "Refresh button clicked")
            fetchDataAndCreatePieChart()
        }

        // Setup filter buttons
        binding.filterTodayButton.setOnClickListener {
            loadTodayData()
        }

        binding.filterWeekButton.setOnClickListener {
            loadWeekData()
        }

        binding.filterMonthButton.setOnClickListener {
            loadMonthData()
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
                // Get all activities using repository
                val activities = repository.getAllActivities()
                Log.d(TAG, "Fetched ${activities.size} activities using getAllActivities()")

                if (activities.isNotEmpty()) {
                    // Create comprehensive data using multiple repository methods
                    val chartData = createComprehensiveChartData(activities)

                    if (chartData.isNotEmpty()) {
                        createPieChart(chartData, activities)
                        updateEmptyState(false)
                        Log.d(TAG, "Pie chart created successfully with ${chartData.size} entries")
                    } else {
                        updateEmptyState(true)
                        Log.d(TAG, "No chart data available")
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

    private suspend fun createComprehensiveChartData(activities: List<Activity>): List<ChartData> {
        val chartData = mutableListOf<ChartData>()

        // Process each activity using multiple repository methods
        for (activity in activities) {
            try {
                // 1. Get total activity time
                val totalTime = repository.getTotalActivityTime(activity.id)

                // 2. Get session count
                val sessionCount = repository.getActivitySessionCount(activity.id)

                // 3. Get today's instances
                val todayInstances = repository.getTodayActivityInstances(activity.id)
                val todayTotalTime = todayInstances.sumOf { it.duration }

                // 4. Get all instances for this activity
                val allInstances = repository.getActivityInstances(activity.id)

                if (totalTime > 0) {
                    val chartEntry = ChartData(
                        activityName = activity.name,
                        totalTime = totalTime,
                        sessionCount = sessionCount,
                        todayTime = todayTotalTime,
                        allInstances = allInstances
                    )

                    chartData.add(chartEntry)

                    Log.d(TAG, "Activity '${activity.name}': " +
                            "Total=${formatTime(totalTime)}, " +
                            "Sessions=$sessionCount, " +
                            "Today=${formatTime(todayTotalTime)}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing activity '${activity.name}': ${e.message}")
                continue
            }
        }

        // Sort by total time (descending)
        return chartData.sortedByDescending { it.totalTime }
    }

    private fun createPieChart(chartData: List<ChartData>, activities: List<Activity>) {
        try {
            Log.d(TAG, "Creating MPAndroidChart pie chart with ${chartData.size} entries")

            // Create entries for the pie chart
            val entries = ArrayList<PieEntry>()

            chartData.forEachIndexed { index, data ->
                // Use seconds as values
                val value = data.totalTime.toFloat()
                val label = "${data.activityName}\n${formatTimeShort(data.totalTime)}"
                entries.add(PieEntry(value, label))
                Log.d(TAG, "Chart entry: ${data.activityName} - ${data.totalTime}s")
            }

            // Create dataset
            val dataSet = PieDataSet(entries, "")
            dataSet.colors = colors.toList()
            dataSet.sliceSpace = 3f
            dataSet.selectionShift = 5f
            dataSet.valueTextSize = 12f
            dataSet.valueTextColor = Color.WHITE

            // Configure value formatter
            dataSet.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return formatTimeShort(value.toLong())
                }
            }

            // Create pie data
            val pieData = PieData(dataSet)
            pieData.setValueTextSize(11f)
            pieData.setValueTextColor(Color.WHITE)

            // Set data to chart
            pieChart.data = pieData
            pieChart.invalidate() // refresh

            // Update statistics
            updateStatistics(chartData, activities.size)

            Log.d(TAG, "MPAndroidChart pie chart created successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error creating pie chart: ${e.message}", e)
            showError("Error creating chart: ${e.message}")
        }
    }

    private fun updateStatistics(chartData: List<ChartData>, totalActivities: Int) {
        val totalTime = chartData.sumOf { it.totalTime }
        val totalSessions = chartData.sumOf { it.sessionCount }
        val activitiesWithData = chartData.size

        binding.statsContainer.visibility = View.VISIBLE
        binding.totalTimeText.text = formatTime(totalTime)
        binding.totalSessionsText.text = totalSessions.toString()
        binding.activitiesCountText.text = "$activitiesWithData/$totalActivities"

        // Calculate average time per session
        val avgTimePerSession = if (totalSessions > 0) totalTime / totalSessions else 0
        binding.avgSessionText.text = formatTime(avgTimePerSession)

        // Find most active activity
        val mostActive = chartData.maxByOrNull { it.totalTime }
        mostActive?.let {
            binding.mostActiveText.text = it.activityName
        } ?: run {
            binding.mostActiveText.text = "None"
        }
    }

    private fun loadTodayData() {
        coroutineScope.launch {
            try {
                showLoading(true)
                val activities = repository.getAllActivities()
                val todayData = mutableListOf<ChartData>()
                var totalTodayTime = 0L

                for (activity in activities) {
                    val todayInstances = repository.getTodayActivityInstances(activity.id)
                    val todayTime = todayInstances.sumOf { it.duration }

                    if (todayTime > 0) {
                        totalTodayTime += todayTime
                        todayData.add(
                            ChartData(
                                activityName = activity.name,
                                totalTime = todayTime,
                                sessionCount = todayInstances.size,
                                todayTime = todayTime,
                                allInstances = todayInstances
                            )
                        )
                    }
                }

                if (todayData.isNotEmpty()) {
                    createPieChart(todayData, activities)
                    showSnackbar("Showing today's data: ${formatTime(totalTodayTime)}")
                    updateFilterButtonStates("today")
                } else {
                    showSnackbar("No activity data for today")
                    updateEmptyState(true)
                }

            } catch (e: Exception) {
                showError("Failed to load today's data: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun loadWeekData() {
        coroutineScope.launch {
            try {
                showLoading(true)
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                val startOfWeek = calendar.timeInMillis
                val endOfWeek = System.currentTimeMillis()

                val activities = repository.getAllActivities()
                val weekData = mutableListOf<ChartData>()
                var totalWeekTime = 0L

                for (activity in activities) {
                    val weekInstances = repository.getActivityInstancesInRange(
                        activity.id, startOfWeek, endOfWeek
                    )
                    val weekTime = weekInstances.sumOf { it.duration }

                    if (weekTime > 0) {
                        totalWeekTime += weekTime
                        weekData.add(
                            ChartData(
                                activityName = activity.name,
                                totalTime = weekTime,
                                sessionCount = weekInstances.size,
                                todayTime = 0L,
                                allInstances = weekInstances
                            )
                        )
                    }
                }

                if (weekData.isNotEmpty()) {
                    createPieChart(weekData, activities)
                    showSnackbar("Showing weekly data: ${formatTime(totalWeekTime)}")
                    updateFilterButtonStates("week")
                } else {
                    showSnackbar("No activity data for this week")
                    updateEmptyState(true)
                }

            } catch (e: Exception) {
                showError("Failed to load weekly data: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun loadMonthData() {
        coroutineScope.launch {
            try {
                showLoading(true)
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, -1)
                val startOfMonth = calendar.timeInMillis
                val endOfMonth = System.currentTimeMillis()

                val activities = repository.getAllActivities()
                val monthData = mutableListOf<ChartData>()
                var totalMonthTime = 0L

                for (activity in activities) {
                    val monthInstances = repository.getActivityInstancesInRange(
                        activity.id, startOfMonth, endOfMonth
                    )
                    val monthTime = monthInstances.sumOf { it.duration }

                    if (monthTime > 0) {
                        totalMonthTime += monthTime
                        monthData.add(
                            ChartData(
                                activityName = activity.name,
                                totalTime = monthTime,
                                sessionCount = monthInstances.size,
                                todayTime = 0L,
                                allInstances = monthInstances
                            )
                        )
                    }
                }

                if (monthData.isNotEmpty()) {
                    createPieChart(monthData, activities)
                    showSnackbar("Showing monthly data: ${formatTime(totalMonthTime)}")
                    updateFilterButtonStates("month")
                } else {
                    showSnackbar("No activity data for this month")
                    updateEmptyState(true)
                }

            } catch (e: Exception) {
                showError("Failed to load monthly data: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateFilterButtonStates(activeFilter: String) {
        // Reset all buttons
        binding.filterTodayButton.isSelected = false
        binding.filterWeekButton.isSelected = false
        binding.filterMonthButton.isSelected = false

        // Set active button
        when (activeFilter) {
            "today" -> binding.filterTodayButton.isSelected = true
            "week" -> binding.filterWeekButton.isSelected = true
            "month" -> binding.filterMonthButton.isSelected = true
        }

        updateButtonAppearance()
    }

    private fun updateButtonAppearance() {
        val selectedColor = Color.parseColor("#6200EE")
        val defaultColor = Color.parseColor("#666666")

        binding.filterTodayButton.setTextColor(if (binding.filterTodayButton.isSelected) Color.WHITE else defaultColor)
        binding.filterWeekButton.setTextColor(if (binding.filterWeekButton.isSelected) Color.WHITE else defaultColor)
        binding.filterMonthButton.setTextColor(if (binding.filterMonthButton.isSelected) Color.WHITE else defaultColor)

        binding.filterTodayButton.setBackgroundColor(if (binding.filterTodayButton.isSelected) selectedColor else Color.WHITE)
        binding.filterWeekButton.setBackgroundColor(if (binding.filterWeekButton.isSelected) selectedColor else Color.WHITE)
        binding.filterMonthButton.setBackgroundColor(if (binding.filterMonthButton.isSelected) selectedColor else Color.WHITE)
    }

    private fun formatTime(seconds: Long): String {
        val hours = TimeUnit.SECONDS.toHours(seconds)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds - TimeUnit.HOURS.toSeconds(hours))
        val remainingSeconds = seconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes)

        return if (hours > 0) {
            String.format("%dh %02dm %02ds", hours, minutes, remainingSeconds)
        } else if (minutes > 0) {
            String.format("%dm %02ds", minutes, remainingSeconds)
        } else {
            String.format("%ds", remainingSeconds)
        }
    }

    private fun formatTimeShort(seconds: Long): String {
        val hours = TimeUnit.SECONDS.toHours(seconds)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds - TimeUnit.HOURS.toSeconds(hours))

        return if (hours > 0) {
            String.format("%dh", hours)
        } else if (minutes > 0) {
            String.format("%dm", minutes)
        } else {
            String.format("%ds", seconds)
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.emptyState.visibility = View.GONE
            pieChart.visibility = View.GONE
            binding.statsContainer.visibility = View.GONE
        } else {
            pieChart.visibility = View.VISIBLE
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyState.visibility = View.VISIBLE
            pieChart.visibility = View.GONE
            binding.statsContainer.visibility = View.GONE
            binding.filterButtonsContainer.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            pieChart.visibility = View.VISIBLE
            binding.statsContainer.visibility = View.VISIBLE
            binding.filterButtonsContainer.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        Log.e(TAG, "Error: $message")
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Fragment resumed")
        fetchDataAndCreatePieChart()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Fragment destroyed")
        _binding = null
    }
}
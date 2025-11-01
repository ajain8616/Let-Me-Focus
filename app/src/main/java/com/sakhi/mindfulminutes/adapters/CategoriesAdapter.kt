package com.sakhi.mindfulminutes.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.model.Activity
import com.sakhi.mindfulminutes.repository.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CategoriesAdapter(
    private val context: Context,
    private var activityList: MutableList<Activity>,
    private val onItemClick: (Activity) -> Unit = {}
) : RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {

    private val repository = ActivityRepository()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var currentFilter: String? = null

    fun updateData(newList: List<Activity>, filter: String?) {
        activityList.clear()
        activityList.addAll(newList)
        currentFilter = filter
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activities_details, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val activity = activityList[position]
        holder.bind(activity)

        holder.itemView.setOnClickListener {
            onItemClick(activity)
        }

        // Set up long click listener for additional actions
        holder.setOnLongClickListener(activity) { clickedActivity ->
            // Handle long click - you can show a context menu or dialog
            true
        }
    }

    override fun getItemCount(): Int = activityList.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val activityNameTextView: TextView = itemView.findViewById(R.id.activityNameTextView)
        private val creationTimeTextView: TextView = itemView.findViewById(R.id.creationTimeTextView)
        private val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
        private val totalSpentTimeTextView: TextView = itemView.findViewById(R.id.totalSpentTimeTextView)
        private val sessionsTextView: TextView = itemView.findViewById(R.id.sessionsTextView)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        private val lastSessionTextView: TextView = itemView.findViewById(R.id.lastSessionTextView)
        private val averageTimeTextView: TextView = itemView.findViewById(R.id.averageTimeTextView)
        private val todayTimeTextView: TextView = itemView.findViewById(R.id.todayTimeTextView)

        fun bind(activity: Activity) {
            // Set basic activity information
            activityNameTextView.text = activity.name
            creationTimeTextView.text = formatDate(Date(activity.createdAt))
            statusTextView.text = activity.status.replaceFirstChar { it.uppercase() }

            // Set status color and indicator
            setStatusStyle(activity.status, statusTextView, statusIndicator)

            // Load real-time data from repository
            loadRealTimeData(activity.id)
        }

        private fun loadRealTimeData(activityId: String) {
            coroutineScope.launch {
                try {
                    // 1. Get total activity time using repository
                    val totalTime = repository.getTotalActivityTime(activityId)
                    totalSpentTimeTextView.text = formatTime(totalTime)

                    // 2. Get session count using repository
                    val sessionCount = repository.getActivitySessionCount(activityId)
                    sessionsTextView.text = sessionCount.toString()

                    // 3. Get last activity instance
                    val lastInstance = repository.getLastActivityInstance(activityId)
                    lastSessionTextView.text = if (lastInstance != null) {
                        formatTime(lastInstance.duration)
                    } else {
                        "00:00:00"
                    }

                    // 4. Calculate and display average time
                    val averageTime = if (sessionCount > 0) totalTime / sessionCount else 0
                    averageTimeTextView.text = formatTime(averageTime)

                    // 5. Get today's activity instances
                    val todayInstances = repository.getTodayActivityInstances(activityId)
                    val todayTotalTime = todayInstances.sumOf { it.duration }
                    todayTimeTextView.text = formatTime(todayTotalTime)

                    // 6. Get activity with detailed stats for any additional updates
                    val activityWithStats = repository.getActivityWithStats(activityId)
                    activityWithStats?.let {
                        // Update any additional fields if needed
                        updateActivityDetails(it)
                    }

                } catch (e: Exception) {
                    // Handle error by setting placeholder data
                    setPlaceholderData()
                }
            }
        }

        private fun updateActivityDetails(activity: Activity) {
            // Update any activity-specific details if needed
            // This can be used for additional fields from getActivityWithStats
        }

        private fun setPlaceholderData() {
            totalSpentTimeTextView.text = "00:00:00"
            sessionsTextView.text = "0"
            lastSessionTextView.text = "00:00:00"
            averageTimeTextView.text = "00:00:00"
            todayTimeTextView.text = "00:00:00"
        }

        private fun setStatusStyle(status: String, statusView: TextView, indicator: View) {
            val (textColor, backgroundRes) = when (status.lowercase()) {
                "active" -> Pair(R.color.success_dark, R.drawable.status_indicator_active)
                "inactive" -> Pair(R.color.error, R.drawable.status_indicator_inactive)
                "paused" -> Pair(R.color.warning_dark, R.drawable.status_indicator_paused)
                else -> Pair(R.color.textSecondary, R.drawable.status_indicator_inactive)
            }

            statusView.setTextColor(itemView.context.getColor(textColor))
            indicator.setBackgroundResource(backgroundRes)
        }

        private fun formatDate(date: Date): String {
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            return formatter.format(date)
        }

        private fun formatTime(seconds: Long): String {
            return if (seconds <= 0) {
                "00:00:00"
            } else {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60
                String.format("%02d:%02d:%02d", hours, minutes, secs)
            }
        }

        // Method to handle item long click for additional actions
        fun setOnLongClickListener(activity: Activity, onLongClick: (Activity) -> Boolean) {
            itemView.setOnLongClickListener {
                onLongClick(activity)
            }
        }
    }

    // Public methods to interact with the adapter from outside
    fun getActivityAtPosition(position: Int): Activity? {
        return if (position in 0 until activityList.size) {
            activityList[position]
        } else {
            null
        }
    }

    fun removeActivity(activityId: String) {
        val position = activityList.indexOfFirst { it.id == activityId }
        if (position != -1) {
            activityList.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun updateActivity(activityId: String, updatedActivity: Activity) {
        val position = activityList.indexOfFirst { it.id == activityId }
        if (position != -1) {
            activityList[position] = updatedActivity
            notifyItemChanged(position)
        }
    }

    fun refreshActivityData(activityId: String) {
        coroutineScope.launch {
            try {
                val activityWithStats = repository.getActivityWithStats(activityId)
                activityWithStats?.let { activity ->
                    updateActivity(activityId, activity)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun refreshAllData() {
        coroutineScope.launch {
            try {
                val allActivities = repository.getAllActivities()
                updateData(allActivities, currentFilter)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    // Method to get activities by status
    fun filterByStatus(status: String): List<Activity> {
        return activityList.filter { it.status.equals(status, ignoreCase = true) }
    }

    // Method to get total time across all activities
    fun getTotalTimeAllActivities(callback: (Long) -> Unit) {
        coroutineScope.launch {
            try {
                val totalTime = repository.getTotalTimeAllActivities()
                callback(totalTime)
            } catch (e: Exception) {
                callback(0L)
            }
        }
    }

    // Method to reset activity instances
    fun resetActivityInstances(activityId: String, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch {
            try {
                repository.resetActivityInstances(activityId)
                refreshActivityData(activityId)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    // Method to update activity status
    fun updateActivityStatus(activityId: String, newStatus: String, onComplete: (Boolean) -> Unit) {
        coroutineScope.launch {
            try {
                repository.updateActivity(activityId, mapOf("status" to newStatus))
                refreshActivityData(activityId)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    // Method to get filtered activities based on current filter
    fun getFilteredActivities(): List<Activity> {
        return when (currentFilter?.lowercase()) {
            "active" -> activityList.filter { it.status.equals("active", ignoreCase = true) }
            "inactive" -> activityList.filter { it.status.equals("inactive", ignoreCase = true) }
            else -> activityList
        }
    }

    // Method to get activities by name search
    fun filterByName(searchQuery: String): List<Activity> {
        return activityList.filter {
            it.name.contains(searchQuery, ignoreCase = true)
        }
    }

    // Method to sort activities by different criteria
    fun sortBy(sortType: String) {
        when (sortType.lowercase()) {
            "name" -> activityList.sortBy { it.name }
            "date" -> activityList.sortByDescending { it.createdAt }
            "time" -> {
                // Sort by total time (requires async operation)
                coroutineScope.launch {
                    try {
                        val activitiesWithTime = activityList.map { activity ->
                            Pair(activity, repository.getTotalActivityTime(activity.id))
                        }
                        activityList.clear()
                        activityList.addAll(activitiesWithTime.sortedByDescending { it.second }.map { it.first })
                        notifyDataSetChanged()
                    } catch (e: Exception) {
                        // Handle error
                    }
                }
            }
            "sessions" -> {
                // Sort by session count (requires async operation)
                coroutineScope.launch {
                    try {
                        val activitiesWithSessions = activityList.map { activity ->
                            Pair(activity, repository.getActivitySessionCount(activity.id))
                        }
                        activityList.clear()
                        activityList.addAll(activitiesWithSessions.sortedByDescending { it.second }.map { it.first })
                        notifyDataSetChanged()
                    } catch (e: Exception) {
                        // Handle error
                    }
                }
            }
        }
    }
}
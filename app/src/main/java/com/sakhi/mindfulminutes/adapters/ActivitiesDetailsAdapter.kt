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

class ActivitiesDetailsAdapter(
    private val context: Context,
    private var activityList: MutableList<Activity>,
    private val onItemClick: (Activity) -> Unit
) : RecyclerView.Adapter<ActivitiesDetailsAdapter.ViewHolder>() {

    private val repository = ActivityRepository()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun updateList(newList: List<Activity>) {
        activityList.clear()
        activityList.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_activities_details, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val activity = activityList[position]
        holder.bind(activity)

        holder.itemView.setOnClickListener {
            onItemClick(activity)
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

        fun bind(activity: Activity) {
            activityNameTextView.text = activity.name
            creationTimeTextView.text = formatDate(activity.creationTime)
            statusTextView.text = activity.status.replaceFirstChar { it.uppercase() }
            totalSpentTimeTextView.text = formatTime(activity.totalTime)
            sessionsTextView.text = activity.sessionCount.toString()

            // Set status color and indicator
            setStatusStyle(activity.status, statusTextView, statusIndicator)

            // Load additional data if needed
            loadAdditionalData(activity.id)
        }

        private fun loadAdditionalData(activityId: String) {
            coroutineScope.launch {
                try {
                    // You can load additional real-time data here if needed
                    // For example, current status, recent sessions, etc.
                } catch (e: Exception) {
                    // Handle error silently
                }
            }
        }

        private fun setStatusStyle(status: String, statusView: TextView, indicator: View) {
            val (textColor, backgroundRes) = when (status.lowercase()) {
                "active", "start" -> Pair(R.color.success_dark, R.drawable.status_indicator_active)
                "inactive" -> Pair(R.color.error, R.drawable.status_indicator_inactive)
                "pause", "stop" -> Pair(R.color.warning_dark, R.drawable.status_indicator_paused)
                else -> Pair(R.color.textSecondary, R.drawable.status_indicator_inactive)
            }

            statusView.setTextColor(context.getColor(textColor))
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
    }
}
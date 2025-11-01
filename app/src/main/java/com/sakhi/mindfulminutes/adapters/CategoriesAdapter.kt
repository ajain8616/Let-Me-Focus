package com.sakhi.mindfulminutes.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.model.Activity
import java.text.SimpleDateFormat
import java.util.*

class CategoriesAdapter(
    private var activityList: MutableList<Activity>,
    private val onItemClick: (Activity) -> Unit = {}
) : RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {

    private var currentFilter: String? = null

    fun updateData(newList: List<Activity>, filter: String?) {
        activityList.clear()
        activityList.addAll(newList)
        currentFilter = filter
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activities_details, parent, false) // Reuse your existing item layout
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

            setStatusStyle(activity.status, statusTextView, statusIndicator)
        }

        private fun setStatusStyle(status: String, statusView: TextView, indicator: View) {
            val (textColor, backgroundRes) = when (status.lowercase()) {
                "active", "start" -> Pair(R.color.success_dark, R.drawable.status_indicator_active)
                "inactive" -> Pair(R.color.error, R.drawable.status_indicator_inactive)
                "pause", "stop" -> Pair(R.color.warning_dark, R.drawable.status_indicator_paused)
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
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, secs)
        }

    }
}
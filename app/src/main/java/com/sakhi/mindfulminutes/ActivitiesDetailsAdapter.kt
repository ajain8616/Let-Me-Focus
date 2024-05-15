package com.sakhi.mindfulminutes

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ActivitiesDetailsAdapter(
    private val context: Context,
    activityList: MutableList<ActivityItem>
) :
    RecyclerView.Adapter<ActivitiesDetailsAdapter.ViewHolder>() {

    private lateinit var auth: FirebaseAuth
    private val activities = mutableListOf<ActivityItem>()

    init {
        setupFirebase()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(context).inflate(R.layout.item_activities_details, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val activity = activities[position]

        holder.activityNameTextView.text = activity.activityName
        holder.creationTimeTextView.text = activity.creationTime
        holder.statusTextView.text = activity.status
        holder.totalSpentTimeTextView.text = formatTime(activity.totalSpentTime)
        setStatusTextColor(activity.status, holder.statusTextView)

        holder.itemView.setOnClickListener {
            val activityName = activities[position].activityName
            val fragment = FilteredActivitiesFragment.newInstance(activityName)
            (context as AppCompatActivity).supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit()
        }
    }

    override fun getItemCount(): Int {
        return activities.size
    }

    private fun setStatusTextColor(status: String, statusTextView: TextView) {
        val color = when (status) {
            "Inactive" -> R.color.red
            "Stop" -> R.color.yellow
            "Start" -> R.color.green
            "Pause" -> R.color.colorBlue
            else -> android.R.color.black
        }
        statusTextView.setTextColor(context.resources.getColor(color))
    }

    private fun setupFirebase() {
        auth = FirebaseAuth.getInstance()
        val activityRef = FirebaseDatabase.getInstance().reference
            .child("Activities").child(auth.currentUser?.uid ?: "")
        activityRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    handleSnapshot(snapshot)
                } else {
                    // Handle no activities found
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun handleSnapshot(snapshot: DataSnapshot) {
        val updatedActivities = mutableListOf<ActivityItem>() // Create a temporary list
        for (activitySnapshot in snapshot.children) {
            val activityId = activitySnapshot.key.orEmpty()
            if (activityId.isNotEmpty()) {
                val activityData = activitySnapshot.child("activity").value.toString()
                val creationTime = activitySnapshot.child("creationTime").value.toString()
                val status = activitySnapshot.child("status").value.toString()

                val instancesSnapshot = activitySnapshot.child("instances")
                val totalSpentTimeList = mutableListOf<String>()
                for (instanceSnapshot in instancesSnapshot.children) {
                    val totalSpentTime =
                        instanceSnapshot.child("totalSpentTime").value?.toString()
                    if (!totalSpentTime.isNullOrBlank() && totalSpentTime != "NA") {
                        totalSpentTimeList.add(totalSpentTime)
                    }
                }

                val totalSpentTime = singleTotalSpentTime(totalSpentTimeList)
                val totalSpentTimeInt = calculateTotalSpentTimeInSeconds(totalSpentTime)
                updatedActivities.add(ActivityItem(activityData, creationTime, status, totalSpentTimeInt))
            }
        }
        // After processing all activities, update the original list and sort alphabetically
        activities.addAll(updatedActivities)
        activities.sortBy { it.activityName }
        notifyDataSetChanged()
    }

    private fun singleTotalSpentTime(totalSpentTime: List<String>): String {
        return calculateSumTotalSpentTime(totalSpentTime)
    }

    private fun calculateTotalSpentTimeInSeconds(totalSpentTime: String): Int {
        val timeParts = totalSpentTime.split(":")
        val hours = timeParts[0].toIntOrNull() ?: 0
        val minutes = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
        val seconds = timeParts.getOrNull(2)?.toIntOrNull() ?: 0
        return hours * 3600 + minutes * 60 + seconds
    }

    private fun calculateSumTotalSpentTime(totalSpentTimeList: List<String>): String {
        val totalSeconds = totalSpentTimeList.mapNotNull { it.toIntOrNull() }.sum()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun formatTime(totalSpentTime: Int): String {
        return if (totalSpentTime <= 0) {
            "NA"
        } else {
            val hours = totalSpentTime / 3600
            val minutes = (totalSpentTime % 3600) / 60
            val seconds = totalSpentTime % 60
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val activityNameTextView: TextView = itemView.findViewById(R.id.activityNameTextView)
        val creationTimeTextView: TextView = itemView.findViewById(R.id.creationTimeTextView)
        val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
        val totalSpentTimeTextView: TextView = itemView.findViewById(R.id.totalSpentTimeTextView)
    }
}


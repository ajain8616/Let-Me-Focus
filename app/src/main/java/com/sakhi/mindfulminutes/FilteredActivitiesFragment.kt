package com.sakhi.mindfulminutes

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.TimeUnit

class FilteredActivitiesFragment : Fragment() {

    private lateinit var intentActivityView: TextView
    private lateinit var sTimeTextView: TextView
    private lateinit var meanTimeTextView: TextView
    private lateinit var medianTimeTextView: TextView
    private lateinit var countTimeTextView: TextView
    private lateinit var totalSpentTimeTextView: TextView
    private lateinit var maximumTimeTextView: TextView
    private lateinit var minimumTimeTextView: TextView
    private lateinit var endTimeTextView: TextView
    private lateinit var pauseTimeTextView: TextView
    private lateinit var auth: FirebaseAuth
    private var activityName: String? = null

    companion object {
        fun newInstance(activityName: String): FilteredActivitiesFragment {
            val fragment = FilteredActivitiesFragment()
            val args = Bundle()
            args.putString("activityName", activityName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment

        auth = FirebaseAuth.getInstance()
        val view = inflater.inflate(R.layout.fragment_filtered_activities, container, false)
        initializeIds(view)

        // Fetch data from Firebase
        fetchDataFromFirebase()

        return view
    }

    private fun initializeIds(view: View) {

        intentActivityView = view.findViewById(R.id.intentActivityView)
        sTimeTextView = view.findViewById(R.id.sTimeTextView)
        meanTimeTextView = view.findViewById(R.id.meanTimeTextView)
        medianTimeTextView = view.findViewById(R.id.medianTimeTextView)
        countTimeTextView = view.findViewById(R.id.countTimeTextView)
        totalSpentTimeTextView = view.findViewById(R.id.totalTimeTextView)
        maximumTimeTextView = view.findViewById(R.id.maximumTimeTextView)
        minimumTimeTextView = view.findViewById(R.id.minimumTimeTextView)
        endTimeTextView = view.findViewById(R.id.endTimeTextView)
        pauseTimeTextView = view.findViewById(R.id.pauseTimeTextView)

        // Retrieve the activity name passed as an argument
        activityName = arguments?.getString("activityName")
        activityName?.let { intentActivityView.text = it }
    }

    private fun fetchDataFromFirebase() {
        val activityRef = FirebaseDatabase.getInstance().reference.child("Activities")
            .child(auth.currentUser?.uid ?: "")

        activityRef.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val startTimeList = mutableListOf<String>()
                    val endTimeList = mutableListOf<String>()
                    var pauseTime: String? = null // Initialize pauseTime variable

                    for (activitySnapshot in snapshot.children) {
                        val activityNameSnapshot =
                            activitySnapshot.child("activity").value.toString()
                        val activityID = activitySnapshot.key

                        if (activityNameSnapshot == activityName) {
                            pauseTime = activitySnapshot.child("pauseTime").value.toString()

                            val instancesSnapshot =
                                activitySnapshot.child("instances")

                            for (instanceSnapshot in instancesSnapshot.children) {
                                val startTime =
                                    instanceSnapshot.child("startTime").value.toString()
                                val endTime =
                                    instanceSnapshot.child("stopTime").value.toString()

                                // Add startTime and endTime to lists
                                startTimeList.add(startTime)
                                endTimeList.add(endTime)
                            }
                            findMinMaxTimes(startTimeList, endTimeList)
                            calculateTotalSpentTime(activityID, instancesSnapshot)
                            // Pass instancesSnapshot to calculatePauseTimes
                            pauseTime?.let { calculatePauseTimes(it) }
                        }
                    }
                }
            }


            override fun onCancelled(error: DatabaseError) {
                // Handle onCancelled
            }
        })
    }


    private fun findMinMaxTimes(startTimeList: List<String>, endTimeList: List<String>) {
        // Find maximum time from endTimeList
        val maxEndTime = endTimeList.maxOrNull()

        // Find minimum time from startTimeList
        val minStartTime = startTimeList.minOrNull()

        // Update TextViews with maximum and minimum times if they exist, else show "NA"
        sTimeTextView.text = minStartTime ?: "NA"
        endTimeTextView.text = maxEndTime ?: "NA"
    }

    private fun calculateTotalSpentTime(activityID: String?, instancesSnapshot: DataSnapshot) {
        var totalSpentTime = 0
        for (instanceSnapshot in instancesSnapshot.children) {
            val instanceTime = instanceSnapshot.child("totalSpentTime").value as Long
            totalSpentTime += instanceTime.toInt() // Accumulate total spent time
        }
        totalSpentTimeTextView.text = if (totalSpentTime > 0) formatTime(totalSpentTime) else "NA"
        calculateMeanTime(totalSpentTime, instancesSnapshot)
        calculateMedianTime(totalSpentTime, instancesSnapshot)
        calculateCountTime(instancesSnapshot)
        calculateMaximumTime(instancesSnapshot)
        calculateMinimumTime(instancesSnapshot)
    }

    private fun calculateMeanTime(totalSpentTime: Int, instancesSnapshot: DataSnapshot) {
        val instanceCount = instancesSnapshot.children.count()
        val meanTime = if (instanceCount > 0) totalSpentTime.toDouble() / instanceCount else 0.0
        meanTimeTextView.text = if (meanTime > 0) formatTime(meanTime.toInt()) else "NA"
    }

    private fun calculateMedianTime(totalSpentTime: Int, instancesSnapshot: DataSnapshot) {
        val instanceTimes = mutableListOf<Long>()
        for (instanceSnapshot in instancesSnapshot.children) {
            val instanceTime = instanceSnapshot.child("totalSpentTime").value as Long
            instanceTimes.add(instanceTime)
        }
        instanceTimes.sort()
        val medianTime = if (instanceTimes.isNotEmpty()) {
            val middle = instanceTimes.size / 2
            if (instanceTimes.size % 2 == 1) {
                instanceTimes[middle]
            } else {
                (instanceTimes[middle - 1] + instanceTimes[middle]) / 2
            }
        } else {
            0L
        }
        medianTimeTextView.text = if (medianTime > 0) formatTime(medianTime.toInt()) else "NA"
    }

    private fun calculateCountTime(instancesSnapshot: DataSnapshot) {
        val instanceCount = instancesSnapshot.children.count()
        countTimeTextView.text = instanceCount.toString()
    }

    private fun calculateMaximumTime(instancesSnapshot: DataSnapshot) {
        var maxTime = Long.MIN_VALUE
        for (instanceSnapshot in instancesSnapshot.children) {
            val instanceTime = instanceSnapshot.child("totalSpentTime").value as Long
            if (instanceTime > maxTime) {
                maxTime = instanceTime
            }
        }
        maximumTimeTextView.text = if (maxTime != Long.MIN_VALUE) formatTime(maxTime.toInt()) else "NA"
    }

    private fun calculateMinimumTime(instancesSnapshot: DataSnapshot) {
        var minTime = Long.MAX_VALUE // Initialize minTime to maximum possible value

        for (instanceSnapshot in instancesSnapshot.children) {
            val instanceTime = instanceSnapshot.child("totalSpentTime").value as Long
            if (instanceTime < minTime) {
                minTime = instanceTime
            }
        }

        // Update the TextView with the minimum time if it exists, else show "NA"
        minimumTimeTextView.text = if (minTime != Long.MAX_VALUE) formatTime(minTime.toInt()) else "NA"
    }

    private fun calculatePauseTimes(pauseTime: String) {
        // Update TextView
        pauseTimeTextView.text = pauseTime
    }




    private fun formatTime(seconds: Int): String {
        val hours = TimeUnit.SECONDS.toHours(seconds.toLong())
        val minutes = TimeUnit.SECONDS.toMinutes(seconds.toLong() - TimeUnit.HOURS.toSeconds(hours))
        val remainingSeconds = seconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
    }
}

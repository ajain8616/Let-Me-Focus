package com.sakhi.mindfulminutes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.anychart.AnyChart
import com.anychart.AnyChartView
import com.anychart.chart.common.dataentry.DataEntry
import com.anychart.chart.common.dataentry.ValueDataEntry
import com.anychart.enums.Align
import com.anychart.enums.LegendLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class PieChartFragment : Fragment() {

    private lateinit var pieChartView: AnyChartView
    private lateinit var auth: FirebaseAuth


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_pie_chart, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        fetchDataAndCreatePieChart()
    }


    private fun fetchDataAndCreatePieChart() {
        auth = Firebase.auth
        val activityRef = FirebaseDatabase.getInstance().reference.child("Activities")
            .child(auth.currentUser?.uid ?: "")

        activityRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val dataEntries = mutableListOf<DataEntry>()
                    for (activitySnapshot in snapshot.children) {
                        val activityData = activitySnapshot.child("activity").value.toString()
                        val status = activitySnapshot.child("status").value.toString()
                        val instancesSnapshot = activitySnapshot.child("instances")
                        var totalSpentTime = 0
                        for (instanceSnapshot in instancesSnapshot.children) {
                            val instanceTime =
                                instanceSnapshot.child("totalSpentTime").value as Long
                            totalSpentTime += instanceTime.toInt() // Accumulate total spent time
                        }
                        val formattedTime = formatTime(totalSpentTime)
                        dataEntries.add(
                            ValueDataEntry(
                                "$activityData=>$status ($formattedTime)",
                                totalSpentTime.toLong() // Converted to Long
                            )
                        )
                    }
                    createPieChart(dataEntries)
                } else {
                    // Handle no activities found
                    // For example, show a message to the user
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error, for example, log or display an error message
            }
        })
    }

    private fun createPieChart(data: MutableList<DataEntry>) {
        val pie = AnyChart.pie()
        pie.animation(true) // Enable animation
        pie.padding(5.0, 10.0, 5.0, 10.0) // Smaller padding
        pie.data(data)

        // Disable data labels outside the pie chart
        pie.labels().position("outside")

        // Legend properties
        pie.legend()
            .position("center")
            .itemsLayout(LegendLayout.VERTICAL)
            .align(Align.TOP)
            .padding(0, 0, 10, 0)

        pieChartView.setChart(pie)
    }


    private fun initializeViews(view: View) {
        pieChartView = view.findViewById(R.id.any_chart_view)
    }

    private fun formatTime(seconds: Int): String {
        val hours = TimeUnit.SECONDS.toHours(seconds.toLong())
        val minutes = TimeUnit.SECONDS.toMinutes(seconds.toLong() - TimeUnit.HOURS.toSeconds(hours))
        val remainingSeconds = seconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
    }
}

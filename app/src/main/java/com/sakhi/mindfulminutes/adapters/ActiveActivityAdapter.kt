package com.sakhi.mindfulminutes.adapters

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.sakhi.mindfulminutes.R
import java.text.SimpleDateFormat
import java.util.*

class ActiveActivityAdapter(private var activityList: MutableList<Pair<String, String>>) :
    RecyclerView.Adapter<ActiveActivityAdapter.ActivityViewHolder>() {

    fun updateList(newList: MutableList<Pair<String, String>>) {
        activityList.clear()
        activityList.addAll(newList)
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_card_item, parent, false)
        return ActivityViewHolder(view)
    }
    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val (activityId, activityName) = activityList[position]
        holder.bind(activityId, activityName, position)
        holder.main_CardView.tag = activityId

    }
    override fun getItemCount(): Int {
        return activityList.size
    }
    inner class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val context: Context = itemView.context
        private val activityNameTextView: TextView = itemView.findViewById(R.id.activityNameTextView)
        private val closeButton: ImageButton = itemView.findViewById(R.id.closeButton)
        private val stopwatchTextView: TextView = itemView.findViewById(R.id.stopwatchTextView)

        private val backButton: ImageButton = itemView.findViewById(R.id.backButton)
        val main_CardView : RelativeLayout = itemView.findViewById(R.id.main_CardView)
        private val cardFront: LinearLayout = itemView.findViewById(R.id.card_front)
        private val cardBack: LinearLayout = itemView.findViewById(R.id.card_back)
        private val activityNameTextView1: TextView = itemView.findViewById(R.id.activityNameTextView1)
        private val backTextView1: TextView = itemView.findViewById(R.id.backTextView1)
        private val backTextView2: TextView = itemView.findViewById(R.id.backTextView2)
        private val backTextView3: TextView = itemView.findViewById(R.id.backTextView3)
        private var startTime: Long = 0
        private var stopTime: Long = 0
        private var totalSpentTime: Int = 0 // Changed to Int
        private var isTimerRunning = false
        private lateinit var activityId: String
        private lateinit var databaseReference: DatabaseReference
        private var auth = FirebaseAuth.getInstance()
        private val handler = Handler()
        private val stopwatchRunnable = object : Runnable {
            override fun run() {
                val elapsedTime = System.currentTimeMillis() - startTime
                updateStopwatchUI(elapsedTime)
                handler.postDelayed(this, 1000) // Update every second
            }
        }

        fun bind(activityId: String, activityName: String, position: Int) {
            this.activityId = activityId
            // Set activity ID and name
            activityNameTextView.text = activityName
            activityNameTextView1.text = activityName

            // Initialize Firebase
            databaseReference = FirebaseDatabase.getInstance().reference.child("Activities")
                .child(auth.currentUser?.uid ?: "")
                .child(activityId)

            // Fetch status from the database
            databaseReference.child("status").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.value.toString()
                    if (status != "Inactive") {
                        // Set onClickListener for the close button
                        closeButton.setOnClickListener {
                            // Show confirmation dialog
                            showConfirmationDialog(activityName, position, activityId)
                        }
                    } else {
                        // If status is "Inactive", do not set the OnClickListener for closeButton
                        closeButton.setOnClickListener(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                    showToast("Failed to fetch status: ${error.message}")
                }
            })


            // Fetch status from the database
            databaseReference.child("status").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.value.toString()
                    if (status != "Inactive") {
                        // Set onClickListener for the card front
                        cardFront.setOnClickListener {
                            val dialogMessage = if (isTimerRunning) {
                                "Are you sure you wish to stop the timing for $activityName?"
                            } else {
                                "Are you sure you wish to start the timing for $activityName?"
                            }

                            AlertDialog.Builder(context)
                                .setMessage(dialogMessage)
                                .setPositiveButton("Yes") { _, _ ->
                                    if (isTimerRunning) {
                                        stopStopwatch()
                                        stopTime = System.currentTimeMillis()
                                        calculateTotalTime(System.currentTimeMillis())
                                        updateStatus(activityId, "Stop")
                                        main_CardView.setBackgroundResource(R.color.red)
                                        Handler().postDelayed({
                                            updateStopwatchUI(0)
                                            main_CardView.setBackgroundResource(android.R.color.transparent) // Change background to clear
                                        }, 3000)
                                    } else {
                                        startTime = System.currentTimeMillis()
                                        startStopwatch()
                                        updateStatus(activityId, "Start")
                                        stopwatchTextView.text = getCurrentTime() // Set the current time as the start time
                                        main_CardView.setBackgroundResource(R.color.green)
                                    }
                                }
                                .setNegativeButton("No", null)
                                .show()
                        }
                    } else {
                        // If status is "Inactive", do not set the OnClickListener
                        cardFront.setOnClickListener(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                    showToast("Failed to fetch status: ${error.message}")
                }
            })


            // Set onLongClickListener for the card front
            cardFront.setOnLongClickListener {
                fetchDataFromDatabase(activityName)
                fetchInactiveActivities()
                flipCard()
                totalSpentTime()
                true
            }

            // Set onClickListener for the back button
            backButton.setOnClickListener {
                flipCard()
            }
        }

        private fun showToast(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        private fun deleteItem(position: Int) {
            if (position != RecyclerView.NO_POSITION) {
                // Remove item from the list
                activityList.removeAt(position)
                notifyItemRemoved(position)
            }
        }

        private fun updateStatus(activityId: String, status: String) {
            // Ensure that the database reference is not null
            val databaseReference = FirebaseDatabase.getInstance().reference.child("Activities")
                .child(auth.currentUser?.uid ?: "")
                .child(activityId)

            databaseReference.child("status").setValue(status)
                .addOnSuccessListener {
                    showToast("$status status is updated successfully")
                }
                .addOnFailureListener { e ->
                    showToast("Failed to update status: ${e.message}")
                }
        }

        private fun fetchInactiveActivities() {
            val inactiveActivitiesReference =
                FirebaseDatabase.getInstance().reference.child("Activities")
                    .child(auth.currentUser?.uid ?: "")

            val query = inactiveActivitiesReference.orderByChild("status").equalTo("Inactive")

            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val inactiveActivityList = mutableListOf<String>()
                    for (dataSnapshot in snapshot.children) {
                        val activityName = dataSnapshot.child("activityName").value.toString()
                        inactiveActivityList.add(activityName)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                    showToast("Failed to fetch inactive activities: ${error.message}")
                }
            })
        }

        private fun fetchDataFromDatabase(activityName: String) {
            databaseReference.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val status = snapshot.child("status").value.toString()
                        val creationTime = snapshot.child("creationTime").value.toString()

                        // Update TextViews with fetched data
                        backTextView1.text = "Status: $status"
                        backTextView2.text = "Creation Time: $creationTime"
                    } else {
                        showToast("No data available")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle database error
                    Toast.makeText(
                        context,
                        "Failed to fetch data: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }

        private fun flipCard() {
            val scale = context.resources.displayMetrics.density
            val cameraDistance = 8000 * scale
            cardFront.cameraDistance = cameraDistance
            cardBack.cameraDistance = cameraDistance

            val animIn =
                AnimatorInflater.loadAnimator(context, R.animator.card_flip_in) as AnimatorSet
            val animOut =
                AnimatorInflater.loadAnimator(context, R.animator.card_flip_out) as AnimatorSet

            if (cardFront.visibility == View.VISIBLE) {
                animOut.setTarget(cardFront)
                animIn.setTarget(cardBack)
                animOut.start()
                animIn.start()
                cardFront.visibility = View.GONE
                cardBack.visibility = View.VISIBLE
            } else {
                animOut.setTarget(cardBack)
                animIn.setTarget(cardFront)
                animOut.start()
                animIn.start()
                cardBack.visibility = View.GONE
                cardFront.visibility = View.VISIBLE
            }
        }

        private fun startStopwatch() {
            isTimerRunning = true
            handler.post(stopwatchRunnable)
        }

        private fun stopStopwatch() {
            isTimerRunning = false
            handler.removeCallbacks(stopwatchRunnable)
        }

        private fun updateStopwatchUI(elapsedTime: Long) {
            val seconds = (elapsedTime / 1000) % 60
            val minutes = (elapsedTime / (1000 * 60)) % 60
            val hours = (elapsedTime / (1000 * 60 * 60))

            val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            stopwatchTextView.text = formattedTime
        }

        private fun calculateTotalTime(stopTime: Long) {
            // Calculate difference in milliseconds
            totalSpentTime = ((stopTime - startTime) / 1000).toInt() // Convert to seconds

            // Convert UTC time to Indian time
            val indianTimeFormatter =
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            indianTimeFormatter.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            val startTimeIndian = indianTimeFormatter.format(Date(startTime))
            val stopTimeIndian = indianTimeFormatter.format(Date(stopTime))

            // Get a reference to the "instances" node
            val instancesReference = FirebaseDatabase.getInstance().reference
                .child("Activities")
                .child(auth.currentUser?.uid ?: "")
                .child(activityId)
                .child("instances")

            // Generate a unique numerical ID for the instance
            instancesReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val instanceId = snapshot.childrenCount // Count of existing instances as ID
                    val instanceData = mapOf(
                        "totalSpentTime" to totalSpentTime, // Changed to Int
                        "startTime" to startTimeIndian,
                        "stopTime" to stopTimeIndian
                    )

                    // Set the value in the database under the generated numerical ID
                    instancesReference.child(instanceId.toString()).setValue(instanceData)
                        .addOnSuccessListener {
                            showToast("Instance data stored successfully")
                        }
                        .addOnFailureListener { e ->
                            showToast("Failed to store instance data: ${e.message}")
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    showToast("Failed to generate instance ID: ${error.message}")
                }
            })
        }

        private fun totalSpentTime() {
            val instancesReference = FirebaseDatabase.getInstance().reference
                .child("Activities")
                .child(auth.currentUser?.uid ?: "")
                .child(activityId)
                .child("instances")

            // Query to fetch the last instance
            instancesReference.limitToLast(1).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (instanceSnapshot in snapshot.children) {
                            // Retrieve totalSpentTime from the last instance
                            val totalSpentTimeSeconds = instanceSnapshot.child("totalSpentTime").value.toString().toIntOrNull() ?: 0

                            // Convert totalSpentTime from seconds to HH:mm:ss format
                            val hours = totalSpentTimeSeconds / 3600
                            val minutes = (totalSpentTimeSeconds % 3600) / 60
                            val seconds = totalSpentTimeSeconds % 60

                            // Display totalSpentTime in backTextView3
                            val formattedTotalSpentTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                            backTextView3.visibility = View.VISIBLE
                            backTextView3.text = "Last Spent Time of TotalTime: $formattedTotalSpentTime"
                        }
                    } else {
                        // Handle case where no data exists
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    showToast("Failed to fetch total spent time: ${error.message}")
                }
            })
        }



        private fun showConfirmationDialog(activityName: String, position: Int, activityId: String) {
            val alertDialogBuilder = AlertDialog.Builder(context)
            alertDialogBuilder.setTitle("Confirm")
            alertDialogBuilder.setMessage("Are you sure you want to delete $activityName?")

            alertDialogBuilder.setPositiveButton("Confirm") { dialog, which ->
                showToast("Item deleted: $activityName")
                deleteItem(position)
                updateStatus(activityId, "Inactive")
                if (isTimerRunning) {
                    calculateTotalTime(System.currentTimeMillis())
                }
                totalSpentTime()
                dialog.dismiss()
            }
            alertDialogBuilder.setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }

            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
        }
        private fun getCurrentTime(): String {
            val currentTime = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH)
            dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set Indian time zone
            return dateFormat.format(Date(currentTime))
        }
    }
}

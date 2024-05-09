package com.sakhi.mindfulminutes

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ActiveActivitiesFragment : Fragment() {

    // UI components
    private lateinit var itemNameEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var itemSearchEditText: EditText
    private lateinit var clearButton: ImageButton
    private lateinit var addActionButton: ImageButton
    private lateinit var searchActionButton: ImageButton
    private lateinit var activityRecyclerView: RecyclerView
    private lateinit var addItemLayout: RelativeLayout
    private lateinit var searchItemLayout: RelativeLayout
    private lateinit var activityNameView: TextView
    private lateinit var activityNameLayout: LinearLayout

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var databaseReference: DatabaseReference

    // Data
    private lateinit var activityAdapter: ActiveActivityAdapter
    private val activityList: MutableList<Pair<String, String>> = mutableListOf()
    private val inactiveActivityList: MutableList<Pair<String, String>> = mutableListOf()

    private var showInactiveActivities = false
    private var isActivityViewVisible = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_active_activities, container, false)

        // Initialize UI components
        initializeViews(view)

        // Initialize Firebase
        initializeFirebase()

        // Set listeners
        setListeners()

        // Fetch data from database
        fetchDataFromDatabase()

        // Setup EditText
        setupEditText()

        return view
    }

    override fun onPause() {
        super.onPause()
        showPauseDialog()
    }

    private fun showPauseDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Pause Activity?")
            .setMessage("Do you want to pause the activity?")
            .setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                // Your logic to stop the stopwatch, update status, and push pause time to Firebase
                val pauseTime = getCurrentIndianTime() // Get current time
                updateStatusAndPushPauseTime(pauseTime) // Update status to pause and push pause time
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun initializeViews(view: View) {
        itemNameEditText = view.findViewById(R.id.itemName)
        sendButton = view.findViewById(R.id.sendButton)
        itemSearchEditText = view.findViewById(R.id.itemSearch)
        clearButton = view.findViewById(R.id.clearButton)
        addActionButton = view.findViewById(R.id.addActionButton)
        searchActionButton = view.findViewById(R.id.searchActionButton)
        activityRecyclerView = view.findViewById(R.id.activityRecyclerView)
        addItemLayout = view.findViewById(R.id.addItemLayout)
        searchItemLayout = view.findViewById(R.id.searchItemLayout)
        activityNameView = view.findViewById(R.id.activityNameView)
        activityNameLayout = view.findViewById(R.id.activityNameLayout)

        // RecyclerView setup
        activityRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        activityAdapter = ActiveActivityAdapter(activityList)
        activityRecyclerView.adapter = activityAdapter
    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().reference.child("Activities")
            .child(auth.currentUser?.uid ?: "")
    }

    private fun arrangeActivityList() {
        activityList.sortBy { it.second.toLowerCase() }
    }

    private fun clearTextShowList() {
        if (itemSearchEditText.text.isEmpty()) {
            fetchDataFromDatabase()
        }
    }

    private fun setListeners() {
        addActionButton.setOnClickListener {
            toggleAddItemLayoutVisibility()
            if (activityList.isEmpty()) {
                toggleActivityNameViewVisibility()
            }
        }
        searchActionButton.setOnClickListener { toggleSearchItemLayoutVisibility() }
        clearButton.setOnClickListener {
            itemSearchEditText.text.clear()
            fetchDataFromDatabase()
        }

        sendButton.setOnClickListener {
            val activityName = itemNameEditText.text.toString().trim()
            if (activityName.isNotEmpty()) {
                duplicateActivities(activityName) { canAddActivity ->
                    if (canAddActivity) {
                        saveActivityToDatabase()
                        // Hide addItemLayout
                        addItemLayout.visibility = View.GONE
                        // Hide keyboard
                        val inputMethodManager =
                            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        inputMethodManager.hideSoftInputFromWindow(
                            itemNameEditText.windowToken,
                            0
                        )
                    } else {
                        Toast.makeText(
                            context,
                            "Cannot add activity with the same name!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(context, "Please enter activity name!", Toast.LENGTH_SHORT).show()
            }
        }

        // TextWatcher for itemSearchEditText
        itemSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Call searchActivity() with the entered text
                searchActivity(s.toString())

                // Check if the new text is empty and call clearTextShowList() if true
                if (s.isNullOrEmpty()) {
                    clearTextShowList()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

    }

    private fun toggleAddItemLayoutVisibility() {
        addItemLayout.visibility = when (addItemLayout.visibility) {
            View.VISIBLE -> View.GONE
            else -> View.VISIBLE
        }
        searchItemLayout.visibility = View.GONE
    }

    private fun toggleSearchItemLayoutVisibility() {
        val searchItemVisibility =
            if (searchItemLayout.visibility == View.GONE) View.VISIBLE else View.GONE
        searchItemLayout.visibility = searchItemVisibility
        addItemLayout.visibility = View.GONE
    }

    private fun toggleActivityNameViewVisibility() {
        activityNameView.visibility = if (isActivityViewVisible) View.GONE else View.VISIBLE
        isActivityViewVisible = !isActivityViewVisible
    }

    private fun saveActivityToDatabase() {
        val activityName = itemNameEditText.text.toString().trim()
        val creationTime = getCurrentIndianTime()
        val status = "Active"

        if (activityName.isNotEmpty()) {
            // Check if the number of activities is less than 12 or if any activity is inactive
            if (activityList.size < 12 || activityList.any { it.second == "Inactive" }) {
                val activityRef = databaseReference

                // Retrieve current activities from the database
                activityRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val newActivityData = hashMapOf(
                            "activity" to activityName,
                            "creationTime" to creationTime,
                            "status" to status
                        )

                        // Find the correct position to insert the new activity
                        var insertIndex = 0
                        for (childSnapshot in snapshot.children) {
                            val childActivityName = childSnapshot.child("activity").value.toString()
                            if (activityName.compareTo(childActivityName) < 0) {
                                break
                            }
                            insertIndex++
                        }

                        // Push the new activity to the correct position
                        activityRef.child(activityRef.push().key ?: "").setValue(newActivityData)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    context,
                                    "Activity saved successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                itemNameEditText.text.clear()
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    context,
                                    "Failed to save activity!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(
                            context,
                            "Failed to retrieve activities: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            } else {
                Toast.makeText(context, "Cannot add more than 12 activities!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Please enter activity name!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentIndianTime(): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.ENGLISH)
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set Indian time zone
        return dateFormat.format(Date())
    }

    private fun fetchDataFromDatabase() {
        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                activityList.clear() // Clear the previous list before adding new data
                for (postSnapshot in snapshot.children) {
                    val activityId = postSnapshot.key ?: ""
                    val activityName = postSnapshot.child("activity").value.toString()
                    val status = postSnapshot.child("status").value.toString()

                    // Include activities with all statuses except "Inactive" if not showing inactive activities
                    if (!showInactiveActivities && status != "Inactive") {
                        activityList.add(activityId to activityName)
                    }
                }

                // Arrange the activity list alphabetically
                arrangeActivityList()

                activityAdapter.notifyDataSetChanged()

                if (activityList.isEmpty()) {
                    // If no activities are found, show the alternative message
                    activityNameView.visibility = View.VISIBLE
                    val capitalizedAppName = getString(R.string.app_name).toUpperCase()
                    activityNameView.text = "WELCOME TO $capitalizedAppName" +
                            "\n• There are no activities." +
                            "\n• You can add activities by clicking on Add Button." +
                            "\n• To search for activities, click on the Search  Button." +
                            "\n• You can clear the search by clicking on the Clear Button."
                    activityNameView.gravity = Gravity.CENTER
                    activityNameView.setPadding(16, 16, 16, 16) // Set padding

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(16, 16, 16, 16) // Set margins
                    activityNameView.layoutParams = params

                } else {
                    // If activities are found, show the default message
                    activityNameView.visibility = View.VISIBLE
                    activityNameView.text = "Active Activities"
                }

            }

            override fun onCancelled(error: DatabaseError) {
                // Handle database error
                Toast.makeText(context, "Failed to fetch activities: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun setupEditText() {
        // Editor action listener for itemNameEditText
        itemNameEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == KeyEvent.KEYCODE_ENTER || event.action == KeyEvent.ACTION_DOWN) {
                val activityName = itemNameEditText.text.toString().trim()
                if (activityName.isNotEmpty()) {
                    duplicateActivities(activityName) { canAddActivity ->
                        if (canAddActivity) {
                            saveActivityToDatabase()
                            fetchDataFromDatabase()
                        } else {
                            Toast.makeText(
                                context,
                                "Cannot add activity with the same name!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Please enter activity name!", Toast.LENGTH_SHORT).show()
                }
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        // TextWatcher for itemSearchEditText
        itemSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Call searchActivity() with the entered text
                searchActivity(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun searchActivity(query: String) {
        val filteredList = if (showInactiveActivities) {
            inactiveActivityList.filter { it.second.contains(query, true) }
        } else {
            activityList.filter { it.second.contains(query, true) }
        }
        activityAdapter.updateList(filteredList.toMutableList())
    }

    private fun duplicateActivities(activityName: String, callback: (Boolean) -> Unit) {
        // Check if the activityName contains only letters and spaces, and not starting or ending with space
        val isAlphaWithSpaces = activityName.matches(Regex("^[a-zA-Z]+(?: [a-zA-Z]+)*$"))
                && !activityName.startsWith(" ")
                && !activityName.endsWith(" ")

        // Check if the activityName does not contain any numerical values or special characters
        val isNoNumericOrSpecial = activityName.matches(Regex("^[a-zA-Z ]+\$"))

        // Check if the activityName has at least one character
        val isNotEmpty = activityName.isNotEmpty()

        // Check if the activityName is not already in the database with a different case
        var isNotDuplicateCase = true // Assuming initially it's not a duplicate case
        val lowercaseActivityName = activityName.toLowerCase()
        val uppercaseActivityName = activityName.toUpperCase()
        val activityRef = databaseReference.orderByChild("activity")
            .startAt(lowercaseActivityName)
            .endAt(lowercaseActivityName + "\uf8ff")

        activityRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (postSnapshot in snapshot.children) {
                    val existingName = postSnapshot.child("activity").value.toString()
                    if (existingName == lowercaseActivityName) {
                        // If an activity with the same name already exists (case-sensitive), it's a duplicate case
                        isNotDuplicateCase = false
                        break
                    }
                    if (existingName == uppercaseActivityName) {
                        // If an activity with the same name in uppercase already exists, it's a duplicate case
                        isNotDuplicateCase = false
                        break
                    }
                }
                // After checking for duplicate case, proceed with other checks
                if (isNotEmpty && isAlphaWithSpaces && isNoNumericOrSpecial && isNotDuplicateCase) {
                    // If all conditions are met, proceed with checking for duplicates in Firebase
                    val activityRef = databaseReference.orderByChild("activity")

                    activityRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            var canAddActivity = true
                            for (postSnapshot in snapshot.children) {
                                val existingName = postSnapshot.child("activity").value.toString()
                                val status = postSnapshot.child("status").value.toString()
                                if (existingName.equals(lowercaseActivityName, ignoreCase = true) && status == "Inactive") {
                                    // If an inactive activity with the same name (case-insensitive) already exists, allow adding the new activity
                                    canAddActivity = true
                                    break
                                }
                                if (existingName.equals(lowercaseActivityName, ignoreCase = true)) {
                                    // If an active activity with the same name (case-insensitive) already exists, user cannot add it again
                                    canAddActivity = false
                                    break
                                }
                            }
                            callback(canAddActivity)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            // Handle database error
                            callback(false)
                        }
                    })
                } else {
                    // If any of the conditions fail, indicate that the activity name is invalid
                    callback(false)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle database error
                callback(false)
            }
        })
    }

    private fun updateStatusAndPushPauseTime(pauseTime: String) {
        // Check if there are any activities with status other than "Inactive"
        databaseReference.orderByChild("status").startAt("A").endAt("z").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // If there are such activities, proceed with updating their status and pause time
                    val activityRef = databaseReference.orderByChild("status").startAt("A").endAt("z")
                    activityRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            for (postSnapshot in snapshot.children) {
                                val activityId = postSnapshot.key ?: ""
                                val activityName = postSnapshot.child("activity").value.toString()
                                val creationTime = postSnapshot.child("creationTime").value.toString()
                                val status = "Pause"
                                val pauseTimeData = hashMapOf(
                                    "activity" to activityName,
                                    "creationTime" to creationTime,
                                    "pauseTime" to pauseTime,
                                    "status" to status
                                )

                                // Update the status and push the pause time data to the database
                                databaseReference.child(activityId).updateChildren(pauseTimeData as Map<String, Any>)
                                    .addOnSuccessListener {
                                        // Show toast on successful update
                                        Toast.makeText(
                                            context,
                                            "$activityName paused successfully!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .addOnFailureListener {
                                        // Show toast on failure
                                        Toast.makeText(
                                            context,
                                            "Failed to pause activity!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            // Handle database error
                            Toast.makeText(
                                context,
                                "Failed to pause activity: ${error.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    })
                } else {
                    // Show toast indicating there are no activities to pause
                    Toast.makeText(
                        context,
                        "No activities to pause!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle database error
                Toast.makeText(
                    context,
                    "Failed to check activity status: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }



}

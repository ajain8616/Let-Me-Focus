package com.sakhi.mindfulminutes

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ActiveActivitiesFragment : Fragment() {

    companion object {
        private const val TAG = "ActiveActivitiesDebug"
    }

    // UI components
    private lateinit var itemNameEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var itemSearchEditText: EditText
    private lateinit var clearButton: ImageButton
    private lateinit var fabActionButton: FloatingActionButton
    private lateinit var addActionButton: FloatingActionButton
    private lateinit var searchActionButton: FloatingActionButton
    private lateinit var activityRecyclerView: RecyclerView
    private lateinit var addItemLayout: RelativeLayout
    private lateinit var searchItemLayout: RelativeLayout
    private lateinit var activityNameView: TextView
    private lateinit var activityNameLayout: LinearLayout

    // Firebase
    private lateinit var auth: FirebaseAuth
    private var databaseReference: DatabaseReference? = null

    // Data
    private lateinit var activityAdapter: ActiveActivityAdapter
    private val activityList: MutableList<Pair<String, String>> = mutableListOf()
    private val inactiveActivityList: MutableList<Pair<String, String>> = mutableListOf()

    private var showInactiveActivities = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_active_activities, container, false)

        // 1. Initialize UI components
        initializeViews(view)

        // 2. Initialize Firebase
        initializeFirebase()

        // 3. Set listeners
        setListeners()

        // 4. Fetch data in realtime
        fetchDataFromDatabase()

        return view
    }

    private fun initializeViews(view: View) {
        itemNameEditText = view.findViewById(R.id.itemName)
        sendButton = view.findViewById(R.id.sendButton)
        itemSearchEditText = view.findViewById(R.id.itemSearch)
        clearButton = view.findViewById(R.id.clearButton)
        fabActionButton = view.findViewById(R.id.fabActionButton)
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
        val currentUserId = auth.currentUser?.uid

        println("[$TAG] Current User UID: $currentUserId")
        Log.d(TAG, "Current User UID: $currentUserId")

        if (!currentUserId.isNullOrEmpty()) {
            databaseReference = FirebaseDatabase.getInstance().reference
                .child("Activities")
                .child(currentUserId)
        } else {
            Log.e(TAG, "Firebase Auth: User is NOT logged in!")
            Toast.makeText(context, "User not authenticated. Please log in.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setListeners() {
        // FAB toggle listeners
        fabActionButton.setOnClickListener { toggleButtonsVisibility() }
        addActionButton.setOnClickListener { toggleAddItemLayoutVisibility() }
        searchActionButton.setOnClickListener { toggleSearchItemLayoutVisibility() }

        // Clear search button
        clearButton.setOnClickListener {
            println("[$TAG] Clear search button clicked")
            itemSearchEditText.text.clear()
            searchActivity("")
        }

        // Send / Add Activity Button
        sendButton.setOnClickListener {
            handleSendAction()
        }

        // Soft Keyboard 'Done' / 'Enter' listener on EditText
        itemNameEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                handleSendAction()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        // Single TextWatcher for Search
        itemSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                println("[$TAG] Search Query: '$query'")
                searchActivity(query)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun handleSendAction() {
        val activityName = itemNameEditText.text.toString().trim()

        println("[$TAG] SendButton Clicked! Input Name: '$activityName'")
        Log.d(TAG, "SendButton Clicked! Input Name: '$activityName'")

        if (activityName.isEmpty()) {
            println("[$TAG] Validation Failed: Activity name is empty")
            Log.w(TAG, "Validation Failed: Activity name is empty")
            Toast.makeText(context, "Please enter activity name!", Toast.LENGTH_SHORT).show()
            return
        }

        // Check for duplicates and save
        validateAndSaveActivity(activityName)
    }

    private fun validateAndSaveActivity(activityName: String) {
        val dbRef = databaseReference
        if (dbRef == null) {
            println("[$TAG] DatabaseReference is NULL (User not logged in)")
            Log.e(TAG, "DatabaseReference is NULL (User not logged in)")
            Toast.makeText(context, "Cannot save: User session not found", Toast.LENGTH_SHORT).show()
            return
        }

        println("[$TAG] Checking duplicates for '$activityName' in Firebase...")
        Log.d(TAG, "Checking duplicates for '$activityName' in Firebase...")

        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var isDuplicate = false
                var totalActiveCount = 0

                for (child in snapshot.children) {
                    val existingName = child.child("activity").value?.toString() ?: ""
                    val status = child.child("status").value?.toString() ?: ""

                    if (status != "Inactive") {
                        totalActiveCount++
                    }

                    // Check duplicate case-insensitively
                    if (existingName.equals(activityName, ignoreCase = true) && status != "Inactive") {
                        isDuplicate = true
                        break
                    }
                }

                println("[$TAG] Validation check: ActiveCount=$totalActiveCount, isDuplicate=$isDuplicate")
                Log.d(TAG, "Validation check: ActiveCount=$totalActiveCount, isDuplicate=$isDuplicate")

                if (isDuplicate) {
                    println("[$TAG] Duplicate activity found!")
                    Log.w(TAG, "Duplicate activity found!")
                    Toast.makeText(context, "Cannot add activity with the same name!", Toast.LENGTH_SHORT).show()
                    return
                }

                if (totalActiveCount >= 12) {
                    println("[$TAG] Max limit reached (12 activities)")
                    Log.w(TAG, "Max limit reached (12 activities)")
                    Toast.makeText(context, "Cannot add more than 12 active activities!", Toast.LENGTH_SHORT).show()
                    return
                }

                // Proceed with saving
                saveToFirebase(activityName)
            }

            override fun onCancelled(error: DatabaseError) {
                println("[$TAG] Duplicate check cancelled/failed: ${error.message}")
                Log.e(TAG, "Duplicate check cancelled/failed: ${error.message}")
                Toast.makeText(context, "Database error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveToFirebase(activityName: String) {
        val dbRef = databaseReference ?: return
        val newKey = dbRef.push().key

        if (newKey == null) {
            println("[$TAG] Error: Failed to generate Firebase Push Key")
            Log.e(TAG, "Error: Failed to generate Firebase Push Key")
            Toast.makeText(context, "Error generating key for activity", Toast.LENGTH_SHORT).show()
            return
        }

        val creationTime = getCurrentIndianTime()
        val status = "Active"

        val activityData = hashMapOf(
            "activity" to activityName,
            "creationTime" to creationTime,
            "status" to status
        )

        println("[$TAG] Pushing data to path Activities/${auth.currentUser?.uid}/$newKey -> $activityData")
        Log.d(TAG, "Pushing data to path Activities/${auth.currentUser?.uid}/$newKey -> $activityData")

        dbRef.child(newKey).setValue(activityData)
            .addOnSuccessListener {
                println("[$TAG] SUCCESS: Activity '$activityName' saved to Firebase!")
                Log.d(TAG, "SUCCESS: Activity '$activityName' saved to Firebase!")
                Toast.makeText(context, "Activity saved successfully!", Toast.LENGTH_SHORT).show()
                itemNameEditText.text.clear()
                addItemLayout.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                println("[$TAG] FAILURE: Failed to save activity: ${exception.localizedMessage}")
                Log.e(TAG, "FAILURE: Failed to save activity: ${exception.localizedMessage}", exception)
                Toast.makeText(context, "Failed to save activity: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getCurrentIndianTime(): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.ENGLISH)
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return dateFormat.format(Date())
    }

    private fun fetchDataFromDatabase() {
        val dbRef = databaseReference ?: return

        println("[$TAG] Attaching realtime ValueEventListener to ${dbRef.key}...")
        Log.d(TAG, "Attaching realtime ValueEventListener to ${dbRef.key}...")

        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                activityList.clear()
                inactiveActivityList.clear()

                for (postSnapshot in snapshot.children) {
                    val activityId = postSnapshot.key ?: ""
                    val activityName = postSnapshot.child("activity").value?.toString() ?: ""
                    val status = postSnapshot.child("status").value?.toString() ?: ""

                    if (status == "Inactive") {
                        inactiveActivityList.add(activityId to activityName)
                    } else {
                        activityList.add(activityId to activityName)
                    }
                }

                // Sort alphabetically
                activityList.sortBy { it.second.lowercase(Locale.getDefault()) }

                println("[$TAG] Realtime update: Fetched ${activityList.size} active activities")
                Log.d(TAG, "Realtime update: Fetched ${activityList.size} active activities")

                activityAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                println("[$TAG] Realtime fetch cancelled: ${error.message}")
                Log.e(TAG, "Realtime fetch cancelled: ${error.message}")
                Toast.makeText(context, "Failed to fetch activities: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun searchActivity(query: String) {
        val listToFilter = if (showInactiveActivities) inactiveActivityList else activityList
        val filteredList = if (query.isEmpty()) {
            listToFilter
        } else {
            listToFilter.filter { it.second.contains(query, ignoreCase = true) }
        }
        activityAdapter.updateList(filteredList.toMutableList())
    }

    private fun toggleButtonsVisibility() {
        val visibility = if (searchActionButton.visibility == View.GONE) View.VISIBLE else View.GONE
        searchActionButton.visibility = visibility
        addActionButton.visibility = visibility
    }

    private fun toggleAddItemLayoutVisibility() {
        val addItemVisibility = if (addItemLayout.visibility == View.GONE) View.VISIBLE else View.GONE
        addItemLayout.visibility = addItemVisibility
        searchItemLayout.visibility = View.GONE
    }

    private fun toggleSearchItemLayoutVisibility() {
        val searchItemVisibility = if (searchItemLayout.visibility == View.GONE) View.VISIBLE else View.GONE
        searchItemLayout.visibility = searchItemVisibility
        addItemLayout.visibility = View.GONE
    }
}
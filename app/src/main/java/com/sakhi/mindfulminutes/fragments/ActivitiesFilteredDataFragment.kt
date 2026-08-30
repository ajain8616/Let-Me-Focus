package com.sakhi.mindfulminutes.fragments

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.adapters.CategoriesAdapter
import com.sakhi.mindfulminutes.models.CategoriesItem

class ActivitiesFilteredDataFragment : Fragment() {

    private lateinit var filterIcon: ImageView
    private lateinit var activityDetailsView: RecyclerView
    private lateinit var categoriesAdapter: CategoriesAdapter
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var databaseReference: DatabaseReference
    private var categoriesList = mutableListOf<CategoriesItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_activities_filtered_data, container, false)

        filterIcon = view.findViewById(R.id.filterIcon)
        activityDetailsView = view.findViewById(R.id.activityDetailsView)
        categoriesAdapter = CategoriesAdapter(categoriesList)

        activityDetailsView.layoutManager = LinearLayoutManager(requireContext())
        activityDetailsView.adapter = categoriesAdapter

        setupFirebase()
        fetchDataFromDatabase()

        filterIcon.setOnClickListener {
            categoryBottomSheet()
        }

        return view
    }

    private fun setupFirebase() {
        firebaseAuth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().reference
            .child("Activities").child(firebaseAuth.currentUser?.uid ?: "")
    }

    private fun fetchDataFromDatabase(filter: String? = null) {
        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                categoriesList.clear()
                for (activitySnapshot in snapshot.children) {
                    val activityId = activitySnapshot.key.orEmpty()
                    val activityName = activitySnapshot.child("activity").value.toString()
                    val creationTime = activitySnapshot.child("creationTime").value.toString()
                    val status = activitySnapshot.child("status").value.toString()

                    val totalSpentTime = activitySnapshot.child("instances")
                        .children.sumOf {
                            it.child("totalSpentTime").value.toString().toIntOrNull() ?: 0
                        }.toString()

                    // Check if the status matches the filter
                    if (filter == null || filter.equals(status, ignoreCase = true)) {
                        categoriesList.add(
                            CategoriesItem(activityId, activityName, creationTime, status, totalSpentTime)
                        )
                    }
                }
                categoriesAdapter.updateData(categoriesList, filter)
            }

            override fun onCancelled(error: DatabaseError) {
                showBottomSheet("Error fetching data", true)
            }
        })
    }

    private fun categoryBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_categories, null)

        val crossIcon = sheetView.findViewById<ImageView>(R.id.crossIcon)
        val radioGroupCategories = sheetView.findViewById<RadioGroup>(R.id.radioGroupCategories)
        val radioButtonCompleteList = sheetView.findViewById<RadioButton>(R.id.radioButtonCompleteList)
        val fetchDataButton = sheetView.findViewById<View>(R.id.fetchDataButton)
        val progressBar = sheetView.findViewById<ProgressBar>(R.id.progressBar)

        crossIcon.setOnClickListener { bottomSheetDialog.dismiss() }

        fetchDataButton.setOnClickListener {
            // Show the ProgressBar
            progressBar.visibility = View.VISIBLE

            // Add a delay (simulate network/database operation)
            Handler().postDelayed({
                val selectedCategoryId = radioGroupCategories.checkedRadioButtonId
                if (selectedCategoryId != -1) {
                    val selectedRadioButton = sheetView.findViewById<RadioButton>(selectedCategoryId)

                    // If the selected radio button is 'Complete List', fetch all data
                    if (selectedRadioButton == radioButtonCompleteList) {
                        fetchDataFromDatabase() // Show all data if 'Complete List' is selected
                        categoriesAdapter.updateData(categoriesList, null)
                        showBottomSheet("Showing all data", false)
                    } else {
                        val filter = selectedRadioButton.text.toString()
                        fetchDataFromDatabase(filter) // Filter data based on selected category
                        categoriesAdapter.updateData(categoriesList, filter)
                        showBottomSheet("Showing data for $filter", false)
                    }
                } else {
                    fetchDataFromDatabase() // Show all data if no filter selected
                    categoriesAdapter.updateData(categoriesList, null)
                    showBottomSheet("Showing all data", true)
                }

                // Hide the ProgressBar and dismiss the BottomSheet
                progressBar.visibility = View.GONE
                bottomSheetDialog.dismiss()

            }, 3000) // Delay for 3 seconds (you can adjust this as needed)
        }


        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }


    private fun showBottomSheet(message: String, isError: Boolean) {
        val bottomSheetFragment = if (isError) {
            ErrorBottomSheetFragment(message)
        } else {
            SuccessBottomSheetFragment(message)
        }

        bottomSheetFragment.show(requireActivity().supportFragmentManager, bottomSheetFragment.tag)
    }
}


package com.sakhi.mindfulminutes.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.adapters.CategoriesAdapter
import com.sakhi.mindfulminutes.databinding.FragmentActivitiesFilteredDataBinding
import com.sakhi.mindfulminutes.model.Activity
import com.sakhi.mindfulminutes.repository.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActivitiesFilteredDataFragment : Fragment() {

    private var _binding: FragmentActivitiesFilteredDataBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoriesAdapter: CategoriesAdapter
    private val repository = ActivityRepository()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var currentFilter: String? = null
    private var allActivities: List<Activity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivitiesFilteredDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        loadAllActivities()
    }

    private fun setupRecyclerView() {
        binding.activityDetailsView.layoutManager = LinearLayoutManager(requireContext())
        categoriesAdapter = CategoriesAdapter(requireContext(), mutableListOf()) { activity ->
            navigateToActivityDetails(activity)
        }
        binding.activityDetailsView.adapter = categoriesAdapter
    }

    private fun setupClickListeners() {
        binding.filterIcon.setOnClickListener {
            showFilterBottomSheet()
        }

        // Add refresh functionality
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadAllActivities()
        }
    }

    private fun loadAllActivities() {
        coroutineScope.launch {
            try {
                showLoading(true)
                binding.swipeRefreshLayout.isRefreshing = true

                allActivities = repository.getAllActivities()
                applyFilter(currentFilter)

            } catch (e: Exception) {
                showError("Failed to load activities: ${e.message}")
            } finally {
                showLoading(false)
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun applyFilter(filter: String?) {
        currentFilter = filter

        val filteredActivities = when {
            filter == null || filter.equals("complete list", ignoreCase = true) -> {
                allActivities
            }
            else -> {
                allActivities.filter {
                    it.status.equals(filter, ignoreCase = true)
                }
            }
        }

        categoriesAdapter.updateData(filteredActivities, filter)

        // Show filter applied message
        if (filter != null && !filter.equals("complete list", ignoreCase = true)) {
            showSnackbar("Showing ${filter.lowercase()} activities")
        } else {
            showSnackbar("Showing all activities")
        }
    }

    private fun showFilterBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_categories, null)

        // Initialize views
        val crossIcon = sheetView.findViewById<View>(R.id.crossIcon)
        val radioGroupCategories = sheetView.findViewById<RadioGroup>(R.id.radioGroupCategories)
        val fetchDataButton = sheetView.findViewById<View>(R.id.fetchDataButton)
        val progressBar = sheetView.findViewById<ProgressBar>(R.id.progressBar)

        // Clear all radio button selections first
        clearAllRadioButtons(sheetView)

        // Set current filter selection
        setCurrentFilterSelection(sheetView)

        // Handle close button
        crossIcon.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        // Setup radio button selection
        setupRadioButtonSelection(sheetView)

        // Handle apply filter button
        fetchDataButton.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            fetchDataButton.isEnabled = false

            coroutineScope.launch {
                try {
                    val selectedId = radioGroupCategories.checkedRadioButtonId
                    val selectedFilter = when (selectedId) {
                        R.id.radioButtonCompleteList -> "complete list"
                        R.id.radioButtonActive -> "active"
                        R.id.radioButtonInactive -> "inactive"
                        R.id.radioButtonPause -> "paused"
                        R.id.radioButtonStop -> "stopped"
                        else -> "complete list"
                    }

                    applyFilter(selectedFilter)
                } catch (e: Exception) {
                    showError("Failed to apply filter: ${e.message}")
                } finally {
                    progressBar.visibility = View.GONE
                    fetchDataButton.isEnabled = true
                    bottomSheetDialog.dismiss()
                }
            }
        }

        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    private fun clearAllRadioButtons(sheetView: View) {
        val radioButtons = listOf(
            R.id.radioButtonCompleteList,
            R.id.radioButtonActive,
            R.id.radioButtonInactive,
            R.id.radioButtonPause,
            R.id.radioButtonStop
        )

        radioButtons.forEach { id ->
            sheetView.findViewById<RadioButton>(id)?.isChecked = false
        }
    }

    private fun setCurrentFilterSelection(sheetView: View) {
        when (currentFilter) {
            null, "complete list" -> sheetView.findViewById<RadioButton>(R.id.radioButtonCompleteList)?.isChecked = true
            "active" -> sheetView.findViewById<RadioButton>(R.id.radioButtonActive)?.isChecked = true
            "inactive" -> sheetView.findViewById<RadioButton>(R.id.radioButtonInactive)?.isChecked = true
            "paused" -> sheetView.findViewById<RadioButton>(R.id.radioButtonPause)?.isChecked = true
            "stopped" -> sheetView.findViewById<RadioButton>(R.id.radioButtonStop)?.isChecked = true
            else -> sheetView.findViewById<RadioButton>(R.id.radioButtonCompleteList)?.isChecked = true
        }
    }

    private fun setupRadioButtonSelection(sheetView: View) {
        val cardToRadioMap = mapOf(
            R.id.cardCompleteList to R.id.radioButtonCompleteList,
            R.id.cardActive to R.id.radioButtonActive,
            R.id.cardInactive to R.id.radioButtonInactive,
            R.id.cardPause to R.id.radioButtonPause,
            R.id.cardStop to R.id.radioButtonStop
        )

        cardToRadioMap.forEach { (cardId, radioButtonId) ->
            val card = sheetView.findViewById<CardView>(cardId)
            val radioButton = sheetView.findViewById<RadioButton>(radioButtonId)

            card?.setOnClickListener {
                // Clear all radio buttons first
                clearAllRadioButtons(sheetView)
                // Select the corresponding radio button
                radioButton?.isChecked = true
            }
        }
    }

    private fun navigateToActivityDetails(activity: Activity) {
        // Create bundle to pass activity data
        val bundle = Bundle().apply {
            putString("activityId", activity.id)
            putString("activityName", activity.name)
        }

        // Navigate to activity details fragment
        val fragment = ActivitiesDetailsFragment().apply {
            arguments = bundle
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("activities_details")
            .commit()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.activityDetailsView.visibility = if (show) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (show) View.GONE else binding.emptyState.visibility
    }


    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    // Method to refresh data from outside
    fun refreshData() {
        loadAllActivities()
    }

    // Method to apply specific filter from outside
    fun applySpecificFilter(filter: String) {
        currentFilter = filter
        loadAllActivities()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when fragment becomes visible
        loadAllActivities()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
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

    // Define filter options in lowercase
    private val filterOptions = listOf(
        "complete list",
        "active",
        "inactive",
        "pause",
        "stop"
    )

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
        categoriesAdapter = CategoriesAdapter(mutableListOf()) { activity ->
            navigateToActivityDetails(activity)
        }
        binding.activityDetailsView.adapter = categoriesAdapter
    }

    private fun setupClickListeners() {
        binding.filterIcon.setOnClickListener {
            showFilterBottomSheet()
        }
    }

    private fun loadAllActivities() {
        coroutineScope.launch {
            try {
                showLoading(true)
                allActivities = repository.getAllActivities()
                applyFilter(currentFilter)
            } catch (e: Exception) {
                showError("Failed to load activities: ${e.message}")
            } finally {
                showLoading(false)
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
                    it.status?.equals(filter, ignoreCase = true) == true
                }
            }
        }

        categoriesAdapter.updateData(filteredActivities, filter)
        updateEmptyState(filteredActivities.isEmpty())

        // Show filter applied message
        if (filter != null && !filter.equals("complete list", ignoreCase = true)) {
            showSnackbar("Showing $filter activities")
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
        sheetView.findViewById<RadioButton>(R.id.radioButtonCompleteList)?.isChecked = false
        sheetView.findViewById<RadioButton>(R.id.radioButtonActive)?.isChecked = false
        sheetView.findViewById<RadioButton>(R.id.radioButtonInactive)?.isChecked = false
        sheetView.findViewById<RadioButton>(R.id.radioButtonPause)?.isChecked = false
        sheetView.findViewById<RadioButton>(R.id.radioButtonStop)?.isChecked = false

        // Set current filter selection
        when (currentFilter) {
            null, "complete list" -> sheetView.findViewById<RadioButton>(R.id.radioButtonCompleteList)?.isChecked = true
            "active" -> sheetView.findViewById<RadioButton>(R.id.radioButtonActive)?.isChecked = true
            "inactive" -> sheetView.findViewById<RadioButton>(R.id.radioButtonInactive)?.isChecked = true
            "pause" -> sheetView.findViewById<RadioButton>(R.id.radioButtonPause)?.isChecked = true
            "stop" -> sheetView.findViewById<RadioButton>(R.id.radioButtonStop)?.isChecked = true
        }

        // Handle close button
        crossIcon.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        // Call setRadioButtonSelection instead of setupCardClickListeners
        setRadioButtonSelection(sheetView, bottomSheetDialog)

        // Handle apply filter button
        fetchDataButton.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            fetchDataButton.isEnabled = false

            coroutineScope.launch {
                try {
                    val selectedId = radioGroupCategories.checkedRadioButtonId
                    val selectedFilter = when (selectedId) {
                        R.id.radioButtonCompleteList -> null
                        R.id.radioButtonActive -> "active"
                        R.id.radioButtonInactive -> "inactive"
                        R.id.radioButtonPause -> "pause"
                        R.id.radioButtonStop -> "stop"
                        else -> null
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


    private fun setRadioButtonSelection(sheetView: View, bottomSheetDialog: BottomSheetDialog) {
        val cardToRadioMap = mapOf(
            R.id.cardCompleteList to R.id.radioButtonCompleteList,
            R.id.cardActive to R.id.radioButtonActive,
            R.id.cardInactive to R.id.radioButtonInactive,
            R.id.cardPause to R.id.radioButtonPause,
            R.id.cardStop to R.id.radioButtonStop
        )

        val allRadioButtons = listOf(
            R.id.radioButtonCompleteList,
            R.id.radioButtonActive,
            R.id.radioButtonInactive,
            R.id.radioButtonPause,
            R.id.radioButtonStop
        )

        cardToRadioMap.forEach { (cardId, radioButtonId) ->
            val card = sheetView.findViewById<androidx.cardview.widget.CardView>(cardId)
            val radioButton = sheetView.findViewById<android.widget.RadioButton>(radioButtonId)

            card?.setOnClickListener {
                // -------------------- RADIO BUTTON FIX --------------------
                // Ensure only one radio button is selected at a time
                allRadioButtons.forEach { id ->
                    sheetView.findViewById<RadioButton>(id)?.isChecked = false
                }
                radioButton?.isChecked = true
            }
        }
    }

    private fun navigateToActivityDetails(activity: Activity) {
        val fragment = ActivitiesDetailsFragment() // Replace with your target fragment
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.activityDetailsView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyState.visibility = View.VISIBLE
            binding.activityDetailsView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.activityDetailsView.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

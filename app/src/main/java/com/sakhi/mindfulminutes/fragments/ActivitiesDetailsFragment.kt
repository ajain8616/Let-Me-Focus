package com.sakhi.mindfulminutes.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.adapters.ActivitiesDetailsAdapter
import com.sakhi.mindfulminutes.databinding.FragmentActivitiesDetailsBinding
import com.sakhi.mindfulminutes.model.Activity
import com.sakhi.mindfulminutes.repository.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActivitiesDetailsFragment : Fragment() {

    private var _binding: FragmentActivitiesDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ActivitiesDetailsAdapter
    private val repository = ActivityRepository()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivitiesDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupRealTimeUpdates()
        loadActivities()
    }

    private fun setupRecyclerView() {
        binding.activityDetailsView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ActivitiesDetailsAdapter(requireContext(), mutableListOf()) { activity ->
            navigateToStatistics(activity)
        }
        binding.activityDetailsView.adapter = adapter
    }

    private fun setupRealTimeUpdates() {
        // Listen for real-time updates from Firestore
        repository.listenToAllActivities { activities ->
            adapter.updateList(activities)
            updateEmptyState(activities.isEmpty())
        }
    }

    private fun loadActivities() {
        coroutineScope.launch {
            try {
                showLoading(true)
                val activities = repository.getAllActivities()
                adapter.updateList(activities)
                updateEmptyState(activities.isEmpty())
            } catch (e: Exception) {
                showError("Failed to load activities: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun navigateToStatistics(activity: Activity) {
        val fragment = StatisticsActivitiesFragment.newInstance(activity.id, activity.name)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
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

    private fun showEmptyState(show: Boolean) {
        updateEmptyState(show)
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
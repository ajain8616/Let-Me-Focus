package com.sakhi.mindfulminutes.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.sakhi.mindfulminutes.adapters.ActiveActivityAdapter
import com.sakhi.mindfulminutes.databinding.FragmentActiveActivitiesBinding
import com.sakhi.mindfulminutes.model.Activity
import com.sakhi.mindfulminutes.models.ActivityInstance
import com.sakhi.mindfulminutes.repository.ActivityRepository
import com.sakhi.mindfulminutes.services.StopwatchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ActiveActivitiesFragment : Fragment() {

    private var _binding: FragmentActiveActivitiesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ActiveActivityAdapter
    private val repository = ActivityRepository()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var stopwatchService: StopwatchService? = null
    private var isServiceBound = false
    private var allActivities: List<Activity> = emptyList()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as StopwatchService.StopwatchBinder
            stopwatchService = binder.getService()
            isServiceBound = true
            setupAdapter()
            loadActivities()
            setupServiceListener()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            stopwatchService = null
            isServiceBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActiveActivitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        setupSearchFunctionality()
        bindStopwatchService()
    }

    private fun setupRecyclerView() {
        binding.activityRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.activityRecyclerView.itemAnimator = null
        binding.activityRecyclerView.overScrollMode = View.OVER_SCROLL_NEVER

        setupRecyclerViewSpacing()
    }

    private fun setupRecyclerViewSpacing() {
        binding.activityRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                outRect.top = 0
                outRect.bottom = 0
            }
        })
    }

    private fun setupAdapter() {
        adapter = ActiveActivityAdapter(mutableListOf(), stopwatchService, ::loadActivities)
        binding.activityRecyclerView.adapter = adapter
    }

    private fun setupServiceListener() {
        stopwatchService?.addListener { time, formattedTime ->
            // Update the adapter when timer changes
            adapter.updateList(allActivities)
        }
    }

    private fun setupClickListeners() {
        binding.addActionButton.setOnClickListener {
            toggleAddLayout()
        }

        binding.searchActionButton.setOnClickListener {
            toggleSearchLayout()
        }

        binding.sendButton.setOnClickListener {
            addNewActivity()
        }

        binding.clearButton.setOnClickListener {
            hideSearchLayout()
        }

        // Add close button listener for add item layout
        binding.btnCloseAddItem.setOnClickListener {
            hideAddLayout()
        }

        binding.itemSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun setupSearchFunctionality() {
        binding.itemSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun bindStopwatchService() {
        val intent = Intent(requireContext(), StopwatchService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun loadActivities() {
        coroutineScope.launch {
            try {
                showLoading(true)
                val activities = repository.getActiveActivities()
                allActivities = activities
                adapter.updateList(activities)

                if (activities.isEmpty()) {
                    showEmptyState(true)
                } else {
                    showEmptyState(false)
                }
            } catch (e: Exception) {
                showError("Failed to load activities: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun toggleAddLayout() {
        val isVisible = binding.addItemLayout.visibility == View.VISIBLE
        if (isVisible) {
            hideAddLayout()
        } else {
            showAddLayout()
        }
    }

    private fun toggleSearchLayout() {
        val isVisible = binding.searchItemLayout.visibility == View.VISIBLE
        if (isVisible) {
            hideSearchLayout()
        } else {
            showSearchLayout()
        }
    }

    private fun showAddLayout() {
        binding.addItemLayout.visibility = View.VISIBLE
        binding.searchItemLayout.visibility = View.GONE

        binding.itemName.requestFocus()
        showKeyboard(binding.itemName)
    }

    private fun hideAddLayout() {
        binding.addItemLayout.visibility = View.GONE
        binding.itemName.text?.clear()
        hideKeyboard()
    }

    private fun showSearchLayout() {
        binding.searchItemLayout.visibility = View.VISIBLE
        binding.addItemLayout.visibility = View.GONE

        binding.itemSearch.requestFocus()
        showKeyboard(binding.itemSearch)
    }

    private fun hideSearchLayout() {
        binding.searchItemLayout.visibility = View.GONE
        binding.itemSearch.text?.clear()
        hideKeyboard()
        loadActivities() // Reload all activities when search is closed
    }

    private fun addNewActivity() {
        val activityName = binding.itemName.text.toString().trim()
        if (activityName.isEmpty()) {
            binding.itemName.error = "Please enter activity name"
            return
        }

        coroutineScope.launch {
            try {
                val activity = Activity(name = activityName, status = "active")
                val activityId = repository.addActivity(activity)
                createInitialActivityInstance(activityId)

                hideAddLayout()
                loadActivities()

                showSnackbar("Activity '$activityName' added successfully")
            } catch (e: Exception) {
                showError("Failed to add activity: ${e.message}")
            }
        }
    }

    private suspend fun createInitialActivityInstance(activityId: String) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentTime = dateFormat.format(Date())

            val initialInstance = ActivityInstance(
                id = "0",
                activityId = activityId,
                duration = 0L, // Zero duration for initial instance
                startTime = currentTime,
                stopTime = currentTime
            )

            repository.addActivityInstanceWithObject(initialInstance)
        } catch (e: Exception) {
            // Log the error but don't block activity creation
            e.printStackTrace()
        }
    }

    private fun performSearch() {
        val query = binding.itemSearch.text.toString().trim().lowercase()

        if (query.isEmpty()) {
            // Show all activities when search is empty
            adapter.updateList(allActivities)
            return
        }

        val filteredActivities = allActivities.filter { activity ->
            activity.name.lowercase().contains(query)
        }

        adapter.updateList(filteredActivities)

        // Show empty state if no results found
        if (filteredActivities.isEmpty()) {
            showEmptyState(true, "No activities found for '$query'")
        } else {
            showEmptyState(false)
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState(show: Boolean, message: String? = null) {
        if (show) {
            binding.emptyState.visibility = View.VISIBLE
            binding.activityRecyclerView.visibility = View.GONE

            // Update empty state message if provided
            message?.let {
                val emptyText = binding.emptyState.getChildAt(1) as? android.widget.TextView
                emptyText?.text = it
            }
        } else {
            binding.emptyState.visibility = View.GONE
            binding.activityRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showKeyboard(view: android.view.View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val view = requireActivity().currentFocus
        view?.let {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        loadActivities()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isServiceBound) {
            stopwatchService?.removeListener { _, _ -> }
            requireContext().unbindService(serviceConnection)
            isServiceBound = false
        }
        _binding = null
    }
}
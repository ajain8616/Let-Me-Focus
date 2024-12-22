package com.sakhi.mindfulminutes.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.adapters.ActivitiesDetailsAdapter
import com.sakhi.mindfulminutes.models.ActivityItem


class ActivitiesDetailsFragment : Fragment() {

    private lateinit var activityDetailsView: RecyclerView
    private lateinit var activityAdapter: ActivitiesDetailsAdapter
    private val activityList = mutableListOf<ActivityItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_activities_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activityDetailsView =view.findViewById(R.id.activityDetailsView)
        setupRecyclerView()

    }

    private fun setupRecyclerView() {
        activityAdapter = ActivitiesDetailsAdapter(requireContext(), activityList)
        activityDetailsView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = activityAdapter
        }
    }
}

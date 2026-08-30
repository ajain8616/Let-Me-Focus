package com.sakhi.mindfulminutes.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.models.CategoriesItem

class CategoriesAdapter(
    private var categoriesList: List<CategoriesItem>
) : RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {

    private var selectedCategory: String? = null // Variable to store selected category

    // Update data and notify the adapter
    fun updateData(newData: List<CategoriesItem>, filter: String?) {
        categoriesList = newData
        selectedCategory = filter // Store the selected category
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categoriesList[position]

        // Set the category name, total spent time, etc.
        holder.activityNameTextView.text = category.activityName
        holder.totalSpentTimeTextView.text = category.totalSpentTime
        holder.statusTextView.text = category.status

        // Set the status text color based on the status
        setStatusTextColor(category.status, holder.statusTextView)

        // Update categoryTextView with the selected filter text
        if (selectedCategory != null) {
            holder.categoryTextView.text = selectedCategory
            holder.linearLayoutCategory.visibility = View.VISIBLE // Make it visible when a filter is selected
        } else {
            holder.linearLayoutCategory.visibility = View.GONE // Hide when no filter is applied
        }
    }

    override fun getItemCount(): Int {
        return categoriesList.size
    }

    // ViewHolder class to hold the views
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val activityNameTextView: TextView = itemView.findViewById(R.id.activityNameTextView)
        val totalSpentTimeTextView: TextView = itemView.findViewById(R.id.totalSpentTimeTextView)
        val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
        val linearLayoutCategory: LinearLayout = itemView.findViewById(R.id.linearLayoutCategory)
        val categoryTextView: TextView = itemView.findViewById(R.id.categoryTextView)
    }

    // Function to set the status text color based on the activity status
    private fun setStatusTextColor(status: String, statusTextView: TextView) {
        val color = when (status) {
            "Inactive" -> R.color.red
            "Stop" -> R.color.yellow
            "Start" -> R.color.green
            "Pause" -> R.color.colorBlue
            else -> android.R.color.black
        }
        statusTextView.setTextColor(statusTextView.context.resources.getColor(color))
    }
}

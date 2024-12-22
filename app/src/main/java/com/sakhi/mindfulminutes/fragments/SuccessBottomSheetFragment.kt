package com.sakhi.mindfulminutes.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sakhi.mindfulminutes.R

class SuccessBottomSheetFragment(private val message: String) : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_success, container, false)

        // Set the success message
        val successMessageTextView = view.findViewById<TextView>(R.id.successMessage)
        successMessageTextView.text = message

        // Set up the close icon click listener
        val closeIcon = view.findViewById<ImageView>(R.id.closeIcon)
        closeIcon.setOnClickListener {
            dismiss()  // Close the bottom sheet
        }

        return view
    }
}

package com.example.activitytracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth

class SignUpFragment : Fragment() {

    private lateinit var userNameEditText: EditText
    private lateinit var userEmailEditText: EditText
    private lateinit var userPasswordEditText: EditText
    private lateinit var confirmUserPasswordEditText: EditText
    private lateinit var signUpButton: Button
    private lateinit var loginTextView: TextView
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_sign_up, container, false)

        // Initialize views
        userNameEditText = view.findViewById(R.id.userName)
        userEmailEditText = view.findViewById(R.id.userEmail)
        userPasswordEditText = view.findViewById(R.id.userPassword)
        confirmUserPasswordEditText = view.findViewById(R.id.confirmUserPassword)
        signUpButton = view.findViewById(R.id.signup_button)
        loginTextView = view.findViewById(R.id.login_textview)

        firebaseAuth = FirebaseAuth.getInstance()

        // Set click listener for sign up button
        signUpButton.setOnClickListener {
            // Handle sign up button click event
            signUp()
        }

        // Set click listener for login text view
        loginTextView.setOnClickListener {
            // Handle login text view click event
            navigateToLoginFragment()
        }

        return view
    }

    private fun signUp() {
        val userName = userNameEditText.text.toString().trim()
        val userEmail = userEmailEditText.text.toString().trim()
        val password = userPasswordEditText.text.toString().trim()
        val confirmPassword = confirmUserPasswordEditText.text.toString().trim()

        // Check if any field is empty
        if (userName.isEmpty() || userEmail.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if passwords match
        if (password != confirmPassword) {
            Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        // Create user with email and password
        firebaseAuth.createUserWithEmailAndPassword(userEmail, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Send verification email
                    firebaseAuth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->
                            if (verificationTask.isSuccessful) {
                                // Verification email sent successfully
                                Toast.makeText(requireContext(), "Verification email sent to $userEmail", Toast.LENGTH_SHORT).show()
                                // Navigate to login fragment
                                navigateToLoginFragment()
                            } else {
                                // Failed to send verification email
                                Toast.makeText(requireContext(), "Failed to send verification email", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    // Sign up failed
                    Toast.makeText(requireContext(), "Sign up failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToLoginFragment() {
        val loginFragment = LoginFragment()
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, loginFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
}

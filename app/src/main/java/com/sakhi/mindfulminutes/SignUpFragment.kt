package com.sakhi.mindfulminutes

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
    private lateinit var verification_textview:TextView
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
        verification_textview = view.findViewById(R.id.verification_textview)

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
            verification_textview.visibility = View.VISIBLE
            verification_textview.text = "Please fill in all fields for sign up and verification"
            return
        }

        // Check if passwords match
        if (password != confirmPassword) {
            verification_textview.visibility = View.VISIBLE
            verification_textview.text = "Passwords do not match. Please try again and ensure that the passwords match."
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
                                verification_textview.visibility = View.VISIBLE
                                verification_textview.text = "A verification email has been successfully sent to $userEmail. Please check your inbox and follow the instructions to verify your email address."
                                // Navigate to login fragment
                                navigateToLoginFragment()
                            } else {
                                // Failed to send verification email
                                verification_textview.visibility = View.VISIBLE
                                verification_textview.text = "Failed to send the verification link to $userEmail. Please ensure that your email address is correct and try again later."
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

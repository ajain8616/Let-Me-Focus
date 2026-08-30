package com.sakhi.mindfulminutes.fragments

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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sakhi.mindfulminutes.R

class SignUpFragment : Fragment() {

    private lateinit var userNameEditText: EditText
    private lateinit var userEmailEditText: EditText
    private lateinit var userPasswordEditText: EditText
    private lateinit var confirmUserPasswordEditText: EditText
    private lateinit var signUpButton: Button
    private lateinit var loginTextView: TextView
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sign_up, container, false)

        userNameEditText = view.findViewById(R.id.userName)
        userEmailEditText = view.findViewById(R.id.userEmail)
        userPasswordEditText = view.findViewById(R.id.userPassword)
        confirmUserPasswordEditText = view.findViewById(R.id.confirmUserPassword)
        signUpButton = view.findViewById(R.id.signup_button)
        loginTextView = view.findViewById(R.id.login_textview)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        signUpButton.setOnClickListener {
            signUp()
        }

        loginTextView.setOnClickListener {
            navigateToLoginFragment()
        }

        return view
    }

    private fun signUp() {
        val userName = userNameEditText.text.toString().trim()
        val userEmail = userEmailEditText.text.toString().trim()
        val password = userPasswordEditText.text.toString().trim()
        val confirmPassword = confirmUserPasswordEditText.text.toString().trim()

        if (userName.isEmpty() || userEmail.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showBottomSheet("Please fill in all fields for sign up and verification", isError = true)
            return
        }

        if (password != confirmPassword) {
            showBottomSheet("Passwords do not match. Please try again.", isError = true)
            return
        }

        firebaseAuth.createUserWithEmailAndPassword(userEmail, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    firebaseAuth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->
                            if (verificationTask.isSuccessful) {
                                saveUserDataToFirestore(userName, userEmail)
                                showBottomSheet("A verification email has been successfully sent to $userEmail. Please check your inbox and follow the instructions to verify your email address.", isError = false)
                                navigateToLoginFragment()
                            } else {
                                showBottomSheet("Failed to send the verification link. Please try again.", isError = true)
                            }
                        }
                } else {
                    showBottomSheet("Sign up failed: ${task.exception?.message}", isError = true)
                }
            }
    }

    private fun showBottomSheet(message: String, isError: Boolean) {
        val bottomSheetFragment = if (isError) {
            ErrorBottomSheetFragment(message)
        } else {
            SuccessBottomSheetFragment(message)
        }

        bottomSheetFragment.show(requireActivity().supportFragmentManager, bottomSheetFragment.tag)
    }

    private fun saveUserDataToFirestore(userName: String, userEmail: String) {
        val userMap = hashMapOf(
            "userName" to userName,
            "userEmail" to userEmail,
            "isVerified" to false
        )

        firestore.collection("users")
            .document(firebaseAuth.currentUser!!.uid)
            .set(userMap)
            .addOnSuccessListener {
                showBottomSheet("User data saved to Firestore",false)
            }
            .addOnFailureListener { e ->
                showBottomSheet("Failed to save user data: ${e.message}", true)
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

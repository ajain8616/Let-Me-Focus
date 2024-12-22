package com.sakhi.mindfulminutes.fragments

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.sakhi.mindfulminutes.R

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        usernameEditText = view.findViewById(R.id.useremail_edittext)
        passwordEditText = view.findViewById(R.id.password_edittext)
        val loginButton = view.findViewById<Button>(R.id.login_button)
        val forgetPasswordTextView = view.findViewById<TextView>(R.id.forget_password_textview)
        val signUpButton = view.findViewById<TextView>(R.id.signup_button)
        val googleButton = view.findViewById<com.google.android.gms.common.SignInButton>(R.id.google_button)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        // Set click listeners
        loginButton.setOnClickListener {
            checkLoginCondition()
        }

        forgetPasswordTextView.setOnClickListener {
            val email = usernameEditText.text.toString().trim()
            if (email.isNotEmpty()) {
                sendPasswordResetEmail(email)
            } else {
                showBottomSheet(
                    "Please enter your email address to reset your password",true)
            }
        }

        signUpButton.setOnClickListener {
            navigateToSignUpFragment() // Navigate to SignUpFragment when signUpButton is clicked
        }

        googleButton.setOnClickListener {
            signInWithGoogle()
        }

        return view
    }

    private fun checkLoginCondition() {
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.isEmailVerified) {
            showBottomSheet("Please verify your email address before logging in for security purposes.",true)
        } else {
            loginUser()
        }
    }

    private fun loginUser() {
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            showBottomSheet("Please enter your email and password, these fields are required to fill and login.", true)
            return
        }

        // Perform login process
        auth.signInWithEmailAndPassword(username, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Check if the user's email is verified
                    val currentUser = auth.currentUser
                    if (currentUser != null && currentUser.isEmailVerified) {
                        // Save user data to Firestore
                        saveUserDataToFirestore(currentUser.displayName ?: "Unknown", currentUser.email ?: "Unknown")

                        // Login successful and email is verified, navigate to HomeFragment
                        navigateToHomeFragment()

                        // Show success bottom sheet
                        showBottomSheet("Login successful!", false)
                    } else {
                        // Email is not verified, display error message
                       ErrorBottomSheetFragment( "Please verify your email address before logging in for security purposes.")
                        // Sign out the user as email is not verified
                        auth.signOut()
                    }
                } else {
                    // Login failed, display error message
                    Toast.makeText(requireContext(), "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    // Show error bottom sheet
                    showBottomSheet("Login failed. Please try again.", true)
                }
            }
    }

    private fun saveUserDataToFirestore(userName: String, userEmail: String) {
        val userMap = hashMapOf(
            "userName" to userName,
            "userEmail" to userEmail,
            "isVerified" to false
        )

        firestore.collection("users")
            .document(auth.currentUser!!.uid)
            .set(userMap)
            .addOnSuccessListener {
                showBottomSheet("User data saved to Firestore",false)
            }
            .addOnFailureListener { e ->
                showBottomSheet("Failed to save user data: ${e.message}", true)
            }
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showBottomSheet("Password reset email sent successfully",false)
                } else {
                    showBottomSheet("Failed to send reset email: ${task.exception?.message}", true)
                }
            }
    }

    private fun navigateToSignUpFragment() {
        val signUpFragment = SignUpFragment()
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, signUpFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun navigateToHomeFragment() {
        val homeFragment = ActiveActivitiesFragment()
        val transaction = requireActivity().supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, homeFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = data?.let { Auth.GoogleSignInApi.getSignInResultFromIntent(it) }
            if (task?.isSuccess == true) {
                val account = task.signInAccount
                firebaseAuthWithGoogle(account!!)
            } else {
                Toast.makeText(requireContext(), "Google sign in failed", Toast.LENGTH_SHORT).show()
                showBottomSheet("Google sign in failed. Please try again.", true)
            }
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Sign in success, navigate to HomeFragment
                    navigateToHomeFragment()

                    // Show success bottom sheet
                    showBottomSheet("Google sign-in successful!", false)
                } else {
                    // Sign in failed, display error message
                    Toast.makeText(requireContext(), "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    showBottomSheet("Authentication failed. Please try again.", true)
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

    companion object {
        private const val RC_SIGN_IN = 9001
    }
}

package com.sakhi.mindfulminutes.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.databinding.ActivitySignupBinding
import com.sakhi.mindfulminutes.fragments.ErrorBottomSheetFragment
import com.sakhi.mindfulminutes.fragments.SuccessBottomSheetFragment
import com.sakhi.mindfulminutes.models.UserProfile

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        setupClickListeners()
        setupTextWatchers()
    }

    private fun enableEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupClickListeners() {
        // Sign Up button
        binding.signupButton.setOnClickListener {
            validateAndSignUp()
        }

        // Navigate to LoginActivity when "Login here" is clicked
        binding.loginTextview.setOnClickListener {
            navigateToLogin()
        }

    }

    private fun setupTextWatchers() {
        binding.userEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateEmailFormat(s.toString())
            }
        })

        binding.confirmUserPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePasswordMatch()
            }
        })
    }

    private fun validateAndSignUp() {
        val name = binding.userName.text.toString().trim()
        val email = binding.userEmail.text.toString().trim()
        val password = binding.userPassword.text.toString().trim()
        val confirmPassword = binding.confirmUserPassword.text.toString().trim()

        if (!validateInputs(name, email, password, confirmPassword)) return

        showLoading(true)
        createUserWithEmailAndPassword(name, email, password)
    }

    private fun validateInputs(name: String, email: String, password: String, confirmPassword: String): Boolean {
        clearErrors()
        var isValid = true

        if (name.isEmpty()) {
            binding.userName.error = "Full name is required"
            isValid = false
        } else if (name.length < 2) {
            binding.userName.error = "Name must be at least 2 characters"
            isValid = false
        }

        if (email.isEmpty()) {
            binding.userEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.userEmail.error = "Please enter a valid email address"
            isValid = false
        }

        if (password.isEmpty()) {
            binding.userPassword.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            binding.userPassword.error = "Password must be at least 6 characters"
            isValid = false
        }

        if (confirmPassword.isEmpty()) {
            binding.confirmUserPassword.error = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            binding.confirmUserPassword.error = "Passwords do not match"
            isValid = false
        }

        return isValid
    }

    private fun validateEmailFormat(email: String) {
        binding.userEmail.error =
            if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
                "Please enter a valid email address"
            else null
    }

    private fun validatePasswordMatch() {
        val password = binding.userPassword.text.toString()
        val confirmPassword = binding.confirmUserPassword.text.toString()
        binding.confirmUserPassword.error =
            if (confirmPassword.isNotEmpty() && password != confirmPassword)
                "Passwords do not match"
            else null
    }

    private fun clearErrors() {
        binding.userName.error = null
        binding.userEmail.error = null
        binding.userPassword.error = null
        binding.confirmUserPassword.error = null
    }

    private fun createUserWithEmailAndPassword(name: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        // Send verification email immediately after user creation
                        sendVerificationEmail(user, name, email)
                    }
                } else {
                    showLoading(false)
                    showErrorBottomSheet("Sign up failed: ${task.exception?.message}")
                }
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                showErrorBottomSheet("Signup error: ${exception.message}")
            }
    }

    private fun sendVerificationEmail(user: com.google.firebase.auth.FirebaseUser, name: String, email: String) {
        user.sendEmailVerification()
            .addOnCompleteListener { verificationTask ->
                if (verificationTask.isSuccessful) {
                    // Update user profile and save to database after verification email is sent
                    updateUserProfileAndSaveToDatabase(user, name, email)
                } else {
                    showLoading(false)
                    showErrorBottomSheet("Error sending verification email: ${verificationTask.exception?.message}")
                }
            }
    }

    private fun updateUserProfileAndSaveToDatabase(user: com.google.firebase.auth.FirebaseUser, name: String, email: String) {
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { profileTask ->
                if (profileTask.isSuccessful) {
                    saveUserToDatabase(user.uid, name, email)
                } else {
                    showLoading(false)
                    showErrorBottomSheet("Profile update failed: ${profileTask.exception?.message}")
                }
            }
    }

    private fun saveUserToDatabase(uid: String, name: String, email: String) {
        val user = UserProfile(
            userId = uid,
            userName = name,
            userEmail = email,
            createdAt = System.currentTimeMillis(),
            isVerified = false,
            lastLoginAt = System.currentTimeMillis(),
            accountStatus = "pending_verification"
        )

        val usersRef = database.getReference("TradingPlatformUsers")
        usersRef.child(uid).setValue(user)
            .addOnCompleteListener { databaseTask ->
                showLoading(false)
                if (databaseTask.isSuccessful) {
                    showSuccessBottomSheet(
                        "Verification link sent to your email. Please verify your email before logging in.",
                        autoDismiss = false
                    )

                    // Navigate to login after 3 seconds
                    binding.root.postDelayed({
                        navigateToLogin()
                    }, 3000)
                } else {
                    showErrorBottomSheet("Failed to save user data: ${databaseTask.exception?.message}")
                }
            }
    }


    private fun showLoading(show: Boolean) {
        binding.signupButton.isEnabled = !show
        binding.signupButton.text = if (show) "Creating Account..." else "Create Account"
    }

    private fun showSuccessBottomSheet(message: String, autoDismiss: Boolean = true) {
        val successBottomSheet = SuccessBottomSheetFragment(message)
        successBottomSheet.show(supportFragmentManager, "SuccessBottomSheet")

        if (autoDismiss) {
            binding.root.postDelayed({
                if (successBottomSheet.isVisible) {
                    successBottomSheet.dismiss()
                }
            }, 3000)
        }
    }

    private fun showErrorBottomSheet(message: String) {
        val errorBottomSheet = ErrorBottomSheetFragment(message)
        errorBottomSheet.show(supportFragmentManager, "ErrorBottomSheet")

        // Auto dismiss error sheets after 4 seconds
        binding.root.postDelayed({
            if (errorBottomSheet.isVisible) {
                errorBottomSheet.dismiss()
            }
        }, 4000)
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    // Optional: Check if email is verified when activity resumes
    override fun onResume() {
        super.onResume()
        auth.currentUser?.let { user ->
            user.reload().addOnCompleteListener { reloadTask ->
                if (reloadTask.isSuccessful && user.isEmailVerified) {
                    showSuccessBottomSheet("Email verified successfully! You can now login.")

                    // Navigate to login after success message
                    binding.root.postDelayed({
                        navigateToLogin()
                    }, 2000)
                }
            }
        }
    }
}
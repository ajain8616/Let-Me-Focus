package com.sakhi.mindfulminutes.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.databinding.ActivityLoginBinding
import com.sakhi.mindfulminutes.models.UserProfile

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val RC_SIGN_IN = 9001
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "LoginActivity created")

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        Log.d(TAG, "Firebase instances initialized")

        // Initialize Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        Log.d(TAG, "Google SignIn client initialized")

        setupClickListeners()
        setupTextWatchers()

        checkExistingUser()
    }

    private fun checkExistingUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User already logged in: ${currentUser.uid}")
            if (currentUser.isEmailVerified) {
                Log.d(TAG, "User email verified, navigating to home")
                saveLoginState()
                navigateToMainActivity()
            } else {
                Log.w(TAG, "User email not verified, signing out")
                auth.signOut()
                googleSignInClient.signOut()
                showError("Please verify your email address before logging in")
            }
        } else {
            Log.d(TAG, "No user currently logged in")
        }
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            Log.d(TAG, "Login button clicked")
            validateAndLogin()
        }

        binding.forgetPasswordTextview.setOnClickListener {
            Log.d(TAG, "Forgot password clicked")
            handleForgotPassword()
        }

        binding.signupButton.setOnClickListener {
            Log.d(TAG, "Sign up button clicked")
            navigateToSignUpActivity()
        }

        binding.googleButton.setOnClickListener {
            Log.d(TAG, "Google sign in button clicked")
            signInWithGoogle()
        }
    }

    private fun setupTextWatchers() {
        binding.useremailEdittext.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateEmailFormat(s.toString())
            }
        })
    }

    private fun validateAndLogin() {
        val email = binding.useremailEdittext.text.toString().trim()
        val password = binding.passwordEdittext.text.toString().trim()

        Log.d(TAG, "Validating login for email: $email")

        if (!validateInputs(email, password)) {
            Log.w(TAG, "Input validation failed")
            return
        }

        showLoading(true)
        checkLoginCondition(email, password)
    }

    private fun validateInputs(email: String, password: String): Boolean {
        clearErrors()

        var isValid = true

        if (email.isEmpty()) {
            binding.useremailEdittext.error = "Email is required"
            isValid = false
            Log.w(TAG, "Email is empty")
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.useremailEdittext.error = "Please enter a valid email address"
            isValid = false
            Log.w(TAG, "Email format is invalid: $email")
        }

        if (password.isEmpty()) {
            binding.passwordEdittext.error = "Password is required"
            isValid = false
            Log.w(TAG, "Password is empty")
        } else if (password.length < 6) {
            binding.passwordEdittext.error = "Password must be at least 6 characters"
            isValid = false
            Log.w(TAG, "Password too short: ${password.length} characters")
        }

        return isValid
    }

    private fun validateEmailFormat(email: String) {
        if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.useremailEdittext.error = "Please enter a valid email address"
        } else {
            binding.useremailEdittext.error = null
        }
    }

    private fun clearErrors() {
        binding.useremailEdittext.error = null
        binding.passwordEdittext.error = null
    }

    private fun checkLoginCondition(email: String, password: String) {
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.isEmailVerified) {
            showLoading(false)
            Log.w(TAG, "Email not verified for user: ${currentUser.uid}")
            showError("Please verify your email address before logging in for security purposes.")
        } else {
            loginUser(email, password)
        }
    }

    private fun loginUser(email: String, password: String) {
        Log.d(TAG, "Attempting to login user: $email")

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val currentUser = auth.currentUser
                    Log.d(TAG, "Email login successful for user: ${currentUser?.uid}")

                    if (currentUser != null && currentUser.isEmailVerified) {
                        Log.d(TAG, "User email verified, updating login data")
                        updateUserLoginData(currentUser.uid)
                        saveLoginState() // <-- Save login state here
                        showSuccess("Login successful!")
                        navigateToMainActivity()
                    } else {
                        showLoading(false)
                        Log.w(TAG, "User email not verified, signing out")
                        showError("Please verify your email address before logging in for security purposes.")
                        auth.signOut()
                    }
                } else {
                    showLoading(false)
                    Log.e(TAG, "Login failed: ${task.exception?.message}", task.exception)
                    showError("Login failed: ${task.exception?.message}")
                }
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Login failed with exception: ${exception.message}", exception)
                showError("Login failed: ${exception.message}")
            }
    }

    private fun updateUserLoginData(uid: String) {
        Log.d(TAG, "Updating user login data for: $uid")

        val userRef = database.getReference("TradingPlatformUsers").child(uid)

        val updates = hashMapOf<String, Any>(
            "lastLoginAt" to System.currentTimeMillis()
        )

        userRef.updateChildren(updates).addOnCompleteListener { task ->
            showLoading(false)
            if (task.isSuccessful) {
                Log.d(TAG, "User login data updated successfully")
            } else {
                Log.e(TAG, "Failed to update user data: ${task.exception?.message}", task.exception)
                showError("Failed to update user data: ${task.exception?.message}")
            }
        }
    }

    private fun handleForgotPassword() {
        val email = binding.useremailEdittext.text.toString().trim()
        if (email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Log.d(TAG, "Sending password reset email to: $email")
            sendPasswordResetEmail(email)
        } else {
            Log.w(TAG, "Invalid email for password reset: $email")
            showError("Please enter a valid email address to reset your password")
        }
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Password reset email sent successfully to: $email")
                showSuccess("Password reset email sent successfully. Please check your inbox.")
            } else {
                Log.e(TAG, "Failed to send reset email: ${task.exception?.message}", task.exception)
                showError("Failed to send reset email: ${task.exception?.message}")
            }
        }
    }

    private fun signInWithGoogle() {
        Log.d(TAG, "Starting Google Sign In flow")
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        if (requestCode == RC_SIGN_IN) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                account?.let {
                    Log.d(TAG, "Google Sign In successful: ${it.email}")
                    firebaseAuthWithGoogle(it)
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google sign in failed: ${e.statusCode}, ${e.message}", e)
                showError("Google sign in failed. Please try again. Error: ${e.statusCode}")
            }
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        Log.d(TAG, "Firebase auth with Google: ${acct.email}")
        showLoading(true)

        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                Log.d(TAG, "Firebase auth successful: ${user?.uid}")
                user?.let { checkAndCreateGoogleUser(it, acct) }
            } else {
                showLoading(false)
                Log.e(TAG, "Google authentication failed: ${task.exception?.message}", task.exception)
                showError("Google authentication failed: ${task.exception?.message}")
            }
        }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Google authentication failed with exception: ${exception.message}", exception)
                showError("Google authentication failed: ${exception.message}")
            }
    }

    private fun checkAndCreateGoogleUser(firebaseUser: com.google.firebase.auth.FirebaseUser, googleAccount: GoogleSignInAccount) {
        Log.d(TAG, "Checking Google user in database: ${firebaseUser.uid}")
        val userRef = database.getReference("TradingPlatformUsers").child(firebaseUser.uid)

        userRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val userData = task.result?.getValue(UserProfile::class.java)
                if (userData == null) {
                    Log.d(TAG, "Google user not found in database, creating new user")
                    createGoogleUserInDatabase(firebaseUser, googleAccount)
                } else {
                    Log.d(TAG, "Google user found in database, updating login data")
                    updateGoogleUserLoginData(firebaseUser, userData)
                }
            } else {
                showLoading(false)
                Log.e(TAG, "Failed to check user data: ${task.exception?.message}", task.exception)
                showError("Failed to check user data: ${task.exception?.message}")
            }
        }
    }

    private fun createGoogleUserInDatabase(firebaseUser: com.google.firebase.auth.FirebaseUser, googleAccount: GoogleSignInAccount) {
        Log.d(TAG, "Creating new Google user in database: ${firebaseUser.uid}")

        val user = UserProfile(
            userId = firebaseUser.uid,
            userName = googleAccount.displayName ?: "Google User",
            userEmail = googleAccount.email ?: "",
            createdAt = System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis(),
            profileImageUrl = googleAccount.photoUrl?.toString() ?: ""
        )

        val usersRef = database.getReference("TradingPlatformUsers")
        usersRef.child(firebaseUser.uid).setValue(user).addOnCompleteListener { task ->
            showLoading(false)
            if (task.isSuccessful) {
                saveLoginState() // <-- Save login state here
                Log.d(TAG, "Google user created successfully in database")
                showSuccess("Google sign-in successful!")
                navigateToMainActivity()
            } else {
                Log.e(TAG, "Failed to save Google user data: ${task.exception?.message}", task.exception)
                showError("Failed to save user data: ${task.exception?.message}")
            }
        }
    }

    private fun updateGoogleUserLoginData(firebaseUser: com.google.firebase.auth.FirebaseUser, existingUser: UserProfile) {
        Log.d(TAG, "Updating Google user login data: ${firebaseUser.uid}")

        val userRef = database.getReference("TradingPlatformUsers").child(firebaseUser.uid)

        val updates = hashMapOf<String, Any>(
            "lastLoginAt" to System.currentTimeMillis()
        )

        firebaseUser.photoUrl?.toString()?.let { photoUrl ->
            if (existingUser.profileImageUrl.isNullOrEmpty()) {
                updates["profileImageUrl"] = photoUrl
            }
        }

        firebaseUser.displayName?.let { displayName ->
            if (existingUser.userName != displayName) {
                updates["userName"] = displayName
            }
        }

        userRef.updateChildren(updates).addOnCompleteListener { task ->
            showLoading(false)
            if (task.isSuccessful) {
                saveLoginState() // <-- Save login state here
                Log.d(TAG, "Google user login data updated successfully")
                showSuccess("Google sign-in successful!")
                navigateToMainActivity()
            } else {
                Log.e(TAG, "Failed to update Google user data: ${task.exception?.message}", task.exception)
                showError("Failed to update user data: ${task.exception?.message}")
            }
        }
    }

    private fun saveLoginState() {
        Log.d(TAG, "Saving login state to SharedPreferences")
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("is_logged_in", true).apply()
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "Navigating to MainActivity")
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToSignUpActivity() {
        Log.d(TAG, "Navigating to SignUpActivity")
        startActivity(Intent(this, SignupActivity::class.java))
    }

    private fun showLoading(show: Boolean) {
        binding.loginButton.isEnabled = !show
        binding.loginButton.text = if (show) "Logging in..." else "Login"
    }


    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
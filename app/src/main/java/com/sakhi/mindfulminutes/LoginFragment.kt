package com.sakhi.mindfulminutes

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

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var ErrorTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        usernameEditText = view.findViewById(R.id.useremail_edittext)
        passwordEditText = view.findViewById(R.id.password_edittext)
        val loginButton = view.findViewById<Button>(R.id.login_button)
        ErrorTextView = view.findViewById(R.id.error_textview)
        val forgetPasswordTextView = view.findViewById<TextView>(R.id.forget_password_textview)
        val signUpButton = view.findViewById<TextView>(R.id.signup_button)
        val googleButton = view.findViewById<com.google.android.gms.common.SignInButton>(R.id.google_button)

        auth = FirebaseAuth.getInstance()

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
                ErrorTextView.visibility = View.VISIBLE
                ErrorTextView.text = "Please enter your email address to reset your password"
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
            ErrorTextView.visibility = View.VISIBLE
            ErrorTextView.text = "Please verify your email address before logging in for security purposes."

        } else {
            loginUser()
        }
    }

    private fun loginUser() {
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            ErrorTextView.visibility = View.VISIBLE
            ErrorTextView.text = "Please enter your email and password , these fields are required to fill and login"
            return
        }

        // Perform login process
        auth.signInWithEmailAndPassword(username, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Login successful, navigate to HomeFragment
                    navigateToHomeFragment()
                } else {
                    // Login failed, display error message
                    Toast.makeText(requireContext(), "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "Password reset email sent successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to send reset email: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
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
                } else {
                    // Sign in failed, display error message
                    Toast.makeText(requireContext(), "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    companion object {
        private const val RC_SIGN_IN = 9001
    }
}

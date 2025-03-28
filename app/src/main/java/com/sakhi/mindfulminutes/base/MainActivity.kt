package com.sakhi.mindfulminutes.base

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.fragments.ActiveActivitiesFragment
import com.sakhi.mindfulminutes.fragments.ActivitiesDetailsFragment
import com.sakhi.mindfulminutes.fragments.ActivitiesFilteredDataFragment
import com.sakhi.mindfulminutes.fragments.ErrorBottomSheetFragment
import com.sakhi.mindfulminutes.fragments.LoginFragment
import com.sakhi.mindfulminutes.fragments.PieChartFragment
import com.sakhi.mindfulminutes.fragments.SignUpFragment
import com.sakhi.mindfulminutes.fragments.SuccessBottomSheetFragment
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var currentFragment: Fragment
    private lateinit var userEmailTextView: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var profileImageView: ImageView
    private lateinit var userNameTextView: TextView
    private lateinit var editProfileIcon: ImageView
    private lateinit var userStatusTextView: TextView
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var progressBar: ProgressBar
    private val IMAGE_PICK_CODE = 1000
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        val headerView = navigationView.getHeaderView(0)
        userEmailTextView = headerView.findViewById(R.id.userEmailTextView)
        userStatusTextView = headerView.findViewById(R.id.userStatusTextView)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        profileImageView = headerView.findViewById(R.id.avatarImage)
        userNameTextView = headerView.findViewById(R.id.userNameTextView)
        editProfileIcon = headerView.findViewById(R.id.editProfileIcon)

        bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_edit_profile, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        val closeIcon = bottomSheetView.findViewById<ImageView>(R.id.btn_close_profile_sheet)
        val userNameEditText = bottomSheetView.findViewById<TextInputEditText>(R.id.et_user_name)
        val emailEditText = bottomSheetView.findViewById<TextInputEditText>(R.id.et_user_email)
        val saveButton = bottomSheetView.findViewById<Button>(R.id.btn_save_profile)
        val profileImageEdit = bottomSheetView.findViewById<ImageView>(R.id.img_profile_picture)
        progressBar = bottomSheetView.findViewById(R.id.progress_bar)

        val currentUser  = auth.currentUser
        if (currentUser  != null) {
            fetchUserDetails(currentUser )
            emailEditText.setText(currentUser .email)
        }

        editProfileIcon.setOnClickListener {
            bottomSheetDialog.show()
        }

        closeIcon.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        saveButton.setOnClickListener {
            val userName = userNameEditText.text.toString()
            val email = emailEditText.text.toString()

            if (userName.isNotEmpty() && validateEmail(email)) {
                if (isInternetAvailable()) {
                    showProgressBar(true)
                    updateUserProfile(userName, email)
                    bottomSheetDialog.dismiss()
                } else {
                    showBottomSheet("No internet connection.", true)
                }
            } else {
                showBottomSheet("Please enter a valid name and email.", true)
            }
        }

        profileImageEdit.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            startActivityForResult(intent, IMAGE_PICK_CODE)
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.open_nav,
            R.string.close_nav
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_active -> {
                    if (auth.currentUser  != null) {
                        replaceFragment(ActiveActivitiesFragment())
                    } else {
                        checkLoginState()
                    }
                    true
                }
                R.id.nav_details -> {
                    if (auth.currentUser  != null) {
                        replaceFragment(ActivitiesDetailsFragment())
                    } else {
                        checkLoginState()
                    }
                    true
                }
                R.id.nav_analysis -> {
                    if (auth.currentUser  != null) {
                        replaceFragment(PieChartFragment())
                    } else {
                        checkLoginState()
                    }
                    true
                }
                R.id.nav_filtered -> {
                    if (auth.currentUser  != null) {
                        replaceFragment(ActivitiesFilteredDataFragment())
                    } else {
                        checkLoginState()
                    }
                    true
                }
                R.id.nav_login -> {
                    if (auth.currentUser  == null) {
                        replaceFragment(LoginFragment())
                    }
                    true
                }
                R.id.nav_logout -> {
                    logoutUser ()
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            val isLoggedIn = checkIfUserIsLoggedIn()
            if (isLoggedIn) {
                replaceFragment(ActiveActivitiesFragment(), addToBackStack = false)
                navigationView.setCheckedItem(R.id.nav_active)
            } else {
                replaceFragment(LoginFragment())
            }
        }
    }

    private fun validateEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun checkIfUserIsLoggedIn(): Boolean {
        return auth.currentUser  != null
    }

    private fun replaceFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        currentFragment = fragment
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun logoutUser () {
        auth.signOut()
        replaceFragment(LoginFragment())
        showBottomSheet("Logout Successful", false)
        navigationView.setCheckedItem(R.id.nav_login)
    }

    private fun checkLoginState() {
        if (auth.currentUser  == null) {
            replaceFragment(SignUpFragment(), addToBackStack = false)
            navigationView.setCheckedItem(R.id.nav_signUp)
        } else if (currentFragment is ActiveActivitiesFragment) {
            val currentUser  = auth.currentUser
            if (currentUser  != null && currentUser .email.isNullOrEmpty()) {
                replaceFragment(SignUpFragment(), addToBackStack = false)
                navigationView.setCheckedItem(R.id.nav_signUp)
            }
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private fun showBottomSheet(message: String, isError: Boolean) {
        val bottomSheetFragment = if (isError) {
            ErrorBottomSheetFragment(message)
        } else {
            SuccessBottomSheetFragment(message)
        }

        bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
    }

    private fun updateUserProfile(userName: String, email: String) {
        val currentUser  = auth.currentUser
        currentUser ?.let {
            val userDocRef = db.collection("users").document(it.uid)

            // Save data to Firestore
            saveUserDataToFirestore(userName, email)

            // Update Firebase Auth user profile
            it.updateEmail(email).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    userEmailTextView.text = email
                    userNameTextView.text = userName
                    showBottomSheet("Profile updated successfully", false)
                    Log.d(TAG, "Profile updated successfully for user: $userName")
                } else {
                    showBottomSheet("Email update failed.", true)
                    Log.d(TAG, "Email update failed: ${task.exception?.message}")
                }
            }
        }
    }

    private fun saveUserDataToFirestore(userName: String, userEmail: String) {
        val userMap = hashMapOf(
            "userName" to userName,
            "userEmail" to userEmail,
            "isVerified" to false
        )

        db.collection("users")
            .document(auth.currentUser !!.uid)
            .set(userMap)
            .addOnSuccessListener {
                showBottomSheet("User  data saved to Firestore", false)
                Log.d(TAG, "User  data saved to Firestore for user: $userName")
            }
            .addOnFailureListener { e ->
                showBottomSheet("Failed to save user data: ${e.message}", true)
                Log.d(TAG, "Failed to save user data: ${e.message}")
            }
    }

    private fun fetchUserDetails(user: FirebaseUser ) {
        val userDocRef = db.collection("users").document(user.uid)
        userDocRef.get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val userName = document.getString("userName") ?: "User "
                val userEmail = document.getString("userEmail") ?: "No email"
                val isVerified = document.getBoolean("isVerified") ?: false

                userNameTextView.text = userName
                userEmailTextView.text = userEmail

                // Update the user status text view based on email verification
                if (isVerified) {
                    userStatusTextView.text = "Email is verified"
                } else {
                    userStatusTextView.text = "Email is not verified"

                }

                val photoUrl = user.photoUrl
                if (photoUrl != null) {
                    Picasso.get().load(photoUrl).into(profileImageView)
                }

                checkEmailVerification()
            } else {
                showBottomSheet("User  document does not exist", true)
                Log.d(TAG, "User  document does not exist for UID: ${user.uid}")
            }
        }.addOnFailureListener { e ->
            showBottomSheet("Failed to fetch user details: ${e.message}", true)
            Log.d(TAG, "Failed to fetch user details: ${e.message}")
        }
    }

    private fun checkEmailVerification() {
        val currentUser  = auth.currentUser
        currentUser ?.reload()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                if (currentUser .isEmailVerified) {
                    userStatusTextView.text = "Verified"
                    updateUserVerificationStatus(true)
                    Log.d(TAG, "Email is verified for user: ${currentUser .displayName}")
                } else {
                    userStatusTextView.text = "Not verified"
                    updateUserVerificationStatus(false)
                    Log.d(TAG, "Email is not verified for user: ${currentUser .displayName}")
                }
            } else {
                Log.d(TAG, "Failed to reload user: ${task.exception?.message}")
            }
        }
    }
    private fun updateUserVerificationStatus(isVerified: Boolean) {
        val userDocRef = db.collection("users").document(auth.currentUser !!.uid)
        userDocRef.update("isVerified", isVerified).addOnSuccessListener {
            Log.d(TAG, "Successfully updated verification status to $isVerified")
        }.addOnFailureListener { e ->
            showBottomSheet("Failed to update verification status: ${e.message}", true)
            Log.d(TAG, "Failed to update verification status: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            Picasso.get().load(imageUri).into(profileImageView)
            val storageRef = storage.reference.child("profile_pictures/${auth.currentUser ?.uid}.jpg")
            storageRef.putFile(imageUri!!).addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val currentUser  = auth.currentUser
                    val userDocRef = db.collection("users").document(currentUser ?.uid ?: "")
                    userDocRef.update("photoUrl", uri.toString())
                    currentUser ?.updateProfile(
                        UserProfileChangeRequest.Builder().setPhotoUri(uri).build()
                    )
                    showProgressBar(false)
                    Log.d(TAG, "Profile picture updated successfully for user: ${currentUser ?.displayName}")
                }
            }.addOnFailureListener { e ->
                showProgressBar(false)
                Log.d(TAG, "Failed to upload profile picture: ${e.message}")
            }
        }
    }

    private fun showProgressBar(show: Boolean) {
        progressBar.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.GONE
    }
}
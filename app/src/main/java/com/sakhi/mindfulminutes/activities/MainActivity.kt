package com.sakhi.mindfulminutes.activities

import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Patterns
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.databinding.ActivityMainBinding
import com.sakhi.mindfulminutes.databinding.BottomSheetEditProfileBinding
import com.sakhi.mindfulminutes.databinding.NavHeaderBinding
import com.sakhi.mindfulminutes.fragments.*
import com.sakhi.mindfulminutes.models.UserProfile
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navHeaderBinding: NavHeaderBinding
    private lateinit var profileBottomSheetBinding: BottomSheetEditProfileBinding

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var currentFragment: Fragment
    private lateinit var bottomSheetDialog: BottomSheetDialog

    private val IMAGE_PICK_CODE = 1000
    private val TAG = "MainActivity"

    companion object {
        private const val PREFS_NAME = "UserProfilePrefs"
        private const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding setup
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Firebase + SharedPreferences setup
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupToolbar()
        setupBottomSheet()          // initialize BEFORE loading user data
        setupNavigationDrawer()
        setupClickListeners()

        // Load initial fragment
        if (savedInstanceState == null && auth.currentUser != null) {
            loadFragment(ActiveActivitiesFragment(), addToBackStack = false)
            binding.navView.setCheckedItem(R.id.nav_active)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun setupNavigationDrawer() {
        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.open_nav,
            R.string.close_nav
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)

        // Header binding
        val headerView = binding.navView.getHeaderView(0)
        navHeaderBinding = NavHeaderBinding.bind(headerView)

        // Load user data
        loadUserData()
    }

    private fun setupBottomSheet() {
        bottomSheetDialog = BottomSheetDialog(this)
        profileBottomSheetBinding = BottomSheetEditProfileBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(profileBottomSheetBinding.root)

        setupBottomSheetListeners()
    }

    private fun setupBottomSheetListeners() {
        profileBottomSheetBinding.btnCloseProfileSheet.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        profileBottomSheetBinding.btnSaveProfile.setOnClickListener {
            val userName = profileBottomSheetBinding.etUserName.text.toString().trim()
            val email = profileBottomSheetBinding.etUserEmail.text.toString().trim()

            if (validateProfileInputs(userName, email)) {
                updateUserProfile(userName, email)
            }
        }

        profileBottomSheetBinding.imgProfilePicture.setOnClickListener {
            openImagePicker()
        }
    }

    private fun setupClickListeners() {
        navHeaderBinding.editProfileButton.setOnClickListener {
            showEditProfileBottomSheet()
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser ?: return

        val savedName = sharedPreferences.getString(KEY_USER_NAME, currentUser.displayName ?: "User")
        val savedEmail = sharedPreferences.getString(KEY_USER_EMAIL, currentUser.email ?: "No email")

        navHeaderBinding.userNameText.text = savedName
        navHeaderBinding.userEmailText.text = savedEmail

        loadProfileImageFromStorage()
        loadUserFromDatabase(currentUser.uid)
    }

    private fun loadProfileImageFromStorage() {
        val imageUriString = sharedPreferences.getString(KEY_PROFILE_IMAGE_URI, null)
        imageUriString?.let { uriString ->
            val imageUri = Uri.parse(uriString)
            Picasso.get()
                .load(imageUri)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(navHeaderBinding.avatarImage)

            if (::profileBottomSheetBinding.isInitialized) {
                Picasso.get().load(imageUri).into(profileBottomSheetBinding.imgProfilePicture)
            }
        } ?: run {
            navHeaderBinding.avatarImage.setImageResource(R.drawable.ic_profile)
            if (::profileBottomSheetBinding.isInitialized) {
                profileBottomSheetBinding.imgProfilePicture.setImageResource(R.drawable.ic_profile)
            }
        }
    }

    private fun saveProfileImageToStorage(imageUri: Uri) {
        sharedPreferences.edit().putString(KEY_PROFILE_IMAGE_URI, imageUri.toString()).apply()
    }

    private fun saveUserProfileToStorage(userName: String, email: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    private fun loadUserFromDatabase(userId: String) {
        val userRef = database.getReference("TradingPlatformUsers").child(userId)
        userRef.get().addOnSuccessListener { snapshot ->
            val user = snapshot.getValue(UserProfile::class.java)
            user?.let {
                navHeaderBinding.userNameText.text = it.userName
                navHeaderBinding.userEmailText.text = it.userEmail

                saveUserProfileToStorage(it.userName, it.userEmail)

                navHeaderBinding.statusChip.text = "Active"
                navHeaderBinding.statusChip.setChipBackgroundColorResource(R.color.statusInfo)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to load user data: ${e.message}")
        }
    }

    private fun showEditProfileBottomSheet() {
        val currentUser = auth.currentUser ?: return

        val savedName = sharedPreferences.getString(KEY_USER_NAME, currentUser.displayName ?: "")
        val savedEmail = sharedPreferences.getString(KEY_USER_EMAIL, currentUser.email ?: "")

        profileBottomSheetBinding.etUserName.setText(savedName)
        profileBottomSheetBinding.etUserEmail.setText(savedEmail)

        loadProfileImageFromStorage()
        bottomSheetDialog.show()
    }

    private fun validateProfileInputs(userName: String, email: String): Boolean {
        if (userName.isEmpty()) {
            profileBottomSheetBinding.etUserName.error = "Name is required"
            return false
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            profileBottomSheetBinding.etUserEmail.error = "Valid email is required"
            return false
        }

        return true
    }

    private fun updateUserProfile(userName: String, email: String) {
        if (!isInternetAvailable()) {
            showError("No internet connection")
            return
        }

        showLoading(true)
        val currentUser = auth.currentUser ?: return

        updateUserInDatabase(currentUser.uid, userName, email)
        updateAuthProfile(currentUser, userName, email)
        saveUserProfileToStorage(userName, email)
    }

    private fun updateUserInDatabase(userId: String, userName: String, email: String) {
        val updates = hashMapOf<String, Any>(
            "userName" to userName,
            "userEmail" to email
        )

        val userRef = database.getReference("TradingPlatformUsers").child(userId)
        userRef.updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "User data updated in database")
                navHeaderBinding.userNameText.text = userName
                navHeaderBinding.userEmailText.text = email
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update user data: ${e.message}")
                showError("Failed to update profile")
            }
    }

    private fun updateAuthProfile(user: FirebaseUser, userName: String, email: String) {
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(userName)
            .build()

        user.updateProfile(profileUpdates)
            .addOnSuccessListener {
                user.updateEmail(email)
                    .addOnSuccessListener {
                        showLoading(false)
                        bottomSheetDialog.dismiss()
                        showSuccess("Profile updated successfully")
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        showError("Email update failed: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                showError("Profile update failed: ${e.message}")
            }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            imageUri?.let { uri ->
                Picasso.get().load(uri).into(profileBottomSheetBinding.imgProfilePicture)
                Picasso.get().load(uri).into(navHeaderBinding.avatarImage)
                saveProfileImageToStorage(uri)
                showSuccess("Profile picture updated locally")
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_active -> loadFragment(ActiveActivitiesFragment())
            R.id.nav_details -> loadFragment(ActivitiesDetailsFragment())
            R.id.nav_analysis -> loadFragment(PieChartFragment())
            R.id.nav_logout -> showLogoutConfirmation()
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun loadFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        currentFragment = fragment
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)

        if (addToBackStack) transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ -> logoutUser() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logoutUser() {
        sharedPreferences.edit().clear().apply()
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    // ✅ New simplified loading indicator
    private fun showLoading(show: Boolean) {
        profileBottomSheetBinding.btnSaveProfile.isEnabled = !show
        profileBottomSheetBinding.btnSaveProfile.text =
            if (show) "Updating..." else "Update Changes"
    }

    private fun showSuccess(message: String) {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            message,
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).setBackgroundTint(getColor(R.color.statusSuccess)).show()
    }

    private fun showError(message: String) {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            message,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).setBackgroundTint(getColor(R.color.error)).show()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

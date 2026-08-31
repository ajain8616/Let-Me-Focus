package com.sakhi.mindfulminutes.activities

import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
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

    private val TAG = "MainActivity"

    companion object {
        private const val PREFS_NAME = "UserProfilePrefs"
        private const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
    }

    // Modern Android Photo Picker Contract
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            Picasso.get().load(it).into(profileBottomSheetBinding.imgProfilePicture)
            Picasso.get().load(it).into(navHeaderBinding.avatarImage)
            saveProfileImageToStorage(it)
            showSuccess("Profile picture updated successfully")
        }
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
        setupBottomSheet()
        setupNavigationDrawer()
        setupClickListeners()
        setupBackNavigation()

        // Handle initial launch or notification tap navigation
        if (savedInstanceState == null && auth.currentUser != null) {
            handleNotificationIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Handles navigation when the user taps on the live stopwatch notification
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (auth.currentUser == null) return

        val navigateTo = intent?.getStringExtra("navigate_to")
        val fragmentParam = intent?.getStringExtra("fragment")

        if (navigateTo == "active_activities" || fragmentParam == "activities") {
            loadFragment(ActiveActivitiesFragment(), addToBackStack = false)
            binding.navView.setCheckedItem(R.id.nav_active)
        } else {
            loadFragment(ActiveActivitiesFragment(), addToBackStack = false)
            binding.navView.setCheckedItem(R.id.nav_active)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupNavigationDrawer() {
        val toggle = ActionBarDrawerToggle(
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

        // Ensures translucent acrylic styling without default rectangular sheet frame
        bottomSheetDialog.window?.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.setBackgroundResource(android.R.color.transparent)
        }

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

        profileBottomSheetBinding.btnChangePhoto.setOnClickListener {
            openImagePicker()
        }
    }

    private fun setupClickListeners() {
        navHeaderBinding.editProfileButton.setOnClickListener {
            showEditProfileBottomSheet()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
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

                navHeaderBinding.statusChip.text = getString(R.string.active)
                navHeaderBinding.statusChip.setChipBackgroundColorResource(R.color.statusSuccess)
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
                showError("Failed to update profile in database")
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
        imagePickerLauncher.launch("image/*")
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
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragment_container, fragment)

        if (addToBackStack) transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.logout))
            .setMessage("Are you sure you want to logout from your account?")
            .setPositiveButton(getString(R.string.logout)) { _, _ -> logoutUser() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logoutUser() {
        sharedPreferences.edit().clear().apply()
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
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

    private fun showLoading(show: Boolean) {
        profileBottomSheetBinding.btnSaveProfile.isEnabled = !show
        profileBottomSheetBinding.btnSaveProfile.text =
            if (show) "Updating..." else getString(R.string.save)
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.statusSuccess))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .show()
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
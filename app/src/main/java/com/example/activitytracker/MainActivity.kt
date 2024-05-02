package com.example.activitytracker

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var currentFragment: Fragment

    // Firebase Authentication instance
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize Firebase Authentication
        auth = FirebaseAuth.getInstance()

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
                    if (auth.currentUser != null) {
                        replaceFragment(ActiveActivitiesFragment())
                    }
                    true
                }
                R.id.nav_details -> {
                    if (auth.currentUser != null) {
                        replaceFragment(ActivitiesDetailsFragment())
                    }
                    true
                }

                R.id.nav_analysis -> {
                    if (auth.currentUser != null) {
                        replaceFragment(PieChartFragment())
                    }
                    true
                }

                R.id.nav_filtered -> {
                    if (auth.currentUser != null) {
                        replaceFragment(FilteredActivitiesFragment())
                    }
                    true
                }

                R.id.nav_login -> {
                    if (auth.currentUser == null) {
                        replaceFragment(LoginFragment())
                    }
                    true
                }
                R.id.nav_signUp -> {
                    if (auth.currentUser == null) {
                        replaceFragment(SignUpFragment())
                    }
                    true
                }
                R.id.nav_logout -> {
                    // Handle logout action here
                    logoutUser()
                    true
                }
                else -> false
            }
        }

        // Check if user is logged in and display appropriate fragment
        val isLoggedIn = checkIfUserIsLoggedIn()
        if (savedInstanceState == null) {
            if (isLoggedIn) {
                replaceFragment(ActiveActivitiesFragment(), addToBackStack = false)
                navigationView.setCheckedItem(R.id.nav_active)
            } else {
                replaceFragment(LoginFragment(), addToBackStack = false)
                navigationView.setCheckedItem(R.id.nav_login)
            }
        }
        showToastBasedOnLoginStatus(isLoggedIn)

    }

    private fun showToastBasedOnLoginStatus(isLoggedIn: Boolean) {
        val message = if (isLoggedIn) "User is logged in" else "User is logged out"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun checkIfUserIsLoggedIn(): Boolean {
        // Check if the user is currently authenticated
        return auth.currentUser != null
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

    private fun logoutUser() {
        // Sign out the user from Firebase
        auth.signOut()
        // After signing out, navigate to LoginFragment
        replaceFragment(LoginFragment(), addToBackStack = false)
        navigationView.setCheckedItem(R.id.nav_login)
    }


    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}

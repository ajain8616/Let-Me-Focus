package com.sakhi.mindfulminutes.base

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sakhi.mindfulminutes.R
import com.sakhi.mindfulminutes.activities.LoginActivity
import com.sakhi.mindfulminutes.activities.MainActivity
import com.sakhi.mindfulminutes.activities.SignupActivity
import com.sakhi.mindfulminutes.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val splashDelay: Long = 3500 // 3.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Adjust padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        startSplashAnimation()
    }

    private fun initializeViews() {
        // Hide elements initially
        binding.appLogo.visibility = View.VISIBLE
        binding.appIcon.visibility = View.INVISIBLE
        binding.appName.visibility = View.INVISIBLE
        binding.appTagline.visibility = View.INVISIBLE
        binding.loadingProgress.visibility = View.INVISIBLE
        binding.versionInfo.visibility = View.INVISIBLE

        binding.appIcon.alpha = 0f
        binding.appName.alpha = 0f
        binding.appTagline.alpha = 0f
        binding.loadingProgress.alpha = 0f
        binding.versionInfo.alpha = 0f
    }

    private fun startSplashAnimation() {
        val flipInAnimator = android.animation.AnimatorInflater.loadAnimator(
            this,
            R.animator.card_flip_in
        )
        flipInAnimator.setTarget(binding.appLogo)
        flipInAnimator.start()

        flipInAnimator.doOnEnd {
            showAppIcon()
        }
    }

    private fun showAppIcon() {
        binding.appIcon.visibility = View.VISIBLE
        val scaleAnimator = android.animation.AnimatorInflater.loadAnimator(
            this,
            R.animator.scale_button
        )
        scaleAnimator.setTarget(binding.appIcon)
        scaleAnimator.start()

        binding.appIcon.animate()
            .alpha(1f)
            .setDuration(600)
            .withEndAction { showAppName() }
            .start()
    }

    private fun showAppName() {
        binding.appName.visibility = View.VISIBLE
        binding.appName.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(800)
            .withEndAction { showTagline() }
            .start()
    }

    private fun showTagline() {
        binding.appTagline.visibility = View.VISIBLE
        binding.appTagline.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(800)
            .withEndAction { showLoadingElements() }
            .start()
    }

    private fun showLoadingElements() {
        binding.loadingProgress.visibility = View.VISIBLE
        binding.versionInfo.visibility = View.VISIBLE

        val scaleAnimator = android.animation.AnimatorInflater.loadAnimator(
            this,
            R.animator.scale_button
        )
        scaleAnimator.setTarget(binding.loadingProgress)
        scaleAnimator.start()

        binding.loadingProgress.animate()
            .alpha(1f)
            .setDuration(600)
            .start()

        binding.versionInfo.animate()
            .alpha(1f)
            .setDuration(600)
            .start()

        simulateLoadingProgress()
    }

    private fun simulateLoadingProgress() {
        binding.loadingProgress.setProgress(0, false)
        binding.loadingProgress.animate()
            .setDuration(splashDelay - 2000)
            .withEndAction { binding.loadingProgress.setProgress(100, true) }
            .start()

        Handler(Looper.getMainLooper()).postDelayed({
            navigateNext()
        }, splashDelay)
    }

    private fun navigateNext() {
        val flipOutAnimator = android.animation.AnimatorInflater.loadAnimator(
            this,
            R.animator.card_flip_out
        )
        flipOutAnimator.setTarget(binding.appLogo)
        flipOutAnimator.start()

        binding.appIcon.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(400)
            .start()

        flipOutAnimator.doOnEnd {
            binding.loadingProgress.animate().alpha(0f).setDuration(400).withEndAction {
                binding.loadingProgress.visibility = View.INVISIBLE
            }.start()
            binding.versionInfo.animate().alpha(0f).setDuration(400).withEndAction {
                binding.versionInfo.visibility = View.INVISIBLE
                decideNextActivity()
            }.start()
        }
    }

    private fun decideNextActivity() {
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val isRegistered = prefs.getBoolean("is_registered", false)

        val nextIntent = when {
            isLoggedIn -> Intent(this, MainActivity::class.java)
            isRegistered -> Intent(this, LoginActivity::class.java)
            else -> Intent(this, SignupActivity::class.java)
        }

        startActivity(nextIntent)
        overridePendingTransition(R.animator.slide_in_right, R.animator.slide_out_left)
        finish()
    }

    override fun onPause() {
        super.onPause()
        Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)
    }
}

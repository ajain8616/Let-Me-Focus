package com.sakhi.mindfulminutes.base

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.sakhi.mindfulminutes.activities.LoginActivity
import com.sakhi.mindfulminutes.activities.MainActivity
import com.sakhi.mindfulminutes.activities.SignupActivity
import com.sakhi.mindfulminutes.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val splashDuration: Long = 2700
    private var blobAnimatorSet: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prepareInitialViewState()
        startAmbientBlobAnimation()
        startCinematicEntrance()
    }

    private fun prepareInitialViewState() {
        val initialOffsetY = 40f

        binding.appLogo.apply {
            alpha = 0f
            scaleX = 0.4f
            scaleY = 0.4f
        }

        binding.appIcon.apply {
            visibility = View.GONE
        }

        binding.appName.apply {
            alpha = 0f
            translationY = initialOffsetY
        }

        binding.appTagline.apply {
            alpha = 0f
            translationY = initialOffsetY
        }

        binding.loadingProgress.apply {
            alpha = 0f
            progress = 0
            translationY = initialOffsetY
        }

        binding.versionInfo.apply {
            alpha = 0f
        }
    }

    /**
     * Ambient floating background blobs for subtle depth
     */
    private fun startAmbientBlobAnimation() {
        val blob1X = ObjectAnimator.ofFloat(binding.bgBlob1, View.TRANSLATION_X, 0f, 35f, 0f).apply {
            duration = 5500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val blob1Y = ObjectAnimator.ofFloat(binding.bgBlob1, View.TRANSLATION_Y, 0f, -35f, 0f).apply {
            duration = 4800
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val blob2X = ObjectAnimator.ofFloat(binding.bgBlob2, View.TRANSLATION_X, 0f, -30f, 0f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val blob2Y = ObjectAnimator.ofFloat(binding.bgBlob2, View.TRANSLATION_Y, 0f, 30f, 0f).apply {
            duration = 5400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        blobAnimatorSet = AnimatorSet().apply {
            playTogether(blob1X, blob1Y, blob2X, blob2Y)
            start()
        }
    }

    /**
     * Staggered Spring & Easing Entrance
     */
    private fun startCinematicEntrance() {
        // 1. Logo Elastic Pop
        binding.appLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(950)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()

        // 2. App Name Rise
        binding.appName.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(280)
            .setDuration(700)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()

        // 3. Tagline Rise
        binding.appTagline.animate()
            .alpha(0.9f)
            .translationY(0f)
            .setStartDelay(420)
            .setDuration(700)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()

        // 4. Progress Indicator & Version Info
        binding.loadingProgress.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(560)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()

        binding.versionInfo.animate()
            .alpha(0.7f)
            .setStartDelay(650)
            .setDuration(600)
            .start()

        // 5. Smooth Linear Progress Filling
        startProgressAnimation()
    }

    private fun startProgressAnimation() {
        val progressAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = splashDuration - 600
            startDelay = 600
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animator ->
                binding.loadingProgress.progress = animator.animatedValue as Int
            }
        }
        progressAnimator.start()

        // Schedule smooth exit transition
        binding.root.postDelayed({
            navigateNextWithExitAnimation()
        }, splashDuration)
    }

    /**
     * Cinematic Zoom & Fade Exit
     */
    private fun navigateNextWithExitAnimation() {
        binding.appLogo.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .alpha(0f)
            .setDuration(350)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()

        binding.appName.animate().alpha(0f).setDuration(300).start()
        binding.appTagline.animate().alpha(0f).setDuration(300).start()
        binding.loadingProgress.animate().alpha(0f).setDuration(300).start()
        binding.versionInfo.animate().alpha(0f).setDuration(300).withEndAction {
            decideNextActivity()
        }.start()
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
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        blobAnimatorSet?.cancel()
        binding.root.removeCallbacks(null)
    }
}
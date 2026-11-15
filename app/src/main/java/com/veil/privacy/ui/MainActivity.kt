package com.veil.privacy.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.R
import com.veil.privacy.activation.ActivationManager
import com.veil.privacy.databinding.ActivityMainBinding
import com.veil.privacy.notification.VeilNotificationManager
import com.veil.privacy.security.SecurityManager
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var initializationInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.ivSplashLogo.alpha = 0f
        binding.ivSplashLogo.animate().alpha(1f).setDuration(220L).start()
        binding.btnSplashRetry.setOnClickListener { initializeApp() }
        initializeApp()
    }

    private fun initializeApp() {
        if (initializationInProgress || isFinishing || isDestroyed) return
        initializationInProgress = true
        binding.progressBar.visibility = View.VISIBLE
        binding.tvSplashError.visibility = View.GONE
        binding.btnSplashRetry.visibility = View.GONE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    VeilPrefs.getInstance(applicationContext).performSecurityMigrationIfNeeded()
                }
                val activation = ActivationManager(applicationContext)
                val hasToken = withContext(Dispatchers.IO) { activation.hasToken() }
                if (!hasToken) {
                    openActivation()
                    return@launch
                }
                val licensed = withContext(Dispatchers.IO) { activation.refresh() }
                if (!licensed) {
                    openActivation()
                    return@launch
                }

                withContext(Dispatchers.IO) { VeilDatabase.getInstance(applicationContext) }
                VeilNotificationManager.getInstance(applicationContext).createNotificationChannel()
                val targetClass = when {
                    !SecurityManager.isSecuritySetupCompleted(this@MainActivity) -> SecuritySetupActivity::class.java
                    !VeilPrefs.isSessionUnlocked -> AuthLockActivity::class.java
                    else -> DashboardActivity::class.java
                }
                startActivity(Intent(this@MainActivity, targetClass))
                finish()
            } catch (error: Exception) {
                initializationInProgress = false
                binding.progressBar.visibility = View.GONE
                binding.tvSplashError.text = error.localizedMessage
                    ?.takeIf { it.isNotBlank() } ?: getString(R.string.launch_unknown_error)
                binding.tvSplashError.visibility = View.VISIBLE
                binding.btnSplashRetry.visibility = View.VISIBLE
            }
        }
    }

    private fun openActivation() {
        startActivity(Intent(this, ActivationActivity::class.java))
        finish()
    }
}

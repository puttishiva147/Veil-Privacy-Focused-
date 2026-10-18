package com.veil.privacy.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.R
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
                    VeilDatabase.getInstance(applicationContext)
                }
                VeilNotificationManager.getInstance(applicationContext).createNotificationChannel()

                val targetClass = when {
                    !SecurityManager.isSecuritySetupCompleted(this@MainActivity) ->
                        SecuritySetupActivity::class.java
                    !VeilPrefs.isSessionUnlocked ->
                        AuthLockActivity::class.java
                    else -> DashboardActivity::class.java
                }
                startActivity(Intent(this@MainActivity, targetClass))
                finish()
            } catch (error: Exception) {
                initializationInProgress = false
                binding.progressBar.visibility = View.GONE
                binding.tvSplashError.text = getString(
                    R.string.launch_initialization_error,
                    error.localizedMessage?.takeIf { it.isNotBlank() } ?: getString(R.string.launch_unknown_error)
                )
                binding.tvSplashError.visibility = View.VISIBLE
                binding.btnSplashRetry.visibility = View.VISIBLE
            }
        }
    }
}

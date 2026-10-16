package com.veil.privacy.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.veil.privacy.databinding.ActivityMainBinding
import com.veil.privacy.notification.VeilNotificationManager
import com.veil.privacy.security.SecurityManager
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilPrefs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Preload database and channels
        val prefs = VeilPrefs.getInstance(this)
        prefs.performSecurityMigrationIfNeeded()
        VeilDatabase.getInstance(this)
        VeilNotificationManager.getInstance(this).createNotificationChannel()

        Handler(Looper.getMainLooper()).postDelayed({
            val targetClass = when {
                !SecurityManager.isSecuritySetupCompleted(this) -> {
                    // First launch / no credentials configured -> Launch Security Setup
                    SecuritySetupActivity::class.java
                }
                !VeilPrefs.isSessionUnlocked -> {
                    // Normal launch after setup -> Launch Unlock Screen
                    AuthLockActivity::class.java
                }
                else -> {
                    // Already unlocked in current session -> Open Dashboard
                    DashboardActivity::class.java
                }
            }
            startActivity(Intent(this, targetClass))
            finish()
        }, 500)
    }
}

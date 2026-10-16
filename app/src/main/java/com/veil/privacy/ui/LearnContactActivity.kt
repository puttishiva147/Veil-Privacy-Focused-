package com.veil.privacy.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivityLearnContactBinding
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.notification.VeilNotificationListener
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.util.NotificationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LearnContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLearnContactBinding
    private lateinit var prefs: VeilPrefs
    private lateinit var db: VeilDatabase
    private var detectedSender: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearnContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.veil.privacy.util.InsetsUtils.applySystemBarInsets(binding.root)

        prefs = VeilPrefs.getInstance(this)
        db = VeilDatabase.getInstance(this)

        setupListeners()
        observeLearnedSender()
    }

    override fun onResume() {
        super.onResume()
        updateListeningUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop learning mode when leaving screen
        prefs.isLearnModeActive = false
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnToggleListening.setOnClickListener {
            if (!NotificationUtils.isNotificationAccessGranted(this)) {
                Toast.makeText(
                    this,
                    "Please grant Notification Access first to enable auto-learning",
                    Toast.LENGTH_LONG
                ).show()
                NotificationUtils.openNotificationListenerSettings(this)
                return@setOnClickListener
            }

            val newState = !prefs.isLearnModeActive
            prefs.isLearnModeActive = newState
            updateListeningUI()

            if (newState) {
                // Clear previous detected sender card when starting fresh
                binding.cardDetectionResult.visibility = View.GONE
                detectedSender = null
            }
        }

        binding.btnConfirmProtectLearned.setOnClickListener {
            val senderToProtect = detectedSender ?: return@setOnClickListener

            lifecycleScope.launch {
                val normalized = ProtectedContact.normalize(senderToProtect)
                val existing = withContext(Dispatchers.IO) {
                    db.protectedContactDao().findByNormalizedNameSync(normalized)
                }

                if (existing != null) {
                    Toast.makeText(
                        this@LearnContactActivity,
                        "'$senderToProtect' is already protected",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }

                val (decoyName, decoyMsg) = com.veil.privacy.privacy.DecoyGenerator.getInstance().generateDecoy(prefs)
                withContext(Dispatchers.IO) {
                    db.protectedContactDao().insert(ProtectedContact(name = senderToProtect))
                    db.protectionRuleDao().insert(
                        com.veil.privacy.model.ProtectionRule(
                            contactName = senderToProtect,
                            disguiseMode = com.veil.privacy.model.ProtectionRule.MODE_RANDOM_ROTATION,
                            decoyIdentity = "Dynamic",
                            decoyMessage = "Dynamic disguise • Rotates on arrival",
                            source = com.veil.privacy.model.ProtectionRule.SOURCE_SENSOR_LEARN,
                            isEnabled = true
                        )
                    )
                    db.selectedContactDao().insert(
                        com.veil.privacy.model.SelectedContact(
                            displayName = senderToProtect,
                            source = com.veil.privacy.model.ProtectionRule.SOURCE_SENSOR_LEARN,
                            isEnabled = true
                        )
                    )
                }

                Toast.makeText(
                    this@LearnContactActivity,
                    "Added '$senderToProtect' to protected contacts",
                    Toast.LENGTH_LONG
                ).show()

                // Turn off learning mode
                prefs.isLearnModeActive = false
                finish()
            }
        }
    }

    private fun updateListeningUI() {
        val isListening = prefs.isLearnModeActive

        if (isListening) {
            binding.tvLearningStatus.text = getString(R.string.learn_status_waiting)
            binding.btnToggleListening.text = getString(R.string.learn_btn_stop)
            binding.pbLearning.visibility = View.VISIBLE
        } else {
            binding.tvLearningStatus.text = getString(R.string.learn_status_idle)
            binding.btnToggleListening.text = getString(R.string.learn_btn_start)
            binding.pbLearning.visibility = View.GONE
        }
    }

    private fun observeLearnedSender() {
        lifecycleScope.launch {
            VeilNotificationListener.learnedSenderFlow.collectLatest { sender ->
                if (!prefs.isLearnModeActive) return@collectLatest

                detectedSender = sender
                binding.cardDetectionResult.visibility = View.VISIBLE

                if (sender.isNullOrBlank()) {
                    binding.tvDetectedSenderName.visibility = View.GONE
                    binding.tvDetectedFailureNotice.visibility = View.VISIBLE
                    binding.btnConfirmProtectLearned.isEnabled = false
                } else {
                    binding.tvDetectedSenderName.text = sender
                    binding.tvDetectedSenderName.visibility = View.VISIBLE
                    binding.tvDetectedFailureNotice.visibility = View.GONE
                    binding.btnConfirmProtectLearned.isEnabled = true
                }
            }
        }
    }
}

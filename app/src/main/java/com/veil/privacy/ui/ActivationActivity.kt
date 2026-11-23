package com.veil.privacy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.R
import com.veil.privacy.activation.ActivationManager
import com.veil.privacy.databinding.ActivityActivationBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityActivationBinding
    private lateinit var activation: ActivationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActivationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        activation = ActivationManager(this)
        binding.btnGetCode.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activation.websiteUrl())))
        }
        binding.btnActivate.setOnClickListener { submitCode() }
    }

    private fun submitCode() {
        val code = binding.etActivationCode.text?.toString().orEmpty()
        if (code.filter { it.isDigit() }.length != 12) {
            showError(getString(R.string.activation_invalid_code))
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { activation.activate(code) }
                Toast.makeText(this@ActivationActivity, R.string.activation_success, Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@ActivationActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                finish()
            } catch (error: Exception) {
                showError(error.localizedMessage ?: getString(R.string.activation_failed))
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.btnActivate.isEnabled = !busy
        binding.btnGetCode.isEnabled = !busy
        binding.progressActivation.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) binding.tvActivationError.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.tvActivationError.text = message
        binding.tvActivationError.visibility = View.VISIBLE
    }
}

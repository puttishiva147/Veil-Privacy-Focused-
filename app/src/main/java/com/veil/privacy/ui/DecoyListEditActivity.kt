package com.veil.privacy.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivityDecoyListEditBinding
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.ui.adapter.DecoyTextAdapter
import com.veil.privacy.util.VeilDialogs

class DecoyListEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_NAMES = 1
        const val MODE_MESSAGES = 2
    }

    private lateinit var binding: ActivityDecoyListEditBinding
    private lateinit var prefs: VeilPrefs
    private lateinit var adapter: DecoyTextAdapter
    private var mode = MODE_NAMES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDecoyListEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.veil.privacy.util.InsetsUtils.applySystemBarInsets(binding.root)

        prefs = VeilPrefs.getInstance(this)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_NAMES)

        setupUI()
        setupListeners()
        loadData()
    }

    private fun setupUI() {
        if (mode == MODE_NAMES) {
            binding.tvDecoyEditTitle.text = getString(R.string.decoy_edit_names_title)
            binding.tilDecoyInput.hint = getString(R.string.decoy_edit_add_name_hint)
        } else {
            binding.tvDecoyEditTitle.text = getString(R.string.decoy_edit_messages_title)
            binding.tilDecoyInput.hint = getString(R.string.decoy_edit_add_message_hint)
        }

        adapter = DecoyTextAdapter(emptyList()) { itemToDelete ->
            deleteItem(itemToDelete)
        }

        binding.rvDecoyItems.layoutManager = LinearLayoutManager(this)
        binding.rvDecoyItems.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddDecoyItem.setOnClickListener {
            val text = binding.etDecoyInput.text?.toString()?.trim() ?: ""
            if (text.isBlank()) return@setOnClickListener

            val success = if (mode == MODE_NAMES) {
                prefs.addDecoyName(text)
            } else {
                prefs.addDecoyMessage(text)
            }

            if (success) {
                binding.etDecoyInput.text?.clear()
                loadData()
            } else {
                Toast.makeText(this, "Item already exists or is empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnResetDefaults.setOnClickListener {
            VeilDialogs.showConfirmationDialog(
                context = this,
                title = getString(R.string.decoy_reset_defaults),
                message = getString(R.string.decoy_reset_confirm),
                confirmText = "Reset Pool",
                isDanger = true,
                onConfirm = {
                    if (mode == MODE_NAMES) {
                        prefs.resetDecoyNames()
                    } else {
                        prefs.resetDecoyMessages()
                    }
                    loadData()
                    Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun loadData() {
        val list = if (mode == MODE_NAMES) {
            prefs.getDecoyNames()
        } else {
            prefs.getDecoyMessages()
        }
        adapter.updateItems(list)
    }

    private fun deleteItem(item: String) {
        val list = if (mode == MODE_NAMES) prefs.getDecoyNames() else prefs.getDecoyMessages()
        if (list.size <= 1) {
            Toast.makeText(this, "You must keep at least one decoy in the pool", Toast.LENGTH_SHORT).show()
            return
        }

        if (mode == MODE_NAMES) {
            prefs.removeDecoyName(item)
        } else {
            prefs.removeDecoyMessage(item)
        }
        loadData()
    }
}

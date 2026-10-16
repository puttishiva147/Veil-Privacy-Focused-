package com.veil.privacy.ui

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivityEventDetailBinding
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.storage.VeilInbox
import com.veil.privacy.util.TimeUtils
import com.veil.privacy.util.VeilDialogs
import kotlinx.coroutines.launch

class EventDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
    }

    private lateinit var binding: ActivityEventDetailBinding
    private lateinit var inbox: VeilInbox
    private var currentEvent: ProtectedEvent? = null
    private var isRevealed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.veil.privacy.util.InsetsUtils.applySystemBarInsets(binding.root)

        inbox = VeilInbox.getInstance(this)

        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId == -1L) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadEvent(eventId)
        setupListeners()
    }

    private fun loadEvent(eventId: Long) {
        lifecycleScope.launch {
            val event = inbox.getEventById(eventId)
            if (event == null) {
                Toast.makeText(this@EventDetailActivity, "Event not found", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            currentEvent = event
            updateUI(event)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnDeleteEventBottom.setOnClickListener { confirmDelete() }
        binding.btnDeleteDetailEvent.setOnClickListener { confirmDelete() }

        // Double-tap gesture detector inside VEIL's own application UI
        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    isRevealed = !isRevealed
                    currentEvent?.let { updateUI(it) }
                    return true
                }
            }
        )

        binding.cardEventContainer.setOnTouchListener { v, event ->
            v.performClick()
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun updateUI(event: ProtectedEvent) {
        binding.tvDetailTimestamp.text = TimeUtils.formatFullDateTime(event.timestamp)
        binding.tvDetailSource.text = "${event.sourcePackage} (Encrypted On-Device)"

        if (isRevealed) {
            // REVEALED REAL IDENTITY
            binding.tvDetailModeBadge.text = getString(R.string.inbox_revealed_badge)
            binding.tvDetailModeBadge.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_secure))
            binding.tvDetailModeBadge.setBackgroundResource(R.drawable.bg_badge_revealed)
            binding.ivDetailEye.visibility = View.VISIBLE

            binding.tvDetailSenderLabel.text = getString(R.string.detail_real_sender_label)
            binding.tvDetailSenderLabel.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_secure))
            binding.tvDetailSenderValue.text = event.realSender

            binding.tvDetailMessageLabel.text = getString(R.string.detail_real_message_label)
            binding.tvDetailMessageLabel.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_secure))
            binding.tvDetailMessageValue.text = event.realMessage
        } else {
            // DISGUISED DECOY IDENTITY
            binding.tvDetailModeBadge.text = getString(R.string.inbox_disguised_badge)
            binding.tvDetailModeBadge.setTextColor(ContextCompat.getColor(this, R.color.veil_text_medium))
            binding.tvDetailModeBadge.setBackgroundResource(R.drawable.bg_badge_disguised)
            binding.ivDetailEye.visibility = View.GONE

            binding.tvDetailSenderLabel.text = getString(R.string.detail_decoy_sender_label)
            binding.tvDetailSenderLabel.setTextColor(ContextCompat.getColor(this, R.color.veil_text_low))
            binding.tvDetailSenderValue.text = event.decoySender

            binding.tvDetailMessageLabel.text = getString(R.string.detail_decoy_message_label)
            binding.tvDetailMessageLabel.setTextColor(ContextCompat.getColor(this, R.color.veil_text_low))
            binding.tvDetailMessageValue.text = event.decoyMessage
        }
    }

    private fun confirmDelete() {
        val event = currentEvent ?: return
        VeilDialogs.showConfirmationDialog(
            context = this,
            title = getString(R.string.action_delete),
            message = "Permanently remove this event from your private inbox?",
            confirmText = "Delete",
            isDanger = true,
            onConfirm = {
                lifecycleScope.launch {
                    inbox.deleteEvent(event.id)
                    Toast.makeText(this@EventDetailActivity, "Event deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        )
    }
}

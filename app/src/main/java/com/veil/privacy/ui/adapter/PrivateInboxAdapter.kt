package com.veil.privacy.ui.adapter

import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.veil.privacy.R
import com.veil.privacy.databinding.ItemInboxEventBinding
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.util.TimeUtils

class PrivateInboxAdapter(
    private val onItemClicked: (ProtectedEvent) -> Unit,
    private val onDeleteClicked: (ProtectedEvent) -> Unit
) : ListAdapter<ProtectedEvent, PrivateInboxAdapter.ViewHolder>(DiffCallback) {

    // Set of event IDs currently revealed by double-tap in the list
    private val revealedEventIds = mutableSetOf<Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInboxEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemInboxEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: ProtectedEvent) {
            val isRevealed = revealedEventIds.contains(event.id)

            binding.tvEventTimestamp.text = TimeUtils.formatRelativeTime(event.timestamp)

            if (isRevealed) {
                // Show real information
                binding.tvEventSender.text = event.realSender
                binding.tvEventMessage.text = event.realMessage
                binding.tvStatusBadge.text = binding.root.context.getString(R.string.inbox_revealed_badge)
                binding.tvStatusBadge.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.veil_accent_secure)
                )
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_revealed)
                binding.ivEyeIndicator.setImageResource(R.drawable.ic_revealed_eye)
                binding.ivEyeIndicator.visibility = View.VISIBLE

                val firstChar = event.realSender.trim().firstOrNull()?.uppercaseChar()
                if (firstChar == 'V' || event.realSender.trim().equals("VEIL", ignoreCase = true)) {
                    binding.tvEventAvatarInitial.visibility = View.GONE
                    binding.ivEventAvatarVeilLogo.visibility = View.VISIBLE
                } else {
                    binding.tvEventAvatarInitial.visibility = View.VISIBLE
                    binding.ivEventAvatarVeilLogo.visibility = View.GONE
                    binding.tvEventAvatarInitial.text = (firstChar ?: 'P').toString()
                }
                binding.ivEventProfileIndicator.imageTintList = ContextCompat.getColorStateList(binding.root.context, R.color.veil_accent_secure)
                binding.ivEventProfileIndicator.contentDescription = "Real Identity Revealed"
            } else {
                // Show disguised decoy information
                binding.tvEventSender.text = event.decoySender
                binding.tvEventMessage.text = event.decoyMessage
                binding.tvStatusBadge.text = binding.root.context.getString(R.string.inbox_disguised_badge)
                binding.tvStatusBadge.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.veil_text_medium)
                )
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_disguised)
                binding.ivEyeIndicator.visibility = View.GONE

                val firstChar = event.decoySender.trim().firstOrNull()?.uppercaseChar()
                if (firstChar == 'V' || event.decoySender.trim().equals("VEIL", ignoreCase = true)) {
                    binding.tvEventAvatarInitial.visibility = View.GONE
                    binding.ivEventAvatarVeilLogo.visibility = View.VISIBLE
                } else {
                    binding.tvEventAvatarInitial.visibility = View.VISIBLE
                    binding.ivEventAvatarVeilLogo.visibility = View.GONE
                    binding.tvEventAvatarInitial.text = (firstChar ?: 'D').toString()
                }
                binding.ivEventProfileIndicator.imageTintList = ContextCompat.getColorStateList(binding.root.context, R.color.veil_accent_titanium)
                binding.ivEventProfileIndicator.contentDescription = "Protected Disguised Identity"
            }

            // Gesture detector for single tap (open detail) and double tap (reveal/conceal)
            val gestureDetector = GestureDetector(
                binding.root.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        onItemClicked(event)
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (revealedEventIds.contains(event.id)) {
                            revealedEventIds.remove(event.id)
                        } else {
                            revealedEventIds.add(event.id)
                        }
                        val pos = adapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            notifyItemChanged(pos)
                        }
                        return true
                    }
                }
            )

            binding.root.setOnTouchListener { v, motionEvent ->
                v.performClick()
                gestureDetector.onTouchEvent(motionEvent)
            }

            binding.btnDeleteEvent.setOnClickListener {
                onDeleteClicked(event)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ProtectedEvent>() {
        override fun areItemsTheSame(oldItem: ProtectedEvent, newItem: ProtectedEvent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProtectedEvent, newItem: ProtectedEvent): Boolean {
            return oldItem == newItem
        }
    }
}

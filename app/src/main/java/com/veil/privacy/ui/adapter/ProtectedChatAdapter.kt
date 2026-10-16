package com.veil.privacy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.veil.privacy.R
import com.veil.privacy.model.ProtectedEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for displaying VEIL Protected Chat conversation threads.
 * Distinguishes incoming WhatsApp messages from outgoing replies dispatched via RemoteInput.
 */
class ProtectedChatAdapter : ListAdapter<ProtectedEvent, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val VIEW_TYPE_INCOMING = 1
        private const val VIEW_TYPE_OUTGOING = 2

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ProtectedEvent>() {
            override fun areItemsTheSame(oldItem: ProtectedEvent, newItem: ProtectedEvent): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ProtectedEvent, newItem: ProtectedEvent): Boolean {
                return oldItem == newItem
            }
        }

        private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val DATE_FORMAT = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item.isOutgoing) VIEW_TYPE_OUTGOING else VIEW_TYPE_INCOMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_OUTGOING) {
            val view = inflater.inflate(R.layout.item_chat_bubble_outgoing, parent, false)
            OutgoingViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_bubble_incoming, parent, false)
            IncomingViewHolder(view)
        }
    }

    // VEIL Chat Privacy: Track revealed messages. Hidden by default!
    private val revealedMessageIds = mutableSetOf<Long>()

    fun resetRevealedStates() {
        if (revealedMessageIds.isNotEmpty()) {
            revealedMessageIds.clear()
            notifyDataSetChanged()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is IncomingViewHolder) {
            holder.bind(item)
        } else if (holder is OutgoingViewHolder) {
            holder.bind(item)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return if (diff < 24 * 3600 * 1000L) {
            TIME_FORMAT.format(Date(timestamp))
        } else {
            DATE_FORMAT.format(Date(timestamp))
        }
    }

    inner class IncomingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView = itemView.findViewById(R.id.tvIncomingSender)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvIncomingMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvIncomingTime)
        private val tvSourceApp: TextView? = itemView.findViewById(R.id.tvIncomingSourceApp)
        private val tvAvatarInitial: TextView? = itemView.findViewById(R.id.tvIncomingAvatarInitial)
        private val ivAvatarVeilLogo: android.widget.ImageView? = itemView.findViewById(R.id.ivIncomingAvatarVeilLogo)
        private val ivProfileIndicator: android.widget.ImageView? = itemView.findViewById(R.id.ivIncomingProfileIndicator)

        fun bind(event: ProtectedEvent) {
            val isRevealed = revealedMessageIds.contains(event.id)

            if (isRevealed) {
                // Revealed only after deliberate double-tap on this specific message
                tvSender.text = event.realSender
                tvMessage.text = event.realMessage
                val appName = com.veil.privacy.model.SupportedApp.getAppDisplayName(event.sourcePackage)
                tvSourceApp?.text = "• $appName (Revealed)"
            } else {
                // Kept hidden by default! Decoy sender and decoy message
                tvSender.text = event.decoySender.ifBlank { "Protected Contact" }
                tvMessage.text = event.decoyMessage.ifBlank { "••••••••••••" }
                tvSourceApp?.text = "• Double-tap to reveal"
            }

            tvTime.text = formatTimestamp(event.timestamp)

            val displaySender = if (isRevealed) event.realSender else event.decoySender
            val firstChar = displaySender.trim().firstOrNull()?.uppercaseChar()
            if (firstChar == 'V' || displaySender.trim().equals("VEIL", ignoreCase = true)) {
                tvAvatarInitial?.visibility = View.GONE
                ivAvatarVeilLogo?.visibility = View.VISIBLE
            } else {
                tvAvatarInitial?.visibility = View.VISIBLE
                ivAvatarVeilLogo?.visibility = View.GONE
                tvAvatarInitial?.text = (firstChar ?: 'P').toString()
            }
            ivProfileIndicator?.imageTintList = androidx.core.content.ContextCompat.getColorStateList(
                itemView.context,
                R.color.veil_accent_secure
            )

            // Deliberate double-tap gesture recognizer
            // A single tap MUST NEVER reveal the original message
            val gestureDetector = android.view.GestureDetector(
                itemView.context,
                object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: android.view.MotionEvent): Boolean = true

                    override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                        // A single tap must never reveal original content
                        return false
                    }

                    override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                        val pos = this@IncomingViewHolder.adapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            if (revealedMessageIds.contains(event.id)) {
                                revealedMessageIds.remove(event.id)
                            } else {
                                revealedMessageIds.add(event.id)
                            }
                            notifyItemChanged(pos)
                        }
                        return true
                    }
                }
            )

            itemView.setOnTouchListener { v, motionEvent ->
                val handled = gestureDetector.onTouchEvent(motionEvent)
                if (motionEvent.action == android.view.MotionEvent.ACTION_UP && !handled) {
                    v.performClick()
                }
                handled
            }
        }
    }

    inner class OutgoingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvOutgoingMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvOutgoingTime)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvOutgoingStatus)

        fun bind(event: ProtectedEvent) {
            tvMessage.text = event.realMessage
            tvTime.text = formatTimestamp(event.timestamp)
            tvStatus.text = if (event.replyStatus == "SENT_VIA_REMOTE_INPUT") {
                "✓ Sent via RemoteInput"
            } else {
                "Sent"
            }
        }
    }
}

package com.veil.privacy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.veil.privacy.R
import com.veil.privacy.model.ProtectedChatThread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProtectedChatThreadsAdapter(
    private val onThreadClick: (ProtectedChatThread) -> Unit
) : ListAdapter<ProtectedChatThread, ProtectedChatThreadsAdapter.ThreadViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ProtectedChatThread>() {
            override fun areItemsTheSame(oldItem: ProtectedChatThread, newItem: ProtectedChatThread): Boolean {
                return oldItem.contactName == newItem.contactName
            }

            override fun areContentsTheSame(oldItem: ProtectedChatThread, newItem: ProtectedChatThread): Boolean {
                return oldItem == newItem
            }
        }

        private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val DATE_FORMAT = SimpleDateFormat("MMM d", Locale.getDefault())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_protected_chat_thread, parent, false)
        return ThreadViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ThreadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvInitial: TextView = itemView.findViewById(R.id.tvAvatarInitial)
        private val ivVeilLogo: android.widget.ImageView = itemView.findViewById(R.id.ivAvatarVeilLogo)
        private val ivProfileIndicator: android.widget.ImageView = itemView.findViewById(R.id.ivProfileIndicator)
        private val tvName: TextView = itemView.findViewById(R.id.tvThreadName)
        private val tvTime: TextView = itemView.findViewById(R.id.tvThreadTime)
        private val tvSnippet: TextView = itemView.findViewById(R.id.tvThreadSnippet)
        private val tvReplyStatus: TextView = itemView.findViewById(R.id.tvReplyStatusPill)
        private val tvDisguiseBadge: TextView = itemView.findViewById(R.id.tvDisguiseBadge)

        fun bind(thread: ProtectedChatThread) {
            val firstChar = thread.contactName.trim().firstOrNull()?.uppercaseChar()
            if (firstChar == 'V' || thread.contactName.trim().equals("VEIL", ignoreCase = true)) {
                // Whenever 'V' is available, attach the VEIL logo and do not use simple 'V'
                tvInitial.visibility = View.GONE
                ivVeilLogo.visibility = View.VISIBLE
            } else {
                tvInitial.visibility = View.VISIBLE
                ivVeilLogo.visibility = View.GONE
                tvInitial.text = (firstChar ?: 'P').toString()
            }

            // Profile Icon Indicator
            if (thread.isLiveReplyAvailable) {
                ivProfileIndicator.imageTintList = ContextCompat.getColorStateList(itemView.context, R.color.veil_accent_secure)
                ivProfileIndicator.contentDescription = "Profile Protected • Live Reply Active"
            } else {
                ivProfileIndicator.imageTintList = ContextCompat.getColorStateList(itemView.context, R.color.veil_accent_titanium)
                ivProfileIndicator.contentDescription = "Profile Protected"
            }
            tvName.text = thread.contactName

            if (thread.lastTimestamp > 0) {
                val now = System.currentTimeMillis()
                val diff = now - thread.lastTimestamp
                tvTime.text = if (diff < 24 * 3600 * 1000L) {
                    TIME_FORMAT.format(Date(thread.lastTimestamp))
                } else {
                    DATE_FORMAT.format(Date(thread.lastTimestamp))
                }
            } else {
                tvTime.text = "Protected"
            }

            tvSnippet.text = thread.lastMessage.ifBlank { "No messages captured yet" }

            if (thread.isLiveReplyAvailable) {
                tvReplyStatus.text = "🟢 Live Reply Ready"
                tvReplyStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.veil_accent_secure))
                tvReplyStatus.visibility = View.VISIBLE
            } else {
                tvReplyStatus.text = "Token Inactive"
                tvReplyStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.veil_text_low))
                tvReplyStatus.visibility = View.VISIBLE
            }

            tvDisguiseBadge.text = "🔒 " + if (thread.decoyIdentity.isNotBlank()) thread.decoyIdentity else "VEIL"

            itemView.setOnClickListener {
                onThreadClick(thread)
            }
        }
    }
}

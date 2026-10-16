package com.veil.privacy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.veil.privacy.databinding.ItemProtectedContactBinding
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.util.TimeUtils

class ProtectedContactAdapter(
    private val onToggleEnabled: (ProtectedContact, Boolean) -> Unit,
    private val onDeleteClicked: (ProtectedContact) -> Unit,
    private val onItemClick: ((ProtectedContact) -> Unit)? = null
) : ListAdapter<ProtectedContact, ProtectedContactAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProtectedContactBinding.inflate(
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
        private val binding: ItemProtectedContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: ProtectedContact) {
            binding.tvContactName.text = contact.displayName

            val firstChar = contact.displayName.trim().firstOrNull()?.uppercaseChar()
            if (firstChar == 'V' || contact.displayName.trim().equals("VEIL", ignoreCase = true)) {
                binding.tvContactInitial.visibility = View.GONE
                binding.ivContactVeilLogo.visibility = View.VISIBLE
            } else {
                binding.tvContactInitial.visibility = View.VISIBLE
                binding.ivContactVeilLogo.visibility = View.GONE
                binding.tvContactInitial.text = (firstChar ?: 'C').toString()
            }

            if (contact.isEnabled) {
                binding.ivContactIndicator.imageTintList = androidx.core.content.ContextCompat.getColorStateList(
                    binding.root.context,
                    com.veil.privacy.R.color.veil_accent_secure
                )
                binding.ivContactIndicator.contentDescription = "Active Protected Profile"
            } else {
                binding.ivContactIndicator.imageTintList = androidx.core.content.ContextCompat.getColorStateList(
                    binding.root.context,
                    com.veil.privacy.R.color.veil_accent_titanium
                )
                binding.ivContactIndicator.contentDescription = "Protection Inactive"
            }

            if (contact.selectedPhoneNumber.isNotBlank()) {
                binding.tvContactPhone.text = contact.selectedPhoneNumber
                binding.tvContactPhone.visibility = View.VISIBLE
            } else {
                binding.tvContactPhone.visibility = View.GONE
            }

            binding.tvContactSourceBadge.text = when (contact.source) {
                ProtectedContact.SOURCE_ANDROID_CONTACTS -> "DEVICE CONTACT"
                ProtectedContact.SOURCE_SENSOR_LEARNED -> "SENSOR LEARNED"
                else -> "DIRECT NUMBER"
            }

            binding.tvContactAddedDate.text = "• Added ${TimeUtils.formatRelativeTime(contact.addedTimestamp)}"

            // Temporarily detach listener to avoid re-triggering during recycling
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = contact.isEnabled

            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggleEnabled(contact, isChecked)
            }

            binding.btnDeleteContact.setOnClickListener {
                onDeleteClicked(contact)
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(contact)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ProtectedContact>() {
        override fun areItemsTheSame(oldItem: ProtectedContact, newItem: ProtectedContact): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProtectedContact, newItem: ProtectedContact): Boolean {
            return oldItem == newItem
        }
    }
}

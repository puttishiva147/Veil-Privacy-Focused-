package com.veil.privacy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.veil.privacy.R
import com.veil.privacy.databinding.ItemProtectionRuleBinding
import com.veil.privacy.model.ProtectionRule

class ProtectionRuleAdapter(
    private val onRuleClick: (ProtectionRule) -> Unit,
    private val onToggleEnabled: (ProtectionRule, Boolean) -> Unit
) : ListAdapter<ProtectionRule, ProtectionRuleAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProtectionRuleBinding.inflate(
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
        private val binding: ItemProtectionRuleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: ProtectionRule) {
            val firstChar = rule.contactName.trim().firstOrNull()?.uppercaseChar()
            if (firstChar == 'V' || rule.contactName.trim().equals("VEIL", ignoreCase = true)) {
                binding.ivRuleIcon.setImageResource(R.drawable.ic_launcher_foreground)
                binding.ivRuleIcon.imageTintList = null
                binding.ivRuleIcon.setPadding(0, 0, 0, 0)
            } else {
                binding.ivRuleIcon.setImageResource(R.drawable.ic_person_protect)
                binding.ivRuleIcon.imageTintList = androidx.core.content.ContextCompat.getColorStateList(binding.root.context, R.color.veil_accent_secure)
                val pad = (9 * binding.root.resources.displayMetrics.density).toInt()
                binding.ivRuleIcon.setPadding(pad, pad, pad, pad)
            }

            binding.tvRuleContactName.text = "« ${rule.contactName} »"

            if (rule.contactPhoneNumber.isNotBlank()) {
                binding.tvRulePhoneNumber.text = rule.contactPhoneNumber
                binding.tvRulePhoneNumber.visibility = View.VISIBLE
            } else {
                binding.tvRulePhoneNumber.visibility = View.GONE
            }

            binding.tvRuleDecoyIdentity.text = rule.decoyIdentity
            binding.tvRuleDecoyMessage.text = "— \"${rule.decoyMessage}\""

            // Temporarily detach listener to avoid re-triggering during recycling
            binding.switchRuleEnabled.setOnCheckedChangeListener(null)
            binding.switchRuleEnabled.isChecked = rule.isEnabled

            binding.switchRuleEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggleEnabled(rule, isChecked)
            }

            binding.root.setOnClickListener {
                onRuleClick(rule)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ProtectionRule>() {
        override fun areItemsTheSame(oldItem: ProtectionRule, newItem: ProtectionRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProtectionRule, newItem: ProtectionRule): Boolean {
            return oldItem == newItem
        }
    }
}

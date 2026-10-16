package com.veil.privacy.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.veil.privacy.databinding.ItemDecoyTextBinding

class DecoyTextAdapter(
    private var items: List<String>,
    private val onDeleteClicked: (String) -> Unit
) : RecyclerView.Adapter<DecoyTextAdapter.ViewHolder>() {

    fun updateItems(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDecoyTextBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemDecoyTextBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(text: String) {
            binding.tvDecoyContent.text = text
            binding.btnDeleteDecoy.setOnClickListener {
                onDeleteClicked(text)
            }
        }
    }
}

package com.veil.privacy.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivityPrivateInboxBinding
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.storage.VeilInbox
import com.veil.privacy.ui.adapter.PrivateInboxAdapter
import com.veil.privacy.util.VeilDialogs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PrivateInboxActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateInboxBinding
    private lateinit var inbox: VeilInbox
    private lateinit var adapter: PrivateInboxAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivateInboxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.veil.privacy.util.InsetsUtils.applySystemBarInsets(binding.root)

        inbox = VeilInbox.getInstance(this)

        setupRecyclerView()
        setupListeners()
        observeEvents()
    }

    private fun setupRecyclerView() {
        adapter = PrivateInboxAdapter(
            onItemClicked = { event ->
                val intent = Intent(this, EventDetailActivity::class.java).apply {
                    putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)
                }
                startActivity(intent)
            },
            onDeleteClicked = { event ->
                showDeleteEventDialog(event)
            }
        )

        binding.rvInboxEvents.layoutManager = LinearLayoutManager(this)
        binding.rvInboxEvents.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnClearInbox.setOnClickListener {
            showClearAllDialog()
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            inbox.getAllEventsFlow().collectLatest { events ->
                adapter.submitList(events)
                val count = events.size

                binding.tvInboxCountSubtitle.text = "$count event${if (count == 1) "" else "s"} stored"

                if (events.isEmpty()) {
                    binding.rvInboxEvents.visibility = View.GONE
                    binding.layoutEmptyInbox.visibility = View.VISIBLE
                    binding.btnClearInbox.visibility = View.GONE
                } else {
                    binding.rvInboxEvents.visibility = View.VISIBLE
                    binding.layoutEmptyInbox.visibility = View.GONE
                    binding.btnClearInbox.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showDeleteEventDialog(event: ProtectedEvent) {
        VeilDialogs.showConfirmationDialog(
            context = this,
            title = getString(R.string.action_delete),
            message = "Permanently remove this protected event from your vault?",
            confirmText = "Delete",
            isDanger = true,
            onConfirm = {
                lifecycleScope.launch {
                    inbox.deleteEvent(event.id)
                }
            }
        )
    }

    private fun showClearAllDialog() {
        VeilDialogs.showConfirmationDialog(
            context = this,
            title = getString(R.string.inbox_clear_all),
            message = getString(R.string.inbox_clear_confirm),
            confirmText = "Purge All",
            isDanger = true,
            onConfirm = {
                lifecycleScope.launch {
                    inbox.clearAll()
                }
            }
        )
    }
}

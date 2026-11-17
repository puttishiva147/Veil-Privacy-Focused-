package com.veil.privacy.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.veil.privacy.vupl.VuplEngine
import com.veil.privacy.vupl.VuplSecretStore

class VuplMessengerActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var envelope: EditText
    private var engine: VuplEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val secret = VuplSecretStore.load(this)
        if (secret == null) {
            Toast.makeText(this, "Pair this phone first in VUPL Device Pairing", Toast.LENGTH_LONG).show()
            finish(); return
        }
        try { engine = VuplEngine.openPair(this, secret) }
        catch (e: Exception) {
            Toast.makeText(this, "VUPL dictionary could not load: ${e.message}", Toast.LENGTH_LONG).show()
            finish(); return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 22)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "VUPL MESSENGER"; textSize = 20f; setTextColor(Color.BLACK); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Messages are encrypted on this phone. Only the paired device can decrypt them. VEIL does not relay or store them on a server."
            textSize = 13f; setTextColor(Color.DKGRAY); setPadding(0, 12, 0, 16)
        })
        input = EditText(this).apply { hint = "Write a message to encrypt"; minLines = 3; gravity = Gravity.TOP; setTextColor(Color.BLACK) }
        root.addView(input, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply { text = "Encrypt & Share"; setOnClickListener { encryptAndShare() } })
        root.addView(TextView(this).apply {
            text = "Received encrypted envelope"; textSize = 14f; setTextColor(Color.BLACK); setPadding(0, 18, 0, 4)
        })
        envelope = EditText(this).apply { hint = "Paste or share a VEILX/2 envelope here"; minLines = 3; gravity = Gravity.TOP; setTextColor(Color.BLACK) }
        root.addView(envelope, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply { text = "Decrypt Message"; setOnClickListener { decryptIncoming() } })
        output = TextView(this).apply { textSize = 16f; setTextColor(Color.BLACK); setPadding(0, 14, 0, 14) }
        root.addView(output)
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        setContentView(root)
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain")
            envelope.setText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
    }

    private fun encryptAndShare() {
        val text = input.text?.toString().orEmpty()
        if (text.isEmpty()) { input.error = "Enter a message first"; return }
        try {
            val encrypted = engine!!.sendMessage(text)
            envelope.setText(encrypted)
            val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, encrypted) }
            startActivity(Intent.createChooser(share, "Send encrypted VUPL message"))
        } catch (e: Exception) { Toast.makeText(this, "Encryption failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun decryptIncoming() {
        try {
            val value = envelope.text?.toString()?.trim().orEmpty()
            require(value.isNotEmpty()) { "Paste the encrypted envelope first" }
            output.text = engine!!.receiveMessage(value)
        } catch (e: Exception) {
            output.text = ""
            Toast.makeText(this, "Cannot decrypt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

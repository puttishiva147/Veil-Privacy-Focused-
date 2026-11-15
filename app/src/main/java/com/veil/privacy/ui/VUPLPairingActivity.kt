package com.veil.privacy.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.integration.android.IntentIntegrator
import com.veil.privacy.vupl.PairSecretStore
import com.veil.privacy.vupl.VUPL
import com.veil.privacy.vupl.VUPLRuntime

class VUPLPairingActivity : AppCompatActivity() {
    private lateinit var store: PairSecretStore
    private lateinit var qrImage: ImageView
    private lateinit var status: TextView
    private lateinit var secretHint: TextView

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val scan = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        val value = scan?.contents?.trim().orEmpty()
        if (!value.matches(Regex("[0-9a-fA-F]{64}"))) {
            Toast.makeText(this, "Invalid VEIL pairing QR. Expected a 32-byte secret.", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        store.save(VUPLRuntime.DEFAULT_PAIR_ID, value.lowercase())
        VUPLRuntime.clearCache()
        showStatus()
        Toast.makeText(this, "VUPL pair saved securely on this device", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = PairSecretStore(this)

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(android.graphics.Color.rgb(12, 12, 12))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "VUPL PAIRING"
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Pair two VEIL phones once, in person. No login, account, or server is involved. Both phones derive the same private language and encryption key from the QR secret."
            textSize = 14f
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, dp(10), 0, dp(16))
        })
        status = TextView(this).apply { textSize = 14f; setTextColor(android.graphics.Color.WHITE) }
        root.addView(status)
        secretHint = TextView(this).apply {
            text = "The QR contains a private 32-byte pairing secret. Show it only to the other phone in person. Do not send or photograph it."
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, dp(10), 0, dp(12))
        }
        root.addView(secretHint)
        qrImage = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = ImageView.GONE
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(qrImage, LinearLayout.LayoutParams(-1, dp(280)).apply { bottomMargin = dp(14) })

        root.addView(actionButton("Generate new pairing QR") { generateAndShow() })
        root.addView(actionButton("Show saved pairing QR") { showSavedQr() })
        root.addView(actionButton("Scan another phone's QR") { startScan() })
        root.addView(actionButton("Remove pairing") { confirmRemove() })
        root.addView(actionButton("Back") { finish() })
        showStatus()
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun generateAndShow() {
        val secret = VUPL.createPairSecret()
        store.save(VUPLRuntime.DEFAULT_PAIR_ID, secret)
        VUPLRuntime.clearCache()
        renderQr(secret)
        showStatus()
    }

    private fun showSavedQr() {
        val secret = store.get(VUPLRuntime.DEFAULT_PAIR_ID)
        if (secret == null) {
            Toast.makeText(this, "No pair exists yet. Generate a QR or scan another phone.", Toast.LENGTH_LONG).show()
        } else renderQr(secret)
    }

    private fun renderQr(secret: String) {
        try {
            val bitmap: Bitmap = BarcodeEncoder().encodeBitmap(secret, BarcodeFormat.QR_CODE, 900, 900)
            qrImage.setImageBitmap(bitmap)
            qrImage.visibility = ImageView.VISIBLE
            secretHint.text = "QR ready. Keep both phones physically present. The secret is stored in encrypted local preferences."
        } catch (e: Exception) {
            Toast.makeText(this, "Could not generate QR: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startScan() {
        val intent = IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Scan the other VEIL phone's pairing QR")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .createScanIntent()
        scanLauncher.launch(intent)
    }

    private fun confirmRemove() {
        AlertDialog.Builder(this)
            .setTitle("Remove VUPL pairing?")
            .setMessage("This device will no longer decrypt messages from the paired phone. The other phone must also be paired again with a new QR.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                store.remove(VUPLRuntime.DEFAULT_PAIR_ID)
                VUPLRuntime.clearCache()
                qrImage.setImageDrawable(null)
                qrImage.visibility = ImageView.GONE
                showStatus()
            }.show()
    }

    private fun showStatus() {
        val paired = store.get(VUPLRuntime.DEFAULT_PAIR_ID) != null
        status.text = if (paired) "STATUS: PAIRED — secret is stored locally" else "STATUS: NOT PAIRED"
    }
}

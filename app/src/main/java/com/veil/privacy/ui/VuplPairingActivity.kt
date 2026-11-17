package com.veil.privacy.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.qrcode.QRCodeWriter
import com.veil.privacy.vupl.VuplEngine
import com.veil.privacy.vupl.VuplSecretStore

class VuplPairingActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var qr: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "VUPL DEVICE PAIRING"
            textSize = 20f
            setTextColor(android.graphics.Color.BLACK)
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(android.graphics.Color.DKGRAY)
            gravity = Gravity.CENTER
            text = "Pair two VEIL devices in person. Never share this QR through chat."
            setPadding(0, 20, 0, 16)
        }
        root.addView(status)
        qr = ImageView(this).apply { adjustViewBounds = true; visibility = android.view.View.GONE }
        root.addView(qr, LinearLayout.LayoutParams(640, 640))
        root.addView(Button(this).apply {
            text = "Generate pairing QR (this phone)"
            setOnClickListener { generateQr() }
        })
        root.addView(Button(this).apply {
            text = "Scan pairing QR (other phone)"
            setOnClickListener {
                IntentIntegrator(this@VuplPairingActivity)
                    .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                    .setPrompt("Scan the QR shown on the other VEIL phone")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .initiateScan()
            }
        })
        root.addView(Button(this).apply {
            text = "Open VUPL Messenger"
            setOnClickListener { startActivity(android.content.Intent(this@VuplPairingActivity, VuplMessengerActivity::class.java)) }
        })
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        setContentView(root)
        updateStatus()
    }

    private fun generateQr() {
        try {
            val secret = VuplEngine.createPairSecret()
            // Verify the bundled dictionary before persisting a new pairing.
            VuplEngine.openPair(this, secret)
            VuplSecretStore.save(this, secret)
            val matrix = QRCodeWriter().encode("VEIL-VUPL/1:$secret", BarcodeFormat.QR_CODE, 640, 640)
            val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
            for (x in 0 until 640) for (y in 0 until 640) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
            qr.setImageBitmap(bitmap)
            qr.visibility = android.view.View.VISIBLE
            status.text = "QR ready. Keep both phones together and scan directly from this screen."
        } catch (e: Exception) {
            Toast.makeText(this, "VUPL setup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated by Android, retained for ZXing ActivityResult compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            val contents = result.contents
            if (contents.isNullOrBlank()) {
                Toast.makeText(this, "Pairing scan cancelled", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    require(contents.startsWith("VEIL-VUPL/1:")) { "This is not a VEIL VUPL pairing QR" }
                    val secret = contents.removePrefix("VEIL-VUPL/1:")
                    VuplEngine.openPair(this, secret)
                    VuplSecretStore.save(this, secret)
                    qr.visibility = android.view.View.GONE
                    status.text = "Paired successfully. This phone now has the same VUPL pair secret."
                    Toast.makeText(this, "VEIL devices paired", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Pairing failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateStatus() {
        status.text = if (VuplSecretStore.load(this) != null)
            "This phone has a VUPL pair secret. Generate a new QR to replace this pairing, or scan another phone's QR."
        else
            "Not paired yet. Generate a QR on one phone, then scan it with the other phone. Pair in person only."
    }
}

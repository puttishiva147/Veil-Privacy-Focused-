package com.veil.privacy.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.qrcode.QRCodeWriter
import com.veil.privacy.R
import com.veil.privacy.vupl.VuplEngine
import com.veil.privacy.vupl.VuplSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local-only VUPL pairing. Dictionary indexing, QR encoding and Keystore operations
 * run off the main thread so lower-powered phones remain responsive.
 */
class VuplPairingActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var qr: ImageView
    private lateinit var generateButton: MaterialButton
    private lateinit var scanButton: MaterialButton
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(buildScreen())
        updateStatus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildScreen(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(getColor(R.color.veil_canvas))
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val back = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Back"
            minWidth = 0
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(88), dp(44)))
        val heading = TextView(this).apply {
            text = "DEVICE PAIRING"
            textSize = 12f
            letterSpacing = .16f
            setTextColor(getColor(R.color.veil_text_medium))
            setPadding(dp(14), 0, 0, 0)
        }
        header.addView(heading)
        page.addView(header)

        page.addView(TextView(this).apply {
            text = "Connect two VEIL devices"
            textSize = 26f
            setTextColor(getColor(R.color.veil_text_high))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(28), 0, dp(8))
        })
        page.addView(TextView(this).apply {
            text = "Create a private pairing key on one phone, then scan it directly from the second phone. No account or server is involved."
            textSize = 14f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(getColor(R.color.veil_text_medium))
        })

        val statusCard = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(getColor(R.color.veil_surface_card))
            strokeWidth = dp(1)
            setStrokeColor(getColor(R.color.veil_hairline))
            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            inner.addView(TextView(context).apply {
                text = "PAIRING STATUS"
                textSize = 10f
                letterSpacing = .14f
                setTextColor(getColor(R.color.veil_text_low))
            })
            status = TextView(context).apply {
                text = "Checking this device…"
                textSize = 14f
                setTextColor(getColor(R.color.veil_text_high))
                setPadding(0, dp(8), 0, 0)
            }
            inner.addView(status)
            addView(inner)
        }
        page.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })

        val qrCard = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(getColor(R.color.veil_surface))
            strokeWidth = dp(1)
            setStrokeColor(getColor(R.color.veil_hairline))
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(18), dp(18), dp(18), dp(18))
            }
            qr = ImageView(context).apply {
                visibility = View.GONE
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Private VEIL device pairing QR code"
            }
            content.addView(qr, LinearLayout.LayoutParams(dp(248), dp(248)))
            content.addView(TextView(context).apply {
                text = "QR CODE"
                textSize = 10f
                letterSpacing = .16f
                setTextColor(getColor(R.color.veil_text_low))
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            })
            addView(content)
        }
        page.addView(qrCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        generateButton = MaterialButton(this).apply {
            text = "Generate pairing QR"
            isAllCaps = false
            setOnClickListener { generateQr() }
        }
        page.addView(generateButton, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(18) })

        scanButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Scan another device's QR"
            isAllCaps = false
            setOnClickListener {
                if (!busy) {
                    IntentIntegrator(this@VuplPairingActivity)
                        .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                        .setPrompt("Scan the QR shown on the other VEIL phone")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .initiateScan()
                }
            }
        }
        page.addView(scanButton, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })

        page.addView(TextView(this).apply {
            text = "SECURITY NOTE  •  Only scan a QR from a phone physically in front of you. Anyone who obtains this QR can join the same private pair. Keep it off screenshots and messaging apps."
            textSize = 12f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(getColor(R.color.veil_text_low))
            setPadding(dp(2), dp(20), dp(2), 0)
        })

        return ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
    }

    private fun setBusy(value: Boolean, message: String? = null) {
        busy = value
        generateButton.isEnabled = !value
        scanButton.isEnabled = !value
        generateButton.text = if (value) "Preparing secure pairing…" else "Generate pairing QR"
        if (message != null) status.text = message
    }

    private fun generateQr() {
        if (busy) return
        lifecycleScope.launch {
            setBusy(true, "Building the secure dictionary and creating your private key…")
            try {
                val result = withContext(Dispatchers.IO) {
                    val secret = VuplEngine.createPairSecret()
                    // Validate dictionary and derive the pair engine away from the UI thread.
                    VuplEngine.openPair(this@VuplPairingActivity, secret)
                    val matrix = QRCodeWriter().encode(
                        "VEIL-VUPL/1:$secret", BarcodeFormat.QR_CODE, 720, 720
                    )
                    val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
                    for (x in 0 until 720) for (y in 0 until 720) {
                        bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                    VuplSecretStore.save(this@VuplPairingActivity, secret)
                    bitmap
                }
                qr.setImageBitmap(result)
                qr.visibility = View.VISIBLE
                status.text = "Pairing key created on this phone. Keep this screen open and scan the QR with the second VEIL device."
            } catch (error: Exception) {
                status.text = "Pairing could not be created. Your existing pairing has not been intentionally changed."
                Toast.makeText(
                    this@VuplPairingActivity,
                    "Pairing setup failed: ${error.localizedMessage ?: error.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setBusy(false)
            }
        }
    }

    @Deprecated("Deprecated by Android, retained for ZXing ActivityResult compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result == null) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            Toast.makeText(this, "Pairing scan cancelled", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            setBusy(true, "Verifying the pairing key…")
            try {
                withContext(Dispatchers.IO) {
                    require(contents.startsWith("VEIL-VUPL/1:")) { "This is not a VEIL pairing QR" }
                    val secret = contents.removePrefix("VEIL-VUPL/1:")
                    VuplEngine.openPair(this@VuplPairingActivity, secret)
                    VuplSecretStore.save(this@VuplPairingActivity, secret)
                }
                qr.visibility = View.GONE
                status.text = "Paired successfully. This phone and the QR-generating phone now share the same private key."
                Toast.makeText(this@VuplPairingActivity, "VEIL devices paired", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                status.text = "Pairing was not completed. Check that the QR belongs to VEIL and try again."
                Toast.makeText(
                    this@VuplPairingActivity,
                    "Pairing failed: ${error.localizedMessage ?: error.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun updateStatus() {
        lifecycleScope.launch {
            val paired = withContext(Dispatchers.IO) {
                try { VuplSecretStore.load(this@VuplPairingActivity) != null } catch (_: Exception) { false }
            }
            status.text = if (paired) {
                "PAIRED  •  This phone already has a VUPL key. Scanning another QR will replace this pairing."
            } else {
                "NOT PAIRED  •  Generate a QR here, then scan it using the second VEIL phone."
            }
        }
    }
}

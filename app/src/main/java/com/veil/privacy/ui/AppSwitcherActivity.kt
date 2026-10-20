package com.veil.privacy.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.veil.privacy.R
import com.veil.privacy.model.SupportedApp
import com.veil.privacy.storage.VeilPrefs

/**
 * Full-screen app selection page. Selecting a platform changes the dashboard's
 * app scope; each platform's protection switch remains independently persisted.
 */
class AppSwitcherActivity : AppCompatActivity() {
    private lateinit var prefs: VeilPrefs
    private val appRows = listOf(
        AppRow(SupportedApp.PKG_WHATSAPP, "WhatsApp", R.drawable.ic_app_whatsapp),
        AppRow(SupportedApp.PKG_INSTAGRAM, "Instagram", R.drawable.ic_app_instagram),
        AppRow(SupportedApp.PKG_TELEGRAM, "Telegram", R.drawable.ic_app_telegram),
        AppRow(SupportedApp.PKG_SNAPCHAT, "Snapchat", R.drawable.ic_app_snapchat)
    )
    private data class AppRow(val packageName: String, val label: String, val icon: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = VeilPrefs.getInstance(this)
        val state = getSharedPreferences("veil_privacy_prefs", MODE_PRIVATE)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@AppSwitcherActivity, R.color.veil_canvas))
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }
        val title = TextView(this).apply {
            text = "Choose an app"
            textSize = 26f
            setTextColor(ContextCompat.getColor(this@AppSwitcherActivity, R.color.veil_text_high))
        }
        page.addView(title)
        page.addView(TextView(this).apply {
            text = "VEIL shows settings and protected chats for the selected app. Protection switches are independent."
            textSize = 14f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(ContextCompat.getColor(this@AppSwitcherActivity, R.color.veil_text_medium))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
                bottomMargin = dp(18)
            }
        })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val radios = mutableMapOf<String, RadioButton>()
        val rows = mutableMapOf<String, View>()
        val switches = mutableMapOf<String, MaterialSwitch>()
        val selectedNow = {
            state.getString("selected_social_app", SupportedApp.PKG_WHATSAPP)
                ?: SupportedApp.PKG_WHATSAPP
        }

        fun updateSelectionVisuals(selectedPackage: String) {
            rows.forEach { (packageName, rowView) ->
                val selected = packageName == selectedPackage
                rowView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(18).toFloat()
                    setColor(Color.parseColor(if (selected) "#202C3D" else "#151B24"))
                    setStroke(dp(if (selected) 2 else 1), Color.parseColor(if (selected) "#72A7FF" else "#293241"))
                }
                rowView.contentDescription = appRows.firstOrNull { it.packageName == packageName }?.let {
                    "${it.label}${if (selected) ", selected" else ""}"
                }
            }
        }

        appRows.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = ContextCompat.getDrawable(this@AppSwitcherActivity, R.drawable.bg_circle_icon)
                isClickable = true
                isFocusable = true
            }
            val radio = RadioButton(this).apply {
                isChecked = selectedNow() == app.packageName
                contentDescription = "Select ${app.label}"
            }
            radios[app.packageName] = radio
            val icon = android.widget.ImageView(this).apply {
                setImageResource(app.icon)
                contentDescription = "${app.label} icon"
                if (app.packageName == SupportedApp.PKG_WHATSAPP) {
                    // WhatsApp's white glyph needs its familiar green badge to remain
                    // instantly recognizable beside VEIL's monochrome platform glyphs.
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#25D366"))
                    }
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginStart = dp(4)
                    marginEnd = dp(10)
                }
            }
            val label = TextView(this).apply {
                text = app.label
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@AppSwitcherActivity, R.color.veil_text_high))
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
            }
            val toggle = MaterialSwitch(this).apply {
                isChecked = prefs.isSocialAppEnabled(app.packageName)
                contentDescription = "Enable protection for ${app.label}"
                setOnCheckedChangeListener { _, enabled ->
                    prefs.setSocialAppEnabled(app.packageName, enabled)
                }
            }
            toggle.setOnCheckedChangeListener { button, enabled ->
                button.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                prefs.setSocialAppEnabled(app.packageName, enabled)
                button.animate().alpha(0.82f).setDuration(70L).withEndAction {
                    button.animate().alpha(1f).setDuration(100L).start()
                }.start()
            }
            switches[app.packageName] = toggle
            rows[app.packageName] = row
            val choose = {
                row.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                row.animate().cancel()
                row.scaleX = 0.98f
                row.scaleY = 0.98f
                row.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
                state.edit().putString("selected_social_app", app.packageName).apply()
                radios.forEach { (pkg, button) -> button.isChecked = pkg == app.packageName }
                updateSelectionVisuals(app.packageName)
            }
            radio.setOnClickListener { choose() }
            row.setOnClickListener { choose() }
            row.setOnLongClickListener {
                choose()
                true
            }
            row.setOnTouchListener { view, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.99f).scaleY(0.99f).setDuration(80L).start()
                } else if (event.action == android.view.MotionEvent.ACTION_UP ||
                    event.action == android.view.MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                }
                false
            }
            row.addView(radio)
            row.addView(icon)
            row.addView(label)
            row.addView(toggle)
            list.addView(row, LinearLayout.LayoutParams(-1, dp(68)).apply {
                bottomMargin = dp(8)
            })
        }
        updateSelectionVisuals(selectedNow())
        scroll.addView(list, android.view.ViewGroup.LayoutParams(-1, -2))
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val buttons = LinearLayout(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val done = MaterialButton(this).apply {
            text = "Done"
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                it.animate().alpha(0.75f).setDuration(70L).withEndAction {
                    it.animate().alpha(1f).setDuration(100L).start()
                    finish()
                }.start()
            }
        }
        buttons.addView(done, LinearLayout.LayoutParams(-2, dp(48)))
        page.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })
        setContentView(page)
    }
}

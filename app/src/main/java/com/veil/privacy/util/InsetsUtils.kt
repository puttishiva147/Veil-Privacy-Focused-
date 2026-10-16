package com.veil.privacy.util

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Universal WindowInsets utility to guarantee edge-to-edge and system bars padding
 * across all Android device form factors, aspect ratios, display scalings, and API versions.
 */
object InsetsUtils {

    /**
     * Applies system bar (status bar, navigation bar, display cutouts/notches) insets
     * as padding to the given root view, ensuring UI elements and buttons are NEVER
     * overlapped by system bars or cut off by screen cutouts.
     */
    fun applySystemBarInsets(
        targetView: View,
        applyTop: Boolean = true,
        applyBottom: Boolean = true,
        applyLeft: Boolean = true,
        applyRight: Boolean = true
    ) {
        val initialPaddingLeft = targetView.paddingLeft
        val initialPaddingTop = targetView.paddingTop
        val initialPaddingRight = targetView.paddingRight
        val initialPaddingBottom = targetView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(targetView) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                if (applyLeft) initialPaddingLeft + insets.left else initialPaddingLeft,
                if (applyTop) initialPaddingTop + insets.top else initialPaddingTop,
                if (applyRight) initialPaddingRight + insets.right else initialPaddingRight,
                if (applyBottom) initialPaddingBottom + insets.bottom else initialPaddingBottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(targetView)
    }

    /**
     * Helper to apply system bar insets to an Activity's root content view.
     */
    fun applySystemBars(activity: Activity) {
        val rootView = activity.window.decorView.findViewById<View>(android.R.id.content)
        if (rootView != null) {
            applySystemBarInsets(rootView)
        }
    }
}

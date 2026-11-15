package com.veil.privacy.trc.security

import android.app.Activity
import android.view.WindowManager

/** Apply to every TRC Activity before sensitive content is drawn. */
object TrcSecureWindow {
    fun enable(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

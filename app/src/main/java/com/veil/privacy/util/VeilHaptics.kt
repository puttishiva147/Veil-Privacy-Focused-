package com.veil.privacy.util

import android.view.HapticFeedbackConstants
import android.view.View

/** Native touch feedback that respects Android system haptic settings. */
object VeilHaptics {
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    fun selection(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun confirm(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
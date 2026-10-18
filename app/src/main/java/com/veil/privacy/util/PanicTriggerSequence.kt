package com.veil.privacy.util

/**
 * Tracks deliberate volume-key presses while the VEIL dashboard is foreground.
 * This does not intercept hardware keys globally or while another app is visible.
 */
class PanicTriggerSequence(private val windowMillis: Long = 1_500L) {
    private var pressCount = 0
    private var lastPressAt = -1L

    fun onPress(timestampMillis: Long, requiredPressCount: Int): Boolean {
        val required = requiredPressCount.coerceIn(2, 3)
        if (lastPressAt < 0L || timestampMillis < lastPressAt ||
            timestampMillis - lastPressAt > windowMillis
        ) {
            pressCount = 0
        }

        lastPressAt = timestampMillis
        pressCount += 1
        if (pressCount >= required) {
            reset()
            return true
        }
        return false
    }

    fun reset() {
        pressCount = 0
        lastPressAt = -1L
    }
}

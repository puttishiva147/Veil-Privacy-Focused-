package com.veil.privacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.veil.privacy.storage.VeilPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VeilNotificationDisplaySettingTest {

    private lateinit var context: Context
    private lateinit var prefs: VeilPrefs

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        VeilPrefs.resetInstance()
        prefs = VeilPrefs.getInstance(context)
        prefs.protectionNotificationDisplay = VeilPrefs.NOTIF_DISPLAY_ALWAYS_SHOW
        prefs.isVeilEnabled = true
        prefs.isPersistentShieldEnabled = true
    }

    @Test
    fun defaultNotificationDisplay_isAlwaysShow() {
        // Clear preference to verify default
        val sp = context.getSharedPreferences("veil_privacy_prefs", Context.MODE_PRIVATE)
        sp.edit().remove("key_protection_notif_display").commit()

        val freshPrefs = VeilPrefs(context)
        assertEquals(VeilPrefs.NOTIF_DISPLAY_ALWAYS_SHOW, freshPrefs.protectionNotificationDisplay)
    }

    @Test
    fun settingAlwaysHide_persistsAcrossFreshInstances() {
        prefs.protectionNotificationDisplay = VeilPrefs.NOTIF_DISPLAY_ALWAYS_HIDE
        assertEquals(VeilPrefs.NOTIF_DISPLAY_ALWAYS_HIDE, prefs.protectionNotificationDisplay)

        // Verify simulated restart with a new prefs instance
        val freshPrefs = VeilPrefs(context)
        assertEquals(VeilPrefs.NOTIF_DISPLAY_ALWAYS_HIDE, freshPrefs.protectionNotificationDisplay)

        // Verify VEIL protection and persistent shield are NOT disabled
        assertTrue(freshPrefs.isVeilEnabled)
        assertTrue(freshPrefs.isPersistentShieldEnabled)
    }

    @Test
    fun settingShowWhenPaused_persistsAcrossFreshInstances() {
        prefs.protectionNotificationDisplay = VeilPrefs.NOTIF_DISPLAY_SHOW_WHEN_PAUSED
        assertEquals(VeilPrefs.NOTIF_DISPLAY_SHOW_WHEN_PAUSED, prefs.protectionNotificationDisplay)

        val freshPrefs = VeilPrefs(context)
        assertEquals(VeilPrefs.NOTIF_DISPLAY_SHOW_WHEN_PAUSED, freshPrefs.protectionNotificationDisplay)

        // Verify VEIL protection and persistent shield remain intact
        assertTrue(freshPrefs.isVeilEnabled)
        assertTrue(freshPrefs.isPersistentShieldEnabled)
    }

    @Test
    fun settingAlwaysShow_persistsAcrossFreshInstances() {
        prefs.protectionNotificationDisplay = VeilPrefs.NOTIF_DISPLAY_ALWAYS_SHOW
        val freshPrefs = VeilPrefs(context)
        assertEquals(VeilPrefs.NOTIF_DISPLAY_ALWAYS_SHOW, freshPrefs.protectionNotificationDisplay)
    }
}

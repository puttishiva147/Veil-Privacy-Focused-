package com.veil.privacy.licensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseDeviceKeyTest {
    @Test
    fun androidIdFingerprintIsStableAndExactly64HexCharacters() {
        val first = LicenseDeviceKey.fingerprintHashFromAndroidId("0123456789abcdef")
        assertEquals(64, first.length)
        assertTrue(first.matches(Regex("[a-f0-9]{64}")))
        assertEquals(first, LicenseDeviceKey.fingerprintHashFromAndroidId("0123456789abcdef"))
        assertNotEquals(first, LicenseDeviceKey.fingerprintHashFromAndroidId("fedcba9876543210"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankAndroidIdIsRejected() {
        LicenseDeviceKey.fingerprintHashFromAndroidId("")
    }
}

package com.veil.privacy.licensing

import com.veil.privacy.BuildConfig

/**
 * Reads the public licensing API origin from the release build environment.
 * Empty means the licensing service has not yet been deployed/configured.
 */
object VeilLicenseConfig {
    val isConfigured: Boolean
        get() = BuildConfig.VEIL_LICENSE_API_BASE_URL.startsWith("https://")

    fun createApi(): VeilLicenseApi? {
        if (!isConfigured) return null
        return VeilLicenseApiFactory.create(BuildConfig.VEIL_LICENSE_API_BASE_URL)
    }
}

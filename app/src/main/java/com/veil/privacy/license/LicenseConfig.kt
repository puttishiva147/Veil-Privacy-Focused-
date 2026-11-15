package com.veil.privacy.license

import com.veil.privacy.BuildConfig

object LicenseConfig {
    val baseUrl: String
        get() = BuildConfig.VEIL_LICENSE_BASE_URL.trim().trimEnd('/')

    fun isConfigured(): Boolean = baseUrl.startsWith("https://") &&
        !baseUrl.contains(".example") && !baseUrl.contains("localhost")
}

package com.veil.privacy.model

/**
 * Catalog of supported messaging and notification applications that VEIL can intercept and disguise.
 */
data class SupportedApp(
    val packageId: String,
    val displayName: String,
    val category: String,
    val defaultEnabled: Boolean = true
) {
    companion object {
        const val PKG_WHATSAPP = "com.whatsapp"
        const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        const val PKG_TELEGRAM = "org.telegram.messenger"
        const val PKG_TELEGRAM_PLUS = "org.telegram.plus"
        const val PKG_TELEGRAM_CH = "org.thunderdog.challegram"
        const val PKG_SIGNAL = "org.thoughtcrime.securesms"
        const val PKG_GOOGLE_MESSAGES = "com.google.android.apps.messaging"
        const val PKG_SAMSUNG_MESSAGES = "com.samsung.android.messaging"
        const val PKG_AOSP_MMS = "com.android.mms"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_MESSENGER = "com.facebook.orca"
        const val PKG_MESSENGER_LITE = "com.facebook.mlite"
        const val PKG_DISCORD = "com.discord"
        const val PKG_SLACK = "com.Slack"
        const val PKG_WECHAT = "com.tencent.mm"
        const val PKG_VIBER = "com.viber.voip"
        const val PKG_SKYPE = "com.skype.raider"
        const val PKG_SNAPCHAT = "com.snapchat.android"
        const val PKG_SIMULATION = "com.veil.privacy.simulation"

        val ALL_SUPPORTED_APPS = listOf(
            SupportedApp(PKG_WHATSAPP, "WhatsApp", "Instant Messaging"),
            SupportedApp(PKG_WHATSAPP_BUSINESS, "WhatsApp Business", "Business Messaging"),
            SupportedApp(PKG_TELEGRAM, "Telegram", "Instant Messaging"),
            SupportedApp(PKG_SIGNAL, "Signal", "Encrypted Messaging"),
            SupportedApp(PKG_GOOGLE_MESSAGES, "Google Messages (SMS/RCS)", "SMS & RCS"),
            SupportedApp(PKG_SAMSUNG_MESSAGES, "Samsung Messages", "SMS & RCS"),
            SupportedApp(PKG_INSTAGRAM, "Instagram Direct", "Social"),
            SupportedApp(PKG_MESSENGER, "Messenger", "Social"),
            SupportedApp(PKG_DISCORD, "Discord", "Chat"),
            SupportedApp(PKG_SLACK, "Slack", "Work"),
            SupportedApp(PKG_WECHAT, "WeChat", "Messaging"),
            SupportedApp(PKG_VIBER, "Viber", "Messaging"),
            SupportedApp(PKG_SKYPE, "Skype", "Calling & Chat"),
            SupportedApp(PKG_SNAPCHAT, "Snapchat", "Social"),
            SupportedApp(PKG_SIMULATION, "VEIL Test Studio (Simulator)", "Diagnostics", defaultEnabled = true)
        )

        private val DEFAULT_PACKAGE_SET = ALL_SUPPORTED_APPS.map { it.packageId }.toSet()

        fun isPackageRecognized(packageName: String?): Boolean {
            if (packageName == null) return false
            if (DEFAULT_PACKAGE_SET.contains(packageName)) return true
            // Also recognize Telegram forks or generic SMS packages
            if (packageName.startsWith("org.telegram.")) return true
            if (packageName.endsWith(".messaging") || packageName.endsWith(".mms")) return true
            return false
        }

        fun getAppDisplayName(packageName: String?): String {
            if (packageName == null) return "Unknown"
            if (packageName.startsWith("org.telegram.")) return "Telegram"
            val app = ALL_SUPPORTED_APPS.find { it.packageId == packageName }
            return app?.displayName ?: when (packageName) {
                PKG_AOSP_MMS -> "SMS Messages"
                PKG_MESSENGER_LITE -> "Messenger Lite"
                else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
    }
}

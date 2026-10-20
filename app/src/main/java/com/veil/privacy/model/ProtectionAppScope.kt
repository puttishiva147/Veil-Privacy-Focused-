package com.veil.privacy.model

import android.content.Context

/**
 * Stores which source app a protection rule belongs to without changing the Room schema.
 * Existing rules default to WhatsApp to preserve their historical behaviour.
 */
object ProtectionAppScope {
    private const val PREFS = "veil_privacy_prefs"
    private const val PREFIX = "protection_app_scope_"
    const val WHATSAPP = SupportedApp.PKG_WHATSAPP
    const val INSTAGRAM = SupportedApp.PKG_INSTAGRAM
    const val TELEGRAM = SupportedApp.PKG_TELEGRAM
    const val SNAPCHAT = SupportedApp.PKG_SNAPCHAT

    fun keyFor(contactName: String): String =
        PREFIX + contactName.trim().replace("\\s+".toRegex(), " ").lowercase()

    fun get(context: Context, contactName: String): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(keyFor(contactName), WHATSAPP) ?: WHATSAPP

    fun set(context: Context, contactName: String, packageName: String) {
        if (contactName.isBlank()) return
        val safePackage = if (packageName in setOf(WHATSAPP, INSTAGRAM, TELEGRAM, SNAPCHAT)) packageName else WHATSAPP
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(keyFor(contactName), safePackage).apply()
    }

    fun isProtectionEnabled(context: Context, packageName: String): Boolean {
        val canonical = when {
            packageName == SupportedApp.PKG_WHATSAPP_BUSINESS -> WHATSAPP
            packageName.startsWith("org.telegram.") -> TELEGRAM
            else -> packageName
        }
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("protection_enabled_" + canonical, true)
    }

    fun setProtectionEnabled(context: Context, packageName: String, enabled: Boolean) {
        val canonical = when {
            packageName == SupportedApp.PKG_WHATSAPP_BUSINESS -> WHATSAPP
            packageName.startsWith("org.telegram.") -> TELEGRAM
            else -> packageName
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("protection_enabled_" + canonical, enabled).apply()
    }

    fun matches(context: Context, contactName: String, notificationPackage: String): Boolean {
        val configured = get(context, contactName)
        return when (configured) {
            WHATSAPP -> notificationPackage == WHATSAPP ||
                notificationPackage == SupportedApp.PKG_WHATSAPP_BUSINESS
            else -> notificationPackage == configured
        }
    }
}

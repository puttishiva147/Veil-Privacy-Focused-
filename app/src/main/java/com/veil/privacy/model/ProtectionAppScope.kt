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

    private fun keyForRule(ruleId: Long): String = PREFIX + "rule_" + ruleId

    fun get(context: Context, contactName: String, ruleId: Long = 0L): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Rule IDs prevent two apps with identically named contacts from overwriting one another.
        if (ruleId > 0L) {
            prefs.getString(keyForRule(ruleId), null)?.let { return it }
        }
        return prefs.getString(keyFor(contactName), WHATSAPP) ?: WHATSAPP
    }

    fun set(context: Context, contactName: String, packageName: String, ruleId: Long = 0L) {
        if (contactName.isBlank()) return
        val safePackage = if (packageName in setOf(WHATSAPP, INSTAGRAM, TELEGRAM, SNAPCHAT)) packageName else WHATSAPP
        val editor = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        // Keep legacy mapping for pre-existing rule rows; new rows use a stable rule-specific key.
        editor.putString(keyFor(contactName), safePackage)
        if (ruleId > 0L) editor.putString(keyForRule(ruleId), safePackage)
        editor.apply()
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

    fun matches(context: Context, contactName: String, notificationPackage: String, ruleId: Long = 0L): Boolean {
        val configured = get(context, contactName, ruleId)
        return when (configured) {
            WHATSAPP -> notificationPackage == WHATSAPP ||
                notificationPackage == SupportedApp.PKG_WHATSAPP_BUSINESS
            TELEGRAM -> notificationPackage.startsWith("org.telegram.") ||
                notificationPackage == SupportedApp.PKG_TELEGRAM_CH
            else -> notificationPackage == configured
        }
    }
}

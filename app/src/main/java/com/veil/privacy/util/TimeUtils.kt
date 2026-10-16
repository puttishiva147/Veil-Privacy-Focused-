package com.veil.privacy.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> {
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    fun formatFullDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy 'at' hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

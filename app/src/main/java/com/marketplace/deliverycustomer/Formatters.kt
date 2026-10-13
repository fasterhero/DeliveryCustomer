
package com.marketplace.deliverycustomer

import com.google.firebase.Timestamp
import java.util.Locale

internal fun money(v: Double): String =
    "₱" + if (v % 1.0 == 0.0) {
        String.format(Locale.US, "%,.0f", v)
    } else {
        String.format(Locale.US, "%,.2f", v)
    }

internal fun stars(n: Int): String =
    "★".repeat(n.coerceIn(0, 5)) +
        "☆".repeat(5 - n.coerceIn(0, 5))

internal fun timeAgo(ts: Timestamp?): String {
    if (ts == null) return "just now"

    val mins = (System.currentTimeMillis() - ts.seconds * 1000) / 60000

    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 1440 -> "${mins / 60} h ago"
        mins < 43200 -> "${mins / 1440} d ago"
        else -> "${mins / 43200} mo ago"
    }
}

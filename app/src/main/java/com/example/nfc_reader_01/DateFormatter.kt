package com.example.nfc_reader_01

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility object for formatting dates.
 */
object DateFormatter {
    /**
     * Converts an Epoch timestamp (in seconds) to a formatted date and time string.
     *
     * @param timestamp The Epoch value in seconds (Long).
     * @return The formatted date (String) or "Never configured" if the timestamp is 0.
     */
    fun formatEpochTimestamp(timestamp: Long): String {
        // The value 0 is used to indicate that it has never been configured
        if (timestamp == 0L) {
            return "Never configured"
        }

        // Assuming the timestamp is in SECONDS (common in embedded protocols)
        val milliseconds = timestamp * 1000L

        // Format: year/month/day hour:minute:second
        // We use Locale.getDefault() to respect the user's regional settings
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        val date = Date(milliseconds)
        return sdf.format(date)
    }
}

package com.swipeclean.app.data

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateTaken: Long, // Epoch millis
    val bucketName: String,
    val path: String
) {
    val formattedSize: String
        get() = when {
            size >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", size / (1024f * 1024f * 1024f))
            size >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024f * 1024f))
            size >= 1024 -> String.format(Locale.US, "%.1f KB", size / 1024f)
            else -> "$size B"
        }

    val formattedDateTime: String
        get() {
            if (dateTaken <= 0L) return ""
            val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
            return sdf.format(Date(dateTaken))
        }

    val formattedDateOnly: String
        get() {
            if (dateTaken <= 0L) return ""
            val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            return sdf.format(Date(dateTaken))
        }

    val formattedTimeOnly: String
        get() {
            if (dateTaken <= 0L) return ""
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(dateTaken))
        }
}

data class FolderItem(
    val name: String,
    val count: Int,
    val totalSizeBytes: Long,
    val thumbnailUri: Uri?
) {
    val formattedSize: String
        get() = when {
            totalSizeBytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", totalSizeBytes / (1024f * 1024f * 1024f))
            totalSizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", totalSizeBytes / (1024f * 1024f))
            else -> String.format(Locale.US, "%.1f KB", totalSizeBytes / 1024f)
        }
}

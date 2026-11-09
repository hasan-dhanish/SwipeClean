package com.swipeclean.app.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    suspend fun getFolders(): List<FolderItem> = withContext(Dispatchers.IO) {
        val folderMap = mutableMapOf<String, MutableList<MediaItem>>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Image" else "Image"
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val rawDateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                    val dateTaken = if (rawDateTaken > 0L) rawDateTaken else (dateAdded * 1000L)
                    val rawBucket = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val bucketName = rawBucket ?: when {
                        path.contains("Screenshot", ignoreCase = true) -> "Screenshots"
                        path.contains("WhatsApp", ignoreCase = true) -> "WhatsApp Images"
                        path.contains("DCIM/Camera", ignoreCase = true) -> "Camera"
                        path.contains("Download", ignoreCase = true) -> "Download"
                        else -> "Other"
                    }

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val item = MediaItem(id, uri, name, size, dateTaken, bucketName, path)
                    folderMap.getOrPut(bucketName) { mutableListOf() }.add(item)
                }
            }
        } catch (_: Exception) {}

        folderMap.map { (name, items) ->
            FolderItem(
                name = name,
                count = items.size,
                totalSizeBytes = items.sumOf { it.size },
                thumbnailUri = items.firstOrNull()?.uri
            )
        }.sortedWith(compareByDescending<FolderItem> { 
            if (it.name.equals("Screenshots", ignoreCase = true)) 100 
            else if (it.name.equals("Camera", ignoreCase = true)) 90 
            else 0 
        }.thenByDescending { it.totalSizeBytes })
    }

    suspend fun getMediaInFolder(folderName: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Image" else "Image"
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val rawDateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                    val dateTaken = if (rawDateTaken > 0L) rawDateTaken else (dateAdded * 1000L)
                    val rawBucket = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val bucket = rawBucket ?: when {
                        path.contains("Screenshot", ignoreCase = true) -> "Screenshots"
                        path.contains("WhatsApp", ignoreCase = true) -> "WhatsApp Images"
                        path.contains("DCIM/Camera", ignoreCase = true) -> "Camera"
                        else -> "Other"
                    }

                    if (bucket.equals(folderName, ignoreCase = true)) {
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        items.add(MediaItem(id, uri, name, size, dateTaken, bucket, path))
                    }
                }
            }
        } catch (_: Exception) {}

        items
    }

    suspend fun getMediaByIds(ids: List<Long>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()

        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )

        val chunks = ids.chunked(500)
        for (chunk in chunks) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.Images.Media._ID} IN ($placeholders)"
            val selectionArgs = chunk.map { it.toString() }.toTypedArray()

            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Image" else "Image"
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                        val rawDateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                        val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                        val dateTaken = if (rawDateTaken > 0L) rawDateTaken else (dateAdded * 1000L)
                        val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "Gallery" else "Gallery"
                        val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        items.add(MediaItem(id, uri, name, size, dateTaken, bucket, path))
                    }
                }
            } catch (_: Exception) {}
        }

        items
    }

    suspend fun getTrashedMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATA
            )
            val bundle = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(
                    android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                )
            }
            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    bundle,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Image" else "Image"
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                        val rawDateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                        val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                        val dateTaken = if (rawDateTaken > 0L) rawDateTaken else (dateAdded * 1000L)
                        val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "Gallery" else "Gallery"
                        val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        items.add(MediaItem(id, uri, name, size, dateTaken, bucket, path))
                    }
                }
            } catch (_: Exception) {}
        }
        items
    }

    fun createTrashRequest(uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            try {
                return MediaStore.createTrashRequest(context.contentResolver, uris, true)
            } catch (_: Exception) {}
        }
        return null
    }

    fun createRestoreRequest(uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            try {
                return MediaStore.createTrashRequest(context.contentResolver, uris, false)
            } catch (_: Exception) {}
        }
        return null
    }

    fun createPermanentDeleteRequest(uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            try {
                return MediaStore.createDeleteRequest(context.contentResolver, uris)
            } catch (_: Exception) {}
        }
        return null
    }

    suspend fun deleteDirect(uris: List<Uri>) = withContext(Dispatchers.IO) {
        for (uri in uris) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
        }
    }
}

package com.swipeclean.app.data

import android.content.Context
import android.content.SharedPreferences

class TrashStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("swipeclean_persistent_v2", Context.MODE_PRIVATE)

    // Using synchronized sets and synchronous commit() to guarantee zero data loss on force-close
    private val trashedIds = mutableSetOf<Long>()
    private val keptIds = mutableSetOf<Long>()

    init {
        loadFromDisk()
    }

    @Synchronized
    private fun loadFromDisk() {
        trashedIds.clear()
        keptIds.clear()

        val trashedRaw = prefs.getString("trashed_ids_csv", "") ?: ""
        if (trashedRaw.isNotBlank()) {
            trashedRaw.split(",").mapNotNull { it.trim().toLongOrNull() }.forEach { trashedIds.add(it) }
        }

        val keptRaw = prefs.getString("kept_ids_csv", "") ?: ""
        if (keptRaw.isNotBlank()) {
            keptRaw.split(",").mapNotNull { it.trim().toLongOrNull() }.forEach { keptIds.add(it) }
        }
    }

    @Synchronized
    private fun saveToDisk() {
        prefs.edit()
            .putString("trashed_ids_csv", trashedIds.joinToString(","))
            .putString("kept_ids_csv", keptIds.joinToString(","))
            .commit() // Synchronous disk flush to survive immediate force-close!
    }

    @Synchronized
    fun getTrashedIds(): Set<Long> {
        return trashedIds.toSet()
    }

    @Synchronized
    fun addTrashedId(id: Long) {
        trashedIds.add(id)
        keptIds.remove(id) // If previously kept, move to trash
        saveToDisk()
    }

    @Synchronized
    fun removeTrashedId(id: Long) {
        trashedIds.remove(id)
        saveToDisk()
    }

    @Synchronized
    fun removeTrashedIds(ids: Collection<Long>) {
        trashedIds.removeAll(ids.toSet())
        saveToDisk()
    }

    @Synchronized
    fun getKeptIds(): Set<Long> {
        return keptIds.toSet()
    }

    @Synchronized
    fun addKeptId(id: Long) {
        keptIds.add(id)
        trashedIds.remove(id) // If previously trashed, move to kept
        saveToDisk()
    }

    @Synchronized
    fun removeKeptId(id: Long) {
        keptIds.remove(id)
        saveToDisk()
    }

    @Synchronized
    fun resetKeptForFolder(folderItemIds: Collection<Long>) {
        keptIds.removeAll(folderItemIds.toSet())
        saveToDisk()
    }

    @Synchronized
    fun resetAllKept() {
        keptIds.clear()
        saveToDisk()
    }
}

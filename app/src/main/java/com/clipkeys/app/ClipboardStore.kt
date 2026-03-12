package com.clipkeys.app

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object ClipboardStore {

    private const val FILE_NAME = "clipboard_items.json"
    private val gson = Gson()
    private var items: MutableList<ClipboardItem> = mutableListOf()
    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            load(context)
            initialized = true
        }
    }

    private fun getFile(context: Context) = File(context.filesDir, FILE_NAME)

    private fun load(context: Context) {
        try {
            val file = getFile(context)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<MutableList<ClipboardItem>>() {}.type
                items = gson.fromJson(json, type) ?: mutableListOf()
            }
        } catch (e: Exception) {
            items = mutableListOf()
        }
    }

    private fun save(context: Context) {
        try {
            getFile(context).writeText(gson.toJson(items))
        } catch (e: Exception) {}
    }

    /** Tambah item baru, cek duplikat */
    fun add(context: Context, text: String) {
        if (text.isBlank()) return
        // Jangan simpan duplikat persis
        val exists = items.any { it.text == text }
        if (exists) {
            // Update timestamp agar muncul di atas
            val idx = items.indexOfFirst { it.text == text }
            if (idx >= 0) {
                val item = items.removeAt(idx)
                items.add(0, item.copy(timestamp = System.currentTimeMillis()))
                save(context)
            }
            return
        }
        val item = ClipboardItem(text = text)
        items.add(0, item) // newest first
        // Batas 500 item (non-pinned)
        val unpinned = items.filter { !it.isPinned }
        if (unpinned.size > 500) {
            val last = unpinned.last()
            items.remove(last)
        }
        save(context)
    }

    fun delete(context: Context, id: String) {
        items.removeAll { it.id == id }
        save(context)
    }

    fun togglePin(context: Context, id: String) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(isPinned = !items[idx].isPinned)
            save(context)
        }
    }

    /** Ambil semua item: pinned dulu, lalu sisanya */
    fun getAll(): List<ClipboardItem> {
        val pinned = items.filter { it.isPinned }
        val rest = items.filter { !it.isPinned }
        return pinned + rest
    }

    /** Search by text */
    fun search(query: String): List<ClipboardItem> {
        if (query.isBlank()) return getAll()
        val q = query.lowercase()
        val pinned = items.filter { it.isPinned && it.text.lowercase().contains(q) }
        val rest = items.filter { !it.isPinned && it.text.lowercase().contains(q) }
        return pinned + rest
    }

    fun count() = items.size

    /** Export ke Downloads */
    fun export(context: Context): Boolean {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "clipkeys_export_${System.currentTimeMillis()}.json")
            file.writeText(gson.toJson(items))
            true
        } catch (e: Exception) {
            false
        }
    }
}

package com.clipkeys.app

data class ClipboardItem(
    val id: String = System.currentTimeMillis().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false,
    val preview: String = if (text.length > 120) text.take(120) + "..." else text
)

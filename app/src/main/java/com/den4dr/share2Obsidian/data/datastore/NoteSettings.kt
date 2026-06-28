package com.den4dr.share2Obsidian.data.datastore

/**
 * vault/folder のグローバルデフォルト設定（DataStore に保存される）。
 */
data class NoteSettings(
    val vault: String = "",
    val folder: String = "",
)

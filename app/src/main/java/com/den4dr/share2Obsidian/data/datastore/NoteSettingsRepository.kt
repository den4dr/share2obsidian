package com.den4dr.share2Obsidian.data.datastore

import kotlinx.coroutines.flow.Flow

/**
 * vault/folder のグローバル設定を DataStore Preferences で読み書きするリポジトリ。
 */
interface NoteSettingsRepository {

    /** vault/folder の現在設定を Flow で取得する（DataStore 変更を自動通知）。 */
    fun getSettings(): Flow<NoteSettings>

    /** vault を DataStore に保存する。 */
    suspend fun saveVault(vault: String)

    /** folder を DataStore に保存する。 */
    suspend fun saveFolder(folder: String)
}

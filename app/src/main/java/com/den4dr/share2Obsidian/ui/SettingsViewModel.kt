package com.den4dr.share2Obsidian.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.den4dr.share2Obsidian.data.datastore.NoteSettingsRepository
import com.den4dr.share2Obsidian.data.llm.LlmSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 【機能概要】: SettingsScreen で表示する vault/folder/LLM設定の状態
 * 【設計方針】: 既存の vault/folder に加え、LLM API 設定3項目（endpointUrl/apiKey/model）を保持する
 * 【保守性】: 全フィールドにデフォルト値（空文字）を持たせ、将来的なフィールド追加時も
 *            既存呼び出し箇所への影響を最小化する（named argument での構築を前提とする）
 * 🔵 信頼性レベル: requirements.md 2.4・testcases.md より、ほぼ推測なし
 */
data class SettingsUiState(
    val vault: String = "",
    val folder: String = "",
    // 【フィールド追加】: LLM API 接続設定（未設定は空文字で表現） 🔵
    val llmEndpointUrl: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "",
)

/**
 * 【機能概要】: vault/folder（NoteSettingsRepository）と LLM API 設定（LlmSettingsRepository）の
 *              グローバル設定を DataStore/EncryptedSharedPreferences 経由で読み書きする ViewModel（REQ-021, REQ-004）
 * 【改善内容】: update系5関数（updateVault/updateFolder/updateLlmEndpointUrl/updateLlmApiKey/updateLlmModel）に
 *              共通していた `viewModelScope.launch(Dispatchers.IO) { ... }` の重複をプライベートヘルパー
 *              [launchOnIo] へ抽出した（DRY原則、Greenフェーズで検出した重複課題への対応）。挙動・公開APIは無変更
 * 【設計方針】: コンストラクタに llmSettingsRepository を追加注入し、uiState は combine() で構築する。
 *              入力変更時に即時保存する（保存ボタンなし）
 * 【保守性】: 保存処理の起動方法（Dispatcherの種類・viewModelScopeの使い方）を1箇所に集約したことで、
 *            将来的な変更（例: 保存失敗時のリトライ処理追加）が1箇所の修正で全update関数に反映できる
 * 🔵 信頼性レベル: requirements.md・testcases.md・green-phase.md より、ほぼ推測なし
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val noteSettingsRepository: NoteSettingsRepository,
    // 【コンストラクタ拡張】: LLM設定の読み書き窓口を追加注入する 🔵
    private val llmSettingsRepository: LlmSettingsRepository,
) : ViewModel() {

    // 【uiState構築】: 2つのRepositoryのFlowをcombine()で集約し、単一のSettingsUiStateを構築する
    // 【処理方針】: WhileSubscribed(5_000)で購読終了5秒後にFlowを停止し、リソースリークを防ぐ
    // 🔵 信頼性レベル: requirements.md 2.5データフロー・note.mdより
    val uiState: StateFlow<SettingsUiState> = combine(
        noteSettingsRepository.getSettings(),
        llmSettingsRepository.getSettings(),
    ) { note, llm ->
        SettingsUiState(
            vault = note.vault,
            folder = note.folder,
            llmEndpointUrl = llm.endpointUrl,
            llmApiKey = llm.apiKey,
            llmModel = llm.model,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /**
     * 【ヘルパー関数】: viewModelScope上でDispatchers.IOによるバックグラウンド処理を起動する共通処理
     * 【再利用性】: vault/folder/LLM設定など、即時保存を行うあらゆるupdate系関数から呼び出せる
     * 【単一責任】: 「バックグラウンドスレッドでコルーチンを起動する」という技術的関心事のみを担当し、
     *              保存先（どのRepositoryのどのメソッドを呼ぶか）には関与しない
     * 🔵 信頼性レベル: 既存updateVault/updateFolderの実装パターンをそのまま抽出したもの（挙動変更なし）
     */
    private fun launchOnIo(action: suspend () -> Unit) {
        // 【処理効率化】: 呼び出し側でlaunch(Dispatchers.IO){...}を都度書く必要がなくなる
        // 【可読性向上】: update系関数の本体が「何を保存するか」だけに集中でき、意図が読み取りやすくなる
        viewModelScope.launch(Dispatchers.IO) {
            action()
        }
    }

    /**
     * 【機能概要】: Vaultパス設定を保存する
     * 【改善内容】: launch(Dispatchers.IO)の重複をlaunchOnIoヘルパーへ置き換えた（挙動は変更なし）
     * 🔵 信頼性レベル: 既存実装（挙動不変のリファクタリングのみ）
     */
    fun updateVault(vault: String) {
        launchOnIo { noteSettingsRepository.saveVault(vault) }
    }

    /**
     * 【機能概要】: 保存先フォルダ設定を保存する
     * 【改善内容】: launch(Dispatchers.IO)の重複をlaunchOnIoヘルパーへ置き換えた（挙動は変更なし）
     * 🔵 信頼性レベル: 既存実装（挙動不変のリファクタリングのみ）
     */
    fun updateFolder(folder: String) {
        launchOnIo { noteSettingsRepository.saveFolder(folder) }
    }

    /**
     * 【機能概要】: LLM APIエンドポイントURLを保存する
     * 【設計方針】: 引数をそのままRepositoryのsave系関数に委譲する（バリデーションなし、本タスク範囲外）
     * 【テスト対応】: TC-N-01（保存委譲）を通すための実装
     * 🔵 信頼性レベル: requirements.md 2.2・testcases.md TC-N-01より
     */
    fun updateLlmEndpointUrl(url: String) {
        launchOnIo { llmSettingsRepository.saveEndpointUrl(url) }
    }

    /**
     * 【機能概要】: LLM APIキーを保存する
     * 【設計方針】: 引数をそのままRepositoryのsave系関数に委譲する。暗号化はRepository側の責務（REQ-401）
     * 【テスト対応】: TC-N-02, TC-B-01（空文字保存）を通すための実装
     * 🔵 信頼性レベル: requirements.md 2.2・REQ-401・testcases.md TC-N-02より
     */
    fun updateLlmApiKey(apiKey: String) {
        launchOnIo { llmSettingsRepository.saveApiKey(apiKey) }
    }

    /**
     * 【機能概要】: LLM使用モデル名を保存する
     * 【設計方針】: 引数をそのままRepositoryのsave系関数に委譲する
     * 【テスト対応】: TC-N-03, TC-B-03（連続更新）を通すための実装
     * 🔵 信頼性レベル: requirements.md 2.2・testcases.md TC-N-03より
     */
    fun updateLlmModel(model: String) {
        launchOnIo { llmSettingsRepository.saveModel(model) }
    }
}

# TASK-0066 TDD Greenフェーズ記録: SettingsViewModel LLM設定対応

**機能名**: SettingsViewModel LLM設定対応 (settings-viewmodel-llm)
**タスクID**: TASK-0066
**要件名**: llm-memo-rewrite
**フェーズ**: Phase 4 - LLM設定UI
**作成日**: 2026-07-06

---

## 1. 実装方針

Redフェーズの `settings-viewmodel-llm-red-phase.md` §4 に記載された実装内容をそのまま採用し、最小実装で全テストを通す。

1. `SettingsUiState` に `llmEndpointUrl` / `llmApiKey` / `llmModel`（各 `String = ""`）を追加
2. `SettingsViewModel` のコンストラクタに `llmSettingsRepository: LlmSettingsRepository` を追加注入
3. `uiState` を `map` から `combine(noteSettingsRepository.getSettings(), llmSettingsRepository.getSettings())` に変更
4. `updateLlmEndpointUrl()` / `updateLlmApiKey()` / `updateLlmModel()` を追加し、各々 `viewModelScope.launch(Dispatchers.IO)` で対応する save 系関数を呼ぶ

仕様（要件定義書・テストケース定義書）と実装前のコードとの間に差異は見つからなかった（AskUserQuestion での確認は不要と判断）。

## 2. 実装コード全文

`app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`（112行）

```kotlin
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
 * 【実装方針】: 既存の vault/folder に加え、LLM API 設定3項目（endpointUrl/apiKey/model）を追加する
 * 【テスト対応】: TC-N-04, TC-N-05, TC-B-02（uiState の combine 集約・後方互換・初期値）を通すための実装
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
 * 【実装方針】: コンストラクタに llmSettingsRepository を追加注入し、uiState を map から combine() に変更する
 * 【テスト対応】: TC-N-01〜05, TC-B-01〜03, TC-E-02（コンストラクタ2引数化）を通すための実装
 * 入力変更時に即時保存する（保存ボタンなし）。
 * 🔵 信頼性レベル: requirements.md・testcases.md・red-phase.md より、ほぼ推測なし
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

    fun updateVault(vault: String) {
        viewModelScope.launch(Dispatchers.IO) {
            noteSettingsRepository.saveVault(vault)
        }
    }

    fun updateFolder(folder: String) {
        viewModelScope.launch(Dispatchers.IO) {
            noteSettingsRepository.saveFolder(folder)
        }
    }

    /**
     * 【機能概要】: LLM APIエンドポイントURLを保存する
     * 【実装方針】: 引数をそのままRepositoryのsave系関数に委譲する（バリデーションなし）
     * 【テスト対応】: TC-N-01（保存委譲）を通すための実装
     * 🔵 信頼性レベル: requirements.md 2.2・testcases.md TC-N-01より
     */
    fun updateLlmEndpointUrl(url: String) {
        // 【非同期保存】: Dispatchers.IOでバックグラウンド実行し、メインスレッドをブロックしない
        viewModelScope.launch(Dispatchers.IO) {
            llmSettingsRepository.saveEndpointUrl(url)
        }
    }

    /**
     * 【機能概要】: LLM APIキーを保存する
     * 【実装方針】: 引数をそのままRepositoryのsave系関数に委譲する。暗号化はRepository側の責務
     * 【テスト対応】: TC-N-02, TC-B-01（空文字保存）を通すための実装
     * 🔵 信頼性レベル: requirements.md 2.2・REQ-401・testcases.md TC-N-02より
     */
    fun updateLlmApiKey(apiKey: String) {
        // 【非同期保存】: Dispatchers.IOでバックグラウンド実行（EncryptedSharedPreferences同期API対応）
        viewModelScope.launch(Dispatchers.IO) {
            llmSettingsRepository.saveApiKey(apiKey)
        }
    }

    /**
     * 【機能概要】: LLM使用モデル名を保存する
     * 【実装方針】: 引数をそのままRepositoryのsave系関数に委譲する
     * 【テスト対応】: TC-N-03, TC-B-03（連続更新）を通すための実装
     * 🔵 信頼性レベル: requirements.md 2.2・testcases.md TC-N-03より
     */
    fun updateLlmModel(model: String) {
        // 【非同期保存】: Dispatchers.IOでバックグラウンド実行
        viewModelScope.launch(Dispatchers.IO) {
            llmSettingsRepository.saveModel(model)
        }
    }
}
```

## 3. 副次的な修正（既存呼び出し側の追随）

`SettingsViewModel` のコンストラクタが2引数化されたことに伴い、`app/src/androidTest/java/com/den4dr/share2Obsidian/ui/SettingsScreenTest.kt` が1引数呼び出し（`SettingsViewModel(FakeNoteSettingsRepository(settings))`）でコンパイルエラーとなることが判明した（Redフェーズのドキュメントには記載がなかったため、実装時に検出）。

- `FakeLlmSettingsRepository`（`LlmSettingsRepository` のテスト用フェイク実装）を追加
- `createViewModel()` を `SettingsViewModel(FakeNoteSettingsRepository(settings), FakeLlmSettingsRepository())` に変更

この修正は仕様（既存機能の後方互換・REQ-021）に沿った既存呼び出し側の追随であり、モック・スタブは実装コード（`SettingsViewModel.kt`）ではなくテストコード内にのみ存在する。

## 4. テスト実行結果

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*SettingsViewModelTest*"
# BUILD SUCCESSFUL

mise exec -- ./gradlew test
# BUILD SUCCESSFUL（全ユニットテスト成功、既存テストへの回帰なし）

mise exec -- ./gradlew compileDebugAndroidTestKotlin
# BUILD SUCCESSFUL（SettingsScreenTest.kt のコンパイルエラー解消を確認。実機/エミュレータ実行は本フェーズの範囲外）
```

`SettingsViewModelTest.kt` の全10テストメソッド（TC-N-01〜05, TC-B-01〜03, TC-E-02×2）が成功。

## 5. 品質判定

```
✅ 高品質:
- テスト結果: mise exec -- ./gradlew test で全て成功（既存テスト含め回帰なし）
- 実装品質: シンプル（Redフェーズ記録の実装方針をそのまま採用、独自の複雑化なし）
- リファクタ箇所: 明確
  - update系3関数（updateLlmEndpointUrl/updateLlmApiKey/updateLlmModel）の
    launch(Dispatchers.IO) { ... } 定型処理が重複（共通化の余地あり）
  - KDocコメントの一部重複（信頼性レベル記載など）
- 機能的問題: なし
- コンパイルエラー: なし（unitTest・androidTestKotlinとも成功）
- ファイルサイズ: 112行（800行制限に対し十分小さい）
- モック使用: 実装コード（SettingsViewModel.kt）にモック・スタブは含まれていない
  （FakeLlmSettingsRepositoryはテストコード側のみに追加）
```

## 6. 課題・改善点（Refactorフェーズで対応）

- `updateLlmEndpointUrl` / `updateLlmApiKey` / `updateLlmModel` の3関数が `viewModelScope.launch(Dispatchers.IO) { llmSettingsRepository.saveXxx(...) }` という同一パターンを繰り返している。共通のプライベートヘルパー関数への抽出を検討する。
- `updateVault` / `updateFolder` も同様のパターンであり、`noteSettingsRepository` 側もあわせて共通化できる可能性がある（ただし別Repositoryのため無理な統合は避ける）。
- KDocの「信頼性レベル」記載が各関数で類似しており、必要に応じて簡潔化を検討する。

---

**作成日**: 2026-07-06 by tsumiki:tdd-green
**次フェーズ**: `/tsumiki:tdd-refactor llm-memo-rewrite TASK-0066`

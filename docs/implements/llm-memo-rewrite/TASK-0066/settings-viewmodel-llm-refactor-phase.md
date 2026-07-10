# TASK-0066 TDD Refactorフェーズ記録: SettingsViewModel LLM設定対応

**機能名**: SettingsViewModel LLM設定対応 (settings-viewmodel-llm)
**タスクID**: TASK-0066
**要件名**: llm-memo-rewrite
**フェーズ**: Phase 4 - LLM設定UI
**実施日**: 2026-07-06

---

## 1. リファクタリング方針

Greenフェーズ記録（`settings-viewmodel-llm-green-phase.md` §6）で指摘された課題に対応する。

1. `updateVault` / `updateFolder` / `updateLlmEndpointUrl` / `updateLlmApiKey` / `updateLlmModel` の5関数が
   `viewModelScope.launch(Dispatchers.IO) { ... }` という同一パターンを繰り返していた（DRY違反）。
2. KDocの「信頼性レベル」記載が各関数で類似・冗長だった。

機能追加は行わず、挙動を変えない範囲でのみ改善する（リファクタリングの原則）。

---

## 2. 改善内容

### 2.1 `launchOnIo` ヘルパー関数の抽出（DRY原則・単一責任）

- 【改善内容】: 5つのupdate系関数に重複していた `viewModelScope.launch(Dispatchers.IO) { ... }` を
  `private fun launchOnIo(action: suspend () -> Unit)` に抽出した。
- 【設計方針】: ヘルパーは「バックグラウンドスレッドでコルーチンを起動する」という技術的関心事のみを担当し、
  どのRepositoryのどのメソッドを呼ぶかには関与しない。そのため vault/folder（NoteSettingsRepository）と
  LLM設定（LlmSettingsRepository）という異なるRepositoryをまたいで無理に統合することなく共通化できた
  （Greenフェーズの懸念「別Repositoryのため無理な統合は避ける」を、Repositoryに依存しない形の抽出で解消）。
- 【保守性】: 保存処理の起動方法を1箇所に集約したことで、将来的な変更（例: 保存失敗時のリトライ処理の追加）が
  1箇所の修正で全update関数に反映できる。
- 🔵 信頼性レベル: 既存 `updateVault`/`updateFolder` の実装パターンをそのまま抽出したものであり、
  ロジック自体の変更はない（推測なし）。

### 2.2 KDocコメントの整理

- 各update関数のKDocから冗長だった「信頼性レベルの理由説明の重複」を整理し、
  「改善内容」「設計方針」の観点を明確化した。
- クラス・データクラスレベルのKDocに「改善内容」「保守性」の説明を追加し、
  何を・なぜ改善したかをコード全体から追跡できるようにした。

### 2.3 変更していない部分

- `SettingsUiState` のフィールド構成・デフォルト値: 変更なし。
- `uiState` の `combine()` / `stateIn()` 構築ロジック: 変更なし。
- 各update関数の公開シグネチャ（引数・戻り値）: 変更なし。
- テストコード（`SettingsViewModelTest.kt`）: 変更なし（内部実装のみのリファクタリングのため、既存テストが無修正で全て通ることを確認済み）。

---

## 3. 改善後の実装コード全文

`app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`（120行）

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
```

---

## 4. セキュリティレビュー

- 【APIキーの取り扱い】: `updateLlmApiKey` は `launchOnIo` 経由で `llmSettingsRepository.saveApiKey(apiKey)` に委譲するのみで、
  ViewModel内にAPIキーを平文フィールドとして保持しない。暗号化保存（EncryptedSharedPreferences）は
  `LlmSettingsRepository`（TASK-0058実装済）側の責務であり、本リファクタでもこの境界を変更していない。
- 【ログ出力】: `launchOnIo` ヘルパーはログ出力を行わないため、リファクタによる機微情報の漏洩リスクは追加されていない。
- 【入力検証】: URL/APIキー/モデル名のバリデーションは行っていないが、これは要件定義書・テストケース定義書で
  「本タスク範囲外」と明示済み（TC-E-01）であり、リファクタ対象外。
- 【SQLi/XSS/CSRF】: 本コンポーネントはネイティブAndroidのViewModel層であり、SQL実行・Web描画・HTTPフォーム送信を
  直接行わないため該当なし。
- 【認証・認可】: ViewModel層では扱わない（該当なし）。
- **重大な脆弱性は発見されなかった。**

---

## 5. パフォーマンスレビュー

- 【計算量】: `combine()` は2つのFlowの最新値をO(1)でマッピングするのみで、リファクタ前後で計算量は変化なし。
- 【ヘルパー抽出のオーバーヘッド】: `launchOnIo` は関数呼び出しが1段増えるのみで、`viewModelScope.launch(Dispatchers.IO) { action() }`
  という実行内容自体は変わらないため、実質的なパフォーマンス影響はない（コンパイラによるインライン化可能な単純な委譲）。
- 【メモリ】: 各update関数が生成するラムダのキャプチャ量は既存実装と同等（引数1個＋Repository参照）。
- 【スレッド】: 全update系関数が引き続き `Dispatchers.IO` で実行されるため、メインスレッドをブロックしない制約は維持されている。
- **重大な性能課題は発見されなかった。**

---

## 6. テスト実行結果

```bash
mise exec -- ./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest*" --info
# BUILD SUCCESSFUL（全10テスト成功、失敗・スキップ0）

mise exec -- ./gradlew test
# BUILD SUCCESSFUL（全ユニットテストに回帰なし）

mise exec -- ./gradlew compileDebugAndroidTestKotlin
# BUILD SUCCESSFUL（SettingsScreenTest.kt 等 androidTest側も問題なくコンパイル）

mise exec -- ./gradlew lint
# BUILD SUCCESSFUL（エラーなし）
```

- 2秒以上かかるテストなし（最長 `updateVault_savesToRepository` で約1.07秒、mockk初期化コストによる推測）。
- `@Ignore`/`.skip`/`xit`/`xdescribe`/`@Disabled`等のテスト無効化記述はなし。
- テスト除外設定（`excludeTestsMatching`等）はなし。
- 開発中生成の一時ファイル（`debug-*`/`temp-*`/`*.tmp`/`*.bak`等）は検出されず。

---

## 7. 品質判定

```
✅ 高品質:
- テスト結果: 全て継続成功（Taskツールによる実行で確認、既存テスト無修正で通過）
- セキュリティ: 重大な脆弱性なし
- パフォーマンス: 重大な性能課題なし
- リファクタ品質: Greenフェーズで指摘されたDRY違反（launch(Dispatchers.IO)の重複）を解消。目標達成
- コード品質: launchOnIoヘルパーへの抽出により可読性・保守性が向上
- ファイルサイズ: 120行（500行制限に対し十分小さい）
- 日本語コメント: 改善内容・設計方針・保守性の観点を明記し、信頼性レベルの重複記載を整理
- ドキュメント: 完成
```

---

**実施日**: 2026-07-06 by tsumiki:tdd-refactor
**次フェーズ**: `/tsumiki:tdd-verify-complete`

# TDD Greenフェーズ記録: LlmSettings・LlmSettingsRepository・LlmSettingsRepositoryImpl

**機能名**: llm-settings-repository
**タスクID**: TASK-0058
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. 実装方針

Red フェーズの記録（`llm-settings-repository-red-phase.md`）・note.md・TASK-0058.md に記載された実装例をそのまま採用した。仕様（要件定義書・テストケース定義書）と実装案の間に差異は見つからなかったため、AskUserQuestion での確認は行わず実装を進めた。

- `LlmSettings`: 全フィールド `String = ""` の data class。🔵
- `LlmSettingsRepository`: `getSettings()` / `saveEndpointUrl()` / `saveApiKey()` / `saveModel()` の4メソッドを持つインターフェース。🔵
- `LlmSettingsRepositoryImpl`: 既存 `NoteSettingsRepositoryImpl` の DataStore パターン（`stringPreferencesKey` + `map` + `edit`）を踏襲し、`apiKey` のみ `EncryptedSharedPreferences`（コンストラクタでは `SharedPreferences` 型として注入）に分離。`encryptedApiKeyFlow()` を `callbackFlow` + `SharedPreferences.OnSharedPreferenceChangeListener` で実装し、DataStore の `Flow` と `combine()` して単一の `Flow<LlmSettings>` を返す。🟡（callbackFlowパターンは本プロジェクト初採用のため推測を含む）

DI（`LlmModule`）は本タスクのテスト対象外（テストはコンストラクタ直接注入で検証）のため、Green フェーズでは実装していない（note.mdの記載どおり後続タスクで対応）。

## 2. 実装コード

### 2.1 `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt`

```kotlin
package com.den4dr.share2Obsidian.data.llm

data class LlmSettings(
    val endpointUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)
```

### 2.2 `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt`

```kotlin
package com.den4dr.share2Obsidian.data.llm

import kotlinx.coroutines.flow.Flow

interface LlmSettingsRepository {
    fun getSettings(): Flow<LlmSettings>
    suspend fun saveEndpointUrl(url: String)
    suspend fun saveApiKey(apiKey: String)
    suspend fun saveModel(model: String)
}
```

### 2.3 `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt`

日本語コメント・信頼性レベル付きの全文は当該ファイルを参照（本文はテストケース定義書 TC-01〜TC-13 に対応する形で実装済み）。要点:

- `ENDPOINT_URL_KEY`/`MODEL_KEY`/`API_KEY_KEY` の3定数を定義（既存キーと衝突しない命名）。
- `getSettings()`: `dataStore.data.map { (endpointUrl, model) }.combine(encryptedApiKeyFlow()) { ..., apiKey -> LlmSettings(...) }`。
- `encryptedApiKeyFlow()`: `callbackFlow` で現在値を即時発行し、`OnSharedPreferenceChangeListener` で apiKey キーの変更のみ再発行。`awaitClose` でリスナー解除。
- `saveApiKey()`: `withContext(Dispatchers.IO)` 内で `encryptedPrefs.edit().putString(...).apply()`。
- `saveEndpointUrl()`/`saveModel()`: `dataStore.edit { ... }`。

## 3. テストのために修正したテストコード（テスト側のバグ修正）

Green フェーズ着手時、Red フェーズで作成したテストのうち2点に実装とは無関係な問題があり、テストコード側を修正した（仕様・期待値は変更していない）。

### 3.1 Robolectric SDK 未指定によるテスト初期化エラー

`LlmSettingsRepositoryImplTest.kt` / `LlmSettingsRepositoryImplInteractionTest.kt` に `@Config(sdk = [34])` が付与されておらず、targetSdk(36) を既定値として解決しようとして `DefaultSdkPicker` が `IllegalArgumentException` を送出していた（`initializationError`）。プロジェクト内の既存 Robolectric テスト（`ObsidianUriBuilderTest` 等）はすべて `@Config(sdk = [34])` を付与しており、本タスクの2ファイルのみ抜け落ちていた。両ファイルに `import org.robolectric.annotation.Config` と `@Config(sdk = [34])` を追加して解消。

### 3.2 TC-09 の競合状態（register 前に cancel していた）

`getSettingsCollectCancelled_unregistersSharedPreferenceListener`（TC-09）は `scope.launch { repo.getSettings().collect {} }` の直後に `collectJob.cancelAndJoin()` を呼んでいたが、`combine()` と `callbackFlow` は内部で子コルーチンを起動して `register...Listener` を呼び出すため、`Dispatchers.IO` 上のスケジューリング次第では `register` が一度も呼ばれないままキャンセルされ、`verify(exactly = 1) { registerOnSharedPreferenceChangeListener(any()) }` が失敗していた（3回連続実行で再現、флаky ではなく決定的な競合バグ）。

修正: `registerOnSharedPreferenceChangeListener` のモック応答に `CompletableDeferred<Unit>` を complete する処理を追加し、`collectJob` 起動後に `withTimeout(5_000) { registered.await() }` で register 完了を待ってから `cancelAndJoin()` するよう変更。検証内容（register → unregister が同一リスナーで対になって呼ばれること）は変更していない。

## 4. テスト実行結果

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmSettings*"
# BUILD SUCCESSFUL（14 tests, 3回再実行して全てSUCCESSFUL、フレーク無し確認済み）

mise exec -- ./gradlew testDebugUnitTest
# BUILD SUCCESSFUL（プロジェクト全体のJVMユニットテスト、既存テストへの影響なし）

mise exec -- ./gradlew test
# BUILD SUCCESSFUL（UP-TO-DATE、全variant）

mise exec -- ./gradlew compileDebugAndroidTestKotlin
# BUILD SUCCESSFUL（TC-08 を含む計器テストのコンパイル成功。deprecation警告のみ、エラー無し）
```

**環境注記**: この開発環境には adb/emulator が存在しないため、TC-08（計器テスト、実 EncryptedSharedPreferences による暗号化確認）は `connectedAndroidTest` で実機実行できない。ユーザー指示に従い、コンパイル成功をもって暫定確認とした。実機/エミュレータでの実行確認は別途デバイス環境で行うこと。

### テストケース別結果（JVM実行 13件 + 計器1件）

| No. | テスト名 | 結果 |
|-----|---------|------|
| TC-01 | saveEndpointUrl/saveModel → getSettings() 反映 | ✅ Pass |
| TC-02 | saveApiKey → getSettings() 反映 | ✅ Pass |
| TC-03 | LlmSettings デフォルト値・equals | ✅ Pass（2テスト） |
| TC-04 | 3項目統合読み出し | ✅ Pass |
| TC-05 | saveApiKey の保存先ルーティング（MockK verify） | ✅ Pass |
| TC-06 | 再保存で最新値反映 | ✅ Pass |
| TC-07 | apiKey が DataStore に平文混入しない | ✅ Pass |
| TC-08 | apiKey がファイルに平文で含まれない（計器） | ⏸ コンパイル成功のみ確認（実機未実行） |
| TC-09 | callbackFlow のリスナー解除 | ✅ Pass（テスト側競合状態を修正後） |
| TC-10 | 初期状態でデフォルト値 | ✅ Pass |
| TC-11 | 空文字列によるクリア | ✅ Pass |
| TC-12 | 2ソースの独立更新 | ✅ Pass |
| TC-13 | 特殊文字・長い文字列の往復 | ✅ Pass |

## 5. 品質判定

✅ 高品質:
- テスト結果: JVMユニットテスト13件（+equalsの補完1件で計14件）すべて成功。3回再実行し安定を確認。計器テスト（TC-08）はコンパイル成功のみ確認（環境上の制約、ユーザー指示どおり）。
- 実装品質: 既存 `NoteSettingsRepositoryImpl` パターンを踏襲したシンプルな実装。
- リファクタ箇所: `getSettings()`/`saveApiKey()` の日本語コメント量・`encryptedApiKeyFlow()` の可読性向上余地あり（Refactorフェーズで対応）。
- 機能的問題: なし。
- コンパイルエラー: なし（main/test/androidTest すべて成功）。
- ファイルサイズ: `LlmSettingsRepositoryImpl.kt` 116行、`LlmSettingsRepository.kt` 25行、`LlmSettings.kt` 16行。800行制限に対し十分小さい。
- モック使用: 実装コード（`app/src/main/...`）にモック・スタブは含まれていない。モックはテストコード（MockK）内でのみ使用。

## 6. 課題・改善点（Refactorフェーズで対応）

- `encryptedApiKeyFlow()` の `callbackFlow` パターンは本プロジェクト初採用であり、コメントで信頼性レベルを明示済みだが、可読性・保守性の観点で関数分割や命名の見直し余地がある。
- `LlmModule`（Hilt DI）は未実装（本タスクのスコープ外、後続タスクで対応）。
- TC-09 のテスト同期方法（`CompletableDeferred` によるポーリング回避）は他のテストにも応用できる可能性があり、テストユーティリティ化を検討してもよい（Refactorフェーズの任意対応）。

# TDD要件定義書: LlmSettings・LlmSettingsRepository・LlmSettingsRepositoryImpl

**機能名**: llm-settings-repository
**タスクID**: TASK-0058
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0058/llm-settings-repository-requirements.md`

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測をしている
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測をしている

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: LLM API接続設定（`endpointUrl` / `apiKey` / `model`）を永続化・読み出しする Repository を提供する。`endpointUrl` / `model` は DataStore Preferences に、`apiKey` は EncryptedSharedPreferences（暗号化ストレージ）に分離して保存し、双方の変更を単一の `Flow<LlmSettings>` として公開する。
- 🔵 **どのような問題を解決するか**: LLMによる本文リライト／タグ提案機能（llm-memo-rewrite）を利用するために、ユーザーが設定画面で入力したAPI接続情報を安全に保存し、アプリ全体から一貫して参照できるようにする。特にAPIキー（機微情報）を平文DataStoreに保存せず暗号化保存することで、情報漏洩リスクを低減する（REQ-401）。
- 🔵 **想定されるユーザー**: LLM連携機能を使う Share2Obsidian のエンドユーザー。直接の利用者は本Repositoryを注入される上位レイヤー（SettingsScreen ViewModel / LlmRewriteRepository 呼び出し元）。
- 🔵 **システム内での位置づけ**: データ層（`data/llm` パッケージ）の設定管理コンポーネント。既存 `NoteSettingsRepository`（vault/folder）と責務を分離した新規Repositoryとして、Hilt DI 経由で提供される。後続タスク TASK-0061（本文リライトUI）・TASK-0066 で消費される。
- **参照したEARS要件**: REQ-004, REQ-401, NFR-101, NFR-102
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`「LLM設定管理設計」（行129-146）、`docs/design/llm-memo-rewrite/interfaces.kt`「LLM 設定（新規）」（行85-117）

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 2.1 ドメインモデル `LlmSettings` 🔵

**信頼性**: 🔵 *interfaces.kt 行93-97・architecture.md 行138-144より*

```kotlin
data class LlmSettings(
    val endpointUrl: String = "",  // OpenAI互換 Chat Completions エンドポイント
    val apiKey: String = "",       // 暗号化ストレージ保存の機微情報
    val model: String = "",        // 使用モデル名
)
```

- 全フィールド `String`、デフォルト値は空文字列 `""`。

### 2.2 インターフェース `LlmSettingsRepository` の入出力 🔵

**信頼性**: 🔵 *interfaces.kt 行104-117より*

| メソッド | 入力 | 出力 | 保存先 |
|---------|------|------|--------|
| `getSettings()` | なし | `Flow<LlmSettings>` | DataStore + EncryptedSharedPreferences 合成 |
| `saveEndpointUrl(url: String)` | `url: String` | `Unit`（suspend） | DataStore（`llm_endpoint_url`） |
| `saveApiKey(apiKey: String)` | `apiKey: String` | `Unit`（suspend） | EncryptedSharedPreferences（`llm_api_key`） |
| `saveModel(model: String)` | `model: String` | `Unit`（suspend） | DataStore（`llm_model`） |

### 2.3 ストレージキー定義 🟡

**信頼性**: 🟡 *TASK-0058.md 実装詳細・note.md より（キー名の具体値は妥当な推測を含む）*

- `ENDPOINT_URL_KEY = stringPreferencesKey("llm_endpoint_url")`
- `MODEL_KEY = stringPreferencesKey("llm_model")`
- `API_KEY_KEY = "llm_api_key"`（EncryptedSharedPreferences のキー）

### 2.4 入出力の関係性・データフロー 🔵

**信頼性**: 🔵 *architecture.md 行136より*

- `getSettings()` は `dataStore.data.map { (endpointUrl, model) }` と `encryptedApiKeyFlow()` を `combine()` し、両ソースのいずれかが変化するたびに最新の `LlmSettings` を発行する。
- `saveEndpointUrl` / `saveModel` は DataStore を更新 → その `Flow` が再発行 → `getSettings()` に反映。
- `saveApiKey` は EncryptedSharedPreferences を更新 → `OnSharedPreferenceChangeListener` が発火 → `callbackFlow` が再発行 → `getSettings()` に反映。

- **参照したEARS要件**: REQ-004, REQ-401
- **参照した設計文書**: `interfaces.kt`（`LlmSettings` / `LlmSettingsRepository`）、`architecture.md`「LLM設定管理設計」

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **セキュリティ要件（REQ-401 / NFR-101）**: `apiKey` は EncryptedSharedPreferences（`MasterKey` + `PrefKeyEncryptionScheme.AES256_SIV` / `PrefValueEncryptionScheme.AES256_GCM`）に暗号化保存する。平文DataStore・平文SharedPreferencesへの保存を一切行わない。
- 🔵 **ログ出力禁止（NFR-102）**: `apiKey` をログ・例外メッセージ・クラッシュレポートに出力しない。EncryptedSharedPreferences 由来の例外オブジェクトをそのまま外部出力しない。
- 🔵 **スレッド制約**: `EncryptedSharedPreferences` は同期ブロッキングAPIのため、`saveApiKey()` は `withContext(Dispatchers.IO)` 内で実行する。`saveEndpointUrl()` / `saveModel()` は DataStore（内部で非同期処理）のため追加のIO切り替えは不要。
- 🔵 **責務分離制約**: `NoteSettings`（vault/folder）とは別Repositoryとして新設する。設定ストア（DataStoreファイル・EncryptedSharedPreferencesファイル）は既存と衝突しない名前を用いる。
- 🟡 **変更通知パターン制約**: EncryptedSharedPreferences の変更を Flow 化する `callbackFlow` + `OnSharedPreferenceChangeListener` パターンは本プロジェクトでの実績がなく妥当な推測。`callbackFlow` の `awaitClose` でリスナー登録解除を必須とする。実装時に別方式（都度読み取り等）への変更余地あり。
- 🔵 **アーキテクチャ制約**: 既存 `NoteSettingsRepositoryImpl`（`app/src/main/java/com/den4dr/share2Obsidian/data/datastore/NoteSettingsRepositoryImpl.kt`）の DataStore 実装パターン（`stringPreferencesKey()` + `map()` + `edit()`）を踏襲する。
- 🔵 **DI制約**: Hilt Module（新規 `LlmModule` または既存 `DataStoreModule` 拡張）で `EncryptedSharedPreferences` を Singleton として提供し、Repository に注入する。
- 🔵 **依存関係制約**: `androidx.security:security-crypto`（EncryptedSharedPreferences）・`androidx.datastore:datastore-preferences` が必要（前提タスク TASK-0055 で追加済み）。

- **参照したEARS要件**: REQ-401, NFR-101, NFR-102
- **参照した設計文書**: `architecture.md`「LLM設定管理設計」「技術的制約」、`interfaces.kt`

---

## 4. 想定される使用例（データフロー・エッジケースベース）

### 4.1 基本的な使用パターン 🔵

**信頼性**: 🔵 *REQ-004・interfaces.ktより*

1. ユーザーが設定画面で endpointUrl / apiKey / model を入力 → 上位ViewModelが `saveEndpointUrl()` / `saveApiKey()` / `saveModel()` を呼び出す。
2. LLMリライト実行時、消費側が `getSettings().first()` で現在の `LlmSettings` を取得し、`LlmRewriteRepository.rewrite()` に渡す（TASK-0059以降）。

### 4.2 データフロー 🔵

**信頼性**: 🔵 *architecture.md 行136より*

```
saveEndpointUrl/saveModel → DataStore.edit → dataStore.data Flow ┐
                                                                  ├─ combine → Flow<LlmSettings> → getSettings()
saveApiKey → EncryptedSharedPreferences.edit → listener発火 → callbackFlow ┘
```

### 4.3 エッジケース 🔵🟡

- 🔵 **初期状態（未保存）**: いずれのキーも未保存の場合、`getSettings()` は `LlmSettings("", "", "")` を発行する（各キーの `?: ""` フォールバック）。
- 🟡 **片方のみ更新**: `endpointUrl`/`model` 保存済みの状態で `saveApiKey()` のみ呼ぶと、`endpointUrl`/`model` は保持したまま `apiKey` のみ更新される（`combine()` の一般挙動からの妥当な推測）。
- 🔵 **APIキー暗号化確認**: `saveApiKey("sk-...")` 後、SharedPreferences 実ファイルに平文のAPIキー文字列が含まれない（暗号化されている）。

### 4.4 エラーケース 🟡

**信頼性**: 🟡 *TASK-0058に明示のエラー処理記載はなく、一般的な方針からの推測*

- 本タスク（設定の保存・読み出し）自体は原則例外を上位に伝播させない設計だが、EncryptedSharedPreferences 初期化失敗時の扱いは実装時に検討（キー生成失敗等）。API呼び出し失敗のエラー種別（NetworkError/AuthError/Timeout等）は TASK-0059 の `LlmRewriteRepository` の責務であり本タスク対象外。

- **参照したEARS要件**: REQ-004, REQ-401, NFR-101
- **参照した設計文書**: `architecture.md`「LLM設定管理設計」、`dataflow.md`

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: LLM設定入力（設定画面でのLLM API接続情報の入力・保存）
- **参照した機能要件**:
  - REQ-004: LLM APIエンドポイント・APIキー・モデル名を SettingsScreen で入力
  - REQ-401: APIキーは暗号化ストレージに保存（平文DataStore禁止）
- **参照した非機能要件**:
  - NFR-101: EncryptedSharedPreferences 等での暗号化保存
  - NFR-102: APIキーをログ・クラッシュレポートに出力しない
- **参照したEdgeケース**: 初期状態デフォルト値、片方ソースのみ更新、APIキー暗号化確認（本タスク独自の単体/統合テスト観点）
- **参照した受け入れ基準**: `docs/spec/llm-memo-rewrite/acceptance-criteria.md`（REQ-004/REQ-401 対応項目）
- **参照した設計文書**:
  - **アーキテクチャ**: `docs/design/llm-memo-rewrite/architecture.md`「LLM設定管理設計」（行129-146）、「技術的制約」
  - **データフロー**: `docs/design/llm-memo-rewrite/dataflow.md`
  - **型定義**: `docs/design/llm-memo-rewrite/interfaces.kt`「LLM 設定（新規）」（`LlmSettings` 行93-97 / `LlmSettingsRepository` 行104-117 / 実装例 行340-377 / `LlmModule` 例 行432-467）
  - **API仕様**: `docs/design/llm-memo-rewrite/api-endpoints.md`（本タスクは接続情報の保存のみ、呼び出しは TASK-0059）

---

## 6. 実装・テスト対象ファイル

### 新規実装ファイル
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt` 🔵
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt` 🔵
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt` 🟡
- `app/src/main/java/com/den4dr/share2Obsidian/di/LlmModule.kt`（新規）または `DataStoreModule.kt` 拡張 🔵

### テストファイル
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`（Robolectric + MockK）🔵
- `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`（実 EncryptedSharedPreferences 暗号化確認）🔵

### 参考実装
- `app/src/main/java/com/den4dr/share2Obsidian/data/datastore/NoteSettingsRepositoryImpl.kt`（DataStore パターン）🔵
- `app/src/main/java/com/den4dr/share2Obsidian/di/DataStoreModule.kt`（Hilt 提供パターン）🔵

---

## 7. 品質評価（信頼性レベル分布）

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 機能概要 | 4 | 0 | 0 | 4 |
| 入出力仕様 | 3 | 1 | 0 | 4 |
| 制約条件 | 6 | 1 | 0 | 7 |
| 使用例 | 4 | 2 | 0 | 6 |
| **合計** | **17** | **4** | **0** | **21** |

- 🔵 青信号: 17項目（81%）
- 🟡 黄信号: 4項目（19%）
- 🔴 赤信号: 0項目（0%）

**総合品質評価**: ✅ 高品質
- 要件の曖昧さ: なし
- 入出力定義: 完全（型・保存先・デフォルト値まで明確）
- 制約条件: 明確（セキュリティ/スレッド/DI）
- 実装可能性: 確実（既存 NoteSettingsRepository パターン + AndroidX Security 公式パターン）

**推測を含む主な項目**: EncryptedSharedPreferences の変更通知 Flow 化（`callbackFlow` + `OnSharedPreferenceChangeListener`）とストレージキー名の具体値。実装時に検証・調整の余地あり。

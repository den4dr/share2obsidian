# TASK-0061 設定作業実行

## 作業概要

- **タスクID**: TASK-0061
- **作業内容**: `HttpClient`・`EncryptedSharedPreferences`・`LlmSettingsRepository`・`LlmRewriteRepository` を Hilt Singleton 提供する新規モジュール `LlmModule` の実装
- **実行日時**: 2026-07-06 02:53 JST
- **実行者**: Claude Code (direct-setup)

## 設計文書参照

- **参照文書**:
  - `docs/tasks/llm-memo-rewrite/TASK-0061.md`
  - `docs/design/llm-memo-rewrite/architecture.md`（LLMリクエスト/レスポンス設計・LLM設定管理設計）
  - `docs/design/llm-memo-rewrite/api-endpoints.md`
  - 既存パターン: `app/src/main/java/com/den4dr/share2Obsidian/di/DataStoreModule.kt`, `di/DatabaseModule.kt`
- **関連要件**: REQ-401, REQ-402

## 前提確認

以下の前提タスクの成果物が既に実装済みであることを確認した。

- TASK-0058: `data/llm/LlmSettings.kt`, `LlmSettingsRepository.kt`, `LlmSettingsRepositoryImpl.kt`（コンストラクタ: `DataStore<Preferences>`, `SharedPreferences`）
- TASK-0060: `data/llm/LlmRewriteRepository.kt`, `LlmRewriteRepositoryImpl.kt`（コンストラクタ: `HttpClient`）
- TASK-0055（依存関係）: `gradle/libs.versions.toml`・`app/build.gradle.kts` に `ktor-client-cio`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, `androidx-security-crypto`, `hilt` が導入済み

## 実行した作業

### 1. `LlmModule.kt` の新規作成

**作成ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/di/LlmModule.kt`

既存の `DataStoreModule.kt`（`preferencesDataStore` 拡張プロパティパターン）・`DatabaseModule.kt`（`object` + `@Provides @Singleton` パターン）を踏襲し、以下4つのProvidesメソッドを実装した。

- `provideHttpClient()`: `HttpClient(CIO)` に `ContentNegotiation(json())` と `HttpTimeout(requestTimeoutMillis = 30_000)` をinstall
- `provideEncryptedSharedPreferences()`: `MasterKey(AES256_GCM)` + `EncryptedSharedPreferences.create()`（key: `"llm_secure_prefs"`, AES256_SIV/AES256_GCM）
- `provideLlmSettingsRepository()`: `context.llmSettingsDataStore`（新規 `preferencesDataStore(name = "llm_settings")`）と `EncryptedSharedPreferences` を注入して `LlmSettingsRepositoryImpl` を構築
- `provideLlmRewriteRepository()`: `HttpClient` を注入して `LlmRewriteRepositoryImpl` を構築

DataStore名は既存 `noteSettingsDataStore`（`"note_settings"`）と衝突しないよう `"llm_settings"` を新規採用した。

### 2. ビルド確認

```bash
mise exec -- ./gradlew build
```

**実行結果**: `BUILD SUCCESSFUL in 1m 4s`（111 actionable tasks: 89 executed, 22 up-to-date）。単体テスト（`testDebugUnitTest`）・lint含め全て成功。

`EncryptedSharedPreferences`/`MasterKey` の非推奨警告（deprecation warning）が出力されたが、これはAndroidX Securityライブラリ自体の仕様変更によるものであり、TASK-0058で既に採用されているAPIと同一のため、本タスクでは対応不要と判断した（既存実装との一貫性を優先）。

## 作業結果

- [x] `@Module @InstallIn(SingletonComponent::class) object LlmModule` を実装
- [x] `HttpClient(CIO)` に `HttpTimeout`（30秒）と `ContentNegotiation`（json）をinstall
- [x] `EncryptedSharedPreferences` を `MasterKey`（AES256_GCM）で構築
- [x] `mise exec -- ./gradlew build` が成功

## 遭遇した問題と解決方法

なし。前提タスク（TASK-0055, TASK-0058, TASK-0060）が計画通り完了していたため、設計文書記載のコード例をそのまま適用してビルド成功した。

## 次のステップ

- `/tsumiki:direct-verify` を実行してアプリ起動確認（`MainActivity` 起動時のHiltグラフ構築、クラッシュ有無）を実施
- 後続タスク TASK-0063, TASK-0066 で `EditScreenViewModel` 等への `LlmRewriteRepository`/`LlmSettingsRepository` 注入を実装

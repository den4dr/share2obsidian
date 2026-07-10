# LLMによるメモ更改機能の追加 設計ヒアリング記録

**作成日**: 2026-07-05
**ヒアリング実施**: step4 既存情報ベースの差分ヒアリング

## ヒアリング目的

要件定義書（requirements.md）・既存コードベース（EditScreenViewModel, TemplateApplicator, NoteSettings, TemplateEditScreen 等）を調査した結果、技術選定と既存アーキテクチャへの影響について確定が必要な項目が判明したため、設計着手前にヒアリングを実施しました。

## 質問と回答

### Q1: HTTPクライアント・JSONライブラリの選定

**質問日時**: 2026-07-05
**カテゴリ**: 技術選択
**背景**: 現在プロジェクトにHTTPクライアント・JSONライブラリが一切導入されていない（`jsoup` はHTML静的パース専用でネットワークI/Oを行わない）ため、LLM API呼び出し用に新規導入が必須。

**回答**: Ktor Client (CIO エンジン) + kotlinx-serialization

**信頼性への影響**: HTTPクライアント関連の設計項目（LlmRewriteRepositoryImpl, Hiltモジュール, 依存関係定義）を🔵で確定。

---

### Q2: EditScreenViewModel の Hilt 化

**質問日時**: 2026-07-05
**カテゴリ**: アーキテクチャ
**背景**: `EditScreenViewModel` は現状 Hilt 未対応の素の `ViewModel()` であり（`MainActivity` で `by viewModels()` により生成）、LLM呼び出し用の Repository をコンストラクタ注入するには `@HiltViewModel` 化が必要。既存の `SettingsViewModel`/`TemplateEditViewModel` はすでに `@HiltViewModel` パターンを採用している。

**回答**: `@HiltViewModel` 化を許容

**信頼性への影響**: `EditScreenViewModel` の設計変更（Hilt化・Repository注入・rewriteBody()等のメソッド追加）を🔵で確定。

---

### Q3: LLM呼び出し失敗時のエラー通知パターン

**質問日時**: 2026-07-05
**カテゴリ**: アーキテクチャ
**背景**: 既存のエラー通知（`ActivityNotFoundException` → Toast）は `MainActivity.onSend` コールバック内で同期的に発生するが、LLM呼び出しは `EditScreenViewModel` 内の非同期処理（ボタン押下起点）で発生するため、ViewModel からエラーを画面に伝達する手段を確定する必要があった。

**回答**: ViewModelのSharedFlowイベントをEditScreenで購読してToast表示

**信頼性への影響**: `EditScreenViewModel.errorEvents: SharedFlow<Int>`（string resource ID を流す一回限りイベント）の設計を🔵で確定。EditScreen 側で `LaunchedEffect` + `collectLatest` により購読し、`Toast.makeText(context, ...)` で表示する設計とする。

---

## ヒアリング結果サマリー

### 確認できた事項
- HTTP通信は Ktor Client (CIO) + kotlinx-serialization で実装する
- `EditScreenViewModel` は `@HiltViewModel` 化し、`LlmRewriteRepository` / `LlmSettingsRepository` をコンストラクタ注入する
- LLM呼び出しエラーは `SharedFlow` による一回限りイベントとして ViewModel → EditScreen へ伝達し、Toast表示する

### 設計方針の決定事項
- `NoteSettings`（vault/folder）とは別に、新規 `LlmSettings`（endpointUrl/apiKey/model）を新設する。`apiKey` のみ暗号化ストレージ（EncryptedSharedPreferences）に分離保存し、`endpointUrl`/`model` は既存の DataStore Preferences パターンを踏襲する
- `Template` に `bodyLlmPrompt: String` を追加。`TemplateField` に `llmPrompt: String` を追加し `FieldValueSource.LLM` を新設する
- タグ提案（REQ-301/302）は要件上テンプレート単位のプロンプト設定が明示されていないため、アプリ内固定のデフォルトプロンプト（string resource）を使用する設計とする（下記「残課題」参照）
- カスタムフィールドのLLM生成タイミング（REQ-304で未確定だった論点）は、body/tags と一貫性を持たせるため「EditScreen上のボタン押下時」に統一する設計とする

### 残課題
- タグ提案のプロンプトをテンプレート単位でカスタマイズ可能にすべきかどうかは、requirements.md のヒアリングで明示的に確認されていない。本設計では「アプリ内固定プロンプト」という保守的な選択をしたが、Should Have の実装着手前に再確認を推奨する
- カスタムフィールドのLLM生成タイミングを「EditScreen上のボタン押下時」とした判断は、REQ-304の🟡（黄信号）を設計レベルで妥当推測により解消したものであり、Could Have の実装着手前に再確認を推奨する

### 信頼性レベル分布

**ヒアリング前**（要件定義書のみの段階）:
- 要件定義書は🔵27/🟡3/🔴0だが、設計固有の技術選択（HTTPクライアント・Hilt化・エラー伝達パターン）は未定義だった

**ヒアリング後**（設計文書全体集計、architecture.md/interfaces.kt/database-schema.kt/dataflow.md/api-endpoints.md 集計）:
- 詳細は各ファイル末尾の信頼性レベルサマリー、および完了報告を参照

## 関連文書

- **アーキテクチャ設計**: [architecture.md](architecture.md)
- **データフロー**: [dataflow.md](dataflow.md)
- **型定義**: [interfaces.kt](interfaces.kt)
- **DBスキーマ**: [database-schema.kt](database-schema.kt)
- **外部API連携仕様**: [api-endpoints.md](api-endpoints.md)
- **要件定義**: [requirements.md](../../spec/llm-memo-rewrite/requirements.md)

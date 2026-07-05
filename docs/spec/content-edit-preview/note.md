# 展開内容の編集・プレビュー機能 コンテキストノート

**作成日**: 2026-03-29

## 技術スタック

- **言語**: Kotlin 2.2.10
- **Android Gradle Plugin**: 9.1.0
- **minSdk**: 33 (Android 13)
- **targetSdk/compileSdk**: 36
- **Java互換性**: 11
- **UI**: Jetpack Compose BOM 2024.09.00 (Material3)
- **非同期**: Kotlin Coroutines (lifecycleScope)
- **ViewModel**: AndroidX ViewModel (viewModels() delegate)
- **依存関係管理**: gradle/libs.versions.toml (Version Catalog)

## ビルドコマンド

```bash
./gradlew assembleDebug          # デバッグビルド
./gradlew test                   # ユニットテスト
./gradlew connectedAndroidTest   # インストゥルメントテスト
./gradlew lint                   # リントチェック
```

## 既存実装（share-content-expansion 完了済み）

| クラス | パッケージ | 役割 |
|--------|----------|------|
| `MainActivity` | root | エントリポイント（変更対象） |
| `AppConfig` | root | 設定定数 (VAULT/FOLDER/TAGS/TIMEOUT) |
| `ShareContent` | content | sealed class (Text/Url/Html/File) |
| `ContentTypeDetector` | content | Intent → ShareContent 判定 |
| `ContentProcessor<T>` | content | 処理インターフェース |
| `TextContentProcessor` | content | テキスト処理 |
| `UrlContentProcessor` | content | URL/WebView処理 |
| `HtmlContentProcessor` | content | HTML→Markdown変換 |
| `FileContentProcessor` | content | ファイル・Clipboard |
| `ProcessedContent` | content | 処理結果 (body, title?, contentType) |
| `FrontmatterBuilder` | format | Frontmatter生成（変更なし） |
| `ObsidianUriBuilder` | format | URI構築（変更なし） |
| `LoadingScreen` | ui | URL処理中ローディング画面 |
| `WebViewExtractor` | util | WebView本文抽出 |
| `HtmlToMarkdownConverter` | util | Jsoup変換 |

## 新規追加コンポーネント

| クラス/ファイル | パッケージ | 役割 |
|--------------|----------|------|
| `NoteConfig` | format | vault/folder/defaultTags設定（AppConfig非依存） |
| `NoteComposer` | format | 編集後の値でFrontmatter+URI生成 |
| `EditFormState` | root or ui | フォーム状態データクラス |
| `parseTagsText()` | root or ui | タグ文字列 → List<String> |
| `EditScreenViewModel` | ui | StateFlow<EditFormState>管理 |
| `EditScreen` | ui | 編集フォームComposable |

## 開発ルール

- 日本語文字列は `res/values/strings.xml` に定義する（NFR-103）
- UI は Compose Material3 コンポーネントを使用
- `FrontmatterBuilder` / `ObsidianUriBuilder` は変更しない（REQ-402）
- 単一アクティビティ（MainActivity のみ）を維持（REQ-401）
- エラー処理（ActivityNotFoundException）は既存パターンを踏襲

## 設計文書

- **アーキテクチャ**: `docs/design/content-edit-preview/architecture.md`
- **データフロー**: `docs/design/content-edit-preview/dataflow.md`
- **インターフェース定義**: `docs/design/content-edit-preview/interfaces.kt`
- **設計ヒアリング**: `docs/design/content-edit-preview/design-interview.md`

## 注意事項

- `EditScreenViewModel` は `viewModels()` デリゲートで Activity スコープに束縛する
- `initialize()` は ViewModel の初期化済みフラグで重複実行を防ぐ
- 画面回転対応（EDGE-101）は ViewModel の StateFlow で対応
- タグパース: `split(",").map { trim() }.filter { isNotEmpty() }`
- タイトル空文字の場合は `null` に変換して Frontmatter の title フィールドを省略（EDGE-001）

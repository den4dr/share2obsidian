# 共有内容展開システム コンテキストノート

**作成日**: 2026-03-28
**プロジェクト**: Share2Obsidian

## 技術スタック

- **言語**: Kotlin 2.2.10
- **UIフレームワーク**: Jetpack Compose (BOM 2024.09.00) ※現在のMainActivityでは未使用
- **ビルドシステム**: Gradle 9.3+ (Kotlin DSL)
- **minSdk**: 33 (Android 13)
- **targetSdk/compileSdk**: 36

## 現在の実装サマリー

`MainActivity.kt` のみがアプリロジックを担う。

```
ACTION_SEND (text/plain) 受信
→ EXTRA_TEXT, EXTRA_SUBJECT 抽出
→ "# Title\n\nText" 形式にフォーマット
→ obsidian://new?content=... でObsidian起動
→ finish()
```

## 拡張対象となる機能領域

| 領域 | 現状 | 今回の拡張 |
|------|------|----------|
| コンテンツタイプ | text/plain のみ | URL展開, HTML変換, ファイル/画像 |
| Obsidian URIパラメータ | content のみ | title, vault, folder 追加 |
| フォーマット | # タイトル + 本文 | Frontmatter追加 |
| エラーハンドリング | Obsidian未インストール時のみ | 変更なし |

## 重要な技術的考慮事項

### URL展開
- **実装方法**: Android WebView を使った内部処理
- WebViewは非同期処理が必要 → Kotlin Coroutinesとの組み合わせを検討
- WebViewのJavascriptInterface経由で本文テキストを抽出する
- ネットワーク権限 (`INTERNET`) が必要

### HTML→Markdown変換
- **採用予定ライブラリ**: markwon または html2md 系ライブラリ
- `EXTRA_HTML_TEXT` から取得したHTMLを変換
- `text/html` MIME typeの intent filter 追加が必要

### 画像/ファイル共有
- **実装方針**: クリップボード経由 (`ClipboardManager`)
- `EXTRA_STREAM` (Uri) を取得して `ClipData` に変換
- その後 Obsidian を開く（ファイルURIはノート本文に記載）
- `READ_MEDIA_IMAGES` パーミッション (API 33+) が必要な場合あり

### Vault/フォルダ設定
- **実装方針**: ハードコード（固定値をコードに埋め込み）
- MVP段階では設定画面なし
- 将来的に DataStore Preferences で設定可能にする拡張余地あり

### Frontmatter
- 含めるフィールド: `title`, `tags`（固定値）
- 形式:
  ```markdown
  ---
  title: "ページタイトル"
  tags: [shared]
  ---
  ```

## 既存コードのギャップ

1. ハードコードされた日本語文字列 (`strings.xml` 未使用)
2. URIエンコーディングの安全性（特殊文字でのURI破損リスク）
3. テストカバレッジ 0%（スタブのみ）
4. Compose依存関係が宣言されているが未使用

## 関連ファイル

- `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `gradle/libs.versions.toml`
- `docs/tech-stack.md`

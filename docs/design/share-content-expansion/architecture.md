# 共有内容展開システム アーキテクチャ設計

**作成日**: 2026-03-28
**関連要件定義**: [requirements.md](../../spec/share-content-expansion/requirements.md)
**ヒアリング記録**: [design-interview.md](design-interview.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: 要件定義書・ユーザヒアリングを参考にした確実な設計
- 🟡 **黄信号**: 要件定義書・ユーザヒアリングから妥当な推測による設計
- 🔴 **赤信号**: 要件定義書・ユーザヒアリングにない推測による設計

---

## システム概要 🔵

**信頼性**: 🔵 *requirements.md 概要・CLAUDE.mdより*

Share2Obsidian の「共有内容展開システム」は、Android の共有シートから受け取るコンテンツ（テキスト・URL・HTML・ファイル）を種別ごとに処理し、Frontmatter付きのObsidianノートとして転送するAndroidネイティブアプリ（単一Activity + 複数Processor クラス構成）。

## アーキテクチャパターン 🔵

**信頼性**: 🔵 *ユーザヒアリング（クラス分割あり）より*

- **パターン**: レイヤード + ストラテジーパターン（コンテンツタイプ別にProcessorを差し替え）
- **選択理由**: 現在のMainActivityへの全集中を解消し、コンテンツタイプごとの処理をテスタブルな独立クラスに分離するため

## コンポーネント構成 🔵

**信頼性**: 🔵 *ユーザヒアリング・requirements.mdより*

### エントリポイント層

| クラス | 役割 |
|--------|------|
| `MainActivity` | Intent受信、コンテンツタイプ判定のルーティング、Compose UI（プログレスバー）表示 |

### コンテンツ処理層

| クラス | 役割 | 対応要件 |
|--------|------|--------|
| `ContentTypeDetector` | インテントからコンテンツ種別を判定し `ShareContent` sealed class に変換 | REQ-001, REQ-101, REQ-201, REQ-301 |
| `TextContentProcessor` | `text/plain` テキストをそのまま処理 | REQ-001 |
| `UrlContentProcessor` | URL検出・WebViewで本文抽出 | REQ-101, REQ-102, REQ-103 |
| `HtmlContentProcessor` | HTML→Markdown変換（Jsoup使用） | REQ-201, REQ-202, REQ-203 |
| `FileContentProcessor` | `EXTRA_STREAM` をClipboardManagerでコピー | REQ-301, REQ-302 |

### フォーマット層

| クラス | 役割 | 対応要件 |
|--------|------|--------|
| `FrontmatterBuilder` | title・tagsフィールドのFrontmatterヘッダーを生成 | REQ-003 |
| `ObsidianUriBuilder` | `obsidian://new` URIを構築（content/title/vault/folder） | REQ-002, REQ-004, REQ-005 |

### インフラ層

| クラス | 役割 |
|--------|------|
| `WebViewExtractor` | 非表示WebView + JavascriptInterface で本文テキストを抽出 |
| `HtmlToMarkdownConverter` | Jsoupベースの HTML→Markdown 変換ユーティリティ |

## システム構成図

```
Android Share Sheet
        │ ACTION_SEND intent
        ▼
┌─────────────────────────────────────────────┐
│            MainActivity                      │
│  ┌──────────────────────────────────┐       │
│  │      ContentTypeDetector         │       │
│  │  text/plain + URL? → UrlContent  │       │
│  │  text/plain → TextContent        │       │
│  │  text/html → HtmlContent         │       │
│  │  image/* / application/* → File  │       │
│  └──────────────────────────────────┘       │
│         │                                    │
│  ┌──────▼──────────────────────────┐        │
│  │   ContentProcessor (Strategy)    │        │
│  │  ┌──────────┐ ┌──────────────┐  │        │
│  │  │   Text   │ │ Url(WebView) │  │        │
│  │  └──────────┘ └──────────────┘  │        │
│  │  ┌──────────┐ ┌──────────────┐  │        │
│  │  │   Html   │ │    File      │  │        │
│  │  │  (Jsoup) │ │(Clipboard)   │  │        │
│  │  └──────────┘ └──────────────┘  │        │
│  └──────────────────────────────────┘        │
│         │ ProcessedContent                   │
│  ┌──────▼──────────────────────────┐        │
│  │     FrontmatterBuilder           │        │
│  │     ObsidianUriBuilder           │        │
│  └──────────────────────────────────┘        │
└─────────────────────────────────────────────┘
        │ obsidian://new?...
        ▼
   Obsidian App
```

## ディレクトリ構造 🔵

**信頼性**: 🔵 *既存プロジェクト構造・ユーザヒアリングより*

```
app/src/main/java/com/den4dr/share2Obsidian/
├── MainActivity.kt                  # エントリポイント・ルーティング・UI
├── content/
│   ├── ShareContent.kt              # sealed class (Text/Url/Html/File)
│   ├── ContentTypeDetector.kt       # Intent → ShareContent 判定
│   ├── ContentProcessor.kt          # interface ContentProcessor
│   ├── TextContentProcessor.kt
│   ├── UrlContentProcessor.kt       # WebView + Coroutines
│   ├── HtmlContentProcessor.kt      # Jsoup変換
│   └── FileContentProcessor.kt      # ClipboardManager
├── format/
│   ├── FrontmatterBuilder.kt        # Frontmatter生成
│   └── ObsidianUriBuilder.kt        # obsidian:// URI構築
├── ui/
│   ├── LoadingScreen.kt             # Compose プログレスバー画面
│   └── theme/                       # 既存テーマ
└── util/
    ├── WebViewExtractor.kt          # WebView本文抽出ヘルパー
    └── HtmlToMarkdownConverter.kt   # Jsoupベース変換
```

## 非機能要件の実現方法

### パフォーマンス 🟡

**信頼性**: 🟡 *NFR-001, NFR-002から妥当な推測*

- **WebViewタイムアウト**: `WebViewExtractor` に 10秒タイムアウトを設定
- **UIブロッキング防止**: URL処理中はプログレスバー付きActivityを表示してユーザーに状態を伝える
- **Coroutines**: `lifecycleScope.launch` でWebView処理をメインスレッドをブロックせずに実行

### セキュリティ 🔵

**信頼性**: 🔵 *NFR-101, NFR-102・要件定義より*

- **URIエンコーディング**: `Uri.Builder.appendQueryParameter()` を使用（自動エンコード）
- **ファイルアクセス**: `ContentResolver.openInputStream()` 経由のみ使用
- **WebViewJS制限**: `WebSettings.javaScriptEnabled = true` は本文抽出のみに限定

### 互換性制約 🔵

**信頼性**: 🔵 *CLAUDE.md・tech-stack.mdより*

- **minSdk 33**: Android 13以上のみ。`READ_MEDIA_IMAGES` パーミッションが必要
- **Kotlin 2.2+**: Coroutines 1.8+と組み合わせて使用

## 追加する依存関係 🔵

**信頼性**: 🔵 *ユーザヒアリング（Jsoup利用）・REQ-405より*

`gradle/libs.versions.toml` に追加:

```toml
[versions]
jsoup = "1.18.3"

[libraries]
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
```

`app/build.gradle.kts` に追加:

```kotlin
dependencies {
    implementation(libs.jsoup)
}
```

`AndroidManifest.xml` に追加:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 関連文書

- **データフロー**: [dataflow.md](dataflow.md)
- **Kotlinデータクラス**: [interfaces.kt](interfaces.kt)
- **要件定義**: [requirements.md](../../spec/share-content-expansion/requirements.md)

## 信頼性レベルサマリー

- 🔵 青信号: 12件（75%）
- 🟡 黄信号: 4件（25%）
- 🔴 赤信号: 0件（0%）

**品質評価**: 高品質

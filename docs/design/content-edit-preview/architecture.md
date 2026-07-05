# 展開内容の編集・プレビュー機能 アーキテクチャ設計

**作成日**: 2026-03-29
**関連要件定義**: [requirements.md](../../spec/content-edit-preview/requirements.md)
**ヒアリング記録**: [design-interview.md](design-interview.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: 要件定義書・ユーザヒアリングを参考にした確実な設計
- 🟡 **黄信号**: 要件定義書・ユーザヒアリングから妥当な推測による設計
- 🔴 **赤信号**: 要件定義書・ユーザヒアリングにない推測による設計

---

## システム概要 🔵

**信頼性**: 🔵 *requirements.md 概要・ユーザヒアリングより*

share-content-expansion（14タスク完了）の基盤の上に、Obsidian送信前の編集画面を追加する。全コンテンツタイプ（テキスト・URL・HTML・ファイル）のコンテンツ処理完了後に編集画面を表示し、ユーザーがタイトル・本文・タグ・保存先フォルダを確認・修正してから送信できるようにする。

## アーキテクチャパターン 🔵

**信頼性**: 🔵 *REQ-401（シングルアクティビティ）・既存設計より*

- **パターン**: 既存のレイヤード + ストラテジーパターンを維持し、フォーマット層と処理層の間に編集画面層を追加
- **選択理由**: 既存 `ContentProcessor` → `FrontmatterBuilder` → `ObsidianUriBuilder` のパイプラインを壊さず、EditScreen を追加のステップとして挿入するため

## コンポーネント構成 🔵

**信頼性**: 🔵 *requirements.md・ユーザヒアリング・既存設計より*

### 編集画面層（新規追加）

| クラス | 役割 | 対応要件 |
|--------|------|--------|
| `EditScreen` | 4フィールド + 送信/キャンセルボタンのCompose UI | REQ-003, REQ-004 |
| `EditScreenViewModel` | フォーム状態管理（StateFlow）・タグパース | REQ-101, REQ-103 |

### フォーマット層（変更あり：NoteComposer追加）

| クラス | 役割 | 対応要件 |
|--------|------|--------|
| `NoteComposer` | **新規**: 編集後の値から Frontmatter文字列・Obsidian URI を生成。明示的パラメータを受け取る（AppConfig 非依存） | REQ-101, REQ-103 |
| `NoteConfig` | **新規**: vault/folder/tags を保持するデータクラス。将来のユーザー設定に対応 | REQ-405 |
| `FrontmatterBuilder` | **変更なし** (REQ-402) | 既存 |
| `ObsidianUriBuilder` | **変更なし** (REQ-402) | 既存 |

### 既存コンポーネント（変更なし）

| クラス | 役割 |
|--------|------|
| `ContentTypeDetector` | Intent → ShareContent 判定（変更なし） |
| `TextContentProcessor` | テキスト処理（変更なし） |
| `UrlContentProcessor` | URL・WebView処理（変更なし） |
| `HtmlContentProcessor` | HTML→Markdown変換（変更なし） |
| `FileContentProcessor` | ファイル・Clipboard処理（変更なし） |
| `LoadingScreen` | URL処理中のローディング画面（変更なし） |

### 変更対象

| クラス | 変更内容 |
|--------|--------|
| `MainActivity` | 処理完了後の即時Obsidian起動を撤廃→EditScreen表示に変更 |

## システム構成図 🔵

**信頼性**: 🔵 *REQ-001, REQ-101, REQ-201, REQ-301・既存設計より*

```
Android Share Sheet
        │ ACTION_SEND intent
        ▼
┌─────────────────────────────────────────────────────────┐
│                     MainActivity                         │
│  ┌─────────────────────────────────────────────┐        │
│  │           ContentTypeDetector                │        │
│  └─────────────────────────────────────────────┘        │
│         │ ShareContent                                    │
│  ┌──────▼──────────────────────────────────────┐        │
│  │   ContentProcessor (Strategy, 変更なし)       │        │
│  │  Text / Url(LoadingScreen) / Html / File     │        │
│  └──────────────────────────────────────────────┘        │
│         │ ProcessedContent                                │
│  ┌──────▼──────────────────────────────────────┐        │
│  │            EditScreen (新規)                  │        │
│  │  ┌──────────────────────────────────────┐   │        │
│  │  │         EditScreenViewModel           │   │        │
│  │  │  title / body / tagsText / folder     │   │        │
│  │  └──────────────────────────────────────┘   │        │
│  │  [タイトル] [本文] [タグ] [フォルダ]            │        │
│  │  [     送信     ] [   キャンセル   ]          │        │
│  └──────────────────────────────────────────────┘        │
│         │ onSend: 編集後の値                              │
│  ┌──────▼──────────────────────────────────────┐        │
│  │            NoteComposer (新規)               │        │
│  │  buildFrontmatter(title, body, tags)         │        │
│  │  buildUri(content, title, NoteConfig)        │        │
│  └──────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────┘
        │ obsidian://new?...
        ▼
   Obsidian App
```

## ディレクトリ構造 🔵

**信頼性**: 🔵 *既存プロジェクト構造・REQ-401より*

```
app/src/main/java/com/den4dr/share2Obsidian/
├── MainActivity.kt                  # 変更: EditScreen表示フローへ切り替え
├── content/                         # 変更なし
│   ├── ShareContent.kt
│   ├── ContentTypeDetector.kt
│   ├── ContentProcessor.kt
│   ├── TextContentProcessor.kt
│   ├── UrlContentProcessor.kt
│   ├── HtmlContentProcessor.kt
│   └── FileContentProcessor.kt
├── format/
│   ├── FrontmatterBuilder.kt        # 変更なし（REQ-402）
│   ├── ObsidianUriBuilder.kt        # 変更なし（REQ-402）
│   ├── NoteComposer.kt              # 新規: 編集後の値でFrontmatter+URI生成
│   └── NoteConfig.kt               # 新規: vault/folder/tags設定（将来の拡張ポイント）
├── ui/
│   ├── EditScreen.kt                # 新規: 編集フォームComposable
│   ├── EditScreenViewModel.kt       # 新規: フォーム状態管理ViewModel
│   ├── LoadingScreen.kt             # 変更なし
│   └── theme/
└── util/
    ├── WebViewExtractor.kt          # 変更なし
    └── HtmlToMarkdownConverter.kt   # 変更なし
```

## NoteComposer と NoteConfig の設計方針 🔵

**信頼性**: 🔵 *REQ-402・ユーザヒアリング（将来のユーザー設定化）より*

### FrontmatterBuilder・ObsidianUriBuilder を変更しない理由（REQ-402）

既存の `FrontmatterBuilder` と `ObsidianUriBuilder` は `AppConfig` の値を内部で参照する。これらはそのまま存在させ、**新規の `NoteComposer`** が編集画面からの明示的なパラメータを受け取って同等の文字列・URIを生成する。

### 将来のユーザー設定に対応する設計

`NoteConfig` は現在 `AppConfig` の値をデフォルトとして使用するが、将来的にユーザーが vault・folder・tags を設定できるようにする拡張ポイントとなる。

```
現在: NoteConfig.fromAppConfig()    → AppConfig の値を使用
将来: NoteConfig.fromUserSettings() → ユーザー設定 DB/SharedPreferences から取得
```

EditScreenViewModel は `NoteConfig` を受け取り、フォームの初期値として使用する。送信時は ViewModel から `NoteConfig` を含む送信パラメータを取得し、`NoteComposer` に渡す。

## MainActivity フロー変更 🔵

**信頼性**: 🔵 *REQ-001, REQ-301・既存実装より*

### 変更前
```
処理完了 → FrontmatterBuilder.build() → ObsidianUriBuilder.build() → startActivity → finish()
```

### 変更後
```
処理完了 → viewModel.initialize(processed, NoteConfig.fromAppConfig())
         → setContent { EditScreen(viewModel, onSend, onCancel) }
         → [ユーザー編集]
         → onSend → NoteComposer.buildFrontmatter() → NoteComposer.buildUri() → startActivity → finish()
         → onCancel → finish()
```

### URL処理の変更 🔵

**信頼性**: 🔵 *REQ-301・既存LoadingScreen実装より*

```
URL受信 → setContent { LoadingScreen() }  // 変更なし
        → UrlContentProcessor.process()   // 変更なし
        → viewModel.initialize(processed, NoteConfig.fromAppConfig())
        → setContent { EditScreen(...) }  // LoadingScreen → EditScreen に切り替え
```

## 非機能要件の実現方法

### 状態保持（画面回転） 🟡

**信頼性**: 🟡 *EDGE-101・Compose + ViewModel パターンから妥当な推測*

- `EditScreenViewModel` を使用（`viewModels()` デリゲート）
- ViewModel の `StateFlow<EditFormState>` が画面回転をまたいで保持される
- `ProcessedContent` の初期化は `viewModel.initialized` フラグで重複実行を防ぐ

### パフォーマンス 🔵

**信頼性**: 🔵 *NFR-001, NFR-002・要件定義より*

- テキスト・HTML・ファイル: 処理完了まで空白画面（< 100ms）→ EditScreen表示
- URL: 既存 LoadingScreen → WebView抽出完了後 → EditScreen表示

### セキュリティ 🔵

**信頼性**: 🔵 *既存設計・NFR-101より*

- URIエンコーディング: `NoteComposer.buildUri()` 内で `Uri.Builder.appendQueryParameter()` を使用
- 既存の ObsidianUriBuilder と同等のエンコード処理を維持

## 関連文書

- **データフロー**: [dataflow.md](dataflow.md)
- **Kotlinデータクラス**: [interfaces.kt](interfaces.kt)
- **ヒアリング記録**: [design-interview.md](design-interview.md)
- **要件定義**: [requirements.md](../../spec/content-edit-preview/requirements.md)
- **既存アーキテクチャ**: [share-content-expansion/architecture.md](../share-content-expansion/architecture.md)

## 信頼性レベルサマリー

- 🔵 青信号: 12件 (80%)
- 🟡 黄信号: 3件 (20%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: 高品質

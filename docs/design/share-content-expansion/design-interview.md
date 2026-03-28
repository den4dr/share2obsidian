# 共有内容展開システム 設計ヒアリング記録

**作成日**: 2026-03-28
**ヒアリング実施**: step4 既存情報ベースの差分ヒアリング

## ヒアリング目的

要件定義書・既存実装を確認し、「共有内容展開システム」の具体的な設計方針（アーキテクチャ、技術選択、UI）を明確化するためのヒアリングを実施した。

---

## 質問と回答

### Q1: アーキテクチャのクラス分割方針

**質問日時**: 2026-03-28
**カテゴリ**: アーキテクチャ
**背景**: 現状はMainActivityに全ロジックが26行で集中しているが、URL展開・HTML変換・ファイル処理が加わると複雑化する。テスタブルな設計のためのクラス分割の要否を確認。

**回答**: 「クラス分割あり（推奨）」

**信頼性への影響**:
- Handler/Processor クラス群（architecture.md のコンポーネント構成）: 🔴 → 🔵 に向上
- ContentTypeDetector, TextContentProcessor, UrlContentProcessor, HtmlContentProcessor, FileContentProcessor, FrontmatterBuilder, ObsidianUriBuilder の設計が確定

---

### Q2: WebView非同期処理の実行場所

**質問日時**: 2026-03-28
**カテゴリ**: 技術選択
**背景**: WebViewは非同期処理が必要。Coroutines で Activity を維持したまま処理するか、バックグラウンドサービスで処理するかを確認。

**回答**: 「Activityを表示したまま処理」

**信頼性への影響**:
- dataflow.md のフロー2（URL展開）に `lifecycleScope.launch` パターンが確定: 🔴 → 🔵
- バックグラウンドサービスの使用は不要と確定

---

### Q3: URL展開時のローディングUI

**質問日時**: 2026-03-28
**カテゴリ**: UI/UX
**背景**: URL取得中の体験設計（UIなし / プログレスバーActivity / 通知シェード）を確認。

**回答**: 「プログレスバーありのActivityを表示」

**信頼性への影響**:
- `LoadingScreen.kt`（Composeプログレスバー画面）の設計が確定: 🔴 → 🔵
- architecture.md の `ui/LoadingScreen.kt` が必須コンポーネントと確定

---

### Q4: HTML変換ライブラリの選定

**質問日時**: 2026-03-28
**カテゴリ**: 技術選択
**背景**: HTML→Markdown変換に3種の選択肢（HtmlCompat/markwon/Jsoup）があり、ライブラリ利用の方針（REQ-202）は決定済みだが具体的なライブラリが未確定だった。

**回答**: 「html2text (Jsoup利用)」

**信頼性への影響**:
- `HtmlToMarkdownConverter.kt` の実装方針が確定: 🔴 → 🔵
- 依存関係として Jsoup (`org.jsoup:jsoup`) を追加することが確定
- architecture.md の依存関係追加セクションが確定

---

## ヒアリング結果サマリー

### 確認できた事項

- クラス分割: ContentProcessor ストラテジーパターンを採用
- UI: URL処理中はプログレスバー付きActivityを表示（Jetpack Compose使用）
- 非同期: `lifecycleScope.launch` + Activity維持
- HTML変換: Jsoup (`org.jsoup:jsoup`) を使用

### 設計方針の決定事項

| 項目 | 決定内容 |
|------|--------|
| アーキテクチャパターン | レイヤード + ストラテジー（Processorクラス群） |
| 非同期処理 | `lifecycleScope.launch` + `WebViewExtractor` |
| ローディングUI | Compose `CircularProgressIndicator` |
| HTML変換ライブラリ | Jsoup 1.18.3+ |
| ファイルコピー | `ClipboardManager.setPrimaryClip()` |

### 残課題

- Vault名・Folder名の具体的な値（prep.md参照）
- WebViewのJS抽出スクリプトの詳細実装（実装時に判断）
- URL長上限超過時の対応（EDGE-101: 未確認）

### 信頼性レベル分布

**ヒアリング前**:
- 🔵 青信号: 2件
- 🟡 黄信号: 5件
- 🔴 赤信号: 8件

**ヒアリング後**:
- 🔵 青信号: 13件 (+11)
- 🟡 黄信号: 5件 (0)
- 🔴 赤信号: 0件 (-8)

---

## 関連文書

- **アーキテクチャ設計**: [architecture.md](architecture.md)
- **データフロー**: [dataflow.md](dataflow.md)
- **Kotlinデータクラス**: [interfaces.kt](interfaces.kt)
- **要件定義**: [requirements.md](../../spec/share-content-expansion/requirements.md)

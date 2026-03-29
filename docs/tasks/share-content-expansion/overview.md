# 共有内容展開システム タスク概要

**作成日**: 2026-03-28
**プロジェクト期間**: Phase 1〜6（7日間 / 14 半日）
**推定工数**: 56時間
**総タスク数**: 14件

## 関連文書

- **要件定義書**: [📋 requirements.md](../spec/share-content-expansion/requirements.md)
- **設計文書**: [📐 architecture.md](../design/share-content-expansion/architecture.md)
- **データフロー図**: [🔄 dataflow.md](../design/share-content-expansion/dataflow.md)
- **Kotlinインターフェース**: [📝 interfaces.kt](../design/share-content-expansion/interfaces.kt)
- **準備タスク**: [🔧 prep.md](../spec/share-content-expansion/prep.md)

## フェーズ構成

| フェーズ | 成果物 | タスク数 | 工数 | ファイル |
|---------|--------|----------|------|----------|
| Phase 1 - 基盤構築 | Jsoup追加・データクラス定義 | 2件 | 8h | TASK-0001〜0002 |
| Phase 2 - フォーマット層 | FrontmatterBuilder・ObsidianUriBuilder | 2件 | 8h | TASK-0003〜0004 |
| Phase 3 - コンテンツ検出・テキスト処理 | ContentTypeDetector・TextProcessor・HtmlToMarkdown | 3件 | 12h | TASK-0005〜0007 |
| Phase 4 - HTML・ファイル処理 | HtmlContentProcessor・FileContentProcessor | 2件 | 8h | TASK-0008〜0009 |
| Phase 5 - URL処理・UI | WebViewExtractor・UrlContentProcessor・LoadingScreen | 3件 | 12h | TASK-0010〜0012 |
| Phase 6 - 統合・仕上げ | MainActivity統合・strings.xml | 2件 | 8h | TASK-0013〜0014 |

## タスク番号管理

**使用済みタスク番号**: TASK-0001 〜 TASK-0014
**次回開始番号**: TASK-0015

## 全体進捗

- [ ] Phase 1: 基盤構築
- [ ] Phase 2: フォーマット層実装
- [ ] Phase 3: コンテンツ検出・テキスト処理
- [ ] Phase 4: HTML・ファイル処理
- [ ] Phase 5: URL処理・UI実装
- [ ] Phase 6: 統合・仕上げ

## マイルストーン

- **M1: 基盤完成**: TASK-0001〜0002完了 → データクラス・Jsoup利用可能
- **M2: フォーマット層完成**: TASK-0003〜0004完了 → Frontmatter・URI生成可能
- **M3: 全Processor完成**: TASK-0005〜0012完了 → 各コンテンツタイプ処理可能
- **M4: 統合完了**: TASK-0013〜0014完了 → リリース可能な状態

---

## Phase 1: 基盤構築

**目標**: Jsoup 依存追加、INTERNET パーミッション追加、型定義ファイル作成
**成果物**: ビルド可能な基盤 + データクラス群

### タスク一覧

- [x] [TASK-0001: 依存パッケージ追加とプロジェクト設定](TASK-0001.md) - 4h (DIRECT) 🔵
- [x] [TASK-0002: ShareContentデータクラス・インターフェース定義](TASK-0002.md) - 4h (DIRECT) 🔵

### 依存関係

```
TASK-0001 → TASK-0002
```

---

## Phase 2: フォーマット層実装

**目標**: Frontmatter 生成・Obsidian URI 構築クラスの実装
**成果物**: FrontmatterBuilder, ObsidianUriBuilder（テスト済み）

### タスク一覧

- [x] [TASK-0003: FrontmatterBuilder実装とテスト](TASK-0003.md) - 4h (TDD) 🔵
- [x] [TASK-0004: ObsidianUriBuilder実装とテスト](TASK-0004.md) - 4h (TDD) 🔵

### 依存関係

```
TASK-0002 → TASK-0003
TASK-0002 → TASK-0004
```

---

## Phase 3: コンテンツ検出・テキスト処理

**目標**: Intent 解析・テキスト/HTML Markdown 変換クラスの実装
**成果物**: ContentTypeDetector, TextContentProcessor, HtmlToMarkdownConverter（テスト済み）

### タスク一覧

- [x] [TASK-0005: ContentTypeDetector実装とテスト](TASK-0005.md) - 4h (TDD) 🔵
- [x] [TASK-0006: TextContentProcessor実装とテスト](TASK-0006.md) - 4h (TDD) 🔵
- [x] [TASK-0007: HtmlToMarkdownConverter実装とテスト](TASK-0007.md) - 4h (TDD) 🔵

### 依存関係

```
TASK-0002 → TASK-0005
TASK-0002 → TASK-0006
TASK-0001 → TASK-0007
TASK-0002 → TASK-0007
```

---

## Phase 4: HTML・ファイル処理

**目標**: HTML→Markdown 変換・ファイルクリップボードコピー処理の実装
**成果物**: HtmlContentProcessor, FileContentProcessor（テスト済み）

### タスク一覧

- [x] [TASK-0008: HtmlContentProcessor実装とテスト](TASK-0008.md) - 4h (TDD) 🔵
- [x] [TASK-0009: FileContentProcessor実装とテスト](TASK-0009.md) - 4h (TDD) 🔵

### 依存関係

```
TASK-0007 → TASK-0008
TASK-0002 → TASK-0009
```

---

## Phase 5: URL処理・UI実装

**目標**: WebView 本文抽出・URL Processor・ローディング画面の実装
**成果物**: WebViewExtractor, UrlContentProcessor, LoadingScreen（テスト済み）

### タスク一覧

- [x] [TASK-0010: WebViewExtractor実装とテスト](TASK-0010.md) - 4h (TDD) 🔵
- [x] [TASK-0011: UrlContentProcessor実装とテスト](TASK-0011.md) - 4h (TDD) 🔵
- [x] [TASK-0012: LoadingScreen UI実装とテスト](TASK-0012.md) - 4h (TDD) 🔵

### 依存関係

```
TASK-0001 → TASK-0010
TASK-0002 → TASK-0010
TASK-0010 → TASK-0011
TASK-0002 → TASK-0012
```

---

## Phase 6: 統合・仕上げ

**目標**: MainActivity リファクタリング・エラーメッセージ追加・最終確認
**成果物**: 完全動作するアプリ

### タスク一覧

- [x] [TASK-0013: MainActivity統合リファクタリング](TASK-0013.md) - 4h (TDD) 🔵
- [ ] [TASK-0014: strings.xmlとエラーハンドリング追加](TASK-0014.md) - 4h (DIRECT) 🔵

### 依存関係

```
TASK-0003 → TASK-0013
TASK-0004 → TASK-0013
TASK-0005 → TASK-0013
TASK-0006 → TASK-0013
TASK-0008 → TASK-0013
TASK-0009 → TASK-0013
TASK-0011 → TASK-0013
TASK-0012 → TASK-0013
TASK-0013 → TASK-0014
```

---

## 信頼性レベルサマリー

### 全タスク統計

- **総タスク数**: 14件
- 🔵 **青信号**: 13件（93%）
- 🟡 **黄信号**: 1件（7%）
- 🔴 **赤信号**: 0件（0%）

### フェーズ別信頼性

| フェーズ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| Phase 1 | 2 | 0 | 0 | 2 |
| Phase 2 | 2 | 0 | 0 | 2 |
| Phase 3 | 3 | 0 | 0 | 3 |
| Phase 4 | 2 | 0 | 0 | 2 |
| Phase 5 | 2 | 1 | 0 | 3 |
| Phase 6 | 2 | 0 | 0 | 2 |

**品質評価**: ✅ 高品質

## クリティカルパス

```
TASK-0001 → TASK-0002 → TASK-0007 → TASK-0008 → TASK-0013 → TASK-0014
```

**クリティカルパス工数**: 24時間（6半日）
**並行作業可能工数**: 32時間

## 実装前の必須確認事項

⚠️ **実装開始前に prep.md の必須タスクを完了してください**:

1. **OBSIDIAN_VAULT の設定**: `AppConfig.kt` の `OBSIDIAN_VAULT` を実際の Vault 名に変更
2. **OBSIDIAN_FOLDER の設定**: `AppConfig.kt` の `OBSIDIAN_FOLDER` を実際のフォルダ名に変更
3. **固定 tags の確認**: `AppConfig.OBSIDIAN_TAGS` が希望の値になっているか確認

詳細: [prep.md](../spec/share-content-expansion/prep.md)

## 次のステップ

タスクを実装するには:
- 全タスク順番に実装: `/tsumiki:kairo-implement`
- 特定タスクを実装: `/tsumiki:kairo-implement TASK-0001`

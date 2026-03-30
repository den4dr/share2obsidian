# TASK-0015: NoteConfig + NoteComposer 実装 - TDD要件定義書

**タスクID**: TASK-0015
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-03-29
**フェーズ**: Phase 1 - 基盤実装

---

## 1. 機能の概要

### 何をする機能か 🔵

**信頼性**: 🔵 *EARS要件定義書 REQ-101, REQ-103, REQ-402, REQ-405・設計文書 interfaces.kt より*

- `NoteConfig` データクラス: Obsidian ノート送信の設定（vault / folder / defaultTags）を保持する。`AppConfig` に依存せず明示的パラメータで動作し、将来のユーザー設定化への拡張ポイントとなる。
- `NoteComposer` オブジェクト: 編集後の値（タイトル・本文・タグ・設定）から Frontmatter 文字列と Obsidian URI を生成する。既存の `FrontmatterBuilder` / `ObsidianUriBuilder` は変更しない（REQ-402）。

### どのような問題を解決するか 🔵

**信頼性**: 🔵 *requirements.md 概要・ユーザヒアリングより*

- 現状の `FrontmatterBuilder` / `ObsidianUriBuilder` は `AppConfig` の固定値を内部参照しており、編集画面でユーザーが変更した値（タグ・フォルダなど）を反映できない。
- `NoteComposer` は明示的パラメータを受け取ることで、編集画面からの動的な値を反映した Frontmatter / URI を生成できる。

### 想定されるユーザー 🔵

**信頼性**: 🔵 *ユーザストーリーより*

- 他アプリからコンテンツを共有し、Obsidian に保存する前に内容を編集したいユーザー

### システム内での位置づけ 🔵

**信頼性**: 🔵 *architecture.md コンポーネント構成・ディレクトリ構造より*

- フォーマット層（`format/` パッケージ）に配置
- 編集画面層（`EditScreenViewModel` → `NoteComposer`）の下位コンポーネントとして機能
- 既存の `FrontmatterBuilder` / `ObsidianUriBuilder` と並存し、編集画面フロー専用のビルダーとして使われる

**参照したEARS要件**: REQ-101, REQ-103, REQ-402, REQ-405
**参照した設計文書**: architecture.md コンポーネント構成・ディレクトリ構造、interfaces.kt NoteConfig/NoteComposer 定義

---

## 2. 入力・出力の仕様

### 2.1 NoteConfig データクラス 🔵

**信頼性**: 🔵 *interfaces.kt NoteConfig 定義・REQ-405 より*

#### 入力パラメータ（コンストラクタ）

| パラメータ | 型 | 制約 | 説明 |
|-----------|-----|------|------|
| `vault` | `String` | 非空文字列 | Obsidian Vault 名 |
| `folder` | `String` | 任意の文字列 | 保存先フォルダパス |
| `defaultTags` | `List<String>` | 空リスト可 | タグフィールドの初期値 |

#### ファクトリメソッド: `fromAppConfig()`

| 入力 | 出力 |
|------|------|
| `AppConfig.OBSIDIAN_VAULT` ("testVault") | `NoteConfig.vault` |
| `AppConfig.OBSIDIAN_FOLDER` ("70_clippings") | `NoteConfig.folder` |
| `AppConfig.OBSIDIAN_TAGS` (listOf("shared")) | `NoteConfig.defaultTags` |

### 2.2 NoteComposer.buildFrontmatter() 🔵

**信頼性**: 🔵 *interfaces.kt NoteComposer 定義・REQ-101, REQ-103・既存 FrontmatterBuilder 実装より*

#### 入力パラメータ

| パラメータ | 型 | 制約 | 説明 |
|-----------|-----|------|------|
| `title` | `String?` | null 許容 | ノートタイトル（null の場合は title フィールド省略） |
| `body` | `String` | 空文字列可 | ノート本文 |
| `tags` | `List<String>` | 空リスト可 | タグリスト |

#### 出力

| 型 | 形式 | 例 |
|----|------|-----|
| `String` | Frontmatter ヘッダー付きノート本文 | `"---\ntitle: \"テスト\"\ntags: [shared, web]\n---\n\n本文テスト"` |

#### 入出力の関係性

- `title` が非 null の場合: `title: "値"` 行が Frontmatter に含まれる
- `title` が null の場合: `title` 行が省略される
- `tags` が空リストの場合: `tags: []` となる
- `tags` が非空の場合: `tags: [tag1, tag2, ...]` となる（カンマ+スペース区切り）
- `body` は Frontmatter 終端 `---` の後に空行を挟んで出力される

### 2.3 NoteComposer.buildUri() 🔵

**信頼性**: 🔵 *interfaces.kt NoteComposer 定義・REQ-101, REQ-405・既存 ObsidianUriBuilder 実装より*

#### 入力パラメータ

| パラメータ | 型 | 制約 | 説明 |
|-----------|-----|------|------|
| `content` | `String` | buildFrontmatter() の出力 | Frontmatter 付きノート本文 |
| `title` | `String?` | null 許容 | ノートタイトル（null の場合は空文字として URI に設定） |
| `config` | `NoteConfig` | 非 null | vault・folder を含む設定 |

#### 出力

| 型 | 形式 | 例 |
|----|------|-----|
| `Uri` | Obsidian URI | `obsidian://new?content=...&title=...&vault=v&folder=f` |

#### URI 構造

- `scheme`: `obsidian`
- `host`: `new`
- クエリパラメータ: `content`, `title`, `vault`, `folder`
- パラメータ値は `Uri.Builder.appendQueryParameter()` により自動 URL エンコードされる

**参照したEARS要件**: REQ-101, REQ-103, REQ-402, REQ-405
**参照した設計文書**: interfaces.kt NoteConfig/NoteComposer 定義、architecture.md NoteComposer 設計方針

---

## 3. 制約条件

### 3.1 既存コンポーネント非変更制約 🔵

**信頼性**: 🔵 *REQ-402 より*

- `FrontmatterBuilder.kt` を変更してはならない
- `ObsidianUriBuilder.kt` を変更してはならない
- `NoteComposer` は既存ビルダーの代替ではなく、編集画面フロー専用の新規ビルダーとして並存する

### 3.2 AppConfig 非依存設計 🔵

**信頼性**: 🔵 *interfaces.kt 設計方針・architecture.md NoteComposer 設計方針より*

- `NoteComposer` は `AppConfig` をインポートしてはならない
- すべてのパラメータは関数引数として明示的に渡す
- `NoteConfig.fromAppConfig()` のみが `AppConfig` を参照してよい

### 3.3 パッケージ配置制約 🔵

**信頼性**: 🔵 *architecture.md ディレクトリ構造より*

- `NoteConfig.kt`: `app/src/main/java/com/den4dr/share2Obsidian/format/` パッケージ
- `NoteComposer.kt`: `app/src/main/java/com/den4dr/share2Obsidian/format/` パッケージ

### 3.4 SDK 互換性制約 🔵

**信頼性**: 🔵 *CLAUDE.md・NFR-201 より*

- minSdk 33 (Android 13) で動作すること
- Java 互換性レベル: 11
- Kotlin: 2.2.10

### 3.5 テスト実行環境制約 🟡

**信頼性**: 🟡 *タスクノート 6.4 Uri テストの実行環境・妥当な推測*

- `buildUri()` のテストは `android.net.Uri` を使用するため Robolectric が必要
- Robolectric が利用できない場合は `androidTest` に移動を検討

**参照したEARS要件**: REQ-402, NFR-201
**参照した設計文書**: architecture.md ディレクトリ構造・NoteComposer 設計方針、interfaces.kt 設計コメント

---

## 4. 想定される使用例

### 4.1 基本的な使用パターン: タイトルあり + タグあり 🔵

**信頼性**: 🔵 *REQ-101・acceptance-criteria.md TC-101-01 より*

**Given**: ユーザーがテキストを共有し、編集画面でタイトル・本文・タグを入力した
**When**: 送信ボタンタップ時に以下が呼ばれる
```
val fm = NoteComposer.buildFrontmatter("テスト", "本文テスト", listOf("shared", "web"))
val uri = NoteComposer.buildUri(fm, "テスト", NoteConfig(vault="testVault", folder="70_clippings", defaultTags=listOf("shared")))
```
**Then**:
- Frontmatter: `"---\ntitle: \"テスト\"\ntags: [shared, web]\n---\n\n本文テスト"`
- URI: `obsidian://new?content=...&title=テスト&vault=testVault&folder=70_clippings`

### 4.2 タイトルなしパターン 🔵

**信頼性**: 🔵 *EDGE-001・FrontmatterBuilder の nullable title 対応より*

**Given**: タイトルが空のため null として渡される
**When**: `NoteComposer.buildFrontmatter(null, "本文", listOf("shared"))`
**Then**: `"---\ntags: [shared]\n---\n\n本文"` (title フィールド省略)

### 4.3 空タグリストパターン 🔵

**信頼性**: 🔵 *EDGE-003・requirements.md より*

**Given**: タグフィールドが空
**When**: `NoteComposer.buildFrontmatter(null, "本文", emptyList())`
**Then**: `"---\ntags: []\n---\n\n本文"`

### 4.4 NoteConfig.fromAppConfig() パターン 🔵

**信頼性**: 🔵 *REQ-405・AppConfig の値より*

**Given**: `AppConfig.OBSIDIAN_VAULT="testVault"`, `OBSIDIAN_FOLDER="70_clippings"`, `OBSIDIAN_TAGS=listOf("shared")`
**When**: `NoteConfig.fromAppConfig()`
**Then**: `NoteConfig(vault="testVault", folder="70_clippings", defaultTags=listOf("shared"))`

### 4.5 エラーケース: 空本文 🟡

**信頼性**: 🟡 *EDGE-002 から妥当な推測*

**Given**: 本文が空文字列
**When**: `NoteComposer.buildFrontmatter("タイトル", "", listOf("shared"))`
**Then**: `"---\ntitle: \"タイトル\"\ntags: [shared]\n---\n\n"` (空ノートとして送信)

### 4.6 統合パターン: FrontmatterBuilder との出力比較 🟡

**信頼性**: 🟡 *REQ-402・既存 FrontmatterBuilder 実装から妥当な推測*

**Given**: 同一入力（title="テスト", body="本文", tags=AppConfig.OBSIDIAN_TAGS）
**When**: `NoteComposer.buildFrontmatter(title, body, tags)` と `FrontmatterBuilder.build(title, body)` を比較
**Then**: セマンティクス的に等価な Frontmatter 文字列が生成される

**参照したEARS要件**: REQ-101, EDGE-001, EDGE-002, EDGE-003
**参照した設計文書**: dataflow.md フロー3（送信ボタンタップ）、interfaces.kt NoteComposer

---

## 5. EARS要件・設計文書との対応関係

### 参照したユーザストーリー
- テキスト共有 → 編集 → Obsidian 送信フロー（ユーザストーリー 1.1, 2.1, 2.2）

### 参照した機能要件
- **REQ-101**: 送信ボタンタップ時に編集後の値から URI を構築し Obsidian を起動する
- **REQ-103**: タグフィールドのカンマ区切りパース仕様
- **REQ-402**: FrontmatterBuilder / ObsidianUriBuilder を変更しない制約
- **REQ-405**: 保存先フォルダの初期値は AppConfig.OBSIDIAN_FOLDER を使用

### 参照した非機能要件
- **NFR-201**: minSdk 33 で動作すること

### 参照したEdgeケース
- **EDGE-001**: タイトル空の場合、Frontmatter の title フィールドを省略
- **EDGE-002**: 本文空の場合、空ノートとして送信
- **EDGE-003**: タグフィールド空の場合、`tags: []` として Frontmatter を生成

### 参照した受け入れ基準
- TC-101-01: タイトルあり Frontmatter 生成

### 参照した設計文書
- **アーキテクチャ**: `docs/design/content-edit-preview/architecture.md` - コンポーネント構成、ディレクトリ構造、NoteComposer 設計方針
- **データフロー**: `docs/design/content-edit-preview/dataflow.md` - フロー1（テキスト共有時）、フロー3（送信ボタンタップ）、NoteConfig データフロー
- **型定義**: `docs/design/content-edit-preview/interfaces.kt` - NoteConfig, NoteComposer, SendParams 定義
- **タスク定義**: `docs/tasks/content-edit-preview/TASK-0015.md` - 実装詳細、単体テスト要件、完了条件

---

## 信頼性レベルサマリー

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 機能概要 | 4 | 0 | 0 | 4 |
| 入出力仕様 | 3 | 0 | 0 | 3 |
| 制約条件 | 4 | 1 | 0 | 5 |
| 使用例 | 4 | 2 | 0 | 6 |
| **合計** | **15** | **3** | **0** | **18** |

- **総項目数**: 18項目
- 🔵 **青信号**: 15項目 (83%)
- 🟡 **黄信号**: 3項目 (17%)
- 🔴 **赤信号**: 0項目 (0%)

**品質評価**: 高品質

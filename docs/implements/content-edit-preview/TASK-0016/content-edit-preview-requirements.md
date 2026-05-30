# TASK-0016: EditFormState + parseTagsText + SendParams 実装 - TDD要件定義書

**タスクID**: TASK-0016
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-03-31
**フェーズ**: Phase 1 - 基盤実装

---

## 1. 機能の概要

### 何をする機能か 🔵

**信頼性**: 🔵 *REQ-003, REQ-103・interfaces.kt の EditFormState/parseTagsText/SendParams 定義より*

編集画面で使用するデータクラスとユーティリティ関数を実装する。具体的には以下の3つ。

1. **EditFormState**: 編集フォームの状態を保持するデータクラス（title, body, tagsText, folder の4フィールド）
2. **parseTagsText()**: カンマ区切りのタグ文字列を `List<String>` に変換するトップレベル関数
3. **SendParams**: 送信ボタンタップ時に ViewModel が返す送信用パラメータのデータクラス

### どのような問題を解決するか 🔵

**信頼性**: 🔵 *REQ-003（編集フィールド定義）、REQ-101（送信パラメータ）、REQ-103（タグパース）より*

- 編集画面（EditScreen）のフォーム状態を型安全に管理するための構造が必要
- ユーザーが入力したカンマ区切りタグ文字列を Frontmatter で使える `List<String>` に変換する仕組みが必要
- 送信ボタンタップ時に ViewModel から送信ロジック（NoteComposer）へ渡すパラメータの構造が必要

### 想定されるユーザー 🔵

**信頼性**: 🔵 *user-stories.md・ユーザヒアリングより*

- 後続タスク（TASK-0017: EditScreenViewModel）の開発者が直接使用する内部データ構造
- 最終的にはエンドユーザーのフォーム操作を支える基盤

### システム内での位置づけ 🔵

**信頼性**: 🔵 *architecture.md・dataflow.md・依存関係グラフより*

- **パッケージ**: `com.den4dr.share2Obsidian.ui`（UI層のデータモデル）
- **依存先**: `NoteConfig`（`format` パッケージ、TASK-0015 で実装済み）
- **依存元**: `EditScreenViewModel`（TASK-0017）が `EditFormState`, `parseTagsText`, `SendParams` を使用
- **位置**: データフローの「ViewModel 層」と「Composable UI 層」の間の橋渡し

**参照したEARS要件**: REQ-003, REQ-101, REQ-103
**参照した設計文書**: architecture.md（コンポーネント構成）、dataflow.md（フロー1: テキスト共有時）、interfaces.kt（EditFormState, SendParams, parseTagsText）

---

## 2. 入力・出力の仕様

### 2.1 EditFormState データクラス 🔵

**信頼性**: 🔵 *REQ-003（4フィールド）・interfaces.kt の EditFormState 定義より*

**ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt`

#### 入力（コンストラクタパラメータ）

| パラメータ | 型 | 説明 | 初期値の由来 | 信頼性 |
|-----------|-----|------|------------|--------|
| `title` | `String` | タイトルフィールドの現在値 | `ProcessedContent.title ?: ""` | 🔵 REQ-003, TC-003-01 |
| `body` | `String` | 本文フィールドの現在値 | `ProcessedContent.body` | 🔵 REQ-003 |
| `tagsText` | `String` | タグフィールドの現在値（カンマ区切り） | `config.defaultTags.joinToString(", ")` | 🔵 REQ-103 |
| `folder` | `String` | フォルダフィールドの現在値 | `config.folder` | 🔵 REQ-003, REQ-405 |

#### 出力

- `data class` として各フィールドへのアクセス、`copy()`, `equals()`, `hashCode()`, `toString()` が自動生成される

### 2.2 parseTagsText 関数 🔵

**信頼性**: 🔵 *REQ-103・interfaces.kt の parseTagsText 仕様・acceptance-criteria.md TC-101-02/03 より*

**ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt`（同ファイルにトップレベル関数として配置）

#### 入力

| パラメータ | 型 | 説明 | 制約 | 信頼性 |
|-----------|-----|------|------|--------|
| `tagsText` | `String` | カンマ区切りのタグ文字列 | 空文字列も許容 | 🔵 REQ-103 |

#### 出力

| 戻り値 | 型 | 説明 | 信頼性 |
|--------|-----|------|--------|
| タグリスト | `List<String>` | トリム済みの非空タグのリスト | 🔵 REQ-103 |

#### 入出力の関係性

| 入力例 | 出力例 | 信頼性 |
|--------|--------|--------|
| `"shared, web, clipping"` | `["shared", "web", "clipping"]` | 🔵 REQ-103, TC-101-02 |
| `"shared ,  web , clipping "` | `["shared", "web", "clipping"]` | 🔵 REQ-103, TC-101-03 |
| `""` | `[]` | 🟡 EDGE-003 から妥当な推測 |
| `","` | `[]` | 🟡 TC-103-02 から妥当な推測 |
| `"  ,  ,  "` | `[]` | 🟡 EDGE-003 から妥当な推測 |
| `"shared"` | `["shared"]` | 🔵 REQ-103 通常ケース |

#### 処理ロジック

```
1. カンマ(",") で split
2. 各要素を trim() で前後空白除去
3. 空文字列をフィルタリング（filter { it.isNotEmpty() }）
```

### 2.3 SendParams データクラス 🔵

**信頼性**: 🔵 *REQ-101・dataflow.md の送信フロー・interfaces.kt の SendParams 定義より*

**ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SendParams.kt`

#### 入力（コンストラクタパラメータ）

| パラメータ | 型 | 説明 | 制約 | 信頼性 |
|-----------|-----|------|------|--------|
| `title` | `String?` | 編集後のタイトル | 空文字の場合は `null`（EDGE-001） | 🔵 REQ-101, EDGE-001 |
| `body` | `String` | 編集後の本文 | 空文字許容（EDGE-002） | 🔵 REQ-101, EDGE-002 |
| `tags` | `List<String>` | `parseTagsText()` 適用済みのタグリスト | 空リスト許容（EDGE-003） | 🔵 REQ-103 |
| `config` | `NoteConfig` | 送信設定（vault, folder, defaultTags） | TASK-0015 で実装済み | 🔵 REQ-405 |

#### 出力

- `data class` として各フィールドへのアクセス、`copy()`, `equals()`, `hashCode()`, `toString()` が自動生成される

#### データフロー

```
EditFormState（ViewModel 内）
    ↓ buildSendParams()（TASK-0017 の責務）
    ↓ title: "" → null 変換
    ↓ tagsText: parseTagsText() 適用
SendParams
    ↓ NoteComposer.buildFrontmatter(title, body, tags)
    ↓ NoteComposer.buildUri(content, title, config)
obsidian://new?...
```

**参照したEARS要件**: REQ-003, REQ-101, REQ-103, REQ-405, EDGE-001, EDGE-002, EDGE-003
**参照した設計文書**: interfaces.kt（EditFormState, SendParams, parseTagsText）、dataflow.md（フロー1, フロー3）

---

## 3. 制約条件

### パフォーマンス要件 🟡

**信頼性**: 🟡 *NFR-001 から妥当な推測（データクラスは即座に生成可能）*

- `EditFormState` の生成・コピーは O(1) で行われること
- `parseTagsText()` はユーザー入力サイズ（通常数十文字）に対して十分高速（O(n)）であること

### アーキテクチャ制約 🔵

**信頼性**: 🔵 *REQ-401, REQ-402・architecture.md より*

- `EditFormState` と `parseTagsText()` は `ui` パッケージに配置する（`com.den4dr.share2Obsidian.ui`）
- `SendParams` も `ui` パッケージに配置する
- `SendParams` は `format` パッケージの `NoteConfig` に依存する（TASK-0015 で実装済み）
- `parseTagsText()` は `EditFormState.kt` と同ファイルにトップレベル関数として配置する

### 互換性制約 🔵

**信頼性**: 🔵 *CLAUDE.md, NFR-201 より*

- minSdk 33（Android 13）以上で動作すること
- Kotlin 2.2.10 / Java 11 互換
- 追加の外部ライブラリ依存なし（標準 Kotlin のみ使用）

### テスト制約 🔵

**信頼性**: 🔵 *既存テストパターン（NoteComposerTest.kt 等）より*

- JUnit 4 でユニットテストを記述する
- `parseTagsText()` は純粋関数のためRobolectric不要（通常のJUnit テストで可）
- テストコメントに【テスト目的】【テスト内容】【期待される動作】の3点セットと信頼性レベルを記載する

### 命名規約 🔵

**信頼性**: 🔵 *既存実装パターン・note.md の名前空間ルールより*

- データクラス: パスカルケース（`EditFormState`, `SendParams`）
- 関数: キャメルケース（`parseTagsText`）
- テストクラス: `ParseTagsTextTest`（テスト対象関数名 + Test）
- テストメソッド: バッククォート記法 `` `TC-XXX 説明` ``

**参照したEARS要件**: REQ-401, REQ-402, NFR-001, NFR-201
**参照した設計文書**: architecture.md（パッケージ構成）、CLAUDE.md（SDK・言語バージョン）

---

## 4. 想定される使用例

### 4.1 基本的な使用パターン 🔵

**信頼性**: 🔵 *REQ-003, REQ-101, REQ-103・dataflow.md フロー1 より*

#### パターン1: EditFormState の初期化（ViewModel から使用）

```kotlin
// ProcessedContent と NoteConfig から EditFormState を生成
val formState = EditFormState(
    title = processed.title ?: "",
    body = processed.body,
    tagsText = config.defaultTags.joinToString(", "),
    folder = config.folder
)
```

#### パターン2: タグパース（送信時）

```kotlin
// ユーザーが入力したカンマ区切りタグを List に変換
val tags = parseTagsText("shared, web, clipping")
// → ["shared", "web", "clipping"]
```

#### パターン3: SendParams の生成（ViewModel の buildSendParams から使用）

```kotlin
val sendParams = SendParams(
    title = formState.title.ifEmpty { null },
    body = formState.body,
    tags = parseTagsText(formState.tagsText),
    config = NoteConfig.fromAppConfig()
)
```

### 4.2 エッジケース 🟡

**信頼性**: 🟡 *EDGE-001, EDGE-002, EDGE-003 から妥当な推測*

#### エッジケース1: タグフィールドが空

```kotlin
val tags = parseTagsText("")
// → [] （空リスト）
// → Frontmatter: tags: []
```

#### エッジケース2: カンマのみ入力

```kotlin
val tags = parseTagsText(",")
// → [] （空リスト、空文字がフィルタリングされる）
```

#### エッジケース3: スペースのみのカンマ区切り

```kotlin
val tags = parseTagsText("  ,  ,  ")
// → [] （トリム後に空文字がフィルタリングされる）
```

#### エッジケース4: 単一タグ

```kotlin
val tags = parseTagsText("shared")
// → ["shared"]
```

### 4.3 エラーケース 🟡

**信頼性**: 🟡 *EDGE-001, EDGE-002 から妥当な推測*

- **parseTagsText に null は渡されない**: Kotlin の型システムで `String`（非 nullable）を受け取るため、コンパイル時に保証される
- **SendParams.title が null**: これはエラーではなく正常な状態（タイトルなしのノート送信、EDGE-001）
- **SendParams.body が空文字**: これもエラーではなく正常な状態（空ノート送信、EDGE-002）

**参照したEARS要件**: REQ-003, REQ-101, REQ-103, EDGE-001, EDGE-002, EDGE-003
**参照した設計文書**: dataflow.md（フロー1, フロー3）、interfaces.kt（parseTagsText 仕様）

---

## 5. EARS要件・設計文書との対応関係

### 参照したユーザストーリー

- ユーザストーリー1.1: テキスト共有時の編集画面表示

### 参照した機能要件

| 要件ID | 内容 | 対応する実装 | 信頼性 |
|--------|------|------------|--------|
| REQ-003 | 編集フィールド（title, body, tags, folder）の表示 | `EditFormState` の4フィールド | 🔵 |
| REQ-101 | 送信時に編集後の値から URI 構築 | `SendParams` データクラス | 🔵 |
| REQ-103 | タグフィールドのカンマ区切りパース | `parseTagsText()` 関数 | 🔵 |
| REQ-405 | フォルダ初期値は AppConfig から | `EditFormState.folder` | 🔵 |

### 参照した非機能要件

| 要件ID | 内容 | 対応 | 信頼性 |
|--------|------|------|--------|
| NFR-001 | 編集画面の初期表示 100ms 以内 | データクラス生成は即時 | 🟡 |
| NFR-201 | minSdk 33 対応 | 標準 Kotlin のみ使用 | 🔵 |

### 参照したEdgeケース

| ケースID | 内容 | 対応 | 信頼性 |
|----------|------|------|--------|
| EDGE-001 | タイトル空で送信 | `SendParams.title: String?`（null 許容） | 🟡 |
| EDGE-002 | 本文空で送信 | `SendParams.body: String`（空文字許容） | 🟡 |
| EDGE-003 | タグ空で送信 | `parseTagsText("")` → `[]` | 🟡 |

### 参照した受け入れ基準

- TC-003-01: タイトル初期値が ProcessedContent.title で設定される
- TC-101-02: カンマ区切りタグが正しくパースされる
- TC-101-03: スペーストリムが正しく行われる
- TC-103-01: 空タグ文字列が空リストに変換される
- TC-103-02: カンマのみが空リストに変換される

### 参照した設計文書

| 文書 | 該当セクション |
|------|------------|
| **アーキテクチャ**: architecture.md | コンポーネント構成、パッケージ配置 |
| **データフロー**: dataflow.md | フロー1（テキスト共有時）、フロー3（送信ボタンタップ時） |
| **型定義**: interfaces.kt | EditFormState, SendParams, parseTagsText |
| **受け入れ基準**: acceptance-criteria.md | TC-003-01〜04, TC-101-02/03, TC-103-01/02 |

---

## 6. 実装ファイル一覧

| ファイル | 種別 | 内容 |
|---------|------|------|
| `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt` | 新規作成 | EditFormState データクラス + parseTagsText 関数 |
| `app/src/main/java/com/den4dr/share2Obsidian/ui/SendParams.kt` | 新規作成 | SendParams データクラス |
| `app/src/test/java/com/den4dr/share2Obsidian/ui/ParseTagsTextTest.kt` | 新規作成 | parseTagsText のユニットテスト |

---

## 信頼性レベルサマリー

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 機能の概要 | 4 | 0 | 0 | 4 |
| 入出力の仕様 | 12 | 3 | 0 | 15 |
| 制約条件 | 5 | 1 | 0 | 6 |
| 使用例 | 1 | 2 | 0 | 3 |
| **合計** | **22** | **6** | **0** | **28** |

- 🔵 **青信号**: 22項目 (79%)
- 🟡 **黄信号**: 6項目 (21%)
- 🔴 **赤信号**: 0項目 (0%)

**品質評価**: 高品質

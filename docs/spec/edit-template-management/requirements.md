# 編集テンプレートの管理機能 要件定義書

**作成日**: 2026-05-31

## 概要

Share2Obsidian の設定画面からアクセスできるテンプレート管理機能を追加する。
テンプレートは「Obsidian ノートに付与する front matter フィールドの定義セット」であり、
フィールドには固定値・HTML メタデータ自動マッピング・URL・空値（ユーザー入力）の 4 種類の値ソースを設定できる。
テンプレートは複数作成・管理でき、1 つをデフォルトに設定することで共有時に自動適用される。

## 関連文書

- **ヒアリング記録**: [💬 interview-record.md](interview-record.md)
- **ユーザストーリー**: [📖 user-stories.md](user-stories.md)
- **受け入れ基準**: [✅ acceptance-criteria.md](acceptance-criteria.md)
- **コンテキストノート**: [📝 note.md](note.md)
- **PRD**: [編集テンプレートの管理機能.md](../../prd/編集テンプレートの管理機能.md)

## 機能要件（EARS記法）

**【信頼性レベル凡例】**:
- 🔵 **青信号**: PRD・設計文書・ユーザヒアリングを参考にした確実な要件
- 🟡 **黄信号**: PRD・設計文書・ユーザヒアリングから妥当な推測による要件
- 🔴 **赤信号**: PRD・設計文書・ユーザヒアリングにない推測による要件

---

### テンプレート画面ナビゲーション

- REQ-001: システムは SettingsScreen からテンプレート一覧画面（TemplateListScreen）へ遷移できなければならない 🔵 *ユーザヒアリング「独立した新画面」「SettingsScreenから遷移」より*
- REQ-002: システムは単一 Activity 構成（MainActivity のみ）を維持しなければならない 🔵 *settings-screen REQ-401・既存アーキテクチャより*

---

### テンプレート CRUD（TemplateListScreen / TemplateEditScreen）

- REQ-011: システムはテンプレートの一覧を TemplateListScreen に表示しなければならない 🔵 *PRD「テンプレートを複数指定できる機能」・ユーザヒアリングより*
- REQ-012: システムはテンプレートを新規作成できなければならない 🔵 *PRD「テンプレートを複数指定」より*
- REQ-013: システムは既存テンプレートを編集できなければならない 🔵 *PRD「テンプレートの編集画面」より*
- REQ-014: システムは既存テンプレートを削除できなければならない 🟡 *CRUD 機能として妥当な推測*
- REQ-015: 各テンプレートには以下の属性を持たなければならない 🔵 *ユーザヒアリング「テンプレート名, vault/folder設定, カスタムfront matterフィールド一覧, デフォルト指定フラグ」より*
  - テンプレート名（必須、一意）— 一意性はアプリ側バリデーションで保証。DB UNIQUE 制約なし 🔵 *ユーザヒアリング確認済み*
  - vault 名
  - 保存先フォルダ
  - カスタム front matter フィールド一覧
  - デフォルト指定フラグ

---

### デフォルトテンプレート管理

- REQ-021: システムは複数のテンプレートの中から 1 つをデフォルトとして指定できなければならない 🔵 *ユーザヒアリング「デフォルト指定フラグ」より*
- REQ-022: デフォルトテンプレートは同時に 1 つのみ存在しなければならない（新たにデフォルト指定すると既存のデフォルトが解除される） 🟡 *デフォルト概念の一般的な動作から妥当な推測*
- REQ-023: デフォルトテンプレートが設定されていない場合、システムは既存の AppConfig 値（vault/folder/tags）を使用して動作しなければならない（後方互換） 🔵 *既存実装・設計文書より*

---

### Front matter フィールド管理（TemplateEditScreen 内インライン）

- REQ-031: テンプレート編集画面において、front matter フィールドを追加できなければならない 🔵 *PRD「front matterの項目を追加・削除できる機能」より*
- REQ-032: テンプレート編集画面において、front matter フィールドを削除できなければならない 🔵 *PRD「front matterの項目を追加・削除できる機能」より*
- REQ-033: 各 front matter フィールドは以下の属性を持たなければならない 🔵 *ユーザヒアリングより*
  - フィールドキー名（文字列）
  - 値ソース種別（FIXED / HTML_META / URL / EMPTY の4種類）
  - 値の型（STRING / LIST）
  - 初期値（FIXED の場合のみ必要）
  - HTML メタデータマッピング種別（HTML_META の場合のみ必要）

---

### 値ソース種別の仕様

- REQ-041: 値ソースが「FIXED（固定値）」の場合、ユーザーが手入力した文字列またはリストを初期値として EditScreen に表示しなければならない 🔵 *ユーザヒアリング「固定値（ユーザーが手入力する初期値）」より*
- REQ-042: 値ソースが「HTML_META（HTML メタデータマッピング）」の場合、URL/HTML 共有時に以下のメタデータを自動取得してフィールド値に設定しなければならない 🔵 *PRD・ユーザヒアリングより*
  - `og:title` / `<title>` タグ → タイトル
  - `og:description` / `<meta name="description">` → 説明
  - ページ URL → URL
  - `article:published_time` / `datePublished` → 公開日
  - `article:modified_time` / `dateModified` → 最終更新日
  - `author` / `article:author` / `og:site_name` → 著者・発行者
- REQ-043: 値ソースが「URL」の場合、共有された URL そのものをフィールド値として自動設定しなければならない 🔵 *ユーザヒアリング「URLそのもの」より*
- REQ-044: 値ソースが「EMPTY（空値）」の場合、フィールドキー名のみをEditScreen のカスタムフィールドエリアに表示し、ユーザーが EditScreen で値を入力できるようにしなければならない 🔵 *ユーザヒアリング「空値（フィールド名だけ追加、値はユーザーが入力）」より*
- REQ-045: HTML_META マッピング時にメタデータが存在しない場合、フィールド値を空文字列として扱わなければならない 🟡 *URL/HTML 共有時のフォールバック動作として妥当な推測*

---

### テンプレート適用（EditScreen への反映）

- REQ-051: デフォルトテンプレートが設定されている場合、共有処理完了後にそのテンプレートを自動適用しなければならない 🔵 *ユーザヒアリング「デフォルトテンプレートのみ自動適用」より*
- REQ-052: テンプレート適用時、EditScreen のフォームに vault・folder・カスタム front matter フィールドを反映しなければならない 🔵 *PRD・ユーザヒアリングより*
- REQ-053: テキスト共有時（ContentKind.TEXT）は HTML_META マッピングフィールドを空文字列として扱い、FIXED/EMPTY/URL フィールドは通常通り適用しなければならない 🟡 *HTML_META はURL/HTML専用という設計から妥当な推測*

---

### データ永続化

- REQ-061: テンプレートデータは Room DB に永続化しなければならない 🔵 *ユーザヒアリング「Room DB」より*
- REQ-062: アプリ再起動後もテンプレートデータは保持されなければならない 🔵 *永続化の目的より*
- REQ-063: Room DB の読み書きは Coroutines で非同期実行しなければならない 🔵 *既存非同期設計・tech-stack.md より*

---

### HTML メタデータ抽出

- REQ-071: URL 共有時（ContentKind.URL）および HTML 共有時（ContentKind.HTML）において、システムは HTML メタデータを抽出して `ProcessedContent` に保持しなければならない 🔵 *PRD「urlやhtmlが共有された際に...HTMLのメタデータを定義する機能」より*
- REQ-072: `ProcessedContent` にメタデータ格納用のフィールドを追加しなければならない 🔵 *PRD・実装要件より*

---

### 制約要件

- REQ-401: UI 文字列はすべて `res/values/strings.xml` に定義しなければならない 🔵 *既存開発ルール・content-edit-preview note.md より*
- REQ-402: UI コンポーネントは Compose Material3 を使用しなければならない 🔵 *tech-stack.md・既存実装より*
- REQ-403: 依存性注入は Hilt を使用しなければならない 🔵 *tech-stack.md より*
- REQ-404: FrontmatterBuilder をテンプレートのカスタムフィールドに対応できるよう拡張しなければならない 🔵 *ユーザヒアリング「FrontmatterBuilderの変更許容」より*

---

## 非機能要件

### ユーザビリティ

- NFR-001: テンプレート一覧は上下スクロール可能なリスト形式で表示すること 🟡 *Material3 LazyColumn の標準 UX パターンから妥当な推測*
- NFR-002: フィールドの追加は「+」ボタン、削除は各フィールド行のアイコンボタンで操作できること 🟡 *Material3 リスト編集 UX パターンから妥当な推測*
- NFR-003: デフォルトテンプレートは一覧内で視覚的に区別できること（アイコンまたはバッジ等） 🟡 *UX 観点から妥当な推測*

### パフォーマンス

- NFR-101: Room DB の読み書きは IO Dispatcher で実行し、メインスレッドをブロックしないこと 🔵 *Room + Coroutines の標準設計より*

### 保守性

- NFR-201: テンプレート機能は Repository パターンで実装し、ViewModel から DB 実装を隠蔽すること 🔵 *tech-stack.md MVVM + Repository より*
- NFR-202: Room の Hilt Module は `DatabaseModule.kt` として分離すること 🟡 *Hilt の一般的な設計パターンから妥当な推測*

### 互換性

- NFR-301: minSdk 33（Android 13）で動作すること 🔵 *CLAUDE.md より*

---

## Edgeケース

### デフォルトテンプレート未設定

- EDGE-001: デフォルトテンプレートが 0 件の場合、既存の AppConfig 定数（OBSIDIAN_VAULT, OBSIDIAN_FOLDER, OBSIDIAN_TAGS）から NoteConfig を生成して動作する（後方互換）🔵 *REQ-023・既存実装より*

### テンプレート削除

- EDGE-002: デフォルトテンプレートを削除した場合、デフォルトが 0 件になり EDGE-001 の動作にフォールバックする。削除確認ダイアログはデフォルト有無に関わらず共通（特別警告なし） 🔵 *ユーザヒアリング確認済み*

### HTML メタデータ欠損

- EDGE-003: HTML_META マッピングフィールドで対象メタデータが HTML 内に存在しない場合、フィールド値を空文字列として EditScreen に表示する 🟡 *REQ-045 から妥当な推測*

### テキスト共有時

- EDGE-004: テキスト共有時（ContentKind.TEXT）に HTML_META マッピングフィールドは空文字列として扱い、URL フィールドも空文字列とする。空フィールドは EditScreen に空文字で表示する（非表示にしない） 🔵 *ユーザヒアリング確認済み*

### フィールドキーの重複

- EDGE-005: テンプレートのカスタムフィールドキーが既存の Frontmatter キー（`title`, `tags`）と重複する場合の動作 🔴 *PRD・ヒアリングで未定義。重複禁止バリデーションまたは上書き動作の確認が必要*

### 値の型 LIST

- EDGE-006: 値型が LIST のフィールドにカンマ区切り文字列を入力した場合、`parseTagsText` と同様にカンマ分割・トリムして YAML リスト形式で出力する 🟡 *既存 `parseTagsText` の実装から妥当な推測*

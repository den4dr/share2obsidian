---
name: template-content-management-requirements
description: テンプレートの管理内容の変更 要件定義書
metadata:
  type: project
---

# テンプレートの管理内容の変更 要件定義書

**作成日**: 2026-06-07

## 概要

編集テンプレート管理機能（edit-template-management）の設計を変更する。
テンプレートに紐付く内容はファイルの内容（frontmatterフィールド + 本文テンプレート）のみとし、
保存先（vault/folder）はテンプレートから分離してDataStoreグローバル設定として管理する。
また、テンプレートで本文の初期構造を `{{content}}` プレースホルダーを用いて定義できるようにし、
EditScreen の表示順序を変更する。

## 関連文書

- **ヒアリング記録**: [💬 interview-record.md](interview-record.md)
- **ユーザストーリー**: [📖 user-stories.md](user-stories.md)
- **受け入れ基準**: [✅ acceptance-criteria.md](acceptance-criteria.md)
- **コンテキストノート**: [📝 note.md](note.md)
- **PRD**: [テンプレートの管理内容の変更.md](../../prd/テンプレートの管理内容の変更.md)
- **前提機能要件**: [編集テンプレートの管理機能 requirements.md](../edit-template-management/requirements.md)

## 機能要件（EARS記法）

**【信頼性レベル凡例】**:
- 🔵 **青信号**: PRD・設計文書・ユーザヒアリングを参考にした確実な要件
- 🟡 **黄信号**: PRD・設計文書・ユーザヒアリングから妥当な推測による要件
- 🔴 **赤信号**: PRD・設計文書・ユーザヒアリングにない推測による要件

---

### Template モデル変更

- REQ-001: システムは `Template` ドメインモデルから `vault` と `folder` フィールドを削除しなければならない 🔵 *PRD「テンプレートに紐付くのはファイルの内容のみ」・ユーザヒアリングより*
- REQ-002: システムは `Template` ドメインモデルに `body: String`（デフォルト空文字列）フィールドを追加しなければならない 🔵 *PRD「フィールドは本文含めてすべてテンプレート内で指定可能」・ユーザヒアリングより*
- REQ-003: システムは Room DB の `Template` エンティティをREQ-001・REQ-002の変更に対応したスキーマへマイグレーションしなければならない 🔵 *ユーザヒアリング「Templateからvault/folderを削除しbodyを追加」より*
- REQ-004: REQ-003のマイグレーションは既存のテンプレートデータ（name, fields, isDefault）を失わずに実行しなければならない 🔵 *既存データ保護要件より*

---

### 本文テンプレート（`{{content}}` プレースホルダー）

- REQ-011: テンプレートの `body` フィールドに `{{content}}` プレースホルダーを含めることができる 🔵 *ユーザヒアリング「{{content}}プレースホルダーで共有内容を埋め込む」より*
- REQ-012: `{{content}}` プレースホルダーが含まれる場合、テンプレート適用時に共有コンテンツを `{{content}}` の位置に置換してEditScreenの本文欄に設定しなければならない 🔵 *ユーザヒアリングより*
- REQ-013: テンプレートの `body` が空文字列の場合、共有コンテンツをそのままEditScreenの本文欄に設定しなければならない 🔵 *ユーザヒアリング「テンプレートに本文が未設定の場合は共有コンテンツをそのまま使用」より*
- REQ-014: デフォルトテンプレートが未設定の場合も、共有コンテンツをそのままEditScreenの本文欄に設定しなければならない（後方互換） 🔵 *既存動作の継続より*

---

### vault/folder のグローバル設定管理

- REQ-021: vault と folder はDataStoreにアプリ全体のデフォルト設定として保存し、SettingsScreenで編集できなければならない 🔵 *ユーザヒアリング「DataStoreのデフォルト値を使いつつ設定画面でも変更可」より*
- REQ-022: EditScreenのvault欄とfolder欄はDataStoreの設定値（NoteConfig）で起動時に初期化しなければならない 🔵 *PRD「保存先の内容は保存時に都度設定する前提」・ユーザヒアリングより*
- REQ-023: ユーザーはEditScreenのvault/folder欄を保存のたびに変更できなければならない 🔵 *PRD「保存時に都度設定する前提の挙動」より*
- REQ-024: EditScreenでユーザーが変更したvault/folderの値はObsidian URIの構築に使用し、DataStoreには保存しないこと 🔵 *PRD・ユーザヒアリングより*

---

### テンプレート適用ロジック変更

- REQ-031: テンプレート適用時、vault/folderはDataStoreの設定値を使用し、テンプレートからは取得しないこと 🔵 *PRD「テンプレートに紐付くのはファイルの内容のみ」より*
- REQ-032: テンプレート適用時、テンプレートの `body` に `{{content}}` プレースホルダー解決処理を適用した結果を `EditFormState.body` に設定しなければならない 🔵 *ユーザヒアリングより*

---

### EditScreen UI 変更

- REQ-041: EditScreenの表示順序を以下の順に変更しなければならない: vault → folder → title（ファイル名） → frontmatterフィールド → 本文 🔵 *PRD「保存画面では、上から順に、vault,フォルダ, frontmatter, 本文の順に表示する」より*
- REQ-042: EditScreen の `title` はObsidianのファイル名として位置づけ、frontmatterフィールドとは視覚的に別セクションに表示しなければならない 🔵 *ユーザヒアリング「今のtitleはファイル名という扱いで明確にfrontmatterとは別扱いにしたい」より*
- REQ-043: EditScreenにvaultとfolderの入力欄を追加し、ユーザーが編集できるようにしなければならない 🔵 *PRD・REQ-022・REQ-023より*

---

### TemplateEditScreen UI 変更

- REQ-051: TemplateEditScreenからvaultとfolderの入力エリアを削除しなければならない 🔵 *ユーザヒアリング「編集画面からvault/folderを削除」より*
- REQ-052: TemplateEditScreenに本文テンプレートの入力エリアを追加しなければならない 🔵 *PRD「フィールドは本文含めてすべてテンプレート内で指定可能」より*
- REQ-053: 本文テンプレート入力エリアでは `{{content}}` プレースホルダーの入力をサポートしなければならない（特別なバリデーションなし。ユーザーが任意のテキストを入力可能） 🔵 *REQ-011・ユーザヒアリングより*

---

### EditFormState / SendParams 変更

- REQ-061: `EditFormState` に `vault: String` と `folder: String` フィールドを追加し、EditScreenのvault/folder入力欄と双方向に同期しなければならない 🔵 *REQ-043より*
- REQ-062: `SendParams` から送信する際のvault/folderは `EditFormState.vault` / `EditFormState.folder` から取得しなければならない 🔵 *REQ-024より*

---

## 非機能要件

### データ保護

- NFR-001: Room DBのマイグレーションは既存テンプレートのname・fields・isDefaultデータを失わずに実行しなければならない 🔵 *既存データ保護要件より*

### ユーザビリティ

- NFR-002: EditScreenのvault/folder欄はDataStore値で初期化されており、ユーザーが内容を把握した上で変更できること 🔵 *REQ-022・PRDより*
- NFR-003: TemplateEditScreenの本文テンプレート入力エリアは複数行入力に対応し、`{{content}}` を含む構造的なテキストを記述できること 🟡 *本文テンプレートの用途から妥当な推測*

### 保守性

- NFR-004: `{{content}}` プレースホルダーの解決ロジックは `TemplateApplicator` または専用のユーティリティ関数として実装し、テスト可能な形にすること 🟡 *MVVM + Repository パターン継続から妥当な推測*

---

## Edgeケース

### プレースホルダー複数

- EDGE-001: body内に `{{content}}` が複数ある場合、すべての `{{content}}` を共有コンテンツで置換する 🟡 *String.replace の標準動作から妥当な推測*

### プレースホルダーなしかつ非空body

- EDGE-002: body に `{{content}}` がなくかつ非空の場合、テンプレートのbodyのみをEditScreenの本文として設定し、共有コンテンツは本文に含めない 🟡 *REQ-012・REQ-013の組み合わせから妥当な推測*

### vault/folder 未設定

- EDGE-003: vault/folderがDataStoreで未設定（空文字列）の場合、EditScreenのvault/folder欄は空欄で表示する 🔵 *既存の後方互換動作より*

### テキスト共有時の body プレースホルダー

- EDGE-004: テキスト共有時（ContentKind.TEXT）の場合、body内の `{{content}}` にはテキストコンテンツが埋め込まれる（HTMLメタ情報は無関係） 🔵 *ContentKind非依存のプレースホルダー仕様より*

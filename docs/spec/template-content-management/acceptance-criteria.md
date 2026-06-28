---
name: template-content-management-acceptance-criteria
description: テンプレートの管理内容の変更 受け入れ基準
metadata:
  type: project
---

# テンプレートの管理内容の変更 受け入れ基準

**作成日**: 2026-06-07
**関連要件定義**: [requirements.md](requirements.md)
**関連ユーザストーリー**: [user-stories.md](user-stories.md)
**ヒアリング記録**: [interview-record.md](interview-record.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: PRD・設計文書・ユーザヒアリングを参考にした確実な基準
- 🟡 **黄信号**: PRD・設計文書・ユーザヒアリングから妥当な推測による基準
- 🔴 **赤信号**: PRD・設計文書・ユーザヒアリングにない推測による基準

---

## REQ-001〜004: Template モデル変更と DB マイグレーション 🔵

**信頼性**: 🔵 *ユーザヒアリング「Templateからvault/folderを削除しbodyを追加」より*

### Given（前提条件）
- 既存の Room DB に vault・folder フィールドを持つ Template テーブルが存在する
- アプリが既存データを保持した状態で起動する

### When（実行条件）
- アプリを新しいバージョン（スキーマバージョン更新済み）で起動する

### Then（期待結果）
- Template テーブルから vault・folder カラムが削除される
- Template テーブルに body カラム（TEXT、デフォルト空文字列）が追加される
- マイグレーション前に存在した name・isDefault・fields（関連テーブル）のデータは保持される

### テストケース

#### 正常系

- [ ] **TC-001-01**: Template ドメインモデルに vault・folder フィールドが存在しないこと 🔵
  - **確認方法**: Template.kt のプロパティ一覧に vault・folder がない
  - **期待結果**: コンパイルエラーなし

- [ ] **TC-001-02**: Template ドメインモデルに body: String フィールドが存在すること 🔵
  - **入力**: `Template(name="test", fields=emptyList(), isDefault=false, body="")`
  - **期待結果**: 正常にインスタンス化できる

- [ ] **TC-001-03**: Room DB Migration が正常実行され既存データが保持されること 🔵
  - **入力**: Migration 前の DB（vault="my_vault", folder="notes", name="テンプレA", isDefault=true）
  - **期待結果**: マイグレーション後も name="テンプレA", isDefault=true が保持され、body="" で追加される

- [ ] **TC-001-04**: マイグレーション後に body が空文字列のデフォルトで作成できること 🔵
  - **入力**: `Template(name="new", fields=emptyList(), isDefault=false)`
  - **期待結果**: body = "" として DB に保存される

---

## REQ-011〜014: 本文テンプレート（`{{content}}` プレースホルダー）🔵

**信頼性**: 🔵 *ユーザヒアリング「{{content}}プレースホルダーで共有内容を埋め込む」より*

### Given（前提条件）
- デフォルトテンプレートが設定されている

### When（実行条件）
- 共有コンテンツを受け取り、テンプレートを適用する

### Then（期待結果）
- テンプレートの body 内の `{{content}}` が共有コンテンツで置換される

### テストケース

#### 正常系

- [ ] **TC-011-01**: body に `{{content}}` が1つある場合、共有コンテンツで置換される 🔵
  - **入力**: body = `"## 記事\n{{content}}\n\n## メモ\n"`, 共有コンテンツ = `"テスト本文"`
  - **期待結果**: EditFormState.body = `"## 記事\nテスト本文\n\n## メモ\n"`

- [ ] **TC-011-02**: body が空文字列の場合、共有コンテンツをそのまま使用する 🔵
  - **入力**: body = `""`, 共有コンテンツ = `"テスト本文"`
  - **期待結果**: EditFormState.body = `"テスト本文"`

- [ ] **TC-011-03**: デフォルトテンプレートが未設定の場合、共有コンテンツをそのまま使用する 🔵
  - **入力**: defaultTemplate = null, 共有コンテンツ = `"テスト本文"`
  - **期待結果**: EditFormState.body = `"テスト本文"`

#### Edgeケース

- [ ] **TC-011-E01**: body に `{{content}}` が複数ある場合、すべて置換される 🟡
  - **入力**: body = `"{{content}}\n---\n{{content}}"`, 共有コンテンツ = `"テスト"`
  - **期待結果**: EditFormState.body = `"テスト\n---\nテスト"`

- [ ] **TC-011-E02**: body に `{{content}}` がなくかつ非空の場合、body のみが使用される 🟡
  - **入力**: body = `"固定テキスト"`, 共有コンテンツ = `"テスト本文"`
  - **期待結果**: EditFormState.body = `"固定テキスト"`（共有コンテンツは含まない）

- [ ] **TC-011-E03**: テキスト共有時も `{{content}}` にテキストコンテンツが埋め込まれる 🔵
  - **入力**: body = `"## メモ\n{{content}}"`, 共有コンテンツ（テキスト） = `"共有されたテキスト"`
  - **期待結果**: EditFormState.body = `"## メモ\n共有されたテキスト"`

---

## REQ-021〜024: vault/folder グローバル設定管理 🔵

**信頼性**: 🔵 *ユーザヒアリング「DataStoreのデフォルト値を使いつつ設定画面でも変更可」より*

### Given（前提条件）
- DataStore に vault = "MyVault", folder = "Notes" が設定されている

### When（実行条件）
- 共有後 EditScreen が表示される

### Then（期待結果）
- EditScreen の vault 欄に "MyVault" が表示される
- EditScreen の folder 欄に "Notes" が表示される

### テストケース

#### 正常系

- [ ] **TC-021-01**: EditScreen 起動時に vault 欄に DataStore の vault 値が表示される 🔵
  - **入力**: DataStore vault = "MyVault"
  - **期待結果**: EditScreen の vault 入力欄に "MyVault" が初期表示される

- [ ] **TC-021-02**: EditScreen 起動時に folder 欄に DataStore の folder 値が表示される 🔵
  - **入力**: DataStore folder = "Notes"
  - **期待結果**: EditScreen の folder 入力欄に "Notes" が初期表示される

- [ ] **TC-021-03**: EditScreen で vault/folder を変更して送信すると変更後の値が Obsidian URI に反映される 🔵
  - **入力**: vault 欄を "AnotherVault" に変更して送信
  - **期待結果**: `obsidian://new?vault=AnotherVault&...` のURI が生成される

- [ ] **TC-021-04**: EditScreen で vault/folder を変更しても DataStore の値は変わらない 🔵
  - **入力**: vault 欄を "AnotherVault" に変更して送信
  - **期待結果**: 送信後に DataStore.vault は "MyVault" のまま

#### Edgeケース

- [ ] **TC-021-E01**: DataStore の vault/folder が未設定（空文字列）の場合、EditScreen の vault/folder 欄は空欄で表示される 🔵
  - **入力**: DataStore vault = "", folder = ""
  - **期待結果**: EditScreen の vault・folder 欄は空欄で表示される

---

## REQ-031〜032: テンプレート適用ロジック変更 🔵

**信頼性**: 🔵 *PRD「テンプレートに紐付くのはファイルの内容のみ」より*

### テストケース

- [ ] **TC-031-01**: テンプレート適用時に vault/folder はテンプレートから取得されない 🔵
  - **確認方法**: TemplateApplicator.buildConfig() が Template を参照せず DataStore 値を返す
  - **期待結果**: NoteConfig.vault は DataStore の値、Template に vault フィールドが存在しない

- [ ] **TC-031-02**: テンプレート適用時に body が `{{content}}` 解決処理を経て EditFormState.body に設定される 🔵
  - **入力**: Template.body = `"## 記事\n{{content}}"`, 共有コンテンツ = `"テスト"`
  - **期待結果**: viewModel.uiState.body = `"## 記事\nテスト"`

---

## REQ-041〜043: EditScreen UI 変更 🔵

**信頼性**: 🔵 *PRD「保存画面では、上から順に、vault,フォルダ, frontmatter, 本文の順」より*

### テストケース

- [ ] **TC-041-01**: EditScreen で vault 入力欄がフォームの最上部に表示される 🔵
  - **確認方法**: Compose UI テストで vault テキストフィールドの位置を確認

- [ ] **TC-041-02**: EditScreen で folder 入力欄が vault の次に表示される 🔵
  - **確認方法**: Compose UI テストで folder テキストフィールドの位置を確認

- [ ] **TC-041-03**: EditScreen で title 欄が vault/folder の後に表示される 🔵
  - **確認方法**: Compose UI テストで title テキストフィールドの位置を確認

- [ ] **TC-041-04**: EditScreen で title 欄と frontmatter フィールド群が視覚的に分離されている 🔵
  - **確認方法**: title セクションと frontmatter セクションに別々のラベルまたは区切りが存在する

- [ ] **TC-041-05**: EditScreen で本文欄がフォームの最下部（frontmatter の後）に表示される 🔵
  - **確認方法**: Compose UI テストで body テキストフィールドの位置を確認

---

## REQ-051〜053: TemplateEditScreen UI 変更 🔵

**信頼性**: 🔵 *ユーザヒアリング「編集画面からvault/folderを削除」より*

### テストケース

- [ ] **TC-051-01**: TemplateEditScreen に vault 入力欄が存在しない 🔵
  - **確認方法**: Compose UI テストで vault 関連ノードが見つからない

- [ ] **TC-051-02**: TemplateEditScreen に folder 入力欄が存在しない 🔵
  - **確認方法**: Compose UI テストで folder 関連ノードが見つからない

- [ ] **TC-051-03**: TemplateEditScreen に本文テンプレート入力エリアが表示される 🔵
  - **確認方法**: Compose UI テストで body テンプレートテキストフィールドが存在する

- [ ] **TC-051-04**: 本文テンプレートに `{{content}}` を含むテキストを入力・保存できる 🔵
  - **入力**: body = `"## 記事\n{{content}}\n\n## メモ\n"`
  - **期待結果**: 保存後にDB から同じ body 値が取得できる

- [ ] **TC-051-05**: 本文テンプレートが空欄でもテンプレートを保存できる 🔵
  - **入力**: body = `""`
  - **期待結果**: バリデーションエラーなし、正常保存

---

## テストケースサマリー

### カテゴリ別件数

| カテゴリ | 正常系 | 異常系 | Edgeケース | 合計 |
|---------|--------|--------|----------|------|
| Template モデル変更・DB | 4 | 0 | 0 | 4 |
| 本文テンプレート | 3 | 0 | 3 | 6 |
| vault/folder 管理 | 4 | 0 | 1 | 5 |
| テンプレート適用ロジック | 2 | 0 | 0 | 2 |
| EditScreen UI | 5 | 0 | 0 | 5 |
| TemplateEditScreen UI | 5 | 0 | 0 | 5 |
| **合計** | **23** | **0** | **4** | **27** |

### 信頼性レベル分布

- 🔵 青信号: 25件 (93%)
- 🟡 黄信号: 2件 (7%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: ✅ 高品質

### 優先度別テストケース

- **Must Have**: 27件（全件）
- **Should Have**: 0件
- **Could Have**: 0件

---
name: template-content-management-interview
description: テンプレートの管理内容の変更 ヒアリング記録
metadata:
  type: project
---

# テンプレートの管理内容の変更 ヒアリング記録

**作成日**: 2026-06-07
**ヒアリング実施**: kairo-requirements step4

## ヒアリング目的

PRD「テンプレートの管理内容の変更.md」に基づき、既存の edit-template-management 設計との差分を明確化するためのヒアリングを実施。
既存実装（TASK-0025〜0042 全完了）を踏まえ、変更箇所を特定する。

---

## 質問と回答

### Q1: vault/folder の保存先について

**カテゴリ**: 未定義部分詳細化
**背景**: PRD「保存先の内容は保存時に都度設定する前提の挙動にする」とあるが、
既存実装では Template モデルに vault/folder が含まれていた。
DataStore に保持するのか、毎回空欄から入力させるのかを確認する必要があった。

**回答**: DataStoreのデフォルト値を使いつつ設定画面でも変更可

**信頼性への影響**:
- REQ-021〜024（vault/folder 管理方針）の信頼性が 🔴 → 🔵 に向上
- SettingsScreen での vault/folder 編集は既存機能のため変更量が少ない

---

### Q2: 本文テンプレートのプレースホルダー形式について

**カテゴリ**: 未定義部分詳細化
**背景**: PRD「フィールドは本文含めてすべて、テンプレート内で指定可能にする」とあるが、
共有コンテンツとの関係（どこに/どうやって入るのか）が不明だった。

**回答**: プレースホルダーで共有内容を埋め込む → `{{content}}` 形式

**信頼性への影響**:
- REQ-011〜014（body template 仕様）の信頼性が 🔴 → 🔵 に向上

---

### Q3: テンプレートに本文が未設定の場合の挙動について

**カテゴリ**: 未定義部分詳細化
**背景**: body が空欄のテンプレートが適用された場合の動作を確認。

**回答**: 共有コンテンツをそのまま使用

**信頼性への影響**:
- REQ-013（body 未設定時の挙動）の信頼性が 🟡 → 🔵 に向上

---

### Q4: EditScreen の title 欄の扱いについて

**カテゴリ**: 既存設計確認
**背景**: PRD の表示順「vault,フォルダ, frontmatter, 本文」に title が明示されていなかった。
既存の EditScreen は title を最上部に表示しているため確認が必要だった。

**回答**: 今のtitleはファイル名という扱いで明確にfrontmatterとは別扱いにしたい

**信頼性への影響**:
- REQ-041・042（EditScreen 表示順と title の位置）の信頼性が 🟡 → 🔵 に向上
- 表示順は `vault → folder → title（ファイル名） → frontmatter → body` と確定

---

### Q5: 既存 Template モデルの vault/folder の変更方針について

**カテゴリ**: 影響範囲確認
**背景**: 既存の Template ドメインモデルと Room エンティティには vault/folder が含まれる。
削除するのかデッドコードとして残すのかで DB マイグレーション戦略が変わる。

**回答**: Templateからvault/folderを削除しbodyを追加

**信頼性への影響**:
- REQ-001〜003（Template モデル変更）の信頼性が 🟡 → 🔵 に向上
- DB マイグレーションの実装が必要と確定

---

### Q6: TemplateEditScreen の vault/folder エリアについて

**カテゴリ**: 影響範囲確認
**背景**: TemplateEditScreen に現在ある vault/folder 入力欄の扱いを確認。

**回答**: 編集画面からvault/folderを削除

**信頼性への影響**:
- REQ-051（TemplateEditScreen からの vault/folder 削除）の信頼性が 🟡 → 🔵 に向上

---

## ヒアリング結果サマリー

### 確認できた事項

- vault/folder は DataStore グローバル設定として維持し、SettingsScreen で編集可
- EditScreen では DataStore の値を初期値として表示し、保存時に上書き可能（DataStore には保存しない）
- 本文テンプレートは `{{content}}` プレースホルダーで共有コンテンツを埋め込む
- body 未設定時は共有コンテンツをそのまま使用（後方互換）
- title はファイル名として frontmatter とは別セクションで表示
- Template モデルから vault/folder を削除し body を追加（DB マイグレーション必要）
- TemplateEditScreen から vault/folder UI を削除

### 追加/変更要件

- Template ドメインモデル・Room エンティティの大幅変更（vault/folder 削除、body 追加）
- TemplateApplicator に body テンプレート解決ロジック追加
- EditFormState に vault/folder フィールド追加
- EditScreen の UI 大幅変更（表示順変更 + vault/folder 入力欄追加）
- TemplateEditScreen の UI 変更（vault/folder 削除 + body 入力エリア追加）

### 残課題

- body 内に `{{content}}` が複数ある場合の動作（🟡 全置換と推定）
- body に `{{content}}` なしかつ非空の場合の共有コンテンツの扱い（🟡 テンプレートbodyのみ使用と推定）

### 信頼性レベル分布

**ヒアリング前**:
- 🔵 青信号: 0件
- 🟡 黄信号: 4件
- 🔴 赤信号: 10件

**ヒアリング後**:
- 🔵 青信号: 18件 (+18)
- 🟡 黄信号: 4件 (+0)
- 🔴 赤信号: 0件 (-10)

---

## 関連文書

- **要件定義書**: [requirements.md](requirements.md)
- **ユーザストーリー**: [user-stories.md](user-stories.md)
- **受け入れ基準**: [acceptance-criteria.md](acceptance-criteria.md)

# 展開内容の編集・プレビュー機能 設計ヒアリング記録

**作成日**: 2026-03-29
**ヒアリング実施**: step4 既存情報ベースの差分ヒアリング

## ヒアリング目的

既存の share-content-expansion 実装（14タスク完了済み）を基盤として、編集・プレビュー機能の技術設計に必要な追加決定事項を明確化するためのヒアリングを実施しました。

## 質問と回答

### Q1: FrontmatterBuilder・ObsidianUriBuilder のインターフェース変更方針

**質問日時**: 2026-03-29
**カテゴリ**: アーキテクチャ
**背景**:
- `FrontmatterBuilder.build(title, body)` はタグを `AppConfig.OBSIDIAN_TAGS` から内部参照する
- `ObsidianUriBuilder.build(content, title)` はフォルダを `AppConfig.OBSIDIAN_FOLDER` から内部参照する
- 編集画面でユーザーが変更したタグ・フォルダを反映するためには、何らかの形でこれらを受け取る経路が必要
- 要件定義書 REQ-402「FrontmatterBuilder および ObsidianUriBuilder の実装は変更しないこと」と矛盾する可能性

**回答**:
「将来的にここに使う要素をユーザー側で指定できるようにする可能性があるので、そこを意識した構造にしたい」

**信頼性への影響**:
- REQ-402（ビルダー変更なし）の解釈が確定: 既存ビルダーは変更しない
- 新規 `NoteComposer` を導入し、編集画面フローでは `NoteComposer` が明示的パラメータを受け取って Frontmatter + URI を生成する設計に決定 → 🔵
- `NoteConfig` データクラスを導入し、vault/folder/defaultTags を保持。現在は `AppConfig` の値を使用するが、将来のユーザー設定化への拡張ポイントとして設計 → 🔵
- `NoteConfig.fromAppConfig()` ファクトリ関数で現在の動作と将来の拡張を分離 → 🔵

---

## ヒアリング結果サマリー

### 確認できた事項

- 既存 `FrontmatterBuilder`・`ObsidianUriBuilder` は変更しない（REQ-402 維持）
- 編集画面フローでは新規 `NoteComposer` が明示的パラメータで Frontmatter + URI を生成する
- 将来のユーザー設定化を見越した `NoteConfig` データクラスを設計段階から導入する
- `NoteConfig` は vault・folder・defaultTags を保持し、設定ソースの変更に対して ViewModel の実装変更を最小化する

### 設計方針の決定事項

| 決定事項 | 内容 |
|---------|------|
| 既存ビルダー | `FrontmatterBuilder`・`ObsidianUriBuilder` は変更しない（REQ-402 遵守） |
| 新規ビルダー | `NoteComposer` を `format/` パッケージに追加。`buildFrontmatter(title, body, tags)` + `buildUri(content, title, NoteConfig)` |
| 設定クラス | `NoteConfig(vault, folder, defaultTags)` を導入。現在は `NoteConfig.fromAppConfig()` で初期化 |
| 将来の拡張 | 将来 `NoteConfig.fromUserSettings()` を追加することで、SharedPreferences/Room からユーザー設定を読み込む経路を用意 |
| ViewModel | `EditScreenViewModel` が `NoteConfig` を受け取り、フォームの初期値と送信パラメータに使用 |

### 残課題

- ViewModel の状態スコープ: `viewModels()` での Activity スコープで問題なし（シングルアクティビティのため）
- 本文フィールドのスクロール動作の詳細（実装時に判断）

### 信頼性レベル分布

**ヒアリング前**:
- 🔵 青信号: 6件
- 🟡 黄信号: 4件
- 🔴 赤信号: 3件

**ヒアリング後**:
- 🔵 青信号: 12件 (+6)
- 🟡 黄信号: 3件 (-1)
- 🔴 赤信号: 0件 (-3)

## 関連文書

- **アーキテクチャ設計**: [architecture.md](architecture.md)
- **データフロー**: [dataflow.md](dataflow.md)
- **型定義**: [interfaces.kt](interfaces.kt)
- **要件定義**: [requirements.md](../../spec/content-edit-preview/requirements.md)
- **要件ヒアリング記録**: [spec-interview-record.md](../../spec/content-edit-preview/interview-record.md)

# TASK-0057 TDD要件定義書: TemplateEntity/TemplateFieldEntity/AppDatabase Migration(2→3)・Repositoryマッピング修正

**機能名**: database-migration-v3（TemplateEntity/TemplateFieldEntity への LLM プロンプトカラム追加と Room v2→v3 マイグレーション）
**タスクID**: TASK-0057
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0057/database-migration-v3-requirements.md`

---

## 【信頼性レベル指示】

各項目について、元の資料（EARS要件定義書・設計文書）との照合状況を以下の信号で表記する：

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: Room データベースのスキーマを version 2 から version 3 へ拡張し、`templates` テーブルに本文用LLMプロンプト列 `bodyLlmPrompt` を、`template_fields` テーブルにフィールド用LLMプロンプト列 `llmPrompt` を追加する。あわせて `MIGRATION_2_3`（ADD COLUMN のみ）を実装し、`TemplateRepositoryImpl` のエンティティ⇔ドメイン双方向マッピングに両カラムを反映する。
- 🔵 **どのような問題を解決するか**: LLMによるメモ更改機能（本文リライト・カスタムフィールドLLM生成）で使用する「テンプレート単位の本文プロンプト」「フィールド単位のプロンプト」を永続化する。DB層に保存領域がないと後続の Phase 3/6 UI からプロンプトを保存・読込できないため、その基盤を提供する。
- 🔵 **想定されるユーザー**: 直接のエンドユーザーではなく、上位層（`TemplateEditViewModel`, `EditScreenViewModel`, `TemplateApplicator` 等）が本機能の永続化APIを利用する。既存ユーザーのメモ・テンプレートデータは破壊せず後方互換で移行される。
- 🔵 **システム内での位置づけ**: Phase 1（基盤構築）のデータ永続化層。DB層（Room ORM + SQLite）に閉じた変更で、DAO クエリ本体・上位UIロジックには影響を与えない。TASK-0056（ドメインモデルへの `bodyLlmPrompt`/`llmPrompt`/`FieldValueSource.LLM` 追加）を前提とし、後続 TASK-0062/0070/0071 の前提となる。

- **参照したEARS要件**: REQ-101（本文用LLMプロンプト）、REQ-104（カスタムフィールド用プロンプト入力欄）、REQ-303（`FieldValueSource` への `LLM` 追加）
- **参照した設計文書**: `docs/design/llm-memo-rewrite/database-schema.kt`（「TemplateEntity（変更後）」「TemplateFieldEntity（変更後）」「AppDatabase（変更後）」「Version 2 → Version 3 スキーマ Diff」）、`docs/design/llm-memo-rewrite/architecture.md`（変更が必要な既存コンポーネント）

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

本タスクは「永続化とマッピング」の変更であり、外部APIというよりデータ変換の入出力仕様として定義する。

### 2.1 スキーマ（DDL）仕様 🔵

- 🔵 `templates` テーブル（version 3）に列追加:
  - `bodyLlmPrompt TEXT NOT NULL DEFAULT ''`（`body` の直後、`isDefault` の前）
- 🔵 `template_fields` テーブル（version 3）に列追加:
  - `llmPrompt TEXT NOT NULL DEFAULT ''`（`metaKey` の直後、`sortOrder` の前）
- 🔵 `valueSource` 列はカラム定義変更なし。`FieldValueSource.name()` を格納する文字列列であり、新たに `"LLM"` という値が格納可能になるのみ。

### 2.2 エンティティ型定義 🔵

- 🔵 `TemplateEntity`: `val bodyLlmPrompt: String = ""` を追加
- 🔵 `TemplateFieldEntity`: `val llmPrompt: String = ""` を追加
- 型: `String`、デフォルト値 `""`（空文字）、NULL 非許容

### 2.3 マッピングの入出力仕様 🔵

`TemplateRepositoryImpl` の変換関数における入出力対応:

| 変換方向 | 入力 | 出力 | 追加マッピング |
|---------|------|------|--------------|
| `TemplateWithFields.toDomain()` | `TemplateEntity`（`bodyLlmPrompt` 保持） | `Template` | `bodyLlmPrompt = template.bodyLlmPrompt` |
| `TemplateFieldEntity.toDomain()` | `TemplateFieldEntity`（`llmPrompt` 保持） | `TemplateField` | `llmPrompt = llmPrompt` |
| `Template.toEntity()` | `Template`（`bodyLlmPrompt` 保持） | `TemplateEntity` | `bodyLlmPrompt = bodyLlmPrompt` |
| `TemplateField.toEntity(templateId)` | `TemplateField`（`llmPrompt` 保持） | `TemplateFieldEntity` | `llmPrompt = llmPrompt` |

- 🔵 **入出力の関係性**: `toEntity()` → `toDomain()` の往復変換で `bodyLlmPrompt`/`llmPrompt` の値が損失なく保持される（値の等価性が成立する）。既存フィールド（`id`, `name`, `body`, `isDefault`, `key`, `valueSource`, `valueType`, `defaultValue`, `metaKey`, `sortOrder`）のマッピングは変更しない。

### 2.4 マイグレーション入出力仕様 🔵

- 🔵 **入力**: version 2 スキーマの既存レコード（`bodyLlmPrompt`/`llmPrompt` 列なし）
- 🔵 **出力**: version 3 スキーマ。既存レコードの全既存カラム値が保持され、追加列は `''`（空文字）で初期化される
- 🔵 **Migration SQL**:
  ```sql
  ALTER TABLE templates ADD COLUMN bodyLlmPrompt TEXT NOT NULL DEFAULT ''
  ALTER TABLE template_fields ADD COLUMN llmPrompt TEXT NOT NULL DEFAULT ''
  ```

- **参照したEARS要件**: REQ-101, REQ-104, REQ-303
- **参照した設計文書**: `database-schema.kt`（マッピング関数記述例・スキーマ Diff）、`interfaces.kt`（`Template`/`TemplateField`/`FieldValueSource` 型定義）

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **後方互換性制約**: 追加カラムはすべて `NOT NULL DEFAULT ''` とし、既存レコード・既存呼び出し元（`TemplateApplicator`, `MainActivity` 等）を破壊しない。エンティティ／ドメインの追加プロパティにも `= ""` のデフォルト値を付与する。
- 🔵 **既存マイグレーション不変更制約**: `MIGRATION_1_2` の実装は一切変更しない。既存テスト `migrate1To2_preservesDataAndAddsBody()` が回帰なく通り続けること。
- 🔵 **マイグレーション登録順序制約**: `DatabaseModule` の `addMigrations(...)` にバージョン昇順で `AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3` を登録する。`fallbackToDestructiveMigration()` は使用しない（既存パターン継続）。
- 🔵 **SQLite制約**: 本マイグレーションは `ADD COLUMN` のみで `DROP COLUMN` を含まないため、SQLite 3.35+ 制約（DROP COLUMN 用）の対象外。minSdk 33（Android 13 = SQLite 3.39+）で安全に実行可能。
- 🔵 **DAO不変更制約**: `TemplateDao` のクエリ本体は変更しない。カラム追加はエンティティ側で吸収する。
- 🔵 **スキーマエクスポート制約**: `@Database(exportSchema = true)` を維持し、version 3 スキーマ JSON がエクスポートされる。
- 🔵 **パフォーマンス（NFR-001）**: NFR-001（LLMタイムアウト30秒）はLLM呼び出しに関する要件でありDB層マイグレーションには直接適用されない。マイグレーションはローカル実行で軽量（ADD COLUMN 2件）。
- 🔵 **セキュリティ（NFR-101）**: APIキー暗号化はDB層の対象外（`LlmSettingsRepository` 側で扱う）。本タスクのプロンプト文字列は平文保存で問題ない。

- **参照したEARS要件**: REQ-101, REQ-104, REQ-303, NFR-001
- **参照した設計文書**: `database-schema.kt`（AppDatabase 変更後・スキーマ Diff・注意事項）、既存 `AppDatabase.kt`（`MIGRATION_1_2`）、`DatabaseModule.kt`（`addMigrations` 呼び出し）

---

## 4. 想定される使用例（Edgeケース・データフローベース）

### 4.1 基本的な使用パターン 🔵

- 🔵 **新規テンプレート保存**: `bodyLlmPrompt`/`llmPrompt` を含む `Template`/`TemplateField` を `saveTemplate()` で保存 → `toEntity()` で両カラムが DB に書き込まれる → `getTemplateById()` で `toDomain()` により値が復元される。
- 🔵 **既存アプリのアップデート起動**: version 2 の DB を持つ既存ユーザーがアプリ更新 → 初回DBアクセス時に `MIGRATION_2_3` が自動実行 → 既存テンプレート・フィールドが保持され、`bodyLlmPrompt`/`llmPrompt` が `''` で埋まる。

### 4.2 データフロー 🔵

```
[上位層(ViewModel/Applicator)]
  → Template(bodyLlmPrompt), TemplateField(llmPrompt)
    → TemplateRepositoryImpl.saveTemplate()
      → toEntity() でカラムマッピング
        → TemplateDao (INSERT)
          → SQLite (templates.bodyLlmPrompt / template_fields.llmPrompt)

[読込]
  SQLite → TemplateDao (SELECT, TemplateWithFields)
    → toDomain() でカラムマッピング
      → Template / TemplateField（LLMプロンプト復元）
```

### 4.3 エッジケース 🔵🟡

- 🔵 **EDGE(デフォルト値)**: `bodyLlmPrompt`/`llmPrompt` を指定せず生成した `Template`/`TemplateField` は往復変換後も `""` を保持する（後方互換）。
- 🔵 **EDGE(既存データ移行)**: version 2 の既存レコードが `MIGRATION_2_3` 後に全既存カラム値を保持し、追加列のみ `''` になる。
- 🟡 **EDGE(LLM値ソース)**: `valueSource = FieldValueSource.LLM`（`"LLM"` 文字列）を持つフィールドが `toEntity()`/`toDomain()` 往復で `LLM` のまま保持される（`FieldValueSource.valueOf("LLM")` が例外を投げない ＝ TASK-0056 で enum に `LLM` が追加済みであることが前提）。
- 🟡 **EDGE(空フィールドテンプレート)**: `template_fields` にレコードが無いテンプレートでもマイグレーション・マッピングが正常動作する（`template_fields` への ADD COLUMN は0行に対しても成功）。

### 4.4 エラーケース 🔵

- 🔵 **マイグレーション未登録エラー**: `MIGRATION_2_3` を `addMigrations` に登録し忘れると Room が `IllegalStateException`（マイグレーション経路欠如）を投げる。→ `DatabaseModule` への登録で防止する。本タスクの完了条件に含める。

- **参照したEARS要件**: REQ-101, REQ-104, REQ-303
- **参照した設計文書**: `database-schema.kt`（スキーマ Diff・備考）、`docs/design/llm-memo-rewrite/dataflow.md`（永続化フロー）

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: 「LLMによるメモ更改機能の追加」（本文リライト・カスタムフィールドLLM生成のための永続化基盤）
- **参照した機能要件**:
  - REQ-101（テンプレート本文用LLMプロンプト → `templates.bodyLlmPrompt`）
  - REQ-104（カスタムフィールド用プロンプト → `template_fields.llmPrompt`）
  - REQ-303（`FieldValueSource` への `LLM` 追加 → `valueSource` 文字列列で吸収）
- **参照した非機能要件**:
  - NFR-001（LLMタイムアウト30秒 — 本タスクは直接対象外だが確認事項として記載）
  - NFR-101（APIキー暗号化 — 本タスクは対象外）
- **参照したEdgeケース**: デフォルト値保持、既存データ移行保持、`LLM` 値ソースの往復保持、空フィールドテンプレート
- **参照した受け入れ基準**:
  - version 2→3 マイグレーションが実データで動作し既存レコードの追加列が `''` で初期化される
  - `MigrationTestHelper` によるマイグレーションテストが通る（`connectedAndroidTest`）
  - `TemplateRepositoryImpl` マッピングテストが通る
  - 既存 `MIGRATION_1_2` および既存マイグレーションテストが変更なく通り続ける
- **参照した設計文書**:
  - **アーキテクチャ**: `docs/design/llm-memo-rewrite/architecture.md`（変更が必要な既存コンポーネント: TemplateEntity, AppDatabase, TemplateRepositoryImpl）
  - **データフロー**: `docs/design/llm-memo-rewrite/dataflow.md`（永続化フロー）
  - **型定義**: `docs/design/llm-memo-rewrite/interfaces.kt`（`Template`, `TemplateField`, `FieldValueSource`）
  - **データベース**: `docs/design/llm-memo-rewrite/database-schema.kt`（version 3 スキーマ・MIGRATION_2_3・マッピング）
  - **API仕様**: 本タスクは外部API非該当

---

## 6. 実装・テスト対象ファイル

### 実装対象（`app/src/main/...`）

| ファイル | 変更内容 | 信頼性 |
|---------|---------|--------|
| `app/src/main/java/com/den4dr/share2Obsidian/data/db/TemplateEntity.kt` | `bodyLlmPrompt: String = ""` 追加 | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/data/db/TemplateFieldEntity.kt` | `llmPrompt: String = ""` 追加 | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/data/db/AppDatabase.kt` | version 3, `MIGRATION_2_3` 追加 | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/di/DatabaseModule.kt` | `addMigrations(MIGRATION_1_2, MIGRATION_2_3)` へ更新 | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImpl.kt` | `toDomain()`/`toEntity()` に両カラムマッピング追加 | 🔵 |

### テスト対象

| ファイル | テスト内容 | 信頼性 |
|---------|---------|--------|
| `app/src/test/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImplTest.kt` | 往復変換ユニットテスト（3件） | 🔵 |
| `app/src/androidTest/java/com/den4dr/share2Obsidian/data/db/AppDatabaseMigrationTest.kt` | `migrate2To3_preservesDataAndAddsLlmColumns()` 統合テスト（1件） | 🔵 |

---

## 7. テスト要件サマリー

- 🔵 **TC1（ユニット）**: `bodyLlmPrompt = "本文を要約"` の `Template` を `toEntity()`→`toDomain()` 往復して値が保持される
- 🔵 **TC2（ユニット）**: `llmPrompt = "タイトルを生成"`, `valueSource = FieldValueSource.LLM` の `TemplateField` を往復して `llmPrompt` と `valueSource` が保持される
- 🔵 **TC3（ユニット）**: `bodyLlmPrompt`/`llmPrompt` 未指定（デフォルト `""`）が往復後も `""` を保持する
- 🔵 **TC4（統合）**: `MigrationTestHelper` で version 2 スキーマ＋ダミーレコードを作成し `MIGRATION_2_3` 適用後、既存カラム保持＋`bodyLlmPrompt`/`llmPrompt` が `''` で追加されることを cursor で検証
- 🔵 **回帰**: 既存 `migrate1To2_preservesDataAndAddsBody()` が変更なく通り続ける

---

## 8. 品質判定

```
✅ 高品質:
- 要件の曖昧さ: なし（スキーマ・マッピング・SQL がすべて設計文書に明記）
- 入出力定義: 完全（DDL・エンティティ型・マッピング表・Migration SQL を明示）
- 制約条件: 明確（後方互換・既存不変更・登録順序・SQLite制約）
- 実装可能性: 確実（既存 MIGRATION_1_2 パターンの踏襲、ADD COLUMN のみ）
- 信頼性レベル: 🔵 が支配的
```

### 信頼性レベル分布

| セクション | 🔵 青 | 🟡 黄 | 🔴 赤 |
|-----------|-------|-------|-------|
| 1. 機能概要 | 4 | 0 | 0 |
| 2. 入出力仕様 | 6 | 0 | 0 |
| 3. 制約条件 | 8 | 0 | 0 |
| 4. 使用例・エッジ | 6 | 2 | 0 |
| 合計 | 24 | 2 | 0 |

- 🔵 青信号: 24項目（92%）
- 🟡 黄信号: 2項目（8%、`LLM` 値ソース往復・空フィールドテンプレートの妥当な推測）
- 🔴 赤信号: 0項目（0%）

**総合品質評価**: ✅ 高品質

---

## 次のお勧めステップ

`/tsumiki:tdd-testcases llm-memo-rewrite TASK-0057` でテストケースの洗い出しを行います。

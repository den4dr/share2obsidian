# TDD開発メモ: database-migration-v3

## 概要

- 機能名: database-migration-v3（TemplateEntity/TemplateFieldEntity への LLM プロンプトカラム追加と Room v2→v3 マイグレーション）
- 開発開始: 2026-07-06
- 現在のフェーズ: 完了（Refactor済み）

## 関連ファイル

- 元タスクファイル: `docs/tasks/llm-memo-rewrite/TASK-0057.md`
- 要件定義: `docs/implements/llm-memo-rewrite/TASK-0057/database-migration-v3-requirements.md`
- テストケース定義: `docs/implements/llm-memo-rewrite/TASK-0057/database-migration-v3-testcases.md`
- Redフェーズ記録: `docs/implements/llm-memo-rewrite/TASK-0057/database-migration-v3-red-phase.md`
- 実装ファイル（Greenフェーズで変更予定）:
  - `app/src/main/java/com/den4dr/share2Obsidian/data/db/TemplateEntity.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/data/db/TemplateFieldEntity.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/data/db/AppDatabase.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/di/DatabaseModule.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImpl.kt`
- テストファイル:
  - `app/src/test/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImplTest.kt`（ユニット7件追加）
  - `app/src/androidTest/java/com/den4dr/share2Obsidian/data/db/AppDatabaseMigrationTest.kt`（統合4件追加）

## Redフェーズ（失敗するテスト作成）

### 作成日時

2026-07-06

### テストケース

`database-migration-v3-testcases.md` の全10テストケース（正常系4・異常系2・境界値4）を対象とし、TC-B02をユニット部分/統合部分に分割して計11テストメソッドを実装した。

- ユニット（`TemplateRepositoryImplTest.kt`）: TC-N01, TC-N02, TC-N03, TC-E02, TC-B01, TC-B02(unit), TC-B03（7件）
- 統合（`AppDatabaseMigrationTest.kt`）: TC-N04, TC-E01, TC-B02(integration), TC-B04（4件）

詳細は `database-migration-v3-red-phase.md` を参照。

### テストコード

テストコード全文は Red フェーズ記録ファイル（`database-migration-v3-red-phase.md`）および実際のテストファイルを参照。

### 期待される失敗

- ユニットテスト: `TemplateEntity`/`TemplateFieldEntity` に `bodyLlmPrompt`/`llmPrompt` が存在しないため `compileDebugUnitTestKotlin` がコンパイルエラーで失敗（`mise exec -- ./gradlew :app:testDebugUnitTest --tests "*TemplateRepositoryImplTest*"` で確認済み）
- 統合テスト: `AppDatabase.MIGRATION_2_3` が存在しないため `compileDebugAndroidTestKotlin` がコンパイルエラーで失敗（`mise exec -- ./gradlew :app:compileDebugAndroidTestKotlin` で確認済み）。実機実行（`connectedAndroidTest`）は本環境に adb/エミュレータが無く未実施。Greenフェーズ実装後にデバイス/エミュレータ環境で実行が必要。

### 次のフェーズへの要求事項

Greenフェーズで以下を実装し、テストを通過させる:

1. `TemplateEntity.kt` に `bodyLlmPrompt: String = ""` を追加
2. `TemplateFieldEntity.kt` に `llmPrompt: String = ""` を追加
3. `AppDatabase.kt` を `version = 3` にし `MIGRATION_2_3` を追加（`MIGRATION_1_2` は変更しない）
4. `DatabaseModule.kt` の `addMigrations` に `MIGRATION_2_3` を追加
5. `TemplateRepositoryImpl.kt` の `toDomain()`/`toEntity()` に `bodyLlmPrompt`/`llmPrompt` マッピングを追加
6. 実装後、`mise exec -- ./gradlew test` でユニットテスト成功を確認
7. 可能であれば `mise exec -- ./gradlew connectedAndroidTest`（デバイス/エミュレータ必須）で統合テスト成功を確認

## Greenフェーズ（最小実装）

### 実装日時

2026-07-06

### 実装方針

要件定義書・note.md・database-schema.kt に明記済みの内容をそのまま実装し、推測を伴う判断は発生しなかった（仕様と実装コードに差異なし）。

1. `TemplateEntity.kt`: `body` の直後・`isDefault` の前に `bodyLlmPrompt: String = ""` を追加
2. `TemplateFieldEntity.kt`: `metaKey` の直後・`sortOrder` の前に `llmPrompt: String = ""` を追加
3. `AppDatabase.kt`: `version` を 2→3 に変更し、`MIGRATION_1_2` は変更せず `MIGRATION_2_3`（`ALTER TABLE ... ADD COLUMN ... DEFAULT ''` を2件）を追加
4. `DatabaseModule.kt`: `addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)` へバージョン昇順で登録
5. `TemplateRepositoryImpl.kt`: `toDomain()`（`TemplateWithFields`/`TemplateFieldEntity`）と `toEntity()`（`Template`/`TemplateField`）の双方に `bodyLlmPrompt`/`llmPrompt` マッピングを追加（双方向対称性を担保）

加えて、Redフェーズで作成されたテスト `getAllTemplates_unknownValueSource_throwsIllegalArgumentException`（TC-E02）が Kotlin の戻り値型推論により `InvalidTestClassError`（JUnit 4 は `@Test` メソッドに `void` 相当を要求）でテストクラス全体を起動不能にしていたため、戻り値型を明示的に `Unit` に修正した（アサーション内容は変更なし）。

### 実装コード

実装コード全文は本ファイルと同ディレクトリの `database-migration-v3-green-phase.md` を参照。

### テスト結果

- ユニットテスト（`mise exec -- ./gradlew :app:testDebugUnitTest --tests "*TemplateRepositoryImplTest*"`）: 成功（対象11件、既存4件含む）
- ユニットテスト全体（`mise exec -- ./gradlew :app:testDebugUnitTest`）: 成功（既存クラス回帰なし）
- 統合テストコンパイル確認（`mise exec -- ./gradlew :app:compileDebugAndroidTestKotlin`）: 成功
- 統合テスト実機実行（`connectedAndroidTest`）: 本環境に adb/エミュレータが無いため未実施（次フェーズ以降で別途確認が必要）

### 課題・改善点

- 統合テスト（`AppDatabaseMigrationTest`）4件の実機/エミュレータでの実行時成功確認が未了
- `MIGRATION_2_3` のコメント粒度が既存 `MIGRATION_1_2` と若干異なる（機能に影響なし、Refactorフェーズで統一を検討）

詳細は `database-migration-v3-green-phase.md` を参照。

## Refactorフェーズ（品質改善）

### リファクタリング日時

2026-07-06

### 改善内容

Greenフェーズの実装は既に要件定義書・note.md・database-schema.kt に忠実な最小実装であり、機能・設計面での改善点は見当たらなかった。以下を実施した。

- `TemplateRepositoryImpl.kt` の4つの private マッピング関数（`toDomain()`×2, `toEntity()`×2）に、機能概要・設計方針・保守性を要約するKDocコメントを追加（可読性向上、機能変更なし）
- `MIGRATION_2_3` と `MIGRATION_1_2` のコメント粒度の差異は、要件定義書の「既存マイグレーション不変更制約」を優先し対応を見送った（コメントであっても既存マイグレーションには手を加えない方針）

### セキュリティレビュー結果

SQLインジェクション・データ漏洩・認可の観点で重大な脆弱性は発見されなかった。マイグレーションSQLは外部入力を含まないハードコードDDLであり、新規カラムのプロンプト文字列も既存のドメインモデル定義（TASK-0056）の型・デフォルト値をそのまま利用しているため、DB層での追加バリデーションは不要と判断した。

### パフォーマンスレビュー結果

`ALTER TABLE ... ADD COLUMN ... DEFAULT ''` は定数デフォルト値の列追加でありSQLite上O(1)相当（全行書き換え不要）。Repositoryマッピングの計算量もO(n)のまま変化なし。重大な性能課題は発見されなかった。

### 最終コード

改善後のコード全文は `database-migration-v3-refactor-phase.md` を参照。`TemplateEntity.kt`・`TemplateFieldEntity.kt`・`AppDatabase.kt`・`DatabaseModule.kt` はGreenフェーズから変更なし。

### テスト結果

- `mise exec -- ./gradlew :app:testDebugUnitTest --tests "*TemplateRepositoryImplTest*"`: 成功（13件全て）
- `mise exec -- ./gradlew :app:testDebugUnitTest`: 成功（プロジェクト全体、回帰なし）
- `mise exec -- ./gradlew :app:compileDebugAndroidTestKotlin`: 成功（統合テストのコンパイル継続成功）
- `connectedAndroidTest`（実機実行）: 本環境に adb/エミュレータが無いため未実施。継続する既知の制約であり、デバイス/エミュレータが用意でき次第、別途実行確認が必要

### 品質評価

✅ 高品質（テスト全件成功・セキュリティ/パフォーマンス上の重大な問題なし・lint警告なし・全ファイル500行未満）

詳細は `database-migration-v3-refactor-phase.md` を参照。

### 残課題（引き継ぎ事項）

- 統合テスト（`AppDatabaseMigrationTest`）の `migrate2To3_preservesDataAndAddsLlmColumns` 等4件について、デバイス/エミュレータ環境での `connectedAndroidTest` 実行による実機成功確認が未了。次フェーズ（`tdd-verify-complete`）以降、実機/エミュレータ環境が用意でき次第の確認を推奨する。

## 検証フェーズ（tdd-verify-complete）

### 実施日時

2026-07-06

### 確認内容

- **ユニットテスト（`mise exec -- ./gradlew cleanTest test --rerun-tasks`）**: フル実行。プロジェクト全体で失敗0件（`TemplateRepositoryImplTest` 13件含む全22クラス・約140件すべて成功）。`TemplateRepositoryImplTest` の実行時間は1.086秒（うち初回テストがMockK/コルーチン初期化コストで1.0秒、他は0.1秒未満）で遅延テストなし。
- **統合テスト（`AppDatabaseMigrationTest`）**: 本環境に adb/エミュレータが存在しないため `connectedAndroidTest` は実機実行不可。`mise exec -- ./gradlew compileDebugAndroidTestKotlin --rerun-tasks` でコンパイル成功を確認し、暫定確認とした（TASK-0019 検証時と同様の扱い）。テストファイルには5メソッド（既存回帰1件 `migrate1To2_preservesDataAndAddsBody` ＋新規4件 `migrate2To3_preservesDataAndAddsLlmColumns`/`migrate2To3_withoutMigrationRegistered_throwsIllegalStateException`/`migrate2To3_withNoTemplateFieldsRows_addsLlmPromptColumnSuccessfully`/`migrateFrom1To3_preservesDataAndInitializesAllNewColumns`）が実装されており、テストケース定義書（TC-N04, TC-E01, TC-B02統合, TC-B04＋回帰）と過不足なく一致。`@Ignore` 等のテスト無効化なし。
- **要件網羅**: REQ-101（bodyLlmPrompt）、REQ-104（llmPrompt）、REQ-303（FieldValueSource.LLM）すべて実装・テスト済み。テストケース定義書の全10ケース＋回帰1件が過不足なく実装済み（ユニット7件・統合4件・既存回帰継続）。

### 最終結果

- **実装率**: 100%（11/11 予定テストケース、回帰含め12/12）
- **成功率（実行確認できた範囲）**: ユニット100%（13/13）。統合はコンパイル成功のみで実機成功は未確認（既知の環境制約）
- **品質判定**: ✅ 高品質（完全達成・環境制約は暫定確認扱い）
- **TODO更新**: ✅ 完了マーク追加（`docs/tasks/llm-memo-rewrite/overview.md` および `TASK-0057.md`）

### 既知の環境制約（引き継ぎ）

- `AppDatabaseMigrationTest` の5メソッド（回帰1件＋新規4件）はデバイス/エミュレータ環境が用意でき次第、`mise exec -- ./gradlew connectedAndroidTest` による実機成功確認を推奨する。現時点ではコンパイル成功のみで暫定完了としている。

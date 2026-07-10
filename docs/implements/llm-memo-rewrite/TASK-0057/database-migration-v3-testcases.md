# TASK-0057 TDDテストケース定義書: TemplateEntity/TemplateFieldEntity/AppDatabase Migration(2→3)・Repositoryマッピング修正

**機能名**: database-migration-v3
**タスクID**: TASK-0057
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0057/database-migration-v3-testcases.md`

---

## 【信頼性レベル指示】

各テストケースについて、元の資料（要件定義書・設計文書・既存実装）との照合状況を以下の信号で表記する：

- 🔵 **青信号**: 元の資料を参考にしてほぼ推測していない
- 🟡 **黄信号**: 元の資料から妥当な推測
- 🔴 **赤信号**: 元の資料にない推測

---

## テスト設計の全体方針

本タスクは「DBスキーマ拡張（v2→v3）とマッピング反映」であり、テストは 2 系統に分かれる：

1. **ユニットテスト（`TemplateRepositoryImplTest`）**: `toDomain()`/`toEntity()` マッピングに `bodyLlmPrompt`/`llmPrompt` が正しく反映されるかを、既存の MockK + Flow パターンで検証する（`app/src/test/...`、`mise exec -- ./gradlew test`）。
2. **統合テスト（`AppDatabaseMigrationTest`）**: `MigrationTestHelper` で v2 スキーマに実データを投入し `MIGRATION_2_3` を適用、既存カラム保持＋新規カラム `''` 初期化を cursor で検証する（`app/src/androidTest/...`、`mise exec -- ./gradlew connectedAndroidTest`、デバイス/エミュレータ必須）。

既存テスト（`migrate1To2_preservesDataAndAddsBody`、既存の Repository テスト群）は変更せず回帰なく通し続けることを前提とする。

---

## 1. 正常系テストケース（基本的な動作）

### TC-N01: bodyLlmPrompt が Template→Entity→Domain 往復変換で保持される（ユニット）

- **テスト名**: bodyLlmPrompt の往復変換保持
  - **何をテストするか**: `TemplateEntity.bodyLlmPrompt` が `TemplateWithFields.toDomain()` により `Template.bodyLlmPrompt` へ正しくマッピングされること
  - **期待される動作**: DB から読み込んだ `bodyLlmPrompt` 値が損失なくドメインモデルに反映される
- **入力値**: `TemplateEntity(id=1L, name="テスト", body="本文", bodyLlmPrompt="本文を要約", isDefault=false)` を `TemplateWithFields(template=entity, fields=emptyList())` で包み、`dao.getAllTemplatesWithFields()` が `flowOf(listOf(withFields))` を返すよう MockK 設定
  - **入力データの意味**: `bodyLlmPrompt="本文を要約"` は REQ-101 のテンプレート本文用LLMプロンプトの代表的な実データ。既存フィールド（name/body/isDefault）も設定し、新規カラム追加が既存マッピングを壊さないことも同時確認
- **期待される結果**: `repository.getAllTemplates().first()[0].bodyLlmPrompt == "本文を要約"`（かつ name/body/isDefault も従来通り保持）
  - **期待結果の理由**: `toDomain()` に `bodyLlmPrompt = template.bodyLlmPrompt` が追加されるため、値が一致する（database-schema.kt マッピング定義）
- **テストの目的**: `toDomain()` の `bodyLlmPrompt` マッピング追加の確認
  - **確認ポイント**: 新規カラムのマッピングが追加され、既存カラムのマッピングが壊れていないこと
- 🔵 *要件定義書 TC1・TASK-0057 単体テスト要件・database-schema.kt マッピング定義に基づく*

### TC-N02: llmPrompt と valueSource=LLM が TemplateField 往復変換で保持される（ユニット）

- **テスト名**: llmPrompt と FieldValueSource.LLM の往復変換保持
  - **何をテストするか**: `TemplateFieldEntity.llmPrompt` が `toDomain()` で `TemplateField.llmPrompt` へマッピングされ、かつ `valueSource="LLM"` 文字列が `FieldValueSource.LLM` へ復元されること
  - **期待される動作**: フィールド単位のLLMプロンプトと LLM 値ソースが損失なく往復する
- **入力値**: `TemplateFieldEntity(id=10L, templateId=1L, key="title", valueSource="LLM", valueType="STRING", defaultValue="", metaKey="", llmPrompt="タイトルを生成", sortOrder=0)` を含む `TemplateWithFields`
  - **入力データの意味**: `valueSource="LLM"` + `llmPrompt="タイトルを生成"` は REQ-104/REQ-303 の代表実データ。`valueSource.valueOf("LLM")` が例外を投げないこと（TASK-0056 で enum に LLM 追加済み前提）も確認する
- **期待される結果**: 変換後 `field.llmPrompt == "タイトルを生成"` かつ `field.valueSource == FieldValueSource.LLM`
  - **期待結果の理由**: `toDomain()` に `llmPrompt = llmPrompt` が追加され、`FieldValueSource.valueOf("LLM")` が正常解決するため
- **テストの目的**: `toDomain()` の `llmPrompt` マッピングと LLM enum 解決の確認
  - **確認ポイント**: `llmPrompt` が復元されること、`FieldValueSource.valueOf("LLM")` が `IllegalArgumentException` を投げないこと
- 🔵 *要件定義書 TC2・TASK-0057 単体テスト要件・エッジ(LLM値ソース)に基づく（valueSource往復は🟡妥当な推測を含むが enum は TASK-0056 で実装済み）*

### TC-N03: saveTemplate（toEntity）で bodyLlmPrompt/llmPrompt が Entity へ書き込まれる（ユニット）

- **テスト名**: toEntity() による bodyLlmPrompt/llmPrompt の書き込み
  - **何をテストするか**: `Template.toEntity()` / `TemplateField.toEntity(templateId)` が両カラムを Entity に設定し、`dao.insertTemplate` / `dao.insertFields` へ渡すこと
  - **期待される動作**: 保存経路で LLM プロンプトが Entity に載る
- **入力値**: `Template(id=1L, name="t", body="b", bodyLlmPrompt="要約して", fields=listOf(TemplateField(key="title", valueSource=FieldValueSource.LLM, valueType=FieldValueType.STRING, llmPrompt="生成して")), isDefault=false)` を `repository.saveTemplate(...)`。`dao.insertTemplate/deleteFieldsByTemplateId/insertFields` を MockK でスタブ
  - **入力データの意味**: 保存方向（Domain→Entity）のマッピングを検証。読み込み方向（TC-N01/N02）と対称に確認する
- **期待される結果**: `coVerify` で `dao.insertTemplate` に `bodyLlmPrompt="要約して"` を持つ `TemplateEntity`、`dao.insertFields` に `llmPrompt="生成して"` を持つ `TemplateFieldEntity` が渡ること（スロット捕捉で検証）
  - **期待結果の理由**: `toEntity()` に `bodyLlmPrompt = bodyLlmPrompt` / `llmPrompt = llmPrompt` が追加されるため
- **テストの目的**: `toEntity()` 側の双方向マッピング対称性の確認
  - **確認ポイント**: 保存経路でも両カラムが欠落しないこと（注意事項「双方向マッピング」の担保）
- 🔵 *TASK-0057 実装詳細4「マッピングの対称性」・要件定義書 2.3 マッピング表に基づく*

### TC-N04: MIGRATION_2_3 で v2 実データが保持され新規カラムが '' で追加される（統合）

- **テスト名**: MIGRATION_2_3 による既存データ保持と LLM カラム追加
  - **何をテストするか**: `MigrationTestHelper` で v2 スキーマに投入した `templates`/`template_fields` レコードが、`MIGRATION_2_3` 適用後も既存カラム値を保持し、`bodyLlmPrompt`/`llmPrompt` 列が `''` で追加されること
  - **期待される動作**: ADD COLUMN のみの安全な移行が行われ、既存データが破壊されない
- **入力値**: v2 スキーマ DB を作成し、`INSERT INTO templates (id, name, body, isDefault) VALUES (1, 'テンプレA', '## 記事', 1)` および `INSERT INTO template_fields (id, templateId, key, valueSource, valueType, defaultValue, metaKey, sortOrder) VALUES (10, 1, 'title', 'HTML_META', 'STRING', '', 'OG_TITLE', 0)` を投入 → `helper.runMigrationsAndValidate(testDb, 3, true, AppDatabase.MIGRATION_2_3)`
  - **入力データの意味**: 既存ユーザーの v2 データ（テンプレート本文＋フィールド）を代表。移行後の後方互換性を検証
- **期待される結果**: cursor で `templates` の `name="テンプレA"`, `body="## 記事"`, `isDefault=1`, `bodyLlmPrompt=""`、`template_fields` の `key="title"`, `valueSource="HTML_META"`, `metaKey="OG_TITLE"`, `llmPrompt=""` を確認
  - **期待結果の理由**: `MIGRATION_2_3` が `ALTER TABLE ... ADD COLUMN ... DEFAULT ''` を実行するため、既存行に空文字が入る
- **テストの目的**: マイグレーションのデータ保持性とスキーマ検証（`validateDroppedTables=true`）
  - **確認ポイント**: 既存カラム全保持、両テーブルへの新規カラム追加、`runMigrationsAndValidate` がスキーマ不一致例外を投げないこと
- 🔵 *要件定義書 TC4・TASK-0057 統合テスト要件・既存 migrate1To2_preservesDataAndAddsBody パターンに基づく*

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-E01: MIGRATION_2_3 未登録時の Room 例外（統合／負の検証）

- **テスト名**: マイグレーション経路欠如時の IllegalStateException
  - **エラーケースの概要**: v2 DB を v3 へ開こうとする際、`MIGRATION_2_3` を渡さない（`addMigrations` 登録忘れを模した）と Room がマイグレーション経路欠如で例外を投げる
  - **エラー処理の重要性**: `DatabaseModule` への登録忘れは実行時クラッシュ直結の重大バグ。テストで「登録が必須」であることを保証する
- **入力値**: v2 スキーマで DB 作成後、`helper.runMigrationsAndValidate(testDb, 3, true)`（マイグレーション未指定）を実行
  - **不正な理由**: v2→v3 の移行手段を Room に与えていないため、自動移行できない
  - **実際の発生シナリオ**: `DatabaseModule.addMigrations(...)` に `MIGRATION_2_3` を追加し忘れたままアプリを更新起動したとき
- **期待される結果**: `IllegalStateException`（"A migration from 2 to 3 was required but not found" 相当）がスローされる（`@Test(expected = IllegalStateException::class)` もしくは assertThrows で捕捉）
  - **エラーメッセージの内容**: Room 標準の「必要なマイグレーションが見つからない」メッセージ
  - **システムの安全性**: `fallbackToDestructiveMigration()` 不使用のため、データ破壊ではなく例外で失敗する（データ保護優先）
- **テストの目的**: マイグレーション登録漏れの検出（回帰防止）
  - **品質保証の観点**: 完了条件「MIGRATION_2_3 が実データで動作」を破る構成を明示的に不合格にする
- 🟡 *要件定義書 4.4 エラーケース「マイグレーション未登録エラー」に基づく妥当な推測（例外メッセージ文言は Room 実装依存のため型のみ検証）*

### TC-E02: 未知の valueSource 文字列での toDomain 変換例外（ユニット）

- **テスト名**: 不正な valueSource 文字列での IllegalArgumentException
  - **エラーケースの概要**: `TemplateFieldEntity.valueSource` に enum に存在しない文字列（例 `"UNKNOWN"`）が入っていた場合、`FieldValueSource.valueOf()` が例外を投げる
  - **エラー処理の重要性**: LLM 追加により valueSource の取りうる値が増えたため、未知値の扱い（＝例外伝播）が従来通りであることを回帰確認する
- **入力値**: `TemplateFieldEntity(..., valueSource="UNKNOWN", ...)` を含む `TemplateWithFields`
  - **不正な理由**: `FieldValueSource` に `UNKNOWN` は定義されておらず `valueOf` が解決できない
  - **実際の発生シナリオ**: DB破損や将来の未知enum値をロールバックしたケース（想定外データ）
- **期待される結果**: `getAllTemplates().first()` 収集時に `IllegalArgumentException` がスローされる
  - **エラーメッセージの内容**: `No enum constant ...FieldValueSource.UNKNOWN` 相当
  - **システムの安全性**: 不正データを黙って握りつぶさず例外で顕在化させる（既存挙動維持）
- **テストの目的**: LLM カラム追加が既存 enum マッピングの例外挙動を変えないことの確認
  - **品質保証の観点**: 本タスクの変更（llmPrompt 追加）が valueSource の解決ロジックに副作用を与えないことを保証
- 🟡 *既存 toDomain 実装（`FieldValueSource.valueOf(valueSource)`）の挙動に基づく妥当な推測。要件定義書に明記はないが既存契約の回帰確認*

---

## 3. 境界値テストケース（デフォルト値・空・特殊文字）

### TC-B01: bodyLlmPrompt/llmPrompt 未設定（デフォルト ""）の往復保持（ユニット）

- **テスト名**: デフォルト空文字の往復変換保持
  - **境界値の意味**: `""`（空文字）は「未設定」を表す最小境界値であり、後続UIの「メモを更改」ボタン非活性判定（REQ-102）に使われる
  - **境界値での動作保証**: 空文字が非nullのまま保持され、`null` 化・欠落しないこと
- **入力値**: `bodyLlmPrompt`/`llmPrompt` を指定せず生成した `Template`/`TemplateField`（デフォルト `""`）→ `toEntity()`→`toDomain()` 往復（または Entity 側 `bodyLlmPrompt=""`, `llmPrompt=""` で toDomain）
  - **境界値選択の根拠**: NFR-001/後方互換の最重要ケース。既存呼び出し元は両カラムを指定しない
  - **実際の使用場面**: LLM プロンプトを未設定のまま保存された通常テンプレート
- **期待される結果**: 往復後も `bodyLlmPrompt == ""` かつ `llmPrompt == ""`（`isEmpty()` が true、null ではない）
  - **境界での正確性**: 空文字が空文字のまま復元される
  - **一貫した動作**: 空文字と非空文字で分岐せず同一経路でマッピングされる
- **テストの目的**: 後方互換性（デフォルト値保持）の確認
  - **堅牢性の確認**: 既存データ・既存呼び出し元が壊れないこと
- 🔵 *要件定義書 TC3・エッジ(デフォルト値)・TASK-0057 単体テスト要件3に基づく*

### TC-B02: フィールドが空（0件）のテンプレートのマッピングとマイグレーション（ユニット＋統合）

- **テスト名**: 空フィールドテンプレートの正常動作
  - **境界値の意味**: `fields` が空リスト＝フィールド0件は、`template_fields` へのマッピング/ADD COLUMN が0行に対して行われる境界
  - **境界値での動作保証**: フィールドが無くてもテンプレート本文の `bodyLlmPrompt` マッピング・マイグレーションが成功すること
- **入力値**: （ユニット）`Template(..., bodyLlmPrompt="要約", fields=emptyList())`／（統合）`template_fields` に1行も投入しない状態で `MIGRATION_2_3` 適用
  - **境界値選択の根拠**: `template_fields` への ADD COLUMN は0行でも成功する必要がある（要件定義書エッジ）
  - **実際の使用場面**: カスタムフィールドを1つも持たないシンプルなテンプレート
- **期待される結果**: （ユニット）往復後 `bodyLlmPrompt="要約"`, `fields` が空のまま／（統合）マイグレーションが例外なく完了し `template_fields.llmPrompt` 列が追加される
  - **境界での正確性**: 0件でもクラッシュせず列追加が完了
  - **一貫した動作**: フィールド有無でマイグレーション成否が変わらない
- **テストの目的**: 空コレクション境界での堅牢性確認
  - **堅牢性の確認**: `fields.map { ... }` が空でも安全であること
- 🟡 *要件定義書 4.3 エッジ(空フィールドテンプレート)に基づく妥当な推測*

### TC-B03: 長文・改行・マルチバイトを含む LLM プロンプト文字列の保持（ユニット／境界）

- **テスト名**: 特殊文字含むプロンプト文字列の往復保持
  - **境界値の意味**: プロンプトは日本語・改行・記号を含む自由入力テキストであり、`TEXT` 列の実用上限に近い文字列が損失なく往復するかの境界
  - **境界値での動作保証**: マルチバイト/改行/長文でも文字化け・切り詰めが起きないこと
- **入力値**: `bodyLlmPrompt="本文を3行で要約し、\n箇条書きにしてください。😀"`（改行・全角・絵文字混在）を持つ Template で往復変換
  - **境界値選択の根拠**: LLMプロンプトは長文の自然言語であり、改行と非ASCIIを含むのが常態
  - **実際の使用場面**: 実ユーザーが複数行の指示プロンプトを入力する場合
- **期待される結果**: 往復後も文字列がバイト等価で保持される（改行・絵文字含め一致）
  - **境界での正確性**: `String` の等価性が成立
  - **一貫した動作**: ASCII短文と同一経路で扱われる
- **テストの目的**: 文字列マッピングの無加工性（トリム・エスケープ等の副作用がない）確認
  - **堅牢性の確認**: マッピングが値を一切変換しない単純代入であること
- 🟡 *TEXT列＋String単純代入マッピングからの妥当な推測。要件定義書に明記はないが実用上重要な境界*

### TC-B04: v1→v3 連続マイグレーション（MIGRATION_1_2 + MIGRATION_2_3）でのデータ保持（統合）

- **テスト名**: v1 からの連続マイグレーション経路の検証
  - **境界値の意味**: v1（旧スキーマ vault/folder あり）から v3 まで2段階連続で移行する経路は、既存 MIGRATION_1_2 と新規 MIGRATION_2_3 の連携境界
  - **境界値での動作保証**: 古いバージョンからのユーザーも v3 まで安全に到達し、`body`（v1→v2追加）と `bodyLlmPrompt`/`llmPrompt`（v2→v3追加）が正しく初期化される
- **入力値**: v1 スキーマで `INSERT INTO templates (id, name, vault, folder, isDefault) VALUES (1, 'テンプレA', 'my_vault', 'notes', 1)` を投入 → `runMigrationsAndValidate(testDb, 3, true, MIGRATION_1_2, MIGRATION_2_3)`
  - **境界値選択の根拠**: 単一ステップ（TC-N04）だけでなく複数ステップ連鎖の回帰も担保する。既存 MIGRATION_1_2 が変更なく協調動作することの確認
  - **実際の使用場面**: v1 時代からアプリを使い続けバージョンを飛ばして更新したユーザー
- **期待される結果**: 移行後 `name="テンプレA"`, `isDefault=1`, `body=""`, `bodyLlmPrompt=""` が cursor で確認でき、例外が発生しない
  - **境界での正確性**: 2段階移行後も既存データ保持＋全新規カラム空文字初期化
  - **一貫した動作**: 単一ステップと同じ最終スキーマ・データに到達
- **テストの目的**: マイグレーション連鎖の整合性と MIGRATION_1_2 非破壊の確認
  - **堅牢性の確認**: 完了条件「既存 MIGRATION_1_2 が変更なく通り続ける」の連鎖経路での担保
- 🟡 *既存 migrate1To2 パターン＋連続移行の Room 標準挙動からの妥当な推測（要件定義書は単一ステップを明記、連鎖は堅牢性補強）*

---

## 4. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: 既存コードベースが Kotlin。ドメイン/エンティティは data class で往復変換テストが簡潔に書ける
  - **テストに適した機能**: data class の構造的等価性（`==`）、default 引数によるデフォルト値検証、`copy()` によるテストデータ生成
- **テストフレームワーク**:
  - ユニット: **JUnit 4 + MockK + kotlinx-coroutines-test**（`app/src/test/...`、`dao` を MockK でスタブし Flow を `flowOf` で供給）
  - 統合: **JUnit 4 + Room MigrationTestHelper + AndroidJUnit4**（`app/src/androidTest/...`、`runMigrationsAndValidate` でスキーマ検証）
  - **フレームワーク選択の理由**: 既存 `TemplateRepositoryImplTest`（MockK）・`AppDatabaseMigrationTest`（MigrationTestHelper）と同一構成で回帰と一貫性を担保
  - **テスト実行環境**: ユニットは JVM ローカル（`mise exec -- ./gradlew test`）、統合はデバイス/エミュレータ（`mise exec -- ./gradlew connectedAndroidTest`）
- 🔵 *note.md「5. テスト関連情報」・既存テストファイル・`app/build.gradle.kts` 依存に基づく*

---

## 5. テストケース実装時の日本語コメント指針

### ユニットテスト（TemplateRepositoryImplTest.kt）例

```kotlin
@Test
fun getAllTemplates_mapsBodyLlmPrompt() = runBlocking {
    // 【テスト目的】: toDomain() が bodyLlmPrompt をマッピングすることを確認
    // 【テスト内容】: bodyLlmPrompt を持つ TemplateEntity → Template 変換
    // 【期待される動作】: bodyLlmPrompt 値が損失なく復元される
    // 🔵 要件定義書 TC1 に基づく

    // 【テストデータ準備】: REQ-101 の本文用プロンプトを代表する実データを用意
    // 【初期条件設定】: dao をモックし v3 相当の Entity を返させる
    val entity = TemplateEntity(id = 1L, name = "テスト", body = "本文", bodyLlmPrompt = "本文を要約", isDefault = false)
    val withFields = TemplateWithFields(template = entity, fields = emptyList())
    every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

    // 【実際の処理実行】: getAllTemplates() の Flow を収集し toDomain() を発火
    val result = repository.getAllTemplates().first()

    // 【結果検証】: bodyLlmPrompt が保持されていることを確認
    // 【検証項目】: 新規カラムのマッピング追加
    assertEquals("本文を要約", result[0].bodyLlmPrompt) // 【確認内容】: bodyLlmPrompt が復元される
}
```

### 統合テスト（AppDatabaseMigrationTest.kt）例

```kotlin
@Test
fun migrate2To3_preservesDataAndAddsLlmColumns() {
    // 【テスト目的】: MIGRATION_2_3 が既存データを保持しつつ LLM カラムを '' で追加することを確認
    // 【テスト内容】: v2 スキーマ + 実データ → MIGRATION_2_3 適用 → cursor 検証
    // 🔵 要件定義書 TC4 に基づく

    // 【テストデータ準備】: v2 の templates/template_fields に既存レコードを投入
    helper.createDatabase(testDb, 2).apply {
        execSQL("INSERT INTO templates (id, name, body, isDefault) VALUES (1, 'テンプレA', '## 記事', 1)")
        execSQL(
            "INSERT INTO template_fields (id, templateId, key, valueSource, valueType, defaultValue, metaKey, sortOrder) " +
                "VALUES (10, 1, 'title', 'HTML_META', 'STRING', '', 'OG_TITLE', 0)"
        )
        close()
    }

    // 【実際の処理実行】: MIGRATION_2_3 を適用しスキーマ検証
    val db = helper.runMigrationsAndValidate(testDb, 3, true, AppDatabase.MIGRATION_2_3)

    // 【結果検証】: 既存カラム保持 + 新規カラム '' を確認
    db.query("SELECT name, body, isDefault, bodyLlmPrompt FROM templates WHERE id = 1").use { c ->
        assertTrue(c.moveToFirst())
        assertEquals("テンプレA", c.getString(c.getColumnIndexOrThrow("name"))) // 【確認内容】: 既存 name 保持
        assertEquals("", c.getString(c.getColumnIndexOrThrow("bodyLlmPrompt"))) // 【確認内容】: 新規列が空文字
    }
    db.query("SELECT llmPrompt FROM template_fields WHERE id = 10").use { c ->
        assertTrue(c.moveToFirst())
        assertEquals("", c.getString(c.getColumnIndexOrThrow("llmPrompt"))) // 【確認内容】: フィールド新規列が空文字
    }
}
```

### セットアップ・クリーンアップ

```kotlin
@Before
fun setUp() {
    // 【テスト前準備】: dao をモック化し repository を生成
    // 【環境初期化】: 各テストが独立したモック状態で開始する
    dao = mockk()
    repository = TemplateRepositoryImpl(dao)
}
```

（統合テストは `@get:Rule val helper = MigrationTestHelper(...)` により各テストで隔離されたテストDBを使用。`testDb` 名を共有するため MigrationTestHelper が close/検証を管理する。）

---

## 6. 要件定義との対応関係

- **参照した機能概要**: 要件定義書「1. 機能の概要」（Room v2→v3 スキーマ拡張・LLMプロンプト永続化）
- **参照した入力・出力仕様**: 要件定義書「2. 入力・出力の仕様」（2.1 DDL、2.2 エンティティ型、2.3 マッピング表、2.4 マイグレーションSQL）
- **参照した制約条件**: 要件定義書「3. 制約条件」（後方互換・既存不変更・登録順序・SQLite制約・DAO不変更）
- **参照した使用例**: 要件定義書「4. 想定される使用例」（基本パターン・データフロー・4.3 エッジケース・4.4 エラーケース）
- **参照したテスト要件**: 要件定義書「7. テスト要件サマリー」（TC1〜TC4＋回帰）、TASK-0057「単体テスト要件」「統合テスト要件」、note.md「5. テスト関連情報」
- **参照した既存実装**: `TemplateRepositoryImplTest.kt`（MockK+Flow パターン）、`AppDatabaseMigrationTest.kt`（MigrationTestHelper パターン）、`TemplateRepositoryImpl.kt`（マッピング関数）

---

## 7. テストケース一覧サマリー

| 分類 | ID | テスト名 | 種別 | 信頼性 |
|------|----|---------|------|--------|
| 正常系 | TC-N01 | bodyLlmPrompt 往復保持 | ユニット | 🔵 |
| 正常系 | TC-N02 | llmPrompt+LLM値ソース往復保持 | ユニット | 🔵 |
| 正常系 | TC-N03 | toEntity で両カラム書き込み | ユニット | 🔵 |
| 正常系 | TC-N04 | MIGRATION_2_3 データ保持＋カラム追加 | 統合 | 🔵 |
| 異常系 | TC-E01 | マイグレーション未登録で例外 | 統合 | 🟡 |
| 異常系 | TC-E02 | 未知 valueSource で例外 | ユニット | 🟡 |
| 境界値 | TC-B01 | デフォルト "" 往復保持 | ユニット | 🔵 |
| 境界値 | TC-B02 | 空フィールドテンプレート | ユニット＋統合 | 🟡 |
| 境界値 | TC-B03 | 長文・改行・マルチバイト保持 | ユニット | 🟡 |
| 境界値 | TC-B04 | v1→v3 連続マイグレーション | 統合 | 🟡 |
| 回帰 | - | migrate1To2 既存テスト維持 | 統合 | 🔵 |

### 信頼性レベル分布

- 🔵 青信号: 5件（TC-N01〜N04, TC-B01）＝ 要件定義書・設計文書に直接明記
- 🟡 黄信号: 5件（TC-E01, TC-E02, TC-B02, TC-B03, TC-B04）＝ 既存実装挙動・Room標準挙動からの妥当な推測（堅牢性補強）
- 🔴 赤信号: 0件

---

## 8. 品質判定

```
✅ 高品質:
- テストケース分類: 正常系4・異常系2・境界値4・回帰1 で網羅
- 期待値定義: 各ケースに具体的な入力値・期待値（cursor値/enum値/文字列等価）を明示
- 技術選択: Kotlin + JUnit4/MockK（ユニット）・MigrationTestHelper（統合）に確定
- 実装可能性: 既存 TemplateRepositoryImplTest / AppDatabaseMigrationTest パターンを踏襲し確実に実装可能
- 信頼性レベル: 🔵 が中核（要件明記分）、🟡 は既存挙動/標準挙動からの堅牢性補強で妥当
```

**総合品質評価**: ✅ 高品質

---

## 次のお勧めステップ

`/tsumiki:tdd-red llm-memo-rewrite TASK-0057` でRedフェーズ（失敗テスト作成）を開始します。

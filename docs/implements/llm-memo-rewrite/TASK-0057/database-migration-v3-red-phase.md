# TASK-0057 TDD Redフェーズ記録: database-migration-v3

**機能名**: database-migration-v3
**タスクID**: TASK-0057
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. 対象テストケース

対象テストケース名の指定なし → `database-migration-v3-testcases.md` の全10テストケース（正常系4・異常系2・境界値4）を実装対象とした。実装数は11件（TC-B02をユニット部分・統合部分の2メソッドに分割したため）。

| 分類 | ID | テスト名 | 種別 | 配置ファイル | 信頼性 |
|------|----|---------|------|------|--------|
| 正常系 | TC-N01 | `getAllTemplates_mapsBodyLlmPrompt` | ユニット | `TemplateRepositoryImplTest.kt` | 🔵 |
| 正常系 | TC-N02 | `getAllTemplates_mapsLlmPromptAndLlmValueSource` | ユニット | `TemplateRepositoryImplTest.kt` | 🔵 |
| 正常系 | TC-N03 | `saveTemplate_writesBodyLlmPromptAndFieldLlmPromptToEntity` | ユニット | `TemplateRepositoryImplTest.kt` | 🔵 |
| 正常系 | TC-N04 | `migrate2To3_preservesDataAndAddsLlmColumns` | 統合 | `AppDatabaseMigrationTest.kt` | 🔵 |
| 異常系 | TC-E01 | `migrate2To3_withoutMigrationRegistered_throwsIllegalStateException` | 統合 | `AppDatabaseMigrationTest.kt` | 🟡 |
| 異常系 | TC-E02 | `getAllTemplates_unknownValueSource_throwsIllegalArgumentException` | ユニット | `TemplateRepositoryImplTest.kt` | 🟡 |
| 境界値 | TC-B01 | `getAllTemplates_defaultBodyLlmPromptAndLlmPrompt_remainsEmpty` | ユニット | `TemplateRepositoryImplTest.kt` | 🔵 |
| 境界値 | TC-B02(unit) | `getAllTemplates_emptyFieldsList_mapsBodyLlmPromptCorrectly` | ユニット | `TemplateRepositoryImplTest.kt` | 🟡 |
| 境界値 | TC-B02(integration) | `migrate2To3_withNoTemplateFieldsRows_addsLlmPromptColumnSuccessfully` | 統合 | `AppDatabaseMigrationTest.kt` | 🟡 |
| 境界値 | TC-B03 | `getAllTemplates_multilineAndUnicodeBodyLlmPrompt_preservedExactly` | ユニット | `TemplateRepositoryImplTest.kt` | 🟡 |
| 境界値 | TC-B04 | `migrateFrom1To3_preservesDataAndInitializesAllNewColumns` | 統合 | `AppDatabaseMigrationTest.kt` | 🟡 |

信頼性レベル分布: 🔵 5件 / 🟡 6件 / 🔴 0件（`database-migration-v3-testcases.md` の分布を踏襲）

---

## 2. テストコード

### 2.1 ユニットテスト（`app/src/test/java/com/den4dr/share2Obsidian/data/repository/TemplateRepositoryImplTest.kt`）

既存の `TemplateRepositoryImplTest` クラスに、上記表のユニットテスト7件を追加した（TC-N01, TC-N02, TC-N03, TC-E02, TC-B01, TC-B02(unit), TC-B03）。

主な追加内容:
- import 追加: `com.den4dr.share2Obsidian.domain.model.TemplateField`, `io.mockk.slot`
- `TemplateEntity(bodyLlmPrompt = ...)` / `TemplateFieldEntity(llmPrompt = ...)` を用いた往復変換検証
- `slot<TemplateEntity>()` / `slot<List<TemplateFieldEntity>>()` によるスロット捕捉で `toEntity()` 側の書き込み検証（TC-N03）
- 既存回帰確認として、未知 `valueSource` 文字列での `IllegalArgumentException` 検証（TC-E02）

### 2.2 統合テスト（`app/src/androidTest/java/com/den4dr/share2Obsidian/data/db/AppDatabaseMigrationTest.kt`）

既存の `AppDatabaseMigrationTest` クラスに、`AppDatabase.MIGRATION_2_3` を参照する統合テスト4件を追加した（TC-N04, TC-E01, TC-B02(integration), TC-B04）。

主な追加内容:
- v2 スキーマへのダミーレコード投入 → `MIGRATION_2_3` 適用 → cursor によるカラム保持/新規カラム初期化の確認（TC-N04）
- マイグレーション未指定時の `IllegalStateException` 検証（TC-E01、登録漏れの回帰防止）
- `template_fields` 0件時の `PRAGMA table_info` によるカラム追加確認（TC-B02(integration)）
- v1→v3 の `MIGRATION_1_2` + `MIGRATION_2_3` 連続適用検証（TC-B04）

---

## 3. テスト実行結果と期待される失敗

### 3.1 ユニットテスト実行結果

```
mise exec -- ./gradlew :app:testDebugUnitTest --tests "*TemplateRepositoryImplTest*"

> Task :app:compileDebugUnitTestKotlin FAILED
e: .../TemplateRepositoryImplTest.kt:195:13 No parameter with name 'bodyLlmPrompt' found.
e: .../TemplateRepositoryImplTest.kt:231:13 No parameter with name 'llmPrompt' found.
e: .../TemplateRepositoryImplTest.kt:283:58 Unresolved reference 'bodyLlmPrompt'.
e: .../TemplateRepositoryImplTest.kt:284:60 Unresolved reference 'llmPrompt'.
e: .../TemplateRepositoryImplTest.kt:306:13 No parameter with name 'llmPrompt' found.
e: .../TemplateRepositoryImplTest.kt:362:70 No parameter with name 'bodyLlmPrompt' found.
e: .../TemplateRepositoryImplTest.kt:386:70 No parameter with name 'bodyLlmPrompt' found.

BUILD FAILED
```

**失敗理由**: `TemplateEntity`/`TemplateFieldEntity` に `bodyLlmPrompt`/`llmPrompt` プロパティが存在しないためコンパイルエラー。Kotlin の静的型付けにより「未実装の機能を呼び出すテスト」はコンパイル時に失敗する形で Red 状態が確認された（意図した失敗モード）。

### 3.2 統合テスト コンパイル確認結果

計器テスト（`connectedAndroidTest`）はデバイス/エミュレータが必要だが、本実行環境には adb / エミュレータが存在しないため実機実行はできない。代わりにコンパイルタスクで Red 状態を確認した。

```
mise exec -- ./gradlew :app:compileDebugAndroidTestKotlin

> Task :app:compileDebugAndroidTestKotlin FAILED
e: .../AppDatabaseMigrationTest.kt:80:79 Unresolved reference 'MIGRATION_2_3'.
e: .../AppDatabaseMigrationTest.kt:136:79 Unresolved reference 'MIGRATION_2_3'.
e: .../AppDatabaseMigrationTest.kt:171:69 Unresolved reference 'MIGRATION_2_3'.

BUILD FAILED
```

**失敗理由**: `AppDatabase.MIGRATION_2_3` が未実装のためコンパイルエラー。Greenフェーズで `AppDatabase.kt` に `MIGRATION_2_3` を実装した後、`mise exec -- ./gradlew connectedAndroidTest`（デバイス/エミュレータ必須）で実行時の成功を確認する必要がある。

---

## 4. Greenフェーズで実装すべき内容

1. **`TemplateEntity.kt`**: `bodyLlmPrompt: String = ""` を `body` の直後・`isDefault` の前に追加
2. **`TemplateFieldEntity.kt`**: `llmPrompt: String = ""` を `metaKey` の直後・`sortOrder` の前に追加
3. **`AppDatabase.kt`**: `version = 2` → `version = 3` に変更し、`MIGRATION_2_3`（`ALTER TABLE templates ADD COLUMN bodyLlmPrompt TEXT NOT NULL DEFAULT ''` / `ALTER TABLE template_fields ADD COLUMN llmPrompt TEXT NOT NULL DEFAULT ''`）を追加（`MIGRATION_1_2` は変更しない）
4. **`DatabaseModule.kt`**: `.addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)` へ更新
5. **`TemplateRepositoryImpl.kt`**: `toDomain()`（`TemplateWithFields`/`TemplateFieldEntity`）と `toEntity()`（`Template`/`TemplateField`）の双方に `bodyLlmPrompt`/`llmPrompt` マッピングを追加

実装後の確認コマンド:
```bash
mise exec -- ./gradlew test --tests "*TemplateRepositoryImplTest*"
mise exec -- ./gradlew connectedAndroidTest   # デバイス/エミュレータ必須
```

---

## 5. 品質判定

```
✅ 高品質:
- テスト実行: ユニット/統合ともにコンパイルエラーで失敗することを確認済み（意図した Red 状態）
- 期待値: 各テストでカラム値・enum値・例外型を具体的に明示
- アサーション: assertEquals/assertTrue/@Test(expected=...) で明確
- 実装方針: note.md・要件定義書・設計文書（database-schema.kt）に実装コード例が明記済み
- 信頼性レベル: 🔵 5件・🟡 6件・🔴 0件（testcases.md の分布を踏襲、エッジケース中心の🟡は妥当な推測）
```

**総合品質評価**: ✅ 高品質

---

## 次のお勧めステップ

`/tsumiki:tdd-green` でGreenフェーズ（最小実装）を開始します。

# TDD Refactorフェーズ記録: CustomFieldState拡張・TemplateApplicator.buildCustomFields()のLLM対応

- **機能名**: custom-field-llm-support
- **タスクID**: TASK-0070
- **要件名**: llm-memo-rewrite
- **フェーズ**: Refactor（品質改善）
- **作成日**: 2026-07-09

---

## 1. リファクタリング方針

Greenフェーズの実装記録（`custom-field-llm-support-green-phase.md` 5節）で「現時点で本質的なリファクタリング対象はない」と申し送られていたとおり、実装は要件どおりの最小差分で完結しており、機能面での改善対象は確認できなかった。

そのため本フェーズでは以下を実施した:

1. **既存テストの再実行による安全性確認**（関連5テストクラス・計76件、全て成功）
2. **セキュリティレビュー**（脆弱性なしを確認）
3. **パフォーマンスレビュー**（計算量・設計意図の妥当性を確認）
4. **コード品質の軽微な改善**: KDocコメントのタグ表記統一（`@returns` → `@return`）

大規模な構造変更・重複除去は行っていない。理由: 対象コード（`CustomFieldState.kt` 20行、`TemplateApplicator.kt` 65行）はいずれも単一責任・低複雑度であり、無理に分割・抽象化すると「機能的な変更は行わない」というリファクタリング原則に反するリスク（過剰設計）の方が大きいと判断したため。

---

## 2. テスト実行結果（リファクタ前の安全性確認）

### 実行コマンド

```bash
mise exec -- ./gradlew :app:testDebugUnitTest \
  --tests "com.den4dr.share2Obsidian.TemplateApplicatorTest" \
  --tests "com.den4dr.share2Obsidian.domain.model.TemplateTest" \
  --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest" \
  --tests "com.den4dr.share2Obsidian.format.NoteComposerTest" \
  --tests "com.den4dr.share2Obsidian.integration.EdgeCaseIntegrationTest" \
  --rerun
```

### 結果: BUILD SUCCESSFUL（Taskツールによるサブエージェント実行で確認）

| クラス | テスト数 | 失敗/エラー/スキップ | スイート実行時間 |
|---|---|---|---|
| `TemplateApplicatorTest` | 18 | 0/0/0 | 0.028s |
| `domain.model.TemplateTest` | 14 | 0/0/0 | 0.003s |
| `format.NoteComposerTest` | 20 | 0/0/0 | 2.049s（スイート合計。個別テストは最大でも数百ms未満） |
| `integration.EdgeCaseIntegrationTest` | 5 | 0/0/0 | 0.002s |
| `ui.EditScreenViewModelTest` | 19 | 0/0/0 | 1.24s |

**合計**: 76テスト、全て成功。

- **遅いテスト（2秒以上）の個別テスト**: なし。`TemplateApplicatorTest` 内で最も遅い個別テストは `buildBody_noPlaceholderNonEmpty_returnsTemplateBodyOnly` の 0.009s。`NoteComposerTest` のスイート合計が2.049sだが、これは20件の集計値であり単一テストではない。
- **スキップ・無効化テスト**: `@Ignore` / `.skip(` / `Disabled` の該当なし（`TemplateApplicatorTest.kt` をgrep確認、全XMLレポートで `skipped="0"`）。
- **.gitignoreによる対象外除外**: `git check-ignore` で `CustomFieldState.kt` / `TemplateApplicator.kt` / `TemplateApplicatorTest.kt` を確認し、いずれも除外設定なし。
- **開発時生成ファイル**: `debug-*` / `test-*` / `*.tmp` / `*.bak` 等のパターンで検索したが、該当ファイルなし（クリーンアップ不要）。

### コメント改善後の再検証

`TemplateApplicator.kt` のKDocコメント修正後、`TemplateApplicatorTest` を再実行し `BUILD SUCCESSFUL`（18テスト全て成功、コンパイルエラーなし）を確認した。

---

## 3. セキュリティレビュー結果

- **対象コード**: `CustomFieldState.kt`（純粋なdata class）、`TemplateApplicator.buildCustomFields()`（副作用のない純粋関数）
- **脆弱性検査**: SQLインジェクション・XSS・CSRF・認証認可の対象となる処理（DBクエリ、HTMLレンダリング、外部通信、認証情報の取り扱い）は本コードに一切存在しない。文字列を`when`式で選択的にコピーするのみ。
- **入力値検証**: `processed.metadata[field.metaKey] ?: ""` と `processed.sourceUrl ?: ""` によりnull混入を防止（既存実装を維持、TC-0070-E01/E02で回帰確認済み）。
- **データ漏洩リスク**: `metadata`（共有元ページのHTMLメタ情報。攻撃者が細工したページの可能性を含む）の値はそのまま文字列として`CustomFieldState.value`にコピーされるのみで、評価・実行・レンダリングは本関数のスコープ外（EditScreen描画時にComposeのテキスト表示として安全に扱われる想定、レンダリング層は本タスク対象外）。
- **結論**: 🔵 重大な脆弱性なし。純粋なデータ変換ロジックのため、本タスクのスコープでは追加のセキュリティ対策は不要と判断。

---

## 4. パフォーマンスレビュー結果

- **計算量**: `template.fields.map { ... }` によりフィールド数 `n` に対して時間計算量 O(n)。`metadata[field.metaKey]` はHashMapの平均O(1)ルックアップ。空間計算量も O(n)（新規リスト生成のみ）。カスタムフィールド数は実運用上少数（テンプレート定義に依存、通常一桁〜十数件程度）であり、性能上のボトルネックにはならない。
- **設計意図との整合**: LLM呼び出し（ネットワークI/O・レイテンシの主要因）をテンプレート適用時点では行わず、EditScreen上のボタン押下時に遅延させる設計（REQ-304）により、共有フロー全体の応答性を確保している。本関数自体もその設計を維持（`FieldValueSource.LLM -> ""` で即時復帰）。
- **メモリ使用量**: 追加した2フィールド（`valueSource: FieldValueSource`（enum参照）, `llmPrompt: String`）による増分は無視できるレベル。
- **結論**: 🔵 重大な性能課題なし。追加の最適化は不要。

---

## 5. 実施したリファクタリング内容

### 5-1. KDocコメントのタグ表記統一 🔵

**対象**: `app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt`

**変更内容**: `buildCustomFields()` のKDocで使用していた `@returns` を、プロジェクト内の他ファイル（`LlmRewriteRepository.kt`, `EditFormState.kt`, `EditScreenViewModel.kt`, `SettingsScreen.kt`）で使われている標準的なKDocタグ `@return` に統一した。

```diff
  * @param template カスタムフィールド定義を持つテンプレート（null可）
  * @param processed 共有コンテンツの処理結果
- * @returns 各 TemplateField を変換した編集状態のリスト（template が null の場合は空リスト）
+ * @return 各 TemplateField を変換した編集状態のリスト（template が null の場合は空リスト）
  */
```

- **改善理由**: コードベース内でのKDocタグ表記の一貫性向上（可読性・保守性）。機能・挙動への影響なし。
- 🔵 信頼性レベル: プロジェクト内の既存KDocコメント（4箇所で`@return`使用を確認）に基づく、推測を伴わない表記統一

### 5-2. 検討したが見送った改善項目

- **テストコード内のTemplate/ProcessedContent構築のヘルパー関数化**: Greenフェーズ記録で申し送られていた候補。テストケースごとに入力データの意図（境界値・異常値・正常値）を明示するため、各テストで独立してインラインに構築する現状の書き方の方がテストの意図が読み取りやすいと判断し、本フェーズでは見送った（テストの可読性を優先、DRY原則より意図の明確性を優先）。
- **`buildCustomFields()` 内の `when` 式の関数分割**: 5分岐・20行未満の単純な式であり、分割によってかえって呼び出し関係が追いにくくなるため見送った。

---

## 6. リファクタリング後のコード全文

### 6-1. `app/src/main/java/com/den4dr/share2Obsidian/domain/model/CustomFieldState.kt`（変更なし）

```kotlin
package com.den4dr.share2Obsidian.domain.model

/**
 * 【機能概要】: EditScreen上で各カスタムフィールドの編集状態を表すドメインモデル
 * 【実装方針】: valueSource/llmPrompt にデフォルト値を付与し、既存の3引数コンストラクタ呼び出しとの
 * 後方互換性を維持したまま、LLM生成ボタンの表示判定・生成実行に必要なメタ情報を保持できるようにする
 * 【テスト対応】: TC-0070-N03（3引数コンストラクタ互換）、buildCustomFields 系の各テストケースで参照される
 * 🟡 信頼性レベル: interfaces.kt「CustomFieldState（変更後）」・architecture.mdからの妥当な推測
 */
data class CustomFieldState(
    val key: String,
    val value: String,
    val valueType: FieldValueType,
    // 【フィールド値取得元】: EditScreen側でLLM生成ボタンの表示判定（valueSource == LLM）に使用する
    // 🟡 信頼性レベル: interfaces.kt「CustomFieldState（変更後）」に基づく
    val valueSource: FieldValueSource = FieldValueSource.FIXED,
    // 【LLM呼び出し用プロンプト】: valueSource == LLM の場合にLLM呼び出しへ渡すプロンプト。未設定時は空文字
    // 🟡 信頼性レベル: interfaces.kt「CustomFieldState（変更後）」に基づく
    val llmPrompt: String = "",
)
```

### 6-2. `app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt`（`buildCustomFields()` のみ抜粋・KDocタグ修正済み）

```kotlin
    /**
     * 【機能概要】: テンプレートのカスタムフィールド定義（TemplateField）を、EditScreenの編集状態
     * （CustomFieldState）へ変換する
     * 【実装方針】: FIXED/HTML_META/URL/EMPTY の既存 value 算出ロジックは変更せず、CustomFieldState
     * 生成時に valueSource/llmPrompt を新たに渡すことで、LLM生成ボタンの表示判定に必要な情報を橋渡しする
     * 【テスト対応】: TC-0070-N01〜N03, E01〜E02, B01〜B03（テンプレート適用時のLLM対応・回帰確認）
     * 🔵 信頼性レベル: 要件定義書 2-2 値算出テーブル・REQ-304 に基づく（推測なし）
     * @param template カスタムフィールド定義を持つテンプレート（null可）
     * @param processed 共有コンテンツの処理結果
     * @return 各 TemplateField を変換した編集状態のリスト（template が null の場合は空リスト）
     */
    fun buildCustomFields(
        template: Template?,
        processed: ProcessedContent,
    ): List<CustomFieldState> = template?.fields?.map { field ->
        // 【値算出】: valueSource ごとに value を算出する（既存ロジックを維持） 🔵
        val value = when (field.valueSource) {
            FieldValueSource.FIXED -> field.defaultValue
            FieldValueSource.HTML_META -> processed.metadata[field.metaKey] ?: ""
            FieldValueSource.URL -> processed.sourceUrl ?: ""
            FieldValueSource.EMPTY -> ""
            // 【LLM生成は本関数では行わない】: テンプレート適用時点ではLLM呼び出しを行わず、値は空文字のまま
            // EditScreen上のボタン押下時（TASK-0072/0073）に生成する（REQ-304の設計判断）
            // 🔵 信頼性レベル: 要件定義書 制約条件・design-interview.md Q2 に基づく（推測なし）
            FieldValueSource.LLM -> ""
        }
        // 【CustomFieldState生成】: valueSource/llmPrompt を渡すことで、EditScreen側でLLM生成ボタンの
        // 表示・活性判定が可能になる 🔵
        CustomFieldState(field.key, value, field.valueType, field.valueSource, field.llmPrompt)
    } ?: emptyList()
```

`buildConfig()` / `buildBody()` は本タスクの対象外のため変更なし。

---

## 7. 品質判定

- ✅ **テスト結果**: リファクタ前後とも全76テスト成功（0失敗・0エラー・0スキップ）
- ✅ **セキュリティ**: 重大な脆弱性なし（純粋なデータ変換ロジック、DB/HTML/認証を扱わない）
- ✅ **パフォーマンス**: 重大な性能課題なし（O(n)、LLM呼び出しは意図的に遅延）
- ✅ **リファクタ品質**: KDocタグ表記をプロジェクト規約に統一。機能的な変更なし
- ✅ **コード品質**: 適切なレベル（可読性・日本語コメントとも既にGreenフェーズで高水準）
- ✅ **ファイルサイズ**: `CustomFieldState.kt` 20行、`TemplateApplicator.kt` 65行、`TemplateApplicatorTest.kt` 356行。500行制限に対し十分小さい
- ✅ **日本語コメント品質**: 【機能概要】【実装方針】【テスト対応】等のテンプレートに沿った記述済み、信頼性レベル（🔵🟡）付与済み
- ✅ **モック使用**: 実装コードにモック・スタブなし

**総合判定**: ✅ 高品質。次フェーズ（`/tsumiki:tdd-verify-complete`）へ進行可能。

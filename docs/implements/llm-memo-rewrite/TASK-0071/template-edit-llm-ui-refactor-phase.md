# TASK-0071 TDD Refactorフェーズ記録: template-edit-llm-ui

**機能名**: llm-memo-rewrite（TemplateEditViewModel/TemplateEditScreen/FieldAddDialog へのLLM UI追加）
**タスクID**: TASK-0071
**実施日**: 2026-07-09

---

## 0. 前提の補足（Greenフェーズ記録の欠落について）

- `template-edit-llm-ui-green-phase.md` は本フェーズ開始時点で未作成、`template-edit-llm-ui-memo.md` にも「Greenフェーズ: （未着手）」と記載されていた。
- 一方で実装ファイル（`TemplateEditViewModel.kt`、`TemplateEditScreen.kt`、`strings.xml`）には未コミットの変更としてLLM UI関連の実装が既に存在し、テスト（単体14件・統合11件）もすべて成功する状態だった。
- 実コードとドキュメントに齟齬があったため、既存実装をGreenフェーズ成果物とみなし、テスト全件成功を確認したうえでRefactorフェーズを実施した。
- 🟡 信頼性レベル: この経緯整理は元資料（memo.md）と実コードの突合から導いた記録であり、Greenフェーズの実施記録そのものは本ファイルでは代替しない。

---

## 1. リファクタ前の品質確認（step3）

### テスト実行結果（Taskツール経由）

| 対象 | コマンド | 結果 |
|------|---------|------|
| 単体テスト（TemplateEditViewModelTest） | `mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.template.TemplateEditViewModelTest"` | 14 tests / 0 failures |
| 統合テスト（TemplateEditScreenTest, emulator-5554） | `mise exec -- ./gradlew connectedDebugAndroidTest` (フィルタ指定) | 11 tests / 0 failures |
| プロジェクト全体単体テスト | `mise exec -- ./gradlew testDebugUnitTest` | 271 tests / 0 failures |

### 実行時間チェック

統合テスト側で2秒以上のケースが6件検出された（Compose初期描画コストによるものと推定、アルゴリズム上のボトルネックではない）。

- `saveAndReload_restoresBodyLlmPromptAndFieldLlmPrompt` 3.659s
- `newMode_showsCreateTitle` 3.652s
- `nonLlmSource_discardsLlmPrompt` 3.367s
- `addField_appearsInFieldList` 2.644s
- `nameInput_updatesViewModel` 2.484s
- `llmSource_showsLlmPromptField` 2.098s

いずれも既存の統合テスト構成（`createAndroidComposeRule` + 実機/エミュレータ描画）に起因する固定コストであり、TASK-0071の実装固有の性能課題ではないと判断した。

### テスト除外・一時ファイルチェック

- `describe.skip` / `@Ignore` 等の無効化テストなし
- `debug-*` / `temp-*` / `*.tmp` / `*.bak` 等の一時ファイルなし

---

## 2. レビュー結果（step4）

### セキュリティレビュー

- 対象コードは `TemplateEditUiState.bodyLlmPrompt` / `TemplateFieldEditState.llmPrompt` という単純な文字列状態の保持・表示のみで、外部入力を直接SQL/URI/HTMLに埋め込む処理は含まれない。
- 永続化はRoom経由（`TemplateRepositoryImpl`、TASK-0057で実装済み）でパラメータバインディングされるため、本タスクの変更によるインジェクションリスクはない。
- 重大な脆弱性は確認されなかった。

### パフォーマンスレビュー

- `FieldAddDialog` 内の値取得方法選択肢リスト（`FieldValueSource` → ラベル文字列の `Pair` リスト）が、`key` 等のテキスト入力で発生する再コンポーズのたびに `stringResource()` 呼び出し込みで再構築されていた。
- 件数は5件と小さく実害は軽微だが、テキスト入力の頻度に対して不要な再割り当てが発生するパターンであり、`remember` 化による改善余地ありと判断した（詳細は§3）。
- その他、アルゴリズム的なボトルネックや不要なループ処理は確認されなかった。

### 改善計画

| # | 改善内容 | 観点 | 信頼性 |
|---|---------|------|--------|
| 1 | `FieldAddDialog` の値取得方法選択肢リストを `remember` 化し、再コンポーズごとの再生成を回避 | パフォーマンス | 🔵 |

大きな設計変更・重複除去・ファイル分割は不要と判断した（両ファイルとも500行制限内で、既存パターンとの一貫性も確保されているため）。

---

## 3. 実施した改善（step5）

### 改善1: `FieldAddDialog` 値取得方法選択肢リストの `remember` 化

**対象ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditScreen.kt`

Before:

```kotlin
Text(stringResource(R.string.field_value_source_label))
listOf(
    FieldValueSource.FIXED to stringResource(R.string.field_source_fixed),
    FieldValueSource.HTML_META to stringResource(R.string.field_source_html_meta),
    FieldValueSource.URL to stringResource(R.string.field_source_url),
    FieldValueSource.EMPTY to stringResource(R.string.field_source_empty),
    // 【LLM生成の値取得方法選択肢】: REQ-303に基づき追加 🔵
    FieldValueSource.LLM to stringResource(R.string.field_source_llm),
).forEach { (source, label) ->
    ...
}
```

After:

```kotlin
Text(stringResource(R.string.field_value_source_label))
// 【改善内容】: valueSource選択肢リストをremember化し、key入力等によるダイアログの再コンポーズのたびに
// リストオブジェクトとラベル解決が再実行されるのを防ぐ
// 【設計方針】: stringResource()はComposable専用関数のためremember{}の外側で解決し、
// 結果のPairリストのみをremember対象にする（remember内でComposable呼び出しは不可のため）
// 【パフォーマンス】: 5件程度の小さなリストではあるが、テキスト入力のたびに走る処理から
// 不要な再割り当てを排除する意図で分離した
// 🔵 信頼性レベル: 既存ロジック・並び順は変更せず、生成タイミングのみ最適化（要件への影響なし）
val fieldSourceFixedLabel = stringResource(R.string.field_source_fixed)
val fieldSourceHtmlMetaLabel = stringResource(R.string.field_source_html_meta)
val fieldSourceUrlLabel = stringResource(R.string.field_source_url)
val fieldSourceEmptyLabel = stringResource(R.string.field_source_empty)
// 【LLM生成の値取得方法選択肢】: REQ-303に基づき追加 🔵
val fieldSourceLlmLabel = stringResource(R.string.field_source_llm)
val fieldSourceOptions = remember(
    fieldSourceFixedLabel,
    fieldSourceHtmlMetaLabel,
    fieldSourceUrlLabel,
    fieldSourceEmptyLabel,
    fieldSourceLlmLabel,
) {
    listOf(
        FieldValueSource.FIXED to fieldSourceFixedLabel,
        FieldValueSource.HTML_META to fieldSourceHtmlMetaLabel,
        FieldValueSource.URL to fieldSourceUrlLabel,
        FieldValueSource.EMPTY to fieldSourceEmptyLabel,
        FieldValueSource.LLM to fieldSourceLlmLabel,
    )
}
fieldSourceOptions.forEach { (source, label) ->
    ...
}
```

**改善理由**: `remember` のキーに全ラベル文字列を渡すことで、ロケール変更時など文字列が実際に変わった場合のみリストを再生成し、それ以外の再コンポーズ（テキスト入力等）では既存リストを再利用する。選択肢の内容・順序（FIXED → HTML_META → URL → EMPTY → LLM）は変更していない。

**リファクタ後のテスト確認**: 単体テスト14件・統合テスト（TemplateEditScreenTest）11件がいずれも成功（回帰なし）。

---

## 4. リファクタ後の全体テスト確認（emulator-5554 実機再確認含む）

| 対象 | 結果 |
|------|------|
| `TemplateEditViewModelTest`（単体） | 14 tests / 0 failures |
| `TemplateEditScreenTest`（統合、emulator-5554） | 11 tests / 0 failures |
| プロジェクト全体単体テスト（`testDebugUnitTest`） | 271 tests / 0 failures |

いずれもリファクタ前と同数・同結果であり、機能的な退行は確認されなかった。

---

## 5. 品質判定

```
✅ 高品質
- テスト結果: 単体14件・統合11件・全体271件、すべて継続成功（Taskツールで実行確認）
- セキュリティ: 重大な脆弱性なし（外部入力の直接埋め込みなし、永続化はRoomパラメータバインディング）
- パフォーマンス: 重大な性能課題なし。FieldAddDialogの選択肢リストをremember化して軽微な改善を実施
- リファクタ品質: 目標達成（可読性・パフォーマンスの小改善、機能変更なし）
- コード品質: TemplateEditViewModel.kt 160行 / TemplateEditScreen.kt 419行、いずれも500行制限内
- 日本語コメント: 既存実装時点で【機能概要】【テスト対応】【信頼性レベル】等の形式が付与済み。今回の改善箇所にも同形式で追記
- ドキュメント: 本ファイルとメモファイルを更新して完成
```

---

## 6. 次のステップ

次のお勧めステップ: `/tsumiki:tdd-verify-complete` で完全性検証を実行します。

なお、Greenフェーズの記録（`template-edit-llm-ui-green-phase.md`）が欠落している点は完全性検証時に併せて確認することを推奨する。

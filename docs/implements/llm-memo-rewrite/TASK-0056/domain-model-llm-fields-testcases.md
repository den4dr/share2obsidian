# TASK-0056 テストケース定義書: Template/TemplateField/FieldValueSource ドメインモデル変更

**機能名（feature_name）**: domain-model-llm-fields
**タスクID**: TASK-0056
**要件名**: llm-memo-rewrite
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0056/domain-model-llm-fields-testcases.md`
**作成日**: 2026-07-05
**フェーズ**: Phase 1 - 基盤構築

---

## 【信頼性レベル凡例】

各テストケースについて、元資料（要件定義・設計文書・既存実装）との照合状況を以下で示す：

- 🔵 **青信号**: 元の資料を参考にしてほぼ推測していない
- 🟡 **黄信号**: 元の資料から妥当な推測
- 🔴 **赤信号**: 元の資料にない推測

---

## 0. テスト対象の前提

本タスクは Domain 層（`domain/model`）の data class / enum への「追加のみ」の変更であり、実行時 I/O や外部依存を持たない。したがってテストは data class の自動生成 `copy()`、デフォルト値、`enum.entries` / `valueOf()` の挙動を検証する純粋な JVM 単体テストとなる。

### テスト対象ファイル

| ファイル | 変更内容 |
|---------|---------|
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt` | `bodyLlmPrompt: String = ""` 追加（`body` の直後） |
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt` | `llmPrompt: String = ""` 追加（`metaKey` の直後） |
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt` | `LLM` enum 値追加（末尾） |

### テスト配置先

`app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt`（既存ファイルにテスト追加）

### ⚠️ 既存テスト修正の必要性（重要）

🔵 既存 `TemplateTest.kt` の `FieldValueSource contains all expected values` は `assertEquals(4, names.size)` を検証している（`app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt:37-44`）。`LLM` 追加により enum 数が 5 になるため、このアサーションを **`assertEquals(5, ...)` に更新し、`assert("LLM" in names)` を追加** しなければ既存テストが失敗する。完了条件「既存の単体テストがすべて通る」を満たすため、本更新は必須（本書 TC2-B として記載）。

---

## 1. 正常系テストケース（基本的な動作）

### TC1: Template の copy() で bodyLlmPrompt のみ変更できること

- **テスト名**: Template の copy() で bodyLlmPrompt のみを部分更新できる
  - **何をテストするか**: 既存 `Template` インスタンスに対し `copy(bodyLlmPrompt = ...)` を呼び、`bodyLlmPrompt` のみが更新され他プロパティが保持されること
  - **期待される動作**: data class 自動生成の `copy()` が新規プロパティ `bodyLlmPrompt` を引数として受け付け、指定値で更新した新インスタンスを返す
- **入力値**: `Template(name = "Web記事", fields = emptyList())` を生成し、`.copy(bodyLlmPrompt = "要約してください")`
  - **入力データの意味**: `bodyLlmPrompt` 未指定（デフォルト `""`）の初期状態から、本文リライト用プロンプトを設定する典型ユースケース（requirements.md 4.1 基本的な使用パターン）
- **期待される結果**:
  - `updated.bodyLlmPrompt == "要約してください"`
  - `updated.name == "Web記事"`（不変）
  - `updated.body == ""`（不変）
  - `updated.fields == emptyList()`（不変）
  - `updated.isDefault == false`（不変）
  - `updated.id == 0L`（不変）
  - **期待結果の理由**: `copy()` は指定した引数のみ差し替え、他は元インスタンスの値を引き継ぐ Kotlin data class の標準仕様（REQ-101）
- **テストの目的**: `bodyLlmPrompt` が `copy()` で部分更新可能であることの確認
  - **確認ポイント**: 更新対象プロパティのみが変わり、他プロパティが副作用なく保持されること
- 🔵 *TASK-0056.md テストケース1・requirements.md TC1・REQ-101 に基づく（推測なし）*

### TC3: TemplateField の copy() で llmPrompt のみ変更できること

- **テスト名**: TemplateField の copy() で llmPrompt のみを部分更新できる
  - **何をテストするか**: 既存 `TemplateField` に対し `copy(llmPrompt = ...)` を呼び、`llmPrompt` のみ更新され他プロパティが保持されること
  - **期待される動作**: data class `copy()` が新規プロパティ `llmPrompt` を引数として受け付け、指定値で更新した新インスタンスを返す
- **入力値**: `TemplateField(key = "title", valueSource = FieldValueSource.LLM, valueType = FieldValueType.STRING)` を生成し、`.copy(llmPrompt = "タイトルを生成してください")`
  - **入力データの意味**: `valueSource == LLM` かつ `llmPrompt` 未設定（デフォルト `""`）の初期状態から、フィールド用プロンプトを設定する典型ユースケース（requirements.md 4.1、REQ-104）
- **期待される結果**:
  - `updated.llmPrompt == "タイトルを生成してください"`
  - `updated.key == "title"`（不変）
  - `updated.valueSource == FieldValueSource.LLM`（不変）
  - `updated.valueType == FieldValueType.STRING`（不変）
  - `updated.metaKey == null`（不変）
  - `updated.sortOrder == 0`（不変）
  - **期待結果の理由**: `copy()` は指定引数のみ差し替える標準仕様（REQ-104）
- **テストの目的**: `llmPrompt` が `copy()` で部分更新可能であることの確認
  - **確認ポイント**: `llmPrompt` 以外のフィールド（特に `valueSource`, `metaKey`, `sortOrder`）が保持されること
- 🔵 *TASK-0056.md テストケース3・requirements.md TC3・REQ-104 に基づく（推測なし）*

### TC5: 値を明示指定して Template / TemplateField を生成できること（コンストラクタ受理）

- **テスト名**: bodyLlmPrompt / llmPrompt を明示指定してインスタンスを生成できる
  - **何をテストするか**: 新規プロパティをコンストラクタ引数で明示的に渡してインスタンス生成でき、値が正しく格納されること
  - **期待される動作**: 名前付き引数で新規プロパティを渡すと、その値が保持される
- **入力値**:
  - `Template(name = "記事", bodyLlmPrompt = "本文を整形", fields = emptyList())`
  - `TemplateField(key = "tag", valueSource = FieldValueSource.LLM, valueType = FieldValueType.LIST, llmPrompt = "タグ候補")`
  - **入力データの意味**: Repository マッピングや UI から値ありで生成される後続タスク（TASK-0057/0070）の前提を先取り確認
- **期待される結果**:
  - `template.bodyLlmPrompt == "本文を整形"`
  - `field.llmPrompt == "タグ候補"`
  - **期待結果の理由**: data class は名前付き引数で任意プロパティを受理し、渡した値をそのまま保持する
- **テストの目的**: 新規プロパティがコンストラクタ経由で設定可能であることの確認
  - **確認ポイント**: 名前付き引数の順序非依存性（`fields` より前に `bodyLlmPrompt` を配置しても生成可能）
- 🟡 *data class 標準仕様からの妥当な推測。TASK-0056 コアテストケースには明示されないが、requirements.md 4.2「既存コード互換ケース」の裏返しとして値あり生成を補完*

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-ERR1: 存在しない enum 名の valueOf は例外を投げる

- **テスト名**: FieldValueSource.valueOf に未定義名を渡すと IllegalArgumentException
  - **エラーケースの概要**: enum に存在しない文字列を `valueOf()` に渡した場合の標準例外挙動を確認
  - **エラー処理の重要性**: Repository の文字列→enum マッピング（TASK-0057）で不正な永続化値を検出する基盤挙動。`LLM` 追加後も未定義値は従来どおり例外となることを担保する
- **入力値**: `FieldValueSource.valueOf("INVALID")`（あるいは `"llm"` 小文字など未定義名）
  - **不正な理由**: `FieldValueSource` に `INVALID` は定義されていない。enum 名は完全一致・大文字小文字区別
  - **実際の発生シナリオ**: DB に破損した/旧バージョンの未知文字列が保存されていた場合の読み出し時
- **期待される結果**: `IllegalArgumentException` がスローされる
  - **エラーメッセージの内容**: Kotlin 標準の "No enum constant ..." メッセージ（内容自体は検証対象外、例外型のみ検証）
  - **システムの安全性**: 未知値を黙って通さず例外化することで、マッピング層で明示的にハンドリング可能となる
- **テストの目的**: `LLM` 追加後も enum の `valueOf` 標準契約（未定義名は例外）が維持されることの確認
  - **品質保証の観点**: 後続 Repository マッピングの前提となる enum 変換の堅牢性を保証
- 🔵 *requirements.md 4.3 エラーケース・Kotlin enum 標準仕様に基づく（推測なし）*

---

## 3. 境界値テストケース（デフォルト値・enum 網羅・空文字）

### TC4: 既存デフォルト値の後方互換性（新規プロパティ未指定生成）

- **テスト名**: bodyLlmPrompt / llmPrompt 未指定生成でデフォルト空文字になる
  - **境界値の意味**: 「引数を 1 つも追加指定しない」既存コード互換の境界。新規プロパティ追加が既存呼び出しを壊さないことの最重要保証
  - **境界値での動作保証**: デフォルト値 `""` が確実に適用され、既存コンストラクタ呼び出しがコンパイル・実行ともに成立する
- **入力値**:
  - `Template(name = "Web記事", fields = emptyList())`
  - `TemplateField(key = "status", valueSource = FieldValueSource.FIXED, valueType = FieldValueType.STRING, defaultValue = "draft")`
  - **境界値選択の根拠**: 既存 `TemplateTest.kt` と同一の生成パターン（`TemplateTest.kt:11-17, 27-33`）。既存呼び出し元（`TemplateApplicator`・`TemplateRepositoryImpl`）が新規プロパティを渡さない現状を代表
  - **実際の使用場面**: 既存の全呼び出し元。マイグレーション前の既存コードそのもの
- **期待される結果**:
  - `template.bodyLlmPrompt == ""`
  - `field.llmPrompt == ""`
  - コンパイルエラーが発生しない（＝テストがコンパイル・実行できること自体が保証）
  - **境界での正確性**: デフォルト値が空文字であり、null や未初期化ではないこと
  - **一貫した動作**: 既存の `body`/`defaultValue` と同じく空文字デフォルトで一貫
- **テストの目的**: 後方互換性（NFR-001 相当の既存パターン踏襲）の確認
  - **堅牢性の確認**: 新規プロパティ追加が既存コードへ無影響であること
- 🔵 *TASK-0056.md テストケース4・requirements.md TC4・後方互換性制約に基づく（推測なし）*

### TC2: FieldValueSource.entries に LLM が含まれ valueOf("LLM") が成功する

- **テスト名**: FieldValueSource.entries に LLM が含まれ valueOf で参照できる
  - **境界値の意味**: enum に新規値を「末尾追加」した境界。列挙・文字列変換の両経路で `LLM` が到達可能であること
  - **境界値での動作保証**: `.entries` 走査・`valueOf` 変換のいずれでも `LLM` が正しく解決される
- **入力値**: `FieldValueSource.entries` の走査、および `FieldValueSource.valueOf("LLM")`
  - **境界値選択の根拠**: enum 追加時に確認すべき 2 経路（列挙と文字列変換）。requirements.md 2.3 の「参照方法」に明記
  - **実際の使用場面**: UI の値ソース選択列挙、Repository の文字列↔enum マッピング（TASK-0057/0070）
- **期待される結果**:
  - `"LLM" in FieldValueSource.entries.map { it.name }` が true
  - `FieldValueSource.valueOf("LLM") == FieldValueSource.LLM`（例外なし）
  - **境界での正確性**: `LLM` が既存 4 値と衝突せず新規値として解決される
  - **一貫した動作**: 既存値（FIXED 等）と同様に列挙・変換可能
- **テストの目的**: `FieldValueSource.LLM` が正しく追加され両経路で参照可能なことの確認
  - **堅牢性の確認**: enum の列挙・変換が新規値追加後も一貫すること
- 🔵 *TASK-0056.md テストケース2・requirements.md TC2・REQ-303 に基づく（推測なし）*

### TC2-B: 既存 enum 網羅テストの件数更新（LLM 追加で 5 値になる）

- **テスト名**: FieldValueSource が全 5 値（LLM を含む）を持つ
  - **境界値の意味**: enum の総数が 4 → 5 に変わる境界。既存の件数アサーションを追従させる
  - **境界値での動作保証**: 全既存値 + `LLM` が過不足なく列挙され、総数が正確に 5
- **入力値**: `FieldValueSource.entries.map { it.name }`
  - **境界値選択の根拠**: 既存テスト `FieldValueSource contains all expected values`（`TemplateTest.kt:37-44`）が `assertEquals(4, names.size)` を検証しており、`LLM` 追加でそのままでは失敗する
  - **実際の使用場面**: 回帰テストとして enum 値の意図しない増減を検出
- **期待される結果**:
  - `"FIXED"`, `"HTML_META"`, `"URL"`, `"EMPTY"`, `"LLM"` がすべて含まれる
  - `names.size == 5`
  - **境界での正確性**: 既存 4 値を残したまま `LLM` のみが増える（差分 +1）
  - **一貫した動作**: 既存値の削除・改名がないこと
- **テストの目的**: 既存回帰テストの追従更新と enum 網羅の再保証
  - **堅牢性の確認**: 完了条件「既存の単体テストがすべて通る」の充足（既存テストの更新必須）
- 🔵 *既存 `TemplateTest.kt:37-44` の実コードと REQ-303 に基づく（実装確認済み・推測なし）*

### TC-EDGE1: bodyLlmPrompt / llmPrompt に空文字を明示指定した場合

- **テスト名**: 新規プロパティに空文字を明示指定してもデフォルトと同値になる
  - **境界値の意味**: 「未設定」を表す空文字 `""` の境界。REQ-102 のボタン非活性判定（後続タスク）の入力となる値
  - **境界値での動作保証**: 明示的 `""` 指定とデフォルト適用が同一の結果になること
- **入力値**: `Template(name = "t", fields = emptyList(), bodyLlmPrompt = "")` / `TemplateField(key = "k", valueSource = FieldValueSource.EMPTY, valueType = FieldValueType.STRING, llmPrompt = "")`
  - **境界値選択の根拠**: requirements.md 4.2 エッジケース「未設定（空文字）ケース」。空文字が「プロンプト未設定」を表すという意味論の境界
  - **実際の使用場面**: UI でプロンプト欄を空のまま保存した場合
- **期待される結果**: `bodyLlmPrompt == ""` / `llmPrompt == ""`（デフォルト生成時と等価）
  - **境界での正確性**: 明示指定の空文字とデフォルト空文字が区別されず同値
  - **一貫した動作**: 「未設定」の表現が空文字に統一される
- **テストの目的**: 空文字（未設定）の意味論的境界の確認
  - **堅牢性の確認**: 後続の非活性判定（`== ""`）が安定して機能する前提の保証
- 🟡 *requirements.md 4.2 エッジケースからの妥当な推測。TASK-0056 コアテストには明示されないが空文字境界を補完*

---

## 4. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: プロジェクト全体が Kotlin。対象はすべて Kotlin の data class / enum class（CLAUDE.md・note.md 技術スタック）
  - **テストに適した機能**: data class 自動生成 `copy()`/`equals()`、`enum.entries`、バッククォート日本語テスト名、名前付き引数
- **テストフレームワーク**: JUnit 4（`org.junit.Test` / `org.junit.Assert`）
  - **フレームワーク選択の理由**: 既存 `TemplateTest.kt` が JUnit 4 を使用（`app/build.gradle.kts` の `testImplementation(libs.junit)`）。純 JVM ロジックのため MockK/Robolectric は不要
  - **テスト実行環境**: JVM 単体テスト（`app/src/test/`）。`mise exec -- ./gradlew test` または `--tests "*TemplateTest*"` で実行。デバイス/エミュレータ不要
- 🔵 *note.md「5. テスト関連情報」・既存 `TemplateTest.kt`・`app/build.gradle.kts` に基づく（推測なし）*

---

## 5. テストケース実装時の日本語コメント指針

各テスト実装時に以下の日本語コメントを含める（既存 `TemplateTest.kt` は AAA パターン）。

### テストケース開始時のコメント

```kotlin
// 【テスト目的】: Template.copy() で bodyLlmPrompt のみを部分更新できることを確認
// 【テスト内容】: bodyLlmPrompt 未設定の Template を copy(bodyLlmPrompt=...) し、対象のみ更新・他不変を検証
// 【期待される動作】: 指定プロパティのみ差し替わり、name/body/fields/isDefault/id は保持される
// 🔵 TASK-0056 TC1・REQ-101 に基づく
```

### Given（準備フェーズ）のコメント

```kotlin
// 【テストデータ準備】: bodyLlmPrompt 未指定（デフォルト "")の Template を用意する
// 【初期条件設定】: name="Web記事", fields=emptyList() の最小構成
// 【前提条件確認】: bodyLlmPrompt がデフォルトで空文字であること
```

### When（実行フェーズ）のコメント

```kotlin
// 【実際の処理実行】: original.copy(bodyLlmPrompt = "要約してください") を呼び出す
// 【処理内容】: data class 自動生成の copy() による部分更新
// 【実行タイミング】: 初期状態のインスタンス生成直後
```

### Then（検証フェーズ）のコメント

```kotlin
// 【結果検証】: bodyLlmPrompt が更新され、他プロパティが保持されることを検証
// 【期待値確認】: 更新値="要約してください"、他は元の値と一致
// 【品質保証】: 新規プロパティ追加が既存プロパティに副作用を与えないことを保証
```

### 各 expect（assert）ステートメントのコメント

```kotlin
// 【検証項目】: bodyLlmPrompt が指定値へ更新されたこと
assertEquals("要約してください", updated.bodyLlmPrompt) // 🔵
// 【検証項目】: name が元の値のまま保持されたこと
assertEquals("Web記事", updated.name) // 🔵
```

> 注: 本タスクのテストは外部リソース・状態を持たないため `@Before`/`@After`（setup/cleanup）は不要。既存 `TemplateTest.kt` も setup/cleanup を持たない。

---

## 6. 要件定義との対応関係

- **参照した機能概要**: requirements.md「1. 機能の概要」（Template.bodyLlmPrompt / TemplateField.llmPrompt / FieldValueSource.LLM の追加）
- **参照した入力・出力仕様**: requirements.md「2.1 Template」「2.2 TemplateField」「2.3 FieldValueSource」（型定義・デフォルト・制約表）
- **参照した制約条件**: requirements.md「3. 制約条件」（後方互換性・デフォルト値必須・プロパティ位置・Kotlin/SDK）
- **参照した使用例**: requirements.md「4.1 基本的な使用パターン」「4.2 エッジケース」「4.3 エラーケース」
- **参照した EARS 要件**: REQ-101（bodyLlmPrompt）、REQ-104（llmPrompt）、REQ-303（FieldValueSource.LLM）、REQ-102（空文字＝未設定の後続利用）
- **参照した既存実装**: `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt`（テストパターン・修正必要箇所）
- **参照したタスク定義**: `docs/tasks/llm-memo-rewrite/TASK-0056.md`（コアテストケース 1〜4）

---

## 7. テストケース一覧（サマリー）

| No. | 分類 | テストケース | 対応要件 | 信頼性 |
|-----|------|-------------|---------|--------|
| TC1 | 正常系 | Template.copy() で bodyLlmPrompt のみ変更 | REQ-101 | 🔵 |
| TC3 | 正常系 | TemplateField.copy() で llmPrompt のみ変更 | REQ-104 | 🔵 |
| TC5 | 正常系 | 値を明示指定してインスタンス生成 | REQ-101/104 | 🟡 |
| TC-ERR1 | 異常系 | valueOf に未定義名 → IllegalArgumentException | REQ-303 | 🔵 |
| TC4 | 境界値 | 新規プロパティ未指定でデフォルト空文字（後方互換） | 後方互換性 | 🔵 |
| TC2 | 境界値 | entries に LLM が含まれ valueOf("LLM") 成功 | REQ-303 | 🔵 |
| TC2-B | 境界値 | 既存 enum 網羅テスト件数更新（5 値） | REQ-303 | 🔵 |
| TC-EDGE1 | 境界値 | 空文字を明示指定（未設定の意味論） | REQ-102 | 🟡 |

### 信頼性レベル分布

| 信頼性 | 件数 | 割合 |
|--------|------|------|
| 🔵 青信号 | 6 | 75% |
| 🟡 黄信号 | 2 | 25% |
| 🔴 赤信号 | 0 | 0% |

**総合品質評価**: ✅ 高品質（正常系・異常系・境界値を網羅、期待値明確、Kotlin/JUnit4 で実装可能、青信号 75%）

---

## 8. 実装上の注意（Green フェーズへの申し送り）

1. 🔵 **既存テストの更新必須**: `TemplateTest.kt:37-44` の `FieldValueSource contains all expected values` を `assertEquals(5, ...)` へ更新し `assert("LLM" in names)` を追加すること（TC2-B）。未更新だと既存テストが失敗し完了条件を満たさない。
2. 🔵 **プロパティ配置**: `bodyLlmPrompt` は `body` の直後、`llmPrompt` は `metaKey` の直後（interfaces.kt 準拠。名前付き引数テストのため機能上は順序非依存だが設計統一）。
3. 🔵 **デフォルト値必須**: 両プロパティとも `= ""` を指定（後方互換性）。
4. 🟡 **既存 when 分岐**: `FieldValueSource` を網羅 `when` している既存コードがあれば `LLM` 追加で網羅性警告が出る可能性（本タスク対象外・後続タスクで対応）。本タスクでは `mise exec -- ./gradlew test` が通ることを確認。

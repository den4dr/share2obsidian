# TDDテストケース定義 - TASK-0068: EditScreenViewModel suggestTags()実装

- **機能名**: edit-viewmodel-suggest-tags（EditScreenViewModel タグ提案）
- **タスクID**: TASK-0068
- **要件名**: llm-memo-rewrite
- **作成日**: 2026-07-08
- **参照ノート**: `docs/implements/llm-memo-rewrite/TASK-0068/note.md`
- **参照要件**: `docs/implements/llm-memo-rewrite/TASK-0068/edit-viewmodel-suggest-tags-requirements.md`
- **踏襲元テスト**: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelRewriteBodyTest.kt`（TASK-0063）

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書・既存実装を参考にしてほぼ推測していない
- 🟡 **黄信号**: 元資料から妥当な推測
- 🔴 **赤信号**: 元資料にない推測

---

## 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2+
  - **言語選択の理由**: プロジェクト全体（ViewModel・ドメインモデル・UI）が Kotlin で統一されており、`sealed class`（`LlmRewriteResult`）による網羅的な `when` 分岐、`data class`（`EditFormState`）の `copy()` による不変状態更新、コルーチン（`viewModelScope.launch` / `StateFlow` / `SharedFlow`）が本タスクの非同期処理・状態管理にそのまま適用できる。
  - **テストに適した機能**: `when` の網羅性・null安全・`suspend` 関数のテスト容易性。
- **テストフレームワーク**: JUnit 4 + Robolectric + MockK + kotlinx-coroutines-test
  - **フレームワーク選択の理由**: 踏襲元 `EditScreenViewModelRewriteBodyTest.kt`（TASK-0063）と同一構成に揃えることで、確立済みのテストパターン（`Dispatchers.setMain(testDispatcher)`・`collectErrorEvents` ヘルパー・`CompletableDeferred` によるローディング中間観測）を再利用できる。`R.string.*` リソース解決のため Robolectric が必要。MockK で `LlmRewriteRepository` / `LlmSettingsRepository` をスタブ化し Hilt を起動せず直接コンストラクタ注入する。
  - **テスト実行環境**: JVM 上の Robolectric（`@Config(sdk = [34])`）。実行コマンド `mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest"`
- 🔵 信頼性レベル: note.md §1・§5、踏襲元テスト実装より

**テストクラス（新規）**: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelSuggestTagsTest.kt`

---

## テストケース一覧

| TC ID | カテゴリ | テスト内容 | 信頼性 |
|-------|---------|-----------|--------|
| TC-0068-N01 | 正常系 | 既存tagsTextに生成結果が追加される（既存保持） | 🔵 REQ-302 |
| TC-0068-N02 | 正常系 | 既存tagsTextが空文字なら生成結果のみ設定 | 🔵 完了条件 |
| TC-0068-E01 | 異常系 | 失敗時はtagsText不変・errorEvents発行 | 🔵 EDGE-004/NFR-201 |
| TC-0068-B01 | 境界値 | 実行中isSuggestingTags=true→完了後false | 🔵 REQ-201 |
| TC-0068-B02 | 境界値 | sourceContent空文字でもガードせず1回実行 | 🔵 EDGE-101 |
| TC-0068-N03（補完） | 正常系 | 入力ソースがsourceContentであり本文bodyでない | 🟡 REQ-302/REQ-406 |
| TC-0068-B03（補完） | 境界値 | 成功結果が空文字の場合のマージ挙動 | 🟡 REQ-302境界 |

補完ケース（N03/B03）は踏襲元 TASK-0063 の網羅方針（入力ソース検証・空応答境界）を suggestTags 向けに移植したもの。TASK-0068.md 定義の5ケース（N01/N02/E01/B01/B02）が必須、補完2件は品質強化のための推奨。

---

## 1. 正常系テストケース（基本的な動作）

### TC-0068-N01: suggestTags()成功時に既存tagsTextへ生成結果が追加される

- **テスト名**: suggestTags 成功時 既存 tagsText に生成タグが追加連結される
  - **何をテストするか**: `rewrite()` が `Success` を返したとき、既存タグを保持したまま生成結果をカンマ+スペース区切りで連結（追加）することを確認する。
  - **期待される動作**: `tagsText` が置換されず `"$current, ${result.text}"` に更新される。
- **入力値**:
  - `formState.tagsText = "private, tag1"`（`initialize()` 後に想定タグ設定）
  - `llmRewriteRepository.rewrite(...)` → `LlmRewriteResult.Success("tag2, tag3")`
  - **入力データの意味**: 既にユーザー/テンプレートが付与済みのタグがある一般的状態を代表する。
- **期待される結果**: `viewModel.formState.value.tagsText == "private, tag1, tag2, tag3"`
  - **期待結果の理由**: REQ-302「生成結果は既存タグの置換ではなく追加」。`current.isBlank()` が false なので連結分岐が選択される。
- **テストの目的**: タグの追加マージロジック（既存破壊禁止）の確認。
  - **確認ポイント**: 区切り文字が `", "`（カンマ+スペース）であること、既存部分が完全保持されること。
- 🔵 信頼性レベル: TASK-0068.md テストケース1・REQ-302・requirements.md §4

### TC-0068-N02: 既存tagsTextが空文字の場合は生成結果のみが設定される

- **テスト名**: suggestTags 成功時 tagsText 空文字なら生成結果のみが設定される
  - **何をテストするか**: 既存タグが空（`isBlank()`）のとき、区切り文字なしで生成結果のみが設定されることを確認する。
  - **期待される動作**: 先頭に不要な `", "` が付かず `result.text` がそのまま設定される。
- **入力値**:
  - `formState.tagsText = ""`
  - `llmRewriteRepository.rewrite(...)` → `LlmRewriteResult.Success("tag1, tag2")`
  - **入力データの意味**: タグ未入力の初期状態を代表する。
- **期待される結果**: `viewModel.formState.value.tagsText == "tag1, tag2"`
  - **期待結果の理由**: 完了条件・REQ-302。`current.isBlank()` が true のとき `result.text` のみを設定する分岐。
- **テストの目的**: 空タグ時のマージ分岐（先頭区切り文字混入の防止）の確認。
  - **確認ポイント**: 先頭が `", tag1..."` にならないこと。
- 🔵 信頼性レベル: TASK-0068.md テストケース2・完了条件

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-0068-E01: suggestTags()失敗時にtagsTextが変更されずerrorEventsが発行される

- **テスト名**: suggestTags 失敗時 tagsText 不変で messageResId が emit される
  - **エラーケースの概要**: LLM呼び出しがネットワーク失敗等で `LlmRewriteResult.Failure` を返すケース（EDGE-001〜004）。
  - **エラー処理の重要性**: 失敗時に既存タグ入力を破壊せず、ユーザーに再試行可能な状態を保ち、エラーを明示するため（NFR-201）。
- **入力値**:
  - `formState.tagsText = "private"`
  - `llmRewriteRepository.rewrite(...)` → `LlmRewriteResult.Failure.NetworkError(R.string.error_llm_network)`
  - **不正な理由**: 外部LLM APIへの到達不可という実運用で頻出する失敗を代表する。
  - **実際の発生シナリオ**: 機内モード・圏外・エンドポイント設定ミス等。
- **期待される結果**:
  - `viewModel.formState.value.tagsText == "private"`（不変）
  - `viewModel.errorEvents` から `R.string.error_llm_network` が1件発行される
  - **エラーメッセージの内容**: `messageResId` のみをUIへ渡し、機微情報（APIキー・例外詳細）は含めない（NFR-102）。
  - **システムの安全性**: `tagsText` を変更しないためユーザー入力が保護される。
- **テストの目的**: 失敗時の非破壊性とエラーイベント発行の確認。
  - **品質保証の観点**: 全 `Failure` サブクラス（NetworkError/AuthError/Timeout/EmptyOrInvalidResponse/Unknown）が同じ `result.messageResId` 経路で扱われるため、代表として NetworkError を検証する。
  - **注意点**: `errorEvents` は `SharedFlow`（Hot）のため、`suggestTags()` 呼び出し前に購読を開始し、検証後に `job.cancel()` する（本プロジェクト既知パターン）。
- 🔵 信頼性レベル: TASK-0068.md テストケース3・EDGE-004・NFR-201・踏襲元 TC-0063-E01

---

## 3. 境界値テストケース（最小値、最大値、null等）

### TC-0068-B01: suggestTags()実行中はisSuggestingTagsがtrueになり完了後falseに戻る

- **テスト名**: suggestTags 実行中 isSuggestingTags が true になり完了後 false に戻る
  - **境界値の意味**: ローディングフラグの状態遷移境界（false → true → false）。呼び出し前後の2時点を観測する。
  - **境界値での動作保証**: 呼び出し中と完了後で `isSuggestingTags` が一貫して切り替わること。
- **入力値**:
  - `llmRewriteRepository.rewrite(...)` の応答を `CompletableDeferred` で保留（`coAnswers { deferred.await() }`）
  - **境界値選択の根拠**: 応答完了前後の中間状態を確実に観測するため外部制御が必要。
  - **実際の使用場面**: UI（TASK-0069）がこの状態でスピナー表示・ボタン非活性化を制御する。
- **期待される結果**:
  - `suggestTags()` 呼び出し＋`advanceUntilIdle()` 後（未完了時点）: `isSuggestingTags == true`
  - `deferred.complete(Success(...))` ＋`advanceUntilIdle()` 後: `isSuggestingTags == false`
  - **境界での正確性**: 完了時は成功・失敗いずれでも `finally` 相当で false に戻ること（本タスクは `when` 後に一律 `copy(isSuggestingTags = false)`）。
- **テストの目的**: REQ-201 ローディング状態管理の確認。
  - **堅牢性の確認**: 中間状態を取りこぼさず観測できること。
  - **注意点**: `advanceUntilIdle()` 前に true を検証し、その後 `deferred.complete(...)` する（踏襲元 TC-0063-N03 と同手順）。
- 🔵 信頼性レベル: TASK-0068.md テストケース4・REQ-201・踏襲元 TC-0063-N03

### TC-0068-B02: sourceContentが空文字でもsuggestTags()がガードされず実行される

- **テスト名**: suggestTags は sourceContent 空文字でもガードされず rewrite が1回呼ばれる
  - **境界値の意味**: LLM入力 `sourceContent` の最小値（空文字 `""`）。空チェックによる早期returnを行わない境界。
  - **境界値での動作保証**: 空文字でも呼び出しスキップ・ボタン非活性化を行わない（EDGE-101）。
- **入力値**:
  - `viewModel.initialize(..., sourceContent = "")` で初期化
  - `llmRewriteRepository.rewrite(...)` → `LlmRewriteResult.Success("tag")`（モック）
  - **境界値選択の根拠**: 本文なし共有（タイトルのみ等）でもタグ提案を実行可能とする要件（EDGE-101）。
  - **実際の使用場面**: URL/タイトルのみ共有で本文が空のケース。
- **期待される結果**:
  - `coVerify(exactly = 1) { mockRewrite.rewrite(any(), TAG_SUGGESTION_PROMPT相当, "") }` が成立する
  - **一貫した動作**: `rewriteBody()` の EDGE-101 対応（TC-0063-B02）と同一方針。第3引数（content）が空文字で1回だけ呼ばれる。
  - **補足**: 第2引数（prompt）は固定プロンプト。テストでは `context.getString(R.string.llm_tag_suggestion_prompt)` の解決値、または `any()` で照合する（実装の Context 注入方式確定後に厳密化可能）。
- **テストの目的**: EDGE-101 空文字ガード禁止の確認。
  - **堅牢性の確認**: 空入力で呼び出しがスキップされないこと。
- 🔵 信頼性レベル: TASK-0068.md テストケース5・EDGE-101・踏襲元 TC-0063-B02

---

## 4. 補完テストケース（推奨・品質強化）

### TC-0068-N03（補完）: 入力ソースがsourceContentでありユーザー編集後のtagsText/bodyでない

- **テスト名**: suggestTags は sourceContent を入力とし body/tagsText を入力にしない
  - **何をテストするか**: `updateBody()` 等でフォームを編集した後に `suggestTags()` を呼んでも、`rewrite()` の content 引数が保持済み `sourceContent` であることを確認する。
  - **期待される動作**: `rewrite(settings, prompt, "元コンテンツ")` が呼ばれ、編集後 body では呼ばれない。
- **入力値**:
  - `initialize(..., sourceContent = "元コンテンツ")` → `updateBody("編集後本文")`
  - `rewrite(...)` → `Success("tag")`
- **期待される結果**:
  - `coVerify { mockRewrite.rewrite(any(), any(), "元コンテンツ") }` 成立
  - `coVerify(exactly = 0) { mockRewrite.rewrite(any(), any(), "編集後本文") }`
  - **期待結果の理由**: REQ-406「元コンテンツをユーザー編集と独立して保持」・REQ-302（入力ソースは本文リライトと同一）。
- **テストの目的**: 入力ソース統一（`sourceContent`）の回帰防止。
- 🟡 信頼性レベル: requirements.md §2・REQ-406、踏襲元 TC-0063-N04 からの妥当な移植（TASK-0068.md には明示なし）

### TC-0068-B03（補完）: 成功結果が空文字の場合のマージ挙動

- **テスト名**: suggestTags 成功結果が空文字の場合のマージ結果を確認する
  - **境界値の意味**: `Success.text` が空文字 `""`（生成結果の下限境界）。
  - **境界値での動作保証**: 空応答でもエラー扱いせず、マージ処理が例外なく完走する。
- **入力値**:
  - 既存タグあり: `tagsText = "private"`、`rewrite(...)` → `Success("")`
  - **境界値選択の根拠**: LLMが空文字を返す下限ケースで文字列連結が破綻しないことを確認する。
- **期待される結果**:
  - `current.isBlank()` が false のため `"private, "` となる（`"$current, ${result.text}"` の仕様どおり）。
  - `errorEvents` には何も発行されない（Success は空文字でも成功扱い）。
  - **一貫した動作**: 実装仕様（マージ式）に忠実な結果を検証対象とする。末尾区切りのトリム要否は Green/Refactor フェーズで実装確定に合わせて調整する。
- **テストの目的**: 空応答成功と失敗種別の混同がないこと・連結式の境界挙動の確認。
- 🟡 信頼性レベル: LlmRewriteResult「Success は空文字も許容」・REQ-302 マージ式からの妥当な推測（TASK-0068.md には明示なし）

---

## 5. テストケース実装時の日本語コメント指針

各テストケース実装時は踏襲元 `EditScreenViewModelRewriteBodyTest.kt` に倣い、以下の日本語コメントを含める。

### テストケース開始時のコメント

```kotlin
// 【テスト目的】: suggestTags 成功時に既存 tagsText へ生成タグが追加連結されることを確認する
// 【テスト内容】: Success スタブに対し suggestTags() を呼び、advanceUntilIdle() 後の tagsText を検証する
// 【期待される動作】: tagsText が "private, tag1, tag2, tag3" に更新される（置換ではなく追加）
// 🔵 信頼性レベル: TASK-0068.md テストケース1・REQ-302 より
```

### Given（準備フェーズ）のコメント

```kotlin
// 【テストデータ準備】: 既存タグありの一般的状態を代表するため tagsText を "private, tag1" に設定する
// 【初期条件設定】: rewrite() は Success("tag2, tag3") を返すようスタブ化する
// 【前提条件確認】: getSettings() は setUp() で有効な LlmSettings を返すよう共通スタブ済み
```

### When（実行フェーズ）のコメント

```kotlin
// 【実際の処理実行】: suggestTags() を呼び出し viewModelScope の非同期処理を進める
// 【処理内容】: isSuggestingTags=true → getSettings().first() → rewrite() → tagsText マージ → isSuggestingTags=false
// 【実行タイミング】: advanceUntilIdle() で非同期完了まで進めてから検証する
```

### Then（検証フェーズ）のコメント

```kotlin
// 【結果検証】: tagsText が既存タグ保持のまま追加連結されたことを確認する
// 【期待値確認】: REQ-302 の追加方針。current が非空なので "$current, ${result.text}" となる
// 【品質保証】: 既存タグ破壊がないことを保証し、ユーザー入力の保護を担保する
```

### 各expectステートメントのコメント

```kotlin
// 【検証項目】: 既存タグ保持＋生成結果の追加連結
// 🔵 信頼性レベル: REQ-302
assertEquals(
    "suggestTags 成功時に既存 tagsText へ生成タグが追加連結されること",
    "private, tag1, tag2, tag3",
    viewModel.formState.value.tagsText,
) // 【確認内容】: 置換ではなく追加であること
```

### セットアップ・クリーンアップのコメント

```kotlin
@Before
fun setUp() {
    // 【テスト前準備】: viewModelScope が使う Main dispatcher をテスト用 StandardTestDispatcher に差し替える
    // 【環境初期化】: getSettings() が既定で有効な LlmSettings を返すよう共通スタブする
    Dispatchers.setMain(testDispatcher)
    coEvery { mockSettings.getSettings() } returns flowOf(LlmSettings(...))
}

@After
fun tearDown() {
    // 【テスト後処理】: 差し替えた Main dispatcher を元に戻す
    // 【状態復元】: 後続テストへの dispatcher 汚染を防ぐ
    Dispatchers.resetMain()
}
```

---

## 6. 要件定義との対応関係

- **参照した機能概要**: requirements.md §1（suggestTags の位置づけ）、TASK-0068.md タスク概要
- **参照した入力・出力仕様**: requirements.md §2（入力=sourceContent+固定プロンプト+settings、出力=tagsText マージ / errorEvents / isSuggestingTags）
- **参照した制約条件**: requirements.md §3（NFR-001/101/102、REQ-302、EDGE-101、既存 rewriteBody パターン踏襲）
- **参照した使用例**: requirements.md §4（正常系2パターン・EDGE-101・EDGE-004）
- **対応する EARS要件**: REQ-103, REQ-201, REQ-301, REQ-302, REQ-406, NFR-102, NFR-201, EDGE-001〜004, EDGE-101
- **踏襲元テストパターン**: EditScreenViewModelRewriteBodyTest.kt（`collectErrorEvents`・`CompletableDeferred`・`processedContent()` ヘルパー、`@Config(sdk = [34])`）

### 実装対象ファイル

| ファイル | 変更内容 |
|---------|---------|
| `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt` | `isSuggestingTags: Boolean = false` 追加 |
| `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt` | `suggestTags()` メソッド・`TAG_SUGGESTION_PROMPT` 追加 |
| `app/src/main/res/values/strings.xml` | `llm_tag_suggestion_prompt` 追加 |
| `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelSuggestTagsTest.kt` | 本テストケース（必須5＋推奨2）を新規実装 |

---

## 品質判定

- ✅ **テストケース分類**: 正常系（N01/N02/N03）・異常系（E01）・境界値（B01/B02/B03）を網羅
- ✅ **期待値定義**: 各ケースの入力・期待値が具体的（tagsText の厳密文字列・errorEvents の resId・呼び出し回数）
- ✅ **技術選択**: Kotlin + JUnit4/Robolectric/MockK/coroutines-test で確定（踏襲元と同一）
- ✅ **実装可能性**: 既存 `rewriteBody()` テストパターンで実現可能
- ⚠️ **留意点**: 固定プロンプト解決の Context 注入方式が未確定（B02 の第2引数照合は暫定 `any()` で開始可、実装確定後に厳密化）
- **信頼性レベル分布**: 🔵 5件（必須ケース）、🟡 2件（補完ケース・Context注入方式）、🔴 なし

**総合判定**: 高品質（必須5ケースは🔵、補完2ケースは踏襲元からの妥当な移植で🟡、🔴ゼロ）

---

**次のお勧めステップ**: `/tsumiki:tdd-red llm-memo-rewrite TASK-0068` でRedフェーズ（失敗テスト作成）を開始します。

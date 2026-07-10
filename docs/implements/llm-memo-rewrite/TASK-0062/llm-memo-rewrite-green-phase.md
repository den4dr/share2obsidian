# TDD Greenフェーズ記録: MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加

**機能名**: llm-memo-rewrite
**タスクID**: TASK-0062
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. 実装方針

Redフェーズの記録（`llm-memo-rewrite-red-phase.md` §5）で指示された内容をそのまま最小実装した。

1. `EditScreenViewModel.initialize()` に `sourceContent: String = ""` / `bodyLlmPrompt: String = ""` の
   デフォルト引数を追加し、既存呼び出し（`MainActivityEditFlowTest` 等）との後方互換性を維持する。
2. 上記2値を読み取り可能な保持プロパティ（`var ... private set`）として `EditScreenViewModel` に追加し、
   `initialize()` 内の `initialized` フラグチェック直後で代入する。`initialized` フラグによる重複初期化防止
   （EDGE-101）の対象に含め、2回目以降の呼び出しでは上書きしない。
3. `MainActivity.onCreate()` 内、`TemplateApplicator.buildBody()` 呼び出し**前**に
   `sourceContent = processed.body` を退避し、`bodyLlmPrompt = defaultTemplate?.bodyLlmPrompt.orEmpty()` を算出。
   `viewModel.initialize()` の呼び出しを名前付き引数形式に変更し、新規2引数を渡す。

仕様（要件定義書・テストケース定義書・Redフェーズ記録）と現在の実装を照合したが、差異は発見されなかった。
そのためAskUserQuestionによる確認は不要と判断し、そのまま実装した。

---

## 2. 実装コード

### 2.1 `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`（差分箇所）

```kotlin
    // 【重複初期化防止フラグ】: initialize() が2回以上呼ばれた場合に2回目以降を無視するためのフラグ 🔵
    // 【用途】: 画面回転時に Activity が再作成されても、ViewModel は生存し続けるため
    //           initialize() が再呼び出しされても既存の編集内容を上書きしない（EDGE-101）
    private var initialized = false

    /**
     * 【プロパティ概要】: LLM リライトの入力に使う、テンプレート適用前の元コンテンツ
     * 【実装方針】: `initialize()` の新規引数で受け取った値をそのまま保持する。
     *              後続 TASK-0063 の `rewriteBody()` で LLM API への入力として使用される想定
     * 【テスト対応】: TC-0062-N01, N03, B01, B02, B03
     * 🟡 信頼性レベル: testcases.md §0 方式A（観測点として ViewModel に保持プロパティを追加する方針）より
     */
    var sourceContent: String = ""
        private set

    /**
     * 【プロパティ概要】: デフォルトテンプレートに設定された本文用 LLM プロンプト
     * 【実装方針】: `initialize()` の新規引数で受け取った値をそのまま保持する。
     *              `defaultTemplate` が null または `bodyLlmPrompt` 未設定の場合は空文字になる（REQ-102）
     * 【テスト対応】: TC-0062-N02, N03, E01, E02, B03
     * 🟡 信頼性レベル: testcases.md §0 方式A（観測点として ViewModel に保持プロパティを追加する方針）より
     */
    var bodyLlmPrompt: String = ""
        private set

    /**
     * 【機能概要】: ProcessedContent と NoteConfig からフォーム初期値をセットする
     * ...(既存コメント)...
     * 【新規引数】: `sourceContent`・`bodyLlmPrompt` は LLM リライト（TASK-0063）用の下地として
     *              ViewModel 内部プロパティにそのまま保持する。EditFormState には含めない（note.md 方針）
     * 🔵 信頼性レベル: REQ-001, REQ-003, REQ-405・acceptance-criteria.md TC-003-01〜04 より
     * 🟡 信頼性レベル: sourceContent/bodyLlmPrompt 引数・保持は requirements.md §2.2・testcases.md §0 より
     *
     * @param sourceContent テンプレート適用前の元コンテンツ（LLM 入力用、TASK-0063 で使用）。省略時は空文字
     * @param bodyLlmPrompt テンプレートの本文用 LLM プロンプト。省略時・未設定時は空文字（REQ-102）
     */
    fun initialize(
        processed: ProcessedContent,
        config: NoteConfig,
        customFields: List<CustomFieldState> = emptyList(),
        sourceContent: String = "",
        bodyLlmPrompt: String = "",
    ) {
        // 【重複実行防止】: 画面回転時に initialize() が再度呼ばれても無視する（EDGE-101）🔵
        // 【新規引数の扱い】: sourceContent/bodyLlmPrompt も同じガードの対象とし、
        //                    2回目以降の呼び出しでは上書きしない（既存の値を保持する）🟡
        if (initialized) return

        // 【初期化フラグ更新】: 次回以降の呼び出しを無視するためにフラグを立てる 🔵
        initialized = true

        // 【新規プロパティ保持】: LLM リライト用の下地として、受け取った値をそのまま保持する 🟡
        this.sourceContent = sourceContent
        this.bodyLlmPrompt = bodyLlmPrompt

        // 【状態更新】: ProcessedContent と NoteConfig から EditFormState の初期値を構築して StateFlow に設定する 🔵
        _formState.value = EditFormState( ... )  // 既存ロジック変更なし
    }
```

### 2.2 `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt`（差分箇所）

```kotlin
            val defaultTemplate = templateRepository.getDefaultTemplate()
            // vault/folder は DataStore のグローバル設定から取得する（REQ-031）
            val noteSettings = noteSettingsRepository.getSettings().first()
            val config = TemplateApplicator.buildConfig(noteSettings)
            // 【元コンテンツ退避】: テンプレート適用前の processed.body を退避する（REQ-406, LLM 入力用）🔵
            val sourceContent = processed.body
            // 【LLMプロンプト算出】: defaultTemplate が null の場合は空文字にフォールバックする（REQ-102）🔵
            val bodyLlmPrompt = defaultTemplate?.bodyLlmPrompt.orEmpty()
            // 本文テンプレートの {{content}} を共有コンテンツで解決する（REQ-032）
            val resolvedBody = TemplateApplicator.buildBody(defaultTemplate, processed.body)
            val customFields = TemplateApplicator.buildCustomFields(defaultTemplate, processed)
            viewModel.initialize(
                processed = processed.copy(body = resolvedBody),
                config = config,
                customFields = customFields,
                sourceContent = sourceContent,
                bodyLlmPrompt = bodyLlmPrompt,
            )
```

---

## 3. テスト実行結果

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelInitializeTest"
# BUILD SUCCESSFUL（8テストすべて成功）

mise exec -- ./gradlew test
# BUILD SUCCESSFUL（全テストスイート成功、failures=0 / errors=0）
```

回帰確認済みの主なテストスイート（抜粋）:
- `EditScreenViewModelInitializeTest`: 8件成功（新規）
- `EditScreenViewModelTest`: 19件成功（既存、回帰なし）
- `MainActivityEditFlowTest`: 10件成功（既存、回帰なし）
- その他全テストクラス: すべて成功

TC-0062-B04（既存テストスイート全件合格の回帰確認）はこの `./gradlew test` 全件実行で満たされたことを確認した。

---

## 4. 品質判定

```
✅ 高品質:
- テスト結果: Taskツール（Bash経由でのGradle実行）により新規8件・既存全件が成功
- 実装品質: シンプル（デフォルト引数追加＋プロパティ保持のみ、既存ロジックへの変更なし）
- リファクタ箇所: 明確（Refactorフェーズでの検討候補は次節）
- 機能的問題: なし
- コンパイルエラー: なし
- ファイルサイズ: EditScreenViewModel.kt 228行 / MainActivity.kt 170行（800行制限内、分割不要）
- モック使用: 実装コードにモック・スタブは含まれていない（テストコードのみで使用）
```

---

## 5. 課題・改善点（Refactorフェーズで対応）

- `sourceContent` / `bodyLlmPrompt` の保持ロジック（`this.xxx = xxx`）と `initialize()` 本体のコメント量が
  やや冗長になっている。可読性向上の余地あり。
- 現状 `sourceContent` / `bodyLlmPrompt` は単純な代入のみで、ロジックの重複や抽出すべき共通処理はない。
- `MainActivity.onCreate()` の `viewModel.initialize()` 呼び出しが名前付き引数5個になり長くなった。可読性は
  現状問題ないが、TASK-0063 でさらに引数が増える場合は `initialize()` の引数を専用データクラスに
  まとめる設計を検討する余地がある（本タスクのスコープ外）。
- `sourceContent` / `bodyLlmPrompt` はいずれも TASK-0063 で `rewriteBody()` の入力として使用される予定。
  現時点では「保持するだけ」で未使用のプロパティであるため、静的解析上の未使用警告が出る可能性がある
  （Kotlin コンパイラでは public プロパティのため警告なしを確認済み）。

# TDD Refactorフェーズ記録: MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加

**機能名**: llm-memo-rewrite
**タスクID**: TASK-0062
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. リファクタ前の確認

### 1.1 テスト実行結果（リファクタ前）

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelInitializeTest"
# BUILD SUCCESSFUL

mise exec -- ./gradlew test
# BUILD SUCCESSFUL（226 tests, 0 failures, 0 errors, 0 skipped）
```

- 個別テストの実行時間: `app/build/test-results/testDebugUnitTest/*.xml` を確認し、2秒以上かかるテストは無し（全クラス合計 約7.7秒）。
- `describe.skip` / `it.skip` / `@Ignore` 等によるテスト無効化: 検出なし。
- ビルド設定（`testPathIgnorePatterns` 相当）によるテスト除外: 検出なし。
- `debug-*` / `temp-*` / `*.tmp` / `*.bak` 等の開発中生成ファイル: リポジトリ内に検出なし（削除対象なし）。

### 1.2 コード品質確認

- `EditScreenViewModel.kt`（228行）・`MainActivity.kt`（170行）ともに500行制限内。分割不要。
- 実装コードにモック・スタブ・インメモリストレージ代替の記述なし（禁止事項に抵触なし）。
- lint/typecheck相当のコンパイルエラーなし（Gradleビルド成功で確認）。

---

## 2. セキュリティレビュー

- **対象**: `sourceContent`（共有元アプリからのテキスト、テンプレート適用前）・`bodyLlmPrompt`（ユーザーがテンプレート設定で保存したプロンプト文字列）を `EditScreenViewModel` に保持するだけの変更。
- **外部入力の扱い**: `sourceContent` は `ContentProcessor` を経由した既存の共有インテント由来の文字列であり、本タスクでは新たな入力経路・パーサーを追加していない。値の生成元（`processed.body`）は本タスク以前から存在し、検証範囲は変わらない。
- **注入・XSS等**: 本タスクの変更範囲はViewModel内部プロパティへの代入とMainActivityの変数退避のみで、URI構築・SQL・WebView描画・HTMLレンダリングには関与しない（それらは既存の `NoteComposer` / `TemplateApplicator` の責務であり不変）。
- **データ漏洩リスク**: `bodyLlmPrompt` はユーザー自身が設定したテンプレート値であり、外部送信は本タスクの範囲外（TASK-0063でLLM API呼び出し時に初めて外部送信される）。本タスク単体でのログ出力・永続化保存は追加していない。
- **null安全性**: `defaultTemplate?.bodyLlmPrompt.orEmpty()` によりnull由来のクラッシュを防止（TC-0062-E01で確認済み）。
- **結論**: 重大な脆弱性なし。新規の攻撃面（新しい入力パーサー、外部通信、永続化）を追加していないため、追加のセキュリティ対策は不要と判断。

---

## 3. パフォーマンスレビュー

- **計算量**: 追加した処理は文字列の単純代入（`val sourceContent = processed.body`、`this.sourceContent = sourceContent` 等）のみで、O(1)。ループ・再帰・追加のI/Oは発生しない。
- **メモリ使用量**: `sourceContent` は既存の `processed.body` と同一参照（Kotlin String は不変のため参照共有）であり、追加のコピーは発生しない。TC-0062-B02（10,000文字）でも切り詰め・複製コストなしを確認済み。
- **ボトルネック**: なし。`viewModel.initialize()` 呼び出し自体は `lifecycleScope.launch` 内で1回のみ実行される（`initialized` フラグにより2回目以降は早期リターン）。
- **結論**: 重大な性能課題なし。

---

## 4. 実施したリファクタリング

### 4.1 改善内容: コメントの重複解消（可読性向上）🟡

**信頼性レベル**: 🟡 *元資料に明記された指示ではないが、Greenフェーズ記録（`llm-memo-rewrite-green-phase.md` §5 / `llm-memo-rewrite-memo.md` 課題節）で「コメント量がやや冗長」と自己指摘されていた箇所への対応。リファクタリング観点1（可読性の向上）に基づく妥当な改善*

**改善前の問題**:
`sourceContent` / `bodyLlmPrompt` を「TASK-0063のLLMリライトの下地として保持する」という趣旨の説明が、以下の4箇所にほぼ同内容で重複していた。
1. `sourceContent` プロパティのKDoc
2. `bodyLlmPrompt` プロパティのKDoc
3. `initialize()` メソッドのKDoc（【新規引数】節）
4. `initialize()` メソッド本体のガード節コメント

**改善内容**:
- `initialize()` のKDoc【新規引数】節を削除し、代わりに `initialize()` の【実装方針】節に「`sourceContent`・`bodyLlmPrompt` も同じガードの対象とし、2回目以降は上書きしない」という、プロパティKDocには書かれていないメソッド固有の情報のみを残した。
- `@param sourceContent` / `@param bodyLlmPrompt` の説明を「プロパティ参照」という形で該当プロパティのKDocに誘導し、説明の重複を避けた。
- メソッド本体のガード節コメントから、プロパティKDocと重複する「新規引数の扱い」の説明（2行）を削除し、`EDGE-101`のガード目的のみを残した。
- `bodyLlmPrompt` プロパティのKDocは「`sourceContent` と同様に」という参照形式に変更し、説明の重複を避けた。

**変更ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`

**改善後のコード**（差分抜粋）:

```kotlin
    /**
     * 【プロパティ概要】: LLM リライトの入力に使う、テンプレート適用前の元コンテンツ
     * 【設計方針】: `initialize()` の新規引数で受け取った値をそのまま `initialized` ガード配下で保持する
     *              （詳細は `initialize()` のドキュメント参照）。後続 TASK-0063 の `rewriteBody()` で
     *              LLM API への入力として使用される想定で、EditFormState には含めない（note.md 方針）
     * 【テスト対応】: TC-0062-N01, N03, B01, B02, B03
     * 🟡 信頼性レベル: testcases.md §0 方式A（観測点として ViewModel に保持プロパティを追加する方針）より
     */
    var sourceContent: String = ""
        private set

    /**
     * 【プロパティ概要】: デフォルトテンプレートに設定された本文用 LLM プロンプト
     * 【設計方針】: `sourceContent` と同様に `initialize()` の新規引数からそのまま保持する。
     *              `defaultTemplate` が null または `bodyLlmPrompt` 未設定の場合は空文字になる（REQ-102）
     * 【テスト対応】: TC-0062-N02, N03, E01, E02, B03
     * 🟡 信頼性レベル: testcases.md §0 方式A（観測点として ViewModel に保持プロパティを追加する方針）より
     */
    var bodyLlmPrompt: String = ""
        private set

    /**
     * 【機能概要】: ProcessedContent と NoteConfig からフォーム初期値をセットする
     * 【実装方針】: `initialized` フラグで重複呼び出しを防止し、初回のみ状態を更新する。
     *              2回目以降の呼び出し（画面回転時の Activity 再作成を想定）は何もせずに早期リターンする（EDGE-101）。
     *              `sourceContent`・`bodyLlmPrompt` も同じガードの対象とし、2回目以降は上書きしない
     * 【初期値マッピング】:
     *   - title   : `processed.title ?: ""`（null の場合は空文字）
     *   - body    : `processed.body`（そのまま使用）
     *   - tagsText: `config.defaultTags.joinToString(", ")`（List → カンマ+スペース区切り文字列）
     *   - folder  : `config.folder`（そのまま使用）
     * 🔵 信頼性レベル: REQ-001, REQ-003, REQ-405・acceptance-criteria.md TC-003-01〜04 より
     * 🟡 信頼性レベル: sourceContent/bodyLlmPrompt 引数・保持は requirements.md §2.2・testcases.md §0 より
     *
     * @param processed コンテンツ処理結果。`title` は nullable（共有元アプリがタイトルを提供しない場合は null）
     * @param config アプリ設定。`vault`・`folder`・`defaultTags` を含む（TASK-0015: NoteConfig）
     * @param sourceContent テンプレート適用前の元コンテンツ（`sourceContent` プロパティ参照）。省略時は空文字
     * @param bodyLlmPrompt テンプレートの本文用 LLM プロンプト（`bodyLlmPrompt` プロパティ参照）。省略時は空文字（REQ-102）
     */
    fun initialize(
        processed: ProcessedContent,
        config: NoteConfig,
        customFields: List<CustomFieldState> = emptyList(),
        sourceContent: String = "",
        bodyLlmPrompt: String = "",
    ) {
        // 【重複実行防止】: 画面回転時に initialize() が再度呼ばれても無視する（EDGE-101）🔵
        if (initialized) return

        // 【初期化フラグ更新】: 次回以降の呼び出しを無視するためにフラグを立てる 🔵
        initialized = true

        // 【新規プロパティ保持】: LLM リライト用の下地として、受け取った値をそのまま保持する 🟡
        this.sourceContent = sourceContent
        this.bodyLlmPrompt = bodyLlmPrompt

        // 【状態更新】: ProcessedContent と NoteConfig から EditFormState の初期値を構築して StateFlow に設定する 🔵
        _formState.value = EditFormState(
            vault = config.vault,
            title = processed.title ?: "",
            body = processed.body,
            tagsText = config.defaultTags.joinToString(", "),
            folder = config.folder,
            customFields = customFields,
        )
    }
```

`app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt` は該当箇所（`onCreate()` 内の `sourceContent`/`bodyLlmPrompt` 算出・`viewModel.initialize()` 呼び出し）のコメントが既に簡潔かつ非重複であり、追加のリファクタリング対象は見つからなかった。

### 4.2 検討したが見送った改善項目

- **`initialize()` の引数をデータクラスにまとめる案**（Greenフェーズ課題節で言及）: TASK-0063でさらに引数が増える見込みのため、そのタイミングでまとめて設計する方が適切と判断し、本タスクのスコープ外として見送った（rules「機能的な変更は行わない」「一度に大きな変更をしない」に従う）。
- **重複コードの共通化**: `sourceContent`/`bodyLlmPrompt`の算出・保持ロジックは単純な代入のみで、抽出すべき共通処理は存在しなかった。
- **ファイル分割**: `EditScreenViewModel.kt`（228行）・`MainActivity.kt`（170行）とも500行制限に対して十分小さく、分割の必要性なし。

---

## 5. リファクタ後のテスト実行結果

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelInitializeTest" --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest" --tests "com.den4dr.share2Obsidian.MainActivityEditFlowTest"
# BUILD SUCCESSFUL

mise exec -- ./gradlew test
# BUILD SUCCESSFUL（226 tests, 0 failures, 0 errors, 0 skipped）
```

コメントのみの変更（機能的変更なし）であり、リファクタ前後でテスト結果に差分なし。

---

## 6. 品質判定

```
✅ 高品質:
- テスト結果: Taskツールによる実行で全226件が継続成功（failures=0, errors=0, skipped=0）
- セキュリティ: 重大な脆弱性なし（新規の攻撃面を追加しない変更と確認）
- パフォーマンス: 重大な性能課題なし（O(1)の単純代入のみ）
- リファクタ品質: コメント重複の解消により可読性が向上。機能的変更なし
- コード品質: EditScreenViewModel.kt 228行 / MainActivity.kt 170行（500行制限内）
- ドキュメント: 本ファイル・メモファイルに記録済み
```

---

**次のお勧めステップ**: `/tsumiki:tdd-verify-complete` で完全性検証を実行します。

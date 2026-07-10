# TDD Redフェーズ記録: MainActivity sourceContent退避・EditScreenViewModel.initialize()引数追加

**機能名**: llm-memo-rewrite
**タスクID**: TASK-0062
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 0. テスト戦略（実現方式の確定）

テストケース定義書（`llm-memo-rewrite-testcases.md`）§0/§4.2 で保留となっていた「MainActivity 統合テストの実現方式」を以下の通り確定した。

- **確定した方式**: MainActivity は `@AndroidEntryPoint`（実 Hilt/Room DI）であり、`viewModel` フィールドは `private` のため、Robolectric で実起動しても外部から ViewModel の内部状態を観測できない。
- 既存 `MainActivityEditFlowTest`（TC-0020-N02・E01）が onSend コールバックのロジックを抜粋再現しているのと同じ戦略を採用し、`MainActivity.onCreate()` 内の該当ロジック（`sourceContent` 退避 → `TemplateApplicator.buildBody()` → `bodyLlmPrompt` 算出 → `viewModel.initialize()` 呼び出し）を `TemplateApplicator` + `EditScreenViewModel` を直接使用して再現する形とした。
- 観測点は testcases.md §0 の**方式A**（`EditScreenViewModel` に `sourceContent` / `bodyLlmPrompt` の読み取り可能プロパティを追加し `initialize()` で保持する）を採用する。Green フェーズで実装する。

---

## 1. 実装したテストケース一覧

テストケース定義書の TC-0062-N01〜N03・E01〜E02・B01〜B03（8件）を実装した（定義書全9件中、TC-0062-B04「既存テストスイート全件合格」はスイート全体の回帰確認であり個別テストとしては実装せず、Green/Refactor完了後の `mise exec -- ./gradlew test` 全件実行で確認する）。

| No. | 種別 | テスト名 | 信頼性 |
|-----|------|---------|--------|
| TC-0062-N01 | 正常系 | プレースホルダ入りテンプレートで共有した際 sourceContent にテンプレート適用前の元コンテンツが渡る | 🔵 |
| TC-0062-N02 | 正常系 | bodyLlmPrompt 設定済みテンプレートで共有した際その値が initialize に渡る | 🔵 |
| TC-0062-N03 | 正常系 | initialize が新規2引数を保持する | 🟡 |
| TC-0062-E01 | 異常系 | デフォルトテンプレート未設定時 bodyLlmPrompt が空文字となり例外が発生しない | 🔵 |
| TC-0062-E02 | 異常系 | bodyLlmPrompt 空文字のテンプレートで空文字が改変なく伝搬する | 🟡 |
| TC-0062-B01 | 境界値 | 空ノート共有時 sourceContent が空文字のままガードされず伝搬する | 🟡 |
| TC-0062-B02 | 境界値 | 大きな本文でも sourceContent が切り詰めなく完全一致で渡る | 🟡 |
| TC-0062-B03 | 境界値 | 新規引数を省略した既存呼び出しが従来通り動作し sourceContent/bodyLlmPrompt が空文字既定になる | 🔵 |

**信頼性分布**: 🔵 4件 / 🟡 4件 / 🔴 0件（テストケース定義書の分布を踏襲）

---

## 2. 作成したテストファイル

### `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelInitializeTest.kt`（新規）

- 対象: TC-0062-N01〜N03・E01〜E02・B01〜B03（8件）
- 実行環境: 素の JUnit4（Robolectric 不要。`EditScreenViewModel` は Android 依存なし）
- N01/N02/E01/E02 は `Template` + `TemplateApplicator.buildBody()`/`buildCustomFields()` を用いて MainActivity.onCreate() の該当ロジックを再現し、`EditScreenViewModel().initialize(..., sourceContent = ..., bodyLlmPrompt = ...)` を直接呼び出す方式（ロジック抜粋再現）。
- N03/B01/B02/B03 は ViewModel の契約（シグネチャ＋保持）を単体で検証する方式。

---

## 3. テストコード全文

全文は以下のファイルを参照:
- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelInitializeTest.kt`

---

## 4. 実行結果と期待される失敗

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelInitializeTest"
```

**結果**: `compileDebugUnitTestKotlin` タスクでコンパイルエラー（26件）。

```
e: .../EditScreenViewModelInitializeTest.kt:103:13 No parameter with name 'sourceContent' found.
e: .../EditScreenViewModelInitializeTest.kt:104:13 No parameter with name 'bodyLlmPrompt' found.
e: .../EditScreenViewModelInitializeTest.kt:113:23 Unresolved reference 'sourceContent'.
e: .../EditScreenViewModelInitializeTest.kt:119:23 Unresolved reference 'sourceContent'.
... (以下、各 @Test メソッド内の initialize(sourceContent=..., bodyLlmPrompt=...) 呼び出し・
     viewModel.sourceContent / viewModel.bodyLlmPrompt 参照箇所で計26件、原因は同一)
```

**原因**: `EditScreenViewModel.initialize()` に `sourceContent` / `bodyLlmPrompt` パラメータが存在せず、
`EditScreenViewModel` にも同名プロパティが存在しないため。これは意図した Red フェーズの状態であり、
テストコード自体の記述ミスではない。`Template`・`TemplateApplicator`・`ProcessedContent`・`NoteConfig` など
既存クラスを使用する箇所はすべて正常にコンパイル可能であることを確認済み＝エラーは
production クラス `EditScreenViewModel` の未実装（新規引数・新規プロパティ）にのみ起因する
（TASK-0060 Red フェーズと同方針）。

---

## 5. Greenフェーズで実装すべき内容

1. **`app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`**
   - `initialize()` のシグネチャに以下2引数を追加（デフォルト値付き、後方互換維持）:
     ```kotlin
     fun initialize(
         processed: ProcessedContent,
         config: NoteConfig,
         customFields: List<CustomFieldState> = emptyList(),
         sourceContent: String = "",
         bodyLlmPrompt: String = "",
     )
     ```
   - クラスに読み取り可能な保持プロパティを追加し、`initialize()` 内（`initialized` フラグチェック後）で代入する:
     ```kotlin
     var sourceContent: String = ""
         private set
     var bodyLlmPrompt: String = ""
         private set
     ```
   - `initialized` による重複初期化防止（EDGE-101）は既存ロジックを維持し、2回目以降の呼び出しでは
     `sourceContent`/`bodyLlmPrompt` も上書きしない。

2. **`app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt`**
   - `onCreate()` 内、`TemplateApplicator.buildBody()` 呼び出し前に `sourceContent` を退避:
     ```kotlin
     val sourceContent = processed.body
     val bodyLlmPrompt = defaultTemplate?.bodyLlmPrompt.orEmpty()
     ```
   - `viewModel.initialize()` 呼び出しに新規引数を追加:
     ```kotlin
     viewModel.initialize(
         processed = processed.copy(body = resolvedBody),
         config = config,
         customFields = customFields,
         sourceContent = sourceContent,
         bodyLlmPrompt = bodyLlmPrompt,
     )
     ```

3. **回帰確認**（TC-0062-B04 相当）: 実装後に `mise exec -- ./gradlew test` を全件実行し、
   既存の `MainActivityEditFlowTest`・`EditScreenViewModel` 関連テスト群がすべて合格することを確認する。

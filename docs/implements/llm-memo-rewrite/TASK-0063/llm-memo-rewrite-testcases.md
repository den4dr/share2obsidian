# TDDテストケース定義書: EditScreenViewModel Hilt化・rewriteBody()実装

**機能名**: llm-memo-rewrite（本文リライトUI）
**タスクID**: TASK-0063
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0063/llm-memo-rewrite-testcases.md`

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書・既存実装コードを参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測をしている
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測をしている

---

## 0. テスト戦略・観測点

- 🔵 **テスト対象**: `EditScreenViewModel`（`app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`）の `@HiltViewModel` 化後の `rewriteBody()` / `initialize()` / `errorEvents` / `EditFormState`（`isRewritingBody`, `rewriteBodyEnabled`）。
- 🔵 **DI注入のテスト方法**: `@HiltViewModel` はコンストラクタ `@Inject constructor(llmRewriteRepository, llmSettingsRepository)` で依存を受け取る。単体テストでは Hilt を起動せず、MockK でスタブした2リポジトリを**コンストラクタに直接渡して**インスタンス化する（`SettingsViewModelTest` / `TemplateEditViewModelTest` の既存パターンを踏襲）。
- 🔵 **観測点**:
  - `viewModel.formState.value.body` … リライト結果の反映（`Success`）
  - `viewModel.formState.value.isRewritingBody` … ローディング状態遷移
  - `viewModel.formState.value.rewriteBodyEnabled` … ボタン活性判定
  - `viewModel.errorEvents`（`SharedFlow<Int>`）… 失敗時の messageResId 発行
  - `coVerify { llmRewriteRepository.rewrite(settings, bodyLlmPrompt, content) }` … 入力ソースが `sourceContent` であること
- 🟡 **非同期制御**: `rewriteBody()` は `viewModelScope.launch` で `suspend` 呼び出し（`getSettings().first()`, `rewrite(...)`）を行う。テストは `kotlinx-coroutines-test` の `runTest` + `StandardTestDispatcher` + `Dispatchers.setMain/resetMain` + `advanceUntilIdle()` で制御する。`errorEvents`（Hot Flow / `MutableSharedFlow`、`replay=0`）は emit 前に購読を開始する必要があるため、`backgroundScope`（または `async`）で先行購読してから `rewriteBody()` を呼ぶ。
- 🔵 **信号値の定数**: 失敗種別の messageResId は `res/values/strings.xml` 実在リソース `R.string.error_llm_network` / `error_llm_auth` / `error_llm_timeout` / `error_llm_empty_response` / `error_llm_unknown` を使用する。

**参照**: note.md §5「テスト関連情報」, TASK-0063.md「単体テスト要件」, `EditScreenViewModelInitializeTest.kt`（既存パターン）, `LlmRewriteResult.kt`（Failure種別）, `strings.xml`（error_llm_*）

---

## 1. 正常系テストケース（基本的な動作）

### TC-0063-N01: rewriteBody() 成功時に formState.body が LLM応答テキストで上書きされる

- **テスト名**: rewriteBody 成功時 formState.body が LLM 応答テキストに更新される
  - **何をテストするか**: `llmRewriteRepository.rewrite(...)` が `LlmRewriteResult.Success` を返した場合に、`formState.body` がその応答テキストで即時上書きされること
  - **期待される動作**: 成功結果 `result.text` が `formState.value.body` に反映される（REQ-003 即時上書き）
- **入力値**:
  - `initialize(processed=body="初期本文", ..., sourceContent="元コンテンツ", bodyLlmPrompt="要約してください")`
  - `llmRewriteRepository.rewrite(any(), any(), any())` は `LlmRewriteResult.Success("書き換え後テキスト")` を返すようスタブ
  - `llmSettingsRepository.getSettings()` は `flowOf(LlmSettings(endpointUrl="https://api.example.com/v1/chat/completions", apiKey="sk-test", model="gpt-4o-mini"))` を返すようスタブ
  - **入力データの意味**: 標準的なプロンプト設定済み・接続設定済みの状態を代表する
- **期待される結果**: `viewModel.formState.value.body == "書き換え後テキスト"`
  - **期待結果の理由**: REQ-003「API応答で本文フィールドを即時上書き」の直接的な検証
- **テストの目的**: リライト成功時の本文上書きの正常フローを確認する
  - **確認ポイント**: `advanceUntilIdle()` 後に body が応答値に一致すること
- 🔵 信頼性レベル: TASK-0063.md テストケース1・REQ-003・requirements.md §2.3 より（推測なし）

### TC-0063-N02: initialize() 時 bodyLlmPrompt が非空なら rewriteBodyEnabled が true になる

- **テスト名**: initialize で bodyLlmPrompt 非空の場合 rewriteBodyEnabled が true
  - **何をテストするか**: `initialize()` で非空の `bodyLlmPrompt` を渡すと `formState.rewriteBodyEnabled` が `true` に算出されること
  - **期待される動作**: `rewriteBodyEnabled = bodyLlmPrompt.isNotBlank()` が `true` になる（REQ-102）
- **入力値**: `viewModel.initialize(processed, config, customFields=emptyList(), sourceContent="本文", bodyLlmPrompt="要約してください")`
  - **入力データの意味**: テンプレートに本文用プロンプトが設定済みのケースを代表する
- **期待される結果**: `viewModel.formState.value.rewriteBodyEnabled == true`
  - **期待結果の理由**: REQ-102「プロンプト設定時にボタン活性化」の検証。ボタンを押下可能にする前提条件
- **テストの目的**: 活性判定ロジックが正しく `bodyLlmPrompt` から算出されることを確認する
  - **確認ポイント**: `initialize()` 直後（非同期不要）に評価できること
- 🔵 信頼性レベル: TASK-0063.md テストケース4・REQ-102 より（推測なし）

### TC-0063-N03: rewriteBody() 実行中は isRewritingBody が true、完了後 false に戻る

- **テスト名**: rewriteBody 実行中 isRewritingBody が true になり完了後 false に戻る
  - **何をテストするか**: 非同期処理の開始から完了までのローディング状態遷移（`false → true → false`）
  - **期待される動作**: 呼び出し中は `isRewritingBody = true`、完了後は `false`（REQ-201）
- **入力値**:
  - `llmRewriteRepository.rewrite(...)` を `CompletableDeferred` などで完了タイミングを制御可能にスタブ（`coEvery { rewrite(...) } coAnswers { deferred.await() }`）
  - **入力データの意味**: レスポンス完了前と完了後の2時点を観測するために完了を外部制御する
- **期待される結果**:
  - 応答完了前（`advanceUntilIdle()` でスタブ手前まで進めた状態）: `isRewritingBody == true`
  - `deferred.complete(Success(...))` → `advanceUntilIdle()` 後: `isRewritingBody == false`
  - **期待結果の理由**: REQ-201「LLM呼び出し中のローディング表示」。UI がスピナー表示/非表示を切り替える根拠状態
- **テストの目的**: ローディング状態の on→off 遷移を確認する
  - **確認ポイント**: 完了前後で状態が明確に切り替わること。例外発生時（失敗時）も最終的に false に戻ること（TC-0063-E系でも間接確認）
- 🔵 信頼性レベル: TASK-0063.md テストケース6・REQ-201 より（推測なし）

### TC-0063-N04: rewriteBody() は sourceContent を入力とし formState.body を入力にしない

- **テスト名**: rewriteBody は sourceContent を LLM 入力に使い編集後 body を使わない
  - **何をテストするか**: ユーザーが `updateBody()` で本文を編集した後に `rewriteBody()` を呼んでも、`rewrite()` の `content` 引数が `sourceContent` であること
  - **期待される動作**: `rewrite(settings, bodyLlmPrompt, "元コンテンツ")` が呼ばれ、`"ユーザーが編集した本文"` は渡らない（REQ-002, REQ-406）
- **入力値**:
  - `initialize(..., sourceContent="元コンテンツ", bodyLlmPrompt="要約してください")`
  - `viewModel.updateBody("ユーザーが編集した本文")` で `formState.body` を変更
  - `rewrite(...)` は `Success("結果")` を返すようスタブ
  - **入力データの意味**: 編集前後で `sourceContent` と `formState.body` に確実な差分を作る
- **期待される結果**: `coVerify { llmRewriteRepository.rewrite(any(), "要約してください", "元コンテンツ") }` が成立し、`content="ユーザーが編集した本文"` では呼ばれない
  - **期待結果の理由**: REQ-406「元コンテンツをユーザー編集と独立して保持」の中核。入力ソースの一貫性保証
- **テストの目的**: 入力ソースが常に `sourceContent` であることを検証する（本タスクの最重要検証）
  - **確認ポイント**: `coVerify` で第3引数が `sourceContent` であること、第2引数が `bodyLlmPrompt` であること
- 🔵 信頼性レベル: TASK-0063.md テストケース7・REQ-002・REQ-406 より（推測なし）

### TC-0063-N05: MockK でスタブした2リポジトリをコンストラクタ注入して生成できる（Hilt互換）

- **テスト名**: コンストラクタ注入で EditScreenViewModel を生成でき既存 formState 初期化が回帰しない
  - **何をテストするか**: `@Inject constructor(llmRewriteRepository, llmSettingsRepository)` への変更後も、依存を渡してインスタンス化でき、既存の `initialize()`/`updateXxx()`/`buildSendParams()` が従来通り動作すること
  - **期待される動作**: `EditScreenViewModel(mockRewrite, mockSettings)` で生成でき、`initialize()` 後の `formState` 初期値（title/body/tags/folder/vault）が従来通り
- **入力値**: `EditScreenViewModel(mockLlmRewriteRepository, mockLlmSettingsRepository)` を生成し `initialize(processed=body="本文", title="T", config)` を呼ぶ
  - **入力データの意味**: Hilt化がコンストラクタシグネチャを変えても既存挙動を壊さないことの回帰確認
- **期待される結果**: `formState.value.body == "本文"`, `formState.value.title == "T"`, `formState.value.vault == config.vault` 等が従来通り
  - **期待結果の理由**: 完了条件「既存の単体テストがすべて通る」「MainActivity の by viewModels() がそのまま動作」の下支え
- **テストの目的**: Hilt化に伴うコンストラクタ変更が既存フォーム初期化を破壊しないことを確認する
  - **確認ポイント**: 依存注入版でも既存 API が回帰しないこと
- 🟡 信頼性レベル: 完了条件・requirements.md §3「既存テスト非破壊」より妥当な推測（`@HiltViewModel` アノテーション自体は単体テストで直接検証せず、コンストラクタ注入可能性で代替検証）

---

## 2. 異常系テストケース（エラーハンドリング）

> 共通スタブ: `initialize(..., sourceContent="元コンテンツ", bodyLlmPrompt="要約してください")` で初期化し、`formState.body` は初期本文（例: `"初期本文"`）のまま。`rewrite(...)` が各 `Failure` を返すようスタブする。`errorEvents` は先行購読しておく。

### TC-0063-E01: NetworkError 時 body 不変・errorEvents に error_llm_network を発行

- **テスト名**: rewriteBody が NetworkError を返すと body 不変で error_llm_network が emit される
  - **エラーケースの概要**: ネットワーク未接続・接続失敗（EDGE-001）
  - **エラー処理の重要性**: 失敗時に本文を破壊せず、ユーザーに再試行可能な状態を保つため
- **入力値**: `rewrite(...)` が `LlmRewriteResult.Failure.NetworkError(R.string.error_llm_network)` を返す
  - **不正な理由**: 実際の API 呼び出しが `IOException` 相当で失敗した状態をリポジトリが種別化した結果
  - **実際の発生シナリオ**: 機内モード・圏外・DNS解決失敗時の共有
- **期待される結果**: `formState.value.body` は `"初期本文"` のまま変更されず、`errorEvents` から `R.string.error_llm_network` が1件だけ発行される
  - **エラーメッセージの内容**: 「ネットワークに接続できませんでした」（`strings.xml`）
  - **システムの安全性**: 本文が壊れず、`isRewritingBody` も最終的に `false` に戻る
- **テストの目的**: NetworkError の errorEvents 発行と body 非破壊を確認する
  - **品質保証の観点**: 通信障害時の安全なフォールバック（NFR-201）
- 🔵 信頼性レベル: TASK-0063.md テストケース2・EDGE-001・NFR-201 より（推測なし）

### TC-0063-E02: AuthError 時 body 不変・errorEvents に error_llm_auth を発行

- **テスト名**: rewriteBody が AuthError を返すと body 不変で error_llm_auth が emit される
  - **エラーケースの概要**: APIキー不正・認証失敗（HTTP 401/403）（EDGE-002）
  - **エラー処理の重要性**: 認証失敗を明示し、設定見直しを促すため
- **入力値**: `rewrite(...)` が `LlmRewriteResult.Failure.AuthError(R.string.error_llm_auth)` を返す
  - **不正な理由**: 誤ったAPIキー・失効キーによる 401/403 応答をリポジトリが種別化した結果
  - **実際の発生シナリオ**: APIキー入力ミス・キーローテーション後の未更新
- **期待される結果**: `formState.value.body` 不変、`errorEvents` から `R.string.error_llm_auth`（「APIキーが正しくありません」）が1件発行
  - **システムの安全性**: 本文非破壊・`isRewritingBody=false` へ復帰
- **テストの目的**: AuthError の errorEvents 発行を確認する
  - **品質保証の観点**: 認証エラーの識別可能性（NFR-201）
- 🔵 信頼性レベル: TASK-0063.md テストケース2・EDGE-002・NFR-201 より（推測なし）

### TC-0063-E03: Timeout 時 body 不変・errorEvents に error_llm_timeout を発行

- **テスト名**: rewriteBody が Timeout を返すと body 不変で error_llm_timeout が emit される
  - **エラーケースの概要**: 30秒タイムアウト（EDGE-003, NFR-001）
  - **エラー処理の重要性**: 応答遅延時に無限待機させず、明示的にエラー化するため
- **入力値**: `rewrite(...)` が `LlmRewriteResult.Failure.Timeout(R.string.error_llm_timeout)` を返す
  - **不正な理由**: Ktor HttpClient の `requestTimeoutMillis=30_000` 超過を種別化した結果（ViewModel 側では明示タイムアウトを持たない）
  - **実際の発生シナリオ**: LLM サーバー高負荷・巨大入力での応答遅延
- **期待される結果**: `formState.value.body` 不変、`errorEvents` から `R.string.error_llm_timeout`（「LLMからの応答がタイムアウトしました」）が1件発行
- **テストの目的**: Timeout の errorEvents 発行を確認する
  - **品質保証の観点**: 応答遅延の安全なハンドリング（REQ-202, NFR-001）
- 🔵 信頼性レベル: TASK-0063.md テストケース2・EDGE-003・REQ-202・NFR-001 より（推測なし）

### TC-0063-E04: EmptyOrInvalidResponse 時 body 不変・errorEvents に error_llm_empty_response を発行

- **テスト名**: rewriteBody が EmptyOrInvalidResponse を返すと body 不変で error_llm_empty_response が emit される
  - **エラーケースの概要**: 空応答・パース不能なレスポンス（EDGE-004）
  - **エラー処理の重要性**: 不正レスポンスで本文を空にしたり壊したりしないため
- **入力値**: `rewrite(...)` が `LlmRewriteResult.Failure.EmptyOrInvalidResponse(R.string.error_llm_empty_response)` を返す
  - **不正な理由**: `choices` 空・JSON崩れ等をリポジトリが種別化した結果
  - **実際の発生シナリオ**: 非互換エンドポイント・モデル名誤り・レート制限による空応答
- **期待される結果**: `formState.value.body` 不変、`errorEvents` から `R.string.error_llm_empty_response`（「LLMから有効な応答が得られませんでした」）が1件発行
- **テストの目的**: EmptyOrInvalidResponse の errorEvents 発行を確認する
  - **品質保証の観点**: パース不能時の防御（NFR-201）
- 🔵 信頼性レベル: TASK-0063.md テストケース2・EDGE-004・NFR-201 より（`EmptyOrInvalidResponse` は `LlmRewriteResult.kt` に実在）

### TC-0063-E05: Unknown 時 body 不変・errorEvents に error_llm_unknown を発行

- **テスト名**: rewriteBody が Unknown を返すと body 不変で error_llm_unknown が emit される
  - **エラーケースの概要**: 上記以外の予期しないエラー
  - **エラー処理の重要性**: 想定外例外でもクラッシュせず汎用メッセージで通知するため
- **入力値**: `rewrite(...)` が `LlmRewriteResult.Failure.Unknown(R.string.error_llm_unknown)` を返す
  - **不正な理由**: 分類不能な例外を包括的に受け止める種別
  - **実際の発生シナリオ**: 予期しない HTTP ステータス・想定外の実行時例外
- **期待される結果**: `formState.value.body` 不変、`errorEvents` から `R.string.error_llm_unknown`（「予期しないエラーが発生しました」）が1件発行
- **テストの目的**: Unknown の errorEvents 発行を確認し、全 Failure 種別の網羅を完成させる
  - **品質保証の観点**: フォールバックエラー処理の存在保証（NFR-201）
- 🔵 信頼性レベル: TASK-0063.md テストケース2・NFR-201 より（`Unknown` は `LlmRewriteResult.kt` に実在）

---

## 3. 境界値テストケース（最小値、最大値、null等）

### TC-0063-B01: initialize() 時 bodyLlmPrompt が空文字なら rewriteBodyEnabled が false

- **テスト名**: initialize で bodyLlmPrompt 空文字の場合 rewriteBodyEnabled が false
  - **境界値の意味**: `bodyLlmPrompt.isNotBlank()` の境界（空文字 `""`）。ボタン非活性の判定境界
  - **境界値での動作保証**: 空文字・空白のみは「プロンプトなし」として一貫して非活性化される
- **入力値**: `viewModel.initialize(processed, config, customFields=emptyList(), sourceContent="本文", bodyLlmPrompt="")`
  - **境界値選択の根拠**: テンプレート未設定・プロンプト未入力時の代表境界（REQ-102）
  - **実際の使用場面**: 初回インストール直後や、プロンプトを設定していないテンプレートでの共有
- **期待される結果**: `viewModel.formState.value.rewriteBodyEnabled == false`
  - **境界での正確性**: `isNotBlank()` が `""` に対し `false` を返すこと
  - **一貫した動作**: 非空（TC-0063-N02）と対称的に false になること
- **テストの目的**: プロンプト未設定時のボタン非活性化境界を確認する
  - **堅牢性の確認**: 空文字入力で誤って活性化しないこと
- 🔵 信頼性レベル: TASK-0063.md テストケース3・REQ-102 より（推測なし）

### TC-0063-B02: sourceContent が空文字でも rewriteBody() はガードせず rewrite を呼ぶ（EDGE-101）

- **テスト名**: sourceContent 空文字でも rewriteBody がガードされず rewrite が実行される
  - **境界値の意味**: `sourceContent` の最小値（空文字 `""`）。EDGE-101「空文字でもガードしない」の境界
  - **境界値での動作保証**: 空文字でも早期 return せず、そのまま LLM 呼び出しに渡す
- **入力値**:
  - `initialize(..., sourceContent="", bodyLlmPrompt="要約してください")`
  - `rewrite(...)` が `Success("結果")` を返すようスタブ
  - **境界値選択の根拠**: 本文なし共有でも `bodyLlmPrompt` があればボタンは活性であり、空文字がそのまま送信される（EDGE-101）
  - **実際の使用場面**: タイトルのみ・本文空の共有をLLM対象にするケース
- **期待される結果**: `coVerify { llmRewriteRepository.rewrite(any(), "要約してください", "") }` が成立し（早期returnされない）、`formState.value.body == "結果"` に更新される
  - **境界での正確性**: 第3引数が空文字 `""` で実際に呼ばれること
  - **一貫した動作**: 空文字を「無効」として弾かず、非空 sourceContent と同じフローを通ること
- **テストの目的**: 空文字ガード禁止（EDGE-101）を確認する
  - **堅牢性の確認**: 空入力でスキップ・クラッシュせず正常フローに乗ること
- 🔵 信頼性レベル: TASK-0063.md テストケース5・EDGE-101・`LlmRewriteRepository.kt`（EDGE-101 明記）より（推測なし）

### TC-0063-B03: initialize() 2回目呼び出しは既存 sourceContent/bodyLlmPrompt/formState を上書きしない（EDGE-101 重複初期化防止）

- **テスト名**: 画面回転想定の再 initialize で sourceContent bodyLlmPrompt formState が上書きされない
  - **境界値の意味**: `initialized` フラグの境界（1回目 → 2回目）。画面回転時の重複初期化防止
  - **境界値での動作保証**: 2回目以降の `initialize()` は早期 return し、既存状態を保持する
- **入力値**:
  - 1回目: `initialize(..., sourceContent="元コンテンツ", bodyLlmPrompt="要約してください")`
  - 2回目: `initialize(..., sourceContent="別コンテンツ", bodyLlmPrompt="別プロンプト")`
  - **境界値選択の根拠**: 画面回転で Activity 再作成 → ViewModel 生存 → initialize 再呼び出しのケース（EDGE-101）
  - **実際の使用場面**: 編集中に端末を回転させたとき
- **期待される結果**: `viewModel.sourceContent == "元コンテンツ"`, `viewModel.bodyLlmPrompt == "要約してください"`, `rewriteBodyEnabled` は1回目の値を維持
  - **境界での正確性**: 2回目の引数が反映されないこと
  - **一貫した動作**: 既存 `initialized` フラグ挙動が本タスク変更後も維持されること
- **テストの目的**: 重複初期化防止が Hilt化・引数追加後も維持されることを確認する
  - **堅牢性の確認**: 回転時にユーザー編集内容・入力ソースが失われないこと
- 🔵 信頼性レベル: EDGE-101・requirements.md §3「重複初期化防止」・既存 `EditScreenViewModel.kt`（`initialized` フラグ）より（推測なし）

### TC-0063-B04: rewriteBody() 成功結果が空文字の場合でも formState.body が空文字で上書きされる

- **テスト名**: rewriteBody 成功結果が空文字なら body が空文字で上書きされる
  - **境界値の意味**: `LlmRewriteResult.Success` の `text` が空文字 `""`（`LlmRewriteResult.kt` が空文字を許容: TC-B-04）
  - **境界値での動作保証**: 成功であれば空文字応答でも `body` に反映する（Failure との区別）
- **入力値**:
  - `initialize(processed=body="初期本文", ..., sourceContent="元コンテンツ", bodyLlmPrompt="要約してください")`
  - `rewrite(...)` が `LlmRewriteResult.Success("")` を返すようスタブ
  - **境界値選択の根拠**: 成功だが空応答という下限境界。`Success("")` と `Failure.EmptyOrInvalidResponse` の扱いの違いを固定する
  - **実際の使用場面**: LLM が空文字を正規レスポンスとして返した稀なケース
- **期待される結果**: `formState.value.body == ""`（初期本文 `"初期本文"` から空文字に上書き）、`errorEvents` には何も発行されない
  - **境界での正確性**: `Success` は空文字でも body へ反映され、エラー扱いしないこと
  - **一貫した動作**: `Success` 分岐は text 内容に関わらず body 上書きすること
- **テストの目的**: 成功・空文字応答の扱い（body上書き・非エラー）を確認する
  - **堅牢性の確認**: 空応答成功と失敗種別の混同がないこと
- 🟡 信頼性レベル: `LlmRewriteResult.kt`「Success は空文字も許容（TC-B-04）」・REQ-003（即時上書き）より妥当な推測（ViewModel が空文字成功を特別扱いしないという方針の確認）

---

## 4. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: 既存プロジェクト全体が Kotlin。ViewModel・Coroutines・Flow を用いた実装対象と一致
  - **テストに適した機能**: バッククォート日本語テストメソッド名、`sealed class` の網羅的検証、コルーチンの構造化並行性
- **テストフレームワーク**: JUnit 4 + Robolectric + MockK + kotlinx-coroutines-test
  - **フレームワーク選択の理由**: note.md §5 の既存テストスタックに準拠。`R.string.*` リソースID参照のため Robolectric、リポジトリのスタブに MockK、`viewModelScope` 制御に coroutines-test を使用
  - **テスト実行環境**: ローカル JVM（`mise exec -- ./gradlew test`）。テストクラスは `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk=[34])`
- **想定テストクラス**: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelRewriteBodyTest.kt`（新規）
- 🔵 信頼性レベル: note.md §5・§2「テスト命名規約」・既存 `EditScreenViewModelInitializeTest.kt` より（推測なし）

---

## 5. テストケース実装時の日本語コメント指針

### テストクラス冒頭（Robolectric + Coroutines 設定）

```kotlin
// 【テストクラス目的】: TASK-0063 EditScreenViewModel.rewriteBody()/initialize()/errorEvents の検証
// 【テスト戦略】: Hilt を起動せず MockK でスタブした2リポジトリをコンストラクタ注入する
// 🔵 信頼性レベル: note.md §5・既存 EditScreenViewModelInitializeTest.kt より
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditScreenViewModelRewriteBodyTest {

    private val mockRewrite = mockk<LlmRewriteRepository>()
    private val mockSettings = mockk<LlmSettingsRepository>()

    @Before
    fun setUp() {
        // 【テスト前準備】: viewModelScope が使用する Main dispatcher をテスト用に差し替える
        // 【環境初期化】: StandardTestDispatcher で launch の実行タイミングを制御可能にする
        Dispatchers.setMain(StandardTestDispatcher())
        // 【共通スタブ】: 設定取得は既定で有効な LlmSettings を返す
        coEvery { mockSettings.getSettings() } returns flowOf(
            LlmSettings(endpointUrl = "https://api.example.com/v1/chat/completions", apiKey = "sk-test", model = "gpt-4o-mini")
        )
    }

    @After
    fun tearDown() {
        // 【テスト後処理】: 差し替えた Main dispatcher を元に戻す
        // 【状態復元】: 後続テストへの dispatcher 汚染を防ぐ
        Dispatchers.resetMain()
    }
}
```

### Given（準備フェーズ）のコメント例

```kotlin
// 【テストデータ準備】: 成功応答を返すスタブと、編集前の初期本文で初期化する
// 【初期条件設定】: sourceContent と formState.body に差分を作り、入力ソースを判別可能にする
// 【前提条件確認】: bodyLlmPrompt が非空でボタン活性（rewriteBodyEnabled=true）であること
coEvery { mockRewrite.rewrite(any(), any(), any()) } returns LlmRewriteResult.Success("書き換え後テキスト")
val viewModel = EditScreenViewModel(mockRewrite, mockSettings)
viewModel.initialize(processed, config, emptyList(), sourceContent = "元コンテンツ", bodyLlmPrompt = "要約してください")
```

### When（実行フェーズ）のコメント例

```kotlin
// 【実際の処理実行】: rewriteBody() を呼び出し、viewModelScope の非同期処理を進める
// 【処理内容】: isRewritingBody=true → getSettings().first() → rewrite() → body上書き → isRewritingBody=false
// 【実行タイミング】: advanceUntilIdle() で launch 内の全 suspend が完了するまで進める
viewModel.rewriteBody()
advanceUntilIdle()
```

### errorEvents 検証時の先行購読コメント例

```kotlin
// 【イベント購読準備】: errorEvents は replay=0 の Hot Flow のため emit 前に購読を開始する
// 【前提条件確認】: backgroundScope で collect を開始してから rewriteBody() を呼ぶ
val received = mutableListOf<Int>()
backgroundScope.launch { viewModel.errorEvents.collect { received.add(it) } }
```

### Then（検証フェーズ）のコメント例

```kotlin
// 【結果検証】: body が応答テキストで上書きされたことを確認する
// 【期待値確認】: REQ-003 の即時上書き。result.text が formState.body に反映される
// 【品質保証】: リライト成功の中核挙動を固定し UI が正しい本文を表示できることを保証する
assertEquals("書き換え後テキスト", viewModel.formState.value.body) // 【検証項目】: 成功時の body 上書き 🔵

// 【検証項目】: 入力ソースが sourceContent（編集後 body ではない）であること
coVerify { mockRewrite.rewrite(any(), "要約してください", "元コンテンツ") } // 🔵
```

---

## 6. 要件定義との対応関係

- **参照した機能概要**: requirements.md §1（Hilt化・sourceContent/bodyLlmPrompt保持・rewriteBody()・errorEvents）
- **参照した入力・出力仕様**: requirements.md §2.1〜2.6（コンストラクタ / initialize() / rewriteBody() / errorEvents / EditFormState / データフロー）
- **参照した制約条件**: requirements.md §3（入力ソース制約 REQ-002/REQ-406, 空文字ガード禁止 EDGE-101, ローディング REQ-201, タイムアウト REQ-202/NFR-001, エラー表示 NFR-201, 重複初期化防止 EDGE-101, Hilt互換, 既存テスト非破壊）
- **参照した使用例**: requirements.md §4.1〜4.6（基本 / プロンプト未設定 / エラー各種 / 空文字 / ユーザー編集後 / ローディング遷移）

### EARS要件・タスクテストケースとの対応表

| テストケース | 対応 TASK-0063 TC | 対応 EARS要件 | 信頼性 |
|-------------|------------------|--------------|--------|
| TC-0063-N01 | TC-1 | REQ-003 | 🔵 |
| TC-0063-N02 | TC-4 | REQ-102 | 🔵 |
| TC-0063-N03 | TC-6 | REQ-201, NFR-202 | 🔵 |
| TC-0063-N04 | TC-7 | REQ-002, REQ-406 | 🔵 |
| TC-0063-N05 | （完了条件: Hilt互換・既存テスト非破壊） | — | 🟡 |
| TC-0063-E01 | TC-2 | EDGE-001, NFR-201 | 🔵 |
| TC-0063-E02 | TC-2 | EDGE-002, NFR-201 | 🔵 |
| TC-0063-E03 | TC-2 | EDGE-003, REQ-202, NFR-001 | 🔵 |
| TC-0063-E04 | TC-2 | EDGE-004, NFR-201 | 🔵 |
| TC-0063-E05 | TC-2 | NFR-201 | 🔵 |
| TC-0063-B01 | TC-3 | REQ-102 | 🔵 |
| TC-0063-B02 | TC-5 | EDGE-101 | 🔵 |
| TC-0063-B03 | （EDGE-101 重複初期化防止の回帰） | EDGE-101 | 🔵 |
| TC-0063-B04 | （Success 空文字境界） | REQ-003 | 🟡 |

---

## 品質判定

```
✅ 高品質:
- テストケース分類: 正常系5・異常系5・境界値4 の計14ケースで網羅（成功/各Failure種別/活性判定/空文字/入力ソース/重複初期化/空文字成功）
- 期待値定義: 各ケースの入力値・期待値（body値・rewriteBodyEnabled・errorEvents resId・coVerify引数）が具体的に確定
- 技術選択: Kotlin + JUnit4/Robolectric/MockK/coroutines-test が既存スタックとして確定
- 実装可能性: 依存リポジトリ・Result型・エラーリソース・既存テストパターンすべて実在確認済み
- 信頼性レベル: 🔵 12件 / 🟡 2件 / 🔴 0件（🔵が大半）
```

### 信頼性レベル分布

| 信号 | 件数 | 主な項目 |
|------|------|---------|
| 🔵 青 | 12 | N01〜N04, E01〜E05, B01〜B03（TASK-0063 TC-1〜7 と EARS要件に直接対応） |
| 🟡 黄 | 2 | N05（Hiltアノテーション自体はコンストラクタ注入で代替検証）, B04（Success空文字の扱いの推測） |
| 🔴 赤 | 0 | なし |

**総合評価**: ✅ 高品質。TASK-0063.md の単体テスト要件 TC-1〜TC-7 を全て包含し、Failure 5種別への分割・重複初期化防止・空文字成功の境界を追加して網羅性を高めた。参照した型・リソース・既存テストパターンはすべて実装コードで裏付け済み。

---

**作成日**: 2026-07-06 by tsumiki:tdd-testcases
**次フェーズ**: `/tsumiki:tdd-red llm-memo-rewrite TASK-0063`（失敗テスト作成）

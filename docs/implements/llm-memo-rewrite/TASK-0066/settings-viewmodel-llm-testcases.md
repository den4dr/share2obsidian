# TASK-0066 TDDテストケース定義書: SettingsViewModel LLM設定対応

**機能名**: SettingsViewModel LLM設定対応 (settings-viewmodel-llm)
**タスクID**: TASK-0066
**要件名**: llm-memo-rewrite
**フェーズ**: Phase 4 - LLM設定UI
**作成日**: 2026-07-06

---

## 信頼性レベル凡例

- 🔵 **青信号**: 要件定義書・タスク定義・既存実装を参照し、ほぼ推測していない
- 🟡 **黄信号**: 要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: 元の資料にない推測

---

## 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: 対象クラス `SettingsViewModel` が Kotlin 実装であり、プロジェクト全体が Kotlin 単一言語で統一されているため。
  - **テストに適した機能**: バッククォート関数名による説明的テスト名、`data class` の構造的等価比較（`assertEquals` で `SettingsUiState` 全体を一括検証可能）、Coroutines/Flow のテスト支援。
- **テストフレームワーク**: JUnit 4 + kotlinx-coroutines-test + MockK
  - **フレームワーク選択の理由**: 既存 `SettingsViewModelTest.kt` が同構成であり、`note.md` テスト関連情報・要件定義書 §3 技術スタック制約で確定済み。新規フレームワーク導入は不要。
  - **テスト実行環境**: ローカル JVM（デバイス/エミュレータ不要）。`mise exec -- ./gradlew test` で実行。`UnconfinedTestDispatcher` + `Dispatchers.setMain/resetMain` で `viewModelScope`（Main）を制御。
- 🔵 信頼性レベル: `note.md`「5.テスト関連情報」・既存 `SettingsViewModelTest.kt`・`app/build.gradle.kts` 依存定義より。

**テスト対象ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`
**テストコード配置**: `app/src/test/java/com/den4dr/share2Obsidian/ui/SettingsViewModelTest.kt`（既存ファイルに追記）

---

## 1. 正常系テストケース（基本的な動作）

### TC-N-01: updateLlmEndpointUrl() が saveEndpointUrl() を呼び出す

- **テスト名**: updateLlmEndpointUrl はエンドポイントURLを Repository に保存する
  - **何をテストするか**: `updateLlmEndpointUrl(url)` が `llmSettingsRepository.saveEndpointUrl(url)` を同じ引数で1回だけ呼び出すこと。
  - **期待される動作**: 引数の URL が変換されずそのまま save 系関数に委譲される。
- **入力値**: `viewModel.updateLlmEndpointUrl("https://example.com/v1/chat/completions")`
  - **入力データの意味**: OpenAI互換 Chat Completions エンドポイントの代表的な実 URL。要件 2.2 の「引数をそのまま save に渡す」を検証する代表値。
- **期待される結果**: `coVerify(exactly = 1) { llmSettingsRepository.saveEndpointUrl("https://example.com/v1/chat/completions") }` が成立。
  - **期待結果の理由**: 完了条件「updateLlmEndpointUrl が saveEndpointUrl を呼び出す」を満たすため。バリデーション・変換は本タスク範囲外のため引数一致で検証する。
- **テストの目的**: update 関数から Repository への委譲経路（ViewModel の書き込み窓口）を確認する。
  - **確認ポイント**: 呼び出し回数がちょうど1回であること、引数が改変されていないこと、`Dispatchers.IO` 上の launch が `advanceUntilIdle()` で完了すること。
- 🔵 信頼性レベル: TASK-0066.md テストケース1・完了条件・既存 `updateVault_savesToRepository` パターンより。

### TC-N-02: updateLlmApiKey() が saveApiKey() を呼び出す

- **テスト名**: updateLlmApiKey は APIキーを Repository に保存する
  - **何をテストするか**: `updateLlmApiKey(apiKey)` が `llmSettingsRepository.saveApiKey(apiKey)` を同じ引数で1回だけ呼び出すこと。
  - **期待される動作**: APIキー文字列が ViewModel で恒久保持されず、そのまま save 系へ委譲される（暗号化は Repository 側責務）。
- **入力値**: `viewModel.updateLlmApiKey("sk-xxxx")`
  - **入力データの意味**: OpenAI 形式 APIキーの代表値。機微情報（REQ-401 暗号化保存対象）だがテストでは委譲経路のみ検証する。
- **期待される結果**: `coVerify(exactly = 1) { llmSettingsRepository.saveApiKey("sk-xxxx") }` が成立。
  - **期待結果の理由**: 完了条件「updateLlmApiKey が saveApiKey を呼び出す」を満たすため。
- **テストの目的**: 機微情報の書き込み委譲が正しく行われることを確認する。
  - **確認ポイント**: ViewModel が APIキーを平文フィールドとして保持していないこと（委譲のみ）。呼び出し1回・引数一致。
- 🔵 信頼性レベル: TASK-0066.md テストケース2・完了条件・REQ-401より。

### TC-N-03: updateLlmModel() が saveModel() を呼び出す

- **テスト名**: updateLlmModel はモデル名を Repository に保存する
  - **何をテストするか**: `updateLlmModel(model)` が `llmSettingsRepository.saveModel(model)` を同じ引数で1回だけ呼び出すこと。
  - **期待される動作**: モデル名がそのまま save 系へ委譲される。
- **入力値**: `viewModel.updateLlmModel("gpt-4o-mini")`
  - **入力データの意味**: 実在するモデル名の代表値。
- **期待される結果**: `coVerify(exactly = 1) { llmSettingsRepository.saveModel("gpt-4o-mini") }` が成立。
  - **期待結果の理由**: 完了条件「updateLlmModel が saveModel を呼び出す」を満たすため。
- **テストの目的**: 3つ目の update 関数の委譲経路を確認する。
  - **確認ポイント**: 呼び出し1回・引数一致。
- 🔵 信頼性レベル: TASK-0066.md テストケース3・完了条件より。

### TC-N-04: uiState が note/llm 両 Repository の値を反映して構築される

- **テスト名**: uiState は combine で note と llm 両方の設定を集約する
  - **何をテストするか**: `combine(noteSettingsRepository.getSettings(), llmSettingsRepository.getSettings())` により、両 Repository の値が単一の `SettingsUiState` に集約されること。
  - **期待される動作**: vault/folder（Note由来）と llmEndpointUrl/llmApiKey/llmModel（Llm由来）が同一状態オブジェクトへマッピングされる。
- **入力値**:
  - `noteSettingsRepository.getSettings()` → `flowOf(NoteSettings(vault = "MyVault", folder = "Inbox"))`
  - `llmSettingsRepository.getSettings()` → `flowOf(LlmSettings(endpointUrl = "https://example.com", apiKey = "key", model = "gpt-4o"))`
  - **入力データの意味**: 2つの独立した Flow が異なる値を持つ状態。combine の集約が正しくフィールドを対応付けるかを判別できる代表値。
- **期待される結果**: 収集された最終状態が
  `SettingsUiState(vault = "MyVault", folder = "Inbox", llmEndpointUrl = "https://example.com", llmApiKey = "key", llmModel = "gpt-4o")` と `assertEquals` で一致。
  - **期待結果の理由**: 完了条件「uiState は combine() して構築される」および要件 2.4/2.5 のデータフローに一致するため。フィールド取り違え（例: model と endpointUrl の逆マッピング）を検出できる。
- **テストの目的**: 読み取り経路（2つの Flow → 1つの StateFlow）のマッピング正当性を確認する。
  - **確認ポイント**: `stateIn` で StateFlow 化されていること、`collect` で少なくとも実値が1回流れること、5フィールドすべてが正しいソースにマッピングされていること。
- 🔵 信頼性レベル: TASK-0066.md テストケース4・完了条件・要件 2.5 データフローより。

### TC-N-05: 既存 vault/folder 設定が引き続き uiState に反映される（後方互換）

- **テスト名**: llmSettingsRepository 追加後も vault/folder が uiState に反映される
  - **何をテストするか**: コンストラクタ拡張（2引数化）後も、既存の vault/folder 読み取り機能が破壊されていないこと。
  - **期待される動作**: 既存機能（REQ-021）が維持される。
- **入力値**: note=`NoteSettings(vault = "V1", folder = "F1")`, llm=`LlmSettings()`（全 ""）
  - **入力データの意味**: LLM 未設定でも Note 設定が独立して反映されることを確認する組み合わせ。
- **期待される結果**: `uiState.vault == "V1"` かつ `uiState.folder == "F1"`。LLM 3項目は "" のまま。
  - **期待結果の理由**: 制約条件「後方互換性（REQ-021）: 既存 vault/folder 機能を破壊しない」を満たすため。
- **テストの目的**: フィールド追加が既存挙動へ副作用を与えないことを確認する。
  - **確認ポイント**: 既存テスト（`uiState_reflectsRepositorySettings`）が2引数化の修正後も通ること（Redフェーズで既存呼び出しを2引数化）。
- 🟡 信頼性レベル: 要件 §3 後方互換制約・要件 §6 補足より妥当な推測（明示テストは既存TCの延長）。

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-E-01: save 系の例外ハンドリングは本タスク範囲外（テスト非対象）

- **テスト名**: Repository save 例外時の挙動（範囲外・実装しない）
  - **エラーケースの概要**: `saveEndpointUrl` 等が保存中に例外（IO 例外・暗号化失敗等）を投げるケース。
  - **エラー処理の重要性**: 本来は保存失敗のユーザー通知が望ましいが、TASK-0066 の完了条件・タスク定義に例外ハンドリング要件の記載がない。
- **入力値**: （実装しない）
  - **不正な理由**: 該当なし。
  - **実際の発生シナリオ**: ストレージ書き込み失敗、EncryptedSharedPreferences のキー破損等。
- **期待される結果**: 本タスクでは try/catch を追加せず、既存 `updateVault`/`updateFolder` と同様に例外はコルーチンへ伝播する挙動を維持する。**この振る舞いを固定するテストは追加しない**（設計文書に根拠なし）。
  - **エラーメッセージの内容**: 該当なし。
  - **システムの安全性**: `viewModelScope.launch(Dispatchers.IO)` 内の例外はアプリ全体をクラッシュさせず該当コルーチンに限定される（既存パターン踏襲）。
- **テストの目的**: 範囲を明示し、過剰実装（YAGNI 違反）を防ぐ。
  - **品質保証の観点**: 仕様外のエラーハンドリングを勝手に実装しないことで、要件との整合性を保つ。
- 🔴 信頼性レベル: 要件 §4.4 エラーケース（設計文書に根拠なし・本タスク範囲外）より。後続タスクで扱う場合に別途テスト化する。

### TC-E-02: 既存テストの2引数コンストラクタ移行（回帰防止）

- **テスト名**: 既存 SettingsViewModelTest が2引数コンストラクタで引き続き成功する
  - **エラーケースの概要**: コンストラクタに `llmSettingsRepository` を追加したことで、既存テストの `SettingsViewModel(repo)`（1引数）がコンパイルエラーになる。
  - **エラー処理の重要性**: 破壊的変更の検出漏れを防ぐ。ビルド失敗＝完了条件 `./gradlew test 成功` を満たせない。
- **入力値**: 既存2テスト（`uiState_reflectsRepositorySettings`, `updateVault_savesToRepository`）の `SettingsViewModel(repo)` を `SettingsViewModel(noteRepo, llmRepo)` へ修正。`llmRepo.getSettings()` は `flowOf(LlmSettings())` を返すモック。
  - **不正な理由**: 1引数呼び出しは新シグネチャに適合しない。
  - **実際の発生シナリオ**: Redフェーズでのシグネチャ変更時に必ず発生。
- **期待される結果**: 修正後、既存2テストがコンパイル・実行ともに成功する。
  - **エラーメッセージの内容**: 修正前は「No value passed for parameter 'llmSettingsRepository'」等のコンパイルエラー。
  - **システムの安全性**: 既存機能の回帰がないことをテストスイート全体の成功で担保する。
- **テストの目的**: シグネチャ変更に伴う既存テストの追随を確実にする。
  - **品質保証の観点**: 後方互換性（REQ-021）の実証。
- 🟡 信頼性レベル: 要件 §6 補足「既存テストの2引数化が必要」より妥当な推測。

---

## 3. 境界値テストケース（空文字・初期状態・連続更新）

### TC-B-01: 空文字入力を変換せずそのまま保存する

- **テスト名**: updateLlmEndpointUrl("") は空文字をそのまま saveEndpointUrl に渡す
  - **境界値の意味**: 「未設定・クリア操作」を空文字 "" で表現する仕様（要件 §4.3）の境界。空文字は最小長入力かつ「値なし」の代表。
  - **境界値での動作保証**: 空文字でもバリデーションで弾かず、そのまま save へ委譲されること。
- **入力値**: `viewModel.updateLlmApiKey("")`（代表として apiKey。endpointUrl/model も同様の想定）
  - **境界値選択の根拠**: 要件 §2.2「空文字許容（未設定を "" で表現）」の下限境界。
  - **実際の使用場面**: ユーザーが入力欄を全消去してクリアする操作。
- **期待される結果**: `coVerify(exactly = 1) { llmSettingsRepository.saveApiKey("") }` が成立。例外・スキップが発生しない。
  - **境界での正確性**: 空文字が非空文字と同じ委譲経路を通ること。
  - **一貫した動作**: 空文字（境界内）と通常文字列（境界外）で分岐処理が入らないこと。
- **テストの目的**: 空文字クリア操作が保存として成立することを確認する。
  - **堅牢性の確認**: 空入力で NPE や early-return が発生しないこと。
- 🟡 信頼性レベル: 要件 §4.3 空文字入力エッジケース（妥当な推測）より。

### TC-B-02: 初期未保存状態では uiState の LLM 3項目が "" になる

- **テスト名**: 両 Repository がデフォルト値を返すとき uiState は全項目空文字
  - **境界値の意味**: アプリ初回起動・未設定時の状態。StateFlow 初期値 `SettingsUiState()`（全 ""）との整合性境界。
  - **境界値での動作保証**: 実値が流れた後も未設定項目が "" のまま保たれること。
- **入力値**: note=`NoteSettings()`（vault="", folder=""）, llm=`LlmSettings()`（endpointUrl="", apiKey="", model=""）
  - **境界値選択の根拠**: 要件 §4.3「初期状態（未保存）」。全フィールド空の最小状態。
  - **実際の使用場面**: インストール直後、いずれの設定も保存前。
- **期待される結果**: `uiState` の最終値が `SettingsUiState()`（全フィールド ""）と `assertEquals` で一致。
  - **境界での正確性**: combine が空値を欠損なくマッピングすること。
  - **一貫した動作**: 初期値 `SettingsUiState()` と実 emit 後の値が一致し、意図しない差分が出ないこと。
- **テストの目的**: 未設定状態の安全な既定挙動（null 非使用）を確認する。
  - **堅牢性の確認**: 空 Flow 値でも例外なく StateFlow が構築されること。
- 🟡 信頼性レベル: 要件 §4.3 初期状態エッジケース・`LlmSettings` デフォルト値定義より妥当な推測。

### TC-B-03: 同一項目の連続更新で save が呼び出し回数分だけ実行される

- **テスト名**: updateLlmModel を2回連続呼び出すと saveModel が2回呼ばれる
  - **境界値の意味**: 短時間の連続更新（デバウンスなし）の境界。各呼び出しが独立に launch される仕様の確認。
  - **境界値での動作保証**: 呼び出し回数と save 実行回数が一致し、間引き・重複が起きないこと。
- **入力値**: `viewModel.updateLlmModel("gpt-4o")` の後に `viewModel.updateLlmModel("gpt-4o-mini")`
  - **境界値選択の根拠**: 要件 §4.3「連続更新」。1回（下限）と複数回の境界を跨ぐ。
  - **実際の使用場面**: ユーザーが素早くモデル名を打ち替える／連続タップする操作。
- **期待される結果**: `coVerify(exactly = 2) { llmSettingsRepository.saveModel(any()) }`、かつ最後の引数 `"gpt-4o-mini"` が保存されること。
  - **境界での正確性**: 呼び出しごとに独立した `launch(Dispatchers.IO)` が発行されること。
  - **一貫した動作**: 1回呼び出し（TC-N-03）と複数回で同一の委譲ロジックが機能すること。
- **テストの目的**: 即時保存（保存ボタンなし）方式が連続入力でも破綻しないことを確認する。
  - **堅牢性の確認**: 連続 launch で状態競合・例外が発生しないこと。
- 🟡 信頼性レベル: 要件 §4.3 連続更新エッジケース（既存パターン踏襲の推測）より。

---

## 4. テストケース実装時の日本語コメント指針

各テストメソッドは既存 `SettingsViewModelTest.kt` の AAA（Arrange-Act-Assert / Given-When-Then）構成を踏襲し、以下のコメントを付与する。

### テストメソッド開始時（例: TC-N-01）

```kotlin
// 【テスト目的】: updateLlmEndpointUrl が saveEndpointUrl へ引数をそのまま委譲することを確認する
// 【テスト内容】: URL を1つ渡し、Repository の save が同一引数で1回呼ばれるか検証する
// 【期待される動作】: llmSettingsRepository.saveEndpointUrl(url) が exactly=1 で呼び出される
// 🔵 信頼性レベル: TASK-0066.md TC-1・既存 updateVault テストパターンより
@Test
fun updateLlmEndpointUrl_savesToRepository() = runTest {
    // 【テストデータ準備】: note/llm 双方をモック化。getSettings はデフォルト値を返す
    // 【初期条件設定】: save 系は relaxed もしくは coEvery { ... } just Runs で副作用を無効化
    // 【前提条件確認】: Dispatchers.setMain(dispatcher) 済（@Before）
    val noteRepo = mockk<NoteSettingsRepository>(relaxed = true)
    every { noteRepo.getSettings() } returns flowOf(NoteSettings())
    val llmRepo = mockk<LlmSettingsRepository>(relaxed = true)
    every { llmRepo.getSettings() } returns flowOf(LlmSettings())
    val viewModel = SettingsViewModel(noteRepo, llmRepo)

    // 【実際の処理実行】: update 系関数を呼び出す
    // 【処理内容】: viewModelScope.launch(Dispatchers.IO) で save 系が実行される
    // 【実行タイミング】: advanceUntilIdle() で IO コルーチンの完了を待つ
    viewModel.updateLlmEndpointUrl("https://example.com/v1/chat/completions")
    advanceUntilIdle()

    // 【結果検証】: save が正しい引数で1回呼ばれたか確認
    // 【期待値確認】: 引数一致・回数1回
    // 【品質保証】: ViewModel→Repository の書き込み委譲経路の正当性を担保
    // 【検証項目】: saveEndpointUrl が exactly=1 で呼ばれること
    // 🔵 信頼性レベル: TASK-0066.md TC-1より
    coVerify(exactly = 1, timeout = 2000) {
        llmRepo.saveEndpointUrl("https://example.com/v1/chat/completions")
    }
}
```

### uiState 検証（例: TC-N-04）

```kotlin
// 【結果検証】: combine による集約後の最終状態を data class 一括比較で検証
// 【期待値確認】: 5フィールドすべてが正しいソースにマッピングされている
// 【検証項目】: SettingsUiState 全体の構造的等価性
// 🔵 信頼性レベル: TASK-0066.md TC-4・要件 2.5 データフローより
val collected = mutableListOf<SettingsUiState>()
val job = launch(dispatcher) { viewModel.uiState.collect { collected.add(it) } }
advanceUntilIdle()
job.cancel()
assertEquals(
    SettingsUiState(
        vault = "MyVault", folder = "Inbox",
        llmEndpointUrl = "https://example.com", llmApiKey = "key", llmModel = "gpt-4o",
    ),
    collected.last(),
)
```

### セットアップ・クリーンアップ（既存踏襲）

```kotlin
@Before
fun setUp() {
    // 【テスト前準備】: viewModelScope の Main ディスパッチャをテスト用に差し替える
    // 【環境初期化】: UnconfinedTestDispatcher で launch を即時実行可能にする
    Dispatchers.setMain(dispatcher)
}

@After
fun tearDown() {
    // 【テスト後処理】: Main ディスパッチャを元に戻す
    // 【状態復元】: 次テストへの影響とメモリリークを防ぐ（必須）
    Dispatchers.resetMain()
}
```

---

## 5. 要件定義との対応関係

- **参照した機能概要**: 要件定義書 §1（SettingsViewModel への LlmSettingsRepository 注入・LLM 3項目追加・combine・update 系）
- **参照した入力・出力仕様**: 要件定義書 §2.1〜§2.5（シグネチャ・引数制約・Flow・SettingsUiState 拡張・データフロー）
- **参照した制約条件**: 要件定義書 §3（Hilt DI／REQ-401 暗号化／Dispatchers.IO／stateIn WhileSubscribed(5_000)／後方互換 REQ-021）
- **参照した使用例**: 要件定義書 §4.1〜§4.4（基本パターン・データフロー・空文字/初期未保存/連続更新エッジ・例外範囲外）
- **参照したタスク定義**: `docs/tasks/llm-memo-rewrite/TASK-0066.md`（完了条件・単体テスト TC-1〜TC-4）
- **参照した既存実装**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`（updateVault/updateFolder・stateIn パターン）、`app/src/test/java/com/den4dr/share2Obsidian/ui/SettingsViewModelTest.kt`（MockK + runTest + coVerify）、`app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt` / `LlmSettingsRepository.kt`

---

## 6. テストケース一覧・信頼性サマリー

| ID | 種別 | 内容 | 信頼性 |
|----|------|------|--------|
| TC-N-01 | 正常系 | updateLlmEndpointUrl → saveEndpointUrl を1回呼ぶ | 🔵 |
| TC-N-02 | 正常系 | updateLlmApiKey → saveApiKey を1回呼ぶ | 🔵 |
| TC-N-03 | 正常系 | updateLlmModel → saveModel を1回呼ぶ | 🔵 |
| TC-N-04 | 正常系 | uiState が note/llm 両値を combine で集約 | 🔵 |
| TC-N-05 | 正常系 | vault/folder が引き続き反映（後方互換） | 🟡 |
| TC-E-01 | 異常系 | save 例外ハンドリングは範囲外（非実装を明示） | 🔴 |
| TC-E-02 | 異常系 | 既存テストの2引数コンストラクタ移行（回帰防止） | 🟡 |
| TC-B-01 | 境界値 | 空文字入力をそのまま保存 | 🟡 |
| TC-B-02 | 境界値 | 初期未保存状態で LLM 3項目が "" | 🟡 |
| TC-B-03 | 境界値 | 連続更新で save が回数分実行 | 🟡 |

### 信頼性レベル分布

| 信頼性 | 件数 | 割合 |
|--------|------|------|
| 🔵 青信号 | 4 | 40% |
| 🟡 黄信号 | 5 | 50% |
| 🔴 赤信号 | 1 | 10% |
| 合計 | 10 | 100% |

- 🔵 は完了条件で確定した中核4ケース（TC-1〜TC-4）。
- 🟡 はエッジ／後方互換で要件 §4・§6 に根拠のある妥当な推測。
- 🔴 は要件 §4.4 が「本タスク範囲外」と明示したエラーケース1件のみ（テスト非実装を明記）。

---

## 7. 品質判定

```
✅ 高品質:
- テストケース分類: 正常系(5)・異常系(2)・境界値(3) を網羅
- 期待値定義: 各ケースに coVerify/assertEquals の具体的期待値を明記
- 技術選択: Kotlin + JUnit4 + kotlinx-coroutines-test + MockK で確定
- 実装可能性: 依存タスク TASK-0058/0061 実装済、既存テストパターンで実現可能
- 信頼性レベル: 中核は 🔵、🟡🔴 はいずれも要件に根拠・範囲明示あり
```

**総合判定**: 高品質。次フェーズ（Redフェーズ）へ進行可能。

---

**作成日**: 2026-07-06 by tsumiki:tdd-testcases
**次フェーズ**: `/tsumiki:tdd-red llm-memo-rewrite TASK-0066`

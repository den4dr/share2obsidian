# TDD Refactorフェーズ記録: EditScreenViewModel Hilt化・rewriteBody()実装

**機能名**: llm-memo-rewrite
**タスクID**: TASK-0063
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. リファクタ前の状態確認

- 全テストスイート（35テストクラス・240テストケース）が Green フェーズ完了時点ですべて成功していることを確認
- 2秒以上かかるテストの調査: `MainActivityEditFlowTest`「TC-0020-B03」が2.8秒程度かかっていたが、
  同ファイルで初めて MockK を使用する箇所であり、`SettingsViewModelTest` の初回 MockK 呼び出しでも
  同様の傾向（約1秒）が見られたことから、MockK のプロキシ生成に伴う JVM 初回起動コスト（本テストの
  実装ロジックとは無関係な既知の特性）と判断した。追加のリファクタは不要と判断
- `.gitignore` によるテストファイル除外や `@Ignore`/`.skip` によるテスト無効化は検出されず
- `debug-*` 等の開発時生成ファイルは検出されず（本タスクで作成した一時診断用テストファイル
  `SharedFlowDiagTest.kt` は Green フェーズ内で削除済み）

---

## 2. セキュリティレビュー

- **入力検証**: `rewriteBody()` は `sourceContent`・`bodyLlmPrompt` をそのまま `LlmRewriteRepository.rewrite()` に渡す。
  これらは HTTP リクエストボディの構築（JSON エスケープ等）はリポジトリ層（`LlmRewriteRepositoryImpl`, TASK-0060）の
  責務であり、ViewModel 層での追加検証は不要（責務分離が適切）
- **APIキーの取り扱い**: `llmSettingsRepository.getSettings()` から取得する `LlmSettings.apiKey` は
  ViewModel 内でログ出力・永続化されず、そのままリポジトリに渡すのみ。メモリ上に一時的に保持されるが、
  既存実装（TASK-0058）で EncryptedSharedPreferences から取得される設計を変更していない
- **エラーメッセージ**: `errorEvents` は string resource ID（`Int`）のみを発行し、例外メッセージや
  スタックトレースをそのまま UI に露出しない設計を維持している（機微情報の漏洩リスクなし）
- **重大な脆弱性は発見されなかった**

---

## 3. パフォーマンスレビュー

- **計算量**: `rewriteBody()` 内の処理はすべて O(1)（StateFlow の `copy()`・`Flow.first()`・単発の HTTP 呼び出し）。
  ループや再帰は存在しない
- **メモリ使用量**: `_errorEvents` に `extraBufferCapacity = 1` を設定したことで、1件分の `Int` をバッファする
  領域が追加されるが、無視できるレベル（数バイト）
- **非同期処理**: `viewModelScope.launch` + `suspend fun` で実装されており、メインスレッドをブロックしない
- **重大な性能課題は発見されなかった**

---

## 4. 実施したリファクタリング

### 4.1 `_errorEvents` のバッファ設計変更 🟡

**変更前**: `MutableSharedFlow<Int>()`（replay=0, extraBufferCapacity=0）
**変更後**: `MutableSharedFlow<Int>(extraBufferCapacity = 1)`

**理由**: ゼロバッファの `SharedFlow` は emit 側と collect 側の購読タイミングが完全に一致する
「ランデブー」が成立しないと値の受け渡しに失敗しうる。Green フェーズでのテスト実行時に、
`kotlinx-coroutines-test` 環境でこの種のタイミング依存の問題を確認したため、バッファを1に
設定してタイミング依存を解消した。実運用でも、UI 側（EditScreen, TASK-0065）の
`LaunchedEffect` による購読開始が1フレーム遅れることで通知が失われるリスクを避けられる、
より安全な設計となる。「一回限りのイベント」という意味論自体は変更していない。

`app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`

### 4.2 テストコードの日本語コメント更新（Red→Greenフェーズ後の実態反映） 🔵

`EditScreenViewModelRewriteBodyTest.kt` のクラス doc コメントを、Red フェーズ時点の
「コンパイルエラーが期待される」という説明から、実装完了後の実際の設計判断
（`backgroundScope.launch` を避けた理由・共有スケジューラの意図）を説明する内容に更新した。

`EditScreenViewModelInitializeTest.kt` についても同様に、TASK-0062 Red フェーズ時点の
「コンパイルエラーになることが期待される」という記述を削除した（現在は実装済みで正常に動作するため）。

### 4.3 テストコードの重複除去（DRY原則） 🔵

**`EditScreenViewModelRewriteBodyTest.kt`**: `errorEvents` を購読するボイラープレート
（`backgroundScope不使用の理由コメント` + `val received = mutableListOf<Int>()` +
`launch { collect { ... } }`）が6箇所（TC-0063-E01〜E05, B04）で重複していたため、
`TestScope.collectErrorEvents(viewModel): Pair<MutableList<Int>, Job>` ヘルパー関数に集約した。

```kotlin
private fun TestScope.collectErrorEvents(viewModel: EditScreenViewModel): Pair<MutableList<Int>, Job> {
    val received = mutableListOf<Int>()
    val job = launch { viewModel.errorEvents.collect { received.add(it) } }
    return received to job
}
```

呼び出し側は `val (received, errorEventsJob) = collectErrorEvents(viewModel)` の1行に簡潔化された。

**`EditScreenViewModelTest.kt`**（20箇所）・**`EditScreenViewModelInitializeTest.kt`**（9箇所）:
Hilt化に伴い追加された `EditScreenViewModel(mockRewrite, mockSettings)` という
コンストラクタ呼び出しが繰り返されていたため、`newViewModel()` ファクトリヘルパーに集約した。

```kotlin
private fun newViewModel() = EditScreenViewModel(mockRewrite, mockSettings)
```

---

## 5. リファクタ非対象（検討したが見送った項目）

- **`EditScreenViewModelTest.kt`（714行）・`EditScreenViewModelInitializeTest.kt`（483行）・
  `EditScreenViewModelRewriteBodyTest.kt`（634行）・`MainActivityEditFlowTest.kt`（656行）の
  500行制限超過**: いずれも TASK-0063 以前から存在する、または既存パターンを踏襲した
  大きめのテストファイルである。500行制限はプロダクションコードの可読性・保守性を
  主眼とした基準であり、既存の大規模テストファイルを本タスクの範囲でファイル分割すると
  変更範囲が本タスクの目的（Hilt化・rewriteBody実装）を大きく超え、回帰リスクが
  リファクタの利益を上回ると判断し、本フェーズでは見送った。分割が必要な場合は
  別タスクとして独立して計画することを推奨する
- **production コード（`EditScreenViewModel.kt`: 294行, `EditFormState.kt`: 64行）は
  500行制限を十分に満たしており、追加分割は不要**

---

## 6. テスト実行結果（リファクタ後）

```
mise exec -- ./gradlew testDebugUnitTest
```

全35テストクラス・240テストケースすべて成功（リファクタ前と同数・同結果、退行なし）。

---

## 7. 品質判定

```
✅ 高品質:
- テスト結果: 全240テストケース継続成功
- セキュリティ: 重大な脆弱性なし
- パフォーマンス: 重大な性能課題なし
- リファクタ品質: 目標達成（バッファ設計改善・DRY原則適用・コメント整合性向上）
- コード品質: 適切なレベル
- ドキュメント: 完成
```

---

**作成日**: 2026-07-06 by tsumiki:tdd-refactor
**次フェーズ**: `/tsumiki:tdd-verify-complete llm-memo-rewrite TASK-0063`（完全性検証）

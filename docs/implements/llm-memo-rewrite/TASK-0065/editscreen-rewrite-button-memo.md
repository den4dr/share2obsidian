# TASK-0065 実装メモ: EditScreen UI「メモを更改」ボタン・ローディング・エラーToast

**機能名**: editscreen-rewrite-button
**タスクID**: TASK-0065
**要件名**: llm-memo-rewrite

## 経緯

`/tsumiki:kairo-implement` の自動実行中、Red フェーズを担当したサブエージェントが
月間APIスペンド上限（"You've hit your monthly spend limit"）により停止したため、
Red〜Verify-Complete相当の作業をオペレーター（メインセッション）が直接引き継いで実施した。

## 実施内容

### 事前確認・修正（既存テスト破損の解消）

- テストケース定義書（`editscreen-rewrite-button-testcases.md` §0.3）で指摘されていた通り、
  TASK-0063 の `EditScreenViewModel` Hilt化（コンストラクタが
  `@Inject constructor(llmRewriteRepository, llmSettingsRepository)` に変更）により、
  既存 `app/src/androidTest/.../EditScreenTest.kt` の3箇所（引数なし `EditScreenViewModel()`）が
  コンパイル不能になっていたことを `mise exec -- ./gradlew compileDebugAndroidTestKotlin` で確認。
- `EditScreenViewModel(mockk(relaxed = true), mockk(relaxed = true))` に置き換えて解消（ロジック変更なし）。

### Red相当（テストケース追加）

`app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt` に、
テストケース定義書 TC-01〜TC-05, BC-01〜BC-03（計8件）を追加。
`mockk<EditScreenViewModel>(relaxed = true)` + `every { formState }` / `every { errorEvents }` /
`every { rewriteBody() } just Runs` のパターンでViewModelをスタブ。

**制約**: Compose UI Test の `onNodeWithTag` 等は実行時マッチングのため、ボタン未実装状態でも
コンパイルは成功する（真の失敗確認にはデバイス/エミュレータでの実行が必要）。
この環境にはadb/emulatorが無いため、既存タスク（TASK-0057, 0058, 0060等）と同様に
コンパイル成功を暫定確認とした。

### Green相当（実装）

`app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt` に以下を追加:

1. `LaunchedEffect(Unit) { viewModel.errorEvents.collectLatest { resId -> Toast.makeText(...).show() } }`
   - `Toast.LENGTH_LONG` を使用（要件定義書 §3「エラー表示トーン制約」— 既存 `MainActivity.onSend` の
     `ActivityNotFoundException` Toastと同等のトーンを踏襲）
2. `body_field` 直後に「メモを更改」`Button`（testTag = `rewrite_body_button`）
   - `enabled = formState.rewriteBodyEnabled && !formState.isRewritingBody`
   - `isRewritingBody` 時は `CircularProgressIndicator`（testTag = `rewrite_body_progress`, size 16.dp, strokeWidth 2.dp）
   - 非処理中は `stringResource(R.string.button_rewrite_body)`（「メモを更改」）

### 確認結果

- `mise exec -- ./gradlew build`: BUILD SUCCESSFUL（assembleDebug/Release, lint含む）
- `mise exec -- ./gradlew test`: 全240件成功（failures=0, errors=0）、34テストクラス
- `mise exec -- ./gradlew compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL
- 計装テスト（TC-01〜05, BC-01〜03）自体の実機実行は adb/emulator 不在のため未実施（引き継ぎ事項）

## 完了条件チェック（TASK-0065.md）

- [x] body_field直後に「メモを更改」ボタンが表示される（実装済み、実機確認は未実施）
- [x] rewriteBodyEnabled=falseでボタン非活性
- [x] isRewritingBody中はローディングインジケータ表示・非活性
- [x] ボタン押下でviewModel.rewriteBody()が呼ばれる
- [x] LaunchedEffect+collectLatestでエラーToast表示（LENGTH_LONG）
- [x] 既存EditScreen単体テスト（androidTest）はコンパイル成功を確認（実機実行は未実施）。JVMユニットテスト240件は全成功

## 引き継ぎ事項

- `EditScreenTest.kt` の全テスト（既存分含む）およびTASK-0065新規8件について、
  デバイス/エミュレータ環境が用意でき次第 `mise exec -- ./gradlew connectedAndroidTest` による
  実機成功確認を推奨する（TASK-0057/0058/0060/0061と同様の既知の制約）。

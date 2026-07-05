# TASK-0019: EditScreen Composable - Red フェーズ記録

**機能名**: content-edit-preview
**タスクID**: TASK-0019
**フェーズ**: Red（失敗するテスト作成）
**作成日**: 2026-05-30

---

## 作成したテストケース一覧

| # | テストID | テスト名 | 分類 | 信頼性 |
|---|---------|---------|------|--------|
| 1 | TC-003-01 | タイトルフィールドに ProcessedContent.title の値が初期表示される | 正常系 | 🔵 |
| 2 | TC-003-02 | 本文フィールドに ProcessedContent.body の値が初期表示される | 正常系 | 🔵 |
| 3 | TC-003-03 | タグフィールドに defaultTags 由来のカンマ区切り文字列が初期表示される | 正常系 | 🔵 |
| 4 | TC-003-04 | フォルダフィールドに NoteConfig.folder の値が初期表示される | 正常系 | 🔵 |
| 5 | TC-LABEL-01 | 全フィールドラベルと2ボタンのテキストが表示される | 正常系 | 🔵 |
| 6 | TC-101-01 | 送信ボタンタップで onSend コールバックが SendParams を伴って呼ばれる | 正常系 | 🔵 |
| 7 | TC-201-01 | キャンセルボタンタップで onCancel コールバックが呼ばれ onSend は呼ばれない | 正常系 | 🔵 |
| 8 | TC-FIELD-EDIT-01 | タイトルフィールドを編集してから送信すると編集後の値が onSend に渡る | 正常系 | 🟡 |
| 9 | TC-EDGE-001-01 | タイトル空欄で送信ボタンを押しても送信できる | 異常系 | 🔵 |
| 10 | TC-EDGE-002-01 | 本文空欄で送信ボタンを押しても送信できる | 異常系 | 🔵 |
| 11 | TC-EDGE-003-01 | タグをカンマのみに変更して送信すると tags が空リストで onSend に渡る | 異常系 | 🔵 |
| 12 | TC-NOSEND-ON-CANCEL-01 | キャンセルではフォーム値に関わらず onSend が一度も呼ばれない | 異常系 | 🟡 |
| 13 | TC-EDGE-102-01 | Android バックボタン押下がキャンセルと同等に onCancel を呼ぶ | 境界/エッジ | 🟡 |
| 14 | TC-NFR-102-01 | 送信とキャンセルボタンが常に表示される | 境界/エッジ | 🟡 |

**合計**: 14テストケース（目標 10以上を達成）

---

## テストファイル

- **テストファイル**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt`
- **テストフレームワーク**: JUnit 4 + Compose UI Test（`createAndroidComposeRule<ComponentActivity>()`）
- **実行ターゲット**: `connectedAndroidTest`（デバイス/エミュレータ必須）

---

## 期待されるコンパイルエラー

```
e: file:///.../ui/EditScreenTest.kt:82:13 Unresolved reference 'EditScreen'.
e: file:///.../ui/EditScreenTest.kt:111:13 Unresolved reference 'EditScreen'.
... （全テストケースで同様のエラー）
FAILURE: Build failed with an exception.
Execution failed for task ':app:compileDebugAndroidTestKotlin'.
```

**エラーの理由**: `EditScreen.kt` がまだ `app/src/main/java/com/den4dr/share2Obsidian/ui/` に存在しないため、すべての `EditScreen(...)` 呼び出しが `Unresolved reference` エラーになる。これは TDD の Red フェーズとして正常な状態。

**確認コマンド**:
```bash
mise exec -- ./gradlew compileDebugAndroidTestKotlin
# → BUILD FAILED（Unresolved reference 'EditScreen'）が期待される結果
```

---

## 信頼性レベル分布

| 信号 | 件数 | 該当テストID |
|------|------|------------|
| 🔵 青 | 9 | TC-003-01〜04, TC-LABEL-01, TC-101-01, TC-201-01, TC-EDGE-001-01, TC-EDGE-002-01, TC-EDGE-003-01 |
| 🟡 黄 | 5 | TC-FIELD-EDIT-01, TC-NOSEND-ON-CANCEL-01, TC-EDGE-102-01, TC-NFR-102-01 |
| 🔴 赤 | 0 | （なし） |

---

## Green フェーズで実装すべき内容

### 実装対象ファイル
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`（新規作成）

### Composable 関数シグネチャ
```kotlin
@Composable
fun EditScreen(
    viewModel: EditScreenViewModel,
    config: NoteConfig,
    onSend: (SendParams) -> Unit,
    onCancel: () -> Unit
)
```

### 必須実装要素
1. **BackHandler**: `BackHandler { onCancel() }` でバックボタン対応（TC-EDGE-102-01）
2. **Scaffold + bottomBar**: 送信・キャンセルボタンを画面下部に固定（TC-NFR-102-01）
3. **formState 取得**: `val formState by viewModel.formState.collectAsState()`
4. **4フィールド表示**:
   - タイトル: `OutlinedTextField(singleLine=true)` + ラベル `label_title`
   - 本文: `OutlinedTextField(minLines=5)` + ラベル `label_body`
   - タグ: `OutlinedTextField(singleLine=true)` + ラベル `label_tags`
   - フォルダ: `OutlinedTextField(singleLine=true)` + ラベル `label_folder`
5. **onValueChange 結線**:
   - タイトル: `viewModel.updateTitle(it)`
   - 本文: `viewModel.updateBody(it)`
   - タグ: `viewModel.updateTagsText(it)`
   - フォルダ: `viewModel.updateFolder(it)`
6. **送信ボタン**: `onClick = { onSend(viewModel.buildSendParams(config)) }`
7. **キャンセルボタン**: `onClick = onCancel`
8. **文字列リソース**: `stringResource(R.string.label_*)` / `stringResource(R.string.button_*)`
9. **スクロール**: フィールド部分に `verticalScroll(rememberScrollState())`

---

## 品質判定

```
✅ 高品質:
- テスト定義: 14件（目標10以上を達成）
- コンパイルエラー確認: ✅（Unresolved reference 'EditScreen' × 19か所）
- 期待値: 具体的（"テストタイトル", "shared", "70_clippings", emptyList, callCount==1 等）
- アサーション: assertIsDisplayed(), assertEquals() を適切に使用
- 実装方針: 明確（EditScreen.kt の実装内容が具体化されている）
- 信頼性レベル: 🔵（青信号）9件 / 🟡（黄信号）5件 / 🔴（赤信号）0件
```

---

**作成者**: Claude Code (tsumiki:tdd-red)
**最終更新**: 2026-05-30

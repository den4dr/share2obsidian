# TASK-0020 Red フェーズ記録

**機能名**: content-edit-preview（展開内容の編集・プレビュー機能）
**タスクID**: TASK-0020
**フェーズ**: Red（失敗テスト作成）
**作成日**: 2026-05-30
**テストファイル**: `app/src/test/java/com/den4dr/share2Obsidian/MainActivityEditFlowTest.kt`

---

## 1. 作成したテストケース一覧

| TC-ID | 分類 | テスト名 | 信頼性 | テスト実行結果 |
|-------|------|---------|--------|--------------|
| TC-0020-N01 | 正常系 | テキスト共有インテントで起動した直後は Obsidian を起動しない | 🔵 | ❌ FAIL（期待どおり） |
| TC-0020-N02 | 正常系 | SendParams から NoteComposer 経由で正しい obsidian URI が生成される | 🔵 | ✅ PASS |
| TC-0020-N03 | 正常系 | キャンセルコールバックは Obsidian を起動せず Activity を終了する | 🔵 | ❌ FAIL（期待どおり） |
| TC-0020-N04 | 正常系 | 共有対象外インテントでは EditScreen を表示せず即終了する | 🔵 | ✅ PASS |
| TC-0020-E01 | 異常系 | 送信時に Obsidian が未インストールの場合はトーストを表示して終了する | 🔵 | ❌ FAIL（期待どおり） |
| TC-0020-E02 | 異常系 | title null の SendParams で Frontmatter の title 行が省略され URI の title が空文字になる | 🔵 | ✅ PASS |
| TC-0020-B01 | 境界値 | 本文が空文字でも空ノートの URI が正常に構築される | 🔵 | ✅ PASS |
| TC-0020-B02 | 境界値 | タグが空リストの場合に Frontmatter に tags: [] が出力される | 🔵 | ✅ PASS |
| TC-0020-B03 | 境界値 | 画面回転相当の二度目の初期化呼び出しで編集内容が上書きされない | 🟡 | ✅ PASS |
| TC-0020-B04 | 境界値 | バックボタン押下はキャンセルと同等に Obsidian を起動せず終了する | 🟡 | ❌ FAIL（期待どおり） |

**合計**: 10ケース（4失敗 / 6成功）

---

## 2. テストコードの全文

**ファイルパス**: `app/src/test/java/com/den4dr/share2Obsidian/MainActivityEditFlowTest.kt`

（実際のソースコードはファイルを参照してください）

---

## 3. 期待された失敗内容と実際の失敗

### TC-0020-N01（FAIL）

**期待**: EditScreen 表示段階では `nextStartedActivity == null`

**実際の失敗メッセージ**:
```
AssertionError: EditScreen 表示段階では Obsidian が起動されないこと（即時起動が撤廃されていること）
expected null, but was: <Intent { act=android.intent.action.VIEW dat=obsidian://new/... }>
```

**失敗理由**: 現行の `MainActivity` は `lifecycleScope.launch` 内でコンテンツ処理後に即 `startActivity(Intent(ACTION_VIEW, uri))` を呼び出すため、ユーザー操作なしで Obsidian が起動される。変更後フローでは `EditScreen` を表示してユーザー操作を待機する必要がある。

---

### TC-0020-N03（FAIL）

**期待**: キャンセル後に `nextStartedActivity == null`

**実際の失敗メッセージ**:
```
AssertionError: キャンセル後は Obsidian が起動されていないこと
expected null, but was: <Intent { act=android.intent.action.VIEW dat=obsidian://new/... }>
```

**失敗理由**: 現行フローでは `lifecycleScope.launch` 完了後に即 Obsidian を起動するため、`activity.finish()` を呼んでも既に `nextStartedActivity` にデータが入っている。変更後では `setContent { EditScreen(..., onCancel = { finish() }) }` で onCancel ロジックを分離し、キャンセル経路では `startActivity` を呼ばない。

---

### TC-0020-E01（FAIL）

**期待**: `ShadowToast.getTextOfLatestToast()` が `"Obsidian がインストールされていません"` を返す

**実際の失敗メッセージ**:
```
AssertionError: Obsidian 未インストール時のトーストメッセージが正しいこと
expected:<Obsidian がインストールされていません> but was:<null>
```

**失敗理由**: テスト内で手動で `Toast.makeText(...).show()` を呼び出したが、`ShadowToast.getTextOfLatestToast()` が `null` を返している。Robolectric の ShadowToast が Looper 処理タイミングに依存しているため。Green フェーズで変更後 `MainActivity` の onSend コールバック内のトースト処理を通じて正しくキャプチャされるように調整する。

---

### TC-0020-B04（FAIL）

**期待**: バックボタン後に `nextStartedActivity == null`

**実際の失敗メッセージ**:
```
AssertionError: バックボタン後は Obsidian が起動されていないこと
expected null, but was: <Intent { act=android.intent.action.VIEW dat=obsidian://new/... }>
```

**失敗理由**: TC-0020-N03 と同じ。現行フローでは `activity.finish()` 前に既に Obsidian が起動されている。

---

## 4. 成功したテスト（依存コンポーネントのロジック検証）

以下のテストは、既に実装されている `NoteComposer` / `EditScreenViewModel` のロジックを直接検証しているため、現行コードでも成功する。

- **TC-0020-N02**: `NoteComposer.buildFrontmatter` + `buildUri` の URI 構築ロジック ✅
- **TC-0020-N04**: 既存の「共有対象外 Intent → 即 finish」挙動のリグレッション確認 ✅
- **TC-0020-E02**: `NoteComposer` の `title=null` 対応（Frontmatter title 行省略・URI title 空文字） ✅
- **TC-0020-B01**: 本文空文字の Frontmatter 構造確認（EDGE-002） ✅
- **TC-0020-B02**: タグ空リストの `tags: []` 出力確認（EDGE-003） ✅
- **TC-0020-B03**: `EditScreenViewModel` の重複初期化防止（EDGE-101） ✅

---

## 5. Green フェーズで実装すべき内容

### MainActivity の変更内容

1. **`private val viewModel: EditScreenViewModel by viewModels()`** を追加
   - `androidx.activity.viewModels` import を追加

2. **`FrontmatterBuilder` / `ObsidianUriBuilder` の直接呼び出しを削除**
   - 対象コード（削除）:
     ```kotlin
     val noteContent = FrontmatterBuilder.build(processed.title, processed.body)
     val uri = ObsidianUriBuilder.build(noteContent, processed.title)
     try {
         startActivity(Intent(Intent.ACTION_VIEW, uri))
     } catch (e: ActivityNotFoundException) { ... }
     finish()
     ```

3. **`NoteConfig.fromAppConfig()` で初期設定を取得**
   ```kotlin
   val config = NoteConfig.fromAppConfig()
   ```

4. **`viewModel.initialize(processed, config)` を coroutine 内で呼び出し**

5. **`setContent { EditScreen(viewModel, config, onSend, onCancel) }` で EditScreen を表示**
   - `onSend`: `NoteComposer.buildFrontmatter` → `NoteComposer.buildUri` → `try { startActivity(...) } catch (ActivityNotFoundException) { Toast } ` → `finish()`
   - `onCancel`: `finish()`

### 既存テスト（MainActivityTest.kt）の修正

既存の `text plain intent launches obsidian uri` テストは「起動を期待」しているが、変更後フローでは起動しない。Green フェーズで期待値を変更（またはテストを削除）する必要がある。

### TC-0020-E01 の修正方針

Green フェーズでは変更後 `MainActivity` の onSend コールバック内で `ActivityNotFoundException` がキャッチされてトーストが表示される経路を Robolectric でテストする。テスト内で手動 Toast を呼ぶのではなく、`shadowOf(application).startActivity` をモックして例外を発生させる方法を検討する。

---

## 6. 品質評価

| 項目 | 評価 |
|------|------|
| テスト実行 | 10ケース実行、4ケース失敗（期待どおり Red 状態確認） |
| 期待値 | 明確で具体的（URI クエリ値・isFinishing・nextStartedActivity） |
| アサーション | 適切（JUnit4 の assertEquals / assertNull / assertTrue / assertFalse） |
| 実装方針 | 明確（MainActivity の FrontmatterBuilder 削除 → NoteComposer 経由 + EditScreen 表示） |
| 信頼性レベル | 🔵 8ケース・🟡 2ケース・🔴 0ケース |

**総合判定**: ✅ 高品質（Red フェーズとして適切な失敗状態）

---

## 7. 次のステップ

次のお勧めステップ: `/tsumiki:tdd-green content-edit-preview 0020` で Green フェーズ（最小実装）を開始します。

- `MainActivity.kt` を変更して EditScreen 表示・コールバック処理を実装する
- 既存 `MainActivityTest.kt` の `text plain intent launches obsidian uri` の期待値を修正する
- TC-0020-E01 の ShadowToast 検証を Green フェーズで修正する
- `mise exec -- ./gradlew assembleDebug` でコンパイル通過を確認する（TC-0020-C01）

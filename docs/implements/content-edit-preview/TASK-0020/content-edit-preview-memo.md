# content-edit-preview（MainActivity フロー変更）TDD開発完了記録

## 確認すべきドキュメント

- `docs/tasks/content-edit-preview/TASK-0020.md`
- `docs/implements/content-edit-preview/TASK-0020/content-edit-preview-requirements.md`
- `docs/implements/content-edit-preview/TASK-0020/content-edit-preview-testcases.md`

## 🎯 最終結果 (2026-05-30)
- **実装率**: 100% (10/10 L1テストケース全通過)
- **品質判定**: 合格（高品質）
- **assembleDebug**: BUILD SUCCESSFUL
- **TODO更新**: ✅ TASK-0020.md 完了条件チェックボックス全更新済み

## 💡 重要な技術学習

### 実装パターン
- `viewModels()` デリゲートで Activity スコープに ViewModel を束縛する（画面回転 EDGE-101 対応）
- `lifecycleScope.launch` 内で `setContent { EditScreen(...) }` を呼び出すことで非同期処理完了後にUIを切り替える
- onSend コールバックで `NoteComposer.buildFrontmatter` → `NoteComposer.buildUri` → `startActivity` → `finish()` の一連フロー
- onCancel コールバックは `finish()` のみ（Obsidian 未起動で安全に終了）
- `ActivityNotFoundException` は catch して `ShadowApplication.checkActivities(true)` で Robolectric テスト可能

### テスト設計
- Robolectric + `@LooperMode(PAUSED)` + `Shadows.shadowOf(Looper.getMainLooper()).idle()` でコルーチンを進める
- `ShadowApplication.checkActivities(true)` で obsidian:// 解決不能環境を再現し `ActivityNotFoundException` を発生させる
- `ShadowToast.getTextOfLatestToast()` でトースト表示を検証
- L1（Robolectric）で UI ボタンタップが困難な場合、コールバックロジックを直接呼び出して検証する（TC-0020-N03, B04）
- 画面回転（EDGE-101）は ViewModel 直接操作で `initialized` フラグの重複防止を L1 で代替検証

### 品質保証
- FrontmatterBuilder/ObsidianUriBuilder の直接呼び出しを削除し、NoteComposer 経由に統一することで AppConfig 非依存を実現（REQ-402）
- MainActivityTest.kt の既存テスト `text plain intent launches obsidian uri` を変更後フロー対応（即起動しない→EditScreen待機）に期待値変更
- WHAT コメント削除・WHY コメントのみ残存でクリーンな87行実装を実現

## ⚠️ L3（androidTest）後回し項目
以下は設計段階から「実機/エミュレータ必須のため後回し」と明示された項目で、L1で代替検証済み：
- TC-0020-I01: URL LoadingScreen→EditScreen遷移（Compose UI Test必要）
- TC-0020-I02: 送信ボタン実タップE2E（Compose UI Test必要）
- TC-0020-I03: キャンセルボタン実タップE2E（Compose UI Test必要）
- TC-0020-B03/B04: 実 Activity 再作成・BackHandler 実発火（ActivityScenario.recreate() 必要）

*L1の代替検証（ViewModel直接操作・onCancel直接呼び出し）で品質が確保されているため、本タスクの完了条件を満たす。*

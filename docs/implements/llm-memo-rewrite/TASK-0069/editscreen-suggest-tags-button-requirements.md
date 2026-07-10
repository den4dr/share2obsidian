# TDD要件定義書: EditScreen「タグを提案」ボタンUI追加

- **機能名**: editscreen-suggest-tags-button（EditScreen「タグを提案」ボタンUI追加）
- **タスクID**: TASK-0069
- **要件名**: llm-memo-rewrite
- **フェーズ**: Phase 5 - タグ提案（Should Have）
- **作成日**: 2026-07-08
- **出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0069/editscreen-suggest-tags-button-requirements.md`

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: EditScreen のタグ入力欄（`formState.tagsText` の `OutlinedTextField`）付近に「タグを提案」ボタンを追加し、押下で `viewModel.suggestTags()` を呼び出して LLM にタグ候補を生成させる UI 部品を提供する。*（REQ-103・REQ-301 より）*
- 🔵 **どのような問題を解決するか**: ユーザーが手動でタグを考えて入力する手間を減らし、共有/取得直後の元コンテンツ（`ProcessedContent`）を基に LLM が提案したタグを既存タグ入力欄へ追加できるようにする。*（REQ-302・ユーザーストーリー「タグ提案」より）*
- 🔵 **想定されるユーザー**: Share2Obsidian で共有テキストを Obsidian に送信する前に、タグ付けを効率化したい利用者。*（user-stories.md より）*
- 🔵 **システム内での位置づけ**: シングルアクティビティ＋Compose 構成の EditScreen（`ui` パッケージ）の一部。既に実装済みの `EditScreenViewModel.suggestTags()`（TASK-0068）を呼び出す View 層のトリガー UI であり、本文リライトボタン（TASK-0065）と同一のローディング表示パターンを踏襲する。*（architecture.md「変更が必要な既存コンポーネント」表より）*
- **参照したEARS要件**: REQ-103, REQ-301, REQ-302
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`（EditScreen / EditFormState 変更内容）

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 入力（UIイベント・State）

- 🔵 **ユーザー操作入力**: 「タグを提案」ボタンのタップ（クリック）イベント。*（REQ-103 より）*
- 🔵 **State入力**: `EditFormState.isSuggestingTags: Boolean`（実装済み。初期値 `false`）。UI はこれを購読してボタンの活性/非活性・ローディング表示を切り替える。*（EditFormState.kt・REQ-201 より）*
- 🔵 **表示ラベル入力**: `stringResource(R.string.button_suggest_tags)`（値「タグを提案」）。本タスクで `strings.xml` に新規追加する。*（TASK-0069 完了条件・NFR-201 より。※現状 strings.xml 未追加を確認済み）*

### 出力（副作用・UI状態）

- 🔵 **副作用出力**: ボタン押下時に `viewModel.suggestTags()` を1回呼び出す。*（TASK-0069 完了条件より）*
- 🔵 **UI状態出力（通常時）**: `isSuggestingTags == false` のとき、ボタンは活性、ボタン内に「タグを提案」テキストを表示する。*（REQ-103 より）*
- 🔵 **UI状態出力（処理中）**: `isSuggestingTags == true` のとき、ボタンは非活性（`enabled = false`）、ボタン内に `CircularProgressIndicator`（`size(16.dp)`, `strokeWidth = 2.dp`）を表示する。*（REQ-201・NFR-202・TASK-0065 パターンより）*

### 入出力の関係性

- 🔵 押下 → `suggestTags()` 起動 → ViewModel が `isSuggestingTags = true` に更新 → ボタンがローディング表示・非活性 → LLM 応答/失敗で `isSuggestingTags = false` に戻る → ボタン再活性。エラー時は ViewModel の `errorEvents` 経由で Toast 表示（本タスクでは追加実装不要）。*（EditScreenViewModel.kt・dataflow.md より）*

### データフロー

- 🔵 「タグを提案」ボタンタップ → `suggestTags()` → 元コンテンツ（`ProcessedContent`）を入力に LLM API 呼び出し → 生成タグを既存 `tagsText` に追加連結。*（dataflow.md・REQ-302 より）*

- **参照したEARS要件**: REQ-103, REQ-201, REQ-302, NFR-201, NFR-202
- **参照した設計文書**: `docs/design/llm-memo-rewrite/interfaces.kt`（EditFormState）、`app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt`、`docs/design/llm-memo-rewrite/dataflow.md`

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **パフォーマンス要件**: LLM 呼び出しは30秒タイムアウト（REQ-202・NFR-001。Ktor `HttpTimeout` で設定済み）。ローディング表示（`CircularProgressIndicator`）は押下直後に即時表示。`isSuggestingTags` の `MutableStateFlow` 更新は O(1)。*（REQ-202・NFR-202 より。本タスクは UI 表示のみ担当）*
- 🔵 **セキュリティ要件**: APIキー等の機微情報を扱わない View 層のため本タスクでの追加考慮は不要。エラーは種別のみ ViewModel から `errorEvents`（string resource ID）として通知され、平文はログ出力しない。*（NFR-102 は下位層で担保済み）*
- 🔵 **互換性・アーキテクチャ制約**:
  - シングルアクティビティ構成を維持し、新規 Activity は作らず `EditScreen` Composable 内に追加する。*（CLAUDE.md・note.md 開発ルールより）*
  - UI 文字列は `res/values/strings.xml` で定義する（ハードコード禁止、日本語ローカライズ）。*（NFR-201 より）*
  - 本文リライトボタン（TASK-0065, `isRewritingBody`）と同一のローディング表示パターンで実装し、実装の一貫性を保つ。*（note.md 開発ルールより）*
- 🔵 **ボタン活性化制約（重要）**: 本文リライトボタンと異なり、`rewriteBodyEnabled` のような活性ガードは持たない。非活性化条件は `isSuggestingTags == true`（処理中）のみ。元コンテンツが空文字でもボタンは非活性化しない。*（EDGE-101 より）*
- 🟡 **配置制約**: ボタンはタグ入力欄（`tagsText` の `OutlinedTextField`）付近／直下に配置する。厳密なピクセル位置は指定されておらず、既存 Column レイアウトの流れに沿った合理的配置とする。*（TASK-0069 実装詳細からの妥当な解釈）*

- **参照したEARS要件**: NFR-001, NFR-102, NFR-201, NFR-202, REQ-202, EDGE-101
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`、`app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`（既存レイアウト・rewriteBody ボタン）

---

## 4. 想定される使用例（EARS Edgeケース・データフローベース）

### 基本的な使用パターン

- 🔵 **正常系**: ユーザーが「タグを提案」ボタンをタップ → ローディング表示中に LLM がタグ候補を生成 → 既存タグ欄に追加され、ボタンが再び活性化する。*（REQ-103・REQ-302・dataflow.md より）*

### データフロー

- 🔵 タグ提案ボタンタップ → `suggestTags()` → `ProcessedContent` を入力に LLM API → タグ追加。*（dataflow.md より）*

### エッジケース

- 🔵 **EDGE-101（元コンテンツ空文字）**: 元コンテンツ（`ProcessedContent`）が空文字でもボタンは非活性化されず、押下可能。空文字のまま LLM API へ送信する（ガード実装をしない）。*（EDGE-101 より）*
- 🔵 **処理中の多重押下防止**: `isSuggestingTags == true` の間はボタンが `enabled = false` となり、多重リクエストを防止する。*（REQ-201・NFR-202 より）*

### エラーケース

- 🔵 **LLM 呼び出し失敗**: `suggestTags()` 失敗時、ViewModel が `errorEvents.emit(messageResId)` を発行し、EditScreen 側の既存 `LaunchedEffect { viewModel.errorEvents.collectLatest { ... } }` が日本語 Toast を表示する。本タスクでの Toast 追加実装は不要。失敗後 `isSuggestingTags` は `false` に戻り、ボタンは再活性化する。*（NFR-201・EditScreen.kt 既存実装より）*

- **参照したEARS要件**: EDGE-101, REQ-201, NFR-201, NFR-202
- **参照した設計文書**: `docs/design/llm-memo-rewrite/dataflow.md`、`app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`（errorEvents 購読）

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: タグ提案（`docs/spec/llm-memo-rewrite/user-stories.md`）
- **参照した機能要件**: REQ-103, REQ-201, REQ-202, REQ-301, REQ-302, REQ-406
- **参照した非機能要件**: NFR-001, NFR-102, NFR-201, NFR-202
- **参照したEdgeケース**: EDGE-101
- **参照した受け入れ基準**: TC-301 系（`docs/spec/llm-memo-rewrite/acceptance-criteria.md`）
- **参照した設計文書**:
  - **アーキテクチャ**: `docs/design/llm-memo-rewrite/architecture.md`「変更が必要な既存コンポーネント」表（EditFormState/EditScreen）
  - **データフロー**: `docs/design/llm-memo-rewrite/dataflow.md`（タグ提案フロー）
  - **型定義**: `docs/design/llm-memo-rewrite/interfaces.kt`、`app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt`（`isSuggestingTags` 実装済み）
  - **参考実装**: `docs/tasks/llm-memo-rewrite/TASK-0065.md`（「メモを更改」ボタン）、`docs/tasks/llm-memo-rewrite/TASK-0068.md`（`suggestTags()` 実装）

---

## 6. 実装・テスト対象ファイル

### 実装対象

- `app/src/main/res/values/strings.xml` — `<string name="button_suggest_tags">タグを提案</string>` を新規追加（**現状未追加を確認済み**）
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt` — タグ入力欄付近に「タグを提案」ボタンを追加

### 参照（変更不要・実装済み）

- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt` — `isSuggestingTags: Boolean = false`（TASK-0068 で追加済み）
- `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt` — `suggestTags()`・`errorEvents`（実装済み）

### テスト対象

- `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt` — 「タグを提案」ボタン関連 Compose UI テストを追加

---

## 7. 受け入れ基準（テスト観点）

- 🔵 **AC-1**: タグ入力欄付近に「タグを提案」ボタンが表示される（`onNodeWithText("タグを提案").assertIsDisplayed()`）。*（REQ-103 より）*
- 🔵 **AC-2**: ボタン押下で `viewModel.suggestTags()` が1回呼ばれる。*（完了条件より）*
- 🔵 **AC-3**: `isSuggestingTags == true` のとき、ボタン内に `CircularProgressIndicator` が表示され、ボタンが非活性（`assertIsNotEnabled`）である。*（REQ-201・NFR-202 より）*
- 🔵 **AC-4**: `mise exec -- ./gradlew test` が成功し、既存テスト（回帰）もパスする。*（完了条件より）*

---

## 8. 品質判定

```
✅ 高品質
- 要件の曖昧さ: なし（配置位置のみ軽微な解釈余地 → 🟡1件）
- 入出力定義: 完全（イベント・State・ラベル・副作用を明記）
- 制約条件: 明確（活性化条件・EDGE-101・ローディングパターンを特定）
- 実装可能性: 確実（前提 suggestTags()/isSuggestingTags 実装済み、参考パターン TASK-0065 あり）
- 信頼性レベル: 🔵 が大多数（🟡 は配置位置・REQ-406 のみ）
```

### 信頼性レベル分布

| 区分 | 🔵 青 | 🟡 黄 | 🔴 赤 |
|------|-------|-------|-------|
| 概要（第1章） | 4 | 0 | 0 |
| 入出力（第2章） | 8 | 0 | 0 |
| 制約（第3章） | 4 | 1 | 0 |
| 使用例（第4章） | 6 | 0 | 0 |
| 受け入れ基準（第7章） | 4 | 0 | 0 |

**総合評価**: 高品質（🔵 青信号が大多数、🟡 は「ボタン配置位置」の解釈1件のみ）

---

## 9. 特記事項（実装時の注意）

1. **strings.xml は未追加**: note.md では TASK-0064 で追加済みの想定だが、実際の `strings.xml` には `button_suggest_tags` が存在しないことを確認済み。本タスクで追加が必要。
2. **`isSuggestingTags` は実装済み**: `EditFormState` に既に定義済み（TASK-0068）。本タスクでの追加は不要。
3. **活性ガードなし**: rewriteBody ボタンの `rewriteBodyEnabled && !isRewritingBody` と異なり、タグ提案ボタンの非活性条件は `isSuggestingTags` のみ（`enabled = !formState.isSuggestingTags`）。EDGE-101 により元コンテンツ空でも非活性化しない。
4. **エラー処理は再利用**: `errorEvents` 購読は EditScreen に実装済みで `suggestTags()` 失敗も処理するため、本タスクで Toast 実装の追加は不要。
5. **testTag 付与推奨**: Compose UI テストの安定化のため、rewriteBody ボタン（`rewrite_body_button` / `rewrite_body_progress`）と同様に `suggest_tags_button` / `suggest_tags_progress` 等の testTag 付与を推奨（🟡 妥当な推測、既存パターン踏襲）。

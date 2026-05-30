# TASK-0017: EditScreenViewModel 実装 - TDD テストケース定義

**タスクID**: TASK-0017
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-03-31
**フェーズ**: テストケース洗い出し

---

## 1. 正常系テストケース（基本的な動作）

### TC-001: initialize() で初期値が正しくセットされる

- **テスト名**: initialize で ProcessedContent と NoteConfig から初期値が正しくセットされる
  - **何をテストするか**: `initialize()` メソッドが `ProcessedContent` と `NoteConfig` の値から `EditFormState` の4フィールド（title, body, tagsText, folder）を正しく初期化すること
  - **期待される動作**: `formState.value` の各フィールドが `ProcessedContent` と `NoteConfig` の値に基づいて設定される
- **入力値**:
  - `ProcessedContent(body = "共有テキスト", title = "ページタイトル", contentType = ContentKind.TEXT)`
  - `NoteConfig(vault = "testVault", folder = "70_clippings", defaultTags = listOf("shared"))`
  - **入力データの意味**: テキスト共有時の典型的なユースケース。タイトルあり・本文あり・デフォルトタグ1つの標準パターン
- **期待される結果**:
  - `formState.value.title == "ページタイトル"`
  - `formState.value.body == "共有テキスト"`
  - `formState.value.tagsText == "shared"`
  - `formState.value.folder == "70_clippings"`
  - **期待結果の理由**: REQ-003 で定義された初期値マッピングルール（title = processed.title ?: "", body = processed.body, tagsText = config.defaultTags.joinToString(", "), folder = config.folder）に基づく
- **テストの目的**: ViewModel の初期化ロジックが ProcessedContent と NoteConfig の値を正しく EditFormState に変換すること
  - **確認ポイント**: 4フィールドすべてが正しくマッピングされること。特に tagsText は `joinToString(", ")` で変換されること
- 🔵 **青信号**: REQ-003, TC-003-01〜04, interfaces.kt の EditScreenViewModelSpec、note.md の初期値マッピング仕様に基づく

---

### TC-002: initialize() は2回目以降の呼び出しを無視する（画面回転対応）

- **テスト名**: initialize は2回目以降の呼び出しを無視して状態を保持する
  - **何をテストするか**: `initialized` フラグにより、2回目以降の `initialize()` 呼び出しが無視され、ユーザーが編集した値が保持されること
  - **期待される動作**: 初回 `initialize()` 後に `updateTitle()` で変更した値が、2回目の `initialize()` 呼び出し後も維持される
- **入力値**:
  - 初回: `ProcessedContent(body = "本文", title = "初期タイトル", contentType = ContentKind.TEXT)`, `NoteConfig(...)`
  - 更新: `updateTitle("変更後タイトル")`
  - 2回目: 同じ `ProcessedContent` と `NoteConfig` で `initialize()` を再度呼び出す
  - **入力データの意味**: 画面回転時に Activity が再作成され、`initialize()` が再度呼ばれるシナリオを再現
- **期待される結果**:
  - `formState.value.title == "変更後タイトル"`（初期値の "初期タイトル" に戻らない）
  - **期待結果の理由**: EDGE-101「画面回転後も状態が保持される」要件に基づく。ViewModel はActivity 再作成時にも保持され、`initialized` フラグで重複初期化を防止する
- **テストの目的**: 画面回転時の重複初期化防止が正しく機能すること
  - **確認ポイント**: `initialized` フラグが true の場合に `initialize()` が早期リターンすること
- 🔵 **青信号**: EDGE-101, TC-EDGE-101-01, note.md の重複初期化防止ロジック仕様に基づく

---

### TC-003: title が null の ProcessedContent で初期化した場合に空文字でセットされる

- **テスト名**: title が null の ProcessedContent で初期化すると title フィールドが空文字になる
  - **何をテストするか**: `ProcessedContent.title` が `null` の場合、`EditFormState.title` が空文字 `""` で初期化されること
  - **期待される動作**: `processed.title ?: ""` の変換ロジックにより null が空文字に変換される
- **入力値**:
  - `ProcessedContent(body = "本文のみ", title = null, contentType = ContentKind.TEXT)`
  - `NoteConfig(vault = "testVault", folder = "70_clippings", defaultTags = listOf("shared"))`
  - **入力データの意味**: 共有元アプリがタイトルを提供しない場合のシナリオ（EXTRA_SUBJECT なしのテキスト共有）
- **期待される結果**:
  - `formState.value.title == ""`
  - `formState.value.body == "本文のみ"`
  - **期待結果の理由**: TC-003-02「タイトルが null の場合、タイトルフィールドは空」に準拠。Kotlin の `?: ""` 演算子で null を空文字に変換
- **テストの目的**: nullable な title の安全な変換ロジックを確認
  - **確認ポイント**: NullPointerException が発生せず、空文字として正常に動作すること
- 🔵 **青信号**: TC-003-02, interfaces.kt の `processed.title ?: ""` 仕様に基づく

---

### TC-004: updateTitle() でタイトルが変更される

- **テスト名**: updateTitle でフォーム状態のタイトルが正しく更新される
  - **何をテストするか**: `updateTitle()` メソッドが `formState` の `title` フィールドのみを更新し、他のフィールドに影響しないこと
  - **期待される動作**: `_formState.value.copy(title = title)` による状態更新が正しく行われる
- **入力値**:
  - 初期化後に `updateTitle("新しいタイトル")` を呼び出す
  - **入力データの意味**: ユーザーが編集画面のタイトルフィールドを編集した場合のシナリオ
- **期待される結果**:
  - `formState.value.title == "新しいタイトル"`
  - `formState.value.body` は初期値のまま変更されない
  - `formState.value.tagsText` は初期値のまま変更されない
  - `formState.value.folder` は初期値のまま変更されない
  - **期待結果の理由**: `copy()` によるイミュータブル更新は指定したフィールドのみ変更し、残りは元の値を保持する（Kotlin data class の仕様）
- **テストの目的**: update メソッドの正確な動作と他フィールドへの非影響を確認
  - **確認ポイント**: title のみが変更され、body/tagsText/folder が変わらないこと
- 🔵 **青信号**: REQ-003, note.md の状態更新パターン `_formState.value = _formState.value.copy(title = title)` に基づく

---

### TC-004b: updateBody() で本文が変更される

- **テスト名**: updateBody でフォーム状態の本文が正しく更新される
  - **何をテストするか**: `updateBody()` メソッドが `formState` の `body` フィールドのみを更新すること
  - **期待される動作**: body フィールドが新しい値に更新され、他のフィールドは変更されない
- **入力値**:
  - 初期化後に `updateBody("新しい本文")` を呼び出す
  - **入力データの意味**: ユーザーが本文を編集した場合のシナリオ
- **期待される結果**:
  - `formState.value.body == "新しい本文"`
  - 他フィールドは初期値のまま
  - **期待結果の理由**: `copy(body = body)` によるイミュータブル更新
- **テストの目的**: updateBody の正確な動作確認
  - **確認ポイント**: body のみ変更、title/tagsText/folder が不変であること
- 🔵 **青信号**: REQ-003, note.md の update メソッド仕様に基づく

---

### TC-004c: updateTagsText() でタグテキストが変更される

- **テスト名**: updateTagsText でフォーム状態のタグテキストが正しく更新される
  - **何をテストするか**: `updateTagsText()` メソッドが `formState` の `tagsText` フィールドのみを更新すること
  - **期待される動作**: tagsText フィールドが新しい値に更新され、他のフィールドは変更されない
- **入力値**:
  - 初期化後に `updateTagsText("shared, web, clipping")` を呼び出す
  - **入力データの意味**: ユーザーがタグフィールドにカンマ区切りで複数タグを入力した場合
- **期待される結果**:
  - `formState.value.tagsText == "shared, web, clipping"`
  - 他フィールドは初期値のまま
  - **期待結果の理由**: `copy(tagsText = tagsText)` によるイミュータブル更新
- **テストの目的**: updateTagsText の正確な動作確認
  - **確認ポイント**: tagsText のみ変更、title/body/folder が不変であること
- 🔵 **青信号**: REQ-103, note.md の update メソッド仕様に基づく

---

### TC-004d: updateFolder() でフォルダが変更される

- **テスト名**: updateFolder でフォーム状態のフォルダが正しく更新される
  - **何をテストするか**: `updateFolder()` メソッドが `formState` の `folder` フィールドのみを更新すること
  - **期待される動作**: folder フィールドが新しい値に更新され、他のフィールドは変更されない
- **入力値**:
  - 初期化後に `updateFolder("inbox/notes")` を呼び出す
  - **入力データの意味**: ユーザーが保存先フォルダを変更した場合（サブフォルダパスを含む）
- **期待される結果**:
  - `formState.value.folder == "inbox/notes"`
  - 他フィールドは初期値のまま
  - **期待結果の理由**: `copy(folder = folder)` によるイミュータブル更新
- **テストの目的**: updateFolder の正確な動作確認
  - **確認ポイント**: folder のみ変更、title/body/tagsText が不変であること
- 🔵 **青信号**: REQ-405, note.md の update メソッド仕様に基づく

---

### TC-005: buildSendParams() でタグがパースされる

- **テスト名**: buildSendParams でカンマ区切りタグテキストが List にパースされる
  - **何をテストするか**: `buildSendParams()` 内で `parseTagsText()` が呼び出され、`tagsText` がカンマ区切りの `List<String>` に変換されること
  - **期待される動作**: `"shared, web"` が `["shared", "web"]` に変換される
- **入力値**:
  - 初期化後に `updateTagsText("shared, web")` で更新
  - `buildSendParams(config)` を呼び出す
  - **入力データの意味**: ユーザーがタグフィールドに「shared, web」と入力した典型的なケース
- **期待される結果**:
  - `sendParams.tags == listOf("shared", "web")`
  - `sendParams.title == "ページタイトル"` （空でなければ非null）
  - `sendParams.body == "共有テキスト"`
  - `sendParams.config` が引数で渡した config と一致
  - **期待結果の理由**: REQ-103「タグフィールドのカンマ区切りパース」に準拠。`parseTagsText()` が split → trim → filter を行う
- **テストの目的**: buildSendParams のタグパースロジックが正しく動作すること
  - **確認ポイント**: tags がトリム済みの非空文字列リストとして返されること
- 🔵 **青信号**: REQ-103, TC-101-02/03, note.md の buildSendParams 実装仕様に基づく

---

### TC-005b: buildSendParams() で config が正しく渡される

- **テスト名**: buildSendParams で引数の NoteConfig がそのまま SendParams に設定される
  - **何をテストするか**: `buildSendParams(config)` の引数 `config` が `SendParams.config` にそのまま渡されること
  - **期待される動作**: メソッド引数の config がそのまま SendParams の config フィールドに設定される
- **入力値**:
  - `NoteConfig(vault = "myVault", folder = "inbox", defaultTags = listOf("test"))`
  - **入力データの意味**: カスタム設定値を持つ NoteConfig で config の受け渡しを確認
- **期待される結果**:
  - `sendParams.config == config`（引数で渡した config と参照等価）
  - **期待結果の理由**: buildSendParams の設計上、config は変換せずそのまま渡す仕様
- **テストの目的**: config の受け渡しが正確であること
  - **確認ポイント**: config が変更されずに SendParams に設定されること
- 🔵 **青信号**: REQ-405, note.md の buildSendParams 実装仕様に基づく

---

### TC-008: 複数タグ（デフォルトタグ）の初期値が joinToString で正しく変換される

- **テスト名**: 複数のデフォルトタグが joinToString でカンマ+スペース区切り文字列になる
  - **何をテストするか**: `config.defaultTags` に複数タグがある場合、`joinToString(", ")` で正しくカンマ+スペース区切りの文字列に変換されること
  - **期待される動作**: `listOf("shared", "web")` が `"shared, web"` に変換される
- **入力値**:
  - `NoteConfig(vault = "testVault", folder = "70_clippings", defaultTags = listOf("shared", "web"))`
  - **入力データの意味**: デフォルトタグが複数設定されている場合の初期値変換を確認
- **期待される結果**:
  - `formState.value.tagsText == "shared, web"`
  - **期待結果の理由**: TC-003-03「タグフィールドの初期値が AppConfig.OBSIDIAN_TAGS から生成される」に基づく。`joinToString(", ")` により List がカンマ+スペース区切り文字列に変換される
- **テストの目的**: 複数タグの初期値変換ロジックを確認
  - **確認ポイント**: タグ間にカンマ+スペースが挿入されること
- 🔵 **青信号**: REQ-103, TC-003-03, Kotlin の `joinToString` 仕様に基づく

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-006: buildSendParams() で空タイトルが null になる

- **テスト名**: buildSendParams で空文字タイトルが null に変換される
  - **エラーケースの概要**: ユーザーがタイトルフィールドを空にして送信した場合、`SendParams.title` が `null` になること
  - **エラー処理の重要性**: Obsidian URI でタイトル未指定のノートを作成するために、空タイトルを null に変換する必要がある（NoteComposer が title の null/非null で Frontmatter を分岐するため）
- **入力値**:
  - 初期化後に `updateTitle("")` で空文字に更新
  - `buildSendParams(config)` を呼び出す
  - **不正な理由**: 空文字はユーザーが意図的にタイトルを削除したことを示す。これはエラーではなく、タイトルなしノートの作成を意味する
  - **実際の発生シナリオ**: 共有テキストにタイトルがない場合、またはユーザーがタイトルフィールドの内容を全削除した場合
- **期待される結果**:
  - `sendParams.title == null`
  - **エラーメッセージの内容**: エラーメッセージなし（正常な変換処理）
  - **システムの安全性**: null title は NoteComposer で title フィールド省略として安全に処理される
- **テストの目的**: `ifBlank { null }` 変換ロジックが空文字に対して正しく動作すること
  - **品質保証の観点**: タイトルなしノートの作成が正常に行えることを保証
- 🔵 **青信号**: EDGE-001, note.md の `state.title.ifBlank { null }` 実装仕様に基づく

---

### TC-007: buildSendParams() でスペースのみタイトルが null になる

- **テスト名**: buildSendParams でスペースのみのタイトルが null に変換される
  - **エラーケースの概要**: ユーザーがタイトルフィールドにスペースのみを入力して送信した場合、`SendParams.title` が `null` になること
  - **エラー処理の重要性**: スペースのみのタイトルは実質的に「タイトルなし」と同義であり、意味のないタイトルが Obsidian ノートに設定されることを防ぐ
- **入力値**:
  - 初期化後に `updateTitle("   ")` でスペースのみに更新
  - `buildSendParams(config)` を呼び出す
  - **不正な理由**: スペースのみの文字列は `ifBlank { null }` により blank と判定される（Kotlin の `isBlank()` はスペース・タブ等の空白文字のみで構成される文字列を true と判定）
  - **実際の発生シナリオ**: ユーザーが誤ってスペースキーを押した後に送信した場合
- **期待される結果**:
  - `sendParams.title == null`
  - **エラーメッセージの内容**: エラーメッセージなし（正常な変換処理）
  - **システムの安全性**: スペースのみのタイトルが Obsidian ノートのファイル名として使用されることを防ぐ
- **テストの目的**: `ifBlank { null }` がスペースのみの文字列にも適用されること
  - **品質保証の観点**: Kotlin の `ifBlank` の動作仕様（`isBlank()` ベース）が期待通りであること
- 🟡 **黄信号**: EDGE-001 から妥当な推測。`ifBlank` の動作は Kotlin 標準ライブラリの仕様に基づくが、要件定義にスペースのみの具体的な記載はない

---

### TC-009: buildSendParams() で空本文がそのまま渡される

- **テスト名**: buildSendParams で空文字の本文がそのまま SendParams に設定される
  - **エラーケースの概要**: ユーザーが本文を全削除して送信した場合、空文字がエラーにならずそのまま `SendParams.body` に設定されること
  - **エラー処理の重要性**: 空ノートの作成は Obsidian で正当なユースケースであるため、空本文を拒否しない
- **入力値**:
  - 初期化後に `updateBody("")` で空文字に更新
  - `buildSendParams(config)` を呼び出す
  - **不正な理由**: エラーケースではないが、空文字が正しく処理されることの確認（EDGE-002）
  - **実際の発生シナリオ**: ユーザーが本文を全削除して「タイトルのみ」のノートを作成する場合
- **期待される結果**:
  - `sendParams.body == ""`
  - **エラーメッセージの内容**: エラーなし
  - **システムの安全性**: 空ノートとして正常に処理される
- **テストの目的**: EDGE-002「本文空で送信」が正常に動作すること
  - **品質保証の観点**: 空本文を不正入力として拒否しないことを保証
- 🔵 **青信号**: EDGE-002「本文空で送信」、note.md の buildSendParams 実装仕様「body はそのまま使用」、requirements.md セクション2.4 の変換ロジック表に基づく

---

### TC-010: buildSendParams() で空タグテキストが空リストになる

- **テスト名**: buildSendParams で空文字のタグテキストが空リストに変換される
  - **エラーケースの概要**: ユーザーがタグフィールドを全削除して送信した場合、`parseTagsText("")` が `emptyList()` を返すこと
  - **エラー処理の重要性**: タグなしのノートは Obsidian で正当なユースケースであるため、空タグを拒否しない
- **入力値**:
  - 初期化後に `updateTagsText("")` で空文字に更新
  - `buildSendParams(config)` を呼び出す
  - **不正な理由**: エラーケースではないが、空タグテキストが正しくパースされることの確認（EDGE-003）
  - **実際の発生シナリオ**: ユーザーがデフォルトタグを削除してタグなしノートを作成する場合
- **期待される結果**:
  - `sendParams.tags == emptyList<String>()`
  - **エラーメッセージの内容**: エラーなし
  - **システムの安全性**: タグなしノートとして正常に処理される
- **テストの目的**: EDGE-003「タグ空で送信」が parseTagsText 経由で正常に動作すること
  - **品質保証の観点**: 空タグ入力でクラッシュしないことを保証
- 🔵 **青信号**: EDGE-003「タグ空で送信」、note.md の buildSendParams 実装仕様「parseTagsText(state.tagsText)」、TASK-0016 で parseTagsText("") = emptyList() が検証済みに基づく

---

## 3. 境界値テストケース（最小値、最大値、null等）

### TC-011: デフォルトタグが空リストの場合の初期化

- **テスト名**: デフォルトタグが空リストの場合に tagsText が空文字で初期化される
  - **境界値の意味**: `defaultTags` の最小サイズ（0個）での `joinToString` の動作確認
  - **境界値での動作保証**: 空リストの `joinToString(", ")` は空文字列 `""` を返すこと
- **入力値**:
  - `NoteConfig(vault = "testVault", folder = "70_clippings", defaultTags = emptyList())`
  - **境界値選択の根拠**: タグ数 0 は `joinToString` の最小入力。空リストでもクラッシュしないことを確認
  - **実際の使用場面**: ユーザーがデフォルトタグを設定していない場合（将来の設定機能対応時）
- **期待される結果**:
  - `formState.value.tagsText == ""`
  - **境界での正確性**: Kotlin の `emptyList().joinToString(", ")` は `""` を返す（言語仕様）
  - **一貫した動作**: タグ数 0, 1, 2+ すべてで一貫した joinToString の動作
- **テストの目的**: タグリスト空の場合の初期値変換
  - **堅牢性の確認**: 空リスト入力でもクラッシュせず正常に初期化されること
- 🟡 **黄信号**: Kotlin の `joinToString` 仕様から妥当な推測。要件定義には空タグリストの初期化について明示的な記載はないが、EDGE-003 と整合性がある

---

### TC-012: initialize() 前の formState デフォルト値

- **テスト名**: initialize 呼び出し前の formState がデフォルト値を持つ
  - **境界値の意味**: ViewModel 生成直後（initialize 未呼出し）の初期状態の確認
  - **境界値での動作保証**: ViewModel 生成直後でも formState.value にアクセスして NullPointerException が発生しないこと
- **入力値**:
  - `EditScreenViewModel()` のみ生成、`initialize()` は呼び出さない
  - **境界値選択の根拠**: ViewModel のライフサイクルにおける最初期の状態。MutableStateFlow の初期値が正しく設定されている必要がある
  - **実際の使用場面**: Compose で ViewModel が作成された直後、`initialize()` が呼ばれる前にRecomposition が発生した場合
- **期待される結果**:
  - `formState.value` が非 null
  - `formState.value.title == ""`
  - `formState.value.body == ""`
  - `formState.value.tagsText == ""`
  - `formState.value.folder == ""`
  - **境界での正確性**: MutableStateFlow のコンストラクタに渡す初期値が空の EditFormState であること
  - **一貫した動作**: initialize 前後で formState.value がアクセス可能であること
- **テストの目的**: ViewModel 生成直後の安全性確認
  - **堅牢性の確認**: initialize 前に formState にアクセスしてもクラッシュしないこと
- 🟡 **黄信号**: ViewModel の初期状態について要件定義に明示的な記載はないが、StateFlow の初期値は必須であり、空の EditFormState がデフォルト値として妥当

---

### TC-013: 連続した update メソッド呼び出し

- **テスト名**: 複数の update メソッドを連続して呼び出した場合にすべての変更が反映される
  - **境界値の意味**: StateFlow の連続更新が正しく動作することの確認
  - **境界値での動作保証**: 各 update メソッドが独立して動作し、最後の状態がすべての変更を反映すること
- **入力値**:
  - 初期化後に以下を連続実行:
    - `updateTitle("新タイトル")`
    - `updateBody("新本文")`
    - `updateTagsText("tag1, tag2")`
    - `updateFolder("new_folder")`
  - **境界値選択の根拠**: 4つの update メソッドを連続呼出しした場合の最終状態が全フィールド更新済みであること
  - **実際の使用場面**: ユーザーが編集画面で複数フィールドを順番に編集した場合
- **期待される結果**:
  - `formState.value.title == "新タイトル"`
  - `formState.value.body == "新本文"`
  - `formState.value.tagsText == "tag1, tag2"`
  - `formState.value.folder == "new_folder"`
  - **境界での正確性**: 各 `copy()` が前回の更新結果を正しく引き継いでいること
  - **一貫した動作**: 更新順序に関係なく、最終状態がすべての変更を反映すること
- **テストの目的**: 複数フィールドの連続更新が互いに干渉しないこと
  - **堅牢性の確認**: `copy()` による部分更新が累積的に正しく動作すること
- 🟡 **黄信号**: 要件定義に連続更新の具体的な記載はないが、data class の `copy()` と StateFlow の動作から妥当な推測

---

### TC-014: buildSendParams() でタグにスペース付きカンマ区切りのトリム

- **テスト名**: buildSendParams でスペースを含むカンマ区切りタグが正しくトリムされる
  - **境界値の意味**: `parseTagsText` のトリム処理が buildSendParams 経由でも正しく機能すること
  - **境界値での動作保証**: 前後にスペースを含むタグが正しくトリムされること
- **入力値**:
  - `updateTagsText("  shared  ,  web  ,  clipping  ")` で前後にスペースを含むタグを設定
  - `buildSendParams(config)` を呼び出す
  - **境界値選択の根拠**: ユーザーがコピペでスペースが混入する場合の実用的なケース。TC-101-03「タグのカンマ区切りでスペースがトリムされる」に対応
  - **実際の使用場面**: タグフィールドにコピペで入力した場合にスペースが混入するケース
- **期待される結果**:
  - `sendParams.tags == listOf("shared", "web", "clipping")`
  - **境界での正確性**: 各タグの前後スペースがすべて除去されること
  - **一貫した動作**: スペースの有無にかかわらず同じタグリストが生成されること
- **テストの目的**: parseTagsText のトリム処理が ViewModel 経由で正しく動作すること
  - **堅牢性の確認**: スペースが混入しても正常にパースされること
- 🔵 **青信号**: REQ-103, TC-101-03, TASK-0016 の parseTagsText 実装仕様に基づく

---

### TC-015: ContentKind の異なるタイプでの初期化

- **テスト名**: URL タイプの ProcessedContent で正しく初期化される
  - **境界値の意味**: ContentKind が TEXT 以外（URL）の場合でも ViewModel の初期化が正常に動作すること
  - **境界値での動作保証**: `initialize()` は `contentType` に依存せず、title と body のみを使用すること
- **入力値**:
  - `ProcessedContent(body = "https://example.com", title = "Example Page", contentType = ContentKind.URL)`
  - **境界値選択の根拠**: REQ-001「全コンテンツタイプで編集画面表示」に基づく。TEXT 以外の contentType で ViewModel が正しく動作することの確認
  - **実際の使用場面**: ブラウザから URL を共有した場合
- **期待される結果**:
  - `formState.value.title == "Example Page"`
  - `formState.value.body == "https://example.com"`
  - **境界での正確性**: contentType に関係なく title と body のマッピングが同一であること
  - **一貫した動作**: TEXT/URL/HTML/FILE すべての contentType で同じ初期化ロジックが適用されること
- **テストの目的**: ViewModel が contentType に依存しないことを確認
  - **堅牢性の確認**: 異なる contentType でもクラッシュせず正常に初期化されること
- 🟡 **黄信号**: REQ-001 から妥当な推測。ViewModel が contentType を無視する設計は interfaces.kt の型定義から明確だが、テストケースとしての明示的な記載はない

---

### TC-016: StateFlow の変更が正しく伝播する

- **テスト名**: StateFlow の値変更が collect で正しく受け取れる
  - **境界値の意味**: StateFlow の Reactive な値伝播が正しく機能することの確認（ViewModel の状態管理の根幹）
  - **境界値での動作保証**: `formState` の値を collect したときに、update メソッドによる変更が正しく反映されること
- **入力値**:
  - 初期化後に `updateTitle("新タイトル")` を呼び出し、`formState.value` を取得する
  - **境界値選択の根拠**: note.md のテストケース要件「StateFlow の変更が正しく伝播する（EDGE-101）」に基づく。ViewModel + StateFlow の基本的な動作保証
  - **実際の使用場面**: EditScreen の Composable が `collectAsState()` で ViewModel の状態を監視し、UI を更新する場合
- **期待される結果**:
  - `formState.value.title == "新タイトル"`（更新後の値が取得可能）
  - 他のフィールドは初期値のまま保持
  - **境界での正確性**: StateFlow は常に最新の値を保持するため、`value` プロパティで即座に最新値を取得できる
  - **一貫した動作**: update メソッド呼び出し後、StateFlow の値が即座に反映されること
- **テストの目的**: ViewModel の StateFlow が状態変更を正しく伝播することを確認
  - **堅牢性の確認**: StateFlow のリアクティブな値伝播が EditScreen Composable（TASK-0019）で使用可能であること
- 🟡 **黄信号**: note.md のテストケース要件「StateFlow テスト」に基づくが、具体的なテスト仕様は要件定義に明記されていない。StateFlow の `value` プロパティの同期アクセスで検証する方針

---

## 4. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: プロジェクトの主要言語。Android 開発の標準言語であり、data class・StateFlow・Extension functions 等のテストに適した機能を持つ
  - **テストに適した機能**: バッククォート記法テストメソッド名、data class の equals 自動生成、null safety
- **テストフレームワーク**: JUnit 4 + Robolectric 4.14.1
  - **フレームワーク選択の理由**: 既存テスト（NoteComposerTest, ParseTagsTextTest 等）で使用されているフレームワークに統一。Robolectric は Android API（ViewModel, StateFlow 等）を JVM 上でテスト可能にする
  - **テスト実行環境**: JVM 上で Robolectric がAndroid API をシミュレート。`@RunWith(RobolectricTestRunner::class)` / `@Config(sdk = [34])` を使用
- **追加ライブラリ**: Kotlin Coroutines test utilities（StateFlow テスト用に `runTest`、`advanceUntilIdle` を使用する可能性あり）
- 🔵 **青信号**: note.md のテストフレームワーク情報、gradle/libs.versions.toml、既存テストパターンに基づく

---

## 5. テストケース実装時の日本語コメント指針

各テストケースの実装時には以下のパターンで日本語コメントを記載する。

#### テストケース開始時のコメント（Kotlin）

```kotlin
// 【テスト目的】: [このテストで何を確認するかを日本語で明記]
// 【テスト内容】: [具体的にどのような処理をテストするかを説明]
// 【期待される動作】: [正常に動作した場合の結果を説明]
// 🔵🟡🔴 信頼性レベル: [参照元を記載]
```

#### Arrange（準備フェーズ）のコメント

```kotlin
// 【テストデータ準備】: [なぜこのデータを用意するかの理由]
// 【初期条件設定】: [テスト実行前の状態を説明]
// 【前提条件確認】: [テスト実行に必要な前提条件を明記]
```

#### Act（実行フェーズ）のコメント

```kotlin
// 【実際の処理実行】: [どの機能/メソッドを呼び出すかを説明]
// 【処理内容】: [実行される処理の内容を日本語で説明]
```

#### Assert（検証フェーズ）のコメント

```kotlin
// 【結果検証】: [何を検証するかを具体的に説明]
// 【期待値確認】: [期待される結果とその理由を説明]
assertEquals("期待値", actual) // 【確認内容】: 具体的な確認項目 🔵🟡🔴
```

#### セットアップのコメント

```kotlin
@Before
fun setUp() {
    // 【テスト前準備】: 各テスト共通の ViewModel インスタンス生成と初期化
    // 【環境初期化】: ProcessedContent と NoteConfig の標準テストデータを準備
}
```

---

## 6. 要件定義との対応関係

- **参照した機能概要**: content-edit-preview-requirements.md セクション1「機能の概要」
  - EditScreenViewModel の4つの責務（初期化・状態更新・送信パラメータ生成・画面回転対応）
- **参照した入力・出力仕様**: content-edit-preview-requirements.md セクション2「入力・出力の仕様」
  - initialize() の入力パラメータ・初期値マッピング
  - update メソッド群の入力・動作
  - buildSendParams() の入力・出力・変換ロジック
- **参照した制約条件**: content-edit-preview-requirements.md セクション3「制約条件」
  - アーキテクチャ制約（ui パッケージ、ViewModel 継承、StateFlow 使用）
  - テスト制約（JUnit 4 + Robolectric、Arrange-Act-Assert パターン）
- **参照した使用例**: content-edit-preview-requirements.md セクション4「想定される使用例」
  - 基本パターン3種（初期化・更新・送信）
  - エッジケース4種（画面回転・null タイトル・空タイトル・スペースのみタイトル）
- **参照したEARS要件**: REQ-001, REQ-003, REQ-101, REQ-103, REQ-405, EDGE-001, EDGE-002, EDGE-003, EDGE-101
- **参照した受け入れ基準**: TC-003-01〜04, TC-101-02/03, TC-EDGE-001-01, TC-EDGE-101-01

---

## 7. テストケースサマリー

| TC | テスト名 | 分類 | 対応要件 | 信頼性 |
|----|---------|------|----------|--------|
| TC-001 | initialize で初期値が正しくセットされる | 正常系 | REQ-003, TC-003-01〜04 | 🔵 |
| TC-002 | initialize は2回目以降の呼び出しを無視する | 正常系 | EDGE-101 | 🔵 |
| TC-003 | title が null で空文字初期化 | 正常系 | TC-003-02 | 🔵 |
| TC-004 | updateTitle でタイトル変更 | 正常系 | REQ-003 | 🔵 |
| TC-004b | updateBody で本文変更 | 正常系 | REQ-003 | 🔵 |
| TC-004c | updateTagsText でタグテキスト変更 | 正常系 | REQ-103 | 🔵 |
| TC-004d | updateFolder でフォルダ変更 | 正常系 | REQ-405 | 🔵 |
| TC-005 | buildSendParams でタグパース | 正常系 | REQ-103 | 🔵 |
| TC-005b | buildSendParams で config 受け渡し | 正常系 | REQ-405 | 🔵 |
| TC-006 | 空タイトルが null に変換 | 異常系 | EDGE-001 | 🔵 |
| TC-007 | スペースのみタイトルが null に変換 | 異常系 | EDGE-001 | 🟡 |
| TC-008 | 複数デフォルトタグの joinToString 変換 | 正常系 | REQ-103 | 🔵 |
| TC-009 | 空本文がそのまま渡される | 異常系 | EDGE-002 | 🟡 |
| TC-010 | 空タグテキストが空リストに変換 | 異常系 | EDGE-003 | 🟡 |
| TC-011 | デフォルトタグ空リストの初期化 | 境界値 | EDGE-003 | 🟡 |
| TC-012 | initialize 前のデフォルト値 | 境界値 | - | 🟡 |
| TC-013 | 連続 update メソッド呼び出し | 境界値 | REQ-003 | 🟡 |
| TC-014 | スペース付きタグのトリム | 境界値 | REQ-103, TC-101-03 | 🔵 |
| TC-015 | URL タイプでの初期化 | 境界値 | REQ-001 | 🟡 |
| TC-016 | StateFlow の変更伝播 | 境界値 | EDGE-101 | 🟡 |

---

## 8. 信頼性レベルサマリー

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 正常系 | 9 | 0 | 0 | 9 |
| 異常系 | 3 | 1 | 0 | 4 |
| 境界値 | 1 | 6 | 0 | 7 |
| **合計** | **13** | **7** | **0** | **20** |

- 🔵 **青信号**: 13件 (65%) - 要件定義・設計文書に直接基づくテストケース
- 🟡 **黄信号**: 7件 (35%) - 要件から妥当に推測したテストケース
- 🔴 **赤信号**: 0件 (0%)

---

**作成者**: Claude Code (tsumiki:tdd-testcases)
**最終更新**: 2026-03-31 (TC-009/TC-010 信頼性引き上げ、TC-016 StateFlow テスト追加)

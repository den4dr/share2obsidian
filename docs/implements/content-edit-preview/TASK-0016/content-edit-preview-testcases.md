# TASK-0016: EditFormState + parseTagsText + SendParams - TDDテストケース定義書

**タスクID**: TASK-0016
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-03-31
**フェーズ**: テストケース定義

---

## 1. 正常系テストケース

### TC-016-001: EditFormState の基本的なインスタンス生成

- **テスト名**: EditFormState の全フィールド指定でのインスタンス生成
  - **何をテストするか**: EditFormState データクラスが 4 つのフィールド（title, body, tagsText, folder）を正しく保持すること
  - **期待される動作**: コンストラクタに渡した値がそのまま各プロパティから取得できる
- **入力値**: `title="テスト記事"`, `body="本文テスト"`, `tagsText="shared, web"`, `folder="70_clippings"`
  - **入力データの意味**: ユーザーが共有テキストを受け取った際の典型的な初期値（ProcessedContent + NoteConfig 由来）
- **期待される結果**: 各プロパティが入力値と完全一致する
  - **期待結果の理由**: data class は不変のフィールドを保持するため、コンストラクタの値がそのまま返される
- **テストの目的**: EditFormState のデータ保持機能の確認
  - **確認ポイント**: 4フィールドすべてが正しく保持されること
- 🔵 **青信号**: REQ-003（編集フィールド定義）・interfaces.kt の EditFormState 定義に基づく

---

### TC-016-002: EditFormState の ProcessedContent からの典型的な初期化パターン

- **テスト名**: ProcessedContent.title が存在する場合の EditFormState 初期値
  - **何をテストするか**: `ProcessedContent.title ?: ""` と `config.defaultTags.joinToString(", ")` による初期値生成パターンが正しいこと
  - **期待される動作**: title に ProcessedContent.title の値、tagsText に "shared" が設定される
- **入力値**: `ProcessedContent(body="本文", title="タイトル", contentType=TEXT)`, `NoteConfig(vault="testVault", folder="70_clippings", defaultTags=listOf("shared"))`
  - **入力データの意味**: テキスト共有の基本パターン（dataflow.md フロー1）
- **期待される結果**: `EditFormState(title="タイトル", body="本文", tagsText="shared", folder="70_clippings")`
  - **期待結果の理由**: REQ-003 の初期値仕様に準拠。title は ProcessedContent.title、tagsText は defaultTags を joinToString
- **テストの目的**: ViewModel が initialize() で行う初期化パターンの事前検証
  - **確認ポイント**: title, body, tagsText, folder の4フィールドすべてが正しい初期値を持つこと
- 🔵 **青信号**: REQ-003・TC-003-01〜04・dataflow.md フロー1 に基づく

---

### TC-016-003: EditFormState の ProcessedContent.title が null の場合

- **テスト名**: ProcessedContent.title が null の場合に title が空文字列になる
  - **何をテストするか**: `ProcessedContent.title ?: ""` による null→空文字列変換パターン
  - **期待される動作**: title フィールドが空文字列 "" で初期化される
- **入力値**: `ProcessedContent(body="本文", title=null, contentType=TEXT)`, `NoteConfig(vault="testVault", folder="70_clippings", defaultTags=listOf("shared"))`
  - **入力データの意味**: 共有元アプリが EXTRA_SUBJECT を提供しない場合（EDGE-001）
- **期待される結果**: `EditFormState(title="", body="本文", tagsText="shared", folder="70_clippings")`
  - **期待結果の理由**: EDGE-001 仕様：タイトル空の場合、空文字列として保持する
- **テストの目的**: null タイトルの安全なハンドリング確認
  - **確認ポイント**: title が null ではなく空文字列 "" であること
- 🔵 **青信号**: EDGE-001・TC-003-01・interfaces.kt `processed.title ?: ""` に基づく

---

### TC-016-004: parseTagsText の基本パース（カンマ区切り複数タグ）

- **テスト名**: カンマ区切りの複数タグが正しくパースされる
  - **何をテストするか**: `"shared, web, clipping"` → `["shared", "web", "clipping"]` の変換
  - **期待される動作**: カンマで分割後、各要素がトリムされてリストに格納される
- **入力値**: `"shared, web, clipping"`
  - **入力データの意味**: 典型的なユーザー入力（REQ-103 の例示値）
- **期待される結果**: `listOf("shared", "web", "clipping")`
  - **期待結果の理由**: REQ-103 の入出力仕様に準拠
- **テストの目的**: parseTagsText の基本的なパース機能の確認
  - **確認ポイント**: リストのサイズが 3、各要素が前後空白なしであること
- 🔵 **青信号**: REQ-103・TC-101-02 に基づく

---

### TC-016-005: parseTagsText のスペーストリム処理

- **テスト名**: 前後にスペースを含むタグが正しくトリムされる
  - **何をテストするか**: `"shared ,  web , clipping "` → `["shared", "web", "clipping"]` の変換
  - **期待される動作**: 各要素の前後の空白が除去される
- **入力値**: `"shared ,  web , clipping "`
  - **入力データの意味**: ユーザーがスペースを不揃いに入力した場合（TC-101-03）
- **期待される結果**: `listOf("shared", "web", "clipping")`
  - **期待結果の理由**: trim() による前後空白除去の仕様（REQ-103）
- **テストの目的**: トリム処理の正確性確認
  - **確認ポイント**: 各タグに前後の空白が含まれないこと
- 🔵 **青信号**: REQ-103・TC-101-03 に基づく

---

### TC-016-006: parseTagsText の単一タグ

- **テスト名**: 単一タグがリスト化される
  - **何をテストするか**: `"shared"` → `["shared"]` の変換
  - **期待される動作**: カンマなしの入力がサイズ 1 のリストに変換される
- **入力値**: `"shared"`
  - **入力データの意味**: デフォルトタグが 1 つだけの場合
- **期待される結果**: `listOf("shared")`
  - **期待結果の理由**: split(",") で単一要素のリストが返され、フィルタリングで残る
- **テストの目的**: カンマなし入力の正しいハンドリング
  - **確認ポイント**: リストサイズが 1 であること
- 🔵 **青信号**: REQ-103 通常ケースに基づく

---

### TC-016-007: SendParams の基本的なインスタンス生成

- **テスト名**: SendParams の全フィールド指定でのインスタンス生成
  - **何をテストするか**: SendParams データクラスが title, body, tags, config の 4 フィールドを正しく保持すること
  - **期待される動作**: コンストラクタに渡した値がそのまま各プロパティから取得できる
- **入力値**: `title="テスト"`, `body="本文"`, `tags=listOf("shared", "web")`, `config=NoteConfig(vault="testVault", folder="70_clippings", defaultTags=listOf("shared"))`
  - **入力データの意味**: 送信ボタンタップ時に ViewModel が生成する典型的なパラメータ（REQ-101）
- **期待される結果**: 各プロパティが入力値と完全一致する
  - **期待結果の理由**: data class の不変フィールド保持
- **テストの目的**: SendParams のデータ保持機能の確認
  - **確認ポイント**: 4フィールドすべてが正しく保持され、title が String? 型で nullable であること
- 🔵 **青信号**: REQ-101・interfaces.kt の SendParams 定義に基づく

---

### TC-016-008: SendParams の title が null のケース

- **テスト名**: SendParams.title が null の場合のインスタンス生成
  - **何をテストするか**: title=null で SendParams を生成できること（EDGE-001 のタイトルなし送信）
  - **期待される動作**: title が null のまま保持される
- **入力値**: `title=null`, `body="本文"`, `tags=listOf("shared")`, `config=NoteConfig(...)`
  - **入力データの意味**: ユーザーがタイトルを空にして送信した場合（空文字 → null 変換後）
- **期待される結果**: `sendParams.title == null`
  - **期待結果の理由**: EDGE-001 仕様：タイトル空の場合 null で送信
- **テストの目的**: nullable title の正しいハンドリング
  - **確認ポイント**: title が null であり、他のフィールドは正常値であること
- 🔵 **青信号**: EDGE-001・interfaces.kt の `title: String?` 定義に基づく

---

## 2. 異常系テストケース

### TC-016-009: parseTagsText の空文字列入力

- **テスト名**: 空文字列入力で空リストが返される
  - **エラーケースの概要**: タグフィールドが空の状態で送信された場合
  - **エラー処理の重要性**: 空文字列が不正なタグとして処理されないことが必要
- **入力値**: `""`
  - **不正な理由**: 有効なタグが含まれていないが、エラーではなく正常な空入力
  - **実際の発生シナリオ**: ユーザーがタグフィールドをすべて削除して送信した場合
- **期待される結果**: `emptyList<String>()`
  - **エラーメッセージの内容**: エラーではないため、メッセージなし
  - **システムの安全性**: 空リストとして安全に処理される（Frontmatter で tags: [] になる）
- **テストの目的**: 空入力の安全なハンドリング（EDGE-003）
  - **品質保証の観点**: NullPointerException や IndexOutOfBoundsException が発生しないことを保証
- 🟡 **黄信号**: EDGE-003 から妥当な推測（要件定義で「空文字列も許容」と記載あり）

---

### TC-016-010: parseTagsText のカンマのみ入力

- **テスト名**: カンマのみの入力で空リストが返される
  - **エラーケースの概要**: カンマだけが入力された異常な入力パターン
  - **エラー処理の重要性**: split 後の空文字列が有効なタグとして扱われないこと
- **入力値**: `","`
  - **不正な理由**: カンマで分割すると空文字列のみが残る
  - **実際の発生シナリオ**: ユーザーがタグを入力しようとして途中で消した場合
- **期待される結果**: `emptyList<String>()`
  - **エラーメッセージの内容**: エラーではないため、メッセージなし
  - **システムの安全性**: 空文字列のフィルタリングにより空リストとして処理
- **テストの目的**: 不正な区切り文字のみの入力に対するロバスト性確認（TC-103-02）
  - **品質保証の観点**: split + filter の組み合わせが空要素を適切に除去すること
- 🟡 **黄信号**: TC-103-02 から妥当な推測

---

### TC-016-011: parseTagsText のスペース+カンマ入力

- **テスト名**: スペースとカンマだけの入力で空リストが返される
  - **エラーケースの概要**: 有効なタグ文字を含まない、空白とカンマのみの入力
  - **エラー処理の重要性**: trim 後に空文字列となる要素がフィルタリングされること
- **入力値**: `"  ,  ,  "`
  - **不正な理由**: 各要素が trim 後に空文字列になる
  - **実際の発生シナリオ**: ユーザーがスペースキーとカンマのみを入力した場合
- **期待される結果**: `emptyList<String>()`
  - **エラーメッセージの内容**: エラーではないため、メッセージなし
  - **システムの安全性**: trim + filter で空文字列が除去される
- **テストの目的**: スペースのみの要素が有効タグとして残らないことの確認
  - **品質保証の観点**: 空白文字のみのタグがリストに混入しないことを保証
- 🟡 **黄信号**: EDGE-003 から妥当な推測

---

### TC-016-012: SendParams の body が空文字列のケース

- **テスト名**: SendParams.body が空文字列でも正常にインスタンスが生成される
  - **エラーケースの概要**: ユーザーが本文をすべて削除して送信した場合
  - **エラー処理の重要性**: 空ノート送信は許容される動作（EDGE-002）
- **入力値**: `title="タイトル"`, `body=""`, `tags=listOf("shared")`, `config=NoteConfig(...)`
  - **不正な理由**: エラーではなく正常な空入力（EDGE-002 で許容）
  - **実際の発生シナリオ**: ユーザーが共有テキストを編集画面で全削除して送信
- **期待される結果**: `sendParams.body == ""`
  - **エラーメッセージの内容**: エラーではないため、メッセージなし
  - **システムの安全性**: 空文字列として NoteComposer に渡され、空ノートが生成される
- **テストの目的**: 空本文の許容性確認（EDGE-002）
  - **品質保証の観点**: 空文字列が例外を引き起こさないこと
- 🟡 **黄信号**: EDGE-002 から妥当な推測

---

### TC-016-013: SendParams の tags が空リストのケース

- **テスト名**: SendParams.tags が空リストでも正常にインスタンスが生成される
  - **エラーケースの概要**: ユーザーがタグをすべて削除して送信した場合
  - **エラー処理の重要性**: タグなし送信は許容される動作（EDGE-003）
- **入力値**: `title="タイトル"`, `body="本文"`, `tags=emptyList()`, `config=NoteConfig(...)`
  - **不正な理由**: エラーではなく正常な空リスト（EDGE-003 で許容）
  - **実際の発生シナリオ**: ユーザーがタグフィールドを空にして送信（parseTagsText("") → []）
- **期待される結果**: `sendParams.tags == emptyList<String>()`
  - **エラーメッセージの内容**: エラーではないため、メッセージなし
  - **システムの安全性**: 空リストが NoteComposer に渡され、tags: [] の Frontmatter が生成される
- **テストの目的**: 空タグリストの許容性確認（EDGE-003）
  - **品質保証の観点**: 空リストが例外を引き起こさないこと
- 🟡 **黄信号**: EDGE-003 から妥当な推測

---

## 3. 境界値テストケース

### TC-016-014: parseTagsText の末尾カンマ

- **テスト名**: 末尾にカンマがある入力で空要素がフィルタリングされる
  - **境界値の意味**: split(",") で最後の要素が空文字列になる境界パターン
  - **境界値での動作保証**: 末尾の空要素がリストに含まれないこと
- **入力値**: `"shared, web,"`
  - **境界値選択の根拠**: split(",") が ["shared", " web", ""] を返すパターン
  - **実際の使用場面**: ユーザーがタグ入力中にカンマを打って次のタグ名を入力せずに送信
- **期待される結果**: `listOf("shared", "web")`
  - **境界での正確性**: 末尾の空文字列が filter { it.isNotEmpty() } で除去される
  - **一貫した動作**: 有効なタグのみがリストに残る
- **テストの目的**: 末尾カンマの安全なハンドリング
  - **堅牢性の確認**: 不完全な入力でも有効なタグのみを抽出できること
- 🟡 **黄信号**: REQ-103 のパース仕様から妥当な推測

---

### TC-016-015: parseTagsText の先頭カンマ

- **テスト名**: 先頭にカンマがある入力で空要素がフィルタリングされる
  - **境界値の意味**: split(",") で最初の要素が空文字列になる境界パターン
  - **境界値での動作保証**: 先頭の空要素がリストに含まれないこと
- **入力値**: `",shared, web"`
  - **境界値選択の根拠**: split(",") が ["", "shared", " web"] を返すパターン
  - **実際の使用場面**: ユーザーが誤ってカンマから入力を開始した場合
- **期待される結果**: `listOf("shared", "web")`
  - **境界での正確性**: 先頭の空文字列が filter { it.isNotEmpty() } で除去される
  - **一貫した動作**: 先頭・末尾の位置に関わらず空要素が除去される
- **テストの目的**: 先頭カンマの安全なハンドリング
  - **堅牢性の確認**: 不完全な入力でも有効なタグのみを抽出できること
- 🟡 **黄信号**: REQ-103 のパース仕様から妥当な推測

---

### TC-016-016: parseTagsText の連続カンマ

- **テスト名**: 連続するカンマで空要素がフィルタリングされる
  - **境界値の意味**: split(",") で中間に空文字列が生まれる境界パターン
  - **境界値での動作保証**: 連続カンマ間の空要素がリストに含まれないこと
- **入力値**: `"shared,,web"`
  - **境界値選択の根拠**: split(",") が ["shared", "", "web"] を返すパターン
  - **実際の使用場面**: ユーザーがカンマを誤って2回入力した場合
- **期待される結果**: `listOf("shared", "web")`
  - **境界での正確性**: 中間の空文字列が filter { it.isNotEmpty() } で除去される
  - **一貫した動作**: 連続カンマがあっても有効なタグのみを抽出
- **テストの目的**: 連続カンマの安全なハンドリング
  - **堅牢性の確認**: 区切り文字の重複に対する耐性
- 🟡 **黄信号**: REQ-103 のパース仕様から妥当な推測

---

### TC-016-017: EditFormState の data class 等価性

- **テスト名**: 同一パラメータの EditFormState インスタンスが等価と判定される
  - **境界値の意味**: data class の構造的等価性が正しく動作すること（ViewModel の状態比較で使用）
  - **境界値での動作保証**: equals/hashCode/copy が data class として正しく機能すること
- **入力値**: 同一パラメータで2つの EditFormState インスタンスを生成
  - **境界値選択の根拠**: StateFlow の値比較で data class の equals が使用される
  - **実際の使用場面**: ViewModel の状態が変更されたかの検出（Compose Recomposition のトリガー）
- **期待される結果**: `editFormState1 == editFormState2` が true、`hashCode` が一致
  - **境界での正確性**: Kotlin data class の構造的等価性が保証される
  - **一貫した動作**: 異なるインスタンスでも同値なら等価
- **テストの目的**: data class 等価性の確認（Compose の状態比較で重要）
  - **堅牢性の確認**: 不要な Recomposition を防ぐために正しい等価判定が必要
- 🟡 **黄信号**: Kotlin data class の標準動作からの妥当な推測

---

### TC-016-018: EditFormState の copy() メソッド

- **テスト名**: copy() メソッドで特定フィールドのみが変更される
  - **境界値の意味**: ユーザー編集操作で ViewModel が copy() を使って状態を更新するパターン
  - **境界値での動作保証**: 変更したフィールドのみが更新され、他のフィールドは元の値を保持すること
- **入力値**: `EditFormState(title="元タイトル", body="元本文", tagsText="shared", folder="70_clippings").copy(title="新タイトル")`
  - **境界値選択の根拠**: ユーザーがタイトルのみを編集した場合のパターン
  - **実際の使用場面**: 編集画面で1つのフィールドのみを変更した場合
- **期待される結果**: `title="新タイトル"`, `body="元本文"`, `tagsText="shared"`, `folder="70_clippings"`
  - **境界での正確性**: copy() が指定されたフィールドのみを更新し、残りは元の値を保持
  - **一貫した動作**: 部分更新が正確に行われる
- **テストの目的**: data class の copy() による部分更新の正確性確認
  - **堅牢性の確認**: ViewModel の状態更新パターンが正しく動作すること
- 🟡 **黄信号**: Kotlin data class の標準動作からの妥当な推測

---

### TC-016-019: SendParams の data class 等価性

- **テスト名**: 同一パラメータの SendParams インスタンスが等価と判定される
  - **境界値の意味**: data class としての基本的な構造的等価性
  - **境界値での動作保証**: equals/hashCode が正しく機能すること
- **入力値**: 同一パラメータ（title, body, tags, config すべて同じ）で2つの SendParams インスタンスを生成
  - **境界値選択の根拠**: テスト時のアサーションで等価比較を使用するため
  - **実際の使用場面**: テストコードでの期待値比較
- **期待される結果**: `sendParams1 == sendParams2` が true、`hashCode` が一致
  - **境界での正確性**: NoteConfig を含む入れ子 data class でも等価性が成立
  - **一貫した動作**: 異なるインスタンスでも同値なら等価
- **テストの目的**: SendParams の data class 等価性確認
  - **堅牢性の確認**: 入れ子の data class（NoteConfig）を含む等価性判定が正しいこと
- 🟡 **黄信号**: Kotlin data class の標準動作からの妥当な推測

---

## 4. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: プロジェクト全体で Kotlin を使用しており、CLAUDE.md で明示されている
  - **テストに適した機能**: バッククォートによるテストメソッド名の日本語記述、data class の自動生成メソッド、非null型システム
- **テストフレームワーク**: JUnit 4
  - **フレームワーク選択の理由**: 既存テスト（NoteComposerTest.kt 等）で JUnit 4 を使用しており、プロジェクト統一
  - **テスト実行環境**: `parseTagsText()` と data class は純粋な Kotlin コードのため、Robolectric は不要。通常の JVM テストで実行可能
- 🔵 **青信号**: CLAUDE.md・gradle/libs.versions.toml・既存テストパターンに基づく

### テスト実行コマンド

```bash
# ParseTagsTextTest のみ実行
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.ParseTagsTextTest"

# UI テスト全体
mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.*"

# 全テスト実行（回帰テスト）
mise exec -- ./gradlew test
```

---

## 5. テストケース実装時の日本語コメント指針

各テストケースの実装時には以下の Kotlin コメントを含めてください。

#### テストケース開始時のコメント

```kotlin
// 【テスト目的】: [このテストで何を確認するかを日本語で明記]
// 【テスト内容】: [具体的にどのような処理をテストするかを説明]
// 【期待される動作】: [正常に動作した場合の結果を説明]
// 🔵🟡🔴 信頼性レベル: [参照元を記載]
```

#### Given（Arrange）のコメント

```kotlin
// 【テストデータ準備】: [なぜこのデータを用意するかの理由]
// 【初期条件設定】: [テスト実行前の状態を説明]
// 【前提条件確認】: [テスト実行に必要な前提条件を明記]
```

#### When（Act）のコメント

```kotlin
// 【実際の処理実行】: [どの機能/メソッドを呼び出すかを説明]
// 【処理内容】: [実行される処理の内容を日本語で説明]
```

#### Then（Assert）のコメント

```kotlin
// 【結果検証】: [何を検証するかを具体的に説明]
// 【期待値確認】: [期待される結果とその理由を説明]
assertEquals(expected, actual) // 【確認内容】: 具体的な確認項目 🔵🟡🔴
```

#### セットアップ・クリーンアップのコメント

```kotlin
@Before
fun setUp() {
    // 【テスト前準備】: [各テスト実行前に行う準備作業の説明]
    // 【環境初期化】: [テスト環境をクリーンな状態にする理由と方法]
}
```

---

## 6. 要件定義との対応関係

### 参照した機能概要

- **セクション 1**: EditFormState データクラス定義 → REQ-003（編集フィールドの4項目定義）
- **セクション 1**: parseTagsText() 関数定義 → REQ-103（タグフィールドのカンマ区切りパース）
- **セクション 1**: SendParams データクラス定義 → REQ-101（送信時パラメータ構造）

### 参照した入力・出力仕様

- **セクション 2.1**: EditFormState のコンストラクタパラメータ → REQ-003, TC-003-01〜04
- **セクション 2.2**: parseTagsText の入出力仕様 → REQ-103, TC-101-02, TC-101-03
- **セクション 2.3**: SendParams のコンストラクタパラメータ → REQ-101, EDGE-001/002/003

### 参照した制約条件

- **セクション 3**: アーキテクチャ制約 → REQ-401（シングルアクティビティ）, REQ-402（既存コンポーネント保護）
- **セクション 3**: テスト制約 → JUnit 4, Robolectric 不要（純粋 Kotlin テスト）
- **セクション 3**: 命名規約 → 既存テストパターン（NoteComposerTest.kt）

### 参照した使用例

- **セクション 4.1**: EditFormState 初期化パターン → dataflow.md フロー1
- **セクション 4.1**: parseTagsText 使用パターン → dataflow.md フロー3
- **セクション 4.2**: エッジケース → EDGE-001, EDGE-002, EDGE-003

---

## 7. テストファイル構成

### 新規テストファイル

| ファイル | テスト対象 | テストケース数 |
|---------|----------|-------------|
| `app/src/test/java/com/den4dr/share2Obsidian/ui/ParseTagsTextTest.kt` | parseTagsText() 関数 | 8 件（TC-016-004〜006, TC-016-009〜011, TC-016-014〜016） |
| `app/src/test/java/com/den4dr/share2Obsidian/ui/EditFormStateTest.kt` | EditFormState データクラス | 5 件（TC-016-001〜003, TC-016-017〜018） |
| `app/src/test/java/com/den4dr/share2Obsidian/ui/SendParamsTest.kt` | SendParams データクラス | 4 件（TC-016-007〜008, TC-016-012〜013, TC-016-019） |

### テストケース・テストファイル対応表

| テストケースID | テストファイル | 分類 |
|--------------|-------------|------|
| TC-016-001 | EditFormStateTest.kt | 正常系 |
| TC-016-002 | EditFormStateTest.kt | 正常系 |
| TC-016-003 | EditFormStateTest.kt | 正常系 |
| TC-016-004 | ParseTagsTextTest.kt | 正常系 |
| TC-016-005 | ParseTagsTextTest.kt | 正常系 |
| TC-016-006 | ParseTagsTextTest.kt | 正常系 |
| TC-016-007 | SendParamsTest.kt | 正常系 |
| TC-016-008 | SendParamsTest.kt | 正常系 |
| TC-016-009 | ParseTagsTextTest.kt | 異常系 |
| TC-016-010 | ParseTagsTextTest.kt | 異常系 |
| TC-016-011 | ParseTagsTextTest.kt | 異常系 |
| TC-016-012 | SendParamsTest.kt | 異常系 |
| TC-016-013 | SendParamsTest.kt | 異常系 |
| TC-016-014 | ParseTagsTextTest.kt | 境界値 |
| TC-016-015 | ParseTagsTextTest.kt | 境界値 |
| TC-016-016 | ParseTagsTextTest.kt | 境界値 |
| TC-016-017 | EditFormStateTest.kt | 境界値 |
| TC-016-018 | EditFormStateTest.kt | 境界値 |
| TC-016-019 | SendParamsTest.kt | 境界値 |

---

## 信頼性レベルサマリー

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 正常系テストケース | 8 | 0 | 0 | 8 |
| 異常系テストケース | 0 | 5 | 0 | 5 |
| 境界値テストケース | 0 | 6 | 0 | 6 |
| 開発言語・フレームワーク | 1 | 0 | 0 | 1 |
| **合計** | **9** | **11** | **0** | **20** |

- 🔵 **青信号**: 9項目 (45%) - 要件定義・設計文書から直接導出
- 🟡 **黄信号**: 11項目 (55%) - 要件定義・設計文書から妥当な推測
- 🔴 **赤信号**: 0項目 (0%) - 推測なし

---

**作成者**: Claude Code
**最終更新**: 2026-03-31

# TASK-0015: NoteConfig + NoteComposer テストケース定義書

**タスクID**: TASK-0015
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-03-29
**出力ファイル**: `docs/implements/content-edit-preview/TASK-0015/content-edit-preview-testcases.md`

---

## 1. 正常系テストケース（基本的な動作）

### TC-001: タイトルありの Frontmatter 生成

- **テスト名**: タイトル・本文・タグありの Frontmatter が正しく生成される
  - **何をテストするか**: `NoteComposer.buildFrontmatter()` がタイトル・本文・タグすべて指定時に正しい Frontmatter 文字列を生成すること
  - **期待される動作**: Frontmatter ヘッダー（`---`）内に `title` と `tags` が含まれ、本文が空行後に続く
- **入力値**: `title="テスト"`, `body="本文テスト"`, `tags=listOf("shared", "web")`
  - **入力データの意味**: 典型的な編集画面でユーザーが入力する値。複数タグを含むことで区切り処理も確認
- **期待される結果**: `"---\ntitle: \"テスト\"\ntags: [shared, web]\n---\n\n本文テスト"`
  - **期待結果の理由**: REQ-101・acceptance-criteria.md TC-101-01 で定義された Frontmatter 形式に準拠
- **テストの目的**: 基本的な Frontmatter 生成ロジックの正常動作を確認
  - **確認ポイント**: title 行の存在、タグのカンマ+スペース区切り、本文の正確な出力
- 🔵 **青信号**: REQ-101・acceptance-criteria.md TC-101-01・既存 FrontmatterBuilder 実装に基づく

---

### TC-002: タイトルなしの Frontmatter 生成

- **テスト名**: タイトルが null の場合に title フィールドが省略される
  - **何をテストするか**: `title=null` の場合に `title:` 行が Frontmatter に含まれないこと
  - **期待される動作**: Frontmatter ヘッダー内に `tags` のみが出力される
- **入力値**: `title=null`, `body="本文"`, `tags=listOf("shared")`
  - **入力データの意味**: 共有元アプリがタイトルを提供しない場合の典型パターン（EDGE-001）
- **期待される結果**: `"---\ntags: [shared]\n---\n\n本文"`
  - **期待結果の理由**: EDGE-001 仕様「タイトル空の場合、Frontmatter の title フィールドを省略」に準拠
- **テストの目的**: null タイトル時の条件分岐が正しく動作することを確認
  - **確認ポイント**: `title:` 行が出力に含まれないこと
- 🔵 **青信号**: EDGE-001・FrontmatterBuilder の既存動作・タスクノートに基づく

---

### TC-003: 複数タグの Frontmatter 生成

- **テスト名**: 複数タグがカンマ+スペース区切りで正しく出力される
  - **何をテストするか**: 3つ以上のタグが正しくフォーマットされること
  - **期待される動作**: `tags: [tag1, tag2, tag3]` 形式で出力される
- **入力値**: `title="メモ"`, `body="内容"`, `tags=listOf("shared", "web", "clipping")`
  - **入力データの意味**: ユーザーが複数のタグを指定する実際のユースケース
- **期待される結果**: `"---\ntitle: \"メモ\"\ntags: [shared, web, clipping]\n---\n\n内容"`
  - **期待結果の理由**: タグの `joinToString(", ")` によるカンマ+スペース区切りの正確性を確認
- **テストの目的**: タグリストのフォーマット処理が正確であることを確認
  - **確認ポイント**: タグ間のカンマ+スペース区切り、前後に余計なスペースがないこと
- 🔵 **青信号**: REQ-103・既存 FrontmatterBuilder の `joinToString(", ")` パターンに基づく

---

### TC-004: buildUri の基本構造検証

- **テスト名**: buildUri が正しい scheme・host・クエリパラメータを持つ URI を生成する
  - **何をテストするか**: `NoteComposer.buildUri()` が `obsidian://new?...` 形式の URI を生成すること
  - **期待される動作**: scheme="obsidian", host="new", パラメータに content/title/vault/folder が含まれる
- **入力値**: `content="---\ntitle: \"タイトル\"\ntags: [shared]\n---\n\n本文"`, `title="タイトル"`, `config=NoteConfig(vault="testVault", folder="70_clippings", defaultTags=emptyList())`
  - **入力データの意味**: buildFrontmatter() の出力を content として渡す典型的な使用パターン
- **期待される結果**: URI の scheme が "obsidian"、host が "new"、クエリパラメータに "content"/"title"/"vault"/"folder" が含まれる
  - **期待結果の理由**: REQ-101・ObsidianUriBuilder 既存実装の URI 構造と同等であること
- **テストの目的**: URI 構造の基本的な正確性を確認
  - **確認ポイント**: scheme, host, 各クエリパラメータの存在と値
- 🔵 **青信号**: REQ-101・ObsidianUriBuilder 既存実装に基づく

---

### TC-005: NoteConfig.fromAppConfig() の正常動作

- **テスト名**: fromAppConfig() が AppConfig の値を正確に読み込む
  - **何をテストするか**: ファクトリメソッドが AppConfig の定数値を NoteConfig に正しくマッピングすること
  - **期待される動作**: NoteConfig の各フィールドが AppConfig の対応する値と一致する
- **入力値**: AppConfig の既定値（`OBSIDIAN_VAULT="testVault"`, `OBSIDIAN_FOLDER="70_clippings"`, `OBSIDIAN_TAGS=listOf("shared")`)
  - **入力データの意味**: アプリのデフォルト設定値。将来のユーザー設定化に備えたファクトリメソッドの動作確認
- **期待される結果**: `config.vault=="testVault"`, `config.folder=="70_clippings"`, `config.defaultTags==listOf("shared")`
  - **期待結果の理由**: REQ-405「保存先フォルダの初期値は AppConfig.OBSIDIAN_FOLDER を使用」に準拠
- **テストの目的**: AppConfig からの値読み込みが正確であることを確認
  - **確認ポイント**: vault, folder, defaultTags の3フィールドすべてが一致すること
- 🔵 **青信号**: REQ-405・AppConfig.kt の実装値に基づく

---

### TC-006: buildUri のタイトルなしパターン

- **テスト名**: title が null の場合に URI の title パラメータが空文字になる
  - **何をテストするか**: `title=null` 時に `title=""` として URI に設定されること
  - **期待される動作**: URI の title クエリパラメータが空文字列として設定される
- **入力値**: `content="---\ntags: [shared]\n---\n\n本文"`, `title=null`, `config=NoteConfig(vault="v", folder="f", defaultTags=emptyList())`
  - **入力データの意味**: タイトルなしの共有テキストを処理する場合
- **期待される結果**: URI に `title=` パラメータが存在し、値が空文字であること
  - **期待結果の理由**: 実装仕様 `title ?: ""` に基づく。ObsidianUriBuilder の既存動作と同等
- **テストの目的**: null title の処理が既存動作と一致することを確認
  - **確認ポイント**: title パラメータの値が空文字であること
- 🔵 **青信号**: interfaces.kt `title ?: ""`・ObsidianUriBuilder 既存実装に基づく

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-007: 空本文での Frontmatter 生成

- **テスト名**: 本文が空文字列の場合でも正常に Frontmatter が生成される
  - **エラーケースの概要**: ユーザーが本文を空のまま送信しようとした場合
  - **エラー処理の重要性**: 空本文でもクラッシュせずに Obsidian に送信できること（空ノート送信）
- **入力値**: `title="タイトル"`, `body=""`, `tags=listOf("shared")`
  - **不正な理由**: 本文が空であること自体は許容されるが、境界的なケース
  - **実際の発生シナリオ**: ユーザーが本文を削除して送信した場合
- **期待される結果**: `"---\ntitle: \"タイトル\"\ntags: [shared]\n---\n\n"`
  - **エラーメッセージの内容**: エラーなし。空ノートとして正常に生成される
  - **システムの安全性**: 空文字列は有効な入力として処理される
- **テストの目的**: 空本文が例外を発生させずに処理されることを確認
  - **品質保証の観点**: EDGE-002 仕様に準拠し、ロバストな入力処理を保証
- 🟡 **黄信号**: EDGE-002 から妥当な推測（明示的なテストケース指定はないが仕様から推定）

---

### TC-008: タイトルと本文の両方が空の Frontmatter 生成

- **テスト名**: タイトル null・本文空文字列の場合でも正常に Frontmatter が生成される
  - **エラーケースの概要**: 最小限の入力での動作
  - **エラー処理の重要性**: 極端なケースでもクラッシュしないこと
- **入力値**: `title=null`, `body=""`, `tags=listOf("shared")`
  - **不正な理由**: 実質的に中身のないノート。ユーザーが意図せず送信する可能性
  - **実際の発生シナリオ**: 共有元がタイトルも本文も空で送信した場合
- **期待される結果**: `"---\ntags: [shared]\n---\n\n"`
  - **エラーメッセージの内容**: エラーなし。空ノートとして生成
  - **システムの安全性**: システムは安全に処理を完了する
- **テストの目的**: 最小入力でのクラッシュ耐性を確認
  - **品質保証の観点**: null と空文字の組み合わせでの堅牢性
- 🟡 **黄信号**: EDGE-001 + EDGE-002 の組み合わせから妥当な推測

---

### TC-009: 特殊文字を含むタイトルの Frontmatter 生成

- **テスト名**: タイトルにダブルクォートを含む場合の Frontmatter 生成
  - **エラーケースの概要**: YAML の Frontmatter でタイトルがダブルクォートで囲まれるため、タイトル内のダブルクォートが問題になる可能性
  - **エラー処理の重要性**: Obsidian が Frontmatter を正しくパースできなくなるリスク
- **入力値**: `title="Hello \"World\""`, `body="body"`, `tags=listOf("shared")`
  - **不正な理由**: ダブルクォートが Frontmatter の title フィールドの区切り文字と衝突する
  - **実際の発生シナリオ**: Web ページタイトルにダブルクォートが含まれる場合
- **期待される結果**: `"---\ntitle: \"Hello \"World\"\"\ntags: [shared]\n---\n\nbody"`
  - **エラーメッセージの内容**: 現状の実装ではエスケープ処理なし（既存 FrontmatterBuilder と同等の動作）
  - **システムの安全性**: 既存動作との互換性を維持
- **テストの目的**: 特殊文字を含むタイトルが既存 FrontmatterBuilder と同等の方法で処理されることを確認
  - **品質保証の観点**: 既存テスト `FrontmatterBuilderTest.build with title containing special characters` と同等の動作を確認
- 🔵 **青信号**: 既存 FrontmatterBuilderTest の同等テストケースに基づく

---

## 3. 境界値テストケース（最小値、最大値、null 等）

### TC-010: 空タグリストの Frontmatter 生成

- **テスト名**: タグリストが空の場合に `tags: []` が出力される
  - **境界値の意味**: タグリストの最小サイズ（0個）。ユーザーがすべてのタグを削除した場合
  - **境界値での動作保証**: 空リストでも `tags:` フィールドは出力され、値が `[]` となること
- **入力値**: `title=null`, `body="本文"`, `tags=emptyList()`
  - **境界値選択の根拠**: EDGE-003 で明示的に定義された境界条件
  - **実際の使用場面**: ユーザーがタグフィールドをすべて削除して送信した場合
- **期待される結果**: `"---\ntags: []\n---\n\n本文"`
  - **境界での正確性**: `joinToString(", ")` が空リストに対して空文字を返すことを確認
  - **一貫した動作**: tags フィールド自体は常に出力される（省略されない）
- **テストの目的**: 空タグリスト時のフォーマット処理の正確性を確認
  - **堅牢性の確認**: EDGE-003 仕様への準拠
- 🔵 **青信号**: EDGE-003・requirements.md に基づく

---

### TC-011: 単一タグの Frontmatter 生成

- **テスト名**: タグが1つだけの場合にカンマなしで出力される
  - **境界値の意味**: タグリストの最小有効サイズ（1個）。カンマ区切りの境界
  - **境界値での動作保証**: 単一タグではカンマが出力されないこと
- **入力値**: `title="メモ"`, `body="内容"`, `tags=listOf("shared")`
  - **境界値選択の根拠**: `joinToString(", ")` の単一要素時の動作を確認
  - **実際の使用場面**: デフォルトタグ "shared" のみの状態
- **期待される結果**: `"---\ntitle: \"メモ\"\ntags: [shared]\n---\n\n内容"`
  - **境界での正確性**: カンマが出力されないこと
  - **一貫した動作**: 1個でも複数でも tags フォーマットが一貫していること
- **テストの目的**: 単一タグ時の `joinToString` 動作を確認
  - **堅牢性の確認**: タグ数 0, 1, 2+ の全パターンをカバー
- 🔵 **青信号**: 既存 FrontmatterBuilder テスト・Kotlin joinToString 仕様に基づく

---

### TC-012: NoteConfig のデータクラス等価性

- **テスト名**: 同一パラメータの NoteConfig インスタンスが等価と判定される
  - **境界値の意味**: data class の equals/hashCode が正しく動作することの確認
  - **境界値での動作保証**: Kotlin data class の標準動作が正しいことを検証
- **入力値**: `NoteConfig(vault="v", folder="f", defaultTags=listOf("t"))` を2つ生成
  - **境界値選択の根拠**: data class の等価性はテストやコレクション操作で重要
  - **実際の使用場面**: テスト内での比較、将来的なキャッシュや比較ロジック
- **期待される結果**: `config1 == config2` が `true`、`config1.hashCode() == config2.hashCode()` が `true`
  - **境界での正確性**: Kotlin data class が自動生成する equals/hashCode の正確性
  - **一貫した動作**: 同一パラメータなら常に等価
- **テストの目的**: data class としての基本的な等価性を確認
  - **堅牢性の確認**: data class の構造的等価性が保証されること
- 🟡 **黄信号**: Kotlin data class の標準動作からの妥当な推測（要件には明示されていない）

---

### TC-013: 長い本文の Frontmatter 生成

- **テスト名**: 長い本文（改行を含む複数行テキスト）が正しく処理される
  - **境界値の意味**: 本文サイズの上限付近での動作。改行文字を含む複合的な入力
  - **境界値での動作保証**: 改行や複数行のテキストが Frontmatter と正しく分離されること
- **入力値**: `title="記事"`, `body="第一段落\n\n第二段落\n\n第三段落"`, `tags=listOf("shared")`
  - **境界値選択の根拠**: 実際の共有テキストは改行を含む複数段落であることが多い
  - **実際の使用場面**: Web 記事やメモの共有
- **期待される結果**: `"---\ntitle: \"記事\"\ntags: [shared]\n---\n\n第一段落\n\n第二段落\n\n第三段落"`
  - **境界での正確性**: 本文の改行がそのまま保持されること
  - **一貫した動作**: Frontmatter 終端の `---\n\n` と本文の改行が混同されないこと
- **テストの目的**: 複数行本文がそのまま出力されることを確認
  - **堅牢性の確認**: 改行文字の処理が正確であること
- 🟡 **黄信号**: 一般的なテスト設計パターンからの妥当な推測

---

### TC-014: buildUri の vault/folder パラメータ検証

- **テスト名**: NoteConfig の vault と folder が URI に正しく反映される
  - **境界値の意味**: URI パラメータに異なる config 値が正確に反映されることを確認
  - **境界値での動作保証**: config の値が title/content とは独立して URI に設定されること
- **入力値**: `content="test"`, `title="t"`, `config=NoteConfig(vault="myVault", folder="inbox/clippings", defaultTags=emptyList())`
  - **境界値選択の根拠**: フォルダパスにスラッシュを含むケース。URL エンコーディングの確認
  - **実際の使用場面**: ユーザーがサブフォルダを指定した場合
- **期待される結果**: URI の vault パラメータが "myVault"、folder パラメータが "inbox/clippings" であること
  - **境界での正確性**: `appendQueryParameter()` による URL エンコーディングが適切に行われること
  - **一貫した動作**: パスセパレータを含む folder 値が正しく処理されること
- **テストの目的**: 任意の config 値が URI に正確に反映されることを確認
  - **堅牢性の確認**: URI パラメータのエンコーディング処理
- 🟡 **黄信号**: ObsidianUriBuilder の動作から妥当な推測

---

## 4. 統合テストケース

### TC-015: NoteComposer と FrontmatterBuilder の出力互換性

- **テスト名**: NoteComposer.buildFrontmatter() が FrontmatterBuilder.build() と同等の出力を生成する
  - **何をテストするか**: AppConfig.OBSIDIAN_TAGS を tags パラメータとして渡した場合、FrontmatterBuilder.build() と同一の文字列が生成されること
  - **期待される動作**: 同一入力に対して両ビルダーが同一の文字列を返す
- **入力値**: `title="テスト"`, `body="本文"`, `tags=AppConfig.OBSIDIAN_TAGS`（NoteComposer 側）/ `title="テスト"`, `body="本文"`（FrontmatterBuilder 側）
  - **入力データの意味**: REQ-402 の後方互換性要件。既存ビルダーとの出力が同等であること
- **期待される結果**: `NoteComposer.buildFrontmatter(title, body, AppConfig.OBSIDIAN_TAGS) == FrontmatterBuilder.build(title, body)`
  - **期待結果の理由**: REQ-402「既存ビルダーを変更しない」に関連し、新旧ビルダーの出力互換性を保証
- **テストの目的**: 後方互換性の確認
  - **確認ポイント**: タイトルあり・なしの両パターンで同一出力であること
- 🟡 **黄信号**: REQ-402・既存 FrontmatterBuilder 実装から妥当な推測

---

## 5. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: プロジェクト全体が Kotlin で実装されている。CLAUDE.md に明記
  - **テストに適した機能**: null safety、data class の自動 equals/hashCode、バッククォートによるテスト名の日本語記述
- **テストフレームワーク**: JUnit 4 + Robolectric
  - **フレームワーク選択の理由**: 既存テスト（`FrontmatterBuilderTest`、`ObsidianUriBuilderTest`）が JUnit 4 を使用。`android.net.Uri` を使用するテストには Robolectric が必要（`app/build.gradle.kts` に依存関係あり）
  - **テスト実行環境**: `app/src/test/` ディレクトリ（ローカル JVM ユニットテスト）。`./gradlew test` で実行
- 🔵 **青信号**: 既存テストコード・`app/build.gradle.kts`・CLAUDE.md に基づく

---

## 6. テストケース実装時の日本語コメント指針

各テストケースの実装時には以下の Kotlin コメントを含めてください。

### テストケース開始時のコメント

```kotlin
// 【テスト目的】: [このテストで何を確認するかを日本語で明記]
// 【テスト内容】: [具体的にどのような処理をテストするかを説明]
// 【期待される動作】: [正常に動作した場合の結果を説明]
// 🔵🟡🔴 この内容の信頼性レベルを記載
```

### Given（準備フェーズ）のコメント

```kotlin
// 【テストデータ準備】: [なぜこのデータを用意するかの理由]
// 【初期条件設定】: [テスト実行前の状態を説明]
// 【前提条件確認】: [テスト実行に必要な前提条件を明記]
```

### When（実行フェーズ）のコメント

```kotlin
// 【実際の処理実行】: [どの機能/メソッドを呼び出すかを説明]
// 【処理内容】: [実行される処理の内容を日本語で説明]
// 【実行タイミング】: [なぜこのタイミングで実行するかを説明]
```

### Then（検証フェーズ）のコメント

```kotlin
// 【結果検証】: [何を検証するかを具体的に説明]
// 【期待値確認】: [期待される結果とその理由を説明]
// 【品質保証】: [この検証がシステム品質にどう貢献するかを説明]
```

### 各 assert ステートメントのコメント

```kotlin
// 【検証項目】: [この検証で確認している具体的な項目]
// 🔵🟡🔴 この内容の信頼性レベルを記載
assertEquals(expected, actual) // 【確認内容】: Frontmatter 文字列が期待通りであることを確認
```

### セットアップ・クリーンアップのコメント

```kotlin
@Before
fun setUp() {
    // 【テスト前準備】: NoteConfig のテスト用インスタンスを作成
    // 【環境初期化】: 各テストで共通の設定値を準備
}
```

---

## 7. テストファイル構成

### 単体テスト（Pure Kotlin - Robolectric 不要）

**ファイル**: `app/src/test/java/com/den4dr/share2Obsidian/format/NoteComposerTest.kt`

テストケース:
- TC-001: タイトルありの Frontmatter 生成
- TC-002: タイトルなしの Frontmatter 生成
- TC-003: 複数タグの Frontmatter 生成
- TC-005: NoteConfig.fromAppConfig() の正常動作
- TC-007: 空本文での Frontmatter 生成
- TC-008: タイトルと本文の両方が空の Frontmatter 生成
- TC-009: 特殊文字を含むタイトルの Frontmatter 生成
- TC-010: 空タグリストの Frontmatter 生成
- TC-011: 単一タグの Frontmatter 生成
- TC-012: NoteConfig のデータクラス等価性
- TC-013: 長い本文の Frontmatter 生成
- TC-015: NoteComposer と FrontmatterBuilder の出力互換性

### 単体テスト（Robolectric 必要 - Uri 使用）

**ファイル**: `app/src/test/java/com/den4dr/share2Obsidian/format/NoteComposerTest.kt`（同一ファイル内、`@RunWith(RobolectricTestRunner::class)` 使用）

テストケース:
- TC-004: buildUri の基本構造検証
- TC-006: buildUri のタイトルなしパターン
- TC-014: buildUri の vault/folder パラメータ検証

---

## 8. 要件定義との対応関係

| テストケース | 参照した要件 | 参照したセクション |
|-------------|-------------|-------------------|
| TC-001 | REQ-101, TC-101-01 | 機能概要 2.2, 使用例 4.1 |
| TC-002 | EDGE-001 | 入出力仕様 2.2, 使用例 4.2 |
| TC-003 | REQ-103 | 入出力仕様 2.2 |
| TC-004 | REQ-101 | 入出力仕様 2.3 |
| TC-005 | REQ-405 | 入出力仕様 2.1, 使用例 4.4 |
| TC-006 | REQ-101 | 入出力仕様 2.3 |
| TC-007 | EDGE-002 | 使用例 4.5 |
| TC-008 | EDGE-001 + EDGE-002 | 使用例 4.2, 4.5 |
| TC-009 | - | 既存 FrontmatterBuilderTest |
| TC-010 | EDGE-003 | 入出力仕様 2.2, 使用例 4.3 |
| TC-011 | REQ-103 | 入出力仕様 2.2 |
| TC-012 | - | interfaces.kt NoteConfig 定義 |
| TC-013 | - | 一般的なテスト設計 |
| TC-014 | REQ-405 | 入出力仕様 2.3, 制約条件 3.2 |
| TC-015 | REQ-402 | 制約条件 3.1, 使用例 4.6 |

- **参照した機能概要**: 要件定義書 セクション1（機能の概要）
- **参照した入力・出力仕様**: 要件定義書 セクション2（入力・出力の仕様 - NoteConfig, buildFrontmatter, buildUri）
- **参照した制約条件**: 要件定義書 セクション3（制約条件 - REQ-402 既存コンポーネント非変更, AppConfig 非依存設計）
- **参照した使用例**: 要件定義書 セクション4（想定される使用例 - 4.1〜4.6）

---

## 信頼性レベルサマリー

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 正常系テストケース | 6 | 0 | 0 | 6 |
| 異常系テストケース | 1 | 2 | 0 | 3 |
| 境界値テストケース | 2 | 3 | 0 | 5 |
| 統合テストケース | 0 | 1 | 0 | 1 |
| 開発言語・フレームワーク | 1 | 0 | 0 | 1 |
| **合計** | **10** | **6** | **0** | **16** |

- **総項目数**: 16項目
- 🔵 **青信号**: 10項目 (63%)
- 🟡 **黄信号**: 6項目 (37%)
- 🔴 **赤信号**: 0項目 (0%)

**品質評価**: ✅ 高品質

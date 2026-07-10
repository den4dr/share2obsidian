# TDDテストケース定義書: LlmSettings・LlmSettingsRepository・LlmSettingsRepositoryImpl

**機能名**: llm-settings-repository
**タスクID**: TASK-0058
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0058/llm-settings-repository-testcases.md`

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: 要件定義書・設計文書・既存実装を参考にしてほぼ推測していない
- 🟡 **黄信号**: 要件定義書・設計文書から妥当な推測をしている
- 🔴 **赤信号**: 要件定義書・設計文書にない推測をしている

---

## 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: プロジェクト全体が Kotlin で統一されており、Coroutines Flow・DataStore・data class など本タスクで必要な機能が言語標準／公式ライブラリで提供される。
  - **テストに適した機能**: `data class` の構造的等価比較（`equals`）、`runTest`/`runBlocking` による suspend 関数のテスト、`Flow.first()` による最初の発行値取得が容易。
- **テストフレームワーク**: JUnit 4 + Robolectric（ユニット）/ AndroidJUnit4（計器テスト）+ MockK + kotlinx-coroutines-test
  - **フレームワーク選択の理由**: 既存 `NoteSettingsRepositoryImplTest`（`AndroidJUnit4` + 実 DataStore）のパターンを踏襲。DataStore の実 IO は Robolectric/計器テストで検証し、EncryptedSharedPreferences の暗号化確認は実機／エミュレータ必須のため androidTest に分離する。
  - **テスト実行環境**:
    - ユニット/DataStore: `mise exec -- ./gradlew test --tests "*LlmSettings*"`
    - 計器テスト（EncryptedSharedPreferences 暗号化確認）: `mise exec -- ./gradlew connectedAndroidTest --tests "*LlmSettings*"`（デバイス/エミュレータ必須）
- 🔵 この内容の信頼性レベル: note.md「5. テスト関連情報」・既存 `NoteSettingsRepositoryImplTest.kt` より

### テストファイル配置

| テスト種別 | ファイルパス |
|-----------|-------------|
| ユニット／DataStore（Robolectric or AndroidJUnit4） | `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt` |
| 計器テスト（実 EncryptedSharedPreferences 暗号化確認） | `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt` |

---

## テストケース一覧（サマリー）

| No. | 分類 | テスト名 | 種別 | 信頼性 |
|-----|------|---------|------|--------|
| TC-01 | 正常系 | saveEndpointUrl/saveModel 保存後に getSettings() が更新値を返す | ユニット | 🔵 |
| TC-02 | 正常系 | saveApiKey 保存後に getSettings() が更新値を返す | ユニット | 🔵 |
| TC-03 | 正常系 | LlmSettings のデフォルト値が空文字列である | ユニット | 🔵 |
| TC-04 | 正常系 | 3項目すべて保存後に統合された LlmSettings を返す | ユニット | 🟡 |
| TC-05 | 正常系 | saveApiKey が DataStore ではなく EncryptedSharedPreferences に保存する | ユニット | 🔵 |
| TC-06 | 正常系 | 保存後に再保存（上書き）すると最新値が返る | ユニット | 🟡 |
| TC-07 | 異常系 | apiKey が平文 DataStore に一切保存されない（REQ-401） | ユニット | 🔵 |
| TC-08 | 異常系 | apiKey が SharedPreferences ファイルに平文で含まれない（統合） | 計器 | 🔵 |
| TC-09 | 異常系 | callbackFlow が collect 終了時にリスナー登録解除する | ユニット | 🟡 |
| TC-10 | 境界値 | 初期状態（未保存）では LlmSettings("","","") を返す | ユニット | 🔵 |
| TC-11 | 境界値 | 空文字列の保存が可能（値のクリア） | ユニット | 🟡 |
| TC-12 | 境界値 | DataStore と EncryptedSharedPreferences の変更が独立して反映される | ユニット | 🟡 |
| TC-13 | 境界値 | 長い文字列・特殊文字を含む値の保存・読み出し | ユニット | 🟡 |

---

## 1. 正常系テストケース（基本的な動作）

### TC-01: saveEndpointUrl/saveModel 保存後に getSettings() が更新値を返す

- **テスト名**: DataStore 側設定（endpointUrl / model）の保存と読み出し
  - **何をテストするか**: `saveEndpointUrl()` と `saveModel()` で DataStore に保存した値が、`getSettings()` の発行する `LlmSettings` に正しく反映されること。
  - **期待される動作**: DataStore への書き込み → `dataStore.data` Flow の再発行 → `combine()` 経由で `getSettings()` に反映される。
- **入力値**: `saveEndpointUrl("https://api.example.com/v1/chat")`、`saveModel("gpt-4o-mini")`
  - **入力データの意味**: OpenAI 互換 Chat Completions エンドポイントとモデル名の代表的な実値。REQ-004 が想定する入力形式。
- **期待される結果**: `getSettings().first()` が `endpointUrl == "https://api.example.com/v1/chat"` かつ `model == "gpt-4o-mini"` を含む `LlmSettings` を返す。
  - **期待結果の理由**: 既存 `NoteSettingsRepositoryImpl` と同一の DataStore 読み書きパターンであり、書き込んだ値がそのまま読み出せることが仕様（TASK-0058 完了条件）。
- **テストの目的**: DataStore 保存系メソッドの正常動作確認。
  - **確認ポイント**: `endpointUrl` と `model` が独立して正しいキー（`llm_endpoint_url` / `llm_model`）に保存され、混同されないこと。
- 🔵 信頼性レベル: TASK-0058「テストケース1」・既存 `NoteSettingsRepositoryImplTest` パターンより

### TC-02: saveApiKey 保存後に getSettings() が更新値を返す

- **テスト名**: EncryptedSharedPreferences 側設定（apiKey）の保存と読み出し
  - **何をテストするか**: `saveApiKey()` で EncryptedSharedPreferences（またはモック SharedPreferences）に保存した値が `getSettings()` に反映されること。
  - **期待される動作**: EncryptedSharedPreferences への書き込み → `OnSharedPreferenceChangeListener` 発火 → `callbackFlow` 再発行 → `combine()` 経由で `getSettings()` に反映。
- **入力値**: `saveApiKey("sk-test-12345")`
  - **入力データの意味**: OpenAI 形式 API キーの代表的なプレフィックス（`sk-`）を含むダミー値。機微情報の保存経路を代表する。
- **期待される結果**: `getSettings().first()` が `apiKey == "sk-test-12345"` を含む `LlmSettings` を返す。
  - **期待結果の理由**: `saveApiKey()` は EncryptedSharedPreferences に書き込み、`getSettings()` の `encryptedApiKeyFlow()` がその値を発行するため（architecture.md「LLM設定管理設計」）。
- **テストの目的**: EncryptedSharedPreferences 保存系メソッドの正常動作確認。
  - **確認ポイント**: `apiKey` が DataStore 側の `endpointUrl`/`model` とは別ソースから正しく合成されること。
- 🔵 信頼性レベル: TASK-0058「テストケース2」より

### TC-03: LlmSettings のデフォルト値が空文字列である

- **テスト名**: LlmSettings data class のデフォルト値検証
  - **何をテストするか**: `LlmSettings()`（引数なし）が全フィールド空文字列で構築されること。
  - **期待される動作**: `LlmSettings() == LlmSettings("", "", "")`。
- **入力値**: `LlmSettings()`（デフォルトコンストラクタ）
  - **入力データの意味**: 未設定状態を表すドメインモデルの初期値。
- **期待される結果**: `endpointUrl == ""`、`apiKey == ""`、`model == ""`。
  - **期待結果の理由**: interfaces.kt / requirements.md 2.1 で全フィールドのデフォルト値を `""` と定義しているため。
- **テストの目的**: ドメインモデルのデフォルト値契約の確認。
  - **確認ポイント**: フィールド順（endpointUrl, apiKey, model）とデフォルト値が仕様どおりであること。
- 🔵 信頼性レベル: interfaces.kt デフォルト値定義・requirements.md 2.1 より

### TC-04: 3項目すべて保存後に統合された LlmSettings を返す

- **テスト名**: 全項目保存後の統合読み出し
  - **何をテストするか**: DataStore 側（endpointUrl / model）と EncryptedSharedPreferences 側（apiKey）の両方を保存した状態で、`getSettings()` が 3項目すべてを含む単一の `LlmSettings` を返すこと。
  - **期待される動作**: 2つの Flow が `combine()` され、最新の全項目を含む `LlmSettings` が発行される。
- **入力値**: `saveEndpointUrl("https://api.example.com/v1/chat")`、`saveModel("gpt-4o-mini")`、`saveApiKey("sk-test-abc")`
  - **入力データの意味**: 実利用時の完全な設定入力を代表するデータセット。
- **期待される結果**: `getSettings().first()` が `LlmSettings("https://api.example.com/v1/chat", "sk-test-abc", "gpt-4o-mini")` と等価。
  - **期待結果の理由**: `combine()` は両ソースの最新値を結合するため、全項目が揃った状態が発行される（データフロー 4.2）。
- **テストの目的**: 2ソース Flow 合成の統合動作確認。
  - **確認ポイント**: 3項目の値が取り違えなく正しいフィールドに割り当てられること。
- 🟡 信頼性レベル: requirements.md データフロー 4.2 からの妥当な推測（`combine` 合成の一般挙動）

### TC-05: saveApiKey が DataStore ではなく EncryptedSharedPreferences に保存する

- **テスト名**: apiKey の保存先分離検証（ユニット・MockK）
  - **何をテストするか**: `saveApiKey()` 呼び出しが EncryptedSharedPreferences（`encryptedPrefs.edit().putString(API_KEY_KEY, ...)`）に対して行われ、DataStore には書き込まれないこと。
  - **期待される動作**: `encryptedPrefs` の `putString("llm_api_key", ...)` が呼ばれる。DataStore の `edit` は apiKey に関して呼ばれない。
- **入力値**: `saveApiKey("sk-verify-target")`（`encryptedPrefs` を MockK でモック）
  - **入力データの意味**: 保存経路そのものを検証するためのダミー API キー。
- **期待される結果**: MockK `verify` で `encryptedPrefs.edit()...putString(API_KEY_KEY, "sk-verify-target")` が呼ばれたことを確認。DataStore へ `llm_api_key` 相当のキーが書かれないこと。
  - **期待結果の理由**: REQ-401「平文 DataStore への保存禁止」の実装レベル保証。
- **テストの目的**: 保存先ルーティングの正しさ確認。
  - **確認ポイント**: apiKey が DataStore 経路に漏れないこと。
- 🔵 信頼性レベル: TASK-0058 実装詳細 §3・REQ-401 より

### TC-06: 保存後に再保存（上書き）すると最新値が返る

- **テスト名**: 設定値の上書き更新
  - **何をテストするか**: 同一項目を2回保存した際、`getSettings()` が2回目（最新）の値を返すこと。
  - **期待される動作**: DataStore/EncryptedSharedPreferences ともに last-write-wins で最新値を保持。
- **入力値**: `saveModel("gpt-4o-mini")` の後に `saveModel("gpt-4o")`
  - **入力データの意味**: ユーザーが設定画面で値を変更する実運用シナリオ。
- **期待される結果**: `getSettings().first().model == "gpt-4o"`。
  - **期待結果の理由**: DataStore の `edit` は値を上書きするため、最新値のみが読み出せる。
- **テストの目的**: 更新・冪等性の確認。
  - **確認ポイント**: 古い値が残存しないこと。
- 🟡 信頼性レベル: DataStore の一般挙動からの妥当な推測

---

## 2. 異常系テストケース（エラーハンドリング・セキュリティ）

### TC-07: apiKey が平文 DataStore に一切保存されない（REQ-401）

- **テスト名**: DataStore 平文保存禁止の検証
  - **エラーケースの概要**: apiKey が誤って DataStore（平文 Preferences）に保存されてしまう情報漏洩リスクを防止する。
  - **エラー処理の重要性**: REQ-401 の中核要件。平文保存は機微情報漏洩の直接原因となる。
- **入力値**: `saveApiKey("sk-should-not-be-plain")`、その後 DataStore の全 Preferences を走査
  - **不正な理由**: apiKey が DataStore に含まれること自体が仕様違反（不正状態）。
  - **実際の発生シナリオ**: 実装ミスで `saveApiKey()` が DataStore.edit を呼んでしまう、キー分離漏れ等。
- **期待される結果**: DataStore の `data.first()` の全 Preferences エントリに `"sk-should-not-be-plain"` という値が含まれない。`llm_api_key` キーも DataStore に存在しない。
  - **エラーメッセージの内容**: （保存系メソッドはエラーメッセージを返さない設計。検証はテスト側のアサーションで実施。）
  - **システムの安全性**: apiKey が平文ストレージに残らないことでファイルアクセス時の漏洩を防ぐ。
- **テストの目的**: 平文保存禁止の実装保証。
  - **品質保証の観点**: セキュリティ要件（REQ-401 / NFR-101）の回帰防止。
- 🔵 信頼性レベル: TASK-0058 完了条件・REQ-401 より

### TC-08: apiKey が SharedPreferences ファイルに平文で含まれない（統合テスト）

- **テスト名**: EncryptedSharedPreferences 暗号化確認（計器テスト）
  - **エラーケースの概要**: 実 `EncryptedSharedPreferences.create()` で保存した apiKey がファイル実体に平文で書かれていないこと。
  - **エラー処理の重要性**: NFR-101（暗号化保存）の実効性を実ストレージで検証する唯一の手段。
- **入力値**: 実 `EncryptedSharedPreferences`（`MasterKey` + `AES256_SIV` / `AES256_GCM`）で `saveApiKey("sk-test-secret")` を実行
  - **不正な理由**: 保存後のファイルに平文 `"sk-test-secret"` が含まれていれば暗号化されていない＝仕様違反。
  - **実際の発生シナリオ**: MasterKey/暗号化スキーム設定ミス、通常 SharedPreferences の誤用。
- **期待される結果**: 対象 SharedPreferences ファイル（XML 実体）を読み込んだ内容に `"sk-test-secret"` の平文文字列が含まれない。
  - **エラーメッセージの内容**: （検証はファイル内容アサーションで実施。）
  - **システムの安全性**: 端末内ファイル流出時も apiKey が復号されない限り読み取れない。
- **テストの目的**: 暗号化の実効性確認。
  - **品質保証の観点**: NFR-101 の実測検証（モックでは代替不可）。
- 🔵 信頼性レベル: TASK-0058「統合テスト1」・NFR-101 より

### TC-09: callbackFlow が collect 終了時にリスナー登録解除する

- **テスト名**: OnSharedPreferenceChangeListener のリーク防止検証
  - **エラーケースの概要**: `encryptedApiKeyFlow()` の collect 終了後もリスナーが登録されたまま残るとメモリリーク・多重通知が発生する。
  - **エラー処理の重要性**: `callbackFlow` の `awaitClose` で登録解除しないとリスナーが蓄積する（本プロジェクトで実績のないパターンのため要検証）。
- **入力値**: `encryptedPrefs` を MockK でモックし、`getSettings()` の collect を開始→キャンセル
  - **不正な理由**: collect 終了後に `unregisterOnSharedPreferenceChangeListener` が呼ばれないのは実装漏れ。
  - **実際の発生シナリオ**: ViewModel のスコープ破棄時に Flow collect がキャンセルされる。
- **期待される結果**: MockK `verify` で `registerOnSharedPreferenceChangeListener` と対になる `unregisterOnSharedPreferenceChangeListener` が呼ばれる。
  - **エラーメッセージの内容**: （検証は verify アサーションで実施。）
  - **システムの安全性**: リスナーリークによるメモリ増加・不要コールバックを防止。
- **テストの目的**: リソース解放の確認。
  - **品質保証の観点**: 未検証パターン（callbackFlow + Listener）の動作保証（note.md 注意事項）。
- 🟡 信頼性レベル: note.md「6. 注意事項 §2」・callbackFlow 一般パターンからの妥当な推測

---

## 3. 境界値テストケース（初期値・空文字・独立更新）

### TC-10: 初期状態（未保存）では LlmSettings("","","") を返す

- **テスト名**: 未初期化状態のデフォルト読み出し
  - **境界値の意味**: いずれのキーも未保存という最小状態。アプリ初回起動直後を代表する。
  - **境界値での動作保証**: `?: ""` フォールバックが全項目で機能すること。
- **入力値**: 何も保存していない `LlmSettingsRepositoryImpl` に対し `getSettings().first()`
  - **境界値選択の根拠**: 保存前の初期状態は必ず通過する境界。
  - **実際の使用場面**: 設定画面を一度も開いていないユーザーの状態。
- **期待される結果**: `LlmSettings(endpointUrl = "", apiKey = "", model = "")` と等価な値が返る。
  - **境界での正確性**: 各ソースの未設定値が空文字列に正規化される。
  - **一貫した動作**: DataStore 側・EncryptedSharedPreferences 側どちらも未設定なら空文字列。
- **テストの目的**: 初期状態の堅牢性確認。
  - **堅牢性の確認**: null や例外ではなく空文字列で安全に初期化されること。
- 🔵 信頼性レベル: TASK-0058「テストケース3」・requirements.md 4.3 より

### TC-11: 空文字列の保存が可能（値のクリア）

- **テスト名**: 空文字列による設定クリア
  - **境界値の意味**: 保存済みの値を空文字列で上書きし「クリア」する境界動作。
  - **境界値での動作保証**: 空文字列が有効な保存値として扱われること。
- **入力値**: `saveEndpointUrl("https://x")` の後に `saveEndpointUrl("")`
  - **境界値選択の根拠**: 空文字列は最小長の入力であり、未保存状態との区別が問われる境界。
  - **実際の使用場面**: ユーザーが設定欄を空にして保存するケース。
- **期待される結果**: `getSettings().first().endpointUrl == ""`。
  - **境界での正確性**: 空文字列保存が例外なく成功し、読み出しでも空文字列になる。
  - **一貫した動作**: 未保存時の `""` と保存後の `""` が区別なく安全に扱える。
- **テストの目的**: 空値の受容性確認。
  - **堅牢性の確認**: 空文字列でクラッシュしないこと。
- 🟡 信頼性レベル: DataStore の一般挙動からの妥当な推測（要件に明示なし）

### TC-12: DataStore と EncryptedSharedPreferences の変更が独立して反映される

- **テスト名**: 2ソースの独立更新検証
  - **境界値の意味**: 片方のソースのみ更新した際、もう片方が保持される合成境界。
  - **境界値での動作保証**: `combine()` が片側更新でも他側の最新値を保持すること。
- **入力値**: `saveEndpointUrl(...)` / `saveModel(...)` 保存済みの状態で `saveApiKey("sk-only")` のみ実行
  - **境界値選択の根拠**: 2ソース合成の相互干渉が最も起きやすい部分更新の境界。
  - **実際の使用場面**: ユーザーが apiKey だけ後から入力・変更するケース。
- **期待される結果**: `getSettings().first()` の `endpointUrl`/`model` は変更前の値を維持し、`apiKey` のみ `"sk-only"` に更新される。
  - **境界での正確性**: 片側更新が他側の値を消さない。
  - **一貫した動作**: どちらのソースを更新しても対称に動作する。
- **テストの目的**: Flow 合成の独立性確認。
  - **堅牢性の確認**: 部分更新でデータ欠落が起きないこと。
- 🟡 信頼性レベル: TASK-0058「テストケース4」・`combine()` 一般挙動からの妥当な推測

### TC-13: 長い文字列・特殊文字を含む値の保存・読み出し

- **テスト名**: 特殊入力値の保存耐性
  - **境界値の意味**: URL クエリ・記号・マルチバイトを含む値の保存境界。
  - **境界値での動作保証**: 文字化け・切り詰めなく往復できること。
- **入力値**: `saveEndpointUrl("https://api.example.com/v1/chat?x=1&y=あ")`、`saveApiKey("sk-!@#\$%^&*()_+=あ漢")`
  - **境界値選択の根拠**: クエリ記号・非 ASCII・長文字列は保存経路のエンコード不備が顕在化しやすい。
  - **実際の使用場面**: プロキシ経由エンドポイントや特殊なキー形式。
- **期待される結果**: `getSettings().first()` が保存した値と完全一致する（無変更で往復）。
  - **境界での正確性**: DataStore・EncryptedSharedPreferences ともに文字列を無加工で保持する。
  - **一貫した動作**: ASCII でも非 ASCII でも同一挙動。
- **テストの目的**: 入力多様性への堅牢性確認。
  - **堅牢性の確認**: 特殊文字でエンコード例外や欠落が起きないこと。
- 🟡 信頼性レベル: 一般的な文字列ストレージ挙動からの妥当な推測（要件に明示なし）

---

## 4. テストケース実装時の日本語コメント指針

各テストの実装時、以下の構造で日本語コメントを付与する（例: TC-01）。

```kotlin
@Test
fun saveEndpointUrlAndModel_reflectedInGetSettings() = runBlocking {
    // 【テスト目的】: DataStore 側設定(endpointUrl/model)の保存が getSettings() に反映されることを確認
    // 【テスト内容】: saveEndpointUrl/saveModel 実行後に getSettings().first() の値を検証
    // 【期待される動作】: 書き込んだ値がそのまま LlmSettings として読み出せる
    // 🔵 信頼性レベル: TASK-0058 テストケース1

    // 【テストデータ準備】: 一意ファイル名の実 DataStore を持つ Repository を生成（既存テスト衝突回避）
    // 【初期条件設定】: DataStore は未初期化（キー未保存）
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val repo = createRepository(scope)

    // 【実際の処理実行】: DataStore 保存系メソッドを呼び出す
    // 【処理内容】: endpointUrl と model を別キーに書き込む
    repo.saveEndpointUrl("https://api.example.com/v1/chat")
    repo.saveModel("gpt-4o-mini")

    // 【結果検証】: getSettings() の最初の発行値を取得し各フィールドを確認
    val settings = repo.getSettings().first()

    // 【検証項目】: endpointUrl が保存値と一致すること
    assertEquals("https://api.example.com/v1/chat", settings.endpointUrl)
    // 【検証項目】: model が保存値と一致すること
    assertEquals("gpt-4o-mini", settings.model)

    // 【テスト後処理】: coroutine スコープをキャンセルし DataStore リソースを解放
    scope.cancel()
}
```

- **Given（準備）**: 一意名 DataStore / モック `encryptedPrefs` を用意し、他テストと衝突しない初期状態を作る。
- **When（実行）**: `saveXxx()` を呼ぶ、または `getSettings().first()` を collect する。
- **Then（検証）**: `assertEquals` で値一致、または MockK `verify` で保存先・リスナー解除を確認。
- **セットアップ/クリーンアップ**: `CoroutineScope(Dispatchers.IO + SupervisorJob())` を各テストで生成し、末尾 `scope.cancel()` で解放（既存 `NoteSettingsRepositoryImplTest` 準拠）。EncryptedSharedPreferences 計器テストは `@After` で対象ファイルを削除する。

---

## 5. 要件定義との対応関係

- **参照した機能概要**: requirements.md「1. 機能の概要」（LLM API 設定の暗号化分離保存 / Flow 公開）
- **参照した入力・出力仕様**: requirements.md「2. 入力・出力の仕様」（LlmSettings 型 / メソッド対応表 / ストレージキー）
- **参照した制約条件**: requirements.md「3. 制約条件」（REQ-401 平文禁止 / NFR-101 暗号化 / NFR-102 ログ禁止 / Dispatchers.IO / 変更通知パターン）
- **参照した使用例**: requirements.md「4. 想定される使用例」（初期状態・片側更新・暗号化確認のエッジケース）
- **参照したタスク定義**: TASK-0058.md「単体テスト要件」（テストケース1〜4）・「統合テスト要件」（統合テスト1）
- **参照した既存実装**: `app/src/main/java/com/den4dr/share2Obsidian/data/datastore/NoteSettingsRepositoryImpl.kt`・`app/src/androidTest/java/com/den4dr/share2Obsidian/data/datastore/NoteSettingsRepositoryImplTest.kt`

### 要件↔テストケース トレーサビリティ

| 要件 | 対応テストケース |
|------|-----------------|
| REQ-004（endpointUrl/apiKey/model 保存） | TC-01, TC-02, TC-04, TC-06 |
| REQ-401（apiKey 平文 DataStore 禁止） | TC-05, TC-07, TC-08 |
| NFR-101（EncryptedSharedPreferences 暗号化） | TC-08 |
| NFR-102（apiKey ログ非出力） | （実装レビュー＋TC-08 で補完。ログ検証は本タスク範囲外の観点として注記） |
| デフォルト値定義 | TC-03, TC-10 |
| 2ソース Flow 合成 | TC-04, TC-12 |
| リソース解放（callbackFlow） | TC-09 |
| 入力堅牢性 | TC-11, TC-13 |

---

## 6. 品質評価（信頼性レベル分布）

| 分類 | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|------|-------|-------|-------|------|
| 正常系 | 3 | 3 | 0 | 6 |
| 異常系 | 2 | 1 | 0 | 3 |
| 境界値 | 1 | 3 | 0 | 4 |
| **合計** | **6** | **7** | **0** | **13** |

- 🔵 青信号: 6項目（46%）
- 🟡 黄信号: 7項目（54%）
- 🔴 赤信号: 0項目（0%）

**総合品質評価**: ✅ 高品質

- **テストケース分類**: 正常系・異常系・境界値を網羅（保存/読み出し/暗号化/初期値/独立更新/リソース解放）。
- **期待値定義**: 各テストケースに具体的な入力値・期待値を明記。
- **技術選択**: Kotlin + JUnit4 + Robolectric/AndroidJUnit4 + MockK + coroutines-test で確定。
- **実装可能性**: 既存 `NoteSettingsRepositoryImplTest` パターン + AndroidX Security 公式パターンで実現可能。
- **推測を含む項目（🟡）**: `combine()` の合成挙動（TC-04/TC-12）、callbackFlow のリスナー解除（TC-09）、空文字・特殊文字の受容（TC-11/TC-13）は要件に明示がなく妥当な推測。TASK-0058 注意事項どおり tdd-red/tdd-green で動作検証を優先する。

**注記**:
- NFR-102（apiKey のログ・クラッシュレポート非出力）は自動テストでの完全検証が難しく、主に実装レビューで担保する。本テストケース群では TC-08（平文非出力）で間接的にカバーする。
- ユニットテストでの EncryptedSharedPreferences は MockK モックまたは Robolectric 実体で代替し、真の暗号化検証は計器テスト（TC-08）で行う二層構成とする。

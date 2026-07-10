# TDD Redフェーズ記録: LlmSettings・LlmSettingsRepository・LlmSettingsRepositoryImpl

**機能名**: llm-settings-repository
**タスクID**: TASK-0058
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. 実装したテストケース一覧

テストケース定義書（`docs/implements/llm-memo-rewrite/TASK-0058/llm-settings-repository-testcases.md`）の TC-01〜TC-13 を全件実装した（目標10件以上に対し13件、全テストケースを網羅）。

| No. | 分類 | テスト名 | ファイル | 信頼性 |
|-----|------|---------|---------|--------|
| TC-01 | 正常系 | saveEndpointUrl/saveModel 保存後に getSettings() が更新値を返す | `LlmSettingsRepositoryImplTest.kt` | 🔵 |
| TC-02 | 正常系 | saveApiKey 保存後に getSettings() が更新値を返す | `LlmSettingsRepositoryImplTest.kt` | 🔵 |
| TC-03 | 正常系 | LlmSettings のデフォルト値が空文字列である（+ equals 補完テスト） | `LlmSettingsTest.kt` | 🔵 |
| TC-04 | 正常系 | 3項目すべて保存後に統合された LlmSettings を返す | `LlmSettingsRepositoryImplTest.kt` | 🟡 |
| TC-05 | 正常系 | saveApiKey が DataStore ではなく EncryptedSharedPreferences に保存する | `LlmSettingsRepositoryImplInteractionTest.kt` | 🔵 |
| TC-06 | 正常系 | 保存後に再保存（上書き）すると最新値が返る | `LlmSettingsRepositoryImplTest.kt` | 🟡 |
| TC-07 | 異常系 | apiKey が平文 DataStore に一切保存されない（REQ-401） | `LlmSettingsRepositoryImplTest.kt` | 🔵 |
| TC-08 | 異常系 | apiKey が SharedPreferences ファイルに平文で含まれない（計器テスト） | `app/src/androidTest/.../data/llm/LlmSettingsRepositoryImplTest.kt` | 🔵 |
| TC-09 | 異常系 | callbackFlow が collect 終了時にリスナー登録解除する | `LlmSettingsRepositoryImplInteractionTest.kt` | 🟡 |
| TC-10 | 境界値 | 初期状態（未保存）では LlmSettings("","","") を返す | `LlmSettingsRepositoryImplTest.kt` | 🔵 |
| TC-11 | 境界値 | 空文字列の保存が可能（値のクリア） | `LlmSettingsRepositoryImplTest.kt` | 🟡 |
| TC-12 | 境界値 | DataStore と EncryptedSharedPreferences の変更が独立して反映される | `LlmSettingsRepositoryImplTest.kt` | 🟡 |
| TC-13 | 境界値 | 長い文字列・特殊文字を含む値の保存・読み出し | `LlmSettingsRepositoryImplTest.kt` | 🟡 |

**信頼性分布**: 🔵 7件 / 🟡 6件 / 🔴 0件（テストケース定義書の分布を踏襲）

---

## 2. 作成したテストファイル

### 2.1 `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsTest.kt`
- **対象**: `LlmSettings` data class のデフォルト値・構造的等価性（TC-03）
- **依存**: JUnit4 のみ（DataStore/SharedPreferences 不要）

### 2.2 `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`
- **対象**: TC-01, TC-02, TC-04, TC-06, TC-07, TC-10, TC-11, TC-12, TC-13（状態ベースの検証、9件）
- **実行環境**: `@RunWith(RobolectricTestRunner::class)`。DataStore は `PreferenceDataStoreFactory.create()` による実ファイルI/O、EncryptedSharedPreferences 相当は本ファイル末尾で定義した `FakeSharedPreferences`（`android.content.SharedPreferences` の簡易インメモリ実装）で代替する。
- **設計判断**: 実 EncryptedSharedPreferences（暗号処理）を Robolectric 上で使うと不安定になりやすいため、暗号化の実効性検証は TC-08（計器テスト）に分離し、本ファイルでは「DataStore と別ソースの合成」というロジックの検証に専念した。

### 2.3 `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplInteractionTest.kt`
- **対象**: TC-05, TC-09（インタラクション・呼び出し検証、2件）
- **実行環境**: `@RunWith(RobolectricTestRunner::class)`。`encryptedPrefs` を MockK で完全モック化し、`verify`/`slot` で呼び出し引数・呼び出し回数・リスナーの同一性を検証する。

### 2.4 `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`
- **対象**: TC-08（実 EncryptedSharedPreferences による暗号化実効性確認）
- **実行環境**: `@RunWith(AndroidJUnit4::class)`。実機/エミュレータが必要な `connectedAndroidTest` 用。
- **注記**: 本開発環境には adb/emulator が存在しないため実機実行はできない。**コンパイル成功をもって暫定確認**とする方針（ユーザー指示）に従い、Red フェーズでは「実装クラス不在によるコンパイルエラー」であることのみを確認した。

---

## 3. テストコード全文

各ファイルの全文は以下のパスを参照:
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsTest.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplInteractionTest.kt`
- `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`

---

## 4. 実行結果と期待される失敗

### 4.1 JVMユニットテスト（`testDebugUnitTest`）

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmSettings*"
```

**結果**: `compileDebugUnitTestKotlin` タスクでコンパイルエラー。

代表的なエラー:
```
e: .../LlmSettingsRepositoryImplTest.kt:40:8 Unresolved reference 'LlmSettingsRepositoryImpl'.
e: .../LlmSettingsRepositoryImplTest.kt:62:14 Unresolved reference 'saveEndpointUrl'.
e: .../LlmSettingsTest.kt:24:24 Unresolved reference 'LlmSettings'.
```

**原因**: `LlmSettings`・`LlmSettingsRepository`・`LlmSettingsRepositoryImpl` が未実装のため、テストコードから参照できない。これは意図した Red フェーズの状態であり、テストコード自体の記述ミスではない（Fake/Mock関連のコードは全て正常にコンパイル可能であることを確認済み＝エラーは production クラス不在にのみ起因）。

### 4.2 計器テスト（`compileDebugAndroidTestKotlin`、実行は暫定確認）

```bash
mise exec -- ./gradlew compileDebugAndroidTestKotlin
```

**結果**: コンパイルエラー1件のみ。

```
e: .../data/llm/LlmSettingsRepositoryImplTest.kt:63:20 Unresolved reference 'LlmSettingsRepositoryImpl'.
```

**原因**: 同上（`LlmSettingsRepositoryImpl` 未実装）。`EncryptedSharedPreferences`/`MasterKey` 等、実クラスを使った箇所は全て正しくコンパイルできることを確認済み。

**環境注記**: この開発環境には adb/emulator が存在しないため `connectedAndroidTest` による実機実行はできない。Green フェーズ実装後は、コンパイル成功をもって暫定確認とし、実機/エミュレータでの実行確認は別途デバイス環境で行う。

---

## 5. Greenフェーズで実装すべき内容

1. **`app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt`**
   - `data class LlmSettings(val endpointUrl: String = "", val apiKey: String = "", val model: String = "")`

2. **`app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt`**
   - `interface LlmSettingsRepository { fun getSettings(): Flow<LlmSettings>; suspend fun saveEndpointUrl(url: String); suspend fun saveApiKey(apiKey: String); suspend fun saveModel(model: String) }`

3. **`app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt`**
   - コンストラクタ: `LlmSettingsRepositoryImpl(dataStore: DataStore<Preferences>, encryptedPrefs: SharedPreferences)`
   - `stringPreferencesKey("llm_endpoint_url")` / `stringPreferencesKey("llm_model")` を使った DataStore 読み書き
   - `encryptedPrefs`（キー: `"llm_api_key"`）への apiKey 読み書き。`saveApiKey()` は `withContext(Dispatchers.IO)` で実行
   - `encryptedApiKeyFlow()`: `callbackFlow` + `SharedPreferences.OnSharedPreferenceChangeListener` で apiKey の変更を Flow 化。`awaitClose` でリスナー解除必須（TC-09で検証）
   - `getSettings()`: DataStore の Flow と `encryptedApiKeyFlow()` を `combine()` して単一の `Flow<LlmSettings>` を返す

4. **DI（本タスクの範囲外だがテスト成功には無関係）**
   - `LlmModule.kt` は本 Red フェーズのテスト対象外（テストはコンストラクタ直接注入で検証している）。後続タスクで対応。

上記実装により、本 Red フェーズで作成した13件のテストが全て成功することが期待される（Green フェーズの完了条件）。

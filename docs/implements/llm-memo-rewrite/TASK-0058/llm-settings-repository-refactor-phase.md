# TDD Refactorフェーズ記録: LlmSettings・LlmSettingsRepository・LlmSettingsRepositoryImpl

**機能名**: llm-settings-repository
**タスクID**: TASK-0058
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. リファクタ前の確認

- `mise exec -- ./gradlew testDebugUnitTest --tests "*LlmSettings*"` を3回再実行し、14件全て安定して成功することを確認（ベースライン）。
- テスト実行時間チェック: `LlmSettingsRepositoryImplInteractionTest.getSettingsCollectCancelled_unregistersSharedPreferenceListener`（TC-09）が 3.328秒で2秒閾値を超過。原因調査の結果、同一テストクラス内の2件目のテスト（`saveApiKey_writesToEncryptedPrefsEditor_notDataStore`）は0.105秒であり、MockKの動的バイトコード計装（byte-buddy-agent の初回ロード、標準出力に `WARNING: A Java agent has been loaded dynamically` と表示）による**そのテストクラスで最初にMockKを使う際の一度きりの初期化コスト**と判断した。実装アルゴリズム側のO(n)処理やI/O過多といった性能課題は見当たらないため、リファクタでの対処は不要と判断（🟡 推測を含む）。気になる場合は `/tsumiki:dcs:test-performance-analysis` で追加調査可能。
- `.gitignore` によるコード・テスト除外なし、`@Ignore`/`.skip` 相当の無効化テストなし、Gradleのtest除外設定なしを確認。
- デバッグ・一時ファイル（`debug-*`, `test-*`, `*.tmp`, `*.bak` 等）の残存なしを確認（`app/src/test`・`app/src/androidTest` 配下の正規テストファイルを除く）。

## 2. セキュリティレビュー

| 観点 | 確認内容 | 結果 |
|------|---------|------|
| 機微情報のログ出力（NFR-102） | `saveApiKey()`/`currentApiKey()`/`encryptedApiKeyFlow()` のいずれも apiKey を `Log.*` や例外メッセージに出力していない | 🔵 問題なし |
| 平文保存禁止（REQ-401） | `saveApiKey()` は `encryptedPrefs`（EncryptedSharedPreferences想定）のみを操作し、`dataStore` には一切触れない。TC-05/TC-07がこれを回帰検知する | 🔵 問題なし |
| 入力値検証 | `endpointUrl`/`apiKey`/`model` はいずれも任意の文字列を受け付ける設計（要件上の形式検証なし）。TC-13で特殊文字・非ASCIIの往復を確認済み | 🔵 仕様どおり |
| SQLインジェクション/XSS/CSRF | 本コンポーネントはDataStore/SharedPreferencesアクセスのみで、SQL・Web入出力を扱わないため該当なし | 🔵 該当なし |
| 認可 | 本Repositoryはアプリ内部データ層であり、外部からのアクセス制御は上位層（Hilt DIで注入先を制限）の責務 | 🔵 スコープ外 |
| リスナーリーク | `encryptedApiKeyFlow()` の `awaitClose` で `unregisterOnSharedPreferenceChangeListener` を必ず呼ぶ実装になっており、TC-09で検証済み | 🔵 問題なし |

**重大な脆弱性は発見されなかった。**

## 3. パフォーマンスレビュー

| 観点 | 内容 |
|------|------|
| 計算量 | `getSettings()`/`saveXxx()` はすべて O(1) のキー操作。ループ・再帰処理なし。 |
| メモリ | `encryptedApiKeyFlow()` は購読ごとに1つの `OnSharedPreferenceChangeListener` を生成・保持するのみ。`awaitClose` で確実に解放されるためリークなし。 |
| I/O | `saveApiKey()` の同期I/Oは `Dispatchers.IO` に切り替え済み。`saveEndpointUrl()`/`saveModel()` は DataStore が内部で非同期処理するため追加対応不要。 |
| スケーラビリティ | `getSettings()` を多数箇所から同時購読すると、購読数分の `OnSharedPreferenceChangeListener` が登録される。本アプリの利用形態（設定画面・LLM呼び出し時のみ参照）では問題にならない規模と判断（🟡 妥当な推測、実測はしていない）。 |

**重大な性能課題は発見されなかった。**

## 4. 実施したリファクタリング

### 4.1 DataStore側フォールバック処理の重複排除

**Before**:
```kotlin
dataStore.data.map { prefs ->
    (prefs[ENDPOINT_URL_KEY] ?: "") to (prefs[MODEL_KEY] ?: "")
}
```

**After**:
```kotlin
private fun Preferences.stringOrEmpty(key: Preferences.Key<String>): String = this[key] ?: ""

dataStore.data.map { prefs ->
    prefs.stringOrEmpty(ENDPOINT_URL_KEY) to prefs.stringOrEmpty(MODEL_KEY)
}
```

- 【改善内容】: `?: ""` のフォールバックロジックを拡張関数 `stringOrEmpty()` に抽出し、DRY原則を適用。
- 🔵 信頼性レベル: requirements.md 4.3（未設定時は空文字列に正規化）どおりの挙動を保ったまま可読性のみ改善。動作の変更はなし（テスト結果で確認）。

### 4.2 EncryptedSharedPreferences側の現在値取得の重複排除

**Before**:
```kotlin
private fun encryptedApiKeyFlow(): Flow<String> = callbackFlow {
    trySend(encryptedPrefs.getString(API_KEY_KEY, "") ?: "")
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
        if (key == API_KEY_KEY) {
            trySend(changedPrefs.getString(API_KEY_KEY, "") ?: "")
        }
    }
    ...
}
```

**After**:
```kotlin
private fun currentApiKey(): String = encryptedPrefs.getString(API_KEY_KEY, "") ?: ""

private fun encryptedApiKeyFlow(): Flow<String> = callbackFlow {
    trySend(currentApiKey())
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == API_KEY_KEY) {
            trySend(currentApiKey())
        }
    }
    ...
}
```

- 【改善内容】: 初期発行時・変更時再発行時の2箇所にあった `getString(...) ?: ""` の重複を `currentApiKey()` ヘルパーへ集約。リスナーのラムダ引数 `changedPrefs` は未使用だったため `_` に変更（`encryptedPrefs` を直接参照する設計にすることで意図が明確化）。
- 🔵 信頼性レベル: TASK-0058.md 実装詳細・requirements.md 2.1 より、挙動は変更なし。

### 4.3 KDocコメントの拡充

各関数のKDocに `【改善内容】`・`【設計方針】`・`【パフォーマンス】`・`【保守性】`・`【セキュリティ】` の観点を追加し、Green フェーズ時点の `【実装方針】` のみのコメントから、リファクタの意図・非機能要件との対応関係が読み取れるように強化した。

## 5. リファクタ後のテスト結果

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmSettings*"
# BUILD SUCCESSFUL（14 tests、全て継続成功）

mise exec -- ./gradlew testDebugUnitTest
# BUILD SUCCESSFUL（プロジェクト全体、既存テストへの影響なし）

mise exec -- ./gradlew compileDebugAndroidTestKotlin
# BUILD SUCCESSFUL（TC-08含む計器テストのコンパイル成功、deprecation警告のみ）

mise exec -- ./gradlew lint
# BUILD SUCCESSFUL（lintDebug 含め成功、エラーなし）
```

**環境注記**: adb/emulator 不在のため TC-08（計器テスト）は引き続きコンパイル成功のみで暫定確認。

## 6. 品質判定

✅ 高品質:
- テスト結果: リファクタ後も14件全て継続成功（3回再実行を含め安定）。
- セキュリティ: 重大な脆弱性なし（NFR-102/REQ-401 とも実装で担保されていることを再確認）。
- パフォーマンス: 重大な性能課題なし（全メソッドO(1)、リスナーリークなし）。
- リファクタ品質: DRY原則適用によるコード重複排除（`stringOrEmpty`/`currentApiKey`）を達成。機能的な変更は行っていない（既存テストが無改修で継続成功することで裏付け）。
- コード品質: lint成功、コンパイルエラーなし。
- ファイルサイズ: `LlmSettingsRepositoryImpl.kt` 147行（500行制限に対し十分小さく、分割不要）。
- 日本語コメント: 【改善内容】【設計方針】【パフォーマンス】【保守性】【セキュリティ】の観点を追加し、リファクタ意図が追跡可能な状態にした。

## 7. 残課題（本タスクスコープ外・後続タスクへ引き継ぎ）

- `LlmModule`（Hilt DI）は未実装。後続タスクで `EncryptedSharedPreferences` の実体（`MasterKey` + `AES256_SIV`/`AES256_GCM`）と `LlmSettingsRepositoryImpl` の提供を行う。
- TC-08（計器テスト）は実機/エミュレータでの実行確認が未了。デバイス環境が用意でき次第 `connectedAndroidTest` で確認すること。

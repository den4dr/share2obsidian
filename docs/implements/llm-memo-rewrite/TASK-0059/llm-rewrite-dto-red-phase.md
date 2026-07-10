# TDD Redフェーズ記録: LlmRewriteResult・ChatCompletion DTO実装

**機能名**: llm-rewrite-dto
**タスクID**: TASK-0059
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. 実装したテストケース一覧

テストケース定義書（`docs/implements/llm-memo-rewrite/TASK-0059/llm-rewrite-dto-testcases.md`）の TC-N-01〜06, TC-E-01〜03, TC-B-01〜05 を全件実装した（目標10件以上に対し14件、全テストケースを網羅）。

| No. | 分類 | テスト名 | ファイル | 信頼性 |
|-----|------|---------|---------|--------|
| TC-N-01 | 正常系 | ChatCompletionRequestDto エンコード結果がAPI仕様と一致 | `ChatCompletionDtoTest.kt` | 🔵 |
| TC-N-02 | 正常系 | ChatCompletionResponseDto をデコードし content を取得 | `ChatCompletionDtoTest.kt` | 🔵 |
| TC-N-03 | 正常系 | ChatMessageDto 単体のエンコード/デコード往復一致 | `ChatCompletionDtoTest.kt` | 🔵 |
| TC-N-04 | 正常系 | LlmRewriteResult.Success が text を保持 | `LlmRewriteResultTest.kt` | 🔵 |
| TC-N-05 | 正常系 | 各 Failure サブクラスが messageResId を保持 | `LlmRewriteResultTest.kt` | 🔵（EmptyOrInvalidResponse/Unknownの位置づけは🟡） |
| TC-N-06 | 正常系 | Failure は LlmRewriteResult のサブタイプであり when で分岐可能 | `LlmRewriteResultTest.kt` | 🔵 |
| TC-E-01 | 異常系 | 必須フィールド欠落JSONのデコードで例外 | `ChatCompletionDtoTest.kt` | 🟡 |
| TC-E-02 | 異常系 | 不正な形式のJSONのデコードで例外 | `ChatCompletionDtoTest.kt` | 🟡 |
| TC-E-03 | 異常系 | 未知キーを含むレスポンスを ignoreUnknownKeys でデコード可能 | `ChatCompletionDtoTest.kt` | 🟡 |
| TC-B-01 | 境界値 | choices が空配列のレスポンスを例外なくデコード | `ChatCompletionDtoTest.kt` | 🟡 |
| TC-B-02 | 境界値 | messages が空リストの Request をエンコード | `ChatCompletionDtoTest.kt` | 🟡 |
| TC-B-03 | 境界値 | content が空文字のメッセージをエンコード/デコード | `ChatCompletionDtoTest.kt` | 🟡 |
| TC-B-04 | 境界値 | Success.text が空文字でも保持できる | `LlmRewriteResultTest.kt` | 🟡 |
| TC-B-05 | 境界値 | data class の等価性（同一内容の Success/Failure は equals） | `LlmRewriteResultTest.kt` | 🟡 |

**信頼性分布**: 🔵 6件 / 🟡 8件 / 🔴 0件（テストケース定義書の分布と一致）

---

## 2. 作成したテストファイル

### 2.1 `app/src/test/java/com/den4dr/share2Obsidian/data/llm/ChatCompletionDtoTest.kt`
- **対象**: `ChatCompletionRequestDto` / `ChatMessageDto` / `ChatCompletionResponseDto` / `ChoiceDto`（`data/llm/dto/ChatCompletionDto.kt`、未実装）
- **テスト数**: 9件（TC-N-01〜03, TC-E-01〜03, TC-B-01〜03）
- **依存**: kotlinx.serialization.json.Json のみ（Ktor 通信は含まない）

### 2.2 `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResultTest.kt`
- **対象**: `LlmRewriteResult` sealed class（`data/llm/LlmRewriteResult.kt`、未実装）
- **テスト数**: 5件（TC-N-04〜06, TC-B-04〜05）
- **依存**: JUnit4 のみ

---

## 3. テストコード全文

### 3.1 ChatCompletionDtoTest.kt

```kotlin
package com.den4dr.share2Obsidian.data.llm

import com.den4dr.share2Obsidian.data.llm.dto.ChatCompletionRequestDto
import com.den4dr.share2Obsidian.data.llm.dto.ChatCompletionResponseDto
import com.den4dr.share2Obsidian.data.llm.dto.ChatMessageDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionDtoTest {
    // ... 9テストケース（TC-N-01〜03, TC-E-01〜03, TC-B-01〜03）
    // 全文は app/src/test/java/com/den4dr/share2Obsidian/data/llm/ChatCompletionDtoTest.kt を参照
}
```

（実装済みの全文は `app/src/test/java/com/den4dr/share2Obsidian/data/llm/ChatCompletionDtoTest.kt` を参照）

### 3.2 LlmRewriteResultTest.kt

```kotlin
package com.den4dr.share2Obsidian.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRewriteResultTest {
    // ... 5テストケース（TC-N-04〜06, TC-B-04〜05）
    // 全文は app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResultTest.kt を参照
}
```

（実装済みの全文は `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResultTest.kt` を参照）

---

## 4. 実行結果と期待される失敗

### 実行コマンド

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.data.llm.ChatCompletionDtoTest" --tests "com.den4dr.share2Obsidian.data.llm.LlmRewriteResultTest"
```

### 実際の失敗内容

`:app:compileDebugUnitTestKotlin` タスクでコンパイルエラーが発生し、テスト実行前にビルドが失敗した。

- `ChatCompletionDtoTest.kt`: `Unresolved reference 'dto'` / `Unresolved reference 'ChatCompletionRequestDto'` / `Unresolved reference 'ChatMessageDto'` / `Unresolved reference 'ChatCompletionResponseDto'` など、`data/llm/dto/ChatCompletionDto.kt` が存在しないことに起因するエラー多数。
- `LlmRewriteResultTest.kt`: `Unresolved reference 'LlmRewriteResult'` / `Unresolved reference 'messageResId'` / `'when' expression must be exhaustive` など、`data/llm/LlmRewriteResult.kt` が存在しないことに起因するエラー多数。

これは「まだ実装されていない型を参照するテストがコンパイル段階で失敗する」という Red フェーズとして正しい状態である。

---

## 5. Greenフェーズで実装すべき内容

1. **`app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResult.kt`** を新規作成
   - `sealed class LlmRewriteResult` を定義
   - `data class Success(val text: String) : LlmRewriteResult()`
   - `sealed class Failure : LlmRewriteResult()` に `abstract val messageResId: Int` を宣言
   - `Failure` のサブクラス: `NetworkError`, `AuthError`, `Timeout`, `EmptyOrInvalidResponse`, `Unknown`（いずれも `override val messageResId: Int` を保持する `data class`）

2. **`app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt`** を新規作成
   - `@Serializable data class ChatCompletionRequestDto(val model: String, val messages: List<ChatMessageDto>)`
   - `@Serializable data class ChatMessageDto(val role: String, val content: String)`
   - `@Serializable data class ChatCompletionResponseDto(val choices: List<ChoiceDto>)`
   - `@Serializable data class ChoiceDto(val message: ChatMessageDto)`

3. 実装後、以下のコマンドで新規テストが全て通ることを確認する。

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.data.llm.ChatCompletionDtoTest" --tests "com.den4dr.share2Obsidian.data.llm.LlmRewriteResultTest"
```

---

## 6. 品質判定

| 観点 | 評価 |
|------|------|
| テスト実行 | ✅ 実行可能で、期待通りコンパイルエラー（未実装型参照）により失敗することを確認済み |
| 期待値 | ✅ 明確かつ具体的（JSON構造比較・数値比較・equals比較） |
| アサーション | ✅ 適切（assertEquals/assertTrue/assertFalse/assertThrows を用途に応じ使い分け） |
| 実装方針 | ✅ 明確（型定義・シリアライズ契約が requirements.md に既定） |
| 信頼性レベル | 🔵 6件 / 🟡 8件 / 🔴 0件（🔵優勢、🔴ゼロ） |

**総合判定**: ✅ 高品質（自動修正ループ不要）

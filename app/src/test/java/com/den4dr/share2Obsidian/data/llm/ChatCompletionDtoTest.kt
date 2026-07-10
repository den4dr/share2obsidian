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

/**
 * [ChatCompletionRequestDto] / [ChatMessageDto] / [ChatCompletionResponseDto] / `ChoiceDto` の
 * JSONエンコード/デコード契約を検証するユニットテスト（TASK-0059）。
 *
 * 【テスト対象】: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt`（未実装）
 * このファイルは Red フェーズの時点では DTO クラスが存在しないためコンパイルに失敗する。
 */
class ChatCompletionDtoTest {

    private val strictJson = Json
    private val lenientJson = Json { ignoreUnknownKeys = true }

    // TC-N-01: ChatCompletionRequestDto のエンコード結果がAPI仕様と一致する
    @Test
    fun `ChatCompletionRequestDto をエンコードすると API 仕様の JSON になる`() {
        // 【テスト目的】: リクエストDTOのエンコード結果が OpenAI 互換仕様と一致することを確認する
        // 【テスト内容】: model + messages(system/user) をエンコードし、構造比較する
        // 【期待される動作】: {"model":"gpt-4o","messages":[{"role":"system",...},{"role":"user",...}]} 形式になる
        // 🔵 信頼性レベル: api-endpoints.md「リクエスト仕様」・TASK-0059.md テストケース1に基づく（推測なし）

        // 【テストデータ準備】: api-endpoints.md記載のリクエスト例に対応する system/user の2メッセージ構成を用意する
        // 【初期条件設定】: 実運用の本文リライトリクエストを代表する値を使用する
        val request = ChatCompletionRequestDto(
            model = "gpt-4o",
            messages = listOf(
                ChatMessageDto("system", "プロンプト"),
                ChatMessageDto("user", "元コンテンツ"),
            ),
        )

        // 【実際の処理実行】: kotlinx.serialization でエンコードする
        // 【処理内容】: Json.encodeToString() により JSON 文字列へ変換する
        val encoded = strictJson.encodeToString(request)

        // 【結果検証】: キー順序に依存しないよう JsonElement へ再パースして構造比較する
        // 【期待値確認】: api-endpoints.md 記載のJSON形式と構造一致すること
        val expected = Json.parseToJsonElement(
            """{"model":"gpt-4o","messages":[{"role":"system","content":"プロンプト"},{"role":"user","content":"元コンテンツ"}]}""",
        )
        assertEquals(expected, Json.parseToJsonElement(encoded)) // 【確認内容】: エンコード結果が仕様JSONと構造一致すること 🔵
    }

    // TC-N-02: サンプルレスポンスJSONをデコードして content を取得できる
    @Test
    fun `ChatCompletionResponseDto をデコードすると choices先頭のcontentを取得できる`() {
        // 【テスト目的】: レスポンスDTOのデコード契約がAPI仕様に一致することを確認する
        // 【テスト内容】: API成功レスポンス形式のJSON文字列をデコードし、ネスト構造を取り出す
        // 【期待される動作】: choices[0].message.content / role が正しくマッピングされる
        // 🔵 信頼性レベル: api-endpoints.md「レスポンス仕様」・TASK-0059.md テストケース2に基づく（推測なし）

        // 【テストデータ準備】: api-endpoints.md記載の成功レスポンス例を使用する
        // 【初期条件設定】: 単一choiceの典型的なレスポンスを想定する
        val json = """{"choices":[{"message":{"role":"assistant","content":"書き換え結果"}}]}"""

        // 【実際の処理実行】: kotlinx.serialization でデコードする
        // 【処理内容】: Json.decodeFromString() により ChatCompletionResponseDto へ変換する
        val result = strictJson.decodeFromString<ChatCompletionResponseDto>(json)

        // 【結果検証】: ネスト構造（List → data class → data class）が正しくデコードされていること
        // 【期待値確認】: REQ-003 で使用する content が取得できること
        assertEquals(1, result.choices.size) // 【確認内容】: choices が1件デコードされていること 🔵
        assertEquals("書き換え結果", result.choices[0].message.content) // 【確認内容】: content が正しくデコードされること 🔵
        assertEquals("assistant", result.choices[0].message.role) // 【確認内容】: role も欠落なく取得できること 🔵
    }

    // TC-N-03: ChatMessageDto 単体のエンコード/デコード往復で内容が一致する
    @Test
    fun `ChatMessageDto はエンコードして再デコードすると元の値と一致する`() {
        // 【テスト目的】: エンコードとデコードの対称性（キー名の一貫性）を確認する
        // 【テスト内容】: encodeToString と decodeFromString のラウンドトリップを検証する
        // 【期待される動作】: role/content の双方向シリアライズがロスなく行われる
        // 🔵 信頼性レベル: kotlinx.serialization 標準仕様・requirements.md 2.2 に基づく（推測なし）

        // 【テストデータ準備】: リクエスト/レスポンス双方で再利用される最小構成のメッセージDTOを用意する
        // 【初期条件設定】: 往復一致は DTO 全体の健全性を代表する
        val original = ChatMessageDto(role = "user", content = "テスト本文")

        // 【実際の処理実行】: エンコード後に同じ型へデコードする
        // 【処理内容】: encodeToString → decodeFromString の順で処理する
        val roundTripped = strictJson.decodeFromString<ChatMessageDto>(strictJson.encodeToString(original))

        // 【結果検証】: data class の構造的等価性により元インスタンスと一致すること
        // 【期待値確認】: エンコード側とデコード側でキー名の不一致がないこと
        assertEquals(original, roundTripped) // 【確認内容】: ラウンドトリップ後も値が完全に一致すること 🔵
    }

    // TC-E-01: 必須フィールドが欠落したレスポンスJSONのデコードで例外が発生する
    @Test
    fun `message フィールドを欠く choice のデコードで例外が発生する`() {
        // 【テスト目的】: 必須フィールド欠落時にデコードが確実に失敗することを確認する
        // 【テスト内容】: ChoiceDto.message を含まないJSONをデコードしようとするケース
        // 【期待される動作】: SerializationException（MissingFieldExceptionを含む）がスローされる
        // 🟡 信頼性レベル: kotlinx.serialization の必須フィールド仕様からの妥当な推測（例外種別の詳細は資料に明記なし）

        // 【テストデータ準備】: choice オブジェクトに message を含まない不正なレスポンスを用意する
        // 【初期条件設定】: OpenAI 非互換の応答（プロキシ/ローカルLLMの独自形式）を想定する
        val invalidJson = """{"choices":[{}]}"""

        // 【実際の処理実行】: デコードを試行し、例外がスローされることを確認する
        // 【処理内容】: assertThrows で SerializationException を検証する
        assertThrows(SerializationException::class.java) {
            strictJson.decodeFromString<ChatCompletionResponseDto>(invalidJson)
        } // 【確認内容】: 必須プロパティ欠落時に例外が発生し、不正データが型に入り込まないこと 🟡
    }

    // TC-E-02: 構文的に不正なJSONのデコードで例外が発生する
    @Test
    fun `構文的に不正なJSON文字列のデコードで例外が発生する`() {
        // 【テスト目的】: パース不能入力に対する堅牢性を確認する
        // 【テスト内容】: JSONとしてパースできない文字列をデコードしようとするケース
        // 【期待される動作】: SerializationException（パースエラー）がスローされる
        // 🟡 信頼性レベル: EDGE-004「パース不能なレスポンス」からの妥当な推測（例外種別の詳細は資料に明記なし）

        // 【テストデータ準備】: 閉じ括弧のない不正なJSON文字列を用意する
        // 【初期条件設定】: エンドポイントURL誤設定でHTML等が返るケースを想定する
        val brokenJson = "not a json {"

        // 【実際の処理実行】: デコードを試行し、例外がスローされることを確認する
        // 【処理内容】: assertThrows で SerializationException を検証する
        assertThrows(SerializationException::class.java) {
            strictJson.decodeFromString<ChatCompletionResponseDto>(brokenJson)
        } // 【確認内容】: パース不能な入力に対してアプリがクラッシュせず例外で失敗すること 🟡
    }

    // TC-E-03: 未知キーを含むレスポンスを ignoreUnknownKeys 設定でデコードできる
    @Test
    fun `未定義キーを含むレスポンスを ignoreUnknownKeys でデコードできる`() {
        // 【テスト目的】: 未知キー無視設定でのデコード互換性を確認する
        // 【テスト内容】: DTOに定義していない余分なキー（id, finish_reason, usage）を含む実APIレスポンスをデコードする
        // 【期待される動作】: 例外を投げず choices[0].message.content が取得できる
        // 🟡 信頼性レベル: requirements.md 2.3（ignoreUnknownKeys 前提）・OpenAI互換API一般慣習からの妥当な推測

        // 【テストデータ準備】: 実際のOpenAI互換API応答を模した余分キー付きJSONを用意する
        // 【初期条件設定】: ignoreUnknownKeys = true の Json インスタンスを使用する
        val jsonWithExtraKeys =
            """{"id":"x","choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"結果"}}],"usage":{"total_tokens":5}}"""

        // 【実際の処理実行】: lenientJson でデコードする
        // 【処理内容】: ignoreUnknownKeys 設定下での decodeFromString を実行する
        val result = lenientJson.decodeFromString<ChatCompletionResponseDto>(jsonWithExtraKeys)

        // 【結果検証】: 未知キーが無視され、必要な値のみ正しく取得できること
        // 【期待値確認】: 実運用のレスポンス互換性が担保されていること
        assertEquals("結果", result.choices[0].message.content) // 【確認内容】: 未知キーがあってもcontentが正しく取得できること 🟡
    }

    // TC-B-01: choices が空配列のレスポンスを例外なくデコードできる
    @Test
    fun `choices が空配列のレスポンスを例外なくデコードでき空リストになる`() {
        // 【テスト目的】: 空応答の型レベルでの取り扱いを確認する（Failureマッピングは TASK-0060 の範囲）
        // 【テスト内容】: choices が0件の境界値をデコードするケース
        // 【期待される動作】: 例外を投げずにデコードでき、choices が空リストになる
        // 🟡 信頼性レベル: TASK-0059.md テストケース3・api-endpoints.md 95行・EDGE-004 に基づく（空応答時のFailureマッピングは直接確認していないため）

        // 【テストデータ準備】: LLMサービスが候補ゼロを返す異常応答を想定したJSONを用意する
        // 【初期条件設定】: choices の下限（0件）の境界値
        val emptyChoicesJson = """{"choices":[]}"""

        // 【実際の処理実行】: デコードする
        // 【処理内容】: decodeFromString により ChatCompletionResponseDto へ変換する
        val result = strictJson.decodeFromString<ChatCompletionResponseDto>(emptyChoicesJson)

        // 【結果検証】: 例外を投げず空リストとしてデコードされていること
        // 【期待値確認】: 1件以上（TC-N-02）と0件（本ケース）でデコード成否が一貫すること
        assertTrue(result.choices.isEmpty()) // 【確認内容】: choices が空リストであること 🟡
    }

    // TC-B-02: messages が空リストの Request をエンコードできる
    @Test
    fun `messages が空リストのChatCompletionRequestDtoをエンコードすると空配列になる`() {
        // 【テスト目的】: コレクションプロパティの境界（空）でのエンコードを確認する
        // 【テスト内容】: messages が0件のRequestDtoをエンコードするケース
        // 【期待される動作】: エンコード結果に "messages":[] が含まれる
        // 🟡 信頼性レベル: kotlinx.serialization のコレクション標準仕様からの妥当な推測（要件に空リスト明記はない）

        // 【テストデータ準備】: messages が空リストのRequestDtoを用意する
        // 【初期条件設定】: 通常は system/user の2件だが、境界値として空を検証する
        val request = ChatCompletionRequestDto(model = "gpt-4o", messages = emptyList())

        // 【実際の処理実行】: エンコードする
        // 【処理内容】: encodeToString により JSON 文字列へ変換する
        val encoded = strictJson.encodeToString(request)

        // 【結果検証】: 空リストが [] として出力されていること
        // 【期待値確認】: 非空（TC-N-01）と空（本ケース）でキー構造が一貫すること
        val parsed = Json.parseToJsonElement(encoded)
        val expected = Json.parseToJsonElement("""{"model":"gpt-4o","messages":[]}""")
        assertEquals(expected, parsed) // 【確認内容】: messages が空配列として出力されること 🟡
    }

    // TC-B-03: content が空文字のメッセージをエンコード/デコードできる
    @Test
    fun `content が空文字のChatMessageDtoをエンコードデコードしても値が保持される`() {
        // 【テスト目的】: 空文字境界でのシリアライズ正確性を確認する
        // 【テスト内容】: content が空文字のChatMessageDtoを往復させるケース
        // 【期待される動作】: 空文字が欠落扱いにならず往復で保持される
        // 🟡 信頼性レベル: EDGE-004（content 空文字）・kotlinx.serialization 標準仕様からの妥当な推測

        // 【テストデータ準備】: LLMが空文字を返すケースを想定したChatMessageDtoを用意する
        // 【初期条件設定】: content の下限（空文字）の境界値
        val original = ChatMessageDto(role = "user", content = "")

        // 【実際の処理実行】: エンコード後に再デコードする
        // 【処理内容】: encodeToString → decodeFromString の順で処理する
        val encoded = strictJson.encodeToString(original)
        val decoded = strictJson.decodeFromString<ChatMessageDto>(encoded)

        // 【結果検証】: 空文字が省略されず明示的に出力・復元されていること
        // 【期待値確認】: 非空文字列と空文字列でシリアライズ経路が一貫すること
        assertTrue(encoded.contains("\"content\":\"\"")) // 【確認内容】: エンコード結果に空文字のcontentキーが明示されること 🟡
        assertEquals("", decoded.content) // 【確認内容】: デコード後もcontentが空文字のまま保持されること 🟡
    }
}

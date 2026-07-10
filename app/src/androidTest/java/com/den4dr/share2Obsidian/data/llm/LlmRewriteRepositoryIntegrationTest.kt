package com.den4dr.share2Obsidian.data.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.den4dr.share2Obsidian.R
import com.den4dr.share2Obsidian.data.llm.dto.ChatCompletionRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [LlmRewriteRepositoryImpl] のリクエスト送信内容とタイムアウト機能を、Ktor `MockEngine` を用いて
 * End-to-Endで検証する計器テスト（TASK-0060 / IT-01, IT-02）。
 *
 * 実行環境注記: この開発環境には adb/emulator が存在しないため connectedAndroidTest による実機実行はできない。
 * Red フェーズでは本ファイルのコンパイルが通ること（暫定確認）をもって記録とし、
 * 実機/エミュレータでの実行確認は別途デバイス環境で行うこと（TASK-0058 Red フェーズと同方針）。
 *
 * 【テスト対象】: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImpl.kt`（未実装）
 * このファイルは Red フェーズの時点では `LlmRewriteRepositoryImpl` クラスが存在しないためコンパイルに失敗する。
 */
@RunWith(AndroidJUnit4::class)
class LlmRewriteRepositoryIntegrationTest {

    private val settings = LlmSettings(
        endpointUrl = "https://api.example.com/v1/chat/completions",
        apiKey = "sk-int-test",
        model = "gpt-4o",
    )

    // IT-01: 送信リクエストのヘッダ・ボディが仕様通り（End-to-End）
    @Test
    fun IT01_送信リクエストのAuthorizationヘッダとmessagesボディが仕様通りである() = runBlocking {
        // 【テスト目的】: rewrite()呼び出しで実際に送出されるHTTPリクエストの中身が仕様通りであることを確認
        // 【テスト内容】: MockEngineで受け取ったrequestのヘッダ・ボディJSONを検査する
        // 【期待される動作】: Authorization/Content-Typeヘッダ、model、messages[0]=system/[1]=userが仕様と一致する
        // 🔵 信頼性レベル: TASK-0060.md 統合テスト1・api-endpoints.md「認証」「リクエスト仕様」・testcases.md IT-01
        //   （messages[].roleの固定値は🟡）

        // 【テストデータ準備】: 送信リクエストを捕捉するMockEngineを用意
        var capturedAuthHeader: String? = null
        var capturedContentType: String? = null
        var capturedMethod: String? = null
        var capturedUrl: String? = null
        var capturedBody: String? = null

        val engine = MockEngine { request ->
            capturedAuthHeader = request.headers[HttpHeaders.Authorization]
            // 【Ktor仕様】: Content-Type は request.headers ではなく OutgoingContent（request.body）側に
            // 保持されるため、body.contentType から取得する（charset 等のパラメータは比較対象外）
            capturedContentType = request.body.contentType?.withoutParameters()?.toString()
            capturedMethod = request.method.value
            capturedUrl = request.url.toString()
            val content = request.body
            capturedBody = if (content is OutgoingContent.ByteArrayContent) {
                content.bytes().decodeToString()
            } else {
                null
            }
            respond(
                """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: system-prompt/user-contentでrewrite()を呼び出す
        repository.rewrite(settings, "system-prompt", "user-content")

        // 【結果検証】: ヘッダ・メソッド・URL・ボディが仕様通りであることを確認
        assertEquals("Bearer sk-int-test", capturedAuthHeader) // 【確認内容】: Authorizationヘッダの形式がBearer方式であること
        assertEquals("application/json", capturedContentType) // 【確認内容】: Content-Typeがapplication/jsonであること
        assertEquals("POST", capturedMethod) // 【確認内容】: HTTPメソッドがPOSTであること
        assertEquals(settings.endpointUrl, capturedUrl) // 【確認内容】: 送信先URLがsettings.endpointUrlと一致すること

        val requestDto = Json.decodeFromString<ChatCompletionRequestDto>(requireNotNull(capturedBody))
        assertEquals("gpt-4o", requestDto.model) // 【確認内容】: modelがsettings.modelと一致すること
        assertEquals("system", requestDto.messages[0].role) // 【確認内容】: messages[0]がsystemロールであること
        assertEquals("system-prompt", requestDto.messages[0].content) // 【確認内容】: messages[0].contentがpromptと一致すること
        assertEquals("user", requestDto.messages[1].role) // 【確認内容】: messages[1]がuserロールであること
        assertEquals("user-content", requestDto.messages[1].content) // 【確認内容】: messages[1].contentがcontentと一致すること

        httpClient.close()
    }

    // IT-02: 閾値超の遅延応答で実際にタイムアウトしTimeoutを返す
    @Test
    fun IT02_設定閾値超の遅延応答でHttpTimeoutにより実際にタイムアウトしTimeoutを返す() = runBlocking {
        // 【テスト目的】: HttpTimeoutプラグインのrequestTimeoutMillis設定が実機能することを確認
        // 【テスト内容】: MockEngineが設定閾値を超える遅延応答を返す状況で、実際にHttpRequestTimeoutExceptionが
        //   発生しTimeoutへマッピングされることを検証する
        // 【期待される動作】: LlmRewriteResult.Failure.Timeout(R.string.error_llm_timeout)が返る
        // 🔵 信頼性レベル: TASK-0060.md 統合テスト2・NFR-001・REQ-202・api-endpoints.md「タイムアウト設定」・testcases.md IT-02
        //   （実30秒待機は非現実的なため、閾値・遅延を短縮したテスト用HttpClientで代替検証する。🟡）

        // 【テストデータ準備】: 閾値(200ms)を超える遅延(1000ms)で応答するMockEngineを用意
        val engine = MockEngine {
            delay(1_000)
            respond(
                """{"choices":[{"message":{"role":"assistant","content":"遅延応答"}}]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) {
            install(HttpTimeout) { requestTimeoutMillis = 200 }
            install(ContentNegotiation) { json() }
        }
        val repository = LlmRewriteRepositoryImpl(httpClient)

        // 【実際の処理実行】: rewrite()を呼び出す
        val result = repository.rewrite(settings, "prompt", "content")

        // 【結果検証】: 実際のタイムアウト発火によりTimeoutが返ることを確認
        assertTrue(result is LlmRewriteResult.Failure.Timeout) // 【確認内容】: HttpTimeout設定が実機能しTimeoutへ分類されること
        assertEquals(
            R.string.error_llm_timeout,
            (result as LlmRewriteResult.Failure.Timeout).messageResId,
        ) // 【確認内容】: 表示用メッセージIDがerror_llm_timeoutであること

        httpClient.close()
    }
}

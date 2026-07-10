package com.den4dr.share2Obsidian.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.den4dr.share2Obsidian.data.llm.LlmRewriteRepository
import com.den4dr.share2Obsidian.data.llm.LlmRewriteRepositoryImpl
import com.den4dr.share2Obsidian.data.llm.LlmSettingsRepository
import com.den4dr.share2Obsidian.data.llm.LlmSettingsRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton

// 【DataStore定義】: LlmSettingsRepositoryImpl の endpointUrl/model 用 DataStore。
// 既存 DataStoreModule.kt の noteSettingsDataStore と衝突しない専用名を使用する 🔵
private val Context.llmSettingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "llm_settings")

/**
 * 【モジュール概要】: LLM書き換え機能で使用する HttpClient・EncryptedSharedPreferences・
 *              LlmSettingsRepository・LlmRewriteRepository を Singleton 提供する Hilt モジュール
 * 【設計方針】: 既存 di/DataStoreModule.kt, di/DatabaseModule.kt と同様に object + @Provides @Singleton
 *              パターンを踏襲する
 * 🔵 信頼性レベル: TASK-0061.md・architecture.md「LLMリクエスト/レスポンス設計」「LLM設定管理設計」より
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmModule {

    /**
     * 【機能概要】: OpenAI互換 Chat Completions API 呼び出し用の HttpClient(CIO) を提供する
     * 【設計方針】: ContentNegotiation(json) と HttpTimeout(30秒) をinstallする
     * 🔵 信頼性レベル: TASK-0061.md「provideHttpClient()」・architecture.md「タイムアウト設定」より
     */
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
    }

    /**
     * 【機能概要】: apiKey を暗号化保存する EncryptedSharedPreferences を提供する
     * 【設計方針】: MasterKey(AES256_GCM) を用い、キー/値それぞれ AES256_SIV/AES256_GCM で暗号化する
     * 🔵 信頼性レベル: TASK-0061.md「provideEncryptedSharedPreferences()」・AndroidX Security 公式パターンより
     */
    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "llm_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * 【機能概要】: LLM設定（endpointUrl/apiKey/model）を管理する LlmSettingsRepository を提供する
     * 🔵 信頼性レベル: TASK-0061.md「provideLlmSettingsRepository()」・TASK-0058.md より
     */
    @Provides
    @Singleton
    fun provideLlmSettingsRepository(
        @ApplicationContext context: Context,
        encryptedPrefs: SharedPreferences,
    ): LlmSettingsRepository = LlmSettingsRepositoryImpl(context.llmSettingsDataStore, encryptedPrefs)

    /**
     * 【機能概要】: LLM書き換えAPIを呼び出す LlmRewriteRepository を提供する
     * 🔵 信頼性レベル: TASK-0061.md「provideLlmRewriteRepository()」・TASK-0060.md より
     */
    @Provides
    @Singleton
    fun provideLlmRewriteRepository(httpClient: HttpClient): LlmRewriteRepository =
        LlmRewriteRepositoryImpl(httpClient)
}

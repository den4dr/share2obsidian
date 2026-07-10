package com.den4dr.share2Obsidian.domain.model

data class TemplateField(
    val id: Long = 0,
    val templateId: Long = 0,
    val key: String,
    val valueSource: FieldValueSource,
    val valueType: FieldValueType,
    val defaultValue: String = "",
    val metaKey: HtmlMetaKey? = null,
    // 【フィールド単位のLLM生成用プロンプト】: valueSource == LLM の場合のみ使用。空文字は「未設定」（REQ-104）
    // 🔵 信頼性レベル: TASK-0056 要件定義・interfaces.kt に基づく（推測なし）
    val llmPrompt: String = "",
    val sortOrder: Int = 0,
)

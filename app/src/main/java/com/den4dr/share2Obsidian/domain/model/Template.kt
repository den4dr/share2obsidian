package com.den4dr.share2Obsidian.domain.model

data class Template(
    val id: Long = 0,
    val name: String,
    val body: String = "",
    // 【LLM本文リライト用プロンプト】: 空文字は「未設定」を表し、後続UIでの「メモを更改」ボタン非活性判定に使う（REQ-101, REQ-102）
    // 🔵 信頼性レベル: TASK-0056 要件定義・interfaces.kt に基づく（推測なし）
    val bodyLlmPrompt: String = "",
    val fields: List<TemplateField>,
    val isDefault: Boolean = false,
)

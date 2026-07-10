package com.den4dr.share2Obsidian.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String = "",
    // 【本文用LLMプロンプト】: テンプレート単位の本文リライト用プロンプトを保存する列（REQ-101）。
    // 空文字は「未設定」を表し、既存レコード・既存呼び出し元との後方互換のためデフォルト値 "" を付与する。
    // 🔵 信頼性レベル: TASK-0057 要件定義・database-schema.kt に基づく（推測なし）
    val bodyLlmPrompt: String = "",
    val isDefault: Boolean,
)

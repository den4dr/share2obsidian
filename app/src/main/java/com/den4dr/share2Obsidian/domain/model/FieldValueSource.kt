package com.den4dr.share2Obsidian.domain.model

/**
 * 【カスタムフィールドの値取得元】: TemplateField.valueSource が示す値の生成方法を表す。
 * - FIXED: テンプレートに保存された固定値（defaultValue）をそのまま使用
 * - HTML_META: 共有元ページの HTML メタデータ（metaKey で指定）から取得
 * - URL: 共有元ページの URL を使用
 * - EMPTY: 常に空文字（値なし）
 * - LLM: LLM 生成結果を使用（llmPrompt を元にLLM APIへ問い合わせる。REQ-303）
 *        🔵 信頼性レベル: TASK-0056 要件定義・REQ-303 に基づく（推測なし）
 *        本タスクでは enum 値の追加のみで、実際のLLM呼び出し・値の生成ロジックは TASK-0070/0071/0072 で実装する。
 */
enum class FieldValueSource { FIXED, HTML_META, URL, EMPTY, LLM }

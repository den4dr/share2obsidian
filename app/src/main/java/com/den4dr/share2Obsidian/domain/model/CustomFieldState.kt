package com.den4dr.share2Obsidian.domain.model

/**
 * 【機能概要】: EditScreen上で各カスタムフィールドの編集状態を表すドメインモデル
 * 【実装方針】: valueSource/llmPrompt にデフォルト値を付与し、既存の3引数コンストラクタ呼び出しとの
 * 後方互換性を維持したまま、LLM生成ボタンの表示判定・生成実行に必要なメタ情報を保持できるようにする
 * 【テスト対応】: TC-0070-N03（3引数コンストラクタ互換）、buildCustomFields 系の各テストケースで参照される
 * 🟡 信頼性レベル: interfaces.kt「CustomFieldState（変更後）」・architecture.mdからの妥当な推測
 */
data class CustomFieldState(
    val key: String,
    val value: String,
    val valueType: FieldValueType,
    // 【フィールド値取得元】: EditScreen側でLLM生成ボタンの表示判定（valueSource == LLM）に使用する
    // 🟡 信頼性レベル: interfaces.kt「CustomFieldState（変更後）」に基づく
    val valueSource: FieldValueSource = FieldValueSource.FIXED,
    // 【LLM呼び出し用プロンプト】: valueSource == LLM の場合にLLM呼び出しへ渡すプロンプト。未設定時は空文字
    // 🟡 信頼性レベル: interfaces.kt「CustomFieldState（変更後）」に基づく
    val llmPrompt: String = "",
)

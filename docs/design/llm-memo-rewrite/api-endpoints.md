---
name: llm-memo-rewrite-api-endpoints
description: LLMによるメモ更改機能の追加 外部LLM API連携仕様
metadata:
  type: project
---

# LLMによるメモ更改機能の追加 外部LLM API連携仕様

**作成日**: 2026-07-05
**関連設計**: [architecture.md](architecture.md)
**関連要件定義**: [requirements.md](../../spec/llm-memo-rewrite/requirements.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: EARS要件定義書・設計文書・既存API仕様を参考にした確実な定義
- 🟡 **黄信号**: EARS要件定義書・設計文書・既存API仕様から妥当な推測による定義
- 🔴 **赤信号**: EARS要件定義書・設計文書・既存API仕様にない推測による定義

---

**注記**: 本アプリはAPIを公開する側ではなく、ユーザーが設定した外部LLMサービスのAPIを**呼び出す側**（クライアント）である。本ドキュメントは、その連携先APIの契約（リクエスト/レスポンス形式）を定義する。

---

## 接続先 🔵

**信頼性**: 🔵 *REQ-004・ヒアリング「プロバイダー切替可能（汎用）」より*

- ユーザーが SettingsScreen で設定した任意のエンドポイントURL（OpenAI互換 Chat Completions形式に対応するサービスであること）
- 例: `https://api.openai.com/v1/chat/completions`（OpenAI本家）、または互換プロキシ・ローカルLLMサーバーのURL

## 認証 🔵

**信頼性**: 🔵 *REQ-401・OpenAI Chat Completions API仕様より*

```http
Authorization: Bearer {apiKey}
Content-Type: application/json
```

- `apiKey` は SettingsScreen でユーザーが設定し、暗号化ストレージに保存された値を使用する（REQ-401）

---

## リクエスト仕様 🔵

**信頼性**: 🔵 *REQ-402・OpenAI Chat Completions API仕様より*

### POST {endpointUrl}

**リクエストボディ**:

```json
{
  "model": "gpt-4o",
  "messages": [
    { "role": "system", "content": "{テンプレートの本文用プロンプト、またはタグ提案用固定プロンプト}" },
    { "role": "user", "content": "{共有/取得直後の元コンテンツ（ProcessedContent）}" }
  ]
}
```

| フィールド | 型 | 説明 | 信頼性 |
|-----------|-----|------|--------|
| `model` | string | `LlmSettings.model`（ユーザー設定値） | 🔵 REQ-004 |
| `messages[0].role` | string | `"system"` 固定 | 🟡 OpenAI Chat Completionsの一般的慣習からの推測。直接ヒアリングはしていない |
| `messages[0].content` | string | テンプレートの `bodyLlmPrompt`（本文リライト時）、またはタグ提案用固定プロンプト | 🔵 REQ-002, REQ-101 |
| `messages[1].role` | string | `"user"` 固定 | 🟡 同上 |
| `messages[1].content` | string | `sourceContent`（共有/取得直後の元コンテンツ。EditScreen上の編集済み本文ではない） | 🔵 REQ-002, REQ-302, REQ-406 |

**備考**: `temperature` 等の生成パラメータは本設計では指定しない（サービス側のデフォルト値に委ねる） 🟡 *要件・ヒアリングに明記がないための最小構成での設計判断*

---

## レスポンス仕様 🔵

**信頼性**: 🔵 *OpenAI Chat Completions API仕様より*

**レスポンス（成功時）**:

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "{書き換え/生成されたテキスト}"
      }
    }
  ]
}
```

- `choices[0].message.content` を書き換え結果として使用する（REQ-003）
- `choices` が空、または `message.content` が `null`/空文字の場合は `LlmRewriteResult.Failure.EmptyOrInvalidResponse` として扱う 🟡（EDGE-004、空応答時の扱いは直接確認していない）

---

## エラーレスポンスとマッピング 🔵

**信頼性**: 🔵 *EDGE-001〜004より*

| HTTP状態/例外 | `LlmRewriteResult.Failure` | 表示メッセージ（例） | 対応要件 |
|--------------|---------------------------|---------------------|---------|
| 接続不能（`IOException`, `UnresolvedAddressException`等） | `NetworkError` | 「ネットワークに接続できませんでした」 | EDGE-001 |
| `401 Unauthorized` / `403 Forbidden` | `AuthError` | 「APIキーが正しくありません」 | EDGE-002 |
| `HttpRequestTimeoutException`（30秒経過） | `Timeout` | 「LLMからの応答がタイムアウトしました」 | EDGE-003, NFR-001 |
| `choices` 空 / `content` null・空文字 | `EmptyOrInvalidResponse` | 「LLMから有効な応答が得られませんでした」 | EDGE-004 🟡 |
| その他の予期しない例外 | `Unknown` | 「予期しないエラーが発生しました」 | 🟡 一般的なエラーハンドリング方針からの推測 |

- エラーメッセージはすべて `res/values/strings.xml` に日本語で定義する（既存の `error_obsidian_not_installed` と同様のパターン） 🔵 NFR-201
- 例外オブジェクト自体（APIキーを含むリクエストヘッダ情報等）はログに出力しない（NFR-102） 🔵

---

## タイムアウト設定 🔵

**信頼性**: 🔵 *REQ-202, NFR-001より*

```kotlin
HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
    }
}
```

---

## リクエストボディサイズ・レート制限 🔵

**信頼性**: 🔵 *REQ-403より*

- 本文の文字数上限チェック・APIコスト対策は本リリースでは実施しない（REQ-403、スコープ外と明記済み）
- レート制限（429 Too Many Requests）が発生した場合は `Unknown` Failure として扱う 🟡（要件に明記なし。将来的な専用ハンドリングは対象外とする設計判断）

---

## 接続設定の事前検証 🔵

**信頼性**: 🔵 *REQ-404より*

- SettingsScreen での保存時に接続テスト（疎通確認）は行わない（REQ-404）
- 設定の誤りは実際のリライト実行時にエラーとして判明する（上記エラーマッピング表を参照）

---

## 関連文書

- **アーキテクチャ**: [architecture.md](architecture.md)
- **型定義**: [interfaces.kt](interfaces.kt)
- **データフロー**: [dataflow.md](dataflow.md)
- **要件定義**: [requirements.md](../../spec/llm-memo-rewrite/requirements.md)

## 信頼性レベルサマリー

- 🔵 青信号: 21件 (75%)
- 🟡 黄信号: 7件 (25%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: ✅ 高品質

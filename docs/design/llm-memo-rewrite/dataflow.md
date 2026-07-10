---
name: llm-memo-rewrite-dataflow
description: LLMによるメモ更改機能の追加 データフロー図
metadata:
  type: project
---

# LLMによるメモ更改機能の追加 データフロー図

**作成日**: 2026-07-05
**関連アーキテクチャ**: [architecture.md](architecture.md)
**関連要件定義**: [requirements.md](../../spec/llm-memo-rewrite/requirements.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: EARS要件定義書・設計文書・ユーザヒアリングを参考にした確実なフロー
- 🟡 **黄信号**: EARS要件定義書・設計文書・ユーザヒアリングから妥当な推測によるフロー
- 🔴 **赤信号**: EARS要件定義書・設計文書・ユーザヒアリングにない推測によるフロー

---

## システム全体のデータフロー 🔵

**信頼性**: 🔵 *requirements.md・既存 MainActivity 実装より*

```mermaid
flowchart TD
    A[共有元アプリ] -->|ACTION_SEND| B[MainActivity]
    B --> C[ContentProcessor]
    C --> D[ProcessedContent<br/>元コンテンツ]
    D --> E[TemplateApplicator.buildBody]
    E --> F[resolvedBody<br/>テンプレート適用後]
    D -->|sourceContent として退避| G[EditScreenViewModel]
    F --> G
    G --> H[EditScreen]
    H -->|メモを更改/タグを提案 ボタン押下| I[LlmRewriteRepository]
    I -->|OpenAI互換 Chat Completions| J[外部 LLM API]
    J --> I
    I --> G
    G --> H
    H -->|送信ボタン| K[NoteComposer]
    K --> L[obsidian://new URI]
```

---

## 主要機能のデータフロー

### 機能1: 本文のLLMリライト（Must Have） 🔵

**信頼性**: 🔵 *ユーザーストーリー1.1・受け入れ基準TC-001・REQ-002・REQ-406より*

**関連要件**: REQ-001, REQ-002, REQ-003, REQ-201, REQ-202, REQ-406

```mermaid
sequenceDiagram
    participant MA as MainActivity
    participant VM as EditScreenViewModel
    participant ES as EditScreen
    participant LR as LlmRewriteRepository
    participant LS as LlmSettingsRepository
    participant API as 外部LLM API

    MA->>VM: initialize(processed, config, customFields,<br/>sourceContent, bodyLlmPrompt)
    Note over VM: sourceContent・bodyLlmPrompt を<br/>プライベート状態として保持（REQ-406）
    VM->>ES: formState（rewriteBodyEnabled 含む）

    ES->>VM: rewriteBody()（ボタン押下）
    VM->>VM: isRewritingBody = true（REQ-201）
    VM->>LS: getSettings()
    LS-->>VM: LlmSettings(endpointUrl, apiKey, model)
    VM->>LR: rewrite(settings, bodyLlmPrompt, sourceContent)
    LR->>API: POST {endpointUrl}<br/>messages=[system:prompt, user:sourceContent]
    alt 30秒以内に成功応答
        API-->>LR: choices[0].message.content
        LR-->>VM: LlmRewriteResult.Success(text)
        VM->>VM: body = text（即時上書き、REQ-003）
    else タイムアウト/ネットワークエラー/認証エラー
        API-->>LR: エラー
        LR-->>VM: LlmRewriteResult.Failure(...)
        VM->>VM: errorEvents.emit(messageResId)
        VM-->>ES: errorEvents（SharedFlow）
        ES->>ES: Toast表示（NFR-201）
    end
    VM->>VM: isRewritingBody = false
    VM-->>ES: formState 更新
```

**詳細ステップ**:
1. `MainActivity` は `ContentProcessor` の結果 `ProcessedContent.body`（テンプレート適用前）を `sourceContent` として退避する（REQ-406）
2. `EditScreenViewModel.initialize()` に `sourceContent` とテンプレートの `bodyLlmPrompt` を渡し、ViewModel内部のプライベート状態として保持する
3. ユーザーが「メモを更改」ボタンを押すと `rewriteBody()` が呼ばれ、`sourceContent`（EditScreen上で編集済みの `formState.body` ではない）を入力としてLLM APIを呼び出す
4. 成功時は応答テキストで `formState.body` を即時上書きする（REQ-003）。失敗時は `errorEvents` 経由でEditScreenがToast表示する（NFR-201）

---

### 機能2: タグ提案（Should Have） 🔵

**信頼性**: 🔵 *ユーザーストーリー2.1・REQ-301・REQ-302より*

**関連要件**: REQ-103, REQ-301, REQ-302, REQ-406

```mermaid
sequenceDiagram
    participant ES as EditScreen
    participant VM as EditScreenViewModel
    participant LR as LlmRewriteRepository
    participant API as 外部LLM API

    ES->>VM: suggestTags()（ボタン押下）
    VM->>VM: isSuggestingTags = true
    VM->>LR: rewrite(settings, TAG_SUGGESTION_PROMPT, sourceContent)
    LR->>API: POST {endpointUrl}
    API-->>LR: choices[0].message.content（タグ候補文字列）
    LR-->>VM: LlmRewriteResult.Success(text)
    VM->>VM: tagsText = "${既存tagsText}, ${text}"（REQ-302: 既存タグに追加）
    VM->>VM: isSuggestingTags = false
    VM-->>ES: formState 更新
```

**詳細ステップ**:
1. 入力は本文リライトと同一の `sourceContent`（REQ-302, REQ-406）
2. アプリ内固定のタグ提案用プロンプト（`TAG_SUGGESTION_PROMPT`）を使用する 🟡（テンプレート単位のカスタムプロンプトは要件上明示されていないための設計判断。design-interview.md「残課題」参照）
3. 生成結果は既存の `tagsText` に追加する（置換ではない）

**備考**: タグ提案用プロンプトをテンプレート単位でカスタマイズ可能にするかどうかは、Should Have の実装着手前に再確認を推奨（design-interview.md 残課題）。

---

### 機能3: カスタムフィールドのLLM生成（Could Have） 🟡

**信頼性**: 🟡 *REQ-104・REQ-303・REQ-304から妥当な推測（生成タイミングは設計時点で確定した仮決定）*

**関連要件**: REQ-104, REQ-303, REQ-304

```mermaid
sequenceDiagram
    participant ES as EditScreen
    participant VM as EditScreenViewModel
    participant LR as LlmRewriteRepository
    participant API as 外部LLM API

    Note over ES: カスタムフィールドの valueSource == LLM の場合、<br/>フィールド横に「生成」ボタンを表示（REQ-104）
    ES->>VM: generateCustomFieldValue(index)（ボタン押下）
    VM->>LR: rewrite(settings, field.llmPrompt, sourceContent)
    LR->>API: POST {endpointUrl}
    API-->>LR: choices[0].message.content
    LR-->>VM: LlmRewriteResult.Success(text)
    VM->>VM: customFields[index].value = text
    VM-->>ES: formState 更新
```

**備考**: `TemplateApplicator.buildCustomFields()` の時点（MainActivity起動時）では `FieldValueSource.LLM` のフィールドは空文字で初期化し、実際の値生成はEditScreen上のボタン押下時に行う設計とした（REQ-304の生成タイミングをbody/tagsと統一する設計判断。design-interview.md 残課題参照）。

---

## データ処理パターン

### 同期処理 🔵

**信頼性**: 🔵 *既存アーキテクチャより*

- `TemplateApplicator.buildBody()`/`buildCustomFields()` はMainActivity起動時に同期的に実行（既存パターン、LLM呼び出しは含まない）

### 非同期処理 🔵

**信頼性**: 🔵 *REQ-201, REQ-202より*

- LLM API呼び出し（`rewriteBody()`, `suggestTags()`, `generateCustomFieldValue()`）はすべて `viewModelScope.launch` 内の非同期処理として実行
- 30秒タイムアウトは `HttpTimeout` プラグイン（Ktor）が担当し、`HttpRequestTimeoutException` を捕捉して `LlmRewriteResult.Failure.Timeout` に変換する

---

## エラーハンドリングフロー 🔵

**信頼性**: 🔵 *EDGE-001〜004, NFR-201より*

```mermaid
flowchart TD
    A[LLM API呼び出し] --> B{結果}
    B -->|成功| C[応答テキストで<br/>body/tagsText/customFields上書き]
    B -->|ネットワークエラー| D[Failure.NetworkError]
    B -->|認証エラー 401/403| E[Failure.AuthError]
    B -->|30秒タイムアウト| F[Failure.Timeout]
    B -->|空応答/パース不能| G[Failure.EmptyOrInvalidResponse]
    D --> H[errorEvents.emit]
    E --> H
    F --> H
    G --> H
    H --> I[EditScreen: LaunchedEffectで購読]
    I --> J[Toast.makeText 日本語エラーメッセージ表示]
    J --> K[formStateは変更しない<br/>編集を継続可能]
```

---

## 状態管理フロー

### EditScreenViewModel の状態遷移 🔵

**信頼性**: 🔵 *REQ-201, REQ-202・既存 EditScreenViewModel 実装パターンより*

```mermaid
stateDiagram-v2
    [*] --> 初期状態: initialize()
    初期状態 --> リライト中: rewriteBody()呼び出し
    リライト中 --> 初期状態: 成功（body更新）
    リライト中 --> 初期状態: 失敗（errorEvents発行、bodyは変更なし）
    初期状態 --> タグ提案中: suggestTags()呼び出し
    タグ提案中 --> 初期状態: 成功（tagsText更新）
    タグ提案中 --> 初期状態: 失敗（errorEvents発行）
```

---

## 関連文書

- **アーキテクチャ**: [architecture.md](architecture.md)
- **型定義**: [interfaces.kt](interfaces.kt)
- **DBスキーマ**: [database-schema.kt](database-schema.kt)
- **外部API連携仕様**: [api-endpoints.md](api-endpoints.md)

## 信頼性レベルサマリー

- 🔵 青信号: 14件 (82%)
- 🟡 黄信号: 3件 (18%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: ✅ 高品質

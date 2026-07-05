# 展開内容の編集・プレビュー機能 データフロー図

**作成日**: 2026-03-29
**関連アーキテクチャ**: [architecture.md](architecture.md)
**関連要件定義**: [requirements.md](../../spec/content-edit-preview/requirements.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: 要件定義書・ユーザヒアリングを参考にした確実なフロー
- 🟡 **黄信号**: 要件定義書・ユーザヒアリングから妥当な推測によるフロー
- 🔴 **赤信号**: 要件定義書・ユーザヒアリングにない推測によるフロー

---

## システム全体のデータフロー 🔵

**信頼性**: 🔵 *REQ-001, REQ-101, REQ-201, REQ-301・既存設計より*

```mermaid
flowchart TD
    A[他アプリから共有] -->|ACTION_SEND intent| B[MainActivity.onCreate]
    B --> C[ContentTypeDetector.detect]
    C -->|null| Z1[finish]
    C -->|ShareContent| D{コンテンツタイプ}

    D -->|URL| E[setContent: LoadingScreen]
    E --> F[UrlContentProcessor.process]
    F --> G[ProcessedContent]

    D -->|Text/Html/File| H[ContentProcessor.process]
    H --> G

    G --> I[viewModel.initialize\nProcessedContent + NoteConfig.fromAppConfig]
    I --> J[setContent: EditScreen]
    J --> K{ユーザー操作}

    K -->|送信ボタン| L[viewModel.onSend\ntags: parseTagsText]
    L --> M[NoteComposer.buildFrontmatter\ntitle, body, tags]
    M --> N[NoteComposer.buildUri\ncontent, title, NoteConfig]
    N --> O[startActivity\nobsidian://new?...]
    O -->|成功| P[finish]
    O -->|ActivityNotFoundException| Q[Toast: Obsidian未インストール]
    Q --> P

    K -->|キャンセルボタン| R[finish]
```

---

## フロー1: テキスト共有時 🔵

**信頼性**: 🔵 *REQ-001, REQ-003・ユーザストーリー1.1より*

**関連要件**: REQ-001, REQ-003, REQ-101

```mermaid
sequenceDiagram
    participant A as 他アプリ
    participant M as MainActivity
    participant D as ContentTypeDetector
    participant P as TextContentProcessor
    participant VM as EditScreenViewModel
    participant UI as EditScreen
    participant NC as NoteComposer

    A->>M: ACTION_SEND (text/plain, EXTRA_TEXT)
    M->>D: detect(intent)
    D-->>M: ShareContent.Text(text, title?)
    M->>P: process(ShareContent.Text)
    P-->>M: ProcessedContent(body=text, title=title?)
    M->>VM: initialize(processed, NoteConfig.fromAppConfig())
    M->>UI: setContent { EditScreen(viewModel) }
    UI-->>M: フォーム表示（初期値入力済み）

    Note over UI: ユーザーが編集

    UI->>VM: onSend()
    VM->>VM: parseTagsText() → List<String>
    VM-->>UI: SendParams(title, body, tags, config)
    UI->>NC: buildFrontmatter(title, body, tags)
    NC-->>UI: frontmatterContent
    UI->>NC: buildUri(frontmatterContent, title, config)
    NC-->>UI: obsidian://new?...
    UI->>M: startActivity(Intent(ACTION_VIEW, uri))
    M->>M: finish()
```

**詳細ステップ**:
1. `EXTRA_TEXT` → `TextContentProcessor.process()` → `ProcessedContent(body=text, title=EXTRA_SUBJECT?)`
2. `viewModel.initialize()` で `EditFormState` の初期値をセット
3. `EditScreen` が初期値入力済みのフォームを表示
4. 送信時: `tagsText` をカンマ区切りパース → `NoteComposer` 経由で Frontmatter + URI 生成

---

## フロー2: URL共有時 🔵

**信頼性**: 🔵 *REQ-301・ユーザストーリー1.2・ユーザヒアリングより*

**関連要件**: REQ-001, REQ-301, REQ-302

```mermaid
sequenceDiagram
    participant A as 他アプリ
    participant M as MainActivity
    participant LS as LoadingScreen
    participant P as UrlContentProcessor
    participant VM as EditScreenViewModel
    participant UI as EditScreen

    A->>M: ACTION_SEND (text/plain, URL)
    M->>LS: setContent { LoadingScreen() }
    LS-->>A: ローディング表示

    M->>P: process(ShareContent.Url) [suspend]
    Note over P: WebView で本文抽出 (最大10秒)

    alt 抽出成功
        P-->>M: ProcessedContent(body=抽出テキスト, title=ページタイトル?)
    else タイムアウト
        P-->>M: ProcessedContent(body=URL文字列, title=null)
    end

    M->>VM: initialize(processed, NoteConfig.fromAppConfig())
    M->>UI: setContent { EditScreen(viewModel) }
    UI-->>A: 編集画面表示（本文フィールドに抽出テキスト）
```

**詳細ステップ**:
1. URL受信直後に `LoadingScreen` を表示（既存動作）
2. `UrlContentProcessor` が WebView で本文抽出（タイムアウト: 10秒）
3. タイムアウト時は URL 文字列をそのまま body に設定（REQ-302 フォールバック）
4. 完了後 `LoadingScreen` → `EditScreen` に置き換え

---

## フロー3: 送信ボタンタップ 🔵

**信頼性**: 🔵 *REQ-101, REQ-103・ユーザストーリー2.1/2.2より*

**関連要件**: REQ-101, REQ-102, REQ-103

```mermaid
sequenceDiagram
    participant UI as EditScreen
    participant VM as EditScreenViewModel
    participant NC as NoteComposer
    participant M as MainActivity

    UI->>VM: onSend()
    VM->>VM: formState.tagsText → split(",").map { trim() }.filter { notEmpty() }
    VM-->>UI: SendParams(title, body, tags: List<String>, config: NoteConfig)

    UI->>NC: buildFrontmatter(title?, body, tags)
    Note over NC: "---\ntitle: ...\ntags: [...]\n---\n\nbody"
    NC-->>UI: frontmatterContent: String

    UI->>NC: buildUri(frontmatterContent, title?, config)
    Note over NC: Uri.Builder + appendQueryParameter
    NC-->>UI: uri: Uri

    UI->>M: startActivity(Intent(ACTION_VIEW, uri))
    alt Obsidian インストール済み
        M->>M: finish()
    else ActivityNotFoundException
        M->>M: Toast(R.string.error_obsidian_not_installed)
        M->>M: finish()
    end
```

**タグパース仕様** (REQ-103):
```
入力: "shared,  web , clipping "
分割: ["shared", "  web ", " clipping "]
trim: ["shared", "web", "clipping"]
空除去: ["shared", "web", "clipping"]
出力: tags: [shared, web, clipping]
```

**エッジケース**:
- 空文字 `""` → `[]` → `tags: []` (EDGE-003)
- カンマのみ `","` → `["", ""]` → trim/filter → `[]` → `tags: []`

---

## フロー4: キャンセルボタンタップ 🔵

**信頼性**: 🔵 *REQ-201・ユーザストーリー1.4より*

**関連要件**: REQ-201

```mermaid
sequenceDiagram
    participant UI as EditScreen
    participant M as MainActivity

    UI->>M: onCancel()
    Note over M: startActivity は呼ばない
    M->>M: finish()
```

---

## NoteConfig データフロー 🔵

**信頼性**: 🔵 *ユーザヒアリング（将来のユーザー設定）・REQ-405より*

```mermaid
flowchart LR
    subgraph 現在
        AC[AppConfig\nOBSIDIAN_VAULT\nOBSIDIAN_FOLDER\nOBSIDIAN_TAGS] -->|fromAppConfig| NC[NoteConfig\nvault\nfolder\ndefaultTags]
    end

    subgraph 将来の拡張
        US[ユーザー設定\nSharedPreferences\nor Room DB] -->|fromUserSettings| NC
    end

    NC --> VM[EditScreenViewModel\ninitialize]
    VM -->|初期値| FS[EditFormState\nfolder = config.folder\ntagsText = config.defaultTags.joinToString]
    FS --> ES[EditScreen フォーム表示]
    ES -->|onSend| SP[SendParams\nconfig: NoteConfig]
    SP --> NCA[NoteComposer.buildUri\nconfig.vault, config.folder]
```

**設計意図**: `NoteConfig` は現在 `AppConfig` の値をそのまま使用するが、将来的にユーザーが vault・folder・defaultTags を設定できるようにする際の拡張ポイントとなる。`EditScreenViewModel` は `NoteConfig` を受け取るため、設定ソースが変わっても ViewModel の実装変更は最小限で済む。

---

## EditFormState 状態変化 🔵

**信頼性**: 🔵 *REQ-003, REQ-101・EDGE-101より*

```mermaid
stateDiagram-v2
    [*] --> 未初期化: onCreate
    未初期化 --> 初期値セット: viewModel.initialize(processed, config)
    初期値セット --> 編集中: ユーザーがフィールドをタップ
    編集中 --> 編集中: フィールド更新
    初期値セット --> 送信処理: 送信ボタン（初期値のまま）
    編集中 --> 送信処理: 送信ボタン
    送信処理 --> [*]: finish()
    初期値セット --> [*]: キャンセルボタン
    編集中 --> [*]: キャンセルボタン / バックボタン

    note right of 初期値セット
        ViewModel の StateFlow に保持
        画面回転後も復元（EDGE-101）
    end note
```

---

## 関連文書

- **アーキテクチャ**: [architecture.md](architecture.md)
- **Kotlinインターフェース**: [interfaces.kt](interfaces.kt)
- **要件定義**: [requirements.md](../../spec/content-edit-preview/requirements.md)
- **既存データフロー**: [share-content-expansion/dataflow.md](../share-content-expansion/dataflow.md)

## 信頼性レベルサマリー

- 🔵 青信号: 8件 (89%)
- 🟡 黄信号: 1件 (11%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: 高品質

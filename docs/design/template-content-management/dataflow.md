---
name: template-content-management-dataflow
description: テンプレートの管理内容の変更 データフロー図
metadata:
  type: project
---

# テンプレートの管理内容の変更 データフロー図

**作成日**: 2026-06-07
**関連アーキテクチャ**: [architecture.md](architecture.md)
**関連要件定義**: [requirements.md](../../spec/template-content-management/requirements.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: EARS要件定義書・設計文書・ユーザヒアリングを参考にした確実なフロー
- 🟡 **黄信号**: EARS要件定義書・設計文書・ユーザヒアリングから妥当な推測によるフロー
- 🔴 **赤信号**: EARS要件定義書・設計文書・ユーザヒアリングにない推測によるフロー

---

## フロー1: URL 共有 → テンプレート・本文適用 → EditScreen 表示 🔵

**信頼性**: 🔵 *REQ-031, REQ-032, REQ-041〜043・ユーザストーリー 1.2, 2.1 より*

**変更点**: 旧フローと比較して ③テンプレート適用でvault/folder取得元が変わり、④body解決が追加される

```
ユーザー（ブラウザ）
    │  ACTION_SEND (URL)
    ▼
MainActivity.onCreate()
    │  ContentTypeDetector.detect(intent) → ShareContent.Url
    │  setContent { LoadingScreen() }
    │
    │  lifecycleScope.launch (Coroutines)
    │  ┌────────────────────────────────────────────────────────────┐
    │  │ ① コンテンツ処理（変更なし）                               │
    │  │    UrlContentProcessor.process(ShareContent.Url)          │
    │  │    → ProcessedContent(body, title, metadata, sourceUrl)   │
    │  │                                                            │
    │  │ ② 設定・テンプレート並行取得（変更あり）                   │
    │  │    noteSettingsRepository.getSettings()  ← 新規           │
    │  │      → NoteSettings(vault="", folder="")                  │
    │  │    templateRepository.getDefaultTemplate()                 │
    │  │      → Template?(name, body, fields, isDefault)           │
    │  │         ※ Template から vault/folder は削除済み            │
    │  │                                                            │
    │  │ ③ 設定適用（変更あり: DataStore 由来に変更）               │
    │  │    config = TemplateApplicator.buildConfig(noteSettings)  │
    │  │      → NoteConfig(vault=noteSettings.vault,               │
    │  │                   folder=noteSettings.folder,              │
    │  │                   defaultTags=AppConfig.OBSIDIAN_TAGS)    │
    │  │                                                            │
    │  │ ④ 本文テンプレート適用（新規）                             │
    │  │    body = TemplateApplicator.buildBody(template,          │
    │  │                                        processed.body)    │
    │  │      ├─ template?.body が空 or null                        │
    │  │      │    → body = processed.body（共有コンテンツそのまま）│
    │  │      └─ template?.body に {{content}} あり                │
    │  │           → body = template.body.replace(                 │
    │  │                      "{{content}}", processed.body)       │
    │  │                                                            │
    │  │ ⑤ カスタムフィールド適用（変更なし）                       │
    │  │    customFields = TemplateApplicator.buildCustomFields(   │
    │  │                     template, processed)                  │
    │  │                                                            │
    │  │ ⑥ ViewModel 初期化（変更あり: body を上書き）              │
    │  │    viewModel.initialize(                                   │
    │  │      processed.copy(body = body),  ← body を上書き        │
    │  │      config,                                               │
    │  │      customFields)                                         │
    │  └────────────────────────────────────────────────────────────┘
    │
    │  setContent { EditScreen(viewModel, ...) }
    ▼
EditScreen（変更後の表示順）
    ├─ vault 欄: config.vault（DataStore値 or ""）   ← 新規追加
    ├─ folder 欄: config.folder（DataStore値 or ""） ← 位置変更
    ├─ title 欄: processed.title ?: ""              ← 位置変更（ファイル名として明示）
    ├─ frontmatter フィールド: customFields          ← 位置変更
    └─ body 欄: buildBody()解決済みの本文           ← 位置変更
```

---

## フロー2: EditScreenViewModel の状態管理と buildSendParams 変更 🔵

**信頼性**: 🔵 *REQ-061, REQ-062・設計ヒアリング「EditFormStateからvault/folderを取得」より*

```
EditScreenViewModel.initialize(processed, config, customFields)
    │
    │  _formState.value = EditFormState(
    │    vault = config.vault,       ← 新規: DataStore由来
    │    folder = config.folder,
    │    title = processed.title ?: "",
    │    body = processed.body,      ← buildBody()解決済み
    │    tagsText = config.defaultTags.joinToString(", "),
    │    customFields = customFields,
    │  )
    ▼
EditScreen（ユーザー編集）
    ├─ vault 変更 → viewModel.updateVault(vault)   ← 新規
    ├─ folder 変更 → viewModel.updateFolder(folder)
    ├─ title 変更 → viewModel.updateTitle(title)
    ├─ body 変更 → viewModel.updateBody(body)
    ├─ tagsText 変更 → viewModel.updateTagsText(tagsText)
    └─ customField 変更 → viewModel.updateCustomField(index, value)
    │
    │  「送信」ボタンタップ
    ▼
viewModel.buildSendParams()   ← 引数削除（変更後）
    │
    │  state = _formState.value
    │  return SendParams(
    │    title = state.title.ifBlank { null },
    │    body = state.body,
    │    tags = parseTagsText(state.tagsText),
    │    config = NoteConfig(      ← EditFormState から構築（変更後）
    │      vault = state.vault,
    │      folder = state.folder,
    │      defaultTags = emptyList()
    │    ),
    │    customFields = state.customFields,
    │  )
    ▼
MainActivity.onSend(sendParams)
    │  NoteComposer.buildFrontmatter(sendParams.body, sendParams.tags, sendParams.customFields)
    │  NoteComposer.buildUri(content, sendParams.title, sendParams.config)
    │    → obsidian://new?content=...&vault={state.vault}&folder={state.folder}&name=...
    ▼
startActivity(Intent(ACTION_VIEW, uri))
```

---

## フロー3: SettingsScreen での vault/folder 設定 🔵

**信頼性**: 🔵 *REQ-021・設計ヒアリング「SettingsScreenに追加」より*

```
ユーザー（アイコンタップまたはEditScreen→設定アイコン）
    │
    ▼
SettingsScreen（変更後）
    │
    │  SettingsViewModel（新規）
    │    │  noteSettingsRepository.getSettings(): Flow<NoteSettings>
    │    │    → DataStore Preferences から vault/folder を読み込み
    │    ▼
    │  uiState: StateFlow<NoteSettings>(vault, folder)
    │
    ├─ vault 欄: 現在の DataStore 値で初期化
    ├─ folder 欄: 現在の DataStore 値で初期化
    └─ テンプレート管理へのナビゲーション（既存）
    │
    │  ユーザーが vault 欄を変更
    ▼
SettingsViewModel.updateVault(newVault)
    │  viewModelScope.launch (IO)
    │  noteSettingsRepository.saveVault(newVault)
    │    → DataStore.edit { prefs → prefs[VAULT_KEY] = newVault }
    ▼
DataStore 更新完了
    │  Flow<NoteSettings> が新しい値を emit
    ▼
SettingsScreen の vault 欄が更新（State 反映）
```

---

## フロー4: テンプレートの新規作成（変更後） 🔵

**信頼性**: 🔵 *REQ-051, REQ-052, REQ-053・TemplateEditViewModel 変更後より*

**変更点**: vault/folder 入力がなくなり、body 入力が追加される

```
TemplateListScreen
    │  「+」ボタンタップ
    ▼
TemplateEditScreen（新規モード、変更後）
    │
    │  TemplateEditUiState（変更後）:
    │  ├─ name: ""
    │  ├─ isDefault: false
    │  ├─ body: ""           ← 新規追加（vault/folder は削除）
    │  └─ fields: []
    │
    │  ユーザーが入力:
    │  ├─ テンプレート名: "Web記事"
    │  ├─ isDefault: true
    │  ├─ body: "## 記事\n{{content}}\n\n## メモ\n"  ← 新規
    │  └─ フィールド追加: source=URL, description=HTML_META(OG_DESCRIPTION)
    │
    │  「保存」ボタンタップ
    │  TemplateEditViewModel.save()
    │       │
    │       │  Template(
    │       │    id = 0, name = "Web記事",
    │       │    body = "## 記事\n{{content}}\n\n## メモ\n",  ← 新規
    │       │    isDefault = true,
    │       │    fields = [...]
    │       │    ※ vault/folder は含まない
    │       │  )
    │       ▼
    │  templateRepository.saveTemplate(template)  → DB保存
    ▼
TemplateListScreen（新規テンプレートが一覧に反映）
```

---

## フロー5: テンプレート適用時の body 解決パターン 🔵

**信頼性**: 🔵 *REQ-011〜014, EDGE-001〜002・ユーザヒアリングより*

```
TemplateApplicator.buildBody(template, sharedBody)

パターン A: template = null または template.body = ""
    │  REQ-013, REQ-014
    └─ return sharedBody（共有コンテンツをそのまま使用）

パターン B: template.body = "## 記事\n{{content}}\n\n## メモ\n"
    │  REQ-012, EDGE-001（{{content}} が1つ）
    └─ return "## 記事\n{sharedBody}\n\n## メモ\n"

パターン C: template.body = "{{content}}\n---\n{{content}}"
    │  EDGE-001（{{content}} が複数）
    └─ return "{sharedBody}\n---\n{sharedBody}"
        ※ String.replace() で全置換

パターン D: template.body = "固定テキスト"（{{content}} なし）
    │  EDGE-002
    └─ return "固定テキスト"（共有コンテンツは含まない）
```

---

## フロー6: DataStore NoteSettings 初回起動 🟡

**信頼性**: 🟡 *DataStore Preferences の標準動作から妥当な推測*

```
アプリ初回起動
    │
    ▼
noteSettingsRepository.getSettings()
    │  DataStore にキーが存在しない
    │  → vault = "" (デフォルト値)
    │  → folder = "" (デフォルト値)
    ▼
EditScreen
    ├─ vault 欄: ""（空欄）
    └─ folder 欄: ""（空欄）
    │
    │  NoteComposer.buildUri() で vault = "" → URI に vault パラメータなし
    └─ obsidian://new?content=...&name=...  （vault, folder なし）
```

---

## フロー7: Room DB マイグレーション (v1 → v2) 🔵

**信頼性**: 🔵 *REQ-003, REQ-004, NFR-001・Room Migration 公式パターンより*

```
アプリ更新（v1 DB が存在する状態）
    │
    ▼
AppDatabase.build()
    │  既存 DB version = 1 を検出
    │  Migration(1, 2) を実行
    │
    │  1. ALTER TABLE templates ADD COLUMN body TEXT NOT NULL DEFAULT ''
    │     → 既存レコードは body = '' で追加される（REQ-004: 既存データ保護）
    │  2. ALTER TABLE templates DROP COLUMN vault
    │  3. ALTER TABLE templates DROP COLUMN folder
    │
    ▼
DB version = 2（移行完了）
    │  既存テンプレートの name, isDefault, fields は保持される
    │  vault, folder は削除（DataStore で管理）
    └─ body = "" として初期化（テンプレートに本文なし）
```

---

## 関連文書

- **アーキテクチャ**: [architecture.md](architecture.md)
- **型定義**: [interfaces.kt](interfaces.kt)
- **DBスキーマ**: [database-schema.kt](database-schema.kt)

## 信頼性レベルサマリー

- 🔵 青信号: 13件 (93%)
- 🟡 黄信号: 1件 (7%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: ✅ 高品質

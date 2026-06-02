# 編集テンプレートの管理機能 データフロー図

**作成日**: 2026-05-31
**関連アーキテクチャ**: [architecture.md](architecture.md)
**関連要件定義**: [requirements.md](../../spec/edit-template-management/requirements.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: EARS要件定義書・設計文書・ユーザヒアリングを参考にした確実なフロー
- 🟡 **黄信号**: EARS要件定義書・設計文書・ユーザヒアリングから妥当な推測によるフロー
- 🔴 **赤信号**: EARS要件定義書・設計文書・ユーザヒアリングにない推測によるフロー

---

## フロー1: URL 共有 → テンプレート適用 → EditScreen 表示 🔵

**信頼性**: 🔵 *REQ-051, REQ-052, REQ-071・ユーザストーリー 3.1 より*

**関連要件**: REQ-051, REQ-052, REQ-071, REQ-072

```
ユーザー（ブラウザ）
    │  ACTION_SEND (URL)
    ▼
MainActivity.onCreate()
    │  ContentTypeDetector.detect(intent) → ShareContent.Url
    │  setContent { LoadingScreen() }  ← URL の場合のみ
    │
    │  lifecycleScope.launch (Coroutines)
    │  ┌─────────────────────────────────────────────────────┐
    │  │ 1. UrlContentProcessor.process(ShareContent.Url)    │
    │  │    └─ WebViewExtractor.extract(url)                 │
    │  │         │  JS: JSON.stringify({ body, ogTitle,      │
    │  │         │       ogDescription, publishedTime,        │
    │  │         │       modifiedTime, author })              │
    │  │         │  AndroidBridge.onExtracted(json)          │
    │  │         ▼                                           │
    │  │    WebViewExtractionResult(                         │
    │  │      bodyText, ogTitle, ogDescription,              │
    │  │      publishedTime, modifiedTime, author)           │
    │  │         │                                           │
    │  │         ▼                                           │
    │  │    ProcessedContent(                                │
    │  │      body = result.bodyText ?: url,                 │
    │  │      title = shareContent.title,                    │
    │  │      contentType = URL,                             │
    │  │      metadata = mapOf(                              │
    │  │        OG_TITLE → result.ogTitle,                   │
    │  │        OG_DESCRIPTION → result.ogDescription,       │
    │  │        URL → url,                                   │
    │  │        PUBLISHED_DATE → result.publishedTime,       │
    │  │        MODIFIED_DATE → result.modifiedTime,         │
    │  │        AUTHOR → result.author),                     │
    │  │      sourceUrl = url)                               │
    │  │                                                     │
    │  │ 2. templateRepository.getDefaultTemplate()         │
    │  │         │  (suspend, IO Dispatcher)                 │
    │  │         ▼                                           │
    │  │    Template? (null の場合は AppConfig フォールバック)│
    │  │                                                     │
    │  │ 3. テンプレート適用                                  │
    │  │    ├─ config = NoteConfig(                          │
    │  │    │    vault = template.vault,                     │
    │  │    │    folder = template.folder,                   │
    │  │    │    defaultTags = AppConfig.OBSIDIAN_TAGS)       │
    │  │    └─ customFields = template.fields.map { field →  │
    │  │         val value = when(field.valueSource) {       │
    │  │           FIXED   → field.defaultValue              │
    │  │           HTML_META → processed.metadata[field.metaKey] ?: "" │
    │  │           URL     → processed.sourceUrl ?: ""       │
    │  │           EMPTY   → ""                              │
    │  │         }                                           │
    │  │         CustomFieldState(field.key, value, field.valueType) │
    │  │       }                                             │
    │  │                                                     │
    │  │ 4. viewModel.initialize(processed, config, customFields) │
    │  └─────────────────────────────────────────────────────┘
    │
    │  setContent { EditScreen(viewModel, config, ...) }
    ▼
EditScreen
    ├─ タイトルフィールド: processed.title ?: ""
    ├─ 本文フィールド: processed.body
    ├─ タグフィールド: config.defaultTags (または template の FIXED tags フィールド)
    ├─ フォルダフィールド: config.folder
    └─ カスタムフィールドセクション:
         [key: sourceUrl value, key2: ogDescription value, ...]
```

---

## フロー2: テンプレートの新規作成 🔵

**信頼性**: 🔵 *REQ-012, REQ-015, REQ-031〜033, REQ-061・ユーザストーリー 1.2, 2.1 より*

**関連要件**: REQ-012, REQ-015, REQ-031〜033, REQ-061

```
ユーザー
    │  SettingsScreen「テンプレート管理」タップ
    ▼
MainActivity (showTemplateList = true)
    ▼
TemplateListScreen
    │  viewModel.templates (Flow<List<Template>>) → LazyColumn に表示
    │  「+」ボタンタップ
    ▼
MainActivity (editingTemplateId = -1L ← 新規を示す定数)
    ▼
TemplateEditScreen (新規モード: templateId = null)
    │
    │  ユーザーが入力:
    │  ├─ テンプレート名: "Web記事"
    │  ├─ vault: "myVault"
    │  ├─ folder: "Clippings"
    │  ├─ isDefault: true（トグル）
    │  └─ フィールド追加:
    │       ├─ key="source", source=URL
    │       ├─ key="description", source=HTML_META(OG_DESCRIPTION), type=STRING
    │       └─ key="status", source=FIXED, value="draft", type=STRING
    │
    │  「保存」ボタンタップ
    │  TemplateEditViewModel.save()
    │       │
    │       │  Dispatchers.IO (coroutine)
    │       ▼
    │  templateRepository.saveTemplate(template, fields)
    │       │
    │       ▼
    │  TemplateDao (Room)
    │       ├─ isDefault=true の場合: clearDefaultExcept(newId) を実行
    │       │   → 既存デフォルトを解除（REQ-022）
    │       ├─ insertTemplate(TemplateEntity) → newId
    │       └─ insertFields(fields.map { TemplateFieldEntity(..., templateId=newId) })
    │
    │  onNavigateBack() → MainActivity (editingTemplateId = null)
    ▼
TemplateListScreen（新規テンプレートが一覧に反映）
```

---

## フロー3: 送信（カスタムフィールドあり） 🔵

**信頼性**: 🔵 *REQ-052, REQ-404, EDGE-005・NoteComposer 既存実装より*

**関連要件**: REQ-404, EDGE-005 (カスタムが上書き)

```
EditScreen
    │  「送信」ボタンタップ
    ▼
viewModel.buildSendParams(config)
    │  SendParams(
    │    title = formState.title.ifBlank { null },
    │    body = formState.body,
    │    tags = parseTagsText(formState.tagsText),
    │    config = config,
    │    customFields = formState.customFields)
    ▼
MainActivity.onSend(sendParams)
    │
    ▼
NoteComposer.buildFrontmatter(
    body = sendParams.body,
    tags = sendParams.tags,
    customFields = sendParams.customFields)
    │
    │  例: customFields = [
    │    CustomFieldState("source", "https://example.com", STRING),
    │    CustomFieldState("description", "記事の概要", STRING),
    │    CustomFieldState("status", "draft", STRING)
    │  ]
    │
    │  出力:
    │  ---
    │  source: https://example.com
    │  description: 記事の概要
    │  status: draft
    │  tags: [shared]        ← "tags" がcustomKeysに含まれない場合のみ
    │  ---
    │
    │  [もしカスタムに key="tags" があれば tags 行は出力しない → 上書き動作]
    ▼
NoteComposer.buildUri(content, title, config)
    │  obsidian://new?content=...&vault=...&folder=...&name=...
    ▼
startActivity(Intent(ACTION_VIEW, uri))
```

---

## フロー4: HTML 共有 → Jsoup メタデータ抽出 🔵

**信頼性**: 🔵 *REQ-071・HtmlContentProcessor 既存実装・Jsoup 導入済みより*

**関連要件**: REQ-071, REQ-072

```
ユーザー（SNS アプリなど）
    │  ACTION_SEND (HTML)
    ▼
MainActivity
    │  ContentTypeDetector.detect → ShareContent.Html(html, fallbackText, title)
    ▼
HtmlContentProcessor.process(ShareContent.Html)
    │
    │  1. Jsoup.parse(html) → doc
    │  2. body = HtmlToMarkdownConverter.convert(html)
    │
    │  3. extractMetadata(doc, sourceUrl = null):
    │     mapOf(
    │       OG_TITLE    → doc.select("meta[property=og:title]").attr("content")
    │                     ?: doc.title(),
    │       OG_DESC     → doc.select("meta[property=og:description]").attr("content")
    │                     ?: doc.select("meta[name=description]").attr("content"),
    │       URL         → "",   ← HTML 共有時は URL なし
    │       PUB_DATE    → doc.select("meta[property=article:published_time]").attr("content"),
    │       MOD_DATE    → doc.select("meta[property=article:modified_time]").attr("content"),
    │       AUTHOR      → doc.select("meta[name=author]").attr("content")
    │                     ?: doc.select("meta[property=og:site_name]").attr("content")
    │     )
    │
    ▼
ProcessedContent(
    body = markdownBody,
    title = html.title ?: extractedOgTitle,
    contentType = HTML,
    metadata = Map<HtmlMetaKey, String>,
    sourceUrl = null)
```

---

## フロー5: デフォルトテンプレート未設定時（後方互換） 🔵

**信頼性**: 🔵 *REQ-023, EDGE-001・既存実装より*

```
MainActivity (共有フロー)
    │
    ▼
templateRepository.getDefaultTemplate()
    │  → null（テンプレート未設定）
    │
    ▼
config = NoteConfig.fromAppConfig()
    │  vault = AppConfig.OBSIDIAN_VAULT
    │  folder = AppConfig.OBSIDIAN_FOLDER
    │  defaultTags = AppConfig.OBSIDIAN_TAGS
    │
customFields = emptyList()
    │
    ▼
viewModel.initialize(processed, config, customFields = emptyList())
    │
    ▼
EditScreen（カスタムフィールドセクションは非表示）
```

---

## フロー6: テンプレート削除 🟡

**信頼性**: 🟡 *REQ-014, EDGE-002 から妥当な推測*

```
TemplateListScreen
    │  削除アイコンタップ → 確認ダイアログ表示
    │  「削除」確認
    ▼
TemplateListViewModel.deleteTemplate(template)
    │  Dispatchers.IO
    ▼
TemplateDao.deleteTemplate(TemplateEntity)
    │  ForeignKey CASCADE → template_fields も自動削除
    ▼
Flow<List<TemplateWithFields>> が更新
    ▼
TemplateListScreen（一覧から削除されたテンプレートが消える）
    │
    │  [削除したテンプレートが isDefault=true だった場合]
    │  → デフォルトが 0 件になる → EDGE-001 動作（AppConfig フォールバック）
```

---

## 状態管理フロー（TemplateEditScreen） 🔵

**信頼性**: 🔵 *既存 EditScreenViewModel パターン・tech-stack.md より*

```
TemplateEditViewModel
    │
    │  @HiltViewModel (inject: TemplateRepository)
    │
    │  _uiState: MutableStateFlow<TemplateEditUiState>
    │  uiState: StateFlow<TemplateEditUiState> (公開)
    │
    │  init:
    │    if (templateId != null) {
    │      loadTemplate(templateId)   ← suspend, IO Dispatcher
    │    } else {
    │      _uiState.value = TemplateEditUiState.empty()
    │    }
    │
    │  addField(key, valueSource, valueType, defaultValue, metaKey) → uiState更新
    │  removeField(index) → uiState更新
    │  updateName(name) → uiState更新
    │  updateVault(vault) → uiState更新
    │  updateFolder(folder) → uiState更新
    │  updateIsDefault(isDefault) → uiState更新
    │
    │  save():
    │    Dispatchers.IO
    │    templateRepository.saveTemplate(
    │      template = Template(id, name, vault, folder, fields, isDefault),
    │      isNew = (templateId == null)
    │    )
    │
    ▼
TemplateEditScreen
    │  uiState.collectAsState() → フォーム表示
    │  ユーザー操作 → viewModel.updateXxx()
    │  保存 → viewModel.save() → onNavigateBack()
```

---

## エラーハンドリングフロー 🟡

**信頼性**: 🟡 *既存実装パターン・REQ-403 から妥当な推測*

```
Room DB 操作失敗
    │  (Coroutine exception)
    ├─ TemplateListViewModel: uiState に error メッセージを設定
    │  → TemplateListScreen でスナックバー表示
    │
    └─ MainActivity でのテンプレート読み込み失敗:
       → null として扱い AppConfig フォールバック（EDGE-001 と同じ動作）

HTML メタデータ取得失敗
    │  (JSONException などの parse エラー)
    └─ 空文字列として処理（EDGE-003 と同じ動作）

WebView タイムアウト
    │  (既存動作を継続)
    └─ bodyText = null → url をそのまま body として使用
       metadata = emptyMap() として扱う
```

---

## 関連文書

- **アーキテクチャ**: [architecture.md](architecture.md)
- **型定義**: [interfaces.kt](interfaces.kt)
- **DB スキーマ**: [database-schema.kt](database-schema.kt)

## 信頼性レベルサマリー

- 🔵 青信号: 8件 (80%)
- 🟡 黄信号: 2件 (20%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: 高品質

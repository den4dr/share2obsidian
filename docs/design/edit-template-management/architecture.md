# 編集テンプレートの管理機能 アーキテクチャ設計

**作成日**: 2026-05-31
**関連要件定義**: [requirements.md](../../spec/edit-template-management/requirements.md)
**ヒアリング記録**: [design-interview.md](design-interview.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: EARS要件定義書・設計文書・ユーザヒアリングを参考にした確実な設計
- 🟡 **黄信号**: EARS要件定義書・設計文書・ユーザヒアリングから妥当な推測による設計
- 🔴 **赤信号**: EARS要件定義書・設計文書・ユーザヒアリングにない推測による設計

---

## システム概要 🔵

**信頼性**: 🔵 *requirements.md REQ-001〜REQ-072・ユーザヒアリングより*

既存の単一 Activity + Compose UI アーキテクチャを維持したまま、以下を追加する:

1. **テンプレート管理 UI**: SettingsScreen → TemplateListScreen → TemplateEditScreen の 3 段階ナビゲーション
2. **Room DB**: Template と TemplateField を永続化する 2 テーブル構成
3. **Hilt DI**: Application クラス追加、Repository/ViewModel の依存注入
4. **HTML メタデータ抽出**: WebViewExtractor の JavaScript 拡張 + Jsoup による HTML 解析
5. **カスタムフィールド適用**: EditScreen にカスタムフィールドセクションを追加

---

## アーキテクチャパターン 🔵

**信頼性**: 🔵 *tech-stack.md・既存設計・ユーザヒアリングより*

- **パターン**: 既存の単一アクティビティ + Compose UI + MVVM + Repository を継続
- **DI**: Hilt（KSP で annotation processing）— **今回新規導入**
- **ナビゲーション**: Compose の `rememberSaveable { mutableStateOf() }` によるネスト状態管理（既存パターン継続、Jetpack Navigation は不使用）

---

## コンポーネント構成

### 新規追加コンポーネント 🔵

**信頼性**: 🔵 *要件定義・ユーザヒアリング・tech-stack.md より*

| ファイル | 区分 | 役割 | 対応要件 |
|---------|------|------|---------|
| `Share2ObsidianApp.kt` | **新規** | Hilt @HiltAndroidApp Application クラス | REQ-403 |
| `di/DatabaseModule.kt` | **新規** | Hilt Module（AppDatabase, DAO, Repository） | REQ-403, REQ-061 |
| `data/db/AppDatabase.kt` | **新規** | Room データベース定義 | REQ-061 |
| `data/db/TemplateEntity.kt` | **新規** | Template の Room エンティティ | REQ-061, REQ-015 |
| `data/db/TemplateFieldEntity.kt` | **新規** | TemplateField の Room エンティティ | REQ-061, REQ-033 |
| `data/db/TemplateDao.kt` | **新規** | Template + TemplateField の DAO | REQ-061 |
| `data/repository/TemplateRepository.kt` | **新規** | Repository インターフェース | REQ-061, NFR-201 |
| `data/repository/TemplateRepositoryImpl.kt` | **新規** | Repository 実装 | REQ-061, NFR-201 |
| `domain/model/Template.kt` | **新規** | テンプレートドメインモデル | REQ-015 |
| `domain/model/TemplateField.kt` | **新規** | フィールドドメインモデル | REQ-033 |
| `domain/model/FieldValueSource.kt` | **新規** | 値ソース種別 Enum | REQ-033, REQ-041〜044 |
| `domain/model/FieldValueType.kt` | **新規** | 値の型 Enum (STRING/LIST) | REQ-033 |
| `domain/model/HtmlMetaKey.kt` | **新規** | HTML メタデータキー Enum | REQ-042, REQ-071 |
| `ui/template/TemplateListScreen.kt` | **新規** | テンプレート一覧画面 | REQ-001, REQ-011 |
| `ui/template/TemplateEditScreen.kt` | **新規** | テンプレート編集画面 | REQ-012〜014, REQ-031〜033 |
| `ui/template/TemplateListViewModel.kt` | **新規** | テンプレート一覧 ViewModel | REQ-011 |
| `ui/template/TemplateEditViewModel.kt` | **新規** | テンプレート編集 ViewModel | REQ-012〜014, REQ-021〜022 |

### 変更が必要な既存コンポーネント 🔵

**信頼性**: 🔵 *要件定義・既存実装より*

| ファイル | 変更内容 | 対応要件 |
|---------|---------|---------|
| `AndroidManifest.xml` | `android:name=".Share2ObsidianApp"` 追加 | REQ-403 |
| `app/build.gradle.kts` | Hilt + KSP + Room 依存追加 | REQ-061, REQ-403 |
| `gradle/libs.versions.toml` | Room / Hilt / KSP バージョン追加 | REQ-061, REQ-403 |
| `MainActivity.kt` | `@AndroidEntryPoint` 追加、TemplateListScreen/EditScreen へのナビゲーション状態追加、テンプレート適用ロジック | REQ-001, REQ-051, REQ-052 |
| `ui/SettingsScreen.kt` | `onNavigateToTemplates` パラメータ追加、メニュー項目追加 | REQ-001 |
| `ui/EditScreen.kt` | カスタムフィールドセクション追加 | REQ-044, REQ-052 |
| `ui/EditFormState.kt` | `customFields: List<CustomFieldState>` フィールド追加 | REQ-052 |
| `ui/EditScreenViewModel.kt` | カスタムフィールド初期化・更新ロジック追加 | REQ-052 |
| `ui/SendParams.kt` | `customFields` フィールド追加 | REQ-052 |
| `format/NoteComposer.kt` | `buildFrontmatter` にカスタムフィールド引数追加、上書きロジック実装 | REQ-404, EDGE-005 |
| `content/ProcessedContent.kt` | `metadata: Map<HtmlMetaKey, String>` / `sourceUrl: String?` フィールド追加 | REQ-072 |
| `content/UrlContentProcessor.kt` | メタデータ取得・設定ロジック追加 | REQ-071 |
| `content/HtmlContentProcessor.kt` | Jsoup でメタデータ抽出・設定 | REQ-071 |
| `util/WebViewExtractor.kt` | JavaScript 拡張（メタデータ JSON 取得） | REQ-071 |
| `util/WebViewExtractionResult.kt` | メタデータフィールド追加 | REQ-071 |

---

## ディレクトリ構造（変更後） 🔵

**信頼性**: 🔵 *既存プロジェクト構造・tech-stack.md より*

```
app/src/main/java/com/den4dr/share2Obsidian/
├── Share2ObsidianApp.kt                   # 新規: @HiltAndroidApp
├── MainActivity.kt                        # 変更
├── AppConfig.kt                           # 変更なし
│
├── content/
│   ├── ShareContent.kt                    # 変更なし
│   ├── ProcessedContent.kt                # 変更: metadata, sourceUrl 追加
│   ├── ContentTypeDetector.kt             # 変更なし
│   ├── ContentProcessor.kt                # 変更なし
│   ├── TextContentProcessor.kt            # 変更なし
│   ├── UrlContentProcessor.kt             # 変更: メタデータ設定
│   ├── HtmlContentProcessor.kt            # 変更: Jsoup メタデータ抽出
│   └── FileContentProcessor.kt            # 変更なし
│
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt                 # 新規
│   │   ├── TemplateEntity.kt              # 新規
│   │   ├── TemplateFieldEntity.kt         # 新規
│   │   ├── TemplateWithFields.kt          # 新規: @Relation データクラス
│   │   └── TemplateDao.kt                 # 新規
│   └── repository/
│       ├── TemplateRepository.kt          # 新規: インターフェース
│       └── TemplateRepositoryImpl.kt      # 新規: 実装
│
├── di/
│   └── DatabaseModule.kt                  # 新規: Hilt Module
│
├── domain/
│   └── model/
│       ├── Template.kt                    # 新規: ドメインモデル
│       ├── TemplateField.kt               # 新規: ドメインモデル
│       ├── FieldValueSource.kt            # 新規: enum
│       ├── FieldValueType.kt              # 新規: enum
│       └── HtmlMetaKey.kt                 # 新規: enum
│
├── format/
│   ├── FrontmatterBuilder.kt              # 変更なし（NoteComposer を利用）
│   ├── NoteComposer.kt                    # 変更: buildFrontmatter 拡張
│   ├── NoteConfig.kt                      # 変更なし（テンプレート適用は Repository 側）
│   └── ObsidianUriBuilder.kt              # 変更なし
│
├── ui/
│   ├── theme/                             # 変更なし
│   ├── EditScreen.kt                      # 変更: カスタムフィールドセクション
│   ├── EditFormState.kt                   # 変更: customFields 追加
│   ├── EditScreenViewModel.kt             # 変更: カスタムフィールド対応
│   ├── SendParams.kt                      # 変更: customFields 追加
│   ├── LoadingScreen.kt                   # 変更なし
│   ├── SettingsScreen.kt                  # 変更: onNavigateToTemplates 追加
│   └── template/
│       ├── TemplateListScreen.kt          # 新規
│       ├── TemplateEditScreen.kt          # 新規
│       ├── TemplateListViewModel.kt       # 新規
│       └── TemplateEditViewModel.kt       # 新規
│
└── util/
    ├── WebViewExtractor.kt                # 変更: JS 拡張
    ├── WebViewExtractionResult.kt         # 変更: メタデータフィールド追加
    └── HtmlToMarkdownConverter.kt         # 変更なし
```

---

## ナビゲーション設計 🔵

**信頼性**: 🔵 *ユーザヒアリング「既存パターンと同じ」・settings-screen architecture.md より*

### 起動経路1: アイコンタップ（直接起動）

```kotlin
// MainActivity.onCreate 内（shareContent == null の場合）
setContent {
    var showTemplateList by rememberSaveable { mutableStateOf(false) }
    var editingTemplateId by rememberSaveable { mutableStateOf<Long?>(null) }

    when {
        editingTemplateId != null ->
            TemplateEditScreen(
                templateId = editingTemplateId,
                onNavigateBack = { editingTemplateId = null }
            )
        showTemplateList ->
            TemplateListScreen(
                onNavigateBack = { showTemplateList = false },
                onNavigateToEdit = { id -> editingTemplateId = id }
            )
        else ->
            SettingsScreen(
                onNavigateBack = { finish() },
                onNavigateToTemplates = { showTemplateList = true }
            )
    }
}
```

### 起動経路2: 共有フロー

```kotlin
// 処理完了後の setContent 内
var showSettings by rememberSaveable { mutableStateOf(false) }
var showTemplateList by rememberSaveable { mutableStateOf(false) }
var editingTemplateId by rememberSaveable { mutableStateOf<Long?>(null) }

when {
    editingTemplateId != null ->
        TemplateEditScreen(
            templateId = editingTemplateId,
            onNavigateBack = { editingTemplateId = null }
        )
    showTemplateList ->
        TemplateListScreen(
            onNavigateBack = { showTemplateList = false },
            onNavigateToEdit = { id -> editingTemplateId = id }
        )
    showSettings ->
        SettingsScreen(
            onNavigateBack = { showSettings = false },
            onNavigateToTemplates = { showTemplateList = true }
        )
    else ->
        EditScreen(
            viewModel = viewModel,
            config = config,
            onSend = { ... },
            onCancel = { finish() },
            onNavigateToSettings = { showSettings = true }
        )
}
```

---

## Hilt セットアップ 🔵

**信頼性**: 🔵 *ユーザヒアリング「Hilt を導入する」・Hilt 公式ドキュメントより*

### build.gradle.kts（プロジェクトルート）への追加

```kotlin
plugins {
    id("com.google.dagger.hilt.android") version "2.56" apply false
    id("com.google.devtools.ksp") version "2.2.10-1.0.25" apply false
}
```

### app/build.gradle.kts への追加

```kotlin
plugins {
    // 既存プラグインに追加
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

dependencies {
    // Hilt
    implementation("com.google.dagger:hilt-android:2.56")
    ksp("com.google.dagger:hilt-compiler:2.56")
    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    // Hilt ViewModel サポート
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
}
```

### AndroidManifest.xml への追加

```xml
<application
    android:name=".Share2ObsidianApp"
    ...>
```

---

## WebView メタデータ抽出の設計 🔵

**信頼性**: 🔵 *REQ-071, REQ-042・WebViewExtractor 既存実装より*

現在の `document.body.innerText` のみの JavaScript を、以下のように拡張する:

```javascript
(function() {
    function getMeta(selectors) {
        for (var s of selectors) {
            var el = document.querySelector(s);
            if (el) {
                var v = el.getAttribute('content') || el.getAttribute('value') || el.innerText;
                if (v && v.trim()) return v.trim();
            }
        }
        return '';
    }
    var result = {
        body:          document.body ? document.body.innerText : '',
        ogTitle:       getMeta(["meta[property='og:title']", "meta[name='twitter:title']", "title"]),
        ogDescription: getMeta(["meta[property='og:description']", "meta[name='description']"]),
        publishedTime: getMeta(["meta[property='article:published_time']", "meta[itemprop='datePublished']"]),
        modifiedTime:  getMeta(["meta[property='article:modified_time']", "meta[itemprop='dateModified']"]),
        author:        getMeta(["meta[name='author']", "meta[property='article:author']", "meta[property='og:site_name']"])
    };
    AndroidBridge.onExtracted(JSON.stringify(result));
})();
```

`WebViewExtractionResult` に `ogTitle`, `ogDescription`, `publishedTime`, `modifiedTime`, `author` を追加し、`UrlContentProcessor` でこれらを `ProcessedContent.metadata` に格納する。

---

## Jsoup による HTML メタデータ抽出 🔵

**信頼性**: 🔵 *REQ-071・Jsoup 既存導入済み・HtmlContentProcessor 既存実装より*

`HtmlContentProcessor` で Jsoup `doc.head()` を参照してメタデータを抽出:

```kotlin
private fun extractMetadata(doc: org.jsoup.nodes.Document, sourceUrl: String?): Map<HtmlMetaKey, String> {
    fun meta(vararg selectors: String): String {
        for (sel in selectors) {
            val v = doc.select(sel).attr("content").trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }
    return mapOf(
        HtmlMetaKey.OG_TITLE to meta("meta[property=og:title]").ifEmpty { doc.title() },
        HtmlMetaKey.OG_DESCRIPTION to meta("meta[property=og:description]", "meta[name=description]"),
        HtmlMetaKey.URL to (sourceUrl ?: ""),
        HtmlMetaKey.PUBLISHED_DATE to meta("meta[property=article:published_time]", "meta[itemprop=datePublished]"),
        HtmlMetaKey.MODIFIED_DATE to meta("meta[property=article:modified_time]", "meta[itemprop=dateModified]"),
        HtmlMetaKey.AUTHOR to meta("meta[name=author]", "meta[property=article:author]", "meta[property=og:site_name]")
    )
}
```

---

## NoteComposer 拡張設計 🔵

**信頼性**: 🔵 *REQ-404, EDGE-005「カスタムが上書き」・ユーザヒアリングより*

```kotlin
// buildFrontmatter に customFields を追加
fun buildFrontmatter(
    body: String,
    tags: List<String>,
    customFields: List<CustomFieldState> = emptyList()
): String {
    val sb = StringBuilder("---\n")
    val customKeySet = customFields.map { it.key }.toSet()

    // 1. カスタムフィールドを先に出力（キー重複があれば標準フィールドを上書き）
    for (field in customFields) {
        val valueStr = if (field.valueType == FieldValueType.LIST) {
            "[${field.value}]"
        } else {
            field.value
        }
        sb.append("${field.key}: $valueStr\n")
    }

    // 2. 標準の tags がカスタムフィールドで上書きされていない場合のみ出力
    if ("tags" !in customKeySet) {
        val tagsString = tags.joinToString(", ")
        sb.append("tags: [$tagsString]\n")
    }

    sb.append("---\n\n$body")
    return sb.toString()
}
```

---

## テンプレート適用フロー 🔵

**信頼性**: 🔵 *REQ-051, REQ-052・ユーザヒアリングより*

`MainActivity` でのテンプレート適用:
1. `TemplateRepository.getDefaultTemplate()` を suspending 呼び出し
2. テンプレートが存在する場合: `NoteConfig` の vault/folder をテンプレートの値で上書き
3. テンプレートの各フィールドについて:
   - `FIXED`: `field.defaultValue` をそのまま使用
   - `HTML_META`: `ProcessedContent.metadata[field.metaKey] ?: ""`
   - `URL`: `ProcessedContent.sourceUrl ?: ""`
   - `EMPTY`: `""`（空文字）
4. `viewModel.initialize(processed, config, customFieldStates)` で初期化

---

## 非機能要件の実現方法

### パフォーマンス（NFR-101） 🔵

**信頼性**: 🔵 *NFR-101・Room + Coroutines 標準設計より*

- Room の DAO は `suspend fun` または `Flow<>` で定義し、IO Dispatcher で実行
- `TemplateListViewModel` は `viewModelScope` + `Dispatchers.IO` で DB 操作

### セキュリティ 🔵

**信頼性**: 🔵 *既存実装（Intent バリデーション）・tech-stack.md より*

- テンプレートデータはアプリの内部ストレージ（Room DB）に保存。外部からアクセス不可
- カスタムフィールドの値は Obsidian URI にエンコードして送信（既存の `appendQueryParameter` が URL エンコードを担保）

### 互換性（NFR-301） 🔵

**信頼性**: 🔵 *CLAUDE.md minSdk 33 より*

- Room 2.7.1 / Hilt 2.56 はいずれも minSdk 21 以上に対応

---

## 技術的制約

- **単一 Activity 維持**: NavController / Fragment は不使用 🔵 *REQ-002・ユーザヒアリングより*
- **Compose Material3**: すべての UI コンポーネントは Material3 🔵 *REQ-402より*
- **UI 文字列**: strings.xml での管理 🔵 *REQ-401より*
- **KSP バージョン整合**: KSP 2.2.10-1.0.25 は Kotlin 2.2.10 に対応 🔵 *KSP 互換性テーブルより*
- **AGP 9.2.1 での Room**: Room 2.7.1 は AGP 9.x に対応済み 🔵 *Room リリースノートより*

---

## 関連文書

- **データフロー**: [dataflow.md](dataflow.md)
- **インターフェース定義**: [interfaces.kt](interfaces.kt)
- **DB スキーマ**: [database-schema.kt](database-schema.kt)
- **要件定義**: [requirements.md](../../spec/edit-template-management/requirements.md)

## 信頼性レベルサマリー

- 🔵 青信号: 21件 (91%)
- 🟡 黄信号: 2件 (9%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: 高品質

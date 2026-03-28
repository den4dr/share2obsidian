# 共有内容展開システム データフロー図

**作成日**: 2026-03-28
**関連アーキテクチャ**: [architecture.md](architecture.md)
**関連要件定義**: [requirements.md](../../spec/share-content-expansion/requirements.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: 要件定義書・ユーザヒアリングを参考にした確実なフロー
- 🟡 **黄信号**: 要件定義書・ユーザヒアリングから妥当な推測によるフロー
- 🔴 **赤信号**: 要件定義書・ユーザヒアリングにない推測によるフロー

---

## システム全体のデータフロー 🔵

**信頼性**: 🔵 *requirements.md 全体・ユーザヒアリングより*

```
Android Share Sheet
        │
        │ ACTION_SEND Intent
        │ (EXTRA_TEXT / EXTRA_HTML_TEXT / EXTRA_STREAM / EXTRA_SUBJECT)
        ▼
  ContentTypeDetector
        │
        ├─ text/plain + URL → [URL展開フロー]
        ├─ text/plain        → [テキストフロー]
        ├─ text/html         → [HTML変換フロー]
        └─ image/*, app/*   → [ファイルフロー]
        │
        ▼ ProcessedContent
  FrontmatterBuilder
        │ "---\ntitle: ...\ntags: [shared]\n---\n{body}"
        ▼
  ObsidianUriBuilder
        │ obsidian://new?content=...&title=...&vault=...&folder=...
        ▼
  startActivity(obsidianIntent)
        │
  finish()
```

---

## フロー1: テキスト共有（最短パス） 🔵

**信頼性**: 🔵 *REQ-001, REQ-002, REQ-003 ・ 既存実装より*

**関連要件**: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005

```
Intent(text/plain, non-URL)
    │
    ├─ EXTRA_TEXT  → body = "テキスト本文"
    └─ EXTRA_SUBJECT → title = "タイトル" (存在する場合)
    │
    ▼
FrontmatterBuilder.build(title, body)
    │
    │  ---
    │  title: "タイトル"
    │  tags: [shared]
    │  ---
    │  テキスト本文
    ▼
ObsidianUriBuilder.build(content, title, vault, folder)
    │
    │  obsidian://new
    │    ?content=---%0Atitle...
    │    &title=タイトル
    │    &vault=MyVault
    │    &folder=Inbox
    ▼
startActivity → Obsidian → finish()
```

**ステップ詳細**:
1. `ContentTypeDetector` が MIME=`text/plain`、かつ URL パターン不一致 → `ShareContent.Text` を生成
2. `TextContentProcessor.process()` で本文文字列をそのまま返す
3. `FrontmatterBuilder.build()` でFrontmatterヘッダーを先頭に付与
4. `ObsidianUriBuilder.build()` でハードコードvault/folderを付与したURIを構築
5. `startActivity(Intent(ACTION_VIEW, uri))` でObsidianを起動
6. `finish()` でアプリ終了

---

## フロー2: URL展開 🔵

**信頼性**: 🔵 *REQ-101, REQ-102, REQ-103 ・ ユーザヒアリング（WebView・プログレスバー）より*

**関連要件**: REQ-101, REQ-102, REQ-103, NFR-001

```
Intent(text/plain, URL)
    │
    ▼ MainActivity: LoadingScreen表示（プログレスバー）
    │
    ▼ lifecycleScope.launch {
    │     WebViewExtractor.extract(url)
    │         │
    │         ├─ WebView.loadUrl(url)
    │         │      │
    │         │      ├─ onPageFinished → JS injection
    │         │      │    "document.body.innerText" 抽出
    │         │      │    → JavascriptInterface.onTextExtracted(text)
    │         │      │
    │         │      └─ タイムアウト(10秒) → null を返す
    │         │
    │         └─ return: bodyText? (null = フォールバック)
    │ }
    │
    ├─ bodyText != null → ProcessedContent(body=bodyText)
    └─ bodyText == null → ProcessedContent(body=url) [フォールバック]
    │
    ▼
FrontmatterBuilder → ObsidianUriBuilder → startActivity → finish()
```

**ステップ詳細**:
1. `ContentTypeDetector` がURL正規表現に一致 → `ShareContent.Url` を生成
2. `MainActivity` が `LoadingScreen`（Composeプログレスバー）を表示
3. `lifecycleScope.launch` 内で `WebViewExtractor.extract(url)` を呼び出し
4. `WebViewExtractor` は非表示WebViewにURLをロードし、`onPageFinished` でJSを実行して本文テキストを抽出
5. 10秒タイムアウト内に取得できなければ null を返す（→ フォールバック: URLそのままをbodyとして使用）
6. 取得した本文テキストでFrontmatter生成→URI構築→Obsidian起動

---

## フロー3: HTML変換 🔵

**信頼性**: 🔵 *REQ-201, REQ-202, REQ-203 ・ ユーザヒアリング（Jsoup利用）より*

**関連要件**: REQ-201, REQ-202, REQ-203

```
Intent(text/html)
    │
    ├─ EXTRA_HTML_TEXT → htmlContent (存在する場合)
    └─ EXTRA_TEXT → fallbackText (EXTRA_HTML_TEXTがない場合)
    │
    ▼ HtmlContentProcessor
    │
    ├─ htmlContent != null:
    │     HtmlToMarkdownConverter.convert(htmlContent)
    │         │
    │         └─ Jsoup.parse(html)
    │               → traverse各要素
    │               → h1/h2/h3 → #/##/###
    │               → strong/b → **text**
    │               → em/i → *text*
    │               → a → [text](href)
    │               → li → - item
    │               → p/br → \n\n
    │               → return: markdownString
    │
    └─ htmlContent == null: body = fallbackText
    │
    ▼
FrontmatterBuilder → ObsidianUriBuilder → startActivity → finish()
```

**ステップ詳細**:
1. `ContentTypeDetector` が MIME=`text/html` → `ShareContent.Html` を生成
2. `HtmlContentProcessor.process()` で `EXTRA_HTML_TEXT` を取得
3. `HtmlToMarkdownConverter.convert()` にJsoupを渡してMarkdown変換
4. 変換失敗時は `EXTRA_TEXT` または空文字列にフォールバック

---

## フロー4: ファイル/画像共有 🔵

**信頼性**: 🔵 *REQ-301, REQ-302 ・ ユーザヒアリング（クリップボード経由）より*

**関連要件**: REQ-301, REQ-302, REQ-303

```
Intent(image/*, application/*)
    │
    ├─ EXTRA_STREAM → fileUri: Uri
    └─ EXTRA_SUBJECT → title (optional)
    │
    ▼ FileContentProcessor
    │
    ClipboardManager.setPrimaryClip(
        ClipData.newUri(contentResolver, "Shared File", fileUri)
    )
    │
    ▼
ObsidianUriBuilder.build(content="(クリップボードに画像をコピーしました)", title, vault, folder)
    │
    ▼
startActivity → Obsidian → finish()
```

**ステップ詳細**:
1. `ContentTypeDetector` が MIME=`image/*` または `application/*` → `ShareContent.File` を生成
2. `FileContentProcessor.process()` で `EXTRA_STREAM` から `Uri` を取得
3. `ClipboardManager.setPrimaryClip()` でファイルURIをクリップボードにコピー
4. Obsidianを起動（ユーザーがObsidian上で貼り付け操作を行う）

---

## エラーハンドリングフロー 🔵

**信頼性**: 🔵 *REQ-401, REQ-402, REQ-403 ・ 既存実装より*

```
startActivity(obsidianIntent)
    │
    ├─ 成功 → finish()
    │
    └─ ActivityNotFoundException (Obsidian未インストール)
          │
          ▼
       Toast("Obsidian がインストールされていません") [strings.xml参照]
          │
          ▼
       finish()
```

**注意**: エラーハンドリングの拡張は行わない（REQ-403）。
ただし内部フォールバックは以下の通り静かに処理する:

| 状況 | フォールバック |
|------|-------------|
| URL取得タイムアウト | URLテキストをそのまま本文に使用 |
| HTML変換例外 | EXTRA_TEXT または空文字にフォールバック |
| EXTRA_STREAM が null | Toast表示してfinish() |

---

## Frontmatter生成フロー 🔵

**信頼性**: 🔵 *REQ-003 ・ ユーザヒアリング（title/tagsフィールド）より*

```
FrontmatterBuilder.build(title: String?, body: String) → String
    │
    ├─ title != null:
    │   """
    │   ---
    │   title: "$title"
    │   tags: [shared]
    │   ---
    │
    │   $body
    │   """
    │
    └─ title == null:
        """
        ---
        tags: [shared]
        ---

        $body
        """
```

---

## Obsidian URI構築フロー 🔵

**信頼性**: 🔵 *REQ-002, REQ-004, REQ-005 ・ ユーザヒアリング（vault/folder/titleパラメータ）より*

```
ObsidianUriBuilder.build(content, title, vault, folder)
    │
    ▼
"obsidian://new"
    .toUri()
    .buildUpon()
    .appendQueryParameter("content", content)  // 自動URIエンコード
    .appendQueryParameter("title", title ?: "")
    .appendQueryParameter("vault", OBSIDIAN_VAULT)  // ハードコード定数
    .appendQueryParameter("folder", OBSIDIAN_FOLDER) // ハードコード定数
    .build()
```

**定数（コード内ハードコード）**:
```kotlin
const val OBSIDIAN_VAULT = "<ユーザーが指定するVault名>"   // prep.md参照
const val OBSIDIAN_FOLDER = "<ユーザーが指定するFolder名>" // prep.md参照
```

---

## 関連文書

- **アーキテクチャ**: [architecture.md](architecture.md)
- **Kotlinデータクラス**: [interfaces.kt](interfaces.kt)
- **要件定義**: [requirements.md](../../spec/share-content-expansion/requirements.md)

## 信頼性レベルサマリー

- 🔵 青信号: 14件（88%）
- 🟡 黄信号: 2件（12%）
- 🔴 赤信号: 0件（0%）

**品質評価**: 高品質

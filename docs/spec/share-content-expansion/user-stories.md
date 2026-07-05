# 共有内容展開システム ユーザストーリー

**作成日**: 2026-03-28
**関連要件定義**: [requirements.md](requirements.md)
**ヒアリング記録**: [interview-record.md](interview-record.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: ユーザヒアリング・既存実装を参考にした確実なストーリー
- 🟡 **黄信号**: ヒアリング・設計文書から妥当な推測によるストーリー
- 🔴 **赤信号**: ヒアリングにない推測によるストーリー

---

## エピック1: テキスト共有の拡張

### ストーリー 1.1: テキストをFrontmatter付きでObsidianに送る 🔵

**信頼性**: 🔵 *ユーザヒアリング（Frontmatter追加・title/tags）・既存実装より*

**私は** Androidユーザー **として**
**任意のアプリからテキストをShare2Obsidianで共有したい**
**そうすることで** title と tags が付いた整形されたObsidianノートとして保存できる

**関連要件**: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005

**詳細シナリオ**:
1. ブラウザや読書アプリなどでテキストを選択し「共有」をタップ
2. 共有シートから「Share2Obsidian」を選択
3. アプリがEXTRA_TEXTとEXTRA_SUBJECTを取得
4. Frontmatter（title, tags）を生成してノート本文に付与
5. obsidian://new?content=...&title=...&vault=...&folder=... でObsidianを起動
6. Obsidianが指定のvault・folderに新規ノートを作成して開く
7. Share2Obsidianはfinish()で終了

**前提条件**:
- Obsidianがインストール済み
- 共有するテキストが1文字以上存在する

**制約事項**:
- 設定画面なし。vault/folderはコード内固定値
- エラーハンドリングはObsidian未インストール時のToastのみ

**優先度**: Must Have

---

### ストーリー 1.2: 件名なしのテキストをFrontmatterのtitleなしで共有する 🟡

**信頼性**: 🟡 *EXTRA_SUBJECTが空の場合の動作として妥当な推測*

**私は** Androidユーザー **として**
**タイトル（件名）なしでテキストのみを共有したい**
**そうすることで** タイトル空のまま本文だけのノートを素早く作成できる

**関連要件**: REQ-001, REQ-003

**詳細シナリオ**:
1. アプリからテキストを共有（件名なし）
2. Frontmatterのtitleフィールドは空文字または省略
3. Obsidianに本文テキストのみ送信

**前提条件**:
- EXTRA_SUBJECTが空またはnull

**優先度**: Must Have

**備考**: titleフィールドが空の場合にFrontmatterを出力するか省略するかは実装時に判断

---

## エピック2: URL展開

### ストーリー 2.1: URLをWebViewで展開してノートに保存する 🔵

**信頼性**: 🔵 *ユーザヒアリング（WebView内部処理・本文取得）より*

**私は** Androidユーザー **として**
**ブラウザなどからURLを共有した際にページの本文テキストを取得したい**
**そうすることで** URLの内容（記事本文など）をそのままObsidianノートとして保存できる

**関連要件**: REQ-101, REQ-102, REQ-003, REQ-004, REQ-005

**詳細シナリオ**:
1. ブラウザのURLを「共有」でShare2Obsidianに送る
2. システムがEXTRA_TEXTからURLパターンを検出
3. Android WebViewでURLを読み込む
4. JavascriptInterface経由でページ本文テキストを抽出
5. Frontmatter + 抽出本文でノート内容を構成
6. Obsidianを起動してノートを作成

**前提条件**:
- インターネット接続が有効
- Obsidianがインストール済み

**制約事項**:
- WebViewのタイムアウト内に本文取得できない場合はURLテキストをそのまま使用（REQ-103）

**優先度**: Must Have

---

### ストーリー 2.2: URL展開失敗時にURLテキストのみを送信する 🟡

**信頼性**: 🟡 *REQ-103のフォールバック動作として妥当な推測*

**私は** Androidユーザー **として**
**URLが読み込めない場合でも共有処理を中断されずに済みたい**
**そうすることで** ネットワーク障害や読み込みエラーがあってもObsidianにノートが作成される

**関連要件**: REQ-103, EDGE-001

**詳細シナリオ**:
1. WebViewのURL読み込みがタイムアウトまたは失敗
2. フォールバックとしてEXTRA_TEXTのURLテキストをそのままコンテンツとして使用
3. Obsidianに送信して完了

**優先度**: Should Have

---

## エピック3: HTMLコンテンツ変換

### ストーリー 3.1: HTMLコンテンツをMarkdownに変換してノートにする 🔵

**信頼性**: 🔵 *ユーザヒアリング（HTMLコンテンツのマークダウン変換・ライブラリ利用）より*

**私は** Androidユーザー **として**
**HTMLコンテンツを共有した際にMarkdown形式に変換されたノートが欲しい**
**そうすることで** Webページの書式（見出し・リスト・リンク等）を保ったままObsidianに保存できる

**関連要件**: REQ-201, REQ-202, REQ-203, REQ-003

**詳細シナリオ**:
1. HTML共有対応アプリから `text/html` MIMEタイプで共有
2. システムがEXTRA_HTML_TEXTを取得
3. markwon等のライブラリでHTML→Markdown変換
4. Frontmatter + 変換済みMarkdownでノート構成
5. Obsidianに送信

**前提条件**:
- 共有元アプリがEXTRA_HTML_TEXTを提供している
- Androidインテントフィルターがtext/htmlに対応

**制約事項**:
- EXTRA_HTML_TEXTがない場合はEXTRA_TEXTをフォールバックとして使用

**優先度**: Must Have

---

## エピック4: ファイル/画像共有

### ストーリー 4.1: 画像をクリップボード経由でObsidianに渡す 🔵

**信頼性**: 🔵 *ユーザヒアリング（クリップボード経由）より*

**私は** Androidユーザー **として**
**画像や添付ファイルを共有してObsidianで参照できるようにしたい**
**そうすることで** 写真やファイルのURIをObsidianノートと紐付けられる

**関連要件**: REQ-301, REQ-302, REQ-303

**詳細シナリオ**:
1. ギャラリーや他のアプリから画像を「共有」
2. システムがEXTRA_STREAMからファイルURIを取得
3. ClipboardManagerでURI（またはファイルコンテンツ）をクリップボードにコピー
4. Obsidianを起動（ユーザーが貼り付け操作を行うことを期待）

**前提条件**:
- 共有元アプリがEXTRA_STREAMを提供
- AndroidManifestにimage/*, application/* のインテントフィルターが追加済み

**制約事項**:
- Obsidian側で自動貼り付けは行われない（ユーザー操作が必要）
- ファイル移動・コピーは行わない

**優先度**: Must Have

---

## ストーリーマップ

```
エピック1: テキスト共有の拡張
├── ストーリー 1.1: Frontmatter付きテキスト送信 (🔵 Must Have)
└── ストーリー 1.2: タイトルなしテキスト送信 (🟡 Must Have)

エピック2: URL展開
├── ストーリー 2.1: WebViewで本文取得してノート作成 (🔵 Must Have)
└── ストーリー 2.2: URL展開失敗時のフォールバック (🟡 Should Have)

エピック3: HTMLコンテンツ変換
└── ストーリー 3.1: HTML→Markdown変換 (🔵 Must Have)

エピック4: ファイル/画像共有
└── ストーリー 4.1: クリップボード経由で画像共有 (🔵 Must Have)
```

## 信頼性レベルサマリー

- 🔵 青信号: 5件（71%）
- 🟡 黄信号: 2件（29%）
- 🔴 赤信号: 0件（0%）

**品質評価**: 高品質

# 編集テンプレートの管理機能 設計ヒアリング記録

**作成日**: 2026-05-31
**ヒアリング実施**: step4 既存情報ベースの差分ヒアリング

## ヒアリング目的

要件定義フェーズで確定した仕様をもとに、技術設計に必要な判断（EDGE-005 の処理方針・DI 導入方式・ナビゲーション方式）を明確化するためのヒアリングを実施。

---

## 質問と回答

### Q1: EDGE-005 — カスタムフィールドキーが既存 Frontmatter キー（title, tags）と重複した場合の動作

**質問日時**: 2026-05-31
**カテゴリ**: 未定義部分詳細化
**背景**: 要件定義時に 🔴 赤信号として残された唯一の未定義項目。カスタムフィールドが `tags` や `title` と同じキーを使った場合、FrontmatterBuilder の出力が曖昧になるため確認が必要だった。

**回答**: カスタムが上書き

**信頼性への影響**:
- EDGE-005 が 🔴 → 🔵 に向上
- `NoteComposer.buildFrontmatter` の設計が確定:
  - カスタムフィールドを先に出力
  - `customKeys` に "tags" が含まれる場合は標準の `tags` 行を出力しない（上書き動作）
  - `customKeys` に "title" が含まれる場合は Frontmatter に `title:` が追加されるが、URI の `name=` パラメータはそのまま（ファイル名とは独立）

---

### Q2: Hilt DI の導入方針（現時点では DI フレームワーク未使用）

**質問日時**: 2026-05-31
**カテゴリ**: 技術選択
**背景**: `app/build.gradle.kts` を確認したところ Hilt も KSP も未導入だった。Room DB 導入には KSP が必要であり、合わせて Hilt を導入するコストを評価するため確認した。

**回答**: Hilt を導入する

**信頼性への影響**:
- REQ-403 が 🔵 として確定
- 以下の追加作業が確定:
  - `build.gradle.kts` (プロジェクトルート + app) への KSP + Hilt プラグイン追加
  - `libs.versions.toml` への Room + Hilt + KSP バージョン追加
  - `Share2ObsidianApp.kt` (@HiltAndroidApp) の新規作成
  - `AndroidManifest.xml` への `android:name=".Share2ObsidianApp"` 追加
  - `MainActivity.kt` への `@AndroidEntryPoint` 追加
  - `di/DatabaseModule.kt` の新規作成

---

### Q3: TemplateListScreen → TemplateEditScreen のナビゲーション方式

**質問日時**: 2026-05-31
**カテゴリ**: アーキテクチャ
**背景**: 3 段階のナビゲーション（SettingsScreen → TemplateListScreen → TemplateEditScreen）を実現する方法として、既存の `rememberSaveable { mutableStateOf }` ネストと Jetpack Navigation の 2 択があった。Jetpack Navigation の導入はさらなる依存追加になるため確認した。

**回答**: 既存パターンと同じ（推奨）

**信頼性への影響**:
- ナビゲーション設計が確定
- `rememberSaveable { mutableStateOf<Long?>(null) }` で `editingTemplateId` を管理
- 3 段階の状態を `when { editingTemplateId != null → ... showTemplateList → ... else → ... }` でネスト
- Jetpack Navigation は不使用（`navigation-compose` の依存追加不要）

---

## 設計調査での主な発見事項

### Jsoup 導入済み

`libs.versions.toml` と `app/build.gradle.kts` に `jsoup = "1.18.3"` が既に存在。
→ HTML 共有時のメタデータ抽出に Jsoup の `doc.head()` を利用可能（追加依存なし）

### WebViewExtractor の現状

現在は `document.body.innerText` のみ取得。
→ JavaScript を JSON 形式に拡張することでメタデータを一括取得できる（android:interface の `onExtracted(json)` に変更）

### NoteComposer の構造

`buildFrontmatter(body, tags)` と `buildUri(content, title, config)` の 2 メソッド構成。
→ `buildFrontmatter` に `customFields: List<CustomFieldState>` を追加する方針で設計を確定

### Room + KSP のバージョン整合

Kotlin 2.2.10 に対応する KSP バージョンは `2.2.10-1.0.25`。
AGP 9.2.1 は Room 2.7.1 に対応済み。

---

## ヒアリング結果サマリー

### 確認できた事項

1. カスタムフィールドが標準フィールド（tags）を上書きする
2. Hilt + KSP を新規導入する
3. ナビゲーションは既存の Compose state ベースで対応する

### 設計方針の決定事項

| 項目 | 決定内容 |
|------|---------|
| EDGE-005 動作 | カスタムフィールドが標準キーを上書き |
| DI フレームワーク | Hilt（KSP 使用） |
| ナビゲーション | rememberSaveable ネスト状態管理 |
| HTML メタデータ抽出 | UrlContentProcessor: WebView JS 拡張 / HtmlContentProcessor: Jsoup head() |
| FrontmatterBuilder | NoteComposer.buildFrontmatter に customFields 追加で対応 |

### 残課題

- フィールドキー名のバリデーション仕様（最大文字数、使用不可文字）が詳細未定
  → 実装時に YAML キーとして有効な文字列のみ許容する簡易バリデーションで対応推奨

### 信頼性レベル分布

**ヒアリング前**:
- 🔵 青信号: 15件
- 🟡 黄信号: 5件
- 🔴 赤信号: 1件（EDGE-005）

**ヒアリング後**:
- 🔵 青信号: 21件 (+6)
- 🟡 黄信号: 2件 (-3)
- 🔴 赤信号: 0件 (-1)

---

## 関連文書

- **アーキテクチャ設計**: [architecture.md](architecture.md)
- **データフロー**: [dataflow.md](dataflow.md)
- **インターフェース定義**: [interfaces.kt](interfaces.kt)
- **DB スキーマ**: [database-schema.kt](database-schema.kt)
- **要件定義**: [requirements.md](../../spec/edit-template-management/requirements.md)

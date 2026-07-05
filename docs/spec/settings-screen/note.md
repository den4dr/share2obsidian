# 設定画面（Settings Screen） コンテキストノート

**作成日**: 2026-05-31

## 技術スタック

- **言語**: Kotlin 2.2.10
- **Android Gradle Plugin**: 9.2.1
- **minSdk**: 33 (Android 13)
- **targetSdk/compileSdk**: 36
- **Java互換性**: 11
- **UI**: Jetpack Compose BOM 2024.09.00 (Material3)
- **非同期**: Kotlin Coroutines (lifecycleScope)
- **ViewModel**: AndroidX ViewModel (viewModels() delegate)
- **依存関係管理**: gradle/libs.versions.toml (Version Catalog)
- **アーキテクチャパターン**: 単一アクティビティ + Compose 画面ナビゲーション

## ビルドコマンド

```bash
mise exec -- ./gradlew assembleDebug          # デバッグビルド
mise exec -- ./gradlew test                   # ユニットテスト
mise exec -- ./gradlew connectedAndroidTest   # インストゥルメントテスト
mise exec -- ./gradlew lint                   # リントチェック
```

**重要**: Java は `.mise.toml` で管理されているため、Gradle コマンドは必ず `mise exec --` 経由で実行すること。

## 既存実装（関連コンポーネント）

| クラス | パッケージ | 役割 |
|--------|----------|------|
| `MainActivity` | root | エントリポイント、Compose UI ホスト（**変更対象**） |
| `AppConfig` | root | 設定定数 (VAULT/FOLDER/TAGS/TIMEOUT) |
| `EditScreen` | ui | コンテンツ編集フォーム Composable（**ツールバー追加対象**） |
| `EditFormState` | ui | 編集フォーム状態データクラス |
| `EditScreenViewModel` | ui | 編集フォーム状態管理 ViewModel |
| `SendParams` | ui | 送信パラメータデータクラス |
| `LoadingScreen` | ui | URL処理中ローディング画面 Composable |
| `NoteConfig` | format | vault/folder/defaultTags設定 |
| `NoteComposer` | format | Frontmatter + URI 生成 |
| `Theme.kt` | ui/theme | Material3 テーマ定義 |
| `Color.kt` | ui/theme | カラーパレット定義 |
| `Type.kt` | ui/theme | テキストスタイル定義 |

## 新規実装コンポーネント（このタスク範囲）

| クラス/ファイル | パッケージ | 役割 |
|--------------|----------|------|
| `SettingsScreen` | ui | 設定画面 Composable（プレースホルダー） |
| `SettingsScreenViewModel` | ui | 設定画面状態管理 ViewModel（オプション、将来機能用） |

## アーキテクチャ設計

### 画面遷移フロー

```
MainActivity
  ├─ [Intent なし] → AppLauncher → Settings アイコンタップ → SettingsScreen
  │
  └─ [Intent あり] → LoadingScreen（URL処理時）
     → EditScreen
       └─ ツールバー設定アイコンタップ → SettingsScreen
          └─ 戻るボタン / バックボタン → EditScreen に戻る
```

### UI層レイアウト設計

#### SettingsScreen（プレースホルダー）

- **ツールバー**: Material3 TopAppBar
  - タイトル: "設定" または localization string
  - 戻るボタン（NavigationIcon）: `onNavigateBack` コールバック実行
- **メインコンテンツ**: ScrollableColumn
  - 現在: プレースホルダーテキスト「設定項目はまだ実装されていません」
  - 将来: 各設定項目セクション（Vault選択、Folder選択、Tags プリセット等）
- **バックボタン対応**: `BackHandler { onNavigateBack() }` で動作

#### EditScreen（既存、変更対象）

- **ツールバー追加**: TopAppBar に設定アイコン（IconButton）を追加
  - 位置: TopAppBar.actions に配置（右寄せ）
  - アイコン: `Icons.Default.Settings` または類似
  - コールバック: `onNavigateToSettings` 実行

### ナビゲーション設計

**画面遷移方式**: Composable の条件分岐（状態ベース）

```kotlin
// MainActivity.kt 内で条件分岐
setContent {
    when {
        showSettingsScreen -> SettingsScreen(
            onNavigateBack = { showSettingsScreen = false }
        )
        else -> EditScreen(
            // 既存パラメータ
            onNavigateToSettings = { showSettingsScreen = true }
        )
    }
}
```

## 開発ルール

### リソース管理

- 日本語文字列は `res/values/strings.xml` に定義する
- 新規追加文字列:
  - `label_settings`: "設定"
  - `settings_placeholder`: "設定項目はまだ実装されていません"

### UI コンポーネント

- Compose Material3 コンポーネントのみ使用
  - `TopAppBar` / `Scaffold` / `Button` / `IconButton` など
- テーマは既存の `Theme.kt` を継承・使用
- レスポンシブ対応は必須（minSdk 33 向けの最新パターンを採用）

### アーキテクチャ

- **単一アクティビティ原則**: MainActivity のみ存在。SettingsScreen は Composable として実装（Fragment 化しない）
- **ViewModel ライフサイクル**: 既存の `viewModels()` デリゲートパターンを踏襲
- **BackHandler**: システムバックボタン対応は `BackHandler { }` で実装

### エラー処理

- 既存の `ActivityNotFoundException` パターンを踏襲
- 新規エラーは Toast でユーザー通知
- 日本語エラーメッセージは `strings.xml` で定義

### 画面回転対応（EDGE-101）

- EditScreen から SettingsScreen へ遷移時、状態フラグを ViewModel で管理する場合は StateFlow を使用
- または、状態を MainActivity の local variable で管理し、`savedInstanceState` は使わない（Compose での推奨パターン）

## 実装スコープ

### In Scope（実装対象）

1. **SettingsScreen Composable** の作成（プレースホルダー実装）
   - TopAppBar + 戻るボタン
   - プレースホルダー画面（テキストのみ）
   - BackHandler 実装

2. **EditScreen ツールバー拡張**
   - TopAppBar.actions に設定アイコンを追加
   - 設定アイコンタップ時の `onNavigateToSettings` コールバック

3. **MainActivity ナビゲーション ロジック**
   - AppLauncher 画面（Intent なし時）で設定アイコンタップを検知
   - EditScreen の設定アイコンタップを検知
   - SettingsScreen への遷移・復帰処理

4. **文字列リソース追加**
   - `strings.xml` に "設定" / "設定項目はまだ実装されていません" を追加

### Out of Scope（実装外）

- 実際の設定機能（Vault 選択、Folder 選択、Tags プリセット等）
- SharedPreferences / DataStore での設定永続化
- SettingsScreen ViewModel（将来実装用の拡張ポイントのみ）

## 既存設計・制約との整合性

### CLAUDE.md との整合

- ✅ 単一アクティビティ維持（MainActivity のみ）
- ✅ Compose UI（Material3）使用
- ✅ 日本語文字列は `strings.xml` 定義
- ✅ 既存コンポーネント（FrontmatterBuilder / ObsidianUriBuilder）は変更なし

### content-edit-preview からの継承パターン

| 設計項目 | content-edit-preview | settings-screen |
|--------|-------------------|-----------------|
| ViewModel ライフサイクル | `viewModels()` デリゲート | 同様（またはシンプルな場合は不要） |
| StateFlow 利用 | EditFormState 管理 | ナビゲーション状態（別案: local variable） |
| BackHandler | EditScreen で実装 | SettingsScreen で実装 |
| TopAppBar | 編集画面用（簡易） | 設定画面用（戻るボタン付き） |
| テーマ継承 | Theme.kt 使用 | 同様 |

## 参考ドキュメント

### 既存仕様書

- `docs/spec/content-edit-preview/note.md` — コンテンツ編集・プレビュー機能のコンテキスト
- `docs/spec/content-edit-preview/requirements.md` — 編集機能の要件定義
- `docs/spec/content-edit-preview/acceptance-criteria.md` — 受け入れ基準

### 既存設計書

- `docs/design/content-edit-preview/architecture.md` — 編集機能のアーキテクチャ設計
- `docs/design/content-edit-preview/dataflow.md` — データフロー図

### プロジェクト定義

- `CLAUDE.md` — プロジェクト概要・ビルドコマンド・アーキテクチャ原則

## 注意事項

### 重要な制約

1. **単一アクティビティ維持**: SettingsScreen は Fragment ではなく Composable として実装。MainActivity で条件分岐で画面切り替え
2. **戻り遷移**: EditScreen → SettingsScreen → EditScreen への戻り遷移では、EditScreen の状態（ユーザー入力）を保持すること
3. **AppLauncher**: Intent なし時に Settings アイコンを表示する別画面の実装検討が必要（Future scope の可能性あり）
4. **文字列ローカライズ**: 現在すべて日本語。将来的には en/ja 等の多言語対応時に `res/values-ja/` 等に分離

### テスト対応

- `app/src/test/java/com/den4dr/share2Obsidian/ui/` 配下にユニットテスト配置
- EditScreen のツールバー拡張テスト
- SettingsScreen のナビゲーション テスト（Robolectric + Compose）

### パフォーマンス

- プレースホルダー実装のため初期パフォーマンス影響なし
- 将来的に複雑な設定画面に拡張する場合は、大量リスト表示時の最適化を検討

## 開発進行チェックリスト

- [ ] SettingsScreen Composable 作成（プレースホルダー実装）
- [ ] EditScreen のツールバーに設定アイコンを追加
- [ ] MainActivity でナビゲーション状態管理を実装
- [ ] `strings.xml` に新規文字列を追加
- [ ] 基本動作テスト（IDE Emulator で実装）
  - [ ] Intent なし → Settings アイコンで SettingsScreen 表示
  - [ ] Intent あり → EditScreen → Settings アイコンで SettingsScreen 遷移
  - [ ] SettingsScreen 戻るボタンで EditScreen に復帰
  - [ ] BackHandler でシステムバックボタン対応確認
- [ ] リント・ビルドエラー確認
- [ ] Compose Preview で UI 確認

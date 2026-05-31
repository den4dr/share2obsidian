# 設定画面 アーキテクチャ設計

**作成日**: 2026-05-31
**関連要件定義**: [requirements.md](../../spec/settings-screen/requirements.md)
**ヒアリング記録**: [design-interview.md](design-interview.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: 要件定義書・ユーザヒアリングを参考にした確実な設計
- 🟡 **黄信号**: 要件定義書・ユーザヒアリングから妥当な推測による設計
- 🔴 **赤信号**: 要件定義書・ユーザヒアリングにない推測による設計

---

## システム概要 🔵

**信頼性**: 🔵 *requirements.md REQ-001〜REQ-403・ユーザヒアリングより*

content-edit-preview（TASK-0015〜TASK-0020 完了）の基盤の上に、設定画面を追加する。
アイコンタップで直接起動したとき、および EditScreen のツールバーから遷移できる `SettingsScreen` Composable を実装する。
今回はプレースホルダー実装のみで、実際の設定変更・永続化は行わない。

## アーキテクチャパターン 🔵

**信頼性**: 🔵 *REQ-401（シングルアクティビティ）・ユーザヒアリング・既存設計より*

- **パターン**: 既存の単一アクティビティ + Compose UI を維持し、画面遷移は `setContent` 内の状態ベース条件分岐で実現
- **選択理由**: Fragment 化・NavController 導入はオーバーエンジニアリングになるため、Compose の `rememberSaveable { mutableStateOf() }` による軽量ナビゲーションを採用

## コンポーネント構成

### UI 層（新規追加・変更） 🔵

**信頼性**: 🔵 *要件定義書・ユーザヒアリング・note.md より*

| クラス | 変更区分 | 役割 | 対応要件 |
|--------|---------|------|---------|
| `SettingsScreen` | **新規** | 設定画面 Composable（プレースホルダー） | REQ-001, REQ-003 |
| `EditScreen` | **変更** | `onNavigateToSettings: () -> Unit` 引数を追加、TopAppBar に設定アイコンを追加 | REQ-002, REQ-101 |
| `MainActivity` | **変更** | 起動経路判定・ナビゲーション状態管理を追加 | REQ-001, REQ-101, REQ-401 |

### 既存コンポーネント（変更なし） 🔵

**信頼性**: 🔵 *既存実装より*

| クラス | 役割 |
|--------|------|
| `EditScreenViewModel` | フォーム状態管理（変更なし） |
| `NoteComposer` | Frontmatter + URI 生成（変更なし） |
| `NoteConfig` | 設定値保持（変更なし） |
| `ContentTypeDetector` | Intent 判定（変更なし） |
| `LoadingScreen` | URL処理中ローディング（変更なし） |

## システム構成図 🔵

**信頼性**: 🔵 *要件定義・ユーザヒアリング・既存実装より*

```
Android System
    │ ACTION_MAIN（アイコンタップ）
    │ ACTION_SEND（共有）
    ▼
┌─────────────────────────────────────────────────────────┐
│                     MainActivity                         │
│                                                          │
│  onCreate:                                               │
│  ┌─────────────────────────────────────────────────┐   │
│  │ ContentTypeDetector.detect(intent)               │   │
│  └─────────────────────────────────────────────────┘   │
│         │ null（直接起動）  │ ShareContent（共有）       │
│         ▼                  ▼                             │
│  ┌─────────────┐  ┌──────────────────────────────┐     │
│  │ setContent  │  │  LoadingScreen（URL時）        │     │
│  │ {           │  │  lifecycleScope.launch {       │     │
│  │  Settings   │  │    process(shareContent)       │     │
│  │  Screen()   │  │    viewModel.initialize(...)   │     │
│  │ }           │  │    setContent {                │     │
│  │             │  │      var showSettings = false  │     │
│  └─────────────┘  │      if (showSettings)         │     │
│         ↓         │        SettingsScreen(...)     │     │
│  戻る→finish()     │      else                      │     │
│                   │        EditScreen(...)          │     │
│                   │    }                            │     │
│                   │  }                              │     │
│                   └──────────────────────────────┘     │
└─────────────────────────────────────────────────────────┘
```

## ナビゲーション設計 🔵

**信頼性**: 🔵 *REQ-101〜REQ-103・ユーザヒアリング・コード分析より*

### 起動経路1: アイコンタップ（Intent なし）

```kotlin
// MainActivity.onCreate 内
val shareContent = ContentTypeDetector.detect(intent)
if (shareContent == null) {
    // 直接起動 → SettingsScreen を即表示
    setContent {
        AppTheme {
            SettingsScreen(onNavigateBack = { finish() })
        }
    }
    return
}
```

### 起動経路2: 共有フロー（Intent あり）

```kotlin
// 処理完了後
setContent {
    AppTheme {
        // Compose の状態として showSettings を保持（画面回転対応）
        var showSettings by rememberSaveable { mutableStateOf(false) }

        if (showSettings) {
            SettingsScreen(onNavigateBack = { showSettings = false })
        } else {
            EditScreen(
                viewModel = viewModel,
                config = config,
                onSend = { /* 既存処理 */ },
                onCancel = { finish() },
                onNavigateToSettings = { showSettings = true },
            )
        }
    }
}
```

### SettingsScreen の UI 構造

```kotlin
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    BackHandler { onNavigateBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, ...)
                    }
                }
            )
        }
    ) { padding ->
        // プレースホルダーコンテンツ
        Box(Modifier.fillMaxSize().padding(padding)) {
            Text(stringResource(R.string.settings_placeholder), ...)
        }
    }
}
```

### EditScreen の変更点

```kotlin
@Composable
fun EditScreen(
    viewModel: EditScreenViewModel,
    config: NoteConfig,
    onSend: (SendParams) -> Unit,
    onCancel: () -> Unit,
    onNavigateToSettings: () -> Unit,  // ← 新規追加
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* なし or アプリ名 */ },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, ...)
                    }
                }
            )
        },
        // ...
    )
}
```

## ディレクトリ構造 🔵

**信頼性**: 🔵 *既存プロジェクト構造より*

```
app/src/main/java/com/den4dr/share2Obsidian/
├── MainActivity.kt                  # 変更: 起動経路判定・ナビゲーション追加
└── ui/
    ├── EditScreen.kt                # 変更: onNavigateToSettings 追加、TopAppBar に設定アイコン
    ├── SettingsScreen.kt            # 新規: プレースホルダー設定画面
    ├── EditScreenViewModel.kt       # 変更なし
    ├── EditFormState.kt             # 変更なし
    ├── SendParams.kt                # 変更なし
    └── LoadingScreen.kt             # 変更なし

app/src/main/res/values/
└── strings.xml                     # 変更: label_settings / settings_placeholder 追加
```

## 非機能要件の実現方法

### 画面回転対応（EDGE-101） 🔵

**信頼性**: 🔵 *EDGE-101・Android Compose ライフサイクル仕様より*

共有フローの `showSettings` 状態は `rememberSaveable { mutableStateOf(false) }` で保持する。
これにより Activity 再作成（画面回転）後も SettingsScreen の表示状態が復元される。

直接起動（Intent なし）の場合は常に SettingsScreen を表示するため、状態保持の考慮不要。

### バックボタン対応 🔵

**信頼性**: 🔵 *REQ-103・BackHandler 実装パターンより*

`SettingsScreen` 内で `BackHandler { onNavigateBack() }` を実装する。
`onNavigateBack` の実体は呼び出し元（MainActivity）から渡す：
- 直接起動: `finish()`
- 共有フロー: `showSettings = false`

## 技術的制約

- **Compose Material3 Icons**: `Icons.Default.Settings` / `Icons.AutoMirrored.Filled.ArrowBack` を使用 🟡 *Material3 Icons パターンより*
- **AppTheme**: 既存の `Theme.Share2Obsidian` テーマを継承 🔵 *既存実装より*
- **文字列リソース**: `label_settings` / `settings_placeholder` を strings.xml に追加 🔵 *REQ-402より*
- **単一アクティビティ**: SettingsScreen は Fragment ではなく Composable として実装 🔵 *REQ-401より*

## 関連文書

- **データフロー**: [dataflow.md](dataflow.md)
- **インターフェース定義**: [interfaces.kt](interfaces.kt)
- **要件定義**: [requirements.md](../../spec/settings-screen/requirements.md)

## 信頼性レベルサマリー

- 🔵 青信号: 14件 (87%)
- 🟡 黄信号: 2件 (13%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: 高品質

# 設定画面 データフロー図

**作成日**: 2026-05-31
**関連アーキテクチャ**: [architecture.md](architecture.md)
**関連要件定義**: [requirements.md](../../spec/settings-screen/requirements.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: 要件定義書・ユーザヒアリングを参考にした確実なフロー
- 🟡 **黄信号**: 要件定義書・ユーザヒアリングから妥当な推測によるフロー
- 🔴 **赤信号**: 要件定義書・ユーザヒアリングにない推測によるフロー

---

## フロー1: アイコンタップ起動 → SettingsScreen 🔵

**信頼性**: 🔵 *REQ-001・ユーザヒアリング「SettingsScreen を直接表示」より*

**関連要件**: REQ-001, REQ-102, REQ-103, EDGE-001

```
ユーザー                 Android OS            MainActivity          UI
   │                        │                      │                  │
   │ アイコンタップ           │                      │                  │
   │──────────────────────►│                      │                  │
   │                        │ ACTION_MAIN Intent   │                  │
   │                        │─────────────────────►│                  │
   │                        │                      │ detect(intent)   │
   │                        │                      │ → null           │
   │                        │                      │                  │
   │                        │                      │ setContent {     │
   │                        │                      │   SettingsScreen │
   │                        │                      │   (onNavigateBack│
   │                        │                      │    = { finish() })
   │                        │                      │ }                │
   │                        │                      │─────────────────►│
   │◄─────────────────────────────────────────────────────────────── │
   │     SettingsScreen 表示（プレースホルダー）                        │
   │                        │                      │                  │
   │ 戻るボタン / バックボタン │                      │                  │
   │──────────────────────────────────────────────────────────────►│
   │                        │                      │                  │
   │                        │                      │◄─ onNavigateBack │
   │                        │                      │   = finish()     │
   │                        │                      │ finish()         │
   │                        │◄─────────────────────│                  │
   │ アプリ終了              │                      │                  │
```

---

## フロー2: 共有フロー → EditScreen → SettingsScreen → 戻る 🔵

**信頼性**: 🔵 *REQ-101〜REQ-103・ユーザヒアリング・EDGE-002 より*

**関連要件**: REQ-002, REQ-101, REQ-102, REQ-103, EDGE-002

```
ユーザー              他アプリ         MainActivity         EditScreen        SettingsScreen
   │                    │                 │                    │                   │
   │ 共有操作            │                 │                    │                   │
   │──────────────────►│                 │                    │                   │
   │                    │ ACTION_SEND     │                    │                   │
   │                    │────────────────►│                    │                   │
   │                    │                 │ detect(intent)     │                   │
   │                    │                 │ → ShareContent     │                   │
   │                    │                 │                    │                   │
   │                    │                 │ viewModel.initialize│                  │
   │                    │                 │ setContent {       │                   │
   │                    │                 │  var showSettings  │                   │
   │                    │                 │    = false         │                   │
   │                    │                 │  if(showSettings)  │                   │
   │                    │                 │   SettingsScreen() │                   │
   │                    │                 │  else              │                   │
   │                    │                 │   EditScreen(      │                   │
   │                    │                 │    onNavigateToSettings=...           │
   │                    │                 │   )                │                   │
   │                    │                 │ }                  │                   │
   │◄───────────────────────────────────────────────────────── │                   │
   │         EditScreen 表示                                    │                   │
   │                    │                 │                    │                   │
   │ 設定アイコンタップ   │                 │                    │                   │
   │──────────────────────────────────────────────────────────►│                   │
   │                    │                 │                    │ onNavigateToSettings()
   │                    │                 │ showSettings = true│                   │
   │                    │                 │ → 再コンポーズ      │                   │
   │                    │                 │                    │                   │
   │◄──────────────────────────────────────────────────────────────────────────── │
   │         SettingsScreen 表示                                                   │
   │                    │                 │                    │                   │
   │ 戻るボタン / バックボタン                                                      │
   │──────────────────────────────────────────────────────────────────────────────►│
   │                    │                 │                    │                   │
   │                    │                 │ showSettings = false│                  │
   │                    │                 │ → 再コンポーズ      │                   │
   │                    │                 │                    │                   │
   │◄──────────────────────────────────────────────────────── │                   │
   │         EditScreen に戻る（フォーム内容保持）               │                   │
```

---

## ナビゲーション状態遷移 🔵

**信頼性**: 🔵 *REQ-401・rememberSaveable パターン・EDGE-101 より*

```
[アイコン起動]
    │
    └→ SettingsScreen
           │
           └→ finish() ─── アプリ終了

[共有フロー]
    │
    └→ EditScreen ──────────────────────────────────────
           │ 設定アイコンタップ                           │
           └→ SettingsScreen                             │
                  │ 戻る                                 │ キャンセル
                  └→ EditScreen（状態保持）             │
                         │ 送信                          │
                         └→ Obsidian 起動 → finish()    │
                                                        └→ finish()
```

### 状態変数（showSettings）の更新フロー 🔵

**信頼性**: 🔵 *Compose rememberSaveable パターン・EDGE-101 より*

```
setContent {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    //                  ↑
    //                  画面回転後も状態復元（EDGE-101）
    
    if (showSettings) → SettingsScreen 表示
    else              → EditScreen 表示
    
    EditScreen.onNavigateToSettings → showSettings = true
    SettingsScreen.onNavigateBack   → showSettings = false
}
```

---

## 文字列リソース追加フロー 🔵

**信頼性**: 🔵 *REQ-402・strings.xml 既存パターンより*

```
strings.xml に追加:
┌─────────────────────────────────────────────────────┐
│  <string name="label_settings">設定</string>         │
│  <string name="settings_placeholder">              │
│    設定項目はまだ実装されていません                     │
│  </string>                                         │
└─────────────────────────────────────────────────────┘
         │
         ├→ SettingsScreen: TopAppBar.title で使用
         └→ SettingsScreen: Body プレースホルダーで使用
```

---

## エラーハンドリング 🟡

**信頼性**: 🟡 *既存の ActivityNotFoundException パターンから妥当な推測*

設定画面（プレースホルダー）ではエラーが発生するシナリオがないため、特別なエラーハンドリングは不要。
将来的に設定機能を追加する際は、既存の Toast パターンを踏襲する。

---

## 信頼性レベルサマリー

- 🔵 青信号: 5件 (83%)
- 🟡 黄信号: 1件 (17%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: 高品質

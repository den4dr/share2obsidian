# 設定画面 受け入れ基準

**作成日**: 2026-05-31
**関連要件定義**: [requirements.md](requirements.md)
**関連ユーザストーリー**: [user-stories.md](user-stories.md)
**ヒアリング記録**: [interview-record.md](interview-record.md)

**【信頼性レベル凡例】**:
- 🔵 **青信号**: PRD・ユーザヒアリングを参考にした確実な基準
- 🟡 **黄信号**: PRD・ユーザヒアリングから妥当な推測による基準
- 🔴 **赤信号**: PRD・ユーザヒアリングにない推測による基準

---

## REQ-001: アイコンタップで SettingsScreen を表示 🔵

**信頼性**: 🔵 *PRD・ユーザヒアリングより*

### Given
- 共有インテントを持たない通常起動（アイコンタップ）

### When
- MainActivity が `ACTION_MAIN` インテントで起動する

### Then
- SettingsScreen が表示される
- EditScreen は表示されない

### テストケース

#### 正常系

- [ ] **TC-001-01**: アイコンタップで SettingsScreen が表示される 🔵
  - **入力**: `ACTION_MAIN` インテント（`EXTRA_TEXT` なし）
  - **期待結果**: SettingsScreen Composable が表示されている
  - **信頼性**: 🔵 *REQ-001 より*

---

## REQ-002: EditScreen のトップバーに設定アイコン配置 🔵

**信頼性**: 🔵 *ユーザヒアリングより*

### Given
- 共有フローにより EditScreen が表示されている

### When
- EditScreen のトップバーを確認する

### Then
- 設定アイコン（歯車等）がトップバー右側に表示されている

### テストケース

#### 正常系

- [ ] **TC-002-01**: EditScreen のトップバーに設定アイコンが表示される 🔵
  - **期待結果**: トップバーの actions 領域に設定アイコンが存在する
  - **信頼性**: 🔵 *REQ-002 より*

---

## REQ-101: 設定アイコンタップで SettingsScreen へ遷移 🔵

**信頼性**: 🔵 *PRD・ユーザヒアリングより*

### Given
- EditScreen が表示されている

### When
- トップバーの設定アイコンをタップする

### Then
- SettingsScreen へ遷移する

### テストケース

#### 正常系

- [ ] **TC-101-01**: 設定アイコンタップで SettingsScreen へ遷移する 🔵
  - **期待結果**: SettingsScreen が表示される
  - **信頼性**: 🔵 *REQ-101 より*

---

## REQ-102 / REQ-103: SettingsScreen からの戻り 🔵

**信頼性**: 🔵 *ユーザヒアリングより*

### Given（共有フローから遷移した場合）
- EditScreen → SettingsScreen へ遷移した状態

### When
- ツールバーの戻るボタン、またはバックボタンを押す

### Then
- EditScreen に戻る
- EditScreen のフォーム入力内容が保持されている

### Given（アイコン起動の場合）
- アイコンタップ → SettingsScreen が表示されている状態

### When
- ツールバーの戻るボタン、またはバックボタンを押す

### Then
- アプリが終了する

### テストケース

#### 正常系

- [ ] **TC-103-01**: 共有フロー経由の SettingsScreen でバックボタンを押すと EditScreen に戻る 🔵
  - **期待結果**: EditScreen が表示され、フォーム内容が保持されている
  - **信頼性**: 🔵 *REQ-103・EDGE-002 より*

- [ ] **TC-102-01**: ツールバーの戻るボタンで前画面に戻る 🔵
  - **期待結果**: TC-103-01 と同等（戻り先は遷移元による）
  - **信頼性**: 🔵 *REQ-102 より*

- [ ] **TC-103-02**: アイコン起動の SettingsScreen でバックボタンを押すとアプリが終了する 🔵
  - **期待結果**: Activity が終了する（`isFinishing == true`）
  - **信頼性**: 🔵 *EDGE-001・Android バックスタック仕様より*

---

## REQ-401: 単一アクティビティ構成の維持 🔵

**信頼性**: 🔵 *ユーザヒアリングより*

### テストケース

- [ ] **TC-401-01**: SettingsScreen 遷移時に新規 Activity が起動しない 🔵
  - **期待結果**: `ShadowApplication.nextStartedActivity` が null（MainActivity 内遷移）
  - **信頼性**: 🔵 *REQ-401 より*

---

## Edgeケーステスト

### EDGE-001: アイコン起動 → バックボタン → アプリ終了 🔵

- [ ] **TC-EDGE-001-01**: アイコン起動の SettingsScreen でバックボタンを押すとアプリが終了する 🔵
  - **条件**: `ACTION_MAIN` 起動 → SettingsScreen 表示中
  - **期待結果**: `activity.isFinishing == true`
  - **信頼性**: 🔵 *EDGE-001 より*

### EDGE-002: EditScreen → SettingsScreen → 戻る → EditScreen（状態保持） 🔵

- [ ] **TC-EDGE-002-01**: 設定画面から戻ったあと EditScreen のフォーム内容が維持される 🔵
  - **条件**: EditScreen でフォームを編集後、SettingsScreen へ遷移し、戻る
  - **期待結果**: EditScreen のフォーム内容が変わっていない
  - **信頼性**: 🔵 *EDGE-002・EditScreenViewModel のスコープより*

### EDGE-101: 画面回転時のナビゲーション状態保持 🟡

- [ ] **TC-EDGE-101-01**: SettingsScreen 表示中に画面回転しても SettingsScreen が表示されたまま 🟡
  - **条件**: SettingsScreen を表示した状態で画面を回転させる
  - **期待結果**: SettingsScreen が引き続き表示される
  - **信頼性**: 🟡 *Android ライフサイクル仕様から妥当な推測*

---

## テストケースサマリー

### カテゴリ別件数

| カテゴリ | 正常系 | 境界値/エッジ | 合計 |
|---------|--------|--------------|------|
| 機能要件 | 6 | 0 | 6 |
| Edgeケース | 0 | 3 | 3 |
| **合計** | **6** | **3** | **9** |

### 信頼性レベル分布

- 🔵 青信号: 8件 (89%)
- 🟡 黄信号: 1件 (11%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: 高品質

### 優先度別テストケース

- **Must Have**: 9件
- **Should Have**: 0件
- **Could Have**: 0件

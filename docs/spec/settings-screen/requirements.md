# 設定画面 要件定義書

**作成日**: 2026-05-31

## 概要

Share2Obsidian にアイコンタップで起動できる設定画面を追加する。
現時点では将来機能へのアクセス口となるプレースホルダー実装とし、実際の設定変更機能は含まない。
EditScreen のツールバーに設定アイコンを配置し、共有フロー中からも設定画面へ遷移できるようにする。

## 関連文書

- **ヒアリング記録**: [💬 interview-record.md](interview-record.md)
- **ユーザストーリー**: [📖 user-stories.md](user-stories.md)
- **受け入れ基準**: [✅ acceptance-criteria.md](acceptance-criteria.md)
- **コンテキストノート**: [📝 note.md](note.md)
- **PRD**: [設定画面.md](../../prd/設定画面.md)

## 機能要件（EARS記法）

**【信頼性レベル凡例】**:
- 🔵 **青信号**: PRD・ユーザヒアリングを参考にした確実な要件
- 🟡 **黄信号**: PRD・ユーザヒアリングから妥当な推測による要件
- 🔴 **赤信号**: PRD・ユーザヒアリングにない推測による要件

### 通常要件

- REQ-001: システムは、アイコンタップ（共有インテントなし）で起動したとき、SettingsScreen を表示しなければならない 🔵 *PRD「共有以外の方法で普通にアイコンタップで起動」より*
- REQ-002: システムは、EditScreen のトップバーに設定アイコンを配置しなければならない 🔵 *ユーザヒアリング「ツールバーの設定アイコン」より*
- REQ-003: SettingsScreen は将来機能のプレースホルダーとして実装しなければならない（現時点では設定変更機能を含まない） 🔵 *ユーザヒアリング「画面のみ（設定なし）」より*

### 条件付き要件

- REQ-101: 設定アイコンをタップした場合、システムは SettingsScreen へ遷移しなければならない 🔵 *PRD「該当の画面へ共有中の画面内から飛べるようにする」より*
- REQ-102: SettingsScreen でツールバーの戻るボタンをタップした場合、システムは前の画面（EditScreen またはアプリ終了）へ戻らなければならない 🔵 *ユーザヒアリング「ツールバーの戻るボタンも追加」より*
- REQ-103: SettingsScreen でバックボタンを押した場合、システムは前の画面（EditScreen またはアプリ終了）へ戻らなければならない 🔵 *ユーザヒアリング「バックボタンとの併用」より*

### 制約要件

- REQ-401: システムは単一アクティビティ構成（MainActivity のみ）を維持しなければならない 🔵 *ユーザヒアリング「同じMainActivity内でCompose画面として追加」より*
- REQ-402: UI 文字列はすべて `res/values/strings.xml` に定義しなければならない 🔵 *既存開発ルール・content-edit-preview note.md より*
- REQ-403: UI コンポーネントは Compose Material3 を使用しなければならない 🔵 *既存開発ルール・content-edit-preview note.md より*

## 非機能要件

### ユーザビリティ

- NFR-101: SettingsScreen のトップバーにタイトル（「設定」）を表示しなければならない 🟡 *Android Material3 標準 UX パターンから妥当な推測*
- NFR-102: 設定アイコンは Material3 の標準アイコン（Icons.Default.Settings）を使用するべきである 🟡 *Material3 UX ガイドラインから妥当な推測*

### 保守性

- NFR-201: SettingsScreen は将来の設定項目追加を考慮した拡張しやすい構造にすべきである 🟡 *PRD「今後実装予定の各種機能へのアクセス」から妥当な推測*

## Edgeケース

### ナビゲーション

- EDGE-001: アイコンタップ起動 → SettingsScreen → バックボタン → バックスタックが空のためアプリが終了する（正常動作） 🔵 *Android バックスタック仕様より*
- EDGE-002: 共有フロー（EditScreen）→ 設定アイコン → SettingsScreen → 戻る → EditScreen に戻る（バックスタック保持） 🔵 *ユーザヒアリング + Android バックスタック仕様より*

### 状態保持

- EDGE-101: SettingsScreen 表示中に画面回転しても、遷移元の情報（EditScreen か直接起動か）が保持される 🟡 *Android ライフサイクル仕様から妥当な推測*

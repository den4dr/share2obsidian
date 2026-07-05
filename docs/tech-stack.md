# プロジェクト技術スタック定義

## 🔧 生成情報
- **生成日**: 2026-03-28
- **生成ツール**: tsumiki:init-tech-stack
- **プロジェクトタイプ**: モバイルアプリ（Android）
- **チーム規模**: 個人開発
- **開発期間**: プロトタイプ/MVP（1-2ヶ月）

## 🎯 プロジェクト要件サマリー
- **パフォーマンス**: 軽負荷（同時利用者10人以下）
- **セキュリティ**: 基本レベル（一般的なAndroidセキュリティ対策）
- **技術スキル**: Kotlin/Java, JavaScript/TypeScript
- **学習コスト許容度**: 積極的に新技術
- **デプロイ先**: Google Play（Android専用）
- **予算**: コスト最小化（無料・低コストツール優先）

## 🚀 言語・UI フレームワーク
- **言語**: Kotlin 2.2+
- **UIフレームワーク**: Jetpack Compose（BOM 2024.09.00+）
- **デザインシステム**: Material 3

### 選択理由
- Kotlinはすでにプロジェクトで採用済みで、チームスキルと一致
- Jetpack ComposeはGoogleが推奨するモダンなAndroid UIツールキット
- Material 3でAndroid 12+のダイナミックカラーに対応可能

## ⚙️ アーキテクチャ
- **パターン**: MVVM（Model-View-ViewModel）+ Repository
- **依存性注入**: Hilt 2.56+
- **ライフサイクル**: AndroidX Lifecycle（ViewModel, LiveData/StateFlow）

### 選択理由
- MVP規模に適切なシンプルさを持つGoogleの公式推奨アーキテクチャ
- HiltはDagger2のAndroid向けラッパーで、ボイラープレートを大幅削減
- StateFlowはCoroutinesと相性がよく、Compose UIと自然に統合できる

## 💾 ローカルデータ管理
- **ORM/DB**: Room 2.7+（必要な場合）
- **設定保存**: DataStore Preferences（SharedPreferencesの後継）
- **ファイルストレージ**: Android標準の内部/外部ストレージAPI

### 設計方針
- 今のShare2Obsidianは永続化不要のシンプルな構成のため、現時点でRoomは不要
- 設定やユーザー設定が必要になればDataStore Preferencesを追加
- ネットワーク通信が必要になればRetrofit 2.11+ + OkHttp 4+を追加検討

## 🛠️ 開発環境
- **ビルドシステム**: Gradle 9.3+ (Kotlin DSL)
- **IDE**: Android Studio (最新安定版)
- **言語バージョン**: Kotlin 2.2+、Java 11互換

### 開発ツール
- **単体テスト**: JUnit 4 / JUnit 5 + MockK（Kotlin向けモックライブラリ）
- **UIテスト**: Jetpack Compose UI Test + Espresso
- **静的解析**: ktlint（コードフォーマット）+ detekt（静的解析）
- **依存関係管理**: Gradle Version Catalog（`gradle/libs.versions.toml`）

### CI/CD
- **CI/CD**: GitHub Actions（無料枠で個人開発に十分）
- **自動テスト**: プッシュ毎にUnit Test実行
- **リリース**: 手動または半自動でGoogle Playへ配布

## ☁️ デプロイ
- **配布**: Google Play Store
- **ビルド成果物**: APK / AAB（Android App Bundle）
- **署名**: Android Keystoreによる署名（リリースビルド時）

## 🔒 セキュリティ
- **権限**: 必要最小限のAndroidパーミッションのみ宣言
- **Intent処理**: 受け取るIntentのバリデーション実施
- **ProGuard/R8**: リリースビルドで有効化推奨
- **機密情報**: APIキー等はlocal.propertiesで管理し、Gitに含めない

## 📊 品質基準
- **テストカバレッジ**: コアロジックは80%以上を目安
- **Lint**: Android Lintチェックをパスすること
- **コード品質**: ktlint準拠のフォーマット

## 📁 推奨ディレクトリ構造

```
app/src/main/java/com/den4dr/share2Obsidian/
├── MainActivity.kt               # エントリポイント（Intent処理）
├── ui/
│   └── theme/                    # Composeテーマ定義
├── domain/                       # ビジネスロジック（将来の拡張用）
│   └── usecase/
├── data/                         # データ層（将来の拡張用）
│   └── repository/
└── di/                           # Hilt依存注入モジュール（将来の拡張用）
```

## 🚀 主要コマンド

```bash
./gradlew assembleDebug          # デバッグAPKビルド
./gradlew assembleRelease        # リリースAPKビルド
./gradlew test                   # ユニットテスト実行
./gradlew connectedAndroidTest   # インストゥルメントテスト実行（実機/エミュレータ必要）
./gradlew lint                   # Lintチェック
./gradlew clean                  # ビルドキャッシュ削除
```

## 🔄 更新履歴
- 2026-03-28: 初回生成（tsumiki:init-tech-stackにより自動生成）

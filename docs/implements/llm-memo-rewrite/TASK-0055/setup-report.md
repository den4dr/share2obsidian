# TASK-0055 設定作業実行

## 作業概要

- **タスクID**: TASK-0055
- **作業内容**: LLM機能追加に向けた依存関係（Ktor Client / kotlinx-serialization / androidx-security-crypto）の追加とプロジェクト設定
- **実行日時**: 2026-07-05
- **実行者**: Claude Code（direct-setup）

## 設計文書参照

- **参照文書**:
  - [docs/tasks/llm-memo-rewrite/TASK-0055.md](../../../tasks/llm-memo-rewrite/TASK-0055.md)
  - [docs/design/llm-memo-rewrite/architecture.md](../../../design/llm-memo-rewrite/architecture.md)（「アーキテクチャパターン」「新規追加コンポーネント」「技術的制約」）
- **関連要件**: REQ-401, REQ-402

## 実行した作業

### 1. version catalog（`gradle/libs.versions.toml`）への追加

タスク仕様の `3.0.x` / `1.7.x` / `1.1.x` はプレースホルダだったため、Kotlin 2.2.10 との互換性を優先してWeb検索で以下の具体バージョンを選定した。

| ライブラリ | 選定バージョン | 選定理由 |
|---|---|---|
| ktor | `3.3.1` | リリースノートに「Update Kotlin to 2.2.20」とあり、Kotlin 2.2.x系列（本プロジェクトの2.2.10）に一致する最終安定版。Ktor 3.4.0以降はKotlin 2.3系が前提のため除外。 |
| kotlinx-serialization-json | `1.9.0` | リリースノートに「updates Kotlin version to 2.2.0」とあり、2.2.x系列に一致する安定版。最新の1.11.0はKotlin 2.3.20ベースで本プロジェクトのコンパイラ(2.2.10)とのメタデータ互換性リスクがあるため見送った。 |
| androidx-security-crypto | `1.1.0` | 2025-07-30リリースの安定版。**注意**: 全APIが非推奨（deprecated）となっており、AndroidX側はAndroid Keystore直接利用への移行を推奨している。ただし設計文書（architecture.md）が本ライブラリを明示的に指定しているため、設計通り採用した。 |

追加内容:

```toml
[versions]
ktor = "3.3.1"
kotlinxSerialization = "1.9.0"
androidxSecurityCrypto = "1.1.0"

[libraries]
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { group = "io.ktor", name = "ktor-client-cio", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "androidxSecurityCrypto" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

### 2. `app/build.gradle.kts` への追加

```kotlin
plugins {
    // 既存に追加
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // 既存に追加
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)
}
```

### 3. ビルド確認

```bash
mise exec -- ./gradlew build
```

**結果**: `BUILD SUCCESSFUL`。依存解決エラーなし。debug APK (`app/build/outputs/apk/debug/app-debug.apk`) を含む全バリアントの生成に成功。

既存の単体テスト（`testDebugUnitTest`）も本ビルドに含まれ、全26テストクラスで **failures=0, errors=0** を確認（`app/build/test-results/testDebugUnitTest/*.xml`）。

## 作業結果（完了条件チェック）

- [x] `gradle/libs.versions.toml` に ktor（client-core/client-cio/client-content-negotiation/serialization-kotlinx-json）、kotlinx-serialization-json、androidx-security-cryptoのバージョン・ライブラリ定義が追加されている
- [x] `app/build.gradle.kts` に `kotlin("plugin.serialization")` プラグインと上記ライブラリの依存関係が追加されている
- [x] `mise exec -- ./gradlew build` が成功する
- [x] 既存テスト（`mise exec -- ./gradlew test`相当。`build`タスクに含まれる`testDebugUnitTest`で確認）が引き続き全て成功する

## 遭遇した問題と解決方法

### 問題1: タスク仕様書のバージョン指定がプレースホルダ（`3.0.x`等）だった

- **発生状況**: TASK-0055.md記載のバージョン番号はプレースホルダで、「導入時に安定版を確認する」との注記があった
- **解決方法**: WebSearch/WebFetchで各ライブラリの最新リリース履歴とKotlinバージョン対応表を確認し、本プロジェクトのKotlin 2.2.10と同じ2.2.x系列でビルドされている安定版（ktor 3.3.1、kotlinx-serialization-json 1.9.0）を選定した。androidx-security-cryptoは最新安定版1.1.0を採用（非推奨化済みだが設計文書の指定通り）。

## 次のステップ

- `/tsumiki:direct-verify` を実行して設定を確認
- 後続タスク（TASK-0056〜0058, 0059）で本タスクの依存関係を利用した実装を進める
- androidx-security-crypto は将来的に非推奨化されているため、後続フェーズでのAPI利用時に非推奨警告が出ることを想定しておく（設計文書の判断を踏襲）

# connectedAndroidTest ビルド阻害要因の修復メモ

**発生タイミング**: TASK-0067 red/green/refactor/verify-complete フェーズ中に発見
**対応日**: 2026-07-08

## 問題1: META-INF/LICENSE.md 重複

`androidTestImplementation(libs.mockk.android)` が推移的に
`org.junit.jupiter:junit-jupiter:5.8.2` 系（junit-jupiter-api/engine/params, junit-platform-engine/commons）を
引き込んでおり、これら6個のjarが同名の `META-INF/LICENSE.md` を含んでいたため
`:app:mergeDebugAndroidTestJavaResource` が失敗していた。

**修正**: `app/build.gradle.kts` の `android {}` ブロックに以下を追加。

```kotlin
packaging {
    resources {
        excludes += "META-INF/LICENSE.md"
        excludes += "META-INF/LICENSE-notice.md"
    }
}
```

## 問題2: DEXに変換不能なテストメソッド名

問題1を修正した後、`:app:dexBuilderDebugAndroidTest` が新たなエラーで失敗した:

```
Method name 'BC-01 enabled=true かつ rewriting=false のとき活性' in class
'com.den4dr.share2Obsidian.ui.EditScreenTest' cannot be represented in dex format.
```

Kotlinのバッククォート識別子は空白・日本語を許容するが、`=` 文字はDEX形式のメソッド名として
無効。TASK-0065実装時（オペレーター直接実装）に追加した2つのテストメソッド名に `=` が
含まれていたことが原因。

**修正**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt` の該当2メソッドを
`=` を含まない表現にリネーム（テスト内容・アサーションは変更なし）:
- `` `BC-01 enabled=true かつ rewriting=false のとき活性` `` → `` `BC-01 enabledがtrue かつ rewritingがfalse のとき活性` ``
- `` `BC-02 enabled=false かつ rewriting=true のとき非活性` `` → `` `BC-02 enabledがfalse かつ rewritingがtrue のとき非活性` ``

他ファイルに同様の `=` を含むバッククォートテスト名がないことを `Grep` で確認済み。

## 検証結果

- `mise exec -- ./gradlew :app:mergeDebugAndroidTestJavaResource`: BUILD SUCCESSFUL
- `mise exec -- ./gradlew assembleDebugAndroidTest`: BUILD SUCCESSFUL（テストAPKの組み立てが可能に）
- `mise exec -- ./gradlew build`: BUILD SUCCESSFUL（lintDebugは1回flakyな内部クラッシュが発生したが、
  再実行で成功。既知のKotlin FIR解析系lintの不安定性であり、今回の変更とは無関係と判断）

## 既知の残課題

この環境には `adb` が存在しないため、`connectedAndroidTest` による実機/エミュレータでの
実行確認は依然としてできない。ただし、テストAPKのビルド自体は正常化されたため、
デバイス/エミュレータが用意でき次第 `mise exec -- ./gradlew connectedAndroidTest` を
実行するだけで良い状態になった。

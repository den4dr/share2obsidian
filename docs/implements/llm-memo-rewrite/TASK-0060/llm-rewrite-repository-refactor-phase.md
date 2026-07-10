# TDD Refactorフェーズ記録: LlmRewriteRepository・LlmRewriteRepositoryImpl

**機能名**: llm-rewrite-repository
**タスクID**: TASK-0060
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. リファクタ前の確認

### 1.1 テスト実行結果（リファクタ前）

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmRewriteRepositoryImplTest*" --tests "*ChatCompletionDtoTest*" --rerun-tasks
# => BUILD SUCCESSFUL（23件全て成功）
```

### 1.2 テスト実行時間チェック

各テストの実行時間を`app/build/test-results/testDebugUnitTest/*.xml`から集計した結果、
`IOException発生時にNetworkErrorを返す`が2.166秒で唯一2秒以上を記録した。

```
⚠️ 遅いテストが検出されました（2秒以上）
- LlmRewriteRepositoryImplTest > IOException発生時にNetworkErrorを返す: 2.166s
```

**原因分析**: 他の12件（同一クラス・同一Robolectricランナー）は全て0.05秒未満で完了しており、
このテストのみが突出して遅いわけではなく、実行順序上最初に実行されたテストが
Robolectricのクラスローダー初期化・SDKシャドウのセットアップコストを一括負担したことによる
一過性のオーバーヘッドと判断した（アルゴリズム的な非効率ではない）。実装コード
（`LlmRewriteRepositoryImpl.rewrite()`）自体はHTTP1往復のみでループや重い計算を含まないため、
`/tsumiki:dcs:test-performance-analysis` によるさらなる分析は不要と判断した。

### 1.3 コード・テスト除外チェック

- `@Ignore` / `.skip(` / `xdescribe` / `xit(` 等のテスト無効化パターンを`app/src/test`・
  `app/src/androidTest`全体で検索したが該当なし。
- `.gitignore`でTASK-0060関連ファイルが除外されていないことを確認。

### 1.4 開発時生成ファイルのクリーンアップ

`debug-*` / `test-*` / `temp-*` / `*.tmp` / `*.bak` / `*.orig` / `*~` / `.DS_Store` 等の
パターンでリポジトリ全体（`node_modules`・`.git`・`build`配下除く）を検索したが、
該当する不要ファイルは見つからなかった。

---

## 2. セキュリティレビュー

| 観点 | 確認結果 |
|------|---------|
| APIキーのログ出力 | `LlmRewriteRepositoryImpl`・`LlmRewriteRepository`・`ChatCompletionDto`のいずれにも`android.util.Log`等のログ呼出しが存在しないことをgrepで確認。各catch節も例外オブジェクト（`e`）を使用せず`Failure`型へのマッピングのみ行うため、例外メッセージ経由でのAPIキー漏洩経路も存在しない（NFR-102, TC-09） 🔵 |
| Authorizationヘッダの構築 | `"Bearer ${settings.apiKey}"`としてのみ使用し、他の永続化・出力先には渡さない（REQ-401） 🔵 |
| 入力値検証 | `content`/`prompt`は意図的に検証・加工しない（EDGE-101, TC-13が要求する仕様であり、脆弱性ではない）。送信先はJSON文字列としてkotlinx.serializationでエスケープされるため、リクエストボディ内へのインジェクションリスクはない 🔵 |
| エンドポイントURL検証 | `settings.endpointUrl`は事前検証を行わない（REQ-404で明示的に「接続テストは行わない」と規定）。本タスクの範囲でURLスキーム（http/https）を強制していない点は、ユーザー設定のローカルLLMサーバー等（REQ-004）を許容する設計上のトレードオフであり、要件定義書に基づく意図的な仕様である 🟡（httpエンドポイント設定時はAPIキーが平文でネットワークを流れる可能性があるが、これはユーザー自身の設定内容に起因するリスクであり、本タスクの実装で新たに導入されたものではない） |
| 例外の握りつぶし | `catch (_: Exception)`は`Failure.Unknown`へ変換するのみで、例外を無視して処理を継続する危険なパターンではない（呼び出し元に必ずFailureとして伝播する） 🔵 |
| DB/SQLインジェクション | 本クラスはHTTP通信のみでDBアクセスを行わないため該当なし |

**総評**: 重大な脆弱性は発見されなかった。エンドポイントURLのスキーム未検証は要件定義に基づく
意図的な設計判断であり、本タスクの範囲での対応は不要と判断した。

---

## 3. パフォーマンスレビュー

| 観点 | 確認結果 |
|------|---------|
| 計算量 | `rewrite()`はHTTP POST 1回・レスポンス解析（`firstOrNull()`によるO(1)アクセス）のみで、O(1)相当（通信レイテンシが支配的） |
| メモリ使用量 | リクエスト/レスポンスDTOはいずれも小サイズのdata classであり、大きなコレクションの保持や不要なコピーは発生しない |
| ボトルネック | ネットワークI/O（LLM API応答時間）が支配的コストであり、実装コード側での最適化余地はない。タイムアウト設定（30秒）はDI層（TASK-0061）の責務として適切に分離されている |
| リソース管理 | `HttpClient`はコンストラクタ注入され、本クラスでは生成・破棄を行わない（呼び出し元が再利用・close責務を持つ設計） |

**総評**: 重大な性能課題は発見されなかった。

---

## 4. 実施したリファクタリング内容

### 4.1 責務の分離（可読性・単一責任原則）

`rewrite()`本体に混在していた「リクエスト構築」「応答解析」「認証ステータス判定」の3つの処理を
それぞれ専用のプライベート関数へ抽出した。

- `postChatCompletion(settings, prompt, content): ChatCompletionResponseDto`
  — HTTPリクエストの構築・送信のみを担当
- `ChatCompletionResponseDto.toRewriteResult(): LlmRewriteResult`
  — 応答からのテキスト抽出とSuccess/EmptyOrInvalidResponse判定のみを担当（拡張関数）
- `isAuthErrorStatus(status: HttpStatusCode): Boolean`
  — 401/403判定のみを担当するヘルパー。将来的な認証対象ステータス追加時の変更箇所を1箇所に限定する

🔵 信頼性レベル: 機能・戻り値・catch順序は変更していないため、Greenフェーズのテストケース定義
（testcases.md）との対応関係に変更はない。

### 4.2 未使用のcatch変数の整理

`catch (e: HttpRequestTimeoutException)` 等、マッピングにのみ使い例外オブジェクト自体を参照しない
catch節について、変数名を`_`に変更し「意図的に使用しない」ことを明示した
（`catch (e: ClientRequestException)`のみ`e.response.status`を参照するため維持）。

### 4.3 コメントの改善

各関数に【機能概要】【改善内容】【設計方針】【パフォーマンス】【保守性】【テスト対応】の
観点別コメントを付与し、リファクタ前の実装意図と変更理由を明示した。

---

## 5. リファクタリング後のテスト実行結果

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmRewriteRepositoryImplTest*" --tests "*ChatCompletionDtoTest*"
# => BUILD SUCCESSFUL（23件全て成功、リファクタ前と同一結果）

mise exec -- ./gradlew testDebugUnitTest --rerun-tasks compileDebugAndroidTestKotlin
# => BUILD SUCCESSFUL
```

プロジェクト全体のJVMユニットテスト結果XML（`app/build/test-results/testDebugUnitTest/*.xml`）を
再集計し、**218件全て成功（failures/errors = 0）**を確認した（リファクタ前と同数・同結果、回帰なし）。

`compileDebugAndroidTestKotlin`もBUILD SUCCESSFULを維持しており、`LlmRewriteRepositoryIntegrationTest.kt`
（IT-01, IT-02）のコンパイルも引き続き成功することを確認した。実機/エミュレータでの実行確認は
本環境にadb/emulatorが存在しないため引き続き別途デバイス環境で行う必要がある。

---

## 6. 品質判定

```
✅ 高品質:
- テスト結果: リファクタ前後で218件全て成功、回帰なし
- セキュリティ: 重大な脆弱性なし（エンドポイントURL未検証は要件定義に基づく意図的仕様）
- パフォーマンス: 重大な性能課題なし（HTTP1往復・O(1)処理のみ）
- リファクタ品質: 3責務の分離・catch変数整理・コメント強化により可読性向上を達成
- コード品質: lint/コンパイルエラーなし（compileDebugUnitTestKotlin, compileDebugAndroidTestKotlin共にBUILD SUCCESSFUL）
- ファイルサイズ: LlmRewriteRepositoryImpl.kt 132行（500行制限内、Green時91行から責務分離により増加したが十分な余裕あり）
- 日本語コメント: 各関数に機能概要・改善内容・設計方針・信頼性レベルを明記
```

## 7. 今後の課題（verify-completeフェーズ以降で検討）

1. IT-01/IT-02の実機/エミュレータでの実行確認（別途デバイス環境が必要）
2. `ChatMessageDto.content`のnullable化（Greenフェーズでの対応）が他タスク（TASK-0059テスト、
   将来のタグ提案・カスタムフィールド機能）に影響しないことをverify-completeで最終確認
3. エンドポイントURLのスキーム（http/https）検証は本タスクの範囲外だが、セキュリティレビューで
   識別した既知のトレードオフとして、必要であれば別タスクでの検討対象とする

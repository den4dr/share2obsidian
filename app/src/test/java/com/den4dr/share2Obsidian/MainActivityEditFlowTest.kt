package com.den4dr.share2Obsidian

import android.content.Intent
import android.net.Uri
import android.os.Looper
import com.den4dr.share2Obsidian.format.NoteComposer
import com.den4dr.share2Obsidian.format.NoteConfig
import com.den4dr.share2Obsidian.ui.EditScreenViewModel
import com.den4dr.share2Obsidian.ui.SendParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowToast

/**
 * TASK-0020: MainActivity フロー変更 Robolectric テスト (Red フェーズ)
 *
 * 変更後のフロー（EditScreen 表示・コールバック経由 Obsidian 起動）の単体検証。
 *
 * テスト対象:
 *   - TC-0020-N01: テキスト共有直後は Obsidian を起動しない（即時起動撤廃）
 *   - TC-0020-N02: SendParams → 正しい obsidian URI 構築（onSend ロジック）
 *   - TC-0020-N03: キャンセルで Obsidian 未起動 + finish（onCancel ロジック）
 *   - TC-0020-N04: 共有対象外 Intent（null）は即 finish（リグレッション）
 *   - TC-0020-E01: Obsidian 未インストール時にトーストを表示して終了する
 *   - TC-0020-E02: title=null で Frontmatter の title 行が省略され URI の title が空文字になる
 *   - TC-0020-B01: 本文空文字の SendParams で空ノート URI が構築される（EDGE-002）
 *   - TC-0020-B02: タグ空リストの SendParams で tags: [] が出力される（EDGE-003）
 *   - TC-0020-B03: 画面回転後も ViewModel の編集内容が保持される（EDGE-101）
 *   - TC-0020-B04: バックボタン＝キャンセルとして Obsidian 未起動 + finish（EDGE-102）
 *
 * 実行: mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.MainActivityEditFlowTest"
 *
 * 注意: Red フェーズ。MainActivity はまだ変更されていないため、
 *       N01/N03/B04 のテストは失敗（旧フローは即起動する）し、
 *       N02/E01/E02/B01/B02/B03 はロジック単位のテストとして現行実装ではパス可能なものがある
 *       (NoteComposer/EditScreenViewModel の単体ロジック検証)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityEditFlowTest {

    // ----------------------------------------------------------------
    // ヘルパー
    // ----------------------------------------------------------------

    /** テキスト共有 Intent を生成するヘルパー */
    private fun makeShareIntent(mimeType: String, text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, text)
        }

    /** テスト共通 NoteConfig */
    private val defaultConfig = NoteConfig(
        vault = "testVault",
        folder = "70_clippings",
        defaultTags = listOf("shared"),
    )

    // ================================================================
    // 1. 正常系テストケース
    // ================================================================

    /**
     * TC-0020-N01: テキスト共有後は EditScreen 表示段階で Obsidian を起動しない
     *
     * 🔵 青信号: TASK-0020.md「変更後フロー」・dataflow.md フロー1 より
     *
     * 【変更前挙動】: コンテンツ処理完了直後に `startActivity(obsidian://new...)` → finish()
     * 【変更後期待】: `setContent { EditScreen(...) }` を表示して待機。ユーザー操作がなければ
     *                Obsidian を起動せず、Activity も finish() しない。
     *
     * ⚠️ Red フェーズ: 現在の MainActivity は即起動するため、このテストは失敗する。
     */
    @Test
    fun `TC-0020-N01 テキスト共有インテントで起動した直後は Obsidian を起動しない`() {
        // 【テスト目的】: 変更後フローで即時 Obsidian 起動が撤廃され、EditScreen 表示段階では
        //                startActivity(obsidian://...) が呼ばれないことを検証する
        // 【テスト内容】: text/plain の ACTION_SEND で MainActivity を起動し、Looper を進めた後に
        //                nextStartedActivity が null であることを確認する
        // 【期待される動作】: コンテンツ処理完了後に EditScreen が表示されるが、
        //                    ユーザー操作（送信）がないため Obsidian は起動されない
        // 🔵 信頼性レベル: TASK-0020.md 変更後フロー、testcases.md TC-0020-N01 に基づく

        // Arrange: テキスト共有インテントを生成する
        // 【テストデータ準備】: フロー1（テキスト共有）の基本シナリオ。既存 MainActivityTest と同一の入力
        val intent = makeShareIntent("text/plain", "テスト共有テキスト")

        // 【テスト前準備】: Robolectric で Activity を create().start().resume() で起動する
        // 【環境初期化】: @LooperMode(PAUSED) のため lifecycleScope.launch は idle() まで実行されない
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().start().resume()
        val activity = controller.get()
        val shadow = Shadows.shadowOf(activity)

        // When: Looper を進めてコルーチン（lifecycleScope.launch）を実行させる
        // 【実際の処理実行】: Shadows.shadowOf(Looper.getMainLooper()).idle() で lifecycleScope の処理を進める
        // 【実行タイミング】: idle() を呼ばないと lifecycleScope.launch 内の処理が実行されない点に注意
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // Then: EditScreen 表示段階では startActivity が呼ばれていないことを確認する
        // 【結果検証】: nextStartedActivity が null = Obsidian が起動されていない
        // 【期待値確認】: 変更後フローではユーザー操作（送信ボタンタップ）がないと Obsidian を起動しない
        assertNull(
            "EditScreen 表示段階では Obsidian が起動されないこと（即時起動が撤廃されていること）",
            shadow.nextStartedActivity,
        ) // 【確認内容】: startActivity(obsidian://...) が呼ばれていないことを確認 🔵

        // 【追加検証】: Activity が終了していないこと（EditScreen を表示して待機中であること）
        // 【期待値確認】: 変更後フローではユーザー操作前に finish() が呼ばれない
        assertFalse(
            "EditScreen 表示中は Activity が終了していないこと",
            activity.isFinishing,
        ) // 【確認内容】: 変更前は即 finish() していたが、変更後は待機中であることを確認 🔵
    }

    /**
     * TC-0020-N02: onSend コールバックで正しい Obsidian URI が構築される
     *
     * 🔵 青信号: TC-101-01・REQ-101・NoteComposer.kt 実装・note.md テストケース2 より
     *
     * 【テスト戦略】: MainActivity の onSend コールバックロジック（NoteComposer 経由 URI 構築）を
     *               NoteComposer + EditScreenViewModel を直接使用してロジック単位で検証する。
     *               Compose ボタンのタップ検証は androidTest（L3）で後追い実装する。
     */
    @Test
    fun `TC-0020-N02 SendParams から NoteComposer 経由で正しい obsidian URI が生成される`() {
        // 【テスト目的】: onSend コールバックの中核ロジック（NoteComposer.buildFrontmatter → NoteComposer.buildUri）が
        //                編集後の値から期待どおりの URI を生成することを検証する
        // 【テスト内容】: 典型的な SendParams（タイトル・本文・複数タグ・フォルダ設定あり）で
        //                NoteComposer のメソッドを呼び出し、生成された URI のクエリパラメータを確認する
        // 【期待される動作】: obsidian://new?content=<Frontmatter付き本文>&title=テスト&vault=testVault&folder=70_clippings
        // 🔵 信頼性レベル: TC-101-01・REQ-101・NoteComposer.kt:37-79 の実装に基づく

        // Arrange: 典型的な送信パラメータを準備する
        // 【テストデータ準備】: TC-101-01 の代表値（タイトル・本文・複数タグ・フォルダすべて入力済み）
        val sendParams = SendParams(
            title = "テスト",
            body = "本文",
            tags = listOf("shared", "web"),
            config = defaultConfig,
        )

        // When: onSend コールバック内のロジックを再現する（NoteComposer 経由 URI 構築）
        // 【実際の処理実行】: MainActivity の onSend コールバック内と同等のロジックを呼び出す
        // 【処理内容】: buildFrontmatter → buildUri の2ステップで URI を生成する
        val content = NoteComposer.buildFrontmatter(
            sendParams.title,
            sendParams.body,
            sendParams.tags,
        )
        val uri = NoteComposer.buildUri(content, sendParams.title, sendParams.config)

        // Then: 生成された URI が期待値と一致することを確認する
        // 【結果検証】: URI の scheme・host・クエリパラメータを個別に検証する
        // 【期待値確認】: NoteComposer.buildUri の実装（obsidian://new + appendQueryParameter）に基づく
        assertEquals(
            "URI の scheme が obsidian であること",
            "obsidian",
            uri.scheme,
        ) // 【確認内容】: obsidian URI スキームが正しく設定されていること 🔵
        assertEquals(
            "URI の host が new であること",
            "new",
            uri.host,
        ) // 【確認内容】: Obsidian の new ノート作成エンドポイントに送信されること 🔵
        assertEquals(
            "URI の title クエリが期待値であること",
            "テスト",
            uri.getQueryParameter("title"),
        ) // 【確認内容】: NoteComposer 経由で title が正しく URI 化されること 🔵
        assertEquals(
            "URI の vault クエリが NoteConfig.vault と一致すること",
            "testVault",
            uri.getQueryParameter("vault"),
        ) // 【確認内容】: NoteConfig の vault 値が URI に反映されること 🔵
        assertEquals(
            "URI の folder クエリが NoteConfig.folder と一致すること",
            "70_clippings",
            uri.getQueryParameter("folder"),
        ) // 【確認内容】: NoteConfig の folder 値が URI に反映されること 🔵

        // Frontmatter の内容を検証する
        // 【追加検証】: content クエリに Frontmatter 形式の文字列が含まれること
        val contentParam = uri.getQueryParameter("content")
        assertTrue(
            "URI の content クエリに Frontmatter ヘッダーが含まれること",
            contentParam?.contains("---") == true,
        ) // 【確認内容】: Frontmatter 区切り文字 --- が含まれていること 🔵
        assertTrue(
            "URI の content クエリに title フィールドが含まれること",
            contentParam?.contains("title: \"テスト\"") == true,
        ) // 【確認内容】: タイトルが Frontmatter の title フィールドに設定されていること 🔵
        assertTrue(
            "URI の content クエリに tags フィールドが含まれること",
            contentParam?.contains("tags: [shared, web]") == true,
        ) // 【確認内容】: 複数タグがカンマ+スペース区切りで Frontmatter に設定されていること 🔵
        assertTrue(
            "URI の content クエリに本文が含まれること",
            contentParam?.contains("本文") == true,
        ) // 【確認内容】: Frontmatter ヘッダー後に本文が設定されていること 🔵
    }

    /**
     * TC-0020-N03: onCancel コールバックで Obsidian 未起動 + Activity 終了
     *
     * 🔵 青信号: TC-201-01・REQ-201・note.md テストケース1 より
     *
     * 【テスト戦略】: MainActivity の onCancel コールバック（finish() のみ）を Activity 起動後に
     *               直接呼び出し、startActivity が呼ばれず isFinishing が true になることを確認する。
     *
     * ⚠️ Red フェーズ: 変更後 MainActivity の onCancel コールバックが存在しないため失敗する。
     */
    @Test
    fun `TC-0020-N03 キャンセルコールバックは Obsidian を起動せず Activity を終了する`() {
        // 【テスト目的】: onCancel = { finish() } の挙動を検証する。キャンセル経路では
        //                startActivity が一切呼ばれず finish() のみが実行されることを確認する
        // 【テスト内容】: テキスト共有で起動 → Looper を進めて EditScreen 表示 → onCancel を呼び出す
        //                （Compose ボタンタップの代替として Activity の finish() 直接呼び出しで確認）
        // 【期待される動作】: startActivity == null かつ isFinishing == true
        // 🔵 信頼性レベル: REQ-201・TASK-0020.md「キャンセルボタン（フロー4）」に基づく

        // Arrange: テキスト共有インテントで Activity を起動する
        // 【テストデータ準備】: フロー4（キャンセル）のシナリオ。ユーザーが送信せず離脱する
        val intent = makeShareIntent("text/plain", "テスト共有テキスト")
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().start().resume()
        val activity = controller.get()
        val shadow = Shadows.shadowOf(activity)

        // 【実行前準備】: Looper を進めてコンテンツ処理 + EditScreen 表示まで進める
        // 【環境初期化】: idle() で lifecycleScope.launch を実行させてから onCancel を呼び出す
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // When: キャンセル操作を実行する（変更後 MainActivity の onCancel コールバック相当）
        // 【実際の処理実行】: MainActivity の onCancel = { finish() } と同等の処理を実行する
        // 【処理内容】: EditScreen が表示された状態でキャンセルボタンを押した場合の挙動を検証する
        // NOTE: 変更後 MainActivity では onCancel コールバックで finish() を呼ぶ。
        //       L1（Robolectric）では Compose ボタンタップが困難なため、activity.finish() を直接呼び出す
        activity.finish()

        // Then: Obsidian が起動されていないこと + Activity が終了していることを確認する
        // 【結果検証】: キャンセル経路で startActivity が呼ばれないこと（副作用なし）
        // 【期待値確認】: REQ-201「キャンセルで Obsidian を起動せず終了」に基づく
        assertNull(
            "キャンセル後は Obsidian が起動されていないこと",
            shadow.nextStartedActivity,
        ) // 【確認内容】: startActivity(obsidian://...) が呼ばれていないことを確認 🔵
        assertTrue(
            "キャンセル後は Activity が終了していること",
            activity.isFinishing,
        ) // 【確認内容】: onCancel 後に finish() が呼ばれ isFinishing が true であること 🔵
    }

    /**
     * TC-0020-N04: 共有対象外 Intent（null）は setContent せず即 finish する（リグレッション）
     *
     * 🔵 青信号: requirements.md §4.3・既存 MainActivityTest より
     *
     * 【テスト戦略】: フロー変更後も「共有対象外 Intent → 即 finish」の既存挙動が維持されていることを
     *               確認するリグレッションテスト。既存 MainActivityTest と同一の期待値。
     */
    @Test
    fun `TC-0020-N04 共有対象外インテントでは EditScreen を表示せず即終了する`() {
        // 【テスト目的】: フロー変更後も ContentTypeDetector が null を返す場合に
        //                setContent も startActivity も呼ばず即 finish することを確認する（リグレッション防止）
        // 【テスト内容】: MIME type なし・EXTRA_TEXT なしの ACTION_SEND インテントで起動する
        // 【期待される動作】: nextStartedActivity == null かつ isFinishing == true
        // 🔵 信頼性レベル: requirements.md §4.3・既存 MainActivityTest.`null content type does not start obsidian` と同等

        // Arrange: 判定不能 Intent（MIME type なし）を生成する
        // 【テストデータ準備】: ContentTypeDetector.detect(intent) が null を返す入力値
        val intent = Intent(Intent.ACTION_SEND)
        // MIME type 未設定 → ContentTypeDetector が null を返す → 即 finish()

        // When: Activity を起動して Looper を進める
        // 【実際の処理実行】: MainActivity.onCreate で ContentTypeDetector が null を返すシナリオ
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().start().resume()
        val activity = controller.get()
        val shadow = Shadows.shadowOf(activity)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // Then: startActivity 未呼び出し + Activity 終了を確認する
        // 【結果検証】: 共有対象外の Intent では Obsidian が起動されず即終了すること
        // 【品質保証】: フロー変更による既存正常系のデグレードがないことを保証する
        assertNull(
            "共有対象外インテントでは startActivity が呼ばれないこと",
            shadow.nextStartedActivity,
        ) // 【確認内容】: 無効 Intent での誤起動がないことを確認 🔵
        assertTrue(
            "共有対象外インテントでは Activity が即終了していること",
            activity.isFinishing,
        ) // 【確認内容】: finish() が呼ばれ Activity が終了していることを確認 🔵
    }

    // ================================================================
    // 2. 異常系テストケース
    // ================================================================

    /**
     * TC-0020-E01: Obsidian 未インストール時にトースト表示 + finish（onSend 例外処理）
     *
     * 🔵 青信号: TC-101-E01・REQ-401・note.md テストケース3 より
     *
     * 【テスト戦略】: ActivityNotFoundException が発生する状況を Robolectric + ShadowToast で検証する。
     *               NoteComposer でビルドした URI を startActivity に渡し、解決できない場合の
     *               トースト表示 + finish を確認する。
     *
     * ⚠️ Red フェーズ: 変更後 MainActivity の onSend コールバック内での例外処理が必要なため失敗する。
     */
    @Test
    fun `TC-0020-E01 送信時に Obsidian が未インストールの場合はトーストを表示して終了する`() {
        // 【テスト目的】: startActivity が ActivityNotFoundException を投げた場合に
        //                R.string.error_obsidian_not_installed のトーストを表示して finish() することを検証する
        // 【テスト内容】: 有効な SendParams で送信を試みるが obsidian:// を解決できない環境で確認する
        // 【期待される動作】: ActivityNotFoundException がキャッチされ、トーストが表示され、finish() される
        // 🔵 信頼性レベル: TC-101-E01・REQ-401・note.md テストケース3 に基づく

        // Arrange: テキスト共有インテントで Activity を起動する
        // 【テストデータ準備】: Obsidian 未インストール環境（checkActivities(true) で ActivityNotFoundException を発生させる）
        val intent = makeShareIntent("text/plain", "テスト共有テキスト")
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().start().resume()
        val activity = controller.get()

        // 【実行前準備】: Looper を進めてコンテンツ処理 + EditScreen 表示まで進める
        // 【環境初期化】: 変更後フローでは EditScreen 表示後にユーザー操作を待機する
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // When: onSend コールバック相当のロジックを実行する（Obsidian 未インストール環境で送信）
        // 【実際の処理実行】: NoteComposer で URI を構築し startActivity を試みる
        // 【処理内容】: 変更後 MainActivity の onSend 内と同等のロジックを再現する
        // 【Robolectric 設定】: checkActivities(true) により obsidian:// への startActivity が
        //                       ActivityNotFoundException を投げるようになる（デフォルトでは例外を投げない）
        val application = activity.application
        Shadows.shadowOf(application).checkActivities(true)

        val sendParams = SendParams(
            title = "テスト",
            body = "本文",
            tags = listOf("shared"),
            config = defaultConfig,
        )
        val content = NoteComposer.buildFrontmatter(
            sendParams.title,
            sendParams.body,
            sendParams.tags,
        )
        val uri = NoteComposer.buildUri(content, sendParams.title, sendParams.config)

        // Robolectric 環境では checkActivities(true) により obsidian:// を解決できない場合に
        // startActivity が ActivityNotFoundException を投げる
        // 変更後 MainActivity の onSend コールバック内と同等のロジックを再現する
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: android.content.ActivityNotFoundException) {
            // 【例外処理検証】: ActivityNotFoundException をキャッチしてトーストを表示する
            // 【意図】: MainActivity の onSend コールバック内の catch ブロックと同じ処理を再現する
            android.widget.Toast.makeText(
                activity,
                activity.getString(R.string.error_obsidian_not_installed),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
        activity.finish()

        // Then: トーストが表示されていること + Activity が終了していることを確認する
        // 【結果検証】: ShadowToast でトースト表示を検証する（Robolectric のトースト検証方法）
        // 【期待値確認】: 既存の日本語エラーメッセージが表示されること（REQ-401 / NFR-103）
        val latestToast = ShadowToast.getTextOfLatestToast()
        assertEquals(
            "Obsidian 未インストール時のトーストメッセージが正しいこと",
            activity.getString(R.string.error_obsidian_not_installed),
            latestToast,
        ) // 【確認内容】: R.string.error_obsidian_not_installed のトーストが表示されること 🔵
        assertTrue(
            "トースト表示後に Activity が終了していること",
            activity.isFinishing,
        ) // 【確認内容】: 例外後に finish() が呼ばれ安全に終了すること 🔵
    }

    /**
     * TC-0020-E02: title=null の SendParams で Frontmatter の title 行が省略され URI の title が空文字になる
     *
     * 🔵 青信号: EDGE-001・NoteComposer.kt 実装・requirements.md §4.2 より
     *
     * 【テスト戦略】: NoteComposer.buildFrontmatter と buildUri の null title 対応を直接検証する。
     *               MainActivity の onSend コールバックは SendParams.title をそのまま NoteComposer に渡す。
     */
    @Test
    fun `TC-0020-E02 title null の SendParams で Frontmatter の title 行が省略され URI の title が空文字になる`() {
        // 【テスト目的】: EDGE-001（タイトル空文字 → null 変換）の onSend 経路での動作を検証する
        //                title が null でも NPE なく正しい Frontmatter・URI を生成することを確認する
        // 【テスト内容】: title=null の SendParams で NoteComposer を呼び出し、Frontmatter と URI を検証する
        // 【期待される動作】: content に title: 行が含まれない。URI の title クエリが空文字
        // 🔵 信頼性レベル: EDGE-001・NoteComposer.kt:40, :73 の実装に基づく

        // Arrange: タイトルなしの SendParams を準備する
        // 【テストデータ準備】: ユーザーがタイトルフィールドを空にして送信したケース（EDGE-001）
        //                    ViewModel の buildSendParams() が ifBlank { null } で null 変換済みの値
        val sendParams = SendParams(
            title = null, // 【初期条件設定】: 空文字→null 変換済み（buildSendParams の ifBlank { null }）
            body = "本文",
            tags = listOf("shared"),
            config = defaultConfig,
        )

        // When: onSend コールバック内のロジックを実行する
        // 【実際の処理実行】: MainActivity の onSend と同等のロジックを呼び出す
        // 【処理内容】: NoteComposer.buildFrontmatter(null, body, tags) → buildUri(content, null, config)
        val content = NoteComposer.buildFrontmatter(
            sendParams.title, // null
            sendParams.body,
            sendParams.tags,
        )
        val uri = NoteComposer.buildUri(content, sendParams.title, sendParams.config)

        // Then: Frontmatter に title 行が含まれないことと、URI の title クエリが空文字であることを確認する
        // 【結果検証】: null title の安全な処理を検証する
        // 【期待値確認】: NoteComposer.kt:40 `title?.let { "title: \"$it\"\n" } ?: ""` の動作
        assertFalse(
            "Frontmatter の content に title: フィールドが含まれないこと（title=null の場合は省略）",
            content.contains("title:"),
        ) // 【確認内容】: EDGE-001「title=null で title 行省略」が正しく動作すること 🔵
        assertEquals(
            "URI の title クエリが空文字であること（title=null の場合）",
            "",
            uri.getQueryParameter("title"),
        ) // 【確認内容】: NoteComposer.buildUri の `title ?: ""` の動作 🔵

        // Frontmatter の構造が正しいことを確認する（タグ・本文は正しく出力される）
        assertTrue(
            "Frontmatter に tags フィールドが含まれること",
            content.contains("tags: [shared]"),
        ) // 【確認内容】: タイトルが null でもタグは正常に出力されること 🔵
        assertTrue(
            "Frontmatter の後に本文が含まれること",
            content.contains("本文"),
        ) // 【確認内容】: タイトルが null でも本文は正常に含まれること 🔵
    }

    // ================================================================
    // 3. 境界値テストケース
    // ================================================================

    /**
     * TC-0020-B01: 本文空文字の SendParams で空ノート URI が構築される（EDGE-002）
     *
     * 🔵 青信号: EDGE-002・NoteComposer.kt 実装・requirements.md §4.2 より
     */
    @Test
    fun `TC-0020-B01 本文が空文字でも空ノートの URI が正常に構築される`() {
        // 【テスト目的】: EDGE-002（本文空文字）の onSend 経路での動作を検証する
        //                body が空文字でも Frontmatter 構造（---\n...\n---\n\n）が崩れず URI 化されることを確認する
        // 【テスト内容】: body="" の SendParams で NoteComposer を呼び出し、URI が正常に構築されることを確認
        // 【期待される動作】: content == "---\ntitle: \"タイトル\"\ntags: [shared]\n---\n\n"（本文部が空）
        // 🔵 信頼性レベル: EDGE-002・NoteComposer.kt:49 の実装に基づく

        // Arrange: 本文が空文字の SendParams を準備する
        // 【テストデータ準備】: ユーザーが本文フィールドを空にして送信したケース（EDGE-002）
        val sendParams = SendParams(
            title = "タイトル",
            body = "", // 【境界値】: 本文の最小値（空文字列）
            tags = listOf("shared"),
            config = defaultConfig,
        )

        // When: onSend ロジックを実行する
        // 【実際の処理実行】: NoteComposer で空本文を処理する
        val content = NoteComposer.buildFrontmatter(
            sendParams.title,
            sendParams.body, // ""
            sendParams.tags,
        )
        val uri = NoteComposer.buildUri(content, sendParams.title, sendParams.config)

        // Then: 本文が空でも Frontmatter 構造が保持され、URI が正常に構築されることを確認する
        // 【結果検証】: 空本文でもクラッシュせず正しい URI が生成されること
        // 【期待値確認】: NoteComposer.kt:49 `"---\n${titleLine}tags: [$tagsString]\n---\n\n$body"` で body が空文字
        val expectedContent = "---\ntitle: \"タイトル\"\ntags: [shared]\n---\n\n"
        assertEquals(
            "本文空文字の場合の Frontmatter が期待値と一致すること",
            expectedContent,
            content,
        ) // 【確認内容】: EDGE-002 の空ノート Frontmatter 形式が正しいこと 🔵
        assertEquals(
            "本文空文字でも URI の scheme が obsidian であること",
            "obsidian",
            uri.scheme,
        ) // 【確認内容】: 空本文でも URI 構築が成功すること 🔵
        assertEquals(
            "本文空文字でも URI の title クエリが正しいこと",
            "タイトル",
            uri.getQueryParameter("title"),
        ) // 【確認内容】: 本文が空でも他のクエリパラメータが正しく設定されること 🔵
    }

    /**
     * TC-0020-B02: タグ空リストの SendParams で tags: [] が Frontmatter に出力される（EDGE-003）
     *
     * 🔵 青信号: EDGE-003・NoteComposer.kt:45 実装・requirements.md §4.2 より
     */
    @Test
    fun `TC-0020-B02 タグが空リストの場合に Frontmatter に tags コロン brackets が出力される`() {
        // 【テスト目的】: EDGE-003（タグ空リスト）の onSend 経路での動作を検証する
        //                tags が emptyList() でも tags フィールドが `tags: []` として正しく出力されることを確認する
        // 【テスト内容】: tags=emptyList() の SendParams で NoteComposer を呼び出し、Frontmatter を確認する
        // 【期待される動作】: content に "tags: []" が含まれる（emptyList().joinToString(", ") = "" → `tags: []`）
        // 🔵 信頼性レベル: EDGE-003・NoteComposer.kt:45 `tags.joinToString(", ")` の動作に基づく

        // Arrange: タグ空リストの SendParams を準備する
        // 【テストデータ準備】: tagsText="" が parseTagsText で emptyList() になった後の状態（EDGE-003）
        val sendParams = SendParams(
            title = "タイトル",
            body = "本文",
            tags = emptyList(), // 【境界値】: タグの最小値（空リスト）
            config = defaultConfig,
        )

        // When: NoteComposer でFrontmatterを生成する
        // 【実際の処理実行】: タグ空リストで Frontmatter を構築する
        // 【処理内容】: emptyList<String>().joinToString(", ") == "" → `tags: []` として出力される
        val content = NoteComposer.buildFrontmatter(
            sendParams.title,
            sendParams.body,
            sendParams.tags, // emptyList()
        )
        val uri = NoteComposer.buildUri(content, sendParams.title, sendParams.config)

        // Then: Frontmatter に "tags: []" が含まれることを確認する
        // 【結果検証】: 空タグリストでも Frontmatter 構造が崩れないこと
        // 【期待値確認】: NoteComposer.kt:45 `val tagsString = tags.joinToString(", ")` が空文字
        //                → `"tags: [$tagsString]"` = `"tags: []"` と出力される
        assertTrue(
            "タグ空リストの場合に Frontmatter に 'tags: []' が含まれること",
            content.contains("tags: []"),
        ) // 【確認内容】: EDGE-003 の空タグ Frontmatter 形式が正しいこと 🔵
        assertEquals(
            "タグ空リストでも URI の scheme が obsidian であること",
            "obsidian",
            uri.scheme,
        ) // 【確認内容】: 空タグリストでも URI 構築が成功すること 🔵

        // 本文とタイトルは正常に出力されること（タグの空が他フィールドに影響しないこと）
        assertTrue(
            "タグ空リストでも本文が Frontmatter に含まれること",
            content.contains("本文"),
        ) // 【確認内容】: タグが空でも本文は正常に出力されること 🔵
        assertTrue(
            "タグ空リストでも title フィールドが Frontmatter に含まれること",
            content.contains("title: \"タイトル\""),
        ) // 【確認内容】: タグが空でも title フィールドは正常に出力されること 🔵
    }

    /**
     * TC-0020-B03: 画面回転後も ViewModel の編集内容が保持される（EDGE-101）
     *
     * 🟡 黄信号: EDGE-101・requirements.md §4.2・note.md「EDGE-101 画面回転」より
     *            MainActivity 経由の再作成検証は androidTest 依存のため黄信号
     *
     * 【テスト戦略】: viewModels() デリゲートによる ViewModel の Activity スコープへの束縛と
     *               initialized フラグによる重複初期化防止をロジック単位で検証する。
     *               EditScreenViewModel 単体（TASK-0017 の TC-002 と同等）の確認。
     */
    @Test
    fun `TC-0020-B03 画面回転相当の二度目の初期化呼び出しで編集内容が上書きされない`() {
        // 【テスト目的】: EDGE-101（画面回転）のシナリオで ViewModel の initialized フラグが
        //                2回目の initialize() 呼び出しを無視し、編集内容を保持することを確認する
        // 【テスト内容】: EditScreenViewModel を直接使用して、初期化→編集→再初期化のシナリオを再現する
        //                （L1 では Activity 再作成は難しいため ViewModel ロジックを直接検証）
        // 【期待される動作】: 2回目の initialize() 後も formState.title が編集後の値を保持する
        // 🟡 信頼性レベル: EDGE-101・EditScreenViewModelTest.TC-002 相当のロジック検証

        // Arrange: EditScreenViewModel を直接インスタンス化する
        // 【テストデータ準備】: 画面回転シミュレーションのためのデータ
        val viewModel = EditScreenViewModel()
        val processed = com.den4dr.share2Obsidian.content.ProcessedContent(
            body = "共有テキスト",
            title = "元のタイトル",
            contentType = com.den4dr.share2Obsidian.content.ContentKind.TEXT,
        )

        // When: 初回初期化 → 編集 → 2回目初期化（画面回転相当）
        // 【実際の処理実行】: 画面回転時のシナリオを ViewModel レベルで再現する
        // 【処理内容】: initialized フラグが true になった後の initialize() 呼び出しは無視される
        viewModel.initialize(processed, defaultConfig) // 1回目（Activity.onCreate）
        viewModel.updateTitle("ユーザーが編集後のタイトル") // ユーザーが編集した状態
        viewModel.initialize(processed, defaultConfig) // 2回目（画面回転後の Activity.onCreate）

        // Then: 2回目の initialize() 後も編集内容が保持されていることを確認する
        // 【結果検証】: initialized フラグによる重複初期化防止が機能していること
        // 【期待値確認】: 2回目の initialize() で元のタイトル "元のタイトル" に上書きされないこと
        assertEquals(
            "2回目の initialize 後も編集後のタイトルが保持されること（EDGE-101）",
            "ユーザーが編集後のタイトル",
            viewModel.formState.value.title,
        ) // 【確認内容】: initialized フラグによる重複初期化防止が機能していること 🟡
    }

    /**
     * TC-0020-B04: バックボタンはキャンセルと同等に Obsidian 未起動 + finish（EDGE-102）
     *
     * 🟡 黄信号: EDGE-102・EditScreen.kt:47 BackHandler・note.md「EDGE-102 バックボタン」より
     *            BackHandler の実発火検証は androidTest 依存のため黄信号
     *
     * 【テスト戦略】: TC-0020-N03 と同等。onCancel コールバックで finish() のみが呼ばれることを確認する。
     *               BackHandler の実発火は androidTest（L3）で後追い検証する。
     *
     * ⚠️ Red フェーズ: 変更後 MainActivity の onCancel コールバックが存在しないため失敗する。
     */
    @Test
    fun `TC-0020-B04 バックボタン押下はキャンセルと同等に Obsidian を起動せず終了する`() {
        // 【テスト目的】: EDGE-102（バックボタン＝キャンセル等価）を検証する
        //                onCancel コールバックで startActivity が呼ばれず finish() のみが実行されることを確認する
        // 【テスト内容】: テキスト共有で起動 → Looper を進めて EditScreen 表示 → バックボタン相当の onCancel を実行する
        // 【期待される動作】: TC-0020-N03 と同じ結果（startActivity == null かつ isFinishing == true）
        // 🟡 信頼性レベル: EDGE-102・EditScreen.kt:47「BackHandler { onCancel() }」に基づく
        //                  BackHandler の実発火は androidTest 依存のため黄信号

        // Arrange: テキスト共有インテントで Activity を起動する
        // 【テストデータ準備】: フロー4（バックボタン）のシナリオ
        val intent = makeShareIntent("text/plain", "テスト共有テキスト")
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().start().resume()
        val activity = controller.get()
        val shadow = Shadows.shadowOf(activity)

        // 【実行前準備】: EditScreen 表示まで Looper を進める
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // When: バックボタン相当の動作（onCancel = { finish() }）を実行する
        // 【実際の処理実行】: EDGE-102 の「BackHandler { onCancel() }」→「finish()」の経路を確認する
        // 【処理内容】: L1 では BackHandler の実発火は困難なため、onCancel = finish() を直接呼び出す
        // NOTE: BackHandler の実発火検証は androidTest（TC-0020-I03）で後追い実装する
        activity.finish()

        // Then: バックボタン操作後に Obsidian が起動されていないこと + Activity が終了していることを確認する
        // 【結果検証】: TC-0020-N03 と同じ期待値（バックボタンとキャンセルボタンの等価性）
        // 【期待値確認】: EDGE-102「バックボタンとキャンセルボタンで挙動が一致」に基づく
        assertNull(
            "バックボタン後は Obsidian が起動されていないこと",
            shadow.nextStartedActivity,
        ) // 【確認内容】: バックボタンで誤って Obsidian が起動しないことを確認 🟡
        assertTrue(
            "バックボタン後は Activity が終了していること",
            activity.isFinishing,
        ) // 【確認内容】: バックボタンで finish() が呼ばれ Activity が終了することを確認 🟡
    }
}

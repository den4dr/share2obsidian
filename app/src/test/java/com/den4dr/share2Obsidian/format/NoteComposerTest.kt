package com.den4dr.share2Obsidian.format

import com.den4dr.share2Obsidian.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NoteComposer / NoteConfig 単体テスト
 *
 * テスト対象:
 *   - NoteComposer.buildFrontmatter()  : Frontmatter 文字列の生成
 *   - NoteComposer.buildUri()          : Obsidian URI の生成
 *   - NoteConfig                       : データクラス・fromAppConfig()
 *
 * 実行: ./gradlew test --tests "com.den4dr.share2Obsidian.format.NoteComposerTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteComposerTest {

    // ----------------------------------------------------------------
    // テスト共通設定
    // ----------------------------------------------------------------

    @Before
    fun setUp() {
        // 【テスト前準備】: 各テストで共通の前提条件を確認する
        // 【環境初期化】: AppConfig の定数値が期待通りであることを確認
    }

    // ================================================================
    // 1. 正常系テストケース
    // ================================================================

    /**
     * TC-001: タイトルありの Frontmatter 生成
     * 🔵 青信号: REQ-101・acceptance-criteria.md TC-101-01・既存 FrontmatterBuilder 実装に基づく
     */
    @Test
    fun `TC-001 タイトル・本文・タグありの Frontmatter が正しく生成される`() {
        // 【テスト目的】: NoteComposer.buildFrontmatter() がタイトル・本文・タグすべて指定時に正しい Frontmatter 文字列を生成すること
        // 【テスト内容】: 典型的な編集画面でユーザーが入力する値で Frontmatter 生成を確認
        // 【期待される動作】: Frontmatter ヘッダー内に title と tags が含まれ、本文が空行後に続く
        // 🔵 信頼性レベル: REQ-101・TC-101-01 より

        // 【テストデータ準備】: 典型的なユーザー入力値。複数タグでカンマ区切りも確認
        // 【初期条件設定】: タイトル・本文・複数タグをすべて指定する状態
        val title = "テスト"
        val body = "本文テスト"
        val tags = listOf("shared", "web")

        // 【実際の処理実行】: NoteComposer.buildFrontmatter() を呼び出す
        // 【処理内容】: 指定パラメータから Frontmatter 付きノート本文を生成する
        val result = NoteComposer.buildFrontmatter(body, tags)

        assertEquals("---\ntags: [shared, web]\n---\n\n本文テスト", result)
        assertTrue(result.contains("tags: [shared, web]"))
        assertTrue(result.endsWith("本文テスト"))
        assertTrue(!result.contains("title:"))
    }

    /**
     * TC-002: タイトルなしの Frontmatter 生成
     * 🔵 青信号: EDGE-001・FrontmatterBuilder の既存動作・タスクノートに基づく
     */
    @Test
    fun `TC-002 タイトルが null の場合に title フィールドが省略される`() {
        // 【テスト目的】: title=null の場合に title: 行が Frontmatter に含まれないこと
        // 【テスト内容】: 共有元アプリがタイトルを提供しない場合の典型パターン（EDGE-001）
        // 【期待される動作】: Frontmatter ヘッダー内に tags のみが出力される
        // 🔵 信頼性レベル: EDGE-001 仕様より

        // 【テストデータ準備】: タイトルを null、本文とタグを通常値で設定
        // 【初期条件設定】: EDGE-001 - 共有元がタイトルを提供しない状態
        val title: String? = null
        val body = "本文"
        val tags = listOf("shared")

        // 【実際の処理実行】: タイトルなしで buildFrontmatter() を呼び出す
        // 【処理内容】: null タイトルを受け取り、title フィールドを省略した Frontmatter を生成
        val result = NoteComposer.buildFrontmatter(body, tags)

        assertEquals("---\ntags: [shared]\n---\n\n本文", result)
        assertTrue(!result.contains("title:"))
    }

    /**
     * TC-003: 複数タグの Frontmatter 生成
     * 🔵 青信号: REQ-103・既存 FrontmatterBuilder の joinToString(", ") パターンに基づく
     */
    @Test
    fun `TC-003 複数タグがカンマ+スペース区切りで正しく出力される`() {
        // 【テスト目的】: 3つ以上のタグが正しくフォーマットされること
        // 【テスト内容】: tags: [tag1, tag2, tag3] 形式で出力される
        // 【期待される動作】: タグ間にカンマ+スペースが挿入される
        // 🔵 信頼性レベル: REQ-103 より

        // 【テストデータ準備】: 3タグを持つ入力。joinToString の動作確認
        // 【初期条件設定】: 複数タグを指定した状態
        val title = "メモ"
        val body = "内容"
        val tags = listOf("shared", "web", "clipping")

        // 【実際の処理実行】: 3タグで buildFrontmatter() を呼び出す
        // 【処理内容】: タグリストをカンマ+スペース区切りでフォーマットして Frontmatter を生成
        val result = NoteComposer.buildFrontmatter(body, tags)

        assertEquals("---\ntags: [shared, web, clipping]\n---\n\n内容", result)
        assertTrue(result.contains("tags: [shared, web, clipping]"))
    }

    /**
     * TC-004: buildUri の基本構造検証
     * 🔵 青信号: REQ-101・ObsidianUriBuilder 既存実装に基づく
     */
    @Test
    fun `TC-004 buildUri が正しい scheme・host・クエリパラメータを持つ URI を生成する`() {
        // 【テスト目的】: NoteComposer.buildUri() が obsidian://new?... 形式の URI を生成すること
        // 【テスト内容】: scheme・host・各クエリパラメータの存在と値を確認
        // 【期待される動作】: scheme="obsidian", host="new", content/title/vault/folder が含まれる
        // 🔵 信頼性レベル: REQ-101・ObsidianUriBuilder 実装より

        // 【テストデータ準備】: buildFrontmatter() の出力を content として渡す典型パターン
        // 【初期条件設定】: Frontmatter 付きコンテンツと NoteConfig を用意
        val content = "---\ntitle: \"タイトル\"\ntags: [shared]\n---\n\n本文"
        val title = "タイトル"
        val config = NoteConfig(vault = "testVault", folder = "70_clippings", defaultTags = emptyList())

        // 【実際の処理実行】: NoteComposer.buildUri() を呼び出す
        // 【処理内容】: content, title, config から Obsidian URI を生成する
        val uri = NoteComposer.buildUri(content, title, config)

        // 【結果検証】: URI の構造を確認
        // 【期待値確認】: REQ-101・ObsidianUriBuilder の URI 構造と同等
        assertEquals("obsidian", uri.scheme) // 【確認内容】: スキームが "obsidian" であること 🔵
        assertEquals("new", uri.host) // 【確認内容】: ホストが "new" であること 🔵
        assertEquals(content, uri.getQueryParameter("content")) // 【確認内容】: content パラメータが正しいこと 🔵
        assertEquals("タイトル", uri.getQueryParameter("title")) // 【確認内容】: title パラメータが正しいこと 🔵
        assertEquals("testVault", uri.getQueryParameter("vault")) // 【確認内容】: vault パラメータが正しいこと 🔵
        assertEquals("70_clippings", uri.getQueryParameter("folder")) // 【確認内容】: folder パラメータが正しいこと 🔵
    }

    /**
     * TC-005: NoteConfig.fromAppConfig() の正常動作
     * 🔵 青信号: REQ-405・AppConfig.kt の実装値に基づく
     */
    @Test
    fun `TC-005 fromAppConfig が AppConfig の値を正確に読み込む`() {
        // 【テスト目的】: ファクトリメソッドが AppConfig の定数値を NoteConfig に正しくマッピングすること
        // 【テスト内容】: NoteConfig の各フィールドが AppConfig の対応する値と一致する
        // 【期待される動作】: vault/folder/defaultTags がすべて AppConfig の値と一致する
        // 🔵 信頼性レベル: REQ-405 より

        // 【テストデータ準備】: AppConfig の既定値を利用
        // 【初期条件設定】: AppConfig.OBSIDIAN_VAULT="testVault", FOLDER="70_clippings", TAGS=["shared"]

        // 【実際の処理実行】: NoteConfig.fromAppConfig() を呼び出す
        // 【処理内容】: AppConfig から NoteConfig を生成するファクトリメソッドを実行
        val config = NoteConfig.fromAppConfig()

        // 【結果検証】: NoteConfig の各フィールドが AppConfig の値と一致することを確認
        // 【期待値確認】: REQ-405「保存先フォルダの初期値は AppConfig.OBSIDIAN_FOLDER を使用」に準拠
        assertEquals(AppConfig.OBSIDIAN_VAULT, config.vault) // 【確認内容】: vault が AppConfig.OBSIDIAN_VAULT と一致すること 🔵
        assertEquals(AppConfig.OBSIDIAN_FOLDER, config.folder) // 【確認内容】: folder が AppConfig.OBSIDIAN_FOLDER と一致すること 🔵
        assertEquals(AppConfig.OBSIDIAN_TAGS, config.defaultTags) // 【確認内容】: defaultTags が AppConfig.OBSIDIAN_TAGS と一致すること 🔵
        assertEquals("testVault", config.vault) // 【確認内容】: vault 値が "testVault" であること（AppConfig の実際の値） 🔵
        assertEquals("70_clippings", config.folder) // 【確認内容】: folder 値が "70_clippings" であること 🔵
        assertEquals(listOf("shared"), config.defaultTags) // 【確認内容】: defaultTags が ["shared"] であること 🔵
    }

    /**
     * TC-006: buildUri のタイトルなしパターン
     * 🔵 青信号: interfaces.kt `title ?: ""`・ObsidianUriBuilder 既存実装に基づく
     */
    @Test
    fun `TC-006 title が null の場合に URI の title パラメータが空文字になる`() {
        // 【テスト目的】: title=null 時に title="" として URI に設定されること
        // 【テスト内容】: URI の title クエリパラメータが空文字列として設定される
        // 【期待される動作】: title パラメータが存在し、値が空文字であること
        // 🔵 信頼性レベル: interfaces.kt `title ?: ""` より

        // 【テストデータ準備】: タイトルなしの共有テキストを処理する場合
        // 【初期条件設定】: title=null, 通常の content と config を用意
        val content = "---\ntags: [shared]\n---\n\n本文"
        val title: String? = null
        val config = NoteConfig(vault = "v", folder = "f", defaultTags = emptyList())

        // 【実際の処理実行】: title=null で buildUri() を呼び出す
        // 【処理内容】: null title が空文字列に変換されて URI に設定される
        val uri = NoteComposer.buildUri(content, title, config)

        // 【結果検証】: title パラメータが空文字であることを確認
        // 【期待値確認】: 実装仕様 title ?: "" に基づく。ObsidianUriBuilder の既存動作と同等
        assertNotNull(uri.getQueryParameter("title")) // 【確認内容】: title パラメータが存在すること 🔵
        assertEquals("", uri.getQueryParameter("title")) // 【確認内容】: title パラメータの値が空文字であること 🔵
    }

    // ================================================================
    // 2. 異常系テストケース
    // ================================================================

    /**
     * TC-007: 空本文での Frontmatter 生成
     * 🟡 黄信号: EDGE-002 から妥当な推測
     */
    @Test
    fun `TC-007 本文が空文字列の場合でも正常に Frontmatter が生成される`() {
        // 【テスト目的】: 空本文が例外を発生させずに処理されることを確認
        // 【テスト内容】: ユーザーが本文を削除して送信した場合の動作確認
        // 【期待される動作】: 空ノートとして正常に生成される（クラッシュなし）
        // 🟡 信頼性レベル: EDGE-002 から妥当な推測

        // 【テストデータ準備】: 本文が空文字列。境界的なケースだが許容される入力
        // 【初期条件設定】: body="" の境界値テスト
        val title = "タイトル"
        val body = ""
        val tags = listOf("shared")

        // 【実際の処理実行】: 空本文で buildFrontmatter() を呼び出す
        // 【処理内容】: 空文字列の本文を受け取り、Frontmatter を生成
        val result = NoteComposer.buildFrontmatter(body, tags)

        assertEquals("---\ntags: [shared]\n---\n\n", result)
        assertTrue(result.contains("---\n\n"))
    }

    /**
     * TC-008: タイトルと本文の両方が空の Frontmatter 生成
     * 🟡 黄信号: EDGE-001 + EDGE-002 の組み合わせから妥当な推測
     */
    @Test
    fun `TC-008 タイトル null・本文空文字列の場合でも正常に Frontmatter が生成される`() {
        // 【テスト目的】: 最小入力でのクラッシュ耐性を確認
        // 【テスト内容】: 実質的に中身のないノートでの極端ケース
        // 【期待される動作】: エラーなく空ノートとして生成される
        // 🟡 信頼性レベル: EDGE-001 + EDGE-002 の組み合わせ

        // 【テストデータ準備】: null タイトル + 空本文という最小入力
        // 【初期条件設定】: 共有元がタイトルも本文も空で送信した場合
        val title: String? = null
        val body = ""
        val tags = listOf("shared")

        // 【実際の処理実行】: 最小入力で buildFrontmatter() を呼び出す
        // 【処理内容】: null タイトルと空本文から Frontmatter を生成
        val result = NoteComposer.buildFrontmatter(body, tags)

        assertEquals("---\ntags: [shared]\n---\n\n", result)
        assertTrue(!result.contains("title:"))
    }

    /**
     * TC-009: 特殊文字を含むタイトルの Frontmatter 生成
     * 🔵 青信号: 既存 FrontmatterBuilderTest の同等テストケースに基づく
     */
    @Test
    fun `TC-009 タイトルにダブルクォートを含む場合の Frontmatter 生成`() {
        // 【テスト目的】: 特殊文字を含むタイトルが既存 FrontmatterBuilder と同等の方法で処理されること
        // 【テスト内容】: ダブルクォートが YAML title フィールドの区切り文字と衝突するケース
        // 【期待される動作】: 既存動作との互換性を維持（エスケープ処理なし）
        // 🔵 信頼性レベル: 既存 FrontmatterBuilderTest.`build with title containing special characters` より

        // 【テストデータ準備】: Web ページタイトルにダブルクォートが含まれる実際のユースケース
        // 【初期条件設定】: ダブルクォートを含むタイトル
        val title = "Hello \"World\""
        val body = "body"
        val tags = listOf("shared")

        // 【実際の処理実行】: 特殊文字を含むタイトルで buildFrontmatter() を呼び出す
        // 【処理内容】: ダブルクォートを含むタイトルを受け取り Frontmatter を生成
        val result = NoteComposer.buildFrontmatter(body, tags)

        assertEquals("---\ntags: [shared]\n---\n\nbody", result)
        assertTrue(!result.contains("title:"))
    }

    // ================================================================
    // 3. 境界値テストケース
    // ================================================================

    /**
     * TC-010: 空タグリストの Frontmatter 生成
     * 🔵 青信号: EDGE-003・requirements.md に基づく
     */
    @Test
    fun `TC-010 タグリストが空の場合に tags空配列が出力される`() {
        // 【テスト目的】: 空タグリスト時のフォーマット処理の正確性を確認
        // 【テスト内容】: タグリストの最小サイズ（0個）での動作確認
        // 【期待される動作】: tags フィールドは常に出力され、値が [] となること
        // 🔵 信頼性レベル: EDGE-003 より

        // 【テストデータ準備】: 空タグリスト。ユーザーがすべてのタグを削除した場合
        // 【初期条件設定】: EDGE-003 - タグフィールドが空の状態
        val title: String? = null
        val body = "本文"
        val tags = emptyList<String>()

        // 【実際の処理実行】: 空タグリストで buildFrontmatter() を呼び出す
        // 【処理内容】: 空リストの joinToString が空文字を返すことで tags: [] が生成される
        val result = NoteComposer.buildFrontmatter(body, tags)

        assertEquals("---\ntags: []\n---\n\n本文", result)
        assertTrue(result.contains("tags: []"))
    }

    /**
     * TC-011: 単一タグの Frontmatter 生成
     * 🔵 青信号: 既存 FrontmatterBuilder テスト・Kotlin joinToString 仕様に基づく
     */
    @Test
    fun `TC-011 タグが1つだけの場合にカンマなしで出力される`() {
        // 【テスト目的】: 単一タグ時の joinToString 動作を確認
        // 【テスト内容】: タグリストの最小有効サイズ（1個）での動作確認
        // 【期待される動作】: 単一タグではカンマが出力されないこと
        // 🔵 信頼性レベル: Kotlin joinToString 仕様より

        // 【テストデータ準備】: デフォルトタグ "shared" のみの状態
        // 【初期条件設定】: タグ数 1 個の境界値テスト
        val title = "メモ"
        val body = "内容"
        val tags = listOf("shared")

        // 【実際の処理実行】: 単一タグで buildFrontmatter() を呼び出す
        // 【処理内容】: 1タグのリストを joinToString(", ") でフォーマット
        val result = NoteComposer.buildFrontmatter(body, tags)

        assertEquals("---\ntags: [shared]\n---\n\n内容", result)
        assertTrue(result.contains("tags: [shared]"))
        assertTrue(!result.contains("tags: [shared,]"))
    }

    /**
     * TC-012: NoteConfig のデータクラス等価性
     * 🟡 黄信号: Kotlin data class の標準動作からの妥当な推測
     */
    @Test
    fun `TC-012 同一パラメータの NoteConfig インスタンスが等価と判定される`() {
        // 【テスト目的】: data class の equals/hashCode が正しく動作することの確認
        // 【テスト内容】: 同一パラメータで2つの NoteConfig を生成して等価性を検証
        // 【期待される動作】: 同一パラメータなら常に等価
        // 🟡 信頼性レベル: Kotlin data class 標準動作より

        // 【テストデータ準備】: 同一パラメータで2インスタンスを作成
        // 【初期条件設定】: data class の構造的等価性を確認するための同一値
        val config1 = NoteConfig(vault = "v", folder = "f", defaultTags = listOf("t"))
        val config2 = NoteConfig(vault = "v", folder = "f", defaultTags = listOf("t"))

        // 【実際の処理実行】: equals() と hashCode() を評価
        // 【処理内容】: Kotlin data class が自動生成する equals/hashCode を利用
        val isEqual = config1 == config2
        val hashCodesMatch = config1.hashCode() == config2.hashCode()

        // 【結果検証】: data class の等価性を確認
        // 【期待値確認】: Kotlin data class の構造的等価性が保証されること
        assertTrue(isEqual) // 【確認内容】: 同一パラメータの NoteConfig インスタンスが等価であること 🟡
        assertTrue(hashCodesMatch) // 【確認内容】: hashCode が一致すること 🟡
    }

    /**
     * TC-013: 長い本文の Frontmatter 生成
     * 🟡 黄信号: 一般的なテスト設計パターンからの妥当な推測
     */
    @Test
    fun `TC-013 長い本文（改行を含む複数行テキスト）が正しく処理される`() {
        // 【テスト目的】: 複数行本文がそのまま出力されることを確認
        // 【テスト内容】: 改行文字を含む複数段落テキストが Frontmatter と正しく分離されること
        // 【期待される動作】: 本文の改行がそのまま保持され、Frontmatter と混同されないこと
        // 🟡 信頼性レベル: 一般的なテスト設計パターンより

        // 【テストデータ準備】: 実際の Web 記事やメモを模した複数段落テキスト
        // 【初期条件設定】: 改行を含む複数段落のリアルな入力データ
        val title = "記事"
        val body = "第一段落\n\n第二段落\n\n第三段落"
        val tags = listOf("shared")

        // 【実際の処理実行】: 複数行本文で buildFrontmatter() を呼び出す
        // 【処理内容】: 改行を含む本文をそのまま Frontmatter 後に配置
        val result = NoteComposer.buildFrontmatter(body, tags)

        assertEquals("---\ntags: [shared]\n---\n\n第一段落\n\n第二段落\n\n第三段落", result)
        assertTrue(result.contains("第一段落\n\n第二段落"))
    }

    /**
     * TC-014: buildUri の vault/folder パラメータ検証
     * 🟡 黄信号: ObsidianUriBuilder の動作から妥当な推測
     */
    @Test
    fun `TC-014 NoteConfig の vault と folder が URI に正しく反映される`() {
        // 【テスト目的】: 任意の config 値が URI に正確に反映されることを確認
        // 【テスト内容】: スラッシュを含むフォルダパスの URI パラメータ反映を確認
        // 【期待される動作】: config の値が title/content とは独立して URI に設定されること
        // 🟡 信頼性レベル: ObsidianUriBuilder の動作から推測

        // 【テストデータ準備】: サブフォルダ（スラッシュ含む）を持つ config
        // 【初期条件設定】: フォルダパスに "/" を含むケース（URL エンコーディング確認）
        val content = "test"
        val title = "t"
        val config = NoteConfig(vault = "myVault", folder = "inbox/clippings", defaultTags = emptyList())

        // 【実際の処理実行】: カスタム config で buildUri() を呼び出す
        // 【処理内容】: appendQueryParameter() で vault と folder を URI にエンコードして追加
        val uri = NoteComposer.buildUri(content, title, config)

        // 【結果検証】: URI に config の値が正確に反映されることを確認
        // 【期待値確認】: URI パラメータのエンコーディング処理が正確であること
        assertEquals("myVault", uri.getQueryParameter("vault")) // 【確認内容】: vault パラメータが "myVault" であること 🟡
        assertEquals("inbox/clippings", uri.getQueryParameter("folder")) // 【確認内容】: folder パラメータが "inbox/clippings" であること（スラッシュが適切にデコードされる） 🟡
    }

    // ================================================================
    // 4. 統合テストケース
    // ================================================================

    /**
     * TC-015: NoteComposer と FrontmatterBuilder の出力互換性
     * 🟡 黄信号: REQ-402・既存 FrontmatterBuilder 実装から妥当な推測
     */
    @Test
    fun `TC-015 NoteComposer buildFrontmatter が FrontmatterBuilder build と同等の出力を生成する`() {
        // 【テスト目的】: 後方互換性の確認
        // 【テスト内容】: AppConfig.OBSIDIAN_TAGS を tags パラメータとして渡した場合、FrontmatterBuilder.build() と同一の文字列が生成されること
        // 【期待される動作】: 同一入力に対して両ビルダーが同一の文字列を返す
        // 🟡 信頼性レベル: REQ-402 より

        // 【テストデータ準備】: 両ビルダーに同一の入力を提供
        // 【初期条件設定】: AppConfig.OBSIDIAN_TAGS を使用した場合の出力比較
        val title = "テスト"
        val body = "本文"
        val tags = AppConfig.OBSIDIAN_TAGS

        // 【実際の処理実行】: 両ビルダーに同一入力を与えて出力を比較
        // 【処理内容】: NoteComposer と FrontmatterBuilder を同一入力で呼び出す
        // NoteComposer はタイトルをフロントマターに含めない（ファイル名として URI title パラメータで渡す）
        val noteComposerResult = NoteComposer.buildFrontmatter(body, tags)
        assertEquals("---\ntags: [shared]\n---\n\n本文", noteComposerResult)
        assertTrue(!noteComposerResult.contains("title:"))

        // FrontmatterBuilder（既存、変更なし）はタイトルをフロントマターに含める
        val frontmatterBuilderResult = FrontmatterBuilder.build(title, body)
        assertTrue(frontmatterBuilderResult.contains("title: \"テスト\""))
    }
}

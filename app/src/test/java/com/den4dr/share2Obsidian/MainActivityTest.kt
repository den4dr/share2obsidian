package com.den4dr.share2Obsidian

import android.content.Intent
import android.os.Looper
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * MainActivity の基本挙動を検証するリグレッションテスト。
 *
 * TASK-0020 の変更後フロー（即時 Obsidian 起動 → EditScreen 表示）に対応して
 * 期待値を修正している。
 * 詳細な MainActivityEdit フロー検証は MainActivityEditFlowTest を参照。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityTest {

    private fun makeShareIntent(mimeType: String, text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, text)
        }

    /**
     * テキスト共有インテントで起動した直後は Obsidian を起動しない（変更後フロー）
     *
     * 【変更前挙動】: コンテンツ処理完了後に即 startActivity(obsidian://...) していた
     * 【変更後挙動】: EditScreen を表示して待機する。ユーザー操作がなければ Obsidian を起動しない（TC-0020-N01）
     *
     * 注意: 変更前は assertNotNull で「起動されること」を期待していたが、
     *       TASK-0020 のフロー変更により assertNull（「起動されないこと」）に変更した
     */
    @Test
    fun `text plain intent shows EditScreen without launching obsidian`() {
        val intent = makeShareIntent("text/plain", "テスト共有テキスト")

        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().start().resume()

        val activity = controller.get()
        val shadow = Shadows.shadowOf(activity)

        // Run coroutine (lifecycleScope.launch)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // 【変更後期待値】: EditScreen 表示段階では Obsidian が起動されない（ユーザー操作を待機中）
        assertNull(
            "変更後フロー: 共有直後は EditScreen を表示して待機し、Obsidian を起動しない",
            shadow.nextStartedActivity,
        )
        // 【追加検証】: Activity が終了せず EditScreen を表示し続けていること
        assertTrue(
            "変更後フロー: EditScreen 表示中は Activity が終了していない",
            !activity.isFinishing,
        )
    }

    @Test
    fun `null content type does not start obsidian`() {
        val intent = Intent(Intent.ACTION_SEND)
        // no MIME type → ContentTypeDetector returns null → finish() called immediately

        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().start().resume()

        val activity = controller.get()
        val shadow = Shadows.shadowOf(activity)

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNull("Should not launch any intent", shadow.nextStartedActivity)
        assertTrue("Activity should be finishing", activity.isFinishing)
    }
}

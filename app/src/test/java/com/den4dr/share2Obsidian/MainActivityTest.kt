package com.den4dr.share2Obsidian

import android.content.Intent
import android.os.Looper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityTest {

    private fun makeShareIntent(mimeType: String, text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, text)
        }

    @Test
    fun `text plain intent launches obsidian uri`() {
        val intent = makeShareIntent("text/plain", "テスト共有テキスト")

        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().start().resume()

        val activity = controller.get()
        val shadow = Shadows.shadowOf(activity)

        // Run coroutine (lifecycleScope.launch)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val nextIntent = shadow.nextStartedActivity
        assertNotNull("Expected obsidian URI launch", nextIntent)
        assertTrue("Expected obsidian scheme", nextIntent?.data?.scheme == "obsidian")
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

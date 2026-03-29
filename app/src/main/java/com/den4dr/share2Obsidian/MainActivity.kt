package com.den4dr.share2Obsidian

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.den4dr.share2Obsidian.content.ContentTypeDetector
import com.den4dr.share2Obsidian.content.FileContentProcessor
import com.den4dr.share2Obsidian.content.HtmlContentProcessor
import com.den4dr.share2Obsidian.content.ShareContent
import com.den4dr.share2Obsidian.content.TextContentProcessor
import com.den4dr.share2Obsidian.content.UrlContentProcessor
import com.den4dr.share2Obsidian.format.FrontmatterBuilder
import com.den4dr.share2Obsidian.format.ObsidianUriBuilder
import com.den4dr.share2Obsidian.ui.LoadingScreen
import com.den4dr.share2Obsidian.util.WebViewExtractor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shareContent = ContentTypeDetector.detect(intent)
        if (shareContent == null) {
            finish()
            return
        }

        if (shareContent is ShareContent.Url) {
            setContent { LoadingScreen() }
        }

        lifecycleScope.launch {
            val processed = when (shareContent) {
                is ShareContent.Text -> TextContentProcessor().process(shareContent)
                is ShareContent.Url -> UrlContentProcessor(WebViewExtractor(this@MainActivity)).process(shareContent)
                is ShareContent.Html -> HtmlContentProcessor().process(shareContent)
                is ShareContent.File -> FileContentProcessor(this@MainActivity).process(shareContent)
            }

            val noteContent = FrontmatterBuilder.build(processed.title, processed.body)
            val uri = ObsidianUriBuilder.build(noteContent, processed.title)

            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_obsidian_not_installed),
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        }
    }
}

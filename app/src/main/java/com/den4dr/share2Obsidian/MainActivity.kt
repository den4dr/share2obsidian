package com.den4dr.share2Obsidian

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.den4dr.share2Obsidian.content.ContentTypeDetector
import com.den4dr.share2Obsidian.content.FileContentProcessor
import com.den4dr.share2Obsidian.content.HtmlContentProcessor
import com.den4dr.share2Obsidian.content.ShareContent
import com.den4dr.share2Obsidian.content.TextContentProcessor
import com.den4dr.share2Obsidian.content.UrlContentProcessor
import com.den4dr.share2Obsidian.format.NoteComposer
import com.den4dr.share2Obsidian.format.NoteConfig
import com.den4dr.share2Obsidian.ui.EditScreen
import com.den4dr.share2Obsidian.ui.EditScreenViewModel
import com.den4dr.share2Obsidian.ui.LoadingScreen
import com.den4dr.share2Obsidian.util.WebViewExtractor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // 画面回転時も ViewModel インスタンスを保持するため viewModels() デリゲートを使用する（EDGE-101）
    private val viewModel: EditScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shareContent = ContentTypeDetector.detect(intent)
        if (shareContent == null) {
            finish()
            return
        }

        // URL は WebView 本文抽出に時間がかかるため、処理中はローディング画面を先に表示する
        if (shareContent is ShareContent.Url) {
            setContent { LoadingScreen() }
        }

        val config = NoteConfig.fromAppConfig()

        lifecycleScope.launch {
            val processed = when (shareContent) {
                is ShareContent.Text -> TextContentProcessor().process(shareContent)
                is ShareContent.Url -> UrlContentProcessor(
                    WebViewExtractor(this@MainActivity)
                ).process(shareContent)
                is ShareContent.Html -> HtmlContentProcessor().process(shareContent)
                is ShareContent.File -> FileContentProcessor(this@MainActivity).process(shareContent)
            }

            viewModel.initialize(processed, config)

            setContent {
                EditScreen(
                    viewModel = viewModel,
                    config = config,
                    onSend = { sendParams ->
                        val content = NoteComposer.buildFrontmatter(
                            sendParams.title,
                            sendParams.body,
                            sendParams.tags,
                        )
                        val uri = NoteComposer.buildUri(content, sendParams.title, sendParams.config)
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.error_obsidian_not_installed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        finish()
                    },
                    onCancel = {
                        finish()
                    },
                )
            }
        }
    }
}

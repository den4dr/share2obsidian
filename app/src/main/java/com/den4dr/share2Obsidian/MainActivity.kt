package com.den4dr.share2Obsidian

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.den4dr.share2Obsidian.ui.SettingsScreen
import com.den4dr.share2Obsidian.util.WebViewExtractor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 画面回転時も ViewModel インスタンスを保持するため viewModels() デリゲートを使用する（EDGE-101）
    private val viewModel: EditScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shareContent = ContentTypeDetector.detect(intent)
        if (shareContent == null) {
            if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
                // 未対応・不正な共有インテント → 即終了
                finish()
            } else {
                // 直接起動（アイコンタップ）→ 設定画面を表示（REQ-001）
                setContent {
                    SettingsScreen(onNavigateBack = { finish() })
                }
            }
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
                // rememberSaveable で画面回転後も状態を復元する（EDGE-101）
                var showSettings by rememberSaveable { mutableStateOf(false) }

                if (showSettings) {
                    SettingsScreen(onNavigateBack = { showSettings = false })
                } else {
                    EditScreen(
                        viewModel = viewModel,
                        config = config,
                        onSend = { sendParams ->
                            val content = NoteComposer.buildFrontmatter(
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
                        onNavigateToSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}

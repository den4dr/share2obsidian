/**
 * 設定画面 Kotlin インターフェース・型定義
 *
 * 作成日: 2026-05-31
 * 関連設計: architecture.md
 * 言語: Kotlin 2.2+
 *
 * 信頼性レベル:
 * - 🔵 青信号: 要件定義書・ユーザヒアリング・既存実装を参考にした確実な定義
 * - 🟡 黄信号: 要件定義書・ユーザヒアリングから妥当な推測による定義
 * - 🔴 赤信号: 要件定義書・ユーザヒアリングにない推測による定義
 */

package com.den4dr.share2Obsidian.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.den4dr.share2Obsidian.R

// ========================================
// SettingsScreen Composable
// ========================================

/**
 * 設定画面 Composable（プレースホルダー実装）
 *
 * 🔵 信頼性: REQ-001, REQ-003, REQ-102, REQ-103・ユーザヒアリングより
 *
 * @param onNavigateBack 戻るボタン・バックボタン押下時のコールバック
 *   - アイコン起動の場合: MainActivity.finish()
 *   - 共有フロー（EditScreen）からの場合: showSettings = false
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,  // 🔵 REQ-102, REQ-103 より
) {
    // バックボタン対応（REQ-103）
    // 🔵 BackHandler パターン - content-edit-preview の EditScreen と同様
    BackHandler { onNavigateBack() }

    Scaffold(
        topBar = {
            // 設定画面トップバー（戻るボタン付き）
            // 🔵 REQ-102・Material3 TopAppBar パターンより
            TopAppBar(
                title = {
                    Text(stringResource(R.string.label_settings))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.label_settings),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        // プレースホルダーコンテンツ（REQ-003）
        // 🔵 将来の設定項目追加時はここを拡張する
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.settings_placeholder))
        }
    }
}

// ========================================
// EditScreen 変更後のシグネチャ
// ========================================

/**
 * 編集画面 Composable の変更後シグネチャ（参照用）
 *
 * 🔵 信頼性: REQ-002, REQ-101・既存 EditScreen 実装への追加より
 *
 * 変更点: onNavigateToSettings パラメータを追加
 * 既存パラメータ（viewModel, config, onSend, onCancel）は変更なし
 *
 * @param onNavigateToSettings 設定アイコンタップ時のコールバック（新規追加）
 */
// fun EditScreen(
//     viewModel: EditScreenViewModel,          // 変更なし 🔵
//     config: NoteConfig,                      // 変更なし 🔵
//     onSend: (SendParams) -> Unit,            // 変更なし 🔵
//     onCancel: () -> Unit,                    // 変更なし 🔵
//     onNavigateToSettings: () -> Unit,        // 新規追加 🔵 REQ-002, REQ-101 より
// )

// TopAppBar の actions スロットに追加するアイコン:
// IconButton(onClick = onNavigateToSettings) {
//     Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.label_settings))
// }

// ========================================
// MainActivity ナビゲーション（共有フロー）
// ========================================

/**
 * 共有フロー時の MainActivity setContent 内のナビゲーション状態（参照用）
 *
 * 🔵 信頼性: REQ-101, REQ-401, EDGE-001, EDGE-002, EDGE-101・ユーザヒアリングより
 *
 * rememberSaveable を使用することで画面回転後も showSettings 状態を復元（EDGE-101）
 */
// setContent {
//     AppTheme {
//         var showSettings by rememberSaveable { mutableStateOf(false) }
//
//         if (showSettings) {
//             SettingsScreen(onNavigateBack = { showSettings = false })
//         } else {
//             EditScreen(
//                 viewModel = viewModel,
//                 config = config,
//                 onSend = { sendParams -> /* 既存処理 */ },
//                 onCancel = { finish() },
//                 onNavigateToSettings = { showSettings = true },
//             )
//         }
//     }
// }

// ========================================
// strings.xml 追加リソース
// ========================================

/**
 * strings.xml に追加する文字列リソース
 *
 * 🔵 信頼性: REQ-402・既存 strings.xml パターンより
 *
 * <string name="label_settings">設定</string>
 * <string name="settings_placeholder">設定項目はまだ実装されていません</string>
 */

// ========================================
// 信頼性レベルサマリー
// ========================================
/**
 * - 🔵 青信号: 8件 (100%)
 * - 🟡 黄信号: 0件 (0%)
 * - 🔴 赤信号: 0件 (0%)
 *
 * 品質評価: 高品質
 */

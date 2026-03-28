package com.den4dr.share2Obsidian

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""

            val content = if (sharedTitle.isNotEmpty()) "# $sharedTitle\n\n$sharedText" else sharedText

            val obsidianUri = "obsidian://new".toUri()
                .buildUpon()
                .appendQueryParameter("content", content)
                .build()

            try {
                startActivity(Intent(Intent.ACTION_VIEW, obsidianUri))
            } catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "Obsidian がインストールされていません", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "テキストを共有してください", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}

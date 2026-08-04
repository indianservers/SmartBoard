package com.indianservers.smartboard.smartboard.presentation

import android.content.Context
import android.content.Intent

object SmartBoardAppShareContent {
    const val PLAY_STORE_URL =
        "https://play.google.com/store/apps/details?id=com.indianservers.AIbiology"
    const val DESCRIPTION =
        "AI Biology brings visual biology learning, interactive diagrams, and smart study tools together for students and teachers."
    const val SUBJECT = "Try AI Biology"

    val message: String
        get() = "$DESCRIPTION\n\nDownload AI Biology from Google Play:\n$PLAY_STORE_URL"
}

fun shareSmartBoardApp(context: Context) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, SmartBoardAppShareContent.SUBJECT)
        putExtra(Intent.EXTRA_TEXT, SmartBoardAppShareContent.message)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share AI Biology"))
}

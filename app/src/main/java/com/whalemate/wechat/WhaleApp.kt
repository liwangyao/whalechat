package com.whalemate.wechat

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 兜底：崩溃时把堆栈写到 Download，方便排查。 */
class WhaleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                saveToDownloads("time=$time\nthread=${thread.name}\n${writer}")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun saveToDownloads(text: String) {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "whalechat-crash.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            contentResolver.openOutputStream(uri)?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Throwable) {
        }
    }
}

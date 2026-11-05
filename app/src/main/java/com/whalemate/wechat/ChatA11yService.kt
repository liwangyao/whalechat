package com.whalemate.wechat

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ChatA11yService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ChatA11yService? = null

        @Volatile
        var pendingResult: BrainResult? = null

        const val WECHAT = "com.tencent.mm"

        fun modeLabel(mode: String): String = when (mode) {
            "goutou" -> "狗头军师"
            "love" -> "恋爱大师"
            else -> "Jev"
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastKey = ""
    private var suppressUntil = 0L
    private var busy = false

    private val scan = Runnable { analyze(true) }

    private fun scoreRoot(node: AccessibilityNodeInfo?, depth: Int): Int {
        if (node == null || depth > 30) return 0
        var score = if (!node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()) 1 else 0
        for (i in 0 until node.childCount) {
            score += scoreRoot(node.getChild(i), depth + 1)
        }
        return score
    }

    private fun wechatRoot(): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestScore = -1
        try {
            for (window in windows) {
                val root = window.root ?: continue
                if (root.packageName?.toString() != WECHAT) continue
                val score = scoreRoot(root, 0)
                if (score > bestScore) {
                    bestScore = score
                    best = root
                }
            }
        } catch (_: Throwable) {
        }
        if (best != null) return best
        val active = rootInActiveWindow ?: return null
        return if (active.packageName?.toString() == WECHAT) active else null
    }

    private fun windowsReport(): String {
        val sb = StringBuilder()
        try {
            val active = rootInActiveWindow
            sb.append("active=").append(active?.packageName)
                .append(" score=").append(scoreRoot(active, 0)).append('\n')
            var index = 0
            for (window in windows) {
                val root = window.root
                val rect = Rect()
                window.getBoundsInScreen(rect)
                sb.append("window[").append(index++).append("] pkg=")
                    .append(root?.packageName)
                    .append(" type=").append(window.type)
                    .append(" bounds=").append(rect)
                    .append(" score=").append(scoreRoot(root, 0))
                    .append('\n')
                appendSamples(root, sb, 0, 10)
            }
        } catch (t: Throwable) {
            sb.append("ERR ").append(t.message)
        }
        return sb.toString()
    }

    private fun appendSamples(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int, limit: Int) {
        if (node == null || depth > 30) return
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (text.isNotEmpty() || desc.isNotEmpty()) {
            sb.append("  ").append(node.className).append(" text=")
                .append(if (text.isNotEmpty()) text.take(40) else "desc=" + desc.take(40))
                .append('\n')
        }
        for (i in 0 until node.childCount) {
            appendSamples(node.getChild(i), sb, depth + 1, limit)
        }
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != WECHAT) return
        if (System.currentTimeMillis() < suppressUntil) return
        if (!AppSettings(this).autoAnalyze) return
        handler.removeCallbacks(scan)
        handler.postDelayed(scan, 800)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    fun analyzeNow() {
        analyze(false)
    }

    fun showModePicker() {
        val settings = AppSettings(this)
        OverlayPanel.showModes(
            this,
            settings.mode,
            { mode ->
                settings.mode = mode
                analyzeNow()
            },
            {}
        )
    }

    private fun analyze(auto: Boolean) {
        if (busy) return
        if (!auto) toast("正在读取屏幕…")
        val settings = AppSettings(this)
        val dm = resources.displayMetrics
        val root = wechatRoot()
        val screen = if (root == null) {
            WeChatScreen(false, "", emptyList())
        } else {
            WeChatParser.parse(root, dm.widthPixels, dm.heightPixels, dm.density, settings.contactName)
        }

        if (screen.isChat && screen.messages.isNotEmpty()) {
            if (auto) {
                val last = screen.messages.last()
                if (last.fromMe) return
                val key = screen.messages.takeLast(4).joinToString("|") {
                    (if (it.fromMe) "1" else "0") + it.text
                }
                if (key == lastKey) return
                lastKey = key
            }
            generate(settings, screen)
        } else {
            if (!auto) toast("无障碍读不到，改用截图识别…")
            captureAndOcr(settings)
        }
    }

    private fun generate(settings: AppSettings, screen: WeChatScreen) {
        busy = true
        Thread {
            try {
                val result = ReplyEngine.generate(this, settings, screen)
                pendingResult = result
                handler.post {
                    when {
                        settings.autoSend && result.options.isNotEmpty() ->
                            sendText(result.options.first().text)

                        else -> OverlayPanel.show(
                            this,
                            result,
                            modeLabel(settings.mode),
                            { text -> sendText(text) },
                            { analyzeNow() },
                            { BallService.stop(this) },
                            { showModePicker() }
                        )
                    }
                }
            } catch (t: Throwable) {
                handler.post { toast("生成失败：${t.message}") }
            } finally {
                busy = false
            }
        }.start()
    }

    private fun captureAndOcr(settings: AppSettings) {
        if (Build.VERSION.SDK_INT < 30) {
            busy = false
            toast("系统版本不支持截图识别")
            return
        }
        busy = true
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val hardware = result.hardwareBuffer
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(hardware, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Throwable) {
                            null
                        } finally {
                            hardware.close()
                        }
                        if (bitmap == null) {
                            busy = false
                            toast("截图失败")
                            return
                        }
                        try {
                            OcrReader.read(this@ChatA11yService, bitmap, settings.contactName) { screen ->
                                if (!screen.isChat || screen.messages.isEmpty()) {
                                    busy = false
                                    val dump = windowsReport()
                                    saveDump(dump)
                                    toast("截图里没识别到文字（报告 ${dump.length} 字已存）")
                                } else {
                                    generate(settings, screen)
                                }
                            }
                        } catch (t: Throwable) {
                            busy = false
                            toast("OCR 初始化失败：${t.message}")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        busy = false
                        toast("截图失败：$errorCode")
                    }
                }
            )
        } catch (t: Throwable) {
            busy = false
            toast("截图接口异常：${t.message}")
        }
    }

    fun sendByIndex(index: Int) {
        val reply = pendingResult?.options?.getOrNull(index) ?: return
        sendText(reply.text)
    }

    private fun saveDump(text: String): Boolean {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("whalechat-dump", text))
        } catch (_: Throwable) {
        }
        return saveDumpToDownloads(text)
    }

    private fun saveDumpToDownloads(text: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "whalechat-dump.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            val out = contentResolver.openOutputStream(uri) ?: return false
            out.use {
                it.write(text.toByteArray(Charsets.UTF_8))
                it.flush()
            }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            contentResolver.update(uri, done, null, null)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun sendText(text: String, retried: Boolean = false) {
        val ime = WhaleIme.instance
        if (ime != null && ime.commit(text)) {
            suppressUntil = System.currentTimeMillis() + 2500
            handler.postDelayed({ tapSendAboveIme() }, 700)
            return
        }

        val edit = findEditableAnyWindow()
        if (edit == null) {
            if (!retried) {
                tapInputBar()
                handler.postDelayed({ sendText(text, true) }, 900)
                return
            }
            copyText(text)
            toast("没找到输入框，已复制，长按输入框粘贴")
            return
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        var ok = edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) {
            copyText(text)
            ok = edit.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        if (!ok) {
            copyText(text)
            toast("已复制，长按输入框粘贴")
            return
        }

        suppressUntil = System.currentTimeMillis() + 2500
        handler.postDelayed({
            val send = findSendAnyWindow()
            if (send == null) {
                toast("没找到发送按钮")
            } else {
                send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }, 600)
    }

    private fun copyText(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("reply", text))
        } catch (_: Throwable) {
        }
    }

    private fun findEditableAnyWindow(): AccessibilityNodeInfo? {
        try {
            for (window in windows) {
                val root = window.root ?: continue
                val found = findEditable(root)
                if (found != null) return found
            }
        } catch (_: Throwable) {
        }
        return findEditable(rootInActiveWindow)
    }

    private fun findSendAnyWindow(): AccessibilityNodeInfo? {
        try {
            for (window in windows) {
                val root = window.root ?: continue
                val found = findSend(root)
                if (found != null) return found
            }
        } catch (_: Throwable) {
        }
        return findSend(rootInActiveWindow)
    }

    private fun tapSendAboveIme() {
        val send = findSendAnyWindow()
        if (send != null) {
            send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        try {
            val dm = resources.displayMetrics
            var imeTop = dm.heightPixels
            try {
                for (window in windows) {
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        val r = Rect()
                        window.getBoundsInScreen(r)
                        if (r.top in 1 until imeTop) imeTop = r.top
                    }
                }
            } catch (_: Throwable) {
            }
            val path = Path().apply {
                moveTo(
                    dm.widthPixels - (55 * dm.density),
                    (imeTop - (35 * dm.density)).toFloat()
                )
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
                .build()
            dispatchGesture(gesture, null, null)
        } catch (_: Throwable) {
        }
    }

    private fun tapInputBar() {
        try {
            val dm = resources.displayMetrics
            var imeTop = dm.heightPixels
            try {
                for (window in windows) {
                    if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        val r = Rect()
                        window.getBoundsInScreen(r)
                        if (r.top in 1 until imeTop) imeTop = r.top
                    }
                }
            } catch (_: Throwable) {
            }
            val y = if (imeTop < dm.heightPixels - (120 * dm.density).toInt()) {
                imeTop - (40 * dm.density).toInt()
            } else {
                dm.heightPixels - (90 * dm.density).toInt()
            }
            val path = Path().apply {
                moveTo(dm.widthPixels * 0.45f, y.toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
                .build()
            dispatchGesture(gesture, null, null)
        } catch (_: Throwable) {
        }
    }

    fun dumpScreen(): String {
        val root = wechatRoot() ?: return "没有可读界面"
        val sb = StringBuilder()
        dump(root, sb, 0)
        return sb.toString()
    }

    private fun dump(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 40) return
        val r = Rect()
        node.getBoundsInScreen(r)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (text.isNotEmpty() || desc.isNotEmpty()) {
            sb.append(" ".repeat(depth.coerceAtMost(20)))
                .append(node.className)
                .append(" [").append(r.left).append(",").append(r.top).append(",")
                .append(r.right).append(",").append(r.bottom).append("] ")
                .append(if (text.isNotEmpty()) "text=$text" else "desc=$desc")
                .append('\n')
        }
        for (i in 0 until node.childCount) {
            dump(node.getChild(i), sb, depth + 1)
        }
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val cls = node.className?.toString() ?: ""
        if (node.isEditable || cls.contains("EditText")) return node
        for (i in 0 until node.childCount) {
            val found = findEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findSend(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if ((text == "发送" || desc == "发送") && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val found = findSend(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun toast(msg: String) {
        handler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
    }
}

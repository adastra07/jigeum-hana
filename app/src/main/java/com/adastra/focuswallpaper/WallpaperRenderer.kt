package com.adastra.focuswallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.util.concurrent.Executors

object WallpaperRenderer {
    // Keep apply/clear operations ordered across the Activity and notification receiver.
    private val worker = Executors.newSingleThreadExecutor()
    fun applyFocus(context: Context, title: String, width: Int, height: Int, background: Int,
                   taskId: String, automatic: Boolean, finished: (Result<Unit>) -> Unit) {
        val c = context.applicationContext
        worker.execute {
            val result = runCatching {
                val store = Store(c)
                if (taskId.isEmpty() || store.selected != taskId || (automatic && !store.wallpaperEnabled)) return@runCatching
                val bitmap = render(title, width, height, background)
                try {
                    if (store.selected == taskId && (!automatic || store.wallpaperEnabled)) {
                        applyToBoth(c, bitmap)
                        store.appliedWallpaperColor = background
                        store.focusWallpaperState = 1
                    }
                } finally { bitmap.recycle() }
            }
            finished(result)
        }
    }
    fun clearFocusText(context: Context, hadFocus: Boolean, finished: (Result<Unit>) -> Unit) {
        val c = context.applicationContext
        worker.execute {
            val result = runCatching {
                val store = Store(c)
                if (!WallpaperResetPolicy.shouldClear(store.focusWallpaperState, hadFocus,
                        store.wallpaperEnabled, store.selected.isNotEmpty())) return@runCatching
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(store.appliedWallpaperColor)
                    applyToBoth(c, bitmap)
                    store.focusWallpaperState = 0
                } finally { bitmap.recycle() }
            }
            finished(result)
        }
    }

    fun render(text: String, width: Int, height: Int, background: Int = WallpaperPalette.colors.first().second): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = WallpaperPalette.textColor(background); typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val textWidth = (width * .76f).toInt().coerceAtLeast(1)
        var size = width * .12f
        var layout: StaticLayout
        while (true) {
            paint.textSize = size
            layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(size * .16f, 1f).build()
            if (layout.height <= height * .48f || size <= 4f) break
            size *= .93f
        }
        canvas.save(); canvas.translate((width - textWidth) / 2f, (height - layout.height) / 2f); layout.draw(canvas); canvas.restore()
        return bitmap
    }
    fun applyToBoth(context: Context, bitmap: Bitmap) {
        val manager = WallpaperManager.getInstance(context)
        check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed) { "이 기기에서는 배경화면 변경이 허용되지 않습니다." }
        check(manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) != 0) { "시스템이 배경화면 적용을 완료하지 못했습니다." }
    }
}

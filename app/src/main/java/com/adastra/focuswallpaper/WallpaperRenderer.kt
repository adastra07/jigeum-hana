package com.adastra.focuswallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

object WallpaperRenderer {
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

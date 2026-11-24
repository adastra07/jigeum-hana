package com.adastra.focuswallpaper

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/** A native drag session with a fixed-X preview drawn inside the list viewport. */
class TaskDragReorder(
    private val card: LinearLayout,
    private val scroll: ScrollView,
    private val settled: () -> Unit = {},
    private val save: (List<String>) -> Unit
) {
    private val overlayHost = scroll.parent as ViewGroup
    private val rows = mutableListOf<Pair<String, View>>()
    private var dragged: View? = null
    private var original = emptyList<View>()
    private var pointerY = 0f
    private var grabY = 0f
    private var preview: BitmapDrawable? = null
    private var previewLeft = 0
    private var scrolling = false
    private val edge = 56 * card.resources.displayMetrics.density
    private fun screenPosition(view: View) = IntArray(2).also { view.getLocationOnScreen(it) }
    private val scrollTick = object : Runnable {
        override fun run() {
            if (!scrolling || dragged == null) return
            val y = pointerY - screenPosition(scroll)[1]
            val speed = when {
                y < edge -> -((edge - y) / 5).coerceIn(1f, edge / 3).toInt()
                y > scroll.height - edge -> ((y - scroll.height + edge) / 5).coerceIn(1f, edge / 3).toInt()
                else -> 0
            }
            if (speed != 0) { scroll.scrollBy(0, speed); move() }
            scroll.postDelayed(this, 24)
        }
    }
    init {
        // Listen across the viewport; horizontal finger motion never moves or cancels the row.
        scroll.setOnDragListener { _, event ->
            if (event.localState !== this) false else {
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> {
                        pointerY = screenPosition(scroll)[1] + event.y
                        move()
                        if (!scrolling) { scrolling = true; scroll.post(scrollTick) }
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> { stopScrolling(); true }
                    DragEvent.ACTION_DROP -> {
                        pointerY = screenPosition(scroll)[1] + event.y
                        move()
                        stopScrolling()
                        val y = pointerY - screenPosition(card)[1]
                        if (y in 0f..card.height.toFloat()) {
                            save((0 until card.childCount).map { index -> rows.first { it.second === card.getChildAt(index) }.first })
                            true
                        } else false
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        stopScrolling()
                        if (!event.result) {
                            card.removeAllViews()
                            original.forEach { card.addView(it) }
                        }
                        releasePreview()
                        dragged?.alpha = 1f
                        dragged?.isPressed = false
                        dragged = null
                        scroll.requestDisallowInterceptTouchEvent(false)
                        if (event.result) settled()
                        true
                    }
                    else -> true
                }
            }
        }
        scroll.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { stopScrolling(); releasePreview() }
        })
    }
    @SuppressLint("ClickableViewAccessibility") // Observation only; normal click and long-click handling stays native.
    fun attach(id: String, row: View, title: View) {
        rows.add(id to row)
        val observeTouch = View.OnTouchListener { _, event ->
            if (dragged == null) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        pointerY = event.rawY
                        val position = screenPosition(row)
                        row.drawableHotspotChanged(event.rawX - position[0], event.rawY - position[1])
                        row.isPressed = true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> row.isPressed = false
                }
            }
            false
        }
        row.setOnTouchListener(observeTouch)
        title.setOnTouchListener(observeTouch)
        val begin = View.OnLongClickListener {
            if (row.width <= 0 || row.height <= 0) return@OnLongClickListener false
            original = (0 until card.childCount).map { card.getChildAt(it) }
            dragged = row
            val position = screenPosition(row)
            grabY = (pointerY - position[1]).coerceIn(0f, row.height.toFloat())
            previewLeft = position[0] - screenPosition(overlayHost)[0]
            row.isPressed = false
            row.jumpDrawablesToCurrentState()
            val bitmap = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { drawColor(Color.WHITE); row.draw(this) }
            preview = object : BitmapDrawable(card.resources, bitmap) {
                override fun draw(canvas: Canvas) {
                    val host = screenPosition(overlayHost)
                    val viewport = screenPosition(scroll)
                    val saved = canvas.save()
                    canvas.clipRect(viewport[0] - host[0], viewport[1] - host[1],
                        viewport[0] - host[0] + scroll.width, viewport[1] - host[1] + scroll.height)
                    super.draw(canvas)
                    canvas.restoreToCount(saved)
                }
            }.apply { alpha = 140 }
            // Hide Android's freely-moving shadow; the viewport overlay follows Y only.
            val shadow = object : View.DragShadowBuilder(row) {
                override fun onProvideShadowMetrics(size: Point, touch: Point) { size.set(1, 1); touch.set(0, 0) }
                override fun onDrawShadow(canvas: Canvas) = Unit
            }
            if (row.startDragAndDrop(null, shadow, this, 0)) {
                preview?.let { overlayHost.overlay.add(it) }
                row.alpha = 0.25f
                updatePreview()
                scroll.requestDisallowInterceptTouchEvent(true)
                true
            } else { releasePreview(); dragged = null; false }
        }
        row.setOnLongClickListener(begin)
        title.setOnLongClickListener(begin)
    }
    private fun updatePreview() {
        val row = dragged ?: return
        val top = (pointerY - grabY - screenPosition(overlayHost)[1]).toInt()
        preview?.setBounds(previewLeft, top, previewLeft + row.width, top + row.height)
    }
    private fun move() {
        val row = dragged ?: return
        updatePreview()
        // Wait for row bounds to catch up after a swap or a scroll layout pass.
        if (card.isLayoutRequested) return
        val top = pointerY - grabY - screenPosition(card)[1]
        val slots = (0 until card.childCount).map { card.getChildAt(it) }.map { TaskDragGeometry.Slot(it.top, it.bottom) }
        val current = card.indexOfChild(row)
        val target = TaskDragGeometry.destination(current, top, row.height, slots)
        if (target != current) {
            card.removeView(row)
            card.addView(row, target)
        }
    }
    private fun stopScrolling() { scrolling = false; scroll.removeCallbacks(scrollTick) }
    private fun releasePreview() {
        preview?.let { overlayHost.overlay.remove(it); it.bitmap.recycle() }
        preview = null
    }
}

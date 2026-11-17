package com.adastra.focuswallpaper

import android.view.DragEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Native long-press drag, scoped to the current category. No buttons intercept it. */
class TaskDragReorder(
    private val card: LinearLayout,
    private val scroll: ScrollView,
    private val save: (List<String>) -> Unit
) {
    private val rows = mutableListOf<Pair<String, View>>()
    private var dragged: View? = null
    private var original = emptyList<View>()
    private var pointerY = 0f
    private var scrolling = false
    private val edge = 56 * card.resources.displayMetrics.density
    private val scrollTick = object : Runnable {
        override fun run() {
            if (!scrolling || dragged == null) return
            val position = IntArray(2).also { scroll.getLocationOnScreen(it) }
            val y = pointerY - position[1]
            val speed = when {
                y < edge -> -((edge - y) / 5).coerceIn(1f, edge / 3).toInt()
                y > scroll.height - edge -> ((y - scroll.height + edge) / 5).coerceIn(1f, edge / 3).toInt()
                else -> 0
            }
            if (speed != 0) {
                scroll.scrollBy(0, speed)
                move()
            }
            scroll.postDelayed(this, 24)
        }
    }
    init {
        card.setOnDragListener { _, event ->
            if (event.localState !== this) false else {
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> {
                        pointerY = IntArray(2).also { card.getLocationOnScreen(it) }[1] + event.y
                        move()
                        if (!scrolling) { scrolling = true; scroll.post(scrollTick) }
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> { stopScrolling(); true }
                    DragEvent.ACTION_DROP -> {
                        pointerY = IntArray(2).also { card.getLocationOnScreen(it) }[1] + event.y
                        move()
                        stopScrolling()
                        save((0 until card.childCount).map { index -> rows.first { it.second === card.getChildAt(index) }.first })
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        stopScrolling()
                        if (!event.result) {
                            card.removeAllViews()
                            original.forEach { card.addView(it) }
                        }
                        dragged?.alpha = 1f
                        dragged = null
                        scroll.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> true
                }
            }
        }
    }
    fun attach(id: String, row: View, title: View) {
        rows.add(id to row)
        val begin = View.OnLongClickListener {
            original = (0 until card.childCount).map { card.getChildAt(it) }
            dragged = row
            if (row.startDragAndDrop(null, View.DragShadowBuilder(row), this, 0)) {
                row.alpha = 0.35f
                scroll.requestDisallowInterceptTouchEvent(true)
                true
            } else { dragged = null; false }
        }
        row.setOnLongClickListener(begin)
        title.setOnLongClickListener(begin)
    }
    private fun move() {
        val row = dragged ?: return
        val y = pointerY - IntArray(2).also { card.getLocationOnScreen(it) }[1]
        val others = (0 until card.childCount).map { card.getChildAt(it) }.filter { it !== row }
        val target = others.count { y > it.top + it.height / 2f }
        if (target != card.indexOfChild(row)) {
            card.removeView(row)
            card.addView(row, target)
        }
    }
    private fun stopScrolling() {
        scrolling = false
        scroll.removeCallbacks(scrollTick)
    }
}

package com.adastra.focuswallpaper

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/** Shared geometry. Selection never scrolls a category into a preferred position. */
class CategoryStrip(
    activity: Activity,
    key: String,
    categories: List<Pair<String, String>>,
    selectedId: String,
    offset: Int,
    saveOffset: (Int) -> Unit,
    select: (String) -> Unit,
    edit: (String) -> Unit,
    add: () -> Unit
) : LinearLayout(activity) {
    init {
        val ui = NoteUi(activity)
        tag = "$key:categories"; orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(52)
        val strip = LinearLayout(activity).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val scroll = object : HorizontalScrollView(activity) {
            private var restore = true
            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                super.onLayout(changed, left, top, right, bottom)
                if (restore) {
                    restore = false
                    scrollTo(offset, 0)
                    saveOffset(scrollX)
                    setOnScrollChangeListener { _, x, _, _, _ -> saveOffset(x) }
                }
            }
        }.apply {
            tag = "$key:category-scroll"; isHorizontalScrollBarEnabled = false
            isFillViewport = false; addView(strip)
        }
        addView(scroll, LayoutParams(0, -2, 1f))
        categories.forEach { (id, name) ->
            val chosen = selectedId == id
            strip.addView(ui.button(name, if (chosen) ui.soft else Color.TRANSPARENT) {
                if (!chosen) select(id)
            }.apply {
                tag = "$key:category:$id"
                minimumHeight = ui.dp(36); setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4))
                isSelected = chosen; setTextColor(if (chosen) ui.ink else ui.muted)
                setTypeface(null, if (chosen) Typeface.BOLD else Typeface.NORMAL)
                setOnLongClickListener { edit(id); true }
            }, LayoutParams(-2, -2).apply { marginEnd = ui.dp(4) })
        }
        val addButton = ui.squareButton("+") { add() }.apply {
            tag = "$key:category-add"
            contentDescription = if (key == "tasks") "분류 추가" else "캘린더 분류 추가"
        }
        addView(addButton, LayoutParams(addButton.minimumWidth, addButton.minimumHeight).apply { marginEnd = ui.dp(4) })
    }
}

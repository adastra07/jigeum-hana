package com.adastra.focuswallpaper

import android.app.Activity
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Same compact ordering controls for checklist lists and calendar categories. */
class CategoryOrderControls(
    activity: Activity,
    private val selectedId: String,
    private val noun: String,
    private val items: () -> List<Pair<String, String>>,
    private val moved: (Int) -> Unit
) : LinearLayout(activity) {
    private val ui = NoteUi(activity)
    private val leftArrow = ui.button("‹") {}.apply { textSize = 23f; setTextColor(ui.muted); minimumHeight = ui.dp(36); setPadding(0, 0, 0, 0) }
    private val leftName = ui.text("", 12f, ui.muted)
    private val currentName = ui.text("", 13f, ui.accent).apply { setTypeface(null, Typeface.BOLD); background = ui.shape(ui.soft) }
    private val rightName = ui.text("", 12f, ui.muted)
    private val rightArrow = ui.button("›") {}.apply { textSize = 23f; setTextColor(ui.muted); minimumHeight = ui.dp(36); setPadding(0, 0, 0, 0) }
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        tag = "category-order:controls"
        listOf(leftName, currentName, rightName).forEach {
            it.gravity = Gravity.CENTER; it.maxLines = 2; it.ellipsize = TextUtils.TruncateAt.END
            it.setPadding(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(3)); it.minimumHeight = ui.dp(36)
        }
        leftName.tag = "category-order:left-name"; currentName.tag = "category-order:current-name"; rightName.tag = "category-order:right-name"
        addView(leftArrow, LayoutParams(ui.dp(36), -2))
        addView(leftName, LayoutParams(0, -2, 1f))
        addView(currentName, LayoutParams(0, -2, 1.2f))
        addView(rightName, LayoutParams(0, -2, 1f))
        addView(rightArrow, LayoutParams(ui.dp(36), -2))
        leftArrow.setOnClickListener { move(-1) }; rightArrow.setOnClickListener { move(1) }
        refresh()
    }
    private fun move(direction: Int) {
        val all = items(); val index = all.indexOfFirst { it.first == selectedId }
        if (index < 0 || index + direction !in all.indices) return
        moved(direction)
        refresh()
    }
    private fun refresh() {
        val all = items(); val index = all.indexOfFirst { it.first == selectedId }
        if (index < 0) { visibility = View.GONE; return }
        val name = all[index].second
        val left = all.getOrNull(index - 1); val right = all.getOrNull(index + 1)
        currentName.text = name; currentName.contentDescription = "순서를 바꿀 $noun, $name"
        leftName.text = left?.second.orEmpty(); rightName.text = right?.second.orEmpty()
        leftArrow.visibility = if (left == null) View.INVISIBLE else View.VISIBLE; leftName.visibility = leftArrow.visibility
        rightArrow.visibility = if (right == null) View.INVISIBLE else View.VISIBLE; rightName.visibility = rightArrow.visibility
        val objectName = if (noun == "분류") "분류를" else "목록을"
        leftArrow.contentDescription = "$name $objectName 한 칸 왼쪽으로 이동"
        rightArrow.contentDescription = "$name $objectName 한 칸 오른쪽으로 이동"
    }
}

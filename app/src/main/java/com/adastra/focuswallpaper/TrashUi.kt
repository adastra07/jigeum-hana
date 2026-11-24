package com.adastra.focuswallpaper

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

data class TrashEntry(val title: String, val detail: String, val restore: () -> Unit, val delete: () -> Unit)

/** One presentation for checklist and calendar trash; storage actions stay with their owners. */
class TrashUi(private val activity: Activity) {
    private val ui = NoteUi(activity)
    private fun text(value: String, size: Float, color: Int = ui.ink) = ui.text(value, size, color).apply {
        setPadding(0, ui.dp(3), 0, ui.dp(3))
    }
    private fun button(value: String, action: () -> Unit): TextView = text(value, 14f, ui.muted).apply {
        gravity = Gravity.CENTER; setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4))
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(28, 36, 90, 72)), ui.shape(Color.TRANSPARENT), ui.shape(Color.WHITE))
        isClickable = true; isFocusable = true; setOnClickListener { action() }
    }
    fun render(parent: LinearLayout, entries: List<TrashEntry>, empty: () -> Unit) {
        if (entries.isEmpty()) {
            parent.addView(text("휴지통이 비어 있습니다", 15f, ui.muted).apply {
                tag = "trash:empty"; setPadding(ui.dp(8), ui.dp(24), ui.dp(8), 0)
            })
            return
        }
        ui.row(parent).apply {
            tag = "trash:toolbar"
            addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(button("휴지통 비우기", empty), LinearLayout.LayoutParams(-2, ui.dp(44)))
        }
        val card = ui.column().apply {
            tag = "trash:card"; background = ui.shape(Color.WHITE, 8)
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
        }
        parent.addView(card)
        entries.forEachIndexed { index, entry ->
            ui.row(card).apply {
                tag = "trash:row:$index"; setPadding(ui.dp(8), ui.dp(4), 0, ui.dp(4))
                addView(ui.column().apply {
                    addView(text(entry.title, 16f).apply { tag = "trash:title:$index" })
                    addView(text(entry.detail, 12f, ui.muted).apply { tag = "trash:detail:$index" })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(button("", entry.restore).apply {
                    tag = "trash:restore:$index"; contentDescription = "${entry.title} 복원"
                    val icon = activity.getDrawable(R.drawable.ic_restore)!!.mutate().apply {
                        setTint(ui.muted); setBounds(0, 0, ui.dp(22), ui.dp(22))
                    }
                    setCompoundDrawables(icon, null, null, null); setPadding(ui.dp(13), 0, ui.dp(13), 0)
                }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
                addView(button("×", entry.delete).apply {
                    tag = "trash:delete:$index"; textSize = 23f; contentDescription = "${entry.title} 영구 삭제"
                }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(48)))
            }
        }
    }
}

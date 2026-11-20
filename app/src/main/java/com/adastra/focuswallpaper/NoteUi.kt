package com.adastra.focuswallpaper

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class NoteUi(val activity: Activity) {
    val ink = 0xff202723.toInt()
    val muted = 0xff848a86.toInt()
    val accent = 0xff245a48.toInt()
    val soft = 0xffeaefeb.toInt()
    val canvas = 0xfff7f8f7.toInt()
    fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    fun text(value: String, size: Float = 14f, color: Int = ink) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color); setPadding(dp(4), dp(3), dp(4), dp(3))
    }
    fun shape(color: Int, radius: Int = 6) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    fun button(value: String, fill: Int = Color.TRANSPARENT, action: () -> Unit) = text(value).apply {
        gravity = Gravity.CENTER; minimumHeight = dp(40); setPadding(dp(10), dp(6), dp(10), dp(6))
        background = RippleDrawable(ColorStateList.valueOf(0x22245a48), shape(fill), shape(Color.WHITE))
        isClickable = true; isFocusable = true; setOnClickListener { action() }
    }
    fun icon(res: Int, description: String, action: () -> Unit) = button("", action = action).apply {
        contentDescription = description
        setCompoundDrawablesWithIntrinsicBounds(activity.getDrawable(res)!!.mutate().apply { setTint(accent) }, null, null, null)
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }
    fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    fun row(parent: LinearLayout) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; parent.addView(this)
    }
    fun date(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
    fun scheduleLabel(s: NoteSchedule): String {
        val dates = if (s.start == s.end) date(s.start) else "${s.start} ~ ${s.end}"
        return if (s.startMinute == null) "$dates · 종일" else "$dates · ${time(s.startMinute)} ~ ${time(s.endMinute!!)}"
    }
    fun time(minute: Int) = "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60)
    fun editCategory(repo: NoteRepository, original: NoteCategory?, done: (NoteCategory) -> Unit) {
        val body = column().apply { setPadding(dp(20), dp(4), dp(20), dp(8)) }
        val name = EditText(activity).apply { setText(original?.name ?: ""); hint = "분류 이름"; isSingleLine = true; filters = arrayOf(android.text.InputFilter.LengthFilter(24)) }
        body.addView(name)
        body.addView(text("분류 색상", 12f, muted))
        var color = original?.color ?: colors.first()
        val swatches = mutableListOf<Pair<Int, View>>()
        fun update() { swatches.forEach { (c, v) -> v.background = shape(c).apply { if (color == c) setStroke(dp(2), accent) }; v.isSelected = color == c } }
        colors.chunked(5).forEach { group ->
            row(body).apply { group.forEachIndexed { i, c ->
                val cell = button("✓") { color = c; update() }.apply {
                    contentDescription = "분류 색상 ${colors.indexOf(c) + 1}"; setTextColor(if (color == c) accent else Color.TRANSPARENT)
                }
                swatches.add(c to cell)
                cell.text = ""
                addView(cell, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            } }
        }
        update()
        val dialog = AlertDialog.Builder(activity).setTitle(if (original == null) "캘린더 분류 추가" else "캘린더 분류 편집")
            .setView(body).setNegativeButton("취소", null).setPositiveButton("저장", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = name.text.toString().trim()
            if (value.isBlank()) { name.error = "분류 이름을 입력해 주세요"; return@setOnClickListener }
            if (value == "모두" || repo.categories().any { it.name == value && it.id != original?.id }) { name.error = "이미 사용하는 이름입니다"; return@setOnClickListener }
            val category = original?.copy(name = value, color = color) ?: NoteCategory(name = value, color = color)
            repo.saveCategory(category); dialog.dismiss(); done(category)
        } }
        dialog.show()
    }
    companion object {
        val colors = listOf(0xffc8d6ef, 0xffd4c7eb, 0xfff3d7c5, 0xffd7e8cd, 0xfff2e8b8,
            0xffedcfd5, 0xffc9e6e1, 0xffdce1e4, 0xffe5d8c7, 0xffd9dcf2).map { it.toInt() }
    }
}

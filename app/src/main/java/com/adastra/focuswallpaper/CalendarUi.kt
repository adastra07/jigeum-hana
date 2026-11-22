package com.adastra.focuswallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import java.time.LocalDate
import java.time.YearMonth

class CalendarUi(private val activity: Activity, private val repo: NoteRepository, private val redraw: () -> Unit) {
    private val ui = NoteUi(activity)
    var month: YearMonth = YearMonth.now()
    var selectedDate: LocalDate = LocalDate.now()
    var filter = ""
    private var dayDialog: AlertDialog? = null
    private var monthPager: CalendarMonthView? = null
    private var redrawDay: (() -> Unit)? = null
    fun dispose() {
        dayDialog?.setOnDismissListener(null); dayDialog?.dismiss()
        dayDialog = null; redrawDay = null; monthPager = null
    }
    fun refreshDay() { if (dayDialog?.isShowing == true) redrawDay?.invoke() }
    fun addMemo() { open(null, selectedDate) }
    private fun updateSelection() {
        fun update(view: View) {
            if (view.tag is LocalDate) view.background = ui.shape(if (view.tag == selectedDate) 0xfff0f4f0.toInt() else Color.WHITE, 0)
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) update(view.getChildAt(i))
        }
        monthPager?.let { update(it) }
    }
    fun render(parent: LinearLayout, viewportHeight: Int = 0) {
        if (filter.isNotEmpty() && repo.categories().none { it.id == filter }) filter = ""
        categoryBar(parent) { redraw() }
        parent.addView(Space(activity), LinearLayout.LayoutParams(1, ui.dp(8)))
        val heightDp = (activity.resources.displayMetrics.heightPixels / activity.resources.displayMetrics.density).toInt()
        val viewportDp = if (viewportHeight > 0) (viewportHeight / activity.resources.displayMetrics.density).toInt() else heightDp - 224
        fun textHeight(size: Float): Int = ui.text("", size).paint.fontMetricsInt.let { it.bottom - it.top }
        val dayHeight = maxOf(ui.dp(22), textHeight(12f) + ui.dp(6))
        val labelHeight = maxOf(ui.dp(14), textHeight(10f))
        val moreHeight = maxOf(ui.dp(12), textHeight(9f))
        val cellHeight = maxOf(ui.dp(CalendarLayout.rowHeight(viewportDp)), dayHeight + ui.dp(5) + labelHeight + ui.dp(2) + moreHeight)
        val capacity = ((cellHeight - dayHeight - ui.dp(5) - moreHeight) / (labelHeight + ui.dp(2))).coerceIn(1, 3)
        val headerHeight = maxOf(ui.dp(40), textHeight(19f) + ui.dp(8))
        val weekdayHeight = maxOf(ui.dp(24), textHeight(12f) + ui.dp(6))
        val notes = repo.notes()
        val categories = repo.categories().associateBy { it.id }
        fun createMonth(displayMonth: YearMonth): View {
            val card = ui.column().apply { background = ui.shape(Color.WHITE, 10) }
            ui.row(card).apply {
                minimumHeight = headerHeight
                addView(ui.button("‹") { monthPager?.showMonth(displayMonth.minusMonths(1)) }.apply { contentDescription = "이전 달" }, LinearLayout.LayoutParams(ui.dp(36), -2))
                addView(ui.button("${displayMonth.year}. ${displayMonth.monthValue}") {
                    chooseDate(displayMonth.atDay(1)) { selectedDate = it; monthPager?.showMonth(YearMonth.from(it)) }
                }.apply {
                    textSize = 19f; setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(4)); setTypeface(null, Typeface.BOLD); contentDescription = "연도와 월 선택"
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.button("›") { monthPager?.showMonth(displayMonth.plusMonths(1)) }.apply { contentDescription = "다음 달" }, LinearLayout.LayoutParams(ui.dp(36), -2))
                addView(ui.button("오늘") { selectedDate = LocalDate.now(); monthPager?.showMonth(YearMonth.now()) }.apply {
                    textSize = 12f; setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
                }, LinearLayout.LayoutParams(ui.dp(40), -2))
                addView(ui.icon(R.drawable.ic_search, "캘린더 메모 검색") { search() }.apply { setPadding(ui.dp(7), ui.dp(7), ui.dp(7), ui.dp(7)) }, LinearLayout.LayoutParams(ui.dp(38), -2))
            }
            ui.row(card).apply {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, day ->
                    addView(ui.text(day, 12f, if (i == 0) 0xffb95b5b.toInt() else ui.muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, weekdayHeight, 1f))
                }
            }
            CalendarDates.cells(displayMonth).chunked(7).forEach { week ->
                val line = ui.row(card).apply { gravity = Gravity.TOP }
                week.forEach { date ->
                    val currentMonth = YearMonth.from(date) == displayMonth
                    val cell = ui.column().apply {
                        tag = date; setPadding(ui.dp(1), ui.dp(2), ui.dp(1), ui.dp(3))
                        background = ui.shape(if (date == selectedDate) 0xfff0f4f0.toInt() else Color.WHITE, 0)
                        isClickable = true; isFocusable = true; contentDescription = "${date.year}년 ${ui.date(date)}"
                        setOnClickListener { selectedDate = date; updateSelection(); showDay(date) }
                    }
                    val day = ui.text(date.dayOfMonth.toString(), 12f, when {
                        !currentMonth -> 0xffb5bab6.toInt()
                        date.dayOfWeek.value == 7 -> 0xffb95b5b.toInt()
                        else -> ui.ink
                    }).apply {
                        gravity = Gravity.CENTER
                        if (date == LocalDate.now()) { background = ui.shape(ui.ink, 5); setTextColor(Color.WHITE) }
                    }
                    val dayWidth = maxOf(ui.dp(24), day.paint.measureText("30").toInt() + ui.dp(8))
                    cell.addView(day, LinearLayout.LayoutParams(dayWidth, dayHeight).apply { gravity = Gravity.CENTER_HORIZONTAL })
                    val entries = CalendarDates.entries(notes, date, filter)
                    entries.take(capacity).forEach { note ->
                        val category = categories[note.categoryId] ?: categories.getValue(NOTE_UNCATEGORIZED)
                        val label = ui.text(note.displayTitle, 10f).apply {
                            isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; setPadding(ui.dp(2), 0, ui.dp(2), 0)
                            background = ui.shape(category.color, 2); minimumHeight = labelHeight
                            alpha = if (currentMonth) 1f else .65f
                            contentDescription = "${note.displayTitle}, ${ui.scheduleLabel(note.schedule!!)}, ${category.name}"
                            isClickable = true; isFocusable = true; setOnClickListener { open(note, date) }
                        }
                        cell.addView(label, LinearLayout.LayoutParams(-1, labelHeight).apply { bottomMargin = ui.dp(2) })
                    }
                    if (entries.size > capacity) cell.addView(ui.text("+${entries.size - capacity}", 9f, ui.muted).apply { setPadding(ui.dp(2), 0, 0, 0) }, LinearLayout.LayoutParams(-1, moreHeight))
                    line.addView(cell, LinearLayout.LayoutParams(0, cellHeight, 1f))
                }
            }
            return card
        }
        val pager = CalendarMonthView(activity, month, headerHeight + weekdayHeight + cellHeight * 6,
            { createMonth(it) }, { shown -> month = shown; selectedDate = CalendarLayout.dateInMonth(selectedDate, shown) }, { updateSelection() })
        monthPager = pager
        parent.addView(pager, LinearLayout.LayoutParams(-1, -2))
    }
    private fun categoryBar(parent: LinearLayout, changed: () -> Unit) {
        val line = ui.row(parent)
        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        line.addView(scroll, LinearLayout.LayoutParams(0, -2, 1f))
        val all = listOf(NoteCategory("", "모두", 0)) + repo.categories()
        var selected: View? = null
        all.forEach { category ->
            val chosen = filter == category.id
            val button = ui.button(category.name, if (chosen) ui.soft else Color.TRANSPARENT) { filter = category.id; changed() }.apply {
                minimumHeight = ui.dp(36); setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4))
                isSelected = chosen; setTextColor(if (chosen) ui.ink else ui.muted)
                setTypeface(null, if (chosen) Typeface.BOLD else Typeface.NORMAL)
                if (category.id.isNotEmpty()) setOnLongClickListener { categoryMenu(category, changed); true }
            }
            tabs.addView(button, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = ui.dp(4) })
            if (chosen) selected = button
        }
        scroll.post { selected?.let { scroll.scrollTo((it.left - ui.dp(4)).coerceAtLeast(0), 0) } }
        line.addView(ui.button("+") { ui.editCategory(repo, null) { changed() } }.apply { contentDescription = "캘린더 분류 추가" }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
    }
    private fun categoryMenu(category: NoteCategory, changed: () -> Unit) {
        val choices = mutableListOf("이름·색상 변경", "왼쪽으로", "오른쪽으로")
        if (category.id != NOTE_UNCATEGORIZED) choices.add("분류 삭제")
        AlertDialog.Builder(activity).setTitle(category.name).setItems(choices.toTypedArray()) { _, i ->
            when (i) {
                0 -> ui.editCategory(repo, category) { changed() }
                1, 2 -> { repo.moveCategory(category.id, if (i == 1) -1 else 1); changed() }
                else -> AlertDialog.Builder(activity).setTitle("분류를 삭제할까요?").setMessage("이 분류의 메모와 휴지통 메모는 미분류로 이동합니다.")
                    .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ -> repo.removeCategory(category.id); if (filter == category.id) filter = ""; changed() }.show()
            }
        }.show()
    }
    fun showDay(date: LocalDate) {
        dayDialog?.dismiss()
        selectedDate = date
        val body = ui.column().apply { setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12)) }
        val dialog = AlertDialog.Builder(activity).setView(body).setNegativeButton("닫기", null).create()
        dayDialog = dialog
        val draw = {
            body.removeAllViews()
            ui.row(body).apply {
                addView(ui.button("‹") { selectedDate = selectedDate.minusDays(1); redrawDay?.invoke() }.apply { contentDescription = "이전 날짜" }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(44)))
                addView(ui.text(ui.date(selectedDate), 19f).apply { gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.button("›") { selectedDate = selectedDate.plusDays(1); redrawDay?.invoke() }.apply { contentDescription = "다음 날짜" }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(44)))
            }
            categoryBar(body) { redrawDay?.invoke() }
            val list = ui.column()
            val entries = CalendarDates.entries(repo.notes(), selectedDate, filter)
            if (entries.isEmpty()) list.addView(ui.text("메모가 없습니다", 14f, ui.muted).apply { setPadding(ui.dp(8), ui.dp(20), ui.dp(8), ui.dp(20)) })
            entries.forEach { note -> list.addView(noteRow(note) { open(note, selectedDate) }) }
            body.addView(ScrollView(activity).apply { addView(list) }, LinearLayout.LayoutParams(-1, ui.dp(280)))
            body.addView(ui.button("+ 메모 추가", ui.soft) { open(null, selectedDate) }, LinearLayout.LayoutParams(-1, -2))
        }
        redrawDay = draw
        dialog.setOnDismissListener { dayDialog = null; redrawDay = null; month = YearMonth.from(selectedDate); redraw() }
        draw(); dialog.show()
    }
    private fun noteRow(note: Note, action: () -> Unit): LinearLayout = ui.column().apply {
        val category = repo.categories().firstOrNull { it.id == note.categoryId }
        minimumHeight = ui.dp(64); setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
        background = ui.shape(Color.WHITE)
        addView(ui.text(note.displayTitle, 16f).apply { setTypeface(null, Typeface.BOLD) })
        addView(ui.text("${category?.name ?: "미분류"} · ${ui.scheduleLabel(note.schedule!!)}", 11f, ui.muted))
        if (note.body.isNotBlank()) addView(ui.text(note.body, 13f, ui.muted).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
        isClickable = true; isFocusable = true; setOnClickListener { action() }
    }
    private fun open(note: Note?, date: LocalDate) {
        selectedDate = date
        activity.startActivity(Intent(activity, NoteEditorActivity::class.java).apply {
            if (note != null) putExtra("noteId", note.id)
            putExtra("date", date.toString()); if (filter.isNotEmpty()) putExtra("categoryId", filter)
        })
    }
    private fun chooseDate(date: LocalDate, selected: (LocalDate) -> Unit) {
        DatePickerDialog(activity, { _, y, m, d -> selected(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    private fun search() {
        val input = EditText(activity).apply { hint = "제목·내용 검색"; isSingleLine = true; setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8)) }
        AlertDialog.Builder(activity).setTitle("캘린더 검색").setView(input).setNegativeButton("닫기", null).setPositiveButton("검색") { _, _ ->
            val query = input.text.toString().trim()
            if (query.isEmpty()) return@setPositiveButton
            val entries = repo.notes().filter { (filter.isEmpty() || it.categoryId == filter) && (it.title.contains(query, true) || it.body.contains(query, true)) }
            val list = ui.column()
            if (entries.isEmpty()) list.addView(ui.text("검색 결과가 없습니다"))
            entries.forEach { note -> list.addView(noteRow(note) { open(note, note.schedule!!.start) }) }
            AlertDialog.Builder(activity).setTitle("검색 결과").setView(ScrollView(activity).apply { addView(list) }).setNegativeButton("닫기", null).show()
        }.show()
    }
    fun renderTrash(parent: LinearLayout) {
        val notes = repo.notes(includeTrash = true).filter { it.deletedAt > 0 }.sortedByDescending { it.deletedAt }
        if (notes.isEmpty()) { parent.addView(ui.text("캘린더 휴지통이 비어 있습니다", 14f, ui.muted)); return }
        parent.addView(ui.button("캘린더 휴지통 비우기") {
            AlertDialog.Builder(activity).setTitle("캘린더 휴지통을 비울까요?").setMessage("${notes.size}개 메모를 영구 삭제합니다. 할 일 휴지통은 유지합니다.")
                .setNegativeButton("취소", null).setPositiveButton("비우기") { _, _ -> repo.emptyCalendarTrash(); redraw() }.show()
        })
        notes.forEach { note ->
            ui.row(parent).apply {
                addView(ui.column().apply {
                    addView(ui.text(note.displayTitle, 16f)); addView(ui.text(ui.scheduleLabel(note.schedule!!), 11f, ui.muted))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.icon(R.drawable.ic_restore, "${note.displayTitle} 복원") { repo.get(note.id)?.let { repo.save(it.restore()) }; redraw() }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
                addView(ui.button("×") {
                    AlertDialog.Builder(activity).setTitle("영구 삭제할까요?").setMessage(note.displayTitle)
                        .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ -> repo.permanentlyDelete(note.id); redraw() }.show()
                }.apply { contentDescription = "${note.displayTitle} 영구 삭제" }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(44)))
            }
        }
    }
}

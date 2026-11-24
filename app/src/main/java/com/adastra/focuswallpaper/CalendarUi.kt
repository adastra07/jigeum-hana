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
    private val categoryOffsets = mutableMapOf<String, Int>()
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
        categoryBar(parent, "calendar") { redraw() }
        parent.addView(Space(activity), LinearLayout.LayoutParams(1, ui.dp(8)))
        val heightDp = (activity.resources.displayMetrics.heightPixels / activity.resources.displayMetrics.density).toInt()
        val viewportDp = if (viewportHeight > 0) (viewportHeight / activity.resources.displayMetrics.density).toInt() else heightDp - 224
        fun textHeight(size: Float): Int = ui.text("", size).paint.fontMetricsInt.let { it.bottom - it.top }
        val dayHeight = maxOf(ui.dp(22), textHeight(12f) + ui.dp(6))
        val labelHeight = maxOf(ui.dp(14), textHeight(10f))
        val moreHeight = maxOf(ui.dp(12), textHeight(9f))
        val capacity = 3
        val dateGap = ui.dp(1).coerceAtLeast(1)
        val headerHeight = maxOf(ui.dp(40), textHeight(19f) + ui.dp(8))
        val weekdayHeight = maxOf(ui.dp(24), textHeight(12f) + ui.dp(6))
        val notes = repo.notes()
        val categories = repo.categories().associateBy { it.id }
        val entryCache = mutableMapOf<LocalDate, List<Note>>()
        fun entriesOn(date: LocalDate) = entryCache.getOrPut(date) { CalendarDates.entries(notes, date, filter) }
        fun weekHeight(week: List<LocalDate>): Int {
            val count = week.maxOf { entriesOn(it).size }
            val labels = minOf(count, capacity)
            return maxOf(ui.dp(CalendarLayout.rowHeight(viewportDp)),
                dayHeight + ui.dp(5) + labels * (labelHeight + ui.dp(2)) + if (count > capacity) moreHeight else 0)
        }
        fun monthHeight(displayMonth: YearMonth) = CalendarDates.cells(displayMonth).chunked(7).sumOf { weekHeight(it) } + dateGap * 5
        val card = ui.column().apply { tag = "calendar:main-card"; background = ui.shape(Color.WHITE, 10) }
        parent.addView(card)
        lateinit var monthLabel: TextView
        ui.row(card).apply {
            tag = "calendar:header"; minimumHeight = headerHeight
            val left = ui.row(this).apply { tag = "calendar:month-controls" }
            left.layoutParams = LinearLayout.LayoutParams(0, -2, 3f)
            left.addView(ui.button("‹") { monthPager?.showMonth(month.minusMonths(1)) }.apply { contentDescription = "이전 달" }, LinearLayout.LayoutParams(ui.dp(36), -2))
            monthLabel = ui.button("${month.year}. ${month.monthValue}") {
                chooseDate(month.atDay(1)) { selectedDate = it; monthPager?.showMonth(YearMonth.from(it)) }
            }.apply {
                tag = "calendar:month-label"; textSize = 19f
                setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(4))
                setTypeface(null, Typeface.BOLD); contentDescription = "연도와 월 선택"
            }
            left.addView(monthLabel, LinearLayout.LayoutParams(0, -2, 1f))
            left.addView(ui.button("›") { monthPager?.showMonth(month.plusMonths(1)) }.apply { contentDescription = "다음 달" }, LinearLayout.LayoutParams(ui.dp(36), -2))
            addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(ui.button("오늘") { selectedDate = LocalDate.now(); monthPager?.showMonth(YearMonth.now()) }.apply {
                textSize = 12f; setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
            }, LinearLayout.LayoutParams(ui.dp(40), -2))
            addView(ui.icon(R.drawable.ic_search, "캘린더 메모 검색") { search() }.apply {
                setPadding(ui.dp(7), ui.dp(7), ui.dp(7), ui.dp(7))
            }, LinearLayout.LayoutParams(ui.dp(38), -2))
        }
        card.addView(Space(activity), LinearLayout.LayoutParams(1, ui.dp(12)))
        ui.row(card).apply {
            tag = "calendar:weekdays"
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, day ->
                addView(ui.text(day, 12f, if (i == 0) 0xffb95b5b.toInt() else ui.muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, weekdayHeight, 1f))
            }
        }
        fun createMonth(displayMonth: YearMonth): View {
            val grid = ui.column()
            CalendarDates.cells(displayMonth).chunked(7).forEachIndexed { weekIndex, week ->
                val cellHeight = weekHeight(week)
                val line = ui.row(grid).apply { gravity = Gravity.TOP }
                line.layoutParams = LinearLayout.LayoutParams(-1, cellHeight).apply { if (weekIndex < 5) bottomMargin = dateGap }
                week.forEachIndexed { dayIndex, date ->
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
                    val entries = entriesOn(date)
                    entries.take(capacity).forEach { note ->
                        val category = categories[note.categoryId] ?: categories.getValue(NOTE_UNCATEGORIZED)
                        val label = ui.text(note.displayTitle, 10f).apply {
                            isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; setPadding(ui.dp(2), 0, ui.dp(2), 0)
                            background = ui.shape(category.color, 2); minimumHeight = labelHeight
                            alpha = if (currentMonth) 1f else .65f
                            contentDescription = "${note.displayTitle}, ${ui.scheduleLabel(note.schedule!!)}, ${category.name}"
                            isClickable = true; isFocusable = true
                            setOnClickListener { selectedDate = date; updateSelection(); showDay(date) }
                        }
                        cell.addView(label, LinearLayout.LayoutParams(-1, labelHeight).apply { bottomMargin = ui.dp(2) })
                    }
                    if (entries.size > capacity) cell.addView(ui.text("+${entries.size - capacity}", 9f, ui.muted).apply { setPadding(ui.dp(2), 0, 0, 0) }, LinearLayout.LayoutParams(-1, moreHeight))
                    line.addView(cell, LinearLayout.LayoutParams(0, cellHeight, 1f).apply { if (dayIndex < 6) marginEnd = dateGap })
                }
            }
            return grid
        }
        val pager = CalendarMonthView(activity, month, monthHeight(month),
            { createMonth(it) }, { shown ->
                month = shown; selectedDate = CalendarLayout.dateInMonth(selectedDate, shown)
                monthLabel.text = "${shown.year}. ${shown.monthValue}"
                monthPager?.setPageHeight(monthHeight(shown))
            }, { updateSelection() })
        monthPager = pager
        card.addView(pager, LinearLayout.LayoutParams(-1, -2))
    }
    private fun categoryBar(parent: LinearLayout, key: String = "calendar-day", changed: () -> Unit) {
        val categories = repo.categories()
        val all = listOf(NoteCategory("", "모두", 0)) + categories
        parent.addView(CategoryStrip(activity, key, all.map { it.id to it.name }, filter,
            categoryOffsets[key] ?: 0, { categoryOffsets[key] = it },
            { filter = it; changed() },
            { id -> categories.firstOrNull { it.id == id }?.let { categoryMenu(it, changed) } },
            { ui.editCategory(repo, null) { changed() } }))
    }
    private fun categoryMenu(category: NoteCategory, changed: () -> Unit) {
        val body = ui.column().apply { setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(4)) }
        body.addView(ui.text("분류 순서", 13f, ui.muted).apply { setPadding(0, 0, 0, 0) })
        body.addView(CategoryOrderControls(activity, category.id, "분류", { repo.categories().map { it.id to it.name } }) { direction ->
            repo.moveCategory(category.id, direction); changed()
        })
        body.addView(Space(activity), LinearLayout.LayoutParams(1, ui.dp(8)))
        val rename = ui.button("이름·색상 변경") {}.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL; minimumHeight = ui.dp(38)
            setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4)); setTextColor(ui.ink)
        }
        body.addView(rename, LinearLayout.LayoutParams(-1, -2))
        val delete = if (category.id != NOTE_UNCATEGORIZED) ui.button("분류 삭제") {}.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL; minimumHeight = ui.dp(38)
            setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4)); setTextColor(ui.muted)
            body.addView(this, LinearLayout.LayoutParams(-1, -2))
        } else null
        val dialog = AlertDialog.Builder(activity).setTitle(category.name).setView(body).setNegativeButton("닫기", null).create()
        rename.setOnClickListener {
            dialog.dismiss()
            repo.categories().firstOrNull { it.id == category.id }?.let { current -> ui.editCategory(repo, current) { changed() } }
        }
        delete?.setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(activity).setTitle("분류를 삭제할까요?").setMessage("이 분류의 메모와 휴지통 메모는 미분류로 이동합니다.")
                .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                    repo.removeCategory(category.id); if (filter == category.id) filter = ""; changed()
                }.show()
        }
        dialog.show()
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
        TrashUi(activity).render(parent, notes.map { note ->
            TrashEntry(note.displayTitle, ui.scheduleLabel(note.schedule!!),
                restore = { repo.get(note.id)?.let { repo.save(it.restore()) }; redraw() },
                delete = {
                    AlertDialog.Builder(activity).setTitle("영구 삭제할까요?").setMessage(note.displayTitle)
                        .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ -> repo.permanentlyDelete(note.id); redraw() }.show()
                })
        }) {
            AlertDialog.Builder(activity).setTitle("휴지통을 비울까요?")
                .setMessage("캘린더 휴지통의 ${notes.size}개 항목이 영구 삭제됩니다. 할 일 휴지통은 유지합니다. 삭제한 항목은 복원할 수 없습니다.")
                .setNegativeButton("취소", null).setPositiveButton("비우기") { _, _ ->
                    repo.emptyCalendarTrash(); redraw(); Toast.makeText(activity, "휴지통을 비웠습니다", Toast.LENGTH_SHORT).show()
                }.show().apply { getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ui.muted) }
        }
    }
}

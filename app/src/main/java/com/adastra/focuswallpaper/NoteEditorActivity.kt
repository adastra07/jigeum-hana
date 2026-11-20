package com.adastra.focuswallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.time.LocalDate

/** General plain-note editor; calendar scheduling is optional metadata on the shared Note. */
class NoteEditorActivity : Activity() {
    private lateinit var repo: NoteRepository
    private lateinit var ui: NoteUi
    private lateinit var draft: Note
    private lateinit var titleInput: EditText
    private lateinit var bodyInput: EditText
    private lateinit var dateLabel: TextView
    private lateinit var categoryButton: TextView
    private lateinit var colorButton: TextView
    private lateinit var scheduleButton: TextView
    private lateinit var deleteButton: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var saved = false
    private var dirty = false
    private var trashed = false
    private var errorShown = false
    private val savePending = Runnable { saveNow() }
    override fun attachBaseContext(base: Context) {
        val config = Configuration(base.resources.configuration)
        config.fontScale *= AppearancePreferences.fontScale(Store(base).fontStep)
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        ui = NoteUi(this); repo = NoteRepository(this)
        window.statusBarColor = Color.WHITE; window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val existing = (state?.getString("id") ?: intent.getStringExtra("noteId"))?.let { repo.get(it) }
        if (existing?.deletedAt?.let { it > 0 } == true) { Toast.makeText(this, "휴지통에 있는 메모입니다", Toast.LENGTH_SHORT).show(); finish(); return }
        saved = existing != null
        val day = runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrDefault(LocalDate.now())
        val categoryId = intent.getStringExtra("categoryId")?.takeIf { id -> repo.categories().any { it.id == id } } ?: NOTE_UNCATEGORIZED
        draft = state?.getString("draft")?.let { NoteCodec.decode(it) } ?: existing ?: Note(categoryId = categoryId, schedule = NoteSchedule(day))
        dirty = state?.getBoolean("dirty") ?: false
        val root = ui.column().apply { setBackgroundColor(Color.WHITE) }
        setContentView(root)
        ui.row(root).apply {
            setPadding(ui.dp(8), ui.dp(4), ui.dp(8), 0)
            addView(ui.button("‹") { closeEditor() }.apply { textSize = 26f; contentDescription = "메모 편집 마치기" }, LinearLayout.LayoutParams(ui.dp(44), -2))
            dateLabel = ui.text("", 17f).apply { setTypeface(null, Typeface.BOLD) }
            addView(dateLabel, LinearLayout.LayoutParams(0, -2, 1f))
            colorButton = ui.button("●") { editCategoryColor() }.apply { textSize = 22f; contentDescription = "이 분류 전체의 색상 변경" }
            addView(colorButton, LinearLayout.LayoutParams(ui.dp(44), -2))
        }
        val body = ui.column().apply { setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(20)) }
        ui.row(body).apply {
            addView(View(this@NoteEditorActivity), LinearLayout.LayoutParams(0, 1, 1f))
            categoryButton = ui.button("") { chooseCategory() }
            addView(categoryButton)
        }
        titleInput = EditText(this).apply {
            setText(draft.title); hint = "제목"; textSize = 23f; setTypeface(null, Typeface.BOLD)
            background = null; setPadding(0, ui.dp(14), 0, ui.dp(8)); maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(InputFilter.LengthFilter(200)); contentDescription = "메모 제목"
        }
        body.addView(titleInput, LinearLayout.LayoutParams(-1, -2))
        bodyInput = EditText(this).apply {
            setText(draft.body); hint = "내용"; textSize = 16f; gravity = Gravity.TOP
            background = null; setPadding(0, ui.dp(8), 0, ui.dp(16)); minimumHeight = ui.dp(280)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            contentDescription = "메모 내용"
        }
        body.addView(bodyInput, LinearLayout.LayoutParams(-1, -2))
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        scheduleButton = ui.button("") { editSchedule() }.apply { textSize = 12f }
        root.addView(scheduleButton, LinearLayout.LayoutParams(-1, -2))
        ui.row(root).apply {
            setPadding(ui.dp(12), ui.dp(2), ui.dp(12), ui.dp(8))
            deleteButton = ui.icon(R.drawable.ic_trash, "메모를 휴지통으로 이동") {
                handler.removeCallbacks(savePending)
                if (saved) repo.save(draft.copy(title = titleInput.text.toString(), body = bodyInput.text.toString()).trash(System.currentTimeMillis()))
                trashed = true; dirty = false; finish()
            }
            addView(deleteButton, LinearLayout.LayoutParams(ui.dp(44), -2))
            addView(View(this@NoteEditorActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(ui.button("✓", ui.soft) { closeEditor() }.apply { textSize = 23f; contentDescription = "저장하고 편집 마치기" }, LinearLayout.LayoutParams(ui.dp(48), -2))
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                draft = draft.copy(title = titleInput.text.toString(), body = bodyInput.text.toString())
                changed()
            }
        }
        titleInput.addTextChangedListener(watcher); bodyInput.addTextChangedListener(watcher)
        refreshMetadata()
        if (dirty) changed()
        if (existing == null && state == null) {
            titleInput.requestFocus()
            titleInput.post { getSystemService(InputMethodManager::class.java).showSoftInput(titleInput, InputMethodManager.SHOW_IMPLICIT) }
        }
    }
    private fun changed() { dirty = true; handler.removeCallbacks(savePending); handler.postDelayed(savePending, 300) }
    private fun saveNow(): Boolean {
        handler.removeCallbacks(savePending)
        if (!::draft.isInitialized || trashed || !dirty || (!saved && !draft.hasContent)) return true
        return try {
            // A category may have been removed elsewhere; keep the note in the default category.
            if (repo.categories().none { it.id == draft.categoryId }) draft = draft.copy(categoryId = NOTE_UNCATEGORIZED)
            draft = draft.copy(updatedAt = System.currentTimeMillis())
            repo.save(draft); saved = true; dirty = false; errorShown = false
            if (::deleteButton.isInitialized) deleteButton.visibility = View.VISIBLE
            true
        } catch (_: android.database.SQLException) {
            if (!errorShown) Toast.makeText(this, "메모를 저장하지 못했습니다. 다시 시도해 주세요", Toast.LENGTH_LONG).show()
            errorShown = true; false
        }
    }
    private fun closeEditor() {
        if (!saveNow()) return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(titleInput.windowToken, 0)
        finish()
    }
    private fun refreshMetadata() {
        val category = repo.categories().first { it.id == draft.categoryId }
        categoryButton.text = "${category.name}⌄"
        colorButton.setTextColor(category.color)
        dateLabel.text = draft.schedule?.let { ui.date(it.start) } ?: "메모"
        scheduleButton.text = draft.schedule?.let { ui.scheduleLabel(it) } ?: "날짜·시간 지정"
        scheduleButton.contentDescription = "날짜·시간·기간 변경, ${scheduleButton.text}"
        deleteButton.visibility = if (saved) View.VISIBLE else View.INVISIBLE
    }
    private fun chooseCategory() {
        val categories = repo.categories()
        AlertDialog.Builder(this).setTitle("메모 분류")
            .setItems((categories.map { it.name } + "+ 분류 추가").toTypedArray()) { _, index ->
                if (index == categories.size) ui.editCategory(repo, null) { draft = draft.copy(categoryId = it.id); changed(); refreshMetadata() }
                else { draft = draft.copy(categoryId = categories[index].id); changed(); refreshMetadata() }
            }.setNegativeButton("닫기", null).show()
    }
    private fun editCategoryColor() {
        val category = repo.categories().first { it.id == draft.categoryId }
        ui.editCategory(repo, category) { refreshMetadata() }
    }
    private fun editSchedule() {
        val initial = draft.schedule ?: NoteSchedule(LocalDate.now())
        var start = initial.start; var end = initial.end
        var startMinute = initial.startMinute ?: 9 * 60
        var endMinute = initial.endMinute ?: 10 * 60
        val body = ui.column().apply { setPadding(ui.dp(20), ui.dp(6), ui.dp(20), ui.dp(8)) }
        val startButton = ui.button("") {}
        val endButton = ui.button("") {}
        val allDay = Switch(this).apply { text = "종일"; isChecked = initial.startMinute == null; minimumHeight = ui.dp(44) }
        body.addView(allDay)
        body.addView(startButton); body.addView(endButton)
        val times = ui.row(body)
        val startTime = ui.button("") {}
        val endTime = ui.button("") {}
        times.addView(startTime, LinearLayout.LayoutParams(0, -2, 1f)); times.addView(endTime, LinearLayout.LayoutParams(0, -2, 1f))
        fun refresh() {
            startButton.text = "시작 · $start"; endButton.text = "종료 · $end"
            times.visibility = if (allDay.isChecked) View.GONE else View.VISIBLE
            startTime.text = "시작 ${ui.time(startMinute)}"; endTime.text = "종료 ${ui.time(endMinute)}"
        }
        startButton.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> start = LocalDate.of(y, m + 1, d); if (end.isBefore(start)) end = start; refresh() }, start.year, start.monthValue - 1, start.dayOfMonth).show() }
        endButton.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> end = LocalDate.of(y, m + 1, d); refresh() }, end.year, end.monthValue - 1, end.dayOfMonth).show() }
        startTime.setOnClickListener { TimePickerDialog(this, { _, h, m -> startMinute = h * 60 + m; refresh() }, startMinute / 60, startMinute % 60, true).show() }
        endTime.setOnClickListener { TimePickerDialog(this, { _, h, m -> endMinute = h * 60 + m; refresh() }, endMinute / 60, endMinute % 60, true).show() }
        allDay.setOnCheckedChangeListener { _, _ -> refresh() }
        refresh()
        val dialog = AlertDialog.Builder(this).setTitle("날짜·시간·기간").setView(body).setNegativeButton("취소", null).setPositiveButton("적용", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val schedule = NoteSchedule(start, end, if (allDay.isChecked) null else startMinute, if (allDay.isChecked) null else endMinute)
            if (!schedule.isValid()) { Toast.makeText(this, "종료는 시작 이후로 설정해 주세요", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            draft = draft.copy(schedule = schedule); changed(); refreshMetadata(); dialog.dismiss()
        } }
        dialog.show()
    }
    @Deprecated("Deprecated in Java") override fun onBackPressed() { closeEditor() }
    override fun onPause() { saveNow(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        saveNow()
        if (::draft.isInitialized) { outState.putString("id", draft.id); outState.putString("draft", NoteCodec.encode(draft)); outState.putBoolean("dirty", dirty) }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { handler.removeCallbacks(savePending); if (::repo.isInitialized) repo.close(); super.onDestroy() }
}

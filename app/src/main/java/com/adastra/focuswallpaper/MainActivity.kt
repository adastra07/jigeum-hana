package com.adastra.focuswallpaper

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val ink = Color.rgb(32, 39, 35)
    private val accent = Color.rgb(36, 90, 72)
    private val muted = Color.rgb(132, 138, 134)
    private val soft = Color.rgb(234, 239, 235)
    private val canvas = Color.rgb(247, 248, 247)
    private lateinit var store: Store
    private lateinit var page: LinearLayout
    private lateinit var heading: TextView
    private lateinit var timerPanel: LinearLayout
    private lateinit var focus: TextView
    private lateinit var clock: TextView
    private lateinit var pause: TextView
    private lateinit var navigation: LinearLayout
    private var screen = "tasks"
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var wallpaperBusy = false
    private val tick = object : Runnable {
        override fun run() {
            if (store.timer.status == "running" && store.timer.remaining(System.currentTimeMillis()) == 0L) TimerEngine.finish(this@MainActivity)
            updateFocus(); handler.postDelayed(this, 500)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = canvas; window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        store = Store(this)
        screen = savedInstanceState?.getString("screen") ?: "tasks"
        if (store.categories().none { it.id == store.category }) store.category = DEFAULT_CATEGORY
        if (active() && store.timer.taskId.isEmpty()) {
            val id = store.tasks().firstOrNull { it.deletedAt == 0L && it.text == store.timer.title }?.id ?: store.selected
            store.timer = store.timer.copy(taskId = id)
        }
        val root = column().apply { setBackgroundColor(canvas) }
        setContentView(root)
        heading = text("", 26f).apply { setTypeface(null, Typeface.BOLD); setPadding(dp(20), dp(16), dp(20), dp(12)) }
        root.addView(heading)
        timerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(2), dp(12), dp(8))
        }
        root.addView(timerPanel)
        focus = text("", 16f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setTypeface(null, Typeface.BOLD) }
        timerPanel.addView(focus, LinearLayout.LayoutParams(0, -2, 1f))
        clock = text("", 20f, accent).apply { typeface = Typeface.MONOSPACE; setPadding(dp(8), 0, dp(6), 0) }
        timerPanel.addView(clock)
        pause = flat("Ⅱ") { togglePause() }.apply { textSize = 20f }
        timerPanel.addView(pause, LinearLayout.LayoutParams(dp(44), dp(44)))
        timerPanel.addView(flat("×") { TimerEngine.stop(this); updateFocus() }.apply { textSize = 24f; contentDescription = "집중 종료" }, LinearLayout.LayoutParams(dp(44), dp(44)))
        page = column().apply { setPadding(dp(16), 0, dp(16), dp(16)) }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(page) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(229, 232, 229)) }, LinearLayout.LayoutParams(-1, dp(1)))
        navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE) }
        root.addView(navigation, LinearLayout.LayoutParams(-1, dp(68)))
        renderPage()
    }
    private fun active() = store.timer.status in listOf("running", "paused")
    private fun selected() = store.tasks().firstOrNull { it.id == store.selected && it.deletedAt == 0L }
    private fun updateFocus() {
        val timer = store.timer
        timerPanel.visibility = if (timer.status in listOf("running", "paused", "finished")) View.VISIBLE else View.GONE
        focus.text = timer.title
        clock.text = if (timer.status == "finished") "완료" else TimerEngine.formatTime(timer.remaining(System.currentTimeMillis()))
        pause.visibility = if (active()) View.VISIBLE else View.GONE
        pause.text = if (timer.status == "paused") "▷" else "Ⅱ"
        pause.contentDescription = if (timer.status == "paused") "집중 재개" else "집중 일시정지"
        clock.contentDescription = when (timer.status) {
            "paused" -> "일시정지, 남은 시간 ${clock.text}"
            "finished" -> "집중 시간 완료"
            else -> "남은 시간 ${clock.text}"
        }
    }
    private fun togglePause() {
        val timer = store.timer; val now = System.currentTimeMillis()
        if (timer.status == "running" && timer.remaining(now) == 0L) TimerEngine.finish(this)
        else store.timer = if (timer.status == "running") timer.pause(now) else timer.resume(now)
        TimerEngine.sync(this); updateFocus()
    }
    private fun renderPage() {
        page.removeAllViews()
        heading.text = when (screen) { "trash" -> "휴지통"; "settings" -> "설정"; else -> "체크리스트" }
        when (screen) { "trash" -> renderTrash(); "settings" -> renderSettings(); else -> renderTasks() }
        navigation.removeAllViews()
        listOf(Triple("tasks", "체크리스트", R.drawable.ic_checklist), Triple("trash", "휴지통", R.drawable.ic_trash), Triple("settings", "설정", R.drawable.ic_settings)).forEach { (id, label, icon) ->
            val chosen = screen == id
            navigation.addView(flat(label, color = if (chosen) accent else muted) { screen = id; renderPage() }.apply {
                textSize = 11f; setTypeface(null, if (chosen) Typeface.BOLD else Typeface.NORMAL)
                val drawable = getDrawable(icon)!!.mutate().apply { setTint(if (chosen) accent else muted); setBounds(0, 0, dp(22), dp(22)) }
                setCompoundDrawables(null, drawable, null, null); compoundDrawablePadding = dp(4)
                isSelected = chosen
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        updateFocus()
    }
    private fun renderTasks() {
        val categories = store.categories()
        val tabs = row(page)
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) }
        tabs.addView(scroll, LinearLayout.LayoutParams(0, dp(52), 1f))
        var selectedTab: View? = null
        categories.forEach { category ->
            val chosen = category.id == store.category
            val tab = flat(category.name, if (chosen) soft else Color.TRANSPARENT, if (chosen) ink else muted) {
                store.category = category.id; renderPage()
            }.apply {
                setTypeface(null, if (chosen) Typeface.BOLD else Typeface.NORMAL)
                setOnLongClickListener { categoryMenu(category); true }
                isSelected = chosen
            }
            strip.addView(tab, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(6) })
            if (chosen) selectedTab = tab
        }
        scroll.post { selectedTab?.let { scroll.smoothScrollTo((it.left - dp(8)).coerceAtLeast(0), 0) } }
        tabs.addView(flat("+") { editCategory(null) }.apply { textSize = 25f; contentDescription = "분류 추가" }, LinearLayout.LayoutParams(dp(44), dp(48)))
        gap(page, 8)
        val card = card(page)
        val tasks = store.tasks().filter { it.deletedAt == 0L && it.categoryId == store.category }
        if (tasks.isEmpty()) card.addView(text("할 일을 추가해 보세요", 14f, muted).apply { setPadding(dp(10), dp(14), dp(10), dp(14)) })
        tasks.forEach { task -> taskRow(card, task) }
        gap(page, 12)
        page.addView(flat("+ 할 일 추가", soft, accent) { editTask(null) }, LinearLayout.LayoutParams(-1, dp(46)))
    }
    private fun taskRow(parent: LinearLayout, task: Task) {
        val line = row(parent).apply { minimumHeight = dp(48); if (task.id == store.selected) background = shape(soft, 6) }
        val check = CheckBox(this).apply {
            isChecked = task.done; buttonTintList = ColorStateList.valueOf(muted)
            contentDescription = "${task.text} 완료 여부"
        }
        line.addView(check, LinearLayout.LayoutParams(dp(42), dp(48)))
        line.addView(text(task.text, 16f, if (task.done) muted else ink).apply {
            setPadding(0, dp(5), dp(6), dp(5)); minimumHeight = dp(48); gravity = Gravity.CENTER_VERTICAL
            if (task.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            isFocusable = true; background = ripple(Color.TRANSPARENT)
            setOnClickListener { editTask(task) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(flat(task.minutes.toString(), color = muted) {
            store.tasks().firstOrNull { it.id == task.id && it.deletedAt == 0L }?.let { selectTask(it, true) }
        }.apply {
            textSize = 14f; setPadding(0, 0, 0, 0)
            background = RippleDrawable(ColorStateList.valueOf(soft), InsetDrawable(shape(Color.TRANSPARENT, 4, muted), dp(4), dp(10), dp(4), dp(10)), null)
            contentDescription = "${task.text}, ${task.minutes}분 집중 시작"
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        line.addView(flat("×", color = muted) { trashTask(task) }.apply {
            textSize = 23f; setPadding(0, 0, 0, 0); contentDescription = "${task.text} 휴지통으로 이동"
        }, LinearLayout.LayoutParams(dp(40), dp(48)))
        check.setOnCheckedChangeListener { _, checked ->
            val tasks = store.tasks(); tasks.firstOrNull { it.id == task.id }?.done = checked
            store.saveTasks(tasks); renderPage()
        }
    }
    private fun renderTrash() {
        val tasks = store.tasks().filter { it.deletedAt > 0 }.sortedByDescending { it.deletedAt }
        if (tasks.isEmpty()) { page.addView(text("휴지통이 비어 있어요", 15f, muted).apply { setPadding(dp(8), dp(24), dp(8), 0) }); return }
        val card = card(page)
        tasks.forEach { task ->
            val line = row(card).apply { setPadding(dp(8), dp(4), 0, dp(4)) }
            val body = column()
            body.addView(text(task.text, 16f))
            body.addView(text(store.categories().firstOrNull { it.id == task.categoryId }?.name ?: "할 일", 12f, muted))
            line.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(flat("복원", color = accent) {
                val tasksNow = store.tasks().map { if (it.id == task.id) it.restore() else it }
                store.saveTasks(tasksNow); renderPage()
            }, LinearLayout.LayoutParams(dp(52), dp(48)))
            line.addView(flat("×", color = muted) { permanentlyDelete(task) }.apply { textSize = 23f; contentDescription = "${task.text} 영구 삭제" }, LinearLayout.LayoutParams(dp(40), dp(48)))
        }
    }
    private fun trashTask(task: Task, onMoved: () -> Unit = {}) {
        store.saveTasks(store.tasks().map { if (it.id == task.id) it.trash(System.currentTimeMillis()) else it })
        if (store.timer.taskId == task.id) TimerEngine.stop(this)
        if (store.selected == task.id) store.selected = ""
        renderPage(); onMoved()
    }
    private fun permanentlyDelete(task: Task) {
        AlertDialog.Builder(this).setTitle("영구 삭제할까요?").setMessage(task.text)
            .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                store.saveTasks(store.tasks().filterNot { it.id == task.id && it.deletedAt > 0 }); renderPage()
            }.show()
    }
    private fun persist(task: Task) {
        val tasks = store.tasks(); val index = tasks.indexOfFirst { it.id == task.id }
        if (index < 0) tasks.add(task) else tasks[index] = task
        store.saveTasks(tasks)
        if (store.timer.taskId == task.id && store.timer.title != task.text) { store.timer = store.timer.copy(title = task.text); TimerEngine.sync(this) }
        renderPage()
    }
    private fun selectTask(task: Task, run: Boolean, onApplied: () -> Unit = {}) {
        val replace = active() && (run || store.timer.taskId != task.id)
        val apply = {
            persist(task)
            if (replace || run) TimerEngine.stop(this)
            store.selected = task.id
            if (run) store.timer = TimerState("running", System.currentTimeMillis() + task.minutes * 60000L, title = task.text, taskId = task.id)
            TimerEngine.sync(this); renderPage(); onApplied(); applyWallpaper(task.text)
            if (run) requestNotifications()
        }
        if (replace) AlertDialog.Builder(this).setTitle("현재 집중을 종료할까요?").setMessage("기존 타이머를 종료하고 집중할 일을 바꿉니다.")
            .setNegativeButton("취소", null).setPositiveButton("변경") { _, _ -> apply() }.show()
        else apply()
    }
    private fun editTask(original: Task?) {
        val draft = original?.copy() ?: Task(text = "", minutes = store.presets[1], categoryId = store.category)
        val body = column().apply { setPadding(dp(20), dp(4), dp(20), dp(6)) }
        val title = EditText(this).apply {
            setText(draft.text); hint = "어떤 일에 집중할까요?"; textSize = 18f
            filters = arrayOf(InputFilter.LengthFilter(200)); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3; backgroundTintList = ColorStateList.valueOf(accent); contentDescription = "할 일 내용"
        }
        body.addView(title); body.addView(text("집중 시간", 13f, muted))
        val minutes = numberInput(draft.minutes)
        row(body).apply {
            addView(minutes, LinearLayout.LayoutParams(dp(64), dp(48))); addView(text("분", 14f, muted))
            store.presets.forEach { n -> addView(flat(n.toString(), soft, accent) { minutes.setText(n.toString()) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) }) }
        }
        body.addView(text("분류", 13f, muted))
        val categories = store.categories()
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories.map { it.name })
            setSelection(categories.indexOfFirst { it.id == draft.categoryId }.coerceAtLeast(0)); contentDescription = "할 일 분류"
        }
        body.addView(category, LinearLayout.LayoutParams(-1, dp(48)))
        gap(body, 12)
        val choices = row(body)
        val choose = flat("집중 선택", soft, accent) {}; val start = flat("집중 시작", accent, Color.WHITE) {}
        action(choices, choose); action(choices, start)
        val builder = AlertDialog.Builder(this).setTitle(if (original == null) "새 할 일" else "할 일 편집")
            .setView(ScrollView(this).apply { addView(body) }).setNegativeButton("닫기", null).setPositiveButton("저장", null)
        if (original != null) builder.setNeutralButton("휴지통", null)
        val dialog = builder.create()
        fun read(): Task? {
            val value = title.text.toString().trim(); val duration = minutes.text.toString().toIntOrNull()
            if (value.isEmpty()) { title.error = "할 일을 입력하세요"; return null }
            if (duration == null || duration !in 1..180) { minutes.error = "1~180분"; return null }
            return draft.copy(text = value, minutes = duration, categoryId = categories[category.selectedItemPosition].id)
        }
        fun close() { getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(title.windowToken, 0); dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { read()?.let { persist(it); close() } }
            choose.setOnClickListener { read()?.let { selectTask(it, false) { close() } } }
            start.setOnClickListener { read()?.let { selectTask(it, true) { close() } } }
            if (original != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { trashTask(original) { close() } }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            title.requestFocus(); title.setSelection(title.text.length)
        }
        dialog.show()
    }
    private fun editCategory(original: TaskCategory?) {
        val input = EditText(this).apply { setText(original?.name.orEmpty()); hint = "분류 이름"; isSingleLine = true; filters = arrayOf(InputFilter.LengthFilter(24)); setPadding(dp(24), dp(12), dp(24), dp(12)) }
        val dialog = AlertDialog.Builder(this).setTitle(if (original == null) "분류 추가" else "분류 이름 변경").setView(input).setNegativeButton("취소", null).setPositiveButton("저장", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) { input.error = "이름을 입력하세요"; return@setOnClickListener }
                if (store.categories().any { it.name == name && it.id != original?.id }) { input.error = "이미 있는 이름입니다"; return@setOnClickListener }
                val updated = original?.copy(name = name) ?: TaskCategory(name = name)
                store.saveCategories(if (original == null) store.categories() + updated else store.categories().map { if (it.id == original.id) updated else it })
                store.category = updated.id; dialog.dismiss(); renderPage()
            }
        }
        dialog.show()
    }
    private fun categoryMenu(category: TaskCategory) {
        val choices = if (category.id == DEFAULT_CATEGORY) arrayOf("이름 변경") else arrayOf("이름 변경", "분류 삭제")
        AlertDialog.Builder(this).setTitle(category.name).setItems(choices) { _, index ->
            if (index == 0) editCategory(category)
            else AlertDialog.Builder(this).setTitle("분류를 삭제할까요?").setMessage("할 일과 휴지통의 항목은 '${store.categories().first { it.id == DEFAULT_CATEGORY }.name}' 분류로 이동합니다.")
                .setNegativeButton("취소", null).setPositiveButton("분류 삭제") { _, _ -> store.removeCategory(category.id); renderPage() }.show()
        }.show()
    }
    private fun renderSettings() {
        section("집중 시간 프리셋") { card ->
            card.addView(text("새 할 일은 두 번째 값으로 시작합니다", 12f, muted))
            val inputs = store.presets.map { numberInput(it) }
            row(card).apply {
                inputs.forEachIndexed { index, input ->
                    val box = column().apply { setPadding(dp(6), 0, dp(6), 0) }
                    box.addView(text("${index + 1}번 · 분", 12f, muted)); box.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
                    addView(box, LinearLayout.LayoutParams(0, -2, 1f))
                }
            }
            card.addView(flat("프리셋 저장", soft, accent) {
                val values = inputs.map { it.text.toString().toIntOrNull() }
                val invalid = values.indexOfFirst { it == null || it !in 1..180 }
                if (invalid >= 0) inputs[invalid].error = "1~180분"
                else { store.presets = values.filterNotNull(); getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(inputs[0].windowToken, 0); toast("저장했어요") }
            }, LinearLayout.LayoutParams(-1, dp(44)))
        }
        section("분류") { card ->
            store.categories().forEach { c -> card.addView(flat(c.name, color = ink) { categoryMenu(c) }.apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START }, LinearLayout.LayoutParams(-1, dp(44))) }
            card.addView(flat("+ 분류 추가", color = accent) { editCategory(null) }, LinearLayout.LayoutParams(-1, dp(44)))
        }
        section("알림과 배경화면") { card ->
            settingRow(card, "앱 알림", if (TimerEngine.notificationsAllowed(this)) "허용됨" else "차단됨") {
                openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
            settingRow(card, "정확한 완료 알람", if (TimerEngine.exactAllowed(this)) "허용됨" else "허용 필요") {
                if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
            settingRow(card, "잠금화면 알림 안내", "") {
                AlertDialog.Builder(this).setTitle("잠금화면 알림").setMessage("휴대전화 설정에서 잠금화면 알림의 내용 표시를 허용하세요.\n\n정확한 완료 알람을 허용하지 않으면 절전 중 알림이 늦을 수 있습니다. 강제 중지한 뒤에는 앱을 다시 열어 주세요.").setPositiveButton("확인", null).show()
            }
            settingRow(card, "홈·잠금화면 배경 미리보기", "") { preview() }
        }
        page.addView(text("지금 하나 1.5", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) })
    }
    private fun settingRow(parent: LinearLayout, title: String, detail: String, action: () -> Unit) {
        parent.addView(flat(if (detail.isEmpty()) title else "$title  ·  $detail", color = ink, action = action).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START }, LinearLayout.LayoutParams(-1, dp(48)))
    }
    private fun section(title: String, content: (LinearLayout) -> Unit) {
        page.addView(text(title, 14f, muted).apply { setPadding(dp(4), dp(14), 0, dp(8)) })
        content(card(page).apply { setPadding(dp(12), dp(10), dp(12), dp(10)) })
    }
    @Suppress("DEPRECATION") private fun displaySize() = Point().also { windowManager.defaultDisplay.getRealSize(it) }
    private fun applyWallpaper(title: String) {
        val size = displaySize(); wallpaperBusy = true
        executor.execute {
            val result = runCatching {
                val bitmap = WallpaperRenderer.render(title, size.x, size.y)
                try { WallpaperRenderer.applyToBoth(this, bitmap) } finally { bitmap.recycle() }
            }
            runOnUiThread { if (!isDestroyed) { wallpaperBusy = false; result.exceptionOrNull()?.let { toast("배경화면 적용 실패: ${it.message}") } } }
        }
    }
    private fun preview() {
        if (wallpaperBusy) return
        val task = selected() ?: run { toast("먼저 집중할 일을 선택해 주세요."); return }
        val size = displaySize(); val width = minOf(size.x, 540); val height = (size.y.toLong() * width / size.x).toInt()
        val bitmap = WallpaperRenderer.render(task.text, width, height)
        val image = ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        AlertDialog.Builder(this).setTitle("홈·잠금화면 미리보기").setView(image).setNegativeButton("취소", null).setPositiveButton("두 화면에 적용") { _, _ -> applyWallpaper(task.text) }.create().apply {
            setOnDismissListener { image.setImageDrawable(null); bitmap.recycle() }; show()
        }
    }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        else if (!TimerEngine.notificationsAllowed(this)) toast("설정에서 앱 알림을 허용해 주세요.")
    }
    private fun openSettings(intent: Intent) { try { startActivity(intent) } catch (_: ActivityNotFoundException) { toast("휴대전화의 앱 설정에서 변경해 주세요.") } }
    override fun onResume() { super.onResume(); TimerEngine.sync(this); renderPage(); handler.removeCallbacks(tick); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onDestroy() { executor.shutdown(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("screen", screen); super.onSaveInstanceState(outState) }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); TimerEngine.sync(this)
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(parent: LinearLayout) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; parent.addView(this) }
    private fun card(parent: LinearLayout) = column().apply { background = shape(Color.WHITE, 8); setPadding(dp(4), dp(4), dp(4), dp(4)); parent.addView(this) }
    private fun shape(fill: Int, radius: Int = 6, stroke: Int? = null) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) } }
    private fun ripple(fill: Int) = RippleDrawable(ColorStateList.valueOf(Color.argb(28, 36, 90, 72)), shape(fill), shape(Color.WHITE))
    // TextViews with a ripple have no native Button elevation, insets, or state animator.
    private fun flat(label: String, fill: Int = Color.TRANSPARENT, color: Int = accent, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 14f; gravity = Gravity.CENTER; setTextColor(color)
        setPadding(dp(12), dp(4), dp(12), dp(4)); background = ripple(fill)
        isClickable = true; isFocusable = true; setAccessibilityDelegate(object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.className = android.widget.Button::class.java.name
            }
        })
        elevation = 0f; stateListAnimator = null; setOnClickListener { action() }
    }
    private fun text(value: String, size: Float = 16f, color: Int = ink) = TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, dp(3), 0, dp(3)) }
    private fun numberInput(value: Int) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; setText(value.toString()); textSize = 16f
        filters = arrayOf(InputFilter.LengthFilter(3)); backgroundTintList = ColorStateList.valueOf(accent); contentDescription = "집중 시간 (분)"
    }
    private fun action(parent: LinearLayout, view: View) { parent.addView(view, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) }) }
    private fun gap(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}

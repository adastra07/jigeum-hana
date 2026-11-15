package com.adastra.focuswallpaper

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val ink = Color.rgb(28, 47, 40)
    private val accent = Color.rgb(36, 90, 72)
    private val muted = Color.rgb(113, 126, 120)
    private val soft = Color.rgb(234, 242, 237)
    private val canvas = Color.rgb(245, 247, 245)
    private lateinit var store: Store
    private lateinit var focus: TextView
    private lateinit var status: TextView
    private lateinit var clock: TextView
    private lateinit var list: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var pause: Button
    private lateinit var wallpaper: Button
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var wallpaperBusy = false
    private val tick = object : Runnable {
        override fun run() {
            val timer = store.timer
            if (timer.status == "running" && timer.remaining(System.currentTimeMillis()) == 0L) TimerEngine.finish(this@MainActivity)
            updateFocus(); handler.postDelayed(this, 500)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = canvas; window.navigationBarColor = canvas
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        store = Store(this)
        // Legacy versions occasionally stored the title but no task ID.
        if (active() && store.timer.taskId.isEmpty()) {
            val id = store.tasks().firstOrNull { it.text == store.timer.title }?.id ?: store.selected
            store.timer = store.timer.copy(taskId = id)
            if (id.isNotEmpty()) store.selected = id
        }
        val root = column().apply { setPadding(dp(20), dp(12), dp(20), dp(24)); setBackgroundColor(canvas) }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
        row(root).apply {
            addView(text("지금 하나", 26f).apply { setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("설정", canvas, muted) { settings() })
        }
        gap(root, 12)
        val card = column().apply { background = shape(ink, 22); setPadding(dp(20), dp(16), dp(20), dp(18)) }
        root.addView(card)
        card.addView(text("지금 집중할 일", 13f, Color.rgb(192, 215, 201)))
        focus = text("", 24f, Color.WHITE).apply { setTypeface(null, Typeface.BOLD) }; card.addView(focus)
        status = text("", 13f, Color.rgb(206, 221, 213)); card.addView(status)
        clock = text("", 38f, Color.WHITE).apply { typeface = Typeface.MONOSPACE }; card.addView(clock)
        controls = row(card)
        pause = button("일시정지", Color.rgb(55, 77, 67), Color.WHITE) {
            val timer = store.timer; val now = System.currentTimeMillis()
            if (timer.status == "running" && timer.remaining(now) == 0L) TimerEngine.finish(this)
            else store.timer = if (timer.status == "running") timer.pause(now) else timer.resume(now)
            TimerEngine.sync(this); updateFocus()
        }
        action(controls, pause)
        action(controls, button("집중 종료", Color.rgb(55, 77, 67), Color.WHITE) { TimerEngine.stop(this); updateFocus() })
        gap(card, 10)
        wallpaper = button("홈·잠금화면 배경 미리보기", Color.WHITE, ink) { preview() }
        card.addView(wallpaper, LinearLayout.LayoutParams(-1, dp(48)))
        gap(root, 18)
        val checklist = column().apply { background = shape(Color.WHITE, 22, Color.rgb(225, 232, 226)); setPadding(dp(14), dp(12), dp(14), dp(14)) }
        root.addView(checklist)
        checklist.addView(text("체크리스트", 20f).apply { setTypeface(null, Typeface.BOLD) })
        checklist.addView(text("항목을 눌러 편집하고 집중 시간을 정하세요", 12f, muted))
        list = column(); checklist.addView(list); gap(checklist, 8)
        checklist.addView(button("+ 할 일 추가") { editTask(null) }, LinearLayout.LayoutParams(-1, dp(48)))
        renderTasks()
    }
    private fun active() = store.timer.status in listOf("running", "paused")
    private fun selected() = store.tasks().firstOrNull { it.id == store.selected }
    private fun updateFocus() {
        val timer = store.timer; val task = selected(); val active = active()
        focus.text = if (active || timer.status == "finished") timer.title else task?.text ?: "할 일을 선택해 주세요"
        status.text = when (timer.status) {
            "running" -> "집중 중"
            "paused" -> "일시정지"
            "finished" -> "집중 시간 완료"
            else -> if (task == null) "체크리스트에서 지금 할 일을 골라 보세요" else "${task.minutes}분 집중 준비"
        }
        clock.text = TimerEngine.formatTime(if (active) timer.remaining(System.currentTimeMillis()) else if (timer.status == "finished") 0 else (task?.minutes ?: 0) * 60000L)
        controls.visibility = if (active) View.VISIBLE else View.GONE
        pause.text = if (timer.status == "paused") "재개" else "일시정지"
        wallpaper.isEnabled = task != null && !wallpaperBusy
    }
    private fun renderTasks() {
        list.removeAllViews()
        val tasks = store.tasks()
        if (tasks.isEmpty()) list.addView(text("할 일을 추가해 보세요", 15f, muted))
        tasks.forEach { task ->
            val selected = task.id == store.selected
            val line = row(list).apply { background = shape(if (selected) soft else Color.WHITE, 12); minimumHeight = dp(64) }
            val check = CheckBox(this).apply { isChecked = task.done; buttonTintList = ColorStateList.valueOf(accent); contentDescription = "${task.text} 완료 여부" }
            line.addView(check, LinearLayout.LayoutParams(dp(48), dp(52)))
            val body = column().apply { isFocusable = true; setPadding(0, dp(8), dp(8), dp(8)); setOnClickListener { editTask(task) } }
            line.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(text(task.text, 17f, if (task.done) muted else ink).apply {
                if (task.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                if (selected) setTypeface(null, Typeface.BOLD)
            })
            body.addView(text(if (selected) "● 집중할 일 · ${task.minutes}분" else "${task.minutes}분 집중", 12f, if (selected) accent else muted))
            check.setOnCheckedChangeListener { _, checked ->
                val current = store.tasks(); current.firstOrNull { it.id == task.id }?.done = checked; store.saveTasks(current); renderTasks()
            }
        }
        updateFocus()
    }
    private fun persist(task: Task) {
        val tasks = store.tasks(); val index = tasks.indexOfFirst { it.id == task.id }
        if (index < 0) tasks.add(task) else tasks[index] = task
        store.saveTasks(tasks)
        if (store.timer.taskId == task.id && store.timer.title != task.text) {
            store.timer = store.timer.copy(title = task.text); TimerEngine.sync(this)
        }
        renderTasks()
    }
    private fun editTask(original: Task?) {
        val draft = original?.copy() ?: Task(text = "")
        val body = column().apply { setPadding(dp(20), dp(4), dp(20), dp(6)) }
        val title = EditText(this).apply {
            setText(draft.text); hint = "어떤 일에 집중할까요?"; textSize = 18f
            filters = arrayOf(InputFilter.LengthFilter(200)); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3; backgroundTintList = ColorStateList.valueOf(accent); contentDescription = "할 일 내용"
        }
        body.addView(title); body.addView(text("집중 시간", 13f, muted))
        val minutes = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(draft.minutes.toString()); filters = arrayOf(InputFilter.LengthFilter(3)); contentDescription = "집중 시간 (분)" }
        row(body).apply {
            addView(minutes, LinearLayout.LayoutParams(dp(72), dp(48))); addView(text("분", 14f, muted))
            listOf(5, 25, 60).forEach { n -> addView(button(n.toString()) { minutes.setText(n.toString()) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) }) }
        }
        gap(body, 12)
        val choices = row(body)
        val choose = button("집중 선택") {}; val start = button("집중 시작", accent, Color.WHITE) {}
        action(choices, choose); action(choices, start)
        val builder = AlertDialog.Builder(this).setTitle(if (original == null) "새 할 일" else "할 일 편집").setView(ScrollView(this).apply { addView(body) }).setNegativeButton("닫기", null).setPositiveButton("저장", null)
        if (original != null) builder.setNeutralButton("삭제", null)
        val dialog = builder.create()
        fun read(): Task? {
            val value = title.text.toString().trim(); val duration = minutes.text.toString().toIntOrNull()
            if (value.isEmpty()) { title.error = "할 일을 입력하세요"; return null }
            if (duration == null || duration !in 1..180) { minutes.error = "1~180분을 입력하세요"; return null }
            return draft.copy(text = value, minutes = duration)
        }
        fun close() { getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(title.windowToken, 0); dialog.dismiss() }
        fun chooseTask(run: Boolean) {
            val task = read() ?: return
            val replace = active() && (run || store.timer.taskId != task.id)
            val apply = {
                persist(task)
                if (replace || run) TimerEngine.stop(this)
                store.selected = task.id
                if (run) store.timer = TimerState("running", System.currentTimeMillis() + task.minutes * 60000L, title = task.text, taskId = task.id)
                TimerEngine.sync(this); renderTasks(); close(); applyWallpaper(task.text)
                if (run) requestNotifications()
            }
            if (replace) AlertDialog.Builder(this).setTitle("현재 집중을 종료할까요?").setMessage(if (run) "기존 타이머를 종료하고 ${task.minutes}분 집중을 새로 시작합니다." else "기존 타이머를 종료하고 집중할 일을 바꿉니다.").setNegativeButton("취소", null).setPositiveButton("변경") { _, _ -> apply() }.show()
            else apply()
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { read()?.let { persist(it); close() } }
            choose.setOnClickListener { chooseTask(false) }; start.setOnClickListener { chooseTask(true) }
            if (original != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this).setTitle("할 일을 삭제할까요?").setMessage(original.text).setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                    if (store.timer.taskId == original.id) TimerEngine.stop(this)
                    store.saveTasks(store.tasks().filterNot { it.id == original.id })
                    if (store.selected == original.id) store.selected = ""
                    renderTasks(); close()
                }.show()
            }
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            title.requestFocus(); title.setSelection(title.text.length)
        }
        dialog.show()
    }
    @Suppress("DEPRECATION")
    private fun displaySize() = Point().also { windowManager.defaultDisplay.getRealSize(it) }
    private fun applyWallpaper(title: String) {
        val size = displaySize(); wallpaperBusy = true; updateFocus()
        executor.execute {
            val result = runCatching {
                val bitmap = WallpaperRenderer.render(title, size.x, size.y)
                try { WallpaperRenderer.applyToBoth(this, bitmap) } finally { bitmap.recycle() }
            }
            runOnUiThread { if (!isDestroyed) { wallpaperBusy = false; updateFocus(); result.exceptionOrNull()?.let { toast("배경화면 적용 실패: ${it.message}") } } }
        }
    }
    private fun preview() {
        if (wallpaperBusy) return
        val task = selected() ?: return
        val size = displaySize()
        // Preview is bounded to avoid a second full-resolution bitmap in the UI.
        val width = minOf(size.x, 540); val height = (size.y.toLong() * width / size.x).toInt()
        val bitmap = WallpaperRenderer.render(task.text, width, height)
        val image = ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        AlertDialog.Builder(this).setTitle("홈·잠금화면 미리보기").setView(image).setNegativeButton("취소", null).setPositiveButton("두 화면에 적용") { _, _ -> applyWallpaper(task.text) }.create().apply {
            setOnDismissListener { image.setImageDrawable(null); bitmap.recycle() }; show()
        }
    }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        else if (!TimerEngine.notificationsAllowed(this)) toast("잠금화면에 표시하려면 설정에서 앱 알림을 허용하세요.")
    }
    private fun settings() {
        AlertDialog.Builder(this).setTitle("설정").setItems(arrayOf("알림 · 잠금화면 안내", "정확한 완료 알람 허용")) { _, index ->
            if (index == 0) AlertDialog.Builder(this).setTitle("잠금화면 알림").setMessage("앱 알림과 휴대전화의 잠금화면 알림 ‘내용 표시’를 허용하세요.\n\n정확한 완료 알람을 허용하지 않으면 절전 상태에서 완료 알림이 늦을 수 있습니다. 앱 강제 종료 후에는 다시 앱을 열어야 복구됩니다.\n\n진행 알림을 지웠다면 앱을 다시 열어 복원할 수 있습니다.").setNegativeButton("닫기", null).setPositiveButton("앱 알림 설정") { _, _ -> openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }.show()
            else if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            else toast("이 Android 버전은 별도 권한이 필요 없습니다.")
        }.setNegativeButton("닫기", null).show()
    }
    private fun openSettings(intent: Intent) { try { startActivity(intent) } catch (_: ActivityNotFoundException) { toast("휴대전화 설정 → 애플리케이션 → 지금 하나에서 설정해 주세요.") } }
    override fun onResume() { super.onResume(); TimerEngine.sync(this); renderTasks(); handler.removeCallbacks(tick); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onDestroy() { executor.shutdown(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); TimerEngine.sync(this)
        if (!TimerEngine.notificationsAllowed(this)) toast("타이머는 실행되지만 알림이 차단되어 있습니다. 설정에서 알림을 허용하세요.")
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(parent: LinearLayout) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; parent.addView(this) }
    private fun shape(fill: Int, radius: Int = 16, stroke: Int? = null) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) } }
    private fun text(value: String, size: Float = 16f, color: Int = ink) = TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, dp(4), 0, dp(4)) }
    private fun button(label: String, fill: Int = soft, color: Int = accent, action: () -> Unit) = Button(this).apply { text = label; textSize = 14f; isAllCaps = false; setTextColor(color); background = shape(fill, 12); minHeight = dp(48); minimumHeight = dp(48); setPadding(dp(12), dp(8), dp(12), dp(8)); setOnClickListener { action() } }
    private fun action(parent: LinearLayout, view: View) { parent.addView(view, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) }) }
    private fun gap(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}

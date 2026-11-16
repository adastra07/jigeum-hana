package com.adastra.focuswallpaper

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import java.util.Locale

// System chronometers display elapsed/remaining time without a continuously running service.
object TimerEngine {
    private const val PROGRESS = "focus_progress"
    private const val FINISHED = "focus_finished"
    private fun manager(c: Context) = c.getSystemService(NotificationManager::class.java)
    private fun alarm(c: Context) = c.getSystemService(AlarmManager::class.java)
    private fun pending(c: Context, action: String, request: Int) = PendingIntent.getBroadcast(c, request, Intent(c, TimerReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun boot(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun elapsed(c: Context, timer: TimerState = Store(c).timer): Long = timer.elapsed(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(c))
    fun taskElapsed(c: Context, task: Task): Long {
        val timer = Store(c).timer
        return if (timer.isStopwatch && timer.taskId == task.id && timer.status in listOf("running", "paused")) elapsed(c, timer) else task.stopwatchMs
    }
    fun exactAllowed(c: Context) = Build.VERSION.SDK_INT < 31 || alarm(c).canScheduleExactAlarms()
    fun notificationsAllowed(c: Context) = manager(c).areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    private fun channels(c: Context) {
        manager(c).createNotificationChannel(NotificationChannel(PROGRESS, "집중 시간", NotificationManager.IMPORTANCE_LOW).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
        manager(c).createNotificationChannel(NotificationChannel(FINISHED, "집중 완료", NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
    }
    private fun open(c: Context) = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun saveState(c: Context, state: TimerState) {
        val store = Store(c)
        if (state.isStopwatch) store.saveTimerAndTasks(state, store.tasks().map {
            if (it.id == state.taskId) it.copy(stopwatchMs = elapsed(c, state)) else it
        }) else store.timer = state
    }
    fun start(c: Context, task: Task) {
        stop(c)
        val store = Store(c)
        val current = store.tasks().firstOrNull { it.id == task.id && it.deletedAt == 0L } ?: return
        if (!current.hasClock) return
        val now = System.currentTimeMillis()
        val state = if (current.stopwatch) TimerState("running", title = current.text, taskId = current.id,
            mode = "stopwatch", elapsedMs = current.stopwatchMs, startedAt = now,
            startedRealtime = SystemClock.elapsedRealtime(), bootCount = boot(c))
        else TimerState("running", now + current.minutes * 60000L, title = current.text, taskId = current.id)
        saveState(c, state); sync(c)
    }
    fun pause(c: Context) {
        val state = Store(c).timer
        val now = System.currentTimeMillis()
        if (!state.isStopwatch && state.status == "running" && state.remaining(now) == 0L) { finish(c); return }
        saveState(c, state.pause(now, SystemClock.elapsedRealtime(), boot(c))); sync(c)
    }
    fun resume(c: Context) {
        saveState(c, Store(c).timer.resume(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(c))); sync(c)
    }
    fun restart(c: Context) {
        val timer = Store(c).timer
        if (!timer.isStopwatch || timer.status !in listOf("running", "paused")) return
        saveState(c, timer.restart(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(c))); sync(c)
    }
    fun sync(c: Context) {
        var timer = Store(c).timer
        val now = System.currentTimeMillis()
        if (timer.isStopwatch && timer.status == "running" && timer.bootCount != boot(c)) {
            timer = timer.copy(elapsedMs = elapsed(c, timer), startedAt = now,
                startedRealtime = SystemClock.elapsedRealtime(), bootCount = boot(c))
            saveState(c, timer)
        }
        if (!timer.isStopwatch && timer.status == "running" && timer.remaining(now) == 0L) { finish(c); return }
        alarm(c).cancel(pending(c, "finish", 0))
        if (!timer.isStopwatch && timer.status == "running") {
            try {
                if (exactAllowed(c)) alarm(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, pending(c, "finish", 0))
                else alarm(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, pending(c, "finish", 0))
            } catch (_: SecurityException) { alarm(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, pending(c, "finish", 0)) }
        }
        if (timer.status !in listOf("running", "paused")) { manager(c).cancel(100); return }
        channels(c)
        if (!notificationsAllowed(c)) { manager(c).cancel(100); return }
        val running = timer.status == "running"
        val description = if (timer.isStopwatch) {
            if (running) "스탑워치 · 경과 시간" else "일시정지 · ${formatElapsed(timer.elapsedMs)} 경과"
        } else if (running) "집중 중 · 남은 시간" else "일시정지 · ${formatTime(timer.pausedMs)} 남음"
        val builder = Notification.Builder(c, PROGRESS).setSmallIcon(R.drawable.ic_focus)
            .setContentTitle(timer.title).setContentText(description).setContentIntent(open(c))
            .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM).setShowWhen(running).setUsesChronometer(running)
            .setChronometerCountDown(!timer.isStopwatch)
        if (timer.isStopwatch) builder.addAction(Notification.Action.Builder(null as Icon?, "재시작", pending(c, "restart", 3)).build())
        builder.addAction(Notification.Action.Builder(null as Icon?, if (running) "일시정지" else "재개", pending(c, if (running) "pause" else "resume", 1)).build())
            .addAction(Notification.Action.Builder(null as Icon?, "집중 해제", pending(c, "stop", 2)).build())
        if (running) builder.setWhen(if (timer.isStopwatch) now - elapsed(c, timer) else timer.endAt)
        manager(c).notify(100, builder.build())
    }
    fun finish(c: Context) {
        val store = Store(c)
        val timer = store.timer
        if (timer.status != "running" || timer.isStopwatch) return
        if (timer.remaining(System.currentTimeMillis()) > 0) { sync(c); return }
        store.timer = timer.copy(status = "finished", endAt = 0, pausedMs = 0)
        alarm(c).cancel(pending(c, "finish", 0)); manager(c).cancel(100); channels(c)
        if (notificationsAllowed(c)) manager(c).notify(101, Notification.Builder(c, FINISHED).setSmallIcon(R.drawable.ic_focus).setContentTitle("집중 시간 완료").setContentText(timer.title).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open(c)).build())
    }
    fun clearFocus(c: Context, finished: (Result<Unit>) -> Unit = {}) {
        val store = Store(c)
        val hadFocus = store.selected.isNotEmpty() || store.timer.status != "idle"
        store.selected = ""; stop(c)
        WallpaperRenderer.clearFocusText(c, hadFocus, finished)
    }
    fun stop(c: Context) {
        val store = Store(c)
        val timer = store.timer
        if (timer.isStopwatch) store.saveTimerAndTasks(TimerState(), store.tasks().map {
            if (it.id == timer.taskId) it.copy(stopwatchMs = elapsed(c, timer)) else it
        }) else store.timer = TimerState()
        manager(c).cancel(101); sync(c)
    }
    fun formatTime(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0) + 999) / 1000
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }
    fun formatElapsed(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
            else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "finish" -> TimerEngine.finish(context)
            "stop" -> {
                val pendingResult = goAsync()
                TimerEngine.clearFocus(context) { pendingResult.finish() }
            }
            "pause" -> TimerEngine.pause(context)
            "resume" -> TimerEngine.resume(context)
            "restart" -> TimerEngine.restart(context)
            else -> TimerEngine.sync(context)
        }
    }
}

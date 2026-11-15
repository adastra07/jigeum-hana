package com.adastra.focuswallpaper

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import java.util.Locale

// AlarmManager owns completion; the system chronometer renders the notification without a service or wake lock.
object TimerEngine {
    private const val PROGRESS = "focus_progress"
    private const val FINISHED = "focus_finished"
    private fun manager(c: Context) = c.getSystemService(NotificationManager::class.java)
    private fun alarm(c: Context) = c.getSystemService(AlarmManager::class.java)
    private fun pending(c: Context, action: String, request: Int) = PendingIntent.getBroadcast(c, request, Intent(c, TimerReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun exactAllowed(c: Context) = Build.VERSION.SDK_INT < 31 || alarm(c).canScheduleExactAlarms()
    fun notificationsAllowed(c: Context) = manager(c).areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    private fun channels(c: Context) {
        manager(c).createNotificationChannel(NotificationChannel(PROGRESS, "집중 타이머", NotificationManager.IMPORTANCE_LOW).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
        manager(c).createNotificationChannel(NotificationChannel(FINISHED, "집중 완료", NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC })
    }
    private fun open(c: Context) = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun sync(c: Context) {
        val timer = Store(c).timer
        if (timer.status == "running" && timer.remaining(System.currentTimeMillis()) == 0L) { finish(c); return }
        alarm(c).cancel(pending(c, "finish", 0))
        if (timer.status == "running") {
            try {
                if (exactAllowed(c)) alarm(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, pending(c, "finish", 0))
                else alarm(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, pending(c, "finish", 0))
            } catch (_: SecurityException) { alarm(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAt, pending(c, "finish", 0)) }
        }
        if (timer.status !in listOf("running", "paused")) { manager(c).cancel(100); return }
        channels(c)
        if (!notificationsAllowed(c)) { manager(c).cancel(100); return }
        val running = timer.status == "running"
        val builder = Notification.Builder(c, PROGRESS).setSmallIcon(R.drawable.ic_focus)
            .setContentTitle(timer.title).setContentText(if (running) "집중 중 · 남은 시간" else "일시정지 · ${formatTime(timer.pausedMs)} 남음")
            .setContentIntent(open(c)).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM).setShowWhen(running).setUsesChronometer(running).setChronometerCountDown(running)
            .addAction(Notification.Action.Builder(null as Icon?, if (running) "일시정지" else "재개", pending(c, if (running) "pause" else "resume", 1)).build())
            .addAction(Notification.Action.Builder(null as Icon?, "종료", pending(c, "stop", 2)).build())
        if (running) builder.setWhen(timer.endAt)
        manager(c).notify(100, builder.build())
    }
    fun finish(c: Context) {
        val store = Store(c)
        val timer = store.timer
        if (timer.status != "running") return
        if (timer.remaining(System.currentTimeMillis()) > 0) { sync(c); return }
        store.timer = timer.copy(status = "finished", endAt = 0, pausedMs = 0)
        alarm(c).cancel(pending(c, "finish", 0)); manager(c).cancel(100); channels(c)
        if (notificationsAllowed(c)) manager(c).notify(101, Notification.Builder(c, FINISHED).setSmallIcon(R.drawable.ic_focus).setContentTitle("집중 시간 완료").setContentText(timer.title).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open(c)).build())
    }
    fun stop(c: Context) { Store(c).timer = TimerState(); manager(c).cancel(101); sync(c) }
    fun formatTime(ms: Long): String { val seconds = (ms.coerceAtLeast(0) + 999) / 1000; return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60) }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        val now = System.currentTimeMillis()
        when (intent.action) {
            "finish" -> { TimerEngine.finish(context); return }
            "stop" -> { TimerEngine.stop(context); return }
            "pause" -> {
                if (store.timer.status == "running" && store.timer.remaining(now) == 0L) { TimerEngine.finish(context); return }
                store.timer = store.timer.pause(now)
            }
            "resume" -> store.timer = store.timer.resume(now)
        }
        TimerEngine.sync(context)
    }
}

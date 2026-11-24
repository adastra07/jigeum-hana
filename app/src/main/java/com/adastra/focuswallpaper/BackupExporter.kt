package com.adastra.focuswallpaper

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object BackupExporter {
    const val REQUEST_CODE = 1260
    private val busy = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    fun createDocumentIntent(): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/vnd.sqlite3"
        putExtra(Intent.EXTRA_TITLE, "jigeum-hana-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.db")
    }
    fun isBusy() = busy.get()
    fun export(context: Context, destination: Uri) {
        val app = context.applicationContext
        fun message(text: String) { Toast.makeText(app, text, Toast.LENGTH_SHORT).show() }
        if (!busy.compareAndSet(false, true)) { message("백업 중입니다"); return }
        val snapshot = try { BackupSnapshot.capture(app) } catch (_: Exception) {
            busy.set(false); message("백업할 데이터를 읽지 못했습니다"); return
        }
        message("백업 파일을 만들고 있습니다")
        worker.execute {
            var temp: File? = null
            val success = runCatching {
                temp = File.createTempFile("jigeum-backup-", ".db", app.cacheDir)
                BackupDatabase.write(app, temp!!, snapshot)
                val output = app.contentResolver.openOutputStream(destination, "wt") ?: error("No output stream")
                output.use { stream -> temp!!.inputStream().use { it.copyTo(stream) } }
            }.isSuccess
            runCatching { temp?.let { SQLiteDatabase.deleteDatabase(it) } }
            busy.set(false)
            main.post { message(if (success) "백업 파일을 저장했습니다" else "백업 파일을 저장하지 못했습니다") }
        }
    }
}

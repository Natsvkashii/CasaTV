package it.casatv.server

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import it.casatv.server.core.Prefs
import it.casatv.server.service.RestartReceiver
import java.io.File

class ServerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // NanoHTTPD stores multipart uploads in java.io.tmpdir. Android's default
        // temp folder is not always writable, so point it at the app cache.
        val temp = File(cacheDir, "http").apply { mkdirs() }
        System.setProperty("java.io.tmpdir", temp.absolutePath)

        // Install uncaught exception handler for autonomous crash recovery
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CasaTV", "Crash non gestito su ${thread.name}: ${throwable.message}", throwable)
            if (Prefs.autoStart(this)) {
                runCatching {
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    if (alarmManager != null) {
                        val intent = Intent(this, RestartReceiver::class.java)
                        val pendingIntent = PendingIntent.getBroadcast(
                            this,
                            997,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                        val triggerAt = System.currentTimeMillis() + 1500L
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                        } else {
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                        }
                    }
                }
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}

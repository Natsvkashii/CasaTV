package it.casatv.server.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import it.casatv.server.core.Prefs

/**
 * Watchdog receiver invoked by AlarmManager or crash handler to revive ServerService
 * if it was terminated, suspended or killed by Android OS.
 */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i("CasaTV", "RestartReceiver: risveglio autonomo del server")
        if (Prefs.autoStart(context)) {
            runCatching {
                ServerService.start(context)
            }.onFailure { error ->
                Log.e("CasaTV", "RestartReceiver: errore avvio ServerService", error)
            }
        }
    }
}

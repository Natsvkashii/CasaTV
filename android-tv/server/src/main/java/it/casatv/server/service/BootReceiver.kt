package it.casatv.server.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import it.casatv.server.core.Prefs

/**
 * Autonomously starts CasaTV Server upon system boot, reboot or package update
 * so that Android TVs on the LAN can immediately find and stream from the server.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i("CasaTV", "BootReceiver: ricevuto $action")
        if (Prefs.autoStart(context)) {
            Log.i("CasaTV", "BootReceiver: avvio autonomo ServerService")
            runCatching {
                ServerService.start(context)
            }.onFailure { error ->
                Log.e("CasaTV", "BootReceiver: errore avvio ServerService", error)
            }
        }
    }
}

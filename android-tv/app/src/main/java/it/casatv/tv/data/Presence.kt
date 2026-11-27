package it.casatv.tv.data

/**
 * Tiny process-wide bridge between the UI and the foreground service so the
 * notification can reflect what the TV is currently playing.
 */
object Presence {
    @Volatile
    var profileName: String = ""

    @Volatile
    var playbackState: String = "idle"

    @Volatile
    var playbackChannel: String = ""

    fun playing(channel: String) {
        playbackState = "playing"
        playbackChannel = channel
    }

    fun idle() {
        playbackState = "idle"
        playbackChannel = ""
    }
}

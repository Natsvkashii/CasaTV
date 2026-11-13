package it.casatv.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import it.casatv.tv.data.Channel
import java.util.Locale

/**
 * Small, allocation-light translation layer. The app carries Italian, English
 * and Spanish UI copy; channel and country names come from the server already
 * localized for the active profile.
 *
 * Existing call sites pass (italian, english). Spanish is resolved from
 * [SPANISH] by Italian source string, or can be passed explicitly as a third
 * argument for new strings. When a translation is missing the English copy is
 * used, so an untranslated string never falls back to Italian.
 */
class AppStrings(val language: String = "it") {
    val english: Boolean = language.lowercase(Locale.ROOT).startsWith("en")
    val spanish: Boolean = language.lowercase(Locale.ROOT).startsWith("es")

    fun t(italian: String, englishText: String, spanishText: String? = null): String =
        when {
            spanish -> spanishText ?: SPANISH[italian] ?: englishText
            english -> englishText
            else -> italian
        }
}

val LocalAppStrings = staticCompositionLocalOf { AppStrings("it") }

@Composable
fun tr(italian: String, english: String, spanish: String? = null): String =
    LocalAppStrings.current.t(italian, english, spanish)

fun localize(language: String, italian: String, english: String, spanish: String? = null): String {
    val code = language.lowercase(Locale.ROOT)
    return when {
        code.startsWith("es") -> spanish ?: SPANISH[italian] ?: english
        code.startsWith("en") -> english
        else -> italian
    }
}

fun localizedSubtitle(channel: Channel, language: String): String {
    val category = channel.categoryNames.firstOrNull()
        ?: channel.categories.firstOrNull()?.replaceFirstChar { it.uppercase() }
        ?: localize(language, "Canale", "Channel", "Canal")
    return listOfNotNull(category, channel.quality.takeIf { it.isNotBlank() })
        .joinToString(" · ")
}

/**
 * Italian source string -> Spanish. Keys must match the literal passed as the
 * first argument of t()/tr()/localize().
 */
private val SPANISH: Map<String, String> = mapOf(
    "Cambia profilo" to "Cambiar perfil",
    "Ora" to "Ahora",
    "Preferito" to "Favorito",
    "Qualcosa non ha funzionato" to "Algo ha fallado",
    "Riprova" to "Reintentar",
    "Online" to "En línea",
    "Offline" to "Sin conexión",
    "Da verificare" to "Por verificar",
    "1 canale" to "1 canal",
    "Catalogo IPTV" to "Catálogo IPTV",
    "Tutti i canali" to "Todos los canales",
    "Filtra per paese o categoria, oppure cerca per nome." to
        "Filtra por país o categoría, o busca por nombre.",
    "Carico…" to "Cargando…",
    "Ricarica" to "Recargar",
    "Tutti i paesi" to "Todos los países",
    "Tutte le categorie" to "Todas las categorías",
    "Cerco i canali…" to "Buscando canales…",
    "Nessun canale trovato" to "No se han encontrado canales",
    "Prova a cambiare paese o categoria, oppure cerca un altro nome." to
        "Prueba a cambiar de país o categoría, o busca otro nombre.",
    "Azzera filtri" to "Borrar filtros",
    "Fine del catalogo · 1 canale visualizzato" to "Fin del catálogo · 1 canal mostrado",
    "Cerca un canale per nome…" to "Busca un canal por nombre…",
    "Cancella ricerca" to "Borrar búsqueda",
    "La tua selezione" to "Tu selección",
    "Preferiti" to "Favoritos",
    "Premi MENU su un canale per aggiungerlo qui." to
        "Pulsa MENÚ en un canal para añadirlo aquí.",
    "1 canale salvato sul server" to "1 canal guardado en el servidor",
    "aggiunge o rimuove un canale" to "añade o quita un canal",
    "Sfoglia i canali" to "Explorar canales",
    "Carico i preferiti…" to "Cargando favoritos…",
    "Nessun preferito" to "Sin favoritos",
    "Vai ai canali" to "Ir a canales",
    "Carico il catalogo IPTV…" to "Cargando el catálogo IPTV…",
    "Nessun canale disponibile" to "No hay canales disponibles",
    "Il server è connesso ma il catalogo è vuoto. Avvia un aggiornamento dal pannello web di CasaTV." to
        "El servidor está conectado pero el catálogo está vacío. Inicia una actualización desde el panel web de CasaTV.",
    "Suggerimento: premi MENU su un canale per aggiungerlo ai preferiti." to
        "Consejo: pulsa MENÚ en un canal para añadirlo a favoritos.",
    "CASA TV · IPTV LOCALE" to "CASA TV · IPTV LOCAL",
    "Scegli un canale e premi OK. Durante la visione usa ▲ ▼ per cambiare canale." to
        "Elige un canal y pulsa OK. Mientras ves la TV, usa ▲ ▼ para cambiar de canal.",
    "EPG attiva" to "EPG disponible",
    "EPG non disponibile" to "EPG no disponible",
    "Vedi tutti" to "Ver todo",
    "Nessun paese disponibile" to "No hay países disponibles",
    "Canale \$number non trovato" to "Canal \$number no encontrado",
    "Tutte le sorgenti di questo canale non sono raggiungibili in questo momento." to
        "Todas las fuentes de este canal no están disponibles en este momento.",
    "Questo canale non ha un flusso disponibile." to
        "Este canal no tiene ningún flujo disponible.",
    "Sintonizzazione…" to "Sintonizando…",
    "ORA IN ONDA" to "AHORA EN DIRECTO",
    "Nessuna informazione dalla guida TV" to "Sin información de la guía TV",
    "Cambia paese · INFO" to "Cambiar país · INFO",
    "Cambia canale" to "Cambiar canal",
    "Vai al canale" to "Ir al canal",
    "Info" to "Información",
    "Rimuovi preferito" to "Quitar de favoritos",
    "Aggiungi preferito" to "Añadir a favoritos",
    "Paese TV" to "País de TV",
    "Esci" to "Salir",
    "VAI AL CANALE" to "IR AL CANAL",
    "Attendi o premi OK per confermare" to "Espera o pulsa OK para confirmar",
    "Canale non disponibile" to "Canal no disponible",
    "▲ ▼  cambia canale      OK  riprova      BACK  esci" to
        "▲ ▼  cambiar canal      OK  reintentar      BACK  salir",
    "PAESE DELLA MODALITÀ TV" to "PAÍS DEL MODO TV",
    "I canali ▲ ▼ partiranno da questo paese" to
        "El zapping con ▲ ▼ empezará en este país",
    "▲ ▼ scegli · OK conferma · BACK annulla" to
        "▲ ▼ elegir · OK confirmar · BACK cancelar",
    "CASA TV · PROFILI" to "CASA TV · PERFILES",
    "Chi sta guardando?" to "¿Quién está viendo?",
    "Ogni profilo ha la sua lingua, il suo paese, i preferiti e la cronologia." to
        "Cada perfil tiene su idioma, país, favoritos e historial.",
    "Scegli un profilo per continuare" to "Elige un perfil para continuar",
    "Torna indietro" to "Volver",
    "Fine" to "Hecho",
    "Gestisci profili" to "Gestionar perfiles",
    "Aggiorno il profilo…" to "Actualizando el perfil…",
    "Modifica" to "Editar",
    "Nuovo profilo" to "Nuevo perfil",
    "Nome, avatar e paese" to "Nombre, avatar y país",
    "PROFILI CASA TV" to "PERFILES CASA TV",
    "Annulla" to "Cancelar",
    "Salva" to "Guardar",
    "Modifica profilo" to "Editar perfil",
    "Il profilo decide lingua dell’app, canali consigliati e coda della modalità TV." to
        "El perfil define el idioma de la app, los canales recomendados y la cola del modo TV.",
    "Scegli un avatar" to "Elige un avatar",
    "Anteprima" to "Vista previa",
    "Paese del profilo" to "País del perfil",
    "I consigliati e la coda della modalità TV partiranno da questo paese." to
        "Las recomendaciones y la cola del modo TV empezarán en este país.",
    "Eliminare questo profilo?" to "¿Eliminar este perfil?",
    "Preferiti, cronologia e impostazioni del profilo verranno rimossi dal server." to
        "Los favoritos, el historial y la configuración del perfil se eliminarán del servidor.",
    "Elimina" to "Eliminar",
    "Nome del profilo" to "Nombre del perfil",
    "Es. Marco" to "Ej. Marco",
    "Lingua dell’app" to "Idioma de la app",
    "Paese preferito" to "País preferido",
    "Cambia paese" to "Cambiar país",
    "Elimina profilo" to "Eliminar perfil",
    "Consigliati, preferiti e cronologia personali" to
        "Recomendaciones, favoritos e historial personales",
    "Chiudi" to "Cerrar",
    "Cerca un paese…" to "Buscar un país…",
    "Nessun paese trovato" to "No se ha encontrado ningún país",
    "Il paese si può cambiare anche durante la visione con il tasto INFO." to
        "También puedes cambiar el país mientras ves la TV con la tecla INFO.",
    "Sistema" to "Sistema",
    "Impostazioni" to "Ajustes",
    "Profilo, lingua, paese, catalogo e guida TV." to
        "Perfil, idioma, país, catálogo y guía TV.",
    "Catalogo" to "Catálogo",
    "Dati indicizzati dal server CasaTV" to "Datos indexados por el servidor CasaTV",
    "Canali" to "Canales",
    "Flussi" to "Flujos",
    "Paesi" to "Países",
    "Nascosti" to "Ocultos",
    "Programmi EPG" to "Programas EPG",
    "Stato EPG" to "Estado EPG",
    "Attiva" to "Activa",
    "Manutenzione" to "Mantenimiento",
    "Le operazioni vengono eseguite dal server in background." to
        "Las operaciones se ejecutan en el servidor en segundo plano.",
    "Sorgente M3U" to "Fuente M3U",
    "Ricarica dati" to "Recargar datos",
    "Aggiorna catalogo" to "Actualizar catálogo",
    "Aggiorna guida TV" to "Actualizar guía TV",
    "Cambia server" to "Cambiar servidor",
    "Telecomando" to "Mando a distancia",
    "Comandi disponibili durante la navigazione e la visione." to
        "Teclas disponibles durante la navegación y la reproducción.",
    "Cambia canale durante la visione" to "Cambiar de canal mientras ves la TV",
    "Mostra o nascondi le informazioni sul canale" to
        "Mostrar u ocultar la información del canal",
    "Digita il numero del canale e premi OK per andare" to
        "Escribe el número de canal y pulsa OK para sintonizar",
    "Aggiunge o rimuove il canale dai preferiti" to
        "Añade o quita el canal de favoritos",
    "Cambia il paese della modalità TV" to "Cambiar el país del modo TV",
    "Torna indietro o chiude il canale in visione" to
        "Volver o cerrar el canal en reproducción",
    "Informazioni" to "Información",
    "CasaTV per Android TV" to "CasaTV para Android TV",
    "Catalogo iptv-org · Proxy HLS locale" to "Catálogo iptv-org · Proxy HLS local",
    "Il profilo usa questo paese per consigliati, Home e coda della modalità TV." to
        "El perfil usa este país para recomendaciones, Inicio y la cola del modo TV.",
    "Scollegare il server?" to "¿Desconectar el servidor?",
    "Scollega" to "Desconectar",
    "Profilo e preferenze" to "Perfil y preferencias",
    "Lingua, paese e preferiti sono salvati per questo profilo." to
        "El idioma, el país y los favoritos se guardan para este perfil.",
    "LINGUA DELL’APP" to "IDIOMA DE LA APP",
    "PAESE PREFERITO" to "PAÍS PREFERIDO",
    "Server collegato" to "Servidor conectado",
    "CasaTV è raggiungibile sulla rete locale." to
        "CasaTV está disponible en la red local.",
    "VERSIONE SERVER" to "VERSIÓN DEL SERVIDOR",
    "rete" to "red",
    "Guida TV" to "Guía TV",
    "Palinsesto disponibile per i canali abbinati." to
        "Programación disponible para los canales asociados.",
    "Nessun palinsesto caricato. Avvia un aggiornamento da Manutenzione." to
        "No hay programación cargada. Inicia una actualización desde Mantenimiento.",
    "Fonti" to "Fuentes",
    "Programmi" to "Programas",
    "Canali abbinati" to "Canales asociados",
    "Stato" to "Estado",
    "Non attiva" to "Inactiva",
)

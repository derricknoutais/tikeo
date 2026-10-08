package com.derricknoutais.tikeo

import android.app.Application
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Un seul pilote pour toute l'application : le service local et la WebView
 * impriment par la même file, et deux reçus ne s'entrelacent jamais.
 */
class TikeoApp : Application() {

    val reglages by lazy { Reglages(this) }

    /** Le pilote du terminal, reconnu une fois pour toutes au premier usage. */
    val pilote: Pilote by lazy { Pilotes.choisir(this) }

    /** L'état de l'imprimante, avec le pilote choisi, le terminal reconnu et ce qu'il sait faire. */
    fun etat(): JSONObject = pilote.etat()
        .put("pilote", pilote.nom)
        .put("terminal", Pilotes.terminal())
        .put("capacites", pilote.capacites())

    val simulation: Boolean get() = pilote.nom == PiloteSimulation.SIMULATION

    // Le journal de l'onglet Test et l'impression en cours vivent ici, pas dans l'écran : ils
    // survivent à une rotation, à un changement de langue, et au verdict qui arrive après coup.

    val journal = mutableListOf<Entree>()
    var impressionEnCours = false

    /** Prévenu, sur le fil de l'interface, de chaque changement du journal ou de l'impression en cours. */
    var ecouteur: (() -> Unit)? = null

    private val principal by lazy { Handler(Looper.getMainLooper()) }

    fun surInterface(action: () -> Unit) {
        principal.post(action)
    }

    fun noter(texte: String, genre: Genre) = surInterface {
        journal.add(0, Entree(SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date()), texte, genre))
        while (journal.size > 8) journal.removeAt(journal.size - 1)
        ecouteur?.invoke()
    }

    fun finImpression() = surInterface {
        impressionEnCours = false
        ecouteur?.invoke()
    }
}

/** Une ligne du journal de l'onglet Test. */
class Entree(val heure: String, val texte: String, val genre: Genre)

enum class Genre { OK, ERREUR, INFO }

package com.derricknoutais.tikeo

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.derricknoutais.sunmiprint.ImprimanteSunmi
import com.derricknoutais.zcsprint.AfficheurZcs
import com.derricknoutais.zcsprint.ImprimanteZcs
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Ce que la page demande pour une impression. */
data class OptionsImpression(
    /** Lignes blanches sous un reçu, pour dépasser la barre ou le massicot. */
    val avance: Int = 3,
    /** Vrai pour du papier étiquette : l'image est une étiquette, imprimée `copies` fois. */
    val etiquette: Boolean = false,
    val copies: Int = 1,
    /** Vrai pour ouvrir le tiroir-caisse avec ce reçu. */
    val tiroir: Boolean = false,
)

/**
 * Ce que Tikéo attend d'un pilote de terminal.
 *
 * Chaque marque a le sien, dans son propre dépôt — sunmi-print, zcs-print —,
 * sans rien savoir de Tikéo : ils parlent le même vocabulaire JSON
 * (`{code, message, largeur, modele}`, `{ok}` ou `{ok: false, code, message}`),
 * et ces quelques lignes les branchent sur l'application.
 */
interface Pilote {
    /** `sunmi`, `zcs`, `simulation` : ce que la page lit dans l'état. */
    val nom: String

    fun etat(): JSONObject

    /**
     * Ce que le terminal sait faire : `{massicot, etiquettes, tiroir, afficheur}`,
     * `afficheur` valant `{largeur, hauteur}` ou `null`, `etiquettes` et
     * `tiroir` `null` quand le pilote ne peut pas le savoir d'avance.
     */
    fun capacites(): JSONObject

    /** Imprime l'image et appelle `fini` une seule fois, avec le verdict de l'imprimante. */
    fun imprimer(image: Bitmap, options: OptionsImpression, fini: (JSONObject) -> Unit)

    /** Ouvre le tiroir-caisse branché sur le terminal et appelle `fini` une seule fois. */
    fun ouvrirTiroir(fini: (JSONObject) -> Unit)

    /** Affiche l'image sur l'écran client — ou l'efface si elle est `null` — et appelle `fini` une seule fois. */
    fun afficher(image: Bitmap?, fini: (JSONObject) -> Unit)

    fun arreter()
}

/**
 * Imprime, en ouvrant d'abord le tiroir-caisse si la page le demande. Les deux
 * demandes partent l'une derrière l'autre dans la file du pilote : le tiroir
 * s'ouvre, puis le reçu sort aussitôt, sans attendre que le tiroir ait
 * répondu. Le verdict du reçu porte celui du tiroir dans `tiroir` — même quand
 * le reçu échoue (papier épuisé en cours de route…), le tiroir, lui, a pu
 * s'ouvrir. Un tiroir qui ne s'ouvre pas n'empêche pas le reçu.
 */
fun Pilote.imprimerAvecTiroir(image: Bitmap, options: OptionsImpression, fini: (JSONObject) -> Unit) {
    if (!options.tiroir) return imprimer(image, options, fini)

    val tiroir = AtomicReference<JSONObject>()
    val verdict = AtomicReference<JSONObject>()
    val attendus = AtomicInteger(2)
    val rendre = { if (attendus.decrementAndGet() == 0) fini(verdict.get().put("tiroir", tiroir.get())) }

    ouvrirTiroir {
        tiroir.set(it)
        rendre()
    }
    imprimer(image, options) {
        verdict.set(it)
        rendre()
    }
}

private fun nonPrisEnCharge(message: String): JSONObject =
    JSONObject().put("ok", false).put("code", "non-pris-en-charge").put("message", message)

private class PiloteSunmi(private val imprimante: ImprimanteSunmi) : Pilote {
    override val nom = "sunmi"
    override fun etat() = imprimante.etat()
    override fun capacites(): JSONObject = JSONObject()
        .put("massicot", false)
        .put("etiquettes", false)
        .put("tiroir", imprimante.aUnTiroir() ?: JSONObject.NULL)
        .put("afficheur", JSONObject.NULL)

    override fun imprimer(image: Bitmap, options: OptionsImpression, fini: (JSONObject) -> Unit) {
        if (options.etiquette) return fini(nonPrisEnCharge("Le pilote Sunmi n'imprime pas encore d'étiquettes."))
        imprimante.imprimer(image, options.avance, fini)
    }

    override fun ouvrirTiroir(fini: (JSONObject) -> Unit) = imprimante.ouvrirTiroir(fini)

    override fun afficher(image: Bitmap?, fini: (JSONObject) -> Unit) =
        fini(nonPrisEnCharge("Le pilote Sunmi ne pilote pas encore d'écran client."))

    override fun arreter() = imprimante.arreter()
}

private class PiloteZcs(private val imprimante: ImprimanteZcs, private val afficheur: AfficheurZcs) : Pilote {
    override val nom = "zcs"
    override fun etat() = imprimante.etat()
    override fun capacites(): JSONObject = imprimante.capacites().put("afficheur", afficheur.format() ?: JSONObject.NULL)
    override fun imprimer(image: Bitmap, options: OptionsImpression, fini: (JSONObject) -> Unit) =
        imprimante.imprimer(image, options.avance, options.etiquette, options.copies, fini)

    override fun ouvrirTiroir(fini: (JSONObject) -> Unit) = imprimante.ouvrirTiroir(fini)

    override fun afficher(image: Bitmap?, fini: (JSONObject) -> Unit) = afficheur.afficher(image, fini)
    override fun arreter() = imprimante.arreter()
}

/**
 * Aucun pilote ne reconnaît l'appareil : téléphone, émulateur, ou marque pas
 * encore prise en charge. Rien n'est imprimé ; l'application montre le reçu
 * (pont) ou le garde en fichier (service local).
 */
class PiloteSimulation : Pilote {
    override val nom = SIMULATION
    override fun etat(): JSONObject = JSONObject()
        .put("code", "simulation")
        .put("message", "Aucune imprimante reconnue sur ${Pilotes.terminal()} : simulation, rien ne sera imprimé.")
        .put("largeur", Pilotes.LARGEUR_58MM)

    override fun capacites(): JSONObject = JSONObject()
        .put("massicot", false)
        .put("etiquettes", false)
        .put("tiroir", false)
        .put("afficheur", JSONObject.NULL)

    override fun imprimer(image: Bitmap, options: OptionsImpression, fini: (JSONObject) -> Unit) =
        fini(JSONObject().put("ok", true).put("simulation", true))

    override fun ouvrirTiroir(fini: (JSONObject) -> Unit) =
        fini(nonPrisEnCharge("Pas de tiroir-caisse sur ${Pilotes.terminal()}."))

    override fun afficher(image: Bitmap?, fini: (JSONObject) -> Unit) =
        fini(nonPrisEnCharge("Pas d'écran client sur ${Pilotes.terminal()}."))

    override fun arreter() {}

    companion object {
        const val SIMULATION = "simulation"
    }
}

object Pilotes {
    const val LARGEUR_58MM = 384

    /**
     * Reconnaît le terminal et choisit son pilote. ZCS d'abord : son pilote ne
     * se déclare que sur un terminal ZCS. Puis Sunmi : son pilote se déclare
     * partout où le service d'impression Sunmi existe — y compris chez les
     * marques qui l'ont repris.
     */
    fun choisir(contexte: Context): Pilote {
        val zcs = ImprimanteZcs()
        if (zcs.demarrer()) return PiloteZcs(zcs, AfficheurZcs())

        val sunmi = ImprimanteSunmi(contexte.applicationContext)
        if (sunmi.demarrer()) return PiloteSunmi(sunmi)

        return PiloteSimulation()
    }

    /** « SUNMI V2_PRO », « ZCS Z92S »… */
    fun terminal(): String = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}".trim()
}

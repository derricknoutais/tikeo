package com.derricknoutais.tikeo

import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONException
import org.json.JSONObject

/**
 * Ce que la page voit sous le nom `window.Tikeo` — le protocole que parle
 * src/pont.ts du paquet web tikeo.
 *
 * Les méthodes tournent sur le fil du pont JavaScript, pas sur celui de
 * l'interface : les réponses repassent par `runOnUiThread`.
 *
 * Android injecte cet objet dans TOUTES les pages et tous les cadres de la
 * WebView ; chaque appel vérifie donc que la page principale affichée est bien
 * une adresse autorisée, ou la page de test embarquée. C'est l'adresse de la
 * page principale qui compte : un cadre (iframe) d'une autre origine intégré
 * à une page autorisée passe ce contrôle — d'où le conseil du README de ne pas
 * intégrer de cadre tiers dans une page autorisée.
 */
class PontImpression(private val activite: MainActivity, private val app: TikeoApp) {

    @JavascriptInterface
    fun version(): String = VERSION

    @JavascriptInterface
    fun etat(): String {
        if (!activite.pageDeConfiance()) return refus().put("largeur", Pilotes.LARGEUR_58MM).toString()
        return app.etat().toString()
    }

    @JavascriptInterface
    fun imprimer(id: String, pngBase64: String, options: String) {
        Log.i(JOURNAL, "pont : imprimer $id (${pngBase64.length} caractères)")
        if (!activite.pageDeConfiance()) return repondre(id, refus().put("ok", false))

        val demande = try {
            Protocole.options(JSONObject(options))
        } catch (e: JSONException) {
            Protocole.options(null)
        }
        val image = Protocole.image(pngBase64)
            ?: return repondre(id, Protocole.echec("image", "Image du reçu illisible."))

        // Aucune imprimante reconnue : on montre le reçu au lieu de l'imprimer.
        if (app.simulation) activite.runOnUiThread { activite.montrerSimulation(image) }

        app.pilote.imprimerAvecTiroir(image, demande) { resultat -> repondre(id, resultat) }
    }

    /** Ouvre le tiroir-caisse branché sur le terminal. */
    @JavascriptInterface
    fun ouvrirTiroir(id: String) {
        Log.i(JOURNAL, "pont : tiroir $id")
        if (!activite.pageDeConfiance()) return repondre(id, refus().put("ok", false))
        app.pilote.ouvrirTiroir { resultat -> repondre(id, resultat) }
    }

    /** Affiche une image sur l'écran client du terminal. */
    @JavascriptInterface
    fun afficher(id: String, pngBase64: String) {
        Log.i(JOURNAL, "pont : afficher $id (${pngBase64.length} caractères)")
        if (!activite.pageDeConfiance()) return repondre(id, refus().put("ok", false))
        val image = Protocole.image(pngBase64)
            ?: return repondre(id, Protocole.echec("image", "Image de l'écran client illisible."))
        app.pilote.afficher(image) { resultat -> repondre(id, resultat) }
    }

    /** Efface l'écran client. */
    @JavascriptInterface
    fun effacer(id: String) {
        if (!activite.pageDeConfiance()) return repondre(id, refus().put("ok", false))
        app.pilote.afficher(null) { resultat -> repondre(id, resultat) }
    }

    private fun repondre(id: String, resultat: JSONObject) {
        Log.i(JOURNAL, "pont : réponse $id → $resultat")
        val script = "window.__tikeo&&window.__tikeo.retour(${JSONObject.quote(id)},${JSONObject.quote(resultat.toString())})"
        activite.runOnUiThread { activite.executer(script) }
    }

    private fun refus() = JSONObject()
        .put("code", "refusee")
        .put("message", "Cette page n'est pas autorisée à imprimer : seules les adresses autorisées dans Tikéo le sont.")

    companion object {
        /** Doit rester égale à VERSION_PONT dans src/pont.ts. */
        const val VERSION = Protocole.VERSION
        private const val JOURNAL = "Tikeo"
    }
}

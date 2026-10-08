package com.derricknoutais.tikeo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * Le test : un reçu, la mire ou une étiquette, vus avant d'être imprimés —
 * les mêmes images que dessinerait une page web (assets/apercus, produits par
 * scripts/construire-apercus.mjs) —, puis l'écran client, le tiroir-caisse,
 * et le journal de ce qui s'est passé.
 */
class EcranTest(private val a: AccueilActivity) : Ecran {

    private val t get() = a.textes
    private val app get() = a.app
    private var choix: Int
        get() = a.choixTest
        set(valeur) { a.choixTest = valeur }

    private val image = ImageView(a).apply {
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(PAPIER)
    }
    private val imprimer: TextView = a.texte(t.imprimer, 16f, Charte.ROBOTO_GRAS, Color.WHITE, 1f).apply {
        gravity = Gravity.CENTER
        setPadding(0, a.dp(17), 0, a.dp(17))
        isClickable = true
        setOnClickListener { imprimer() }
    }
    private val journal = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = a.fond(Color.WHITE, 14, Charte.BORDURE)
        setPadding(a.dp(14), a.dp(4), a.dp(14), a.dp(4))
    }

    override val vue = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(22))
    }

    init {
        vue.addView(Segments(a, listOf(t.recu, t.mire, t.etiquette), choix) { choix = it; majApercu() },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(14) })

        // Le papier, sur un fond chaud, avec ses bords dentelés.
        val ticket = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            elevation = a.dpf(3f)
            addView(Dentele(a, SUPPORT, PAPIER), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            // Couché (le 10″ d'un Z100), l'aperçu entier repousserait « Imprimer » sous l'écran.
            val ecranPx = a.resources.displayMetrics
            val plafond = if (ecranPx.widthPixels > ecranPx.heightPixels) (ecranPx.heightPixels * 0.45f).toInt() else Int.MAX_VALUE
            addView(Plafond(a, plafond).apply {
                addView(image.apply { setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8)) }, android.widget.FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(Dentele(a, SUPPORT, PAPIER).apply { rotation = 180f }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        val support = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = a.fond(SUPPORT, 14)
            setPadding(0, a.dp(14), 0, a.dp(14))
            addView(ticket, LinearLayout.LayoutParams(a.dp(280), WRAP_CONTENT))
        }
        vue.addView(support, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(14) })

        vue.addView(imprimer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(10) })

        val actions = listOf(
            t.ecranClient to ::ecranClient,
            t.tiroir to ::tiroir,
            t.effacerEcran to ::effacer,
            t.actualiser to ::actualiser,
        )
        actions.chunked(2).forEach { paire ->
            val ligne = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            paire.forEachIndexed { i, (libelle, action) ->
                ligne.addView(a.boutonAction(libelle) { action() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i > 0) leftMargin = a.dp(8) })
            }
            vue.addView(ligne, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(8) })
        }

        vue.addView(a.surtitre(t.journal).apply { setPadding(a.dp(2), a.dp(10), 0, a.dp(8)) })
        vue.addView(journal, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        majBouton()
        majApercu()
        journalChange()
    }

    /** Depuis la mise en route : « Imprimer un reçu d'exemple ». */
    fun imprimerApres(ms: Long) = vue.postDelayed({ imprimer() }, ms)

    private fun largeur() = if (a.lireTerminal(a.etat).largeur >= 576) 576 else 384
    private fun nom() = when (choix) { 1 -> "mire"; 2 -> "etiquette"; else -> "recu" }

    /** L'aperçu affiché, pour ne relire l'image que si le choix ou le papier change. */
    private var apercu = ""

    private fun majApercu() {
        val chemin = "apercus/${nom()}-${largeur()}.png"
        if (chemin == apercu) return
        apercu = chemin
        image.setImageBitmap(charger(chemin))
    }

    private fun majBouton() {
        val occupe = app.impressionEnCours
        imprimer.text = if (occupe) t.impression else t.imprimer
        imprimer.background = if (occupe) a.fond(Charte.OCCUPE, 14) else a.fondTouchable(Charte.CORPS, 14, ondulation = Color.argb(60, 255, 255, 255))
        imprimer.isEnabled = !occupe
    }

    private fun charger(chemin: String): Bitmap? = try {
        a.assets.open(chemin).use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    /**
     * Le verdict peut arriver après la fermeture de cet écran (rotation, retour) :
     * il passe par l'application, qui le note au journal et prévient l'écran affiché.
     */
    private fun imprimer() {
        if (app.impressionEnCours) return
        app.impressionEnCours = true
        majBouton()
        val quoi = choix
        val fichier = "apercus/${nom()}-${largeur()}.png"
        val succes = when (quoi) { 1 -> t.jMire; 2 -> t.jEtiquette; else -> t.jRecu }
        val textes = t
        val application = app
        val debut = SystemClock.elapsedRealtime()
        a.enArrierePlan {
            val bitmap = charger(fichier)
            if (bitmap == null) {
                application.noter("[image] $fichier", Genre.ERREUR)
                application.finImpression()
                return@enArrierePlan
            }
            application.pilote.imprimerAvecTiroir(bitmap, OptionsImpression(avance = 3, etiquette = quoi == 2, copies = 1)) { verdict ->
                rapporter(application, textes, verdict, succes, debut)
                application.finImpression()
                application.surInterface { a.lireEtat() }
            }
        }
    }

    private fun ecranClient() {
        val term = a.lireTerminal(a.etat)
        if (!term.aUnEcran) return a.noter(t.jPasDEcran, Genre.ERREUR)
        val debut = SystemClock.elapsedRealtime()
        val textes = t
        val application = app
        a.enArrierePlan {
            val bitmap = charger(if (term.ecranMonochrome) "apercus/ecran-lcd.png" else "apercus/ecran-480.png")
            application.pilote.afficher(bitmap) { verdict -> rapporter(application, textes, verdict, textes.jEcran, debut) }
        }
    }

    private fun tiroir() {
        val debut = SystemClock.elapsedRealtime()
        val textes = t
        app.pilote.ouvrirTiroir { verdict -> rapporter(app, textes, verdict, textes.jTiroir, debut) }
    }

    private fun effacer() {
        val debut = SystemClock.elapsedRealtime()
        val textes = t
        app.pilote.afficher(null) { verdict -> rapporter(app, textes, verdict, textes.jEfface, debut) }
    }

    private fun actualiser() {
        a.lireEtat { etat ->
            val term = a.lireTerminal(etat)
            a.noter(t.jEtat(t.titreEtat(term.code)), if (term.code == "prete") Genre.OK else Genre.INFO)
        }
    }

    /** Le verdict d'un pilote, dans le journal : « Reçu imprimé (2,3 s). », ou « [papier] Plus de papier. ». */
    private fun rapporter(application: TikeoApp, textes: Textes, verdict: JSONObject, succes: String, debut: Long) {
        val duree = textes.duree(SystemClock.elapsedRealtime() - debut)
        when {
            verdict.optBoolean("ok") && verdict.optBoolean("simulation") -> application.noter(textes.jSimulation, Genre.INFO)
            verdict.optBoolean("ok") -> application.noter("$succes $duree.", Genre.OK)
            else -> application.noter("[${verdict.optString("code", "erreur")}] ${verdict.optString("message")}", Genre.ERREUR)
        }
    }

    /** Le journal et le bouton Imprimer, d'après l'application. */
    fun journalChange() {
        majBouton()
        journal.removeAllViews()
        if (app.journal.isEmpty()) {
            journal.addView(a.texte(t.journalVide, 12f, Charte.ROBOTO, Charte.DISCRET).apply { setPadding(0, a.dp(10), 0, a.dp(10)) })
            return
        }
        app.journal.forEachIndexed { i, e ->
            val ligne = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, a.dp(9), 0, a.dp(9))
            }
            ligne.addView(a.texte(e.heure, 11f, Charte.MONO, Charte.DISCRET, 1.4f), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = a.dp(9) })
            ligne.addView(a.texte(e.texte, 12f, Charte.ROBOTO, when (e.genre) {
                Genre.OK -> Charte.VERT
                Genre.ERREUR -> Charte.ROUGE
                Genre.INFO -> Color.parseColor("#44403C")
            }, 1.45f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (i > 0) journal.addView(android.view.View(a).apply { setBackgroundColor(Charte.FILET) }, LinearLayout.LayoutParams(MATCH_PARENT, a.dp(1)))
            journal.addView(ligne)
            if (i == 0 && e !== dernierAnime) {
                dernierAnime = e
                // Le verdict se lit aussi à TalkBack.
                journal.announceForAccessibility(e.texte)
                ligne.alpha = 0f
                ligne.translationY = a.dpf(6f)
                ligne.animate().alpha(1f).translationY(0f).setDuration(250).start()
            }
        }
    }

    /** La dernière ligne animée : une reconstruction de l'écran ne la refait pas entrer. */
    private var dernierAnime: Entree? = app.journal.firstOrNull()

    override fun etatChange(etat: JSONObject) {
        // La largeur du papier peut n'être connue qu'à la première lecture.
        majApercu()
    }

    companion object {
        private val PAPIER = Color.parseColor("#FFFDF8")
        private val SUPPORT = Color.parseColor("#7A736B")
    }
}

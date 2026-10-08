package com.derricknoutais.tikeo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * L'application Tikéo, telle que la dessine la maquette « Tikéo — terminal
 * app » : une mise en route en trois temps au premier lancement, puis trois
 * onglets — Accueil, Adresses, Test. Écrans natifs, construits en code ;
 * couleurs et logo de la planche « Tikéo Logo v2 » (voir Charte).
 *
 * L'état de l'imprimante se lit en arrière-plan, toutes les trois secondes
 * tant que l'écran est visible : chez ZCS, le lire peut attendre que le SDK
 * finisse une impression.
 */
class AccueilActivity : Activity() {

    enum class Vue { MISE_EN_ROUTE, ACCUEIL, ADRESSES, TEST }

    val app get() = application as TikeoApp
    lateinit var textes: Textes
        private set

    /** Le dernier état lu ; `null` tant que le terminal n'a pas répondu. */
    var etat: JSONObject? = null
        private set

    /** L'étape de la mise en route et l'échantillon choisi au Test : gardés quand l'écran se reconstruit (langue, rotation). */
    var etapeMiseEnRoute = 0
    var choixTest = 0

    private val arrierePlan = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())
    private var visible = false

    private var vue = Vue.ACCUEIL
    private var ecran: Ecran? = null
    private lateinit var entete: LinearLayout
    private lateinit var zone: ScrollView
    private lateinit var colonne: FrameLayout
    private lateinit var onglets: LinearLayout

    override fun onCreate(etatSauve: Bundle?) {
        super.onCreate(etatSauve)
        // Le service démarre avec l'application, puis au démarrage du terminal.
        ServiceImpression.demarrer(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        textes = Textes(app.reglages.langue == "en")
        // Après une rotation, on revient où on était ; sinon, l'onglet demandé (EXTRA_VUE) ou l'accueil.
        val demandee = (etatSauve?.getString(CLE_VUE) ?: intent.getStringExtra(EXTRA_VUE))
            ?.let { nom -> Vue.values().firstOrNull { it.name == nom } }
        vue = when {
            !app.reglages.miseEnRouteFaite -> Vue.MISE_EN_ROUTE
            demandee != null && demandee != Vue.MISE_EN_ROUTE -> demandee
            else -> Vue.ACCUEIL
        }
        etapeMiseEnRoute = etatSauve?.getInt(CLE_ETAPE) ?: 0
        choixTest = etatSauve?.getInt(CLE_CHOIX) ?: 0
        app.ecouteur = ecouteur

        // Le fond de la racine se voit sous les barres du système (Android 15) : celui de l'en-tête.
        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Charte.CORPS)
        }
        entete = LinearLayout(this).apply { setBackgroundColor(Charte.CORPS) }
        racine.addView(entete, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        colonne = FrameLayout(this)
        zone = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Charte.FOND)
            addView(colonne, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        racine.addView(zone, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        onglets = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        racine.addView(onglets, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        setContentView(racine)
        reserverBarresSysteme(racine, sombre = true, couleurEtat = Charte.CORPS_PRESSE)
        construireEntete()
        afficher()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        lireEtat()
    }

    override fun onPause() {
        visible = false
        principal.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onSaveInstanceState(sortie: Bundle) {
        super.onSaveInstanceState(sortie)
        sortie.putString(CLE_VUE, vue.name)
        sortie.putInt(CLE_ETAPE, etapeMiseEnRoute)
        sortie.putInt(CLE_CHOIX, choixTest)
    }

    override fun onDestroy() {
        if (app.ecouteur === ecouteur) app.ecouteur = null
        principal.removeCallbacksAndMessages(null)
        arrierePlan.shutdownNow()
        super.onDestroy()
    }

    /** Ce que l'application signale (journal, fin d'impression) va à l'écran affiché. */
    private val ecouteur: () -> Unit = { (ecran as? EcranTest)?.journalChange() }

    /**
     * Lit l'état en arrière-plan, puis recommence dans trois secondes tant que
     * l'écran est visible. Un verdict peut arriver après la fermeture de
     * l'écran (rotation, retour) : rien n'est alors relancé.
     */
    fun lireEtat(apres: ((JSONObject) -> Unit)? = null) {
        if (isDestroyed || arrierePlan.isShutdown) return
        principal.removeCallbacks(relecture)
        arrierePlan.execute {
            val lu = try {
                app.etat()
            } catch (e: Exception) {
                JSONObject().put("code", "erreur").put("message", e.message ?: "")
            }
            principal.post {
                // Pendant une impression, le pilote ZCS rend des valeurs d'attente (58 mm, sans
                // capacités) : on garde celles de la dernière vraie lecture.
                val precedent = etat
                val retenu = if (lu.optString("code") == "occupee" && precedent != null) {
                    lu.put("largeur", precedent.optInt("largeur", lu.optInt("largeur", 384)))
                        .put("capacites", precedent.optJSONObject("capacites") ?: lu.optJSONObject("capacites"))
                } else lu
                etat = retenu
                ecran?.etatChange(retenu)
                apres?.invoke(retenu)
                if (visible) principal.postDelayed(relecture, 3000)
            }
        }
    }

    private val relecture = Runnable { lireEtat() }

    /** Une action sur le terminal, hors du fil de l'interface ; ignorée si l'écran est fermé. */
    fun enArrierePlan(action: () -> Unit) {
        if (!isDestroyed && !arrierePlan.isShutdown) arrierePlan.execute(action)
    }

    fun noter(texte: String, genre: Genre) = app.noter(texte, genre)

    // ------------------------------------------------------------ Navigation

    fun aller(nouvelle: Vue, ensuite: ((Ecran) -> Unit)? = null) {
        if (vue == Vue.MISE_EN_ROUTE && nouvelle != Vue.MISE_EN_ROUTE) {
            app.reglages.miseEnRouteFaite = true
            etapeMiseEnRoute = 0
        }
        vue = nouvelle
        afficher()
        ecran?.let { ensuite?.invoke(it) }
    }

    fun ouvrirModeCoque() {
        val adresse = app.reglages.adressePrincipale ?: return
        noter(textes.jCoque, Genre.INFO)
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ADRESSE, adresse))
    }

    @Deprecated("Remplacé par OnBackPressedDispatcher à partir d'Android 13 ; suffit ici.")
    override fun onBackPressed() {
        val parEcran = ecran?.retour() ?: false
        when {
            parEcran -> {}
            vue == Vue.MISE_EN_ROUTE || vue == Vue.ACCUEIL -> @Suppress("DEPRECATION") super.onBackPressed()
            else -> aller(Vue.ACCUEIL)
        }
    }

    /** L'écran affiché. */
    val ecranActuel: Ecran? get() = ecran

    private fun afficher() {
        cacherClavier()
        ecran = when (vue) {
            Vue.MISE_EN_ROUTE -> EcranMiseEnRoute(this)
            Vue.ACCUEIL -> EcranAccueil(this)
            Vue.ADRESSES -> EcranAdresses(this)
            Vue.TEST -> EcranTest(this)
        }
        colonne.removeAllViews()
        val largeur = minOf(resources.displayMetrics.widthPixels, dp(Charte.LARGEUR_MAX_DP))
        colonne.addView(ecran!!.vue, FrameLayout.LayoutParams(largeur, MATCH_PARENT, Gravity.CENTER_HORIZONTAL))
        zone.scrollTo(0, 0)
        etat?.let { ecran?.etatChange(it) }
        construireOnglets()
    }

    private fun cacherClavier() {
        val actif = currentFocus ?: return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(actif.windowToken, 0)
        actif.clearFocus()
    }

    // --------------------------------------------------------------- En-tête

    /** Le verrou horizontal de la planche, sur le corps sombre : symbole, TIKÉO, PAR ECOLIGHT — et FR / EN. */
    private fun construireEntete() {
        entete.removeAllViews()
        val ligne = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        ligne.addView(SymboleTikeo(this, SymboleTikeo.Palette.SOMBRE), LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(10) })

        val nom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nom.addView(texte(motTikeo(Color.WHITE, Charte.VOYANT), 21f, Charte.montserrat(this), Color.WHITE, 1f).apply { letterSpacing = 0.03f })
        nom.addView(texte(textes.parEcolight, 8f, Charte.montserrat(this, noir = false), Color.parseColor("#C9C4BC"), 1f).apply {
            letterSpacing = 0.3f
            setPadding(0, dp(5), 0, 0)
        })
        ligne.addView(nom, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        ligne.addView(choixLangue())

        val largeur = minOf(resources.displayMetrics.widthPixels, dp(Charte.LARGEUR_MAX_DP))
        entete.gravity = Gravity.CENTER_HORIZONTAL
        entete.addView(ligne, LinearLayout.LayoutParams(largeur, WRAP_CONTENT))
    }

    /** « TIK » de la couleur du texte, « ÉO » en vert — l'accent ne s'omet jamais. */
    fun motTikeo(couleur: Int, vert: Int): CharSequence = android.text.SpannableString("TIKÉO").apply {
        setSpan(android.text.style.ForegroundColorSpan(couleur), 0, 3, 0)
        setSpan(android.text.style.ForegroundColorSpan(vert), 3, 5, 0)
    }

    private fun choixLangue(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = android.graphics.drawable.InsetDrawable(fond(Color.TRANSPARENT, 999, Color.argb(77, 255, 255, 255)), 0, dp(9), 0, dp(9))
        setPadding(dp(1), 0, dp(1), 0)
        for ((code, libelle) in listOf("fr" to "FR", "en" to "EN")) {
            val actif = (code == "en") == textes.anglais
            addView(texte(libelle, 11f, Charte.MONO_GRAS, if (actif) Charte.CORPS else Color.argb(204, 255, 255, 255), 1f).apply {
                gravity = Gravity.CENTER
                // 48 dp à toucher ; la pastille dessinée garde sa taille.
                minimumHeight = dp(48)
                background = if (actif) android.graphics.drawable.InsetDrawable(fond(Color.WHITE, 999), 0, dp(10), 0, dp(10)) else null
                // Après le fond : un InsetDrawable impose ses retraits comme marge intérieure.
                setPadding(dp(11), 0, dp(11), 0)
                isSelected = actif
                isClickable = true
                contentDescription = if (code == "fr") "Français" else "English"
                setOnClickListener {
                    if (actif) return@setOnClickListener
                    app.reglages.langue = code
                    textes = Textes(code == "en")
                    construireEntete()
                    afficher()
                }
            })
        }
    }

    // ------------------------------------------------------------- Onglets

    private fun construireOnglets() {
        onglets.removeAllViews()
        if (vue == Vue.MISE_EN_ROUTE) return
        onglets.addView(View(this).apply { setBackgroundColor(Charte.BORDURE) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))
        val ligne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((cible, forme, libelle) in listOf(
            Triple(Vue.ACCUEIL, IconeOnglet.Forme.ACCUEIL, textes.ongletAccueil),
            Triple(Vue.ADRESSES, IconeOnglet.Forme.ADRESSES, textes.ongletAdresses),
            Triple(Vue.TEST, IconeOnglet.Forme.TEST, textes.ongletTest),
        )) {
            val actif = cible == vue
            val bouton = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(10), 0, dp(12))
                background = fondTouchable(Color.WHITE, 0)
                isClickable = true
                contentDescription = libelle
                isSelected = actif
                setOnClickListener { if (!actif) aller(cible) }
            }
            bouton.addView(IconeOnglet(this, forme).apply { couleur = if (actif) Charte.FENTE else Charte.PALE })
            bouton.addView(texte(libelle, 11f, Charte.ROBOTO_MOYEN, if (actif) Charte.ENCRE else Charte.DISCRET, 1f).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(5), 0, 0)
            })
            ligne.addView(bouton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val largeur = minOf(resources.displayMetrics.widthPixels, dp(Charte.LARGEUR_MAX_DP))
        onglets.gravity = Gravity.CENTER_HORIZONTAL
        onglets.addView(ligne, LinearLayout.LayoutParams(largeur, WRAP_CONTENT))
    }

    companion object {
        /** L'onglet à ouvrir : `ADRESSES` depuis le mode coque, pour le certificat auto-signé. */
        const val EXTRA_VUE = "vue"
        private const val CLE_VUE = "vue"
        private const val CLE_ETAPE = "etape"
        private const val CLE_CHOIX = "choix"
    }
}

/** Un écran de l'application : sa vue, et ce qu'il fait quand l'état de l'imprimante change. */
interface Ecran {
    val vue: View
    fun etatChange(etat: JSONObject) {}
    /** Vrai si l'écran a traité le retour lui-même (une étape en arrière). */
    fun retour(): Boolean = false
}

/** Ce que l'interface dit de l'état lu : couleur du voyant, titre, ligne de détail. */
fun AccueilActivity.lireTerminal(etat: JSONObject?): Terminal = Terminal.depuis(etat, textes)

class Terminal(
    val code: String,
    val nom: String,
    val pilote: String,
    val largeur: Int,
    val message: String,
    val massicot: Boolean,
    val tiroir: Boolean?,
    val ecran: String?,
    val ecranMonochrome: Boolean,
    val aUnEcran: Boolean,
) {
    val mm get() = if (largeur >= 576) "80 mm" else "58 mm"

    fun couleur(): Int = when (code) {
        "prete" -> Charte.VOYANT
        // Rien ne s'imprime : pas une panne, mais à remarquer.
        "simulation", "occupee" -> Charte.FENTE
        else -> Charte.ROUGE
    }

    companion object {
        fun depuis(etat: JSONObject?, textes: Textes): Terminal {
            val c = etat?.optJSONObject("capacites")
            val afficheur = c?.optJSONObject("afficheur")
            val monochrome = afficheur?.optBoolean("monochrome") ?: false
            return Terminal(
                code = etat?.optString("code")?.takeIf { it.isNotEmpty() } ?: "occupee",
                nom = etat?.optString("terminal")?.takeIf { it.isNotEmpty() } ?: textes.enCours,
                pilote = etat?.optString("pilote")?.takeIf { it.isNotEmpty() } ?: "…",
                largeur = etat?.optInt("largeur", 384) ?: 384,
                message = etat?.optString("message").orEmpty(),
                massicot = c?.optBoolean("massicot") ?: false,
                tiroir = if (c == null || c.isNull("tiroir")) null else c.optBoolean("tiroir"),
                ecran = afficheur?.let { "${it.optInt("largeur")} × ${it.optInt("hauteur")}" + if (monochrome) " ${textes.noirEtBlanc}" else "" },
                ecranMonochrome = monochrome,
                aUnEcran = afficheur != null,
            )
        }
    }
}


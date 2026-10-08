package com.derricknoutais.tikeo

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * La mise en route, au premier lancement : le terminal reconnu, qui peut
 * imprimer, puis un reçu d'exemple. Trois barres en haut disent où on en est.
 */
class EcranMiseEnRoute(private val a: AccueilActivity) : Ecran {

    /** L'étape, gardée par l'activité : elle survit à un changement de langue ou à une rotation. */
    private var etape: Int
        get() = a.etapeMiseEnRoute
        set(valeur) { a.etapeMiseEnRoute = valeur }
    private val t get() = a.textes

    override val vue = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(a.dp(18), a.dp(22), a.dp(18), a.dp(26))
    }

    // Les éléments de l'étape 1 que l'état met à jour.
    private var pastille: Pastille? = null
    private var titreReconnu: TextView? = null
    private var valeurs: LinearLayout? = null

    init { construire() }

    private fun construire() {
        vue.removeAllViews()
        pastille = null; titreReconnu = null; valeurs = null

        val barres = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            // Les barres ne disent leur progression qu'en couleur : TalkBack la lit ici.
            contentDescription = t.etapeSur(etape + 1)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        repeat(3) { i ->
            barres.addView(View(a).apply { background = a.fond(if (i <= etape) Charte.FENTE else Color.parseColor("#D6D1C9"), 2) },
                LinearLayout.LayoutParams(0, a.dp(4), 1f).apply { if (i > 0) leftMargin = a.dp(8) })
        }
        vue.addView(barres, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(20) })

        val (titre, corps) = t.etapesTitres[etape]
        vue.addView(a.surtitre(t.etapes))
        vue.addView(a.texte(titre, 24f, Charte.ROBOTO_MOYEN, Charte.ENCRE, 1.2f).apply { setPadding(0, a.dp(8), 0, a.dp(8)) })
        vue.addView(a.texte(corps, 14f, Charte.ROBOTO, Charte.TEXTE, 1.5f).apply { setPadding(0, 0, 0, a.dp(20)) })

        when (etape) {
            0 -> vue.addView(carteTerminal(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            1 -> vue.addView(EditeurAdresses(a, avecVide = false).vue, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            2 -> vue.addView(cartePret(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        vue.addView(View(a), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = a.dp(20) })

        val boutons = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        boutons.addView(a.bouton(t.retour, plein = false) { retour() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = a.dp(10) })
        boutons.addView(a.bouton(if (etape == 2) t.terminer else t.continuer, plein = true) {
            if (etape == 2) a.aller(AccueilActivity.Vue.ACCUEIL) else { etape++; construire() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        vue.addView(boutons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        a.etat?.let { etatChange(it) }
    }

    private fun carteTerminal(): View = a.carte(14).apply {
        val entete = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pastille = Pastille(a)
        entete.addView(pastille, LinearLayout.LayoutParams(a.dp(20), a.dp(20)).apply { rightMargin = a.dp(6) })
        titreReconnu = a.texte(t.enCours, 14f, Charte.ROBOTO_MOYEN)
        entete.addView(titreReconnu)
        addView(entete, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(10) })
        valeurs = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        addView(valeurs)
    }

    private fun cartePret(): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = a.fond(Charte.CORPS_PRESSE, 16)
        setPadding(a.dp(18), a.dp(22), a.dp(18), a.dp(22))
        addView(a.texte(t.pretNote, 12f, Charte.ROBOTO, Charte.BLANC_DOUX, 1.5f).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, a.dp(14))
        })
        addView(a.texte(t.imprimerExemple, 15f, Charte.ROBOTO_GRAS, Charte.CORPS_PRESSE, 1f).apply {
            gravity = Gravity.CENTER
            background = a.fondTouchable(Color.WHITE, 12)
            setPadding(0, a.dp(15), 0, a.dp(15))
            isClickable = true
            setOnClickListener { a.aller(AccueilActivity.Vue.TEST) { (it as? EcranTest)?.imprimerApres(300) } }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    override fun etatChange(etat: JSONObject) {
        val v = valeurs ?: return
        val term = a.lireTerminal(etat)
        pastille?.couleur = term.couleur()
        titreReconnu?.text = t.reconnu
        v.removeAllViews()
        v.addView(a.ligneValeur(t.terminal, term.nom))
        v.addView(a.ligneValeur(t.pilote, term.pilote))
        v.addView(a.ligneValeur(t.papier, "${term.largeur} pts · ${term.mm}"))
        v.addView(a.ligneValeur(t.capacites, t.capacitesCourtes(term.massicot, term.tiroir, term.ecran)))
    }

    override fun retour(): Boolean {
        if (etape > 0) { etape--; construire() } else a.aller(AccueilActivity.Vue.ACCUEIL)
        return true
    }
}

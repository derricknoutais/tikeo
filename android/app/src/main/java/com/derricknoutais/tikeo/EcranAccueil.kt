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
 * L'accueil : l'état de l'imprimante d'un coup d'œil, les deux gestes
 * courants — tester, ouvrir l'application —, puis le détail : adresses,
 * service local, terminal.
 */
class EcranAccueil(private val a: AccueilActivity) : Ecran {

    private val t get() = a.textes

    override val vue = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(22))
    }

    private val pastille = Pastille(a)
    private val titreEtat = a.texte(t.titreEtat("occupee"), 20f, Charte.ROBOTO_MOYEN, Charte.ENCRE, 1.2f)
    private val ligneEtat = a.texte(t.enCours, 13f, Charte.ROBOTO, Charte.DISCRET, 1.5f)
    private val valeurService = a.texte("", 14f, Charte.ROBOTO_MOYEN)
    private val pointService = View(a)
    private val valeurTerminal = a.texte("", 14f, Charte.ROBOTO_MOYEN)
    private val capacitesTerminal = a.texte("", 12f, Charte.ROBOTO, Charte.DISCRET, 1.4f)

    init {
        val statut = a.carte(16, 18).apply {
            val ligne = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            ligne.addView(pastille, LinearLayout.LayoutParams(a.dp(20), a.dp(20)).apply { rightMargin = a.dp(6) })
            ligne.addView(titreEtat)
            addView(ligne)
            addView(ligneEtat.apply { setPadding(a.dp(26), a.dp(4), 0, 0) })
        }
        vue.addView(statut, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(14) })

        vue.addView(a.boutonPrincipal(t.testerImprimante, t.testerImprimanteSous) { a.aller(AccueilActivity.Vue.TEST) },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(10) })

        val principale = a.app.reglages.adressePrincipale
        vue.addView(a.boutonSecondaire(t.ouvrirApplication, if (principale != null) t.ouvrirApplicationSous else t.ouvrirApplicationVide) {
            if (principale != null) a.ouvrirModeCoque() else a.aller(AccueilActivity.Vue.ADRESSES)
        }.apply {
            // Sans adresse, le bouton mène à l'onglet Adresses : son sous-titre le dit, en rouge, sans le griser.
            if (principale == null) (getChildAt(1) as TextView).setTextColor(Charte.ROUGE)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = a.dp(20) })

        vue.addView(a.surtitre(t.detail).apply { setPadding(a.dp(2), 0, 0, a.dp(8)) })
        val detail = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = a.fond(Color.WHITE, 16, Charte.BORDURE)
            // Le fond arrondi découpe les lignes touchables.
            clipToOutline = true
        }

        val n = a.app.reglages.listeOrigines().size
        val valeurAdresses = a.texte(if (n == 0) t.aucune else t.nombreAdresses(n), 14f, Charte.ROBOTO_MOYEN, if (n == 0) Charte.ROUGE else Charte.ENCRE)
        detail.addView(ligneDetail(t.autorisees, valeurAdresses, Chevron(a)) {
            a.aller(AccueilActivity.Vue.ADRESSES)
        })
        detail.addView(filet())
        detail.addView(ligneDetail(t.service, valeurService, pointService.apply { background = a.fond(Charte.VOYANT, 4) }, null))
        detail.addView(filet())
        val terminal = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(16), a.dp(15), a.dp(16), a.dp(15))
            addView(a.texte(t.terminal, 12f, Charte.ROBOTO, Charte.DISCRET, 1f).apply { setPadding(0, 0, 0, a.dp(5)) })
            addView(valeurTerminal)
            addView(capacitesTerminal.apply { setPadding(0, a.dp(4), 0, 0) })
        }
        detail.addView(terminal)
        vue.addView(detail, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        vue.addView(a.texte("v${BuildConfig.VERSION_NAME} · ${t.protocole} ${PontImpression.VERSION} · port ${ServeurImpression.PORT}", 11f, Charte.MONO, Charte.DISCRET).apply {
            gravity = Gravity.CENTER
            setPadding(0, a.dp(18), 0, a.dp(8))
            // Pour le développement : la page web de test, qui imprime par le pont du mode coque.
            setOnLongClickListener {
                a.startActivity(android.content.Intent(a, MainActivity::class.java).putExtra(MainActivity.EXTRA_ADRESSE, MainActivity.PAGE_DE_TEST))
                true
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        majService()
    }

    private fun ligneDetail(libelle: String, valeur: TextView, droite: View, action: (() -> Unit)?): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(a.dp(16), a.dp(15), a.dp(16), a.dp(15))
        if (action != null) {
            background = a.fondTouchable(Color.WHITE, 0)
            isClickable = true
            setOnClickListener { action() }
        }
        val colonne = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        colonne.addView(a.texte(libelle, 12f, Charte.ROBOTO, Charte.DISCRET, 1f).apply { setPadding(0, 0, 0, a.dp(5)) })
        colonne.addView(valeur)
        addView(colonne, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val chevron = droite is Chevron
        addView(droite, LinearLayout.LayoutParams(if (chevron) WRAP_CONTENT else a.dp(8), if (chevron) WRAP_CONTENT else a.dp(8)).apply { leftMargin = a.dp(12) })
    }

    private fun filet() = View(a).apply {
        setBackgroundColor(Charte.FILET)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, a.dp(1))
    }

    private fun majService() {
        val serveur = ServiceImpression.actif
        val (texte, couleur) = when {
            serveur == null -> t.serviceDemarrage to Charte.AMBRE
            !serveur.enMarche -> t.serviceArrete(serveur.erreur) to Charte.ROUGE
            else -> t.serviceEnEcoute(serveur.port) to Charte.VERT
        }
        valeurService.text = texte
        valeurService.setTextColor(couleur)
        pointService.background = a.fond(if (couleur == Charte.VERT) Charte.VOYANT else couleur, 4)
    }

    override fun etatChange(etat: JSONObject) {
        val term = a.lireTerminal(etat)
        pastille.couleur = term.couleur()
        titreEtat.text = t.titreEtat(term.code)
        val detail = when (term.code) {
            "prete" -> t.papierCharge
            "simulation" -> t.rienNImprime
            else -> term.message
        }
        ligneEtat.text = listOf(term.nom, term.mm, detail).filter { it.isNotEmpty() }.joinToString(" · ")
        valeurTerminal.text = "${term.nom} · ${term.pilote}"
        capacitesTerminal.text = t.capacitesCourtes(term.massicot, term.tiroir, term.ecran)
        majService()
    }
}

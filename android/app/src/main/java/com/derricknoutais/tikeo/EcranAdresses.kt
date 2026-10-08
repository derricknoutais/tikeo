package com.derricknoutais.tikeo

import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout

/**
 * Les adresses autorisées à imprimer, sur leur propre page : on les ajoute,
 * on les relit sous forme d'origine normalisée, on les retire. Et le
 * certificat auto-signé du mode coque, pour un serveur de développement.
 */
class EcranAdresses(private val a: AccueilActivity) : Ecran {

    private val t get() = a.textes

    override val vue = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(22))
        addView(a.texte(t.adressesTitre, 20f, Charte.ROBOTO_MOYEN, Charte.ENCRE, 1.2f).apply { setPadding(0, 0, 0, a.dp(6)) })
        addView(a.texte(t.adressesCorps, 13f, Charte.ROBOTO, Charte.TEXTE, 1.55f).apply { setPadding(0, 0, 0, a.dp(16)) })
        addView(EditeurAdresses(a, avecVide = true).vue, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val certificat = a.carte(14, 14).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val textes = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            textes.addView(a.texte(t.certificatTitre, 13f, Charte.ROBOTO_MOYEN).apply { setPadding(0, 0, 0, a.dp(4)) })
            textes.addView(a.texte(t.certificatCorps, 11f, Charte.ROBOTO, Charte.DISCRET, 1.5f))
            addView(textes, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = a.dp(12) })
            addView(Interrupteur(a, a.app.reglages.certificatLocal) { a.app.reglages.certificatLocal = it }.apply {
                contentDescription = t.certificatTitre
            })
        }
        addView(certificat, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = a.dp(16) })
        addView(a.texte(t.adressesPied, 11f, Charte.ROBOTO, Charte.DISCRET, 1.6f).apply { setPadding(a.dp(2), a.dp(14), 0, 0) })
    }
}

package com.derricknoutais.tikeo

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * La saisie des adresses autorisées, commune à la mise en route et à
 * l'onglet Adresses : un champ, son bouton « + », une aide qui dit en direct
 * ce qui sera retenu (l'origine normalisée), et la liste — la première
 * adresse est la principale, celle qu'ouvre le mode coque.
 */
class EditeurAdresses(private val activite: AccueilActivity, private val avecVide: Boolean) {

    private val textes get() = activite.textes
    private val reglages get() = activite.app.reglages

    val vue: LinearLayout = LinearLayout(activite).apply { orientation = LinearLayout.VERTICAL }
    private val champ: EditText
    private val aide: TextView
    private val vide: View?
    private val liste = LinearLayout(activite).apply { orientation = LinearLayout.VERTICAL }

    init {
        val a = activite
        val saisie = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        champ = EditText(a).apply {
            hint = "https://storit.stapog.com"
            setHintTextColor(Charte.DISCRET)
            // Le « + » prend la hauteur du champ : 48 dp à toucher.
            minimumHeight = a.dp(48)
            setTextColor(Charte.ENCRE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Charte.ROBOTO
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(a.dp(1), Color.parseColor("#D6D1C9"))
                val r = a.dpf(12f)
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
            setPadding(a.dp(14), a.dp(13), a.dp(14), a.dp(13))
            setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { ajouter(); true } else false }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, d: Int, n: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, d: Int, a: Int, n: Int) {}
                override fun afterTextChanged(s: Editable?) = majAide()
            })
        }
        saisie.addView(champ, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        saisie.addView(a.texte("+", 22f, Charte.ROBOTO_GRAS, Color.WHITE, 1f).apply {
            gravity = Gravity.CENTER
            contentDescription = textes.ajouter
            background = GradientDrawable().apply {
                setColor(Charte.CORPS)
                val r = a.dpf(12f)
                cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            }
            isClickable = true
            setOnClickListener { ajouter() }
        }, LinearLayout.LayoutParams(a.dp(52), MATCH_PARENT))
        vue.addView(saisie, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        aide = a.texte("", 12f, Charte.MONO, Charte.DISCRET).apply {
            setPadding(a.dp(2), a.dp(8), 0, a.dp(14))
            // Valide, invalide, déjà là : TalkBack l'annonce à mesure qu'on tape.
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        vue.addView(aide)

        vide = if (avecVide) LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FAF8F5"))
                cornerRadius = a.dpf(14f)
                setStroke(a.dp(1), Color.parseColor("#D6D1C9"), a.dpf(5f), a.dpf(4f))
            }
            setPadding(a.dp(18), a.dp(26), a.dp(18), a.dp(26))
            addView(a.texte(textes.videTitre, 14f, Charte.ROBOTO_MOYEN, Charte.ROUGE).apply { gravity = Gravity.CENTER })
            addView(a.texte(textes.videCorps, 12f, Charte.ROBOTO, Charte.DISCRET, 1.45f).apply {
                gravity = Gravity.CENTER
                setPadding(0, a.dp(5), 0, 0)
            })
        } else null
        vide?.let { vue.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }
        vue.addView(liste, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        majAide()
        majListe(anime = null)
    }

    private fun ajouter() {
        val origine = Reglages.origine(champ.text.toString()) ?: return
        if (!reglages.ajouterOrigine(origine)) return
        champ.setText("")
        activite.noter(textes.jAjoutee(origine), Genre.OK)
        majListe(anime = origine)
    }

    private fun majAide() {
        val saisi = champ.text.toString().trim()
        val origine = Reglages.origine(saisi)
        val (texte, couleur) = when {
            saisi.isEmpty() -> textes.aideVide to Charte.DISCRET
            origine == null -> textes.aideInvalide to Charte.ROUGE
            origine in reglages.listeOrigines() -> textes.aideDoublon to Charte.AMBRE
            else -> "→ $origine" to Charte.VERT_TEXTE
        }
        aide.text = texte
        aide.setTextColor(couleur)
    }

    private fun majListe(anime: String?) {
        liste.removeAllViews()
        val origines = reglages.listeOrigines()
        vide?.visibility = if (origines.isEmpty()) View.VISIBLE else View.GONE
        origines.forEachIndexed { i, origine ->
            val ligne = ligne(origine, if (i == 0) textes.principale else textes.autre)
            liste.addView(ligne, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = activite.dp(9) })
            if (origine == anime) {
                // L'arrivée de la maquette : un fondu qui monte de 6 points.
                ligne.alpha = 0f
                ligne.translationY = activite.dpf(6f)
                ligne.animate().alpha(1f).translationY(0f).setDuration(250).start()
            }
        }
        majAide()
    }

    private fun ligne(origine: String, role: String): LinearLayout {
        val a = activite
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = a.fond(Color.WHITE, 14, Charte.BORDURE)
            setPadding(a.dp(14), a.dp(8), a.dp(6), a.dp(8))
            val textesLigne = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            textesLigne.addView(a.texte(origine, 13f, Charte.MONO_GRAS, Charte.ENCRE))
            textesLigne.addView(a.texte(role, 11f, Charte.ROBOTO, Charte.DISCRET).apply { setPadding(0, a.dp(4), 0, 0) })
            addView(textesLigne, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(a.texte("×", 17f, Charte.ROBOTO, Charte.DISCRET, 1f).apply {
                gravity = Gravity.CENTER
                contentDescription = "${textes.retirer} $origine"
                // Le rond dessiné fait 32 dp ; la zone à toucher, 48.
                background = android.graphics.drawable.InsetDrawable(a.fondTouchable(Color.WHITE, 16, Charte.BORDURE, Color.argb(40, 185, 28, 28)), a.dp(8))
                isClickable = true
                setOnClickListener {
                    reglages.retirerOrigine(origine)
                    activite.noter(textes.jRetiree(origine), Genre.INFO)
                    majListe(anime = null)
                }
            }, LinearLayout.LayoutParams(a.dp(48), a.dp(48)).apply { leftMargin = a.dp(2) })
        }
    }
}

package com.derricknoutais.tikeo

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.TextView

/**
 * La charte de Tikéo : les couleurs du logo (planche « Tikéo Logo v2 »), la
 * Montserrat du mot-symbole, Roboto partout ailleurs — la police du terminal.
 *
 * Les rôles viennent de la maquette de l'application (« Tikéo — terminal
 * app »), ses couleurs de la planche : le corps sombre du terminal pour
 * l'en-tête et les actions, l'orange de la fente pour les repères (étapes,
 * onglet actif), le vert du voyant pour l'état. L'orange ne porte jamais de
 * texte : blanc sur #F38120 ne se lit pas assez.
 */
object Charte {
    // Planche « Tikéo Logo v2 ».
    val CORPS = Color.parseColor("#3B3B3D")
    val FENTE = Color.parseColor("#F38120")
    val VOYANT = Color.parseColor("#6DBE4A")
    val VERT = Color.parseColor("#2A8139")

    // Les neutres chauds de la planche.
    val FOND = Color.parseColor("#F0EEE9")
    val CARTE = Color.WHITE
    val BORDURE = Color.parseColor("#E4E0D9")
    val FILET = Color.parseColor("#F0EEE9")
    val ENCRE = Color.parseColor("#2B2A28")
    val TEXTE = Color.parseColor("#57534E")
    val DISCRET = Color.parseColor("#6B6762")
    val PALE = Color.parseColor("#B0AAA2")
    val CORPS_PRESSE = Color.parseColor("#2B2A28")

    val ROUGE = Color.parseColor("#B91C1C")
    /** Assez foncé pour un petit texte sur le fond de page (5,6:1). */
    val AMBRE = Color.parseColor("#9A4607")
    /** Le vert du texte, un ton plus foncé pour un petit texte sur le fond de page (#2A8139 n'y fait que 4,2:1). */
    val VERT_TEXTE = Color.parseColor("#23702F")
    /** Le bouton Imprimer pendant une impression : blanc dessus reste lisible (4,8:1). */
    val OCCUPE = Color.parseColor("#78716C")

    /** Le blanc à 72 % sur l'en-tête sombre. */
    val BLANC_DOUX = Color.argb(184, 255, 255, 255)

    /** La largeur de lecture : sur le 10″ couché d'un Z100, la colonne reste celle d'un téléphone. */
    const val LARGEUR_MAX_DP = 520

    private var montserratNoir: Typeface? = null
    private var montserratGras: Typeface? = null

    /** Le mot-symbole TIKÉO. Montserrat 900, réduite aux lettres du logo (assets/polices). */
    fun montserrat(contexte: Context, noir: Boolean = true): Typeface {
        val existant = if (noir) montserratNoir else montserratGras
        if (existant != null) return existant
        val chargee = try {
            Typeface.createFromAsset(contexte.assets, if (noir) "polices/montserrat_black.ttf" else "polices/montserrat_bold.ttf")
        } catch (e: RuntimeException) {
            Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        if (noir) montserratNoir = chargee else montserratGras = chargee
        return chargee
    }

    val ROBOTO: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val ROBOTO_MOYEN: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val ROBOTO_GRAS: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val MONO: Typeface = Typeface.MONOSPACE
    val MONO_GRAS: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
}

fun Context.dp(valeur: Int): Int = (valeur * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(valeur: Float): Float = valeur * resources.displayMetrics.density

/** Un fond arrondi, plein, avec ou sans filet. */
fun Context.fond(couleur: Int, rayonDp: Int, bordure: Int? = null, epaisseurDp: Int = 1): GradientDrawable =
    GradientDrawable().apply {
        setColor(couleur)
        cornerRadius = dpf(rayonDp.toFloat())
        if (bordure != null) setStroke(dp(epaisseurDp), bordure)
    }

/** Le même fond, qui réagit au toucher. */
fun Context.fondTouchable(couleur: Int, rayonDp: Int, bordure: Int? = null, ondulation: Int = Color.argb(28, 0, 0, 0)): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(ondulation), fond(couleur, rayonDp, bordure), fond(Color.BLACK, rayonDp))

/** Un texte de la charte : taille en sp, police, couleur. */
fun Context.texte(
    contenu: CharSequence,
    tailleSp: Float,
    police: Typeface = Charte.ROBOTO,
    couleur: Int = Charte.ENCRE,
    interligne: Float = 1.3f,
): TextView = TextView(this).apply {
    text = contenu
    setTextSize(TypedValue.COMPLEX_UNIT_SP, tailleSp)
    typeface = police
    setTextColor(couleur)
    setLineSpacing(0f, interligne)
    includeFontPadding = false
}

/** Le petit titre de section en capitales espacées : « DÉTAIL », « JOURNAL ». */
fun Context.surtitre(contenu: String, couleur: Int = Charte.DISCRET): TextView =
    texte(contenu.uppercase(), 11f, Charte.MONO_GRAS, couleur).apply { letterSpacing = 0.12f }

fun View.marges(gauche: Int = 0, haut: Int = 0, droite: Int = 0, bas: Int = 0) {
    val p = layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return
    p.setMargins(gauche, haut, droite, bas)
    layoutParams = p
}

/**
 * Les barres du système. Depuis Android 15, une application qui vise l'API 35
 * est dessinée dessous — l'en-tête passerait sous l'heure, les onglets sous
 * la barre de navigation : on leur rend leur place, et celle du clavier, par
 * une marge intérieure de `racine`. Avant Android 15, la barre d'état prend
 * simplement la couleur donnée.
 *
 * `sombre` : le haut de l'écran est foncé (l'en-tête Tikéo) — icônes claires.
 */
fun android.app.Activity.reserverBarresSysteme(racine: View, sombre: Boolean, couleurEtat: Int) {
    if (android.os.Build.VERSION.SDK_INT >= 35) {
        val claires = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        // Icônes claires sur un haut sombre, foncées sur un haut clair.
        window.insetsController?.setSystemBarsAppearance(if (sombre) 0 else claires, claires)
        racine.setOnApplyWindowInsetsListener { v, encarts ->
            val barres = encarts.getInsets(android.view.WindowInsets.Type.systemBars())
            val clavier = encarts.getInsets(android.view.WindowInsets.Type.ime())
            v.setPadding(barres.left, barres.top, barres.right, maxOf(barres.bottom, clavier.bottom))
            encarts
        }
    } else {
        @Suppress("DEPRECATION")
        window.statusBarColor = couleurEtat
    }
}

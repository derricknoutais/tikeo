package com.derricknoutais.tikeo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Le symbole Tikéo — le terminal : corps, fente, voyant vert, fente orange,
 * ticket qui sort —, dessiné d'après la géométrie de la planche (repère de
 * 300 unités centré sur le symbole). Deux palettes : `SOMBRE` pour un fond
 * foncé (corps blanc, la variante des icônes d'application), `CLAIR` pour un
 * fond clair (corps sombre, le verrou principal).
 */
class SymboleTikeo(contexte: Context, private val palette: Palette) : View(contexte) {

    enum class Palette { SOMBRE, CLAIR }

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val ticket = Path()

    override fun onDraw(canvas: Canvas) {
        val cote = minOf(width, height).toFloat()
        // La règle de la planche : sous 32 px de largeur visible (192 unités sur 300), trois dents.
        val petit = cote * 192f / 300f < 32f
        val echelle = cote / 300f
        canvas.save()
        canvas.translate(width / 2f, height / 2f - (if (petit) 22f else 21f) * echelle)
        canvas.scale(echelle, echelle)

        val sombre = palette == Palette.SOMBRE
        arrondi(canvas, -84f, -70f, 168f, 68f, 15f, if (sombre) Color.WHITE else Charte.CORPS)
        if (petit) arrondi(canvas, -58f, -46f, 74f, 16f, 8f, if (sombre) Charte.CORPS else Color.parseColor("#6B6762"))
        else arrondi(canvas, -58f, -48f, 74f, 14f, 7f, if (sombre) Charte.CORPS else Color.parseColor("#6B6762"))
        pinceau.color = Charte.VOYANT
        canvas.drawCircle(56f, -36f, if (petit) 13f else 12f, pinceau)
        arrondi(canvas, -96f, 10f, 192f, 46f, 23f, Charte.FENTE)
        arrondi(canvas, -62f, 26f, 124f, 13f, 6.5f, if (sombre) Charte.CORPS else Color.WHITE)

        ticket.reset()
        ticket.moveTo(-58f, 64f)
        ticket.lineTo(58f, 64f)
        val dents = if (petit) 3 else 4
        val pas = 116f / (dents * 2)
        val bas = if (petit) 100f else 98f
        ticket.lineTo(58f, bas)
        var x = 58f
        repeat(dents) {
            x -= pas; ticket.lineTo(x, bas + (if (petit) 14f else 14f))
            x -= pas; ticket.lineTo(x, bas)
        }
        ticket.close()
        pinceau.color = if (sombre) Color.WHITE else Color.parseColor("#CFC9C0")
        canvas.drawPath(ticket, pinceau)
        canvas.restore()
    }

    private fun arrondi(canvas: Canvas, x: Float, y: Float, l: Float, h: Float, r: Float, couleur: Int) {
        pinceau.color = couleur
        rect.set(x, y, x + l, y + h)
        canvas.drawRoundRect(rect, r, r, pinceau)
    }
}

/** Le voyant d'état : une pastille qui pulse, comme le voyant vert du terminal. */
class Pastille(contexte: Context) : View(contexte) {
    var couleur: Int = Charte.VOYANT
        set(valeur) { field = valeur; invalidate() }

    private val plein = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animation = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    // L'onde ne tourne que si on la voit : pas quand l'application est derrière le navigateur.
    override fun onVisibilityAggregated(visible: Boolean) {
        super.onVisibilityAggregated(visible)
        if (visible) { if (!animation.isStarted) animation.start() } else animation.cancel()
    }

    override fun onDetachedFromWindow() { animation.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = context.dpf(5f)
        // L'onde s'élargit de 9 dp et s'efface pendant les 70 premiers % du cycle.
        val p = (phase / 0.7f).coerceAtMost(1f)
        halo.color = couleur
        halo.alpha = ((1f - p) * 115).toInt()
        canvas.drawCircle(cx, cy, r + context.dpf(9f) * p, halo)
        plein.color = couleur
        canvas.drawCircle(cx, cy, r, plein)
    }
}

/** L'interrupteur de la maquette : piste de 48 × 28, bouton de 22. */
class Interrupteur(contexte: Context, actif: Boolean, private val change: (Boolean) -> Unit) : View(contexte) {
    var actif: Boolean = actif
        private set
    private val piste = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bouton = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(context.dpf(1.5f), 0f, context.dpf(1f), Color.argb(76, 28, 25, 23)) }
    private var position = if (actif) 1f else 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            this.actif = !this.actif
            ValueAnimator.ofFloat(position, if (this.actif) 1f else 0f).apply {
                duration = 200
                addUpdateListener { position = it.animatedValue as Float; invalidate() }
            }.start()
            change(this.actif)
        }
    }

    /** 48 dp à toucher ; la piste dessinée garde ses 28 dp de haut. */
    override fun onMeasure(l: Int, h: Int) = setMeasuredDimension(context.dp(48), context.dp(48))

    override fun getAccessibilityClassName(): CharSequence = android.widget.Switch::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = true
        info.isChecked = actif
    }

    override fun onDraw(canvas: Canvas) {
        val h = context.dpf(28f)
        val haut = (height - h) / 2f
        // Éteinte, la piste se détache du blanc de la carte (3:1).
        piste.color = melange(Color.parseColor("#8F8981"), Charte.VERT, position)
        canvas.drawRoundRect(0f, haut, width.toFloat(), haut + h, h / 2, h / 2, piste)
        val r = context.dpf(11f)
        val x = context.dpf(3f) + r + (width - context.dpf(6f) - 2 * r) * position
        canvas.drawCircle(x, height / 2f, r, bouton)
    }

    private fun melange(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}

/** Le bord dentelé d'un ticket, en haut ou en bas de l'aperçu. */
class Dentele(contexte: Context, private val couleurFond: Int, private val couleurPapier: Int) : View(contexte) {
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chemin = Path()

    override fun onMeasure(l: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(l), context.dp(6))

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(couleurPapier)
        val pas = context.dpf(10f)
        val h = height.toFloat()
        chemin.reset()
        var x = 0f
        chemin.moveTo(0f, 0f)
        while (x < width) {
            chemin.lineTo(x + pas / 2, h)
            chemin.lineTo(x + pas, 0f)
            x += pas
        }
        chemin.close()
        pinceau.color = couleurFond
        canvas.drawPath(chemin, pinceau)
    }
}

/** Les icônes de la barre d'onglets, tracées comme dans la maquette. */
class IconeOnglet(contexte: Context, private val forme: Forme) : View(contexte) {
    enum class Forme { ACCUEIL, ADRESSES, TEST }

    var couleur: Int = Charte.DISCRET
        set(valeur) { field = valeur; invalidate() }

    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onMeasure(l: Int, h: Int) = setMeasuredDimension(context.dp(22), context.dp(20))

    override fun onDraw(canvas: Canvas) {
        val e = context.dpf(2f)
        trait.strokeWidth = e
        trait.color = couleur
        trait.pathEffect = null
        val cx = width / 2f
        val cy = height / 2f
        when (forme) {
            Forme.ACCUEIL -> {
                val l = context.dpf(18f); val h = context.dpf(14f)
                canvas.drawRoundRect(cx - l / 2 + e / 2, cy - h / 2 + e / 2, cx + l / 2 - e / 2, cy + h / 2 - e / 2, context.dpf(3f), context.dpf(3f), trait)
            }
            Forme.ADRESSES -> canvas.drawCircle(cx, cy, context.dpf(8f) - e / 2, trait)
            Forme.TEST -> {
                val l = context.dpf(14f); val h = context.dpf(17f)
                val g = cx - l / 2 + e / 2; val d = cx + l / 2 - e / 2
                val haut = cy - h / 2 + e / 2; val bas = cy + h / 2 - e / 2
                canvas.drawLine(g, haut, d, haut, trait)
                canvas.drawLine(g, haut, g, bas, trait)
                canvas.drawLine(d, haut, d, bas, trait)
                trait.pathEffect = DashPathEffect(floatArrayOf(context.dpf(2.5f), context.dpf(2f)), 0f)
                canvas.drawLine(g, bas, d, bas, trait)
            }
        }
    }
}

/** Le chevron d'une ligne qui mène ailleurs. */
class Chevron(contexte: Context) : View(contexte) {
    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Charte.PALE
    }

    override fun onMeasure(l: Int, h: Int) = setMeasuredDimension(context.dp(10), context.dp(14))

    override fun onDraw(canvas: Canvas) {
        trait.strokeWidth = context.dpf(2f)
        val e = context.dpf(2f)
        canvas.drawLine(e, e, width - e, height / 2f, trait)
        canvas.drawLine(width - e, height / 2f, e, height - e, trait)
    }
}

/** Une carte blanche à filet, comme dans la maquette (rayon 16). */
fun Context.carte(rayonDp: Int = 16, paddingDp: Int = 18): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = fond(Charte.CARTE, rayonDp, Charte.BORDURE)
    setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
}

/** Le gros bouton d'action : titre et sous-titre, fond sombre. */
fun Context.boutonPrincipal(titre: String, sousTitre: String?, action: () -> Unit): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = fondTouchable(Charte.CORPS, 16, ondulation = Color.argb(60, 255, 255, 255))
    setPadding(dp(18), dp(18), dp(18), dp(18))
    isClickable = true
    setOnClickListener { action() }
    addView(texte(titre, 16f, Charte.ROBOTO_GRAS, Color.WHITE))
    if (sousTitre != null) addView(texte(sousTitre, 12f, Charte.ROBOTO, Charte.BLANC_DOUX).apply { setPadding(0, dp(4), 0, 0) })
}

/** Le bouton secondaire : même forme, fond blanc à filet. */
fun Context.boutonSecondaire(titre: String, sousTitre: String?, action: () -> Unit): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = fondTouchable(Charte.CARTE, 16, Color.parseColor("#D6D1C9"))
    setPadding(dp(18), dp(18), dp(18), dp(18))
    isClickable = true
    setOnClickListener { action() }
    addView(texte(titre, 16f, Charte.ROBOTO_MOYEN, Charte.ENCRE))
    if (sousTitre != null) addView(texte(sousTitre, 12f, Charte.ROBOTO, Charte.DISCRET).apply { setPadding(0, dp(4), 0, 0) })
}

/** Un bouton plein, d'une ligne : « Continuer », « Imprimer ». */
fun Context.bouton(libelle: String, plein: Boolean, action: () -> Unit): TextView = texte(
    libelle, if (plein) 15f else 14f, if (plein) Charte.ROBOTO_GRAS else Charte.ROBOTO_MOYEN, if (plein) Color.WHITE else Charte.TEXTE,
).apply {
    gravity = Gravity.CENTER
    background = if (plein) fondTouchable(Charte.CORPS, 12, ondulation = Color.argb(60, 255, 255, 255))
    else fondTouchable(Charte.CARTE, 12, Color.parseColor("#D6D1C9"))
    setPadding(dp(18), dp(15), dp(18), dp(15))
    isClickable = true
    setOnClickListener { action() }
}

/** Les petits boutons d'action de l'onglet Test, deux par ligne. */
fun Context.boutonAction(libelle: String, action: () -> Unit): TextView = texte(libelle, 13f, Charte.ROBOTO_MOYEN, Charte.ENCRE).apply {
    gravity = Gravity.CENTER
    minimumHeight = dp(48)
    background = fondTouchable(Charte.CARTE, 11, Color.parseColor("#D6D1C9"))
    setPadding(dp(8), dp(13), dp(8), dp(13))
    isClickable = true
    setOnClickListener { action() }
}

/**
 * Le sélecteur à segments de l'onglet Test (Reçu · Mire · Étiquette) : le
 * segment choisi est une pastille blanche sur la piste.
 */
class Segments(contexte: Context, private val libelles: List<String>, choisi: Int, private val change: (Int) -> Unit) : LinearLayout(contexte) {
    private val boutons = mutableListOf<TextView>()

    init {
        orientation = HORIZONTAL
        background = contexte.fond(Color.parseColor("#E4E0D9"), 11)
        setPadding(contexte.dp(3), 0, contexte.dp(3), 0)
        libelles.forEachIndexed { i, libelle ->
            val b = contexte.texte(libelle, 13f, Charte.ROBOTO_MOYEN).apply {
                gravity = Gravity.CENTER
                // 48 dp à toucher ; la pastille blanche, en retrait, garde l'allure de la maquette.
                minimumHeight = contexte.dp(48)
                isClickable = true
                setOnClickListener { choisir(i); change(i) }
            }
            boutons += b
            addView(b, LayoutParams(0, WRAP_CONTENT, 1f))
        }
        choisir(choisi)
    }

    fun choisir(index: Int) {
        boutons.forEachIndexed { i, b ->
            val actif = i == index
            b.setTextColor(if (actif) Charte.ENCRE else Charte.TEXTE)
            b.background = if (actif) android.graphics.drawable.InsetDrawable(context.fond(Color.WHITE, 9), 0, context.dp(3), 0, context.dp(3)) else null
            b.isSelected = actif
        }
    }
}

/** Une ligne « libellé : valeur » de la carte « Terminal reconnu ». */
fun Context.ligneValeur(libelle: String, valeur: String): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    setPadding(0, dp(5), 0, dp(5))
    addView(texte(libelle, 13f, Charte.ROBOTO, Charte.DISCRET), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    addView(texte(valeur, 13f, Charte.ROBOTO_MOYEN, Charte.ENCRE).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(12) })
}

/** Un espace vertical. */
fun Context.espace(hauteurDp: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(hauteurDp)) }

/**
 * Un cadre qui ne dépasse pas `maxPx` de haut : l'enfant garde sa hauteur
 * naturelle et le cadre le coupe — comme un ticket encore en train de sortir.
 */
class Plafond(contexte: Context, private val maxPx: Int) : android.widget.FrameLayout(contexte) {
    override fun onMeasure(largeur: Int, hauteur: Int) {
        super.onMeasure(largeur, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        if (measuredHeight > maxPx) setMeasuredDimension(measuredWidth, maxPx)
    }
}

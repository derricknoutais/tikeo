package com.derricknoutais.tikeo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject

/**
 * Ce que le pont et le service local lisent de la même façon dans une
 * demande de la page : les options d'impression, et l'image en PNG base64.
 */
object Protocole {

    /**
     * Doit rester égale à VERSION_PONT dans src/pont.ts.
     * 2 : étiquettes, écran client, capacités. 3 : tiroir-caisse.
     * 4 : l'application devient Tikéo — le pont s'appelle `window.Tikeo`.
     */
    const val VERSION = "4"

    /** `{avance, support: 'recu' | 'etiquette', copies, tiroir}`, bornés : une page qui se trompe ne vide pas le rouleau. */
    fun options(json: JSONObject?): OptionsImpression = OptionsImpression(
        avance = (json?.optInt("avance", 3) ?: 3).coerceIn(0, 20),
        etiquette = json?.optString("support") == "etiquette",
        copies = (json?.optInt("copies", 1) ?: 1).coerceIn(1, 50),
        tiroir = json?.optBoolean("tiroir", false) ?: false,
    )

    fun image(pngBase64: String?): Bitmap? = try {
        if (pngBase64.isNullOrEmpty()) null
        else Base64.decode(pngBase64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    } catch (e: IllegalArgumentException) {
        null
    }

    fun echec(code: String, message: String): JSONObject =
        JSONObject().put("ok", false).put("code", code).put("message", message)
}

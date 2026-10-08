package com.derricknoutais.tikeo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ScrollView
import androidx.webkit.WebViewAssetLoader

/**
 * Le mode coque : une page web plein écran, avec ce que le navigateur ne lui
 * donne pas sur un terminal — l'imprimante par le pont direct
 * (window.Tikeo) et la caméra (relais de la permission Android).
 *
 * Sert d'abord à la page de test embarquée. Pour une application web, le
 * navigateur du terminal est souvent le meilleur choix : cette WebView est
 * celle du système, en Chrome 62 sur un V2 Pro.
 */
class MainActivity : Activity() {

    lateinit var webView: WebView
        private set

    private val app get() = application as TikeoApp
    private val reglages get() = app.reglages

    /** L'adresse de la page affichée — lue par le pont, depuis un autre fil. */
    @Volatile
    private var pageCourante: String = ""

    private var fichiersEnAttente: ValueCallback<Array<Uri>>? = null
    private var cameraEnAttente: PermissionRequest? = null

    /** La page de test, servie depuis les ressources de l'APK sous une vraie origine https. */
    private val ressources by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    override fun onCreate(etatSauve: Bundle?) {
        super.onCreate(etatSauve)

        val adresse = intent.getStringExtra(EXTRA_ADRESSE) ?: reglages.adressePrincipale
        if (adresse.isNullOrBlank()) {
            startActivity(Intent(this, AccueilActivity::class.java))
            finish()
            return
        }

        // En version de développement : chrome://inspect depuis le poste, par USB.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        webView = WebView(this)
        // La WebView ignore sa propre marge intérieure : c'est un cadre qui prend celle des barres du
        // système et du clavier (Android 15), et la WebView, dedans, est vraiment redimensionnée.
        val cadre = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(webView, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(cadre)
        reserverBarresSysteme(cadre, sombre = true, couleurEtat = android.graphics.Color.BLACK)
        configurer()

        if (etatSauve != null) webView.restoreState(etatSauve) else webView.loadUrl(adresse)
    }

    override fun onNewIntent(nouvelle: Intent) {
        super.onNewIntent(nouvelle)
        // Relancée depuis les réglages : ouvrir l'adresse demandée.
        val adresse = nouvelle.getStringExtra(EXTRA_ADRESSE)
        if (!adresse.isNullOrBlank() && ::webView.isInitialized) webView.loadUrl(adresse)
    }

    private fun configurer() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Le flux de la caméra démarre sans geste supplémentaire.
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            userAgentString = "$userAgentString Tikeo/${BuildConfig.VERSION_NAME}"
        }

        webView.addJavascriptInterface(PontImpression(this, app), "Tikeo")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(vue: WebView, requete: WebResourceRequest): WebResourceResponse? =
                ressources.shouldInterceptRequest(requete.url)

            override fun onPageStarted(vue: WebView, url: String, icone: Bitmap?) {
                pageCourante = url
            }

            override fun doUpdateVisitedHistory(vue: WebView, url: String, rechargement: Boolean) {
                pageCourante = url
            }

            override fun shouldOverrideUrlLoading(vue: WebView, requete: WebResourceRequest): Boolean {
                val cible = requete.url
                // Toute page web reste ici — la connexion SSO passe par un autre
                // domaine et doit revenir dans la même session. Le reste (tel:,
                // mailto:, intent:) part vers l'application qui sait l'ouvrir.
                if (cible.scheme == "http" || cible.scheme == "https") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, cible))
                } catch (e: ActivityNotFoundException) {
                    Log.w(JOURNAL, "Aucune application pour $cible")
                }
                return true
            }

            override fun onReceivedSslError(vue: WebView, gestionnaire: SslErrorHandler, erreur: SslError) {
                val hote = Uri.parse(erreur.url).host.orEmpty()
                if (reglages.certificatLocal && estAdressePrivee(hote)) {
                    gestionnaire.proceed()
                } else {
                    gestionnaire.cancel()
                    signalerPageInjoignable(
                        erreur.url,
                        if (estAdressePrivee(hote)) textes.certificatRefuseLocal else textes.certificatRefuse,
                    )
                }
            }

            override fun onReceivedError(vue: WebView, requete: WebResourceRequest, erreur: WebResourceError) {
                if (requete.isForMainFrame) signalerPageInjoignable(requete.url.toString(), erreur.description.toString())
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(requete: PermissionRequest) {
                runOnUiThread { traiterDemandeCamera(requete) }
            }

            override fun onShowFileChooser(vue: WebView, rappel: ValueCallback<Array<Uri>>, parametres: FileChooserParams): Boolean {
                fichiersEnAttente?.onReceiveValue(null)
                fichiersEnAttente = rappel
                return try {
                    startActivityForResult(parametres.createIntent(), DEMANDE_FICHIERS)
                    true
                } catch (e: ActivityNotFoundException) {
                    fichiersEnAttente = null
                    false
                }
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d(JOURNAL, "${message.messageLevel()} ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }
    }

    /** Vrai si la page affichée est une adresse autorisée, ou la page de test. */
    fun pageDeConfiance(): Boolean = deConfiance(pageCourante)

    private fun deConfiance(adresse: String): Boolean {
        val origine = Reglages.origine(adresse) ?: return false
        return origine == Reglages.origine(PAGE_DE_TEST) || origine in reglages.origines()
    }

    fun executer(script: String) {
        if (::webView.isInitialized) webView.evaluateJavascript(script, null)
    }

    /** Sur un appareil sans imprimante : le reçu tel qu'il serait sorti. */
    fun montrerSimulation(image: Bitmap) {
        val vue = ImageView(this).apply {
            setImageBitmap(image)
            adjustViewBounds = true
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(24, 24, 24, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Simulation — aucune imprimante")
            .setView(ScrollView(this).apply { addView(vue) })
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun traiterDemandeCamera(requete: PermissionRequest) {
        val camera = requete.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        if (!camera || !deConfiance(requete.origin.toString())) {
            requete.deny()
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            requete.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        } else {
            cameraEnAttente?.deny()
            cameraEnAttente = requete
            requestPermissions(arrayOf(Manifest.permission.CAMERA), DEMANDE_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, resultats: IntArray) {
        super.onRequestPermissionsResult(code, permissions, resultats)
        if (code != DEMANDE_CAMERA) return
        val requete = cameraEnAttente ?: return
        cameraEnAttente = null
        if (resultats.isNotEmpty() && resultats[0] == PackageManager.PERMISSION_GRANTED) {
            requete.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        } else {
            requete.deny()
        }
    }

    @Deprecated("API Activity historique, la seule disponible sans AndroidX Activity")
    override fun onActivityResult(code: Int, resultat: Int, donnees: Intent?) {
        super.onActivityResult(code, resultat, donnees)
        if (code != DEMANDE_FICHIERS) return
        fichiersEnAttente?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultat, donnees))
        fichiersEnAttente = null
    }

    private fun signalerPageInjoignable(adresse: String, raison: String) {
        AlertDialog.Builder(this)
            .setTitle(textes.pageInjoignable)
            .setMessage("$adresse\n\n$raison")
            .setPositiveButton(textes.reessayer) { _, _ -> webView.reload() }
            .setNeutralButton(textes.ongletAdresses) { _, _ -> ouvrirReglages(AccueilActivity.Vue.ADRESSES) }
            .setCancelable(true)
            .show()
    }

    private fun ouvrirReglages(vue: AccueilActivity.Vue = AccueilActivity.Vue.ACCUEIL) {
        startActivity(Intent(this, AccueilActivity::class.java).putExtra(AccueilActivity.EXTRA_VUE, vue.name))
    }

    /** Les textes dans la langue choisie dans l'application. */
    private val textes get() = Textes(reglages.langue == "en")

    @Deprecated("Retour arrière d'Activity, suffisant sans AndroidX")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            return
        }
        AlertDialog.Builder(this)
            .setItems(arrayOf(textes.menuAccueil, textes.menuTest, textes.quitter)) { _, choix ->
                when (choix) {
                    0 -> ouvrirReglages()
                    1 -> webView.loadUrl(PAGE_DE_TEST)
                    else -> finish()
                }
            }
            .setNegativeButton(textes.annuler, null)
            .show()
    }

    override fun onSaveInstanceState(etat: Bundle) {
        super.onSaveInstanceState(etat)
        if (::webView.isInitialized) webView.saveState(etat)
    }

    override fun onDestroy() {
        // Le pilote est partagé avec le service local : on ne l'arrête pas ici.
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ADRESSE = "adresse"
        const val PAGE_DE_TEST = "https://appassets.androidplatform.net/assets/test/index.html"
        private const val JOURNAL = "Tikeo"
        private const val DEMANDE_CAMERA = 1
        private const val DEMANDE_FICHIERS = 2

        /** 10/8, 172.16/12, 192.168/16 : le réseau du magasin, pas Internet. */
        fun estAdressePrivee(hote: String): Boolean {
            val octets = hote.split('.').mapNotNull { it.toIntOrNull() }
            if (octets.size != 4 || octets.any { it !in 0..255 }) return false
            return octets[0] == 10 ||
                (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168)
        }
    }
}

package com.derricknoutais.tikeo

/**
 * Les textes de l'interface, en français et en anglais — ceux de la maquette
 * « Tikéo — terminal app », plus ce que les états réels du terminal
 * demandent. La langue se choisit dans l'en-tête et se garde dans les réglages.
 */
class Textes(val anglais: Boolean) {
    private fun t(fr: String, en: String) = if (anglais) en else fr

    /** L'endossement ne se traduit pas : c'est le verrou de la marque (et la police n'a que ses lettres). */
    val parEcolight = "PAR ECOLIGHT"
    val etapes = t("Mise en route", "Setup")
    val terminal = t("Terminal", "Terminal")
    val pilote = t("Pilote", "Driver")
    val papier = t("Papier", "Paper")
    val capacites = t("Capacités", "Capabilities")
    val reconnu = t("Terminal reconnu", "Terminal recognised")
    val enCours = t("Reconnaissance du terminal…", "Recognising the terminal…")

    val testerImprimante = t("Tester l'imprimante", "Test the printer")
    val testerImprimanteSous = t("Reçu d'exemple, mire, étiquette, tiroir", "Sample receipt, test page, label, drawer")
    val ouvrirApplication = t("Ouvrir l'application ici", "Open the app here")
    val ouvrirApplicationSous = t("Mode coque — à réserver aux pages compatibles", "Shell mode — only for compatible pages")
    val ouvrirApplicationVide = t("Ajoutez d'abord une adresse autorisée", "Add an allowed address first")
    val detail = t("Détail", "Details")
    val autorisees = t("Adresses autorisées", "Allowed addresses")
    val service = t("Service local", "Local service")

    val adressesTitre = t("Adresses autorisées", "Allowed addresses")
    val adressesCorps = t(
        "Les pages de ces adresses, ouvertes dans le navigateur du terminal, peuvent imprimer. Les autres sont refusées.",
        "Pages from these addresses, opened in the terminal's browser, may print. Everything else is refused.",
    )
    val videTitre = t("Aucune adresse autorisée", "No allowed address")
    val videCorps = t("Aucune page ne peut imprimer tant que cette liste est vide.", "No page can print while this list is empty.")
    val certificatTitre = t("Certificat auto-signé", "Self-signed certificate")
    val certificatCorps = t(
        "Accepter un certificat auto-signé sur le réseau local, pour un serveur de développement.",
        "Accept a self-signed certificate on the local network, for a development server.",
    )
    val adressesPied = t(
        "Seule l'origine compte : schéma, hôte, port. Le reste de l'adresse est ignoré.",
        "Only the origin counts: scheme, host, port. The rest of the address is ignored.",
    )
    val retirer = t("Retirer", "Remove")
    val ajouter = t("Ajouter", "Add")
    fun etapeSur(n: Int) = t("Étape $n sur 3", "Step $n of 3")

    // Le mode coque.
    val pageInjoignable = t("Page injoignable", "Page unreachable")
    val reessayer = t("Réessayer", "Retry")
    val certificatRefuseLocal = t(
        "Certificat refusé. Serveur de développement : activer « Certificat auto-signé » dans l'onglet Adresses.",
        "Certificate refused. Development server: turn on « Self-signed certificate » in the Addresses tab.",
    )
    val certificatRefuse = t("Certificat refusé : la connexion n'est pas sûre.", "Certificate refused: the connection is not secure.")
    val menuAccueil = t("Accueil de Tikéo", "Tikéo home")
    val menuTest = t("Page de test de l'imprimante", "Printer test page")
    val quitter = t("Quitter", "Quit")
    val annuler = t("Annuler", "Cancel")

    val ongletAccueil = t("Accueil", "Home")
    val ongletAdresses = t("Adresses", "Addresses")
    val ongletTest = t("Test", "Test")
    val recu = t("Reçu", "Receipt")
    val mire = t("Mire", "Test page")
    val etiquette = t("Étiquette", "Label")
    val ecranClient = t("Écran client", "Customer screen")
    val tiroir = t("Tiroir-caisse", "Cash drawer")
    val effacerEcran = t("Effacer l'écran", "Clear screen")
    val actualiser = t("Actualiser l'état", "Refresh status")
    val journal = t("Journal", "Log")
    val journalVide = t("Les essais s'inscrivent ici.", "Your tests show up here.")
    val imprimer = t("Imprimer", "Print")
    val impression = t("Impression…", "Printing…")
    val retour = t("Retour", "Back")
    val continuer = t("Continuer", "Continue")
    val terminer = t("Terminer", "Finish")
    val imprimerExemple = t("Imprimer un reçu d'exemple", "Print a sample receipt")
    val pretNote = t(
        "Le service écoute sur 127.0.0.1:17321 et démarre à chaque allumage du terminal.",
        "The service listens on 127.0.0.1:17321 and starts whenever the terminal boots.",
    )

    val etapesTitres = listOf(
        t("Votre terminal", "Your terminal") to t(
            "Tikéo a reconnu le terminal et choisi son pilote d'impression. Rien à régler ici.",
            "Tikéo recognised the terminal and picked its printer driver. Nothing to set here.",
        ),
        t("Qui peut imprimer ?", "Who may print?") to t(
            "Ajoutez l'adresse de votre application web. Seules ces pages pourront imprimer.",
            "Add your web app's address. Only those pages will be allowed to print.",
        ),
        t("Tout est prêt", "All set") to t(
            "Vérifiez le papier et la tête thermique avec un reçu d'exemple.",
            "Check the paper and the thermal head with a sample receipt.",
        ),
    )

    val aideVide = t("Une adresse — https:// ou http://", "One address — https:// or http://")
    val aideInvalide = t("Ce n'est pas une adresse web valide", "That is not a valid web address")
    val aideDoublon = t("Cette origine est déjà dans la liste", "That origin is already in the list")
    val principale = t("Principale — ouverte en mode coque", "Primary — opened in shell mode")
    val autre = t("Autorisée à imprimer", "Allowed to print")
    val aucune = t("Aucune — personne ne peut imprimer", "None — nobody can print")
    fun nombreAdresses(n: Int) = if (n == 1) t("1 adresse", "1 address") else t("$n adresses", "$n addresses")

    fun serviceEnEcoute(port: Int) = t("En écoute sur 127.0.0.1:$port", "Listening on 127.0.0.1:$port")
    val serviceDemarrage = t("Démarrage…", "Starting…")
    fun serviceArrete(raison: String?) = t("Arrêté — ${raison ?: "port déjà pris"}", "Stopped — ${raison ?: "port already in use"}")

    // L'état de l'imprimante, d'après le code du pilote.
    fun titreEtat(code: String) = when (code) {
        "prete" -> t("Imprimante prête", "Printer ready")
        "simulation" -> t("Simulation", "Simulation")
        "papier" -> t("Plus de papier", "Out of paper")
        "capot" -> t("Capot ouvert", "Cover open")
        "surchauffe" -> t("Tête trop chaude", "Print head too hot")
        "occupee" -> t("Imprimante occupée", "Printer busy")
        "absente" -> t("Pas d'imprimante", "No printer")
        else -> t("Imprimante en défaut", "Printer error")
    }
    val papierCharge = t("papier chargé", "paper loaded")
    val rienNImprime = t("aucune imprimante reconnue, rien n'est imprimé", "no printer recognised, nothing is printed")
    val protocole = t("protocole", "protocol")
    fun capacitesCourtes(massicot: Boolean, tiroir: Boolean?, ecran: String?) = listOf(
        t("Massicot", "Cutter") + " " + ouiNon(massicot),
        t("tiroir", "drawer") + " " + (tiroir?.let { ouiNon(it) } ?: t("à l'essai", "untested")),
        t("écran", "screen") + " " + (ecran ?: t("non", "no")),
    ).joinToString(" · ")
    private fun ouiNon(b: Boolean) = if (b) t("oui", "yes") else t("non", "no")
    val noirEtBlanc = t("N/B", "B/W")

    // Le journal de l'onglet Test.
    val jRecu = t("Reçu imprimé", "Receipt printed")
    val jMire = t("Mire imprimée", "Test page printed")
    val jEtiquette = t("Étiquette imprimée", "Label printed")
    val jSimulation = t("Simulation : rien n'est imprimé", "Simulation: nothing printed")
    val jEcran = t("Écran client affiché", "Customer screen shown")
    val jEfface = t("Écran client effacé", "Customer screen cleared")
    val jTiroir = t("Tiroir-caisse ouvert", "Cash drawer opened")
    fun jEtat(titre: String) = t("État actualisé : $titre", "Status refreshed: $titre")
    val jPasDEcran = t("[non-pris-en-charge] Ce terminal n'a pas d'écran client.", "[unsupported] This terminal has no customer screen.")
    val jCoque = t("Mode coque : page ouverte dans Tikéo.", "Shell mode: page opened in Tikéo.")
    fun jAjoutee(o: String) = t("Adresse ajoutée : $o", "Address added: $o")
    fun jRetiree(o: String) = t("Adresse retirée : $o", "Address removed: $o")
    fun duree(ms: Long) = "(" + String.format(java.util.Locale.FRANCE.takeUnless { anglais } ?: java.util.Locale.UK, "%.1f", ms / 1000.0) + " s)"
}

import { type EtatImprimante, type OptionsEnvoi, type ResultatImpression } from './etat.ts';
/**
 * Le pont direct : la page est ouverte DANS l'application Tikéo, qui
 * l'affiche dans sa WebView et lui injecte `window.Tikeo`. Ses
 * réponses, asynchrones, reviennent par `window.__tikeo.retour`.
 */
/**
 * Version du protocole entre la page et l'application.
 * 2 : étiquettes (`support`, `copies`), écran client (`afficher`, `effacer`), capacités dans l'état.
 * 3 : tiroir-caisse (`ouvrirTiroir`, option `tiroir` d'une impression).
 * 4 : l'application devient Tikéo — le pont s'appelle `window.Tikeo`, ses réponses `window.__tikeo`.
 *     Une page d'avant, ouverte dans Tikéo, ne trouve plus `window.EcoPrint` et passe par le service local.
 */
export declare const VERSION_PONT = "4";
/** Vrai si la page est ouverte dans l'application Tikéo. */
export declare function pontDisponible(): boolean;
/** Version du protocole annoncée par l'application, ou `null` hors application. */
export declare function versionPont(): string | null;
/** L'état de l'imprimante par le pont — synchrone, c'est un appel direct. */
export declare function etatPont(): EtatImprimante;
/**
 * Envoie une image PNG (base64, sans le préfixe `data:`) à imprimer. Se
 * résout quand le reçu — ou la dernière étiquette — est SORTI.
 */
export declare function envoyerParPont(pngBase64: string, options?: OptionsEnvoi): Promise<ResultatImpression>;
/** Ouvre le tiroir-caisse branché sur le terminal. */
export declare function ouvrirTiroirParPont(options?: {
    delai?: number;
}): Promise<ResultatImpression>;
/** Affiche une image PNG (base64) sur l'écran client — ou l'efface si elle vaut `null`. */
export declare function afficherParPont(pngBase64: string | null, options?: {
    delai?: number;
}): Promise<ResultatImpression>;

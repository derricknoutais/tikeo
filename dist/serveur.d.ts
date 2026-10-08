import { type EtatImprimante, type OptionsEnvoi, type ResultatImpression } from './etat.ts';
/**
 * Le service local : la page est ouverte dans le NAVIGATEUR du terminal, et
 * l'application Tikéo, en service de fond, écoute sur 127.0.0.1.
 *
 * C'est le mode à préférer sur un Sunmi V2 Pro : son navigateur est un
 * Chromium 74, mais les applications Android y affichent leurs pages avec le
 * WebView système, resté en version 62 — où une application Vite ne démarre
 * pas. La page reste donc dans le navigateur, et seule l'image du reçu passe
 * par l'application.
 *
 * Une requête d'une page https vers http://127.0.0.1 n'est pas du contenu
 * mixte : l'adresse de boucle locale est tenue pour sûre. L'application ne
 * répond qu'aux origines qu'on lui a autorisées.
 */
export declare const PORT_PAR_DEFAUT = 17321;
export interface OptionsServeur {
    /** Port du service ; 17321 par défaut. `false` : ne pas chercher le service. */
    port?: number | false;
    /** Attente maximale de la détection, en ms ; 1 500 par défaut. */
    delaiDetection?: number;
}
/**
 * L'état de l'imprimante par le service, ou `null` s'il ne répond pas :
 * pas de service, ou page ouverte ailleurs que sur un terminal.
 */
export declare function etatServeur(options?: OptionsServeur): Promise<EtatImprimante | null>;
/**
 * Envoie l'image PNG (base64) à imprimer. Se résout quand le reçu — ou la
 * dernière étiquette — est sorti, se rejette avec une `ErreurImpression` sinon.
 */
export declare function envoyerAuServeur(pngBase64: string, options?: OptionsServeur & OptionsEnvoi): Promise<ResultatImpression>;
/** Ouvre le tiroir-caisse branché sur le terminal. */
export declare function ouvrirTiroirAuServeur(options?: OptionsServeur & {
    delai?: number;
}): Promise<ResultatImpression>;
/** Affiche une image PNG (base64) sur l'écran client — ou l'efface si elle vaut `null`. */
export declare function afficherAuServeur(pngBase64: string | null, options?: OptionsServeur & {
    delai?: number;
}): Promise<ResultatImpression>;

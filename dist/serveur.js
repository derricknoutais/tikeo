var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
import { ErreurImpression, lireEtat, lireVerdict } from "./etat.js";
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
export const PORT_PAR_DEFAUT = 17321;
function adresse(port, chemin) {
    return `http://127.0.0.1:${port}${chemin}`;
}
/**
 * L'état de l'imprimante par le service, ou `null` s'il ne répond pas :
 * pas de service, ou page ouverte ailleurs que sur un terminal.
 */
export function etatServeur() {
    return __awaiter(this, arguments, void 0, function* (options = {}) {
        if (options.port === false)
            return null;
        const port = options.port || PORT_PAR_DEFAUT;
        try {
            const reponse = yield avecDelai(fetch(adresse(port, '/etat'), { cache: 'no-store' }), options.delaiDetection || 1500);
            // Un refus (403) arrive avec les en-têtes CORS : la page peut dire pourquoi.
            return lireEtat(yield reponse.json(), 'serveur');
        }
        catch (_a) {
            return null;
        }
    });
}
/**
 * Envoie l'image PNG (base64) à imprimer. Se résout quand le reçu — ou la
 * dernière étiquette — est sorti, se rejette avec une `ErreurImpression` sinon.
 */
export function envoyerAuServeur(pngBase64, options = {}) {
    return poster('/imprimer', {
        image: pngBase64,
        avance: options.avance === undefined ? 3 : options.avance,
        support: options.support || 'recu',
        copies: options.copies || 1,
        tiroir: !!options.tiroir,
    }, options.port, options.delai || 65000);
}
/** Ouvre le tiroir-caisse branché sur le terminal. */
export function ouvrirTiroirAuServeur(options = {}) {
    return poster('/tiroir', {}, options.port, options.delai || 15000);
}
/** Affiche une image PNG (base64) sur l'écran client — ou l'efface si elle vaut `null`. */
export function afficherAuServeur(pngBase64, options = {}) {
    return poster(pngBase64 === null ? '/effacer' : '/afficher', pngBase64 === null ? {} : { image: pngBase64 }, options.port, options.delai || 15000);
}
function poster(chemin, corps, port, delai) {
    return __awaiter(this, void 0, void 0, function* () {
        if (port === false)
            throw new ErreurImpression('absente', 'Service d’impression désactivé.');
        let reponse;
        try {
            reponse = yield avecDelai(fetch(adresse(port || PORT_PAR_DEFAUT, chemin), {
                method: 'POST',
                // text/plain : une requête « simple », sans pré-vérification CORS
                // — un aller-retour de moins. Le service lit le JSON quand même.
                headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
                body: JSON.stringify(corps),
            }), delai);
        }
        catch (e) {
            if (e instanceof ErreurImpression)
                throw e;
            throw new ErreurImpression('absente', "Le service d'impression ne répond plus : l'application Tikéo est-elle ouverte ?");
        }
        // Une application plus ancienne ne connaît pas cette route : écran client (protocole 2), tiroir (3).
        if (reponse.status === 404) {
            throw new ErreurImpression('non-pris-en-charge', "L'ancienne application EcoPrint ne sait pas faire cela : la remplacer par Tikéo.");
        }
        let reponseJson;
        try {
            reponseJson = yield reponse.json();
        }
        catch (_a) {
            throw new ErreurImpression('erreur', `Réponse illisible du service d'impression (HTTP ${reponse.status}).`);
        }
        return lireVerdict(reponseJson);
    });
}
/**
 * Une promesse bornée dans le temps. Pas d'AbortController : il n'existe qu'à
 * partir de Chrome 66, et le WebView des terminaux est plus ancien.
 */
function avecDelai(promesse, ms) {
    return new Promise((resoudre, rejeter) => {
        const minuteur = setTimeout(() => rejeter(new ErreurImpression('delai', `Le service d'impression n'a pas répondu en ${Math.round(ms / 1000)} s.`)), ms);
        promesse.then((valeur) => {
            clearTimeout(minuteur);
            resoudre(valeur);
        }, (erreur) => {
            clearTimeout(minuteur);
            rejeter(erreur);
        });
    });
}

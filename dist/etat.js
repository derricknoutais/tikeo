import { LARGEUR_58MM } from "./metriques.js";
/**
 * Une erreur du terminal. Son `code` : ceux de l'état (`papier`, `capot`…),
 * plus `delai`, `image`, et `non-pris-en-charge` — étiquettes, tiroir-caisse
 * ou écran client que ce terminal, ou cette version de l'application, ne sait
 * pas faire.
 */
export class ErreurImpression extends Error {
    constructor(code, message) {
        super(message);
        this.name = 'ErreurImpression';
        this.code = code;
    }
}
export const MESSAGE_ABSENTE = "Imprimante injoignable : l'application Tikéo n'est pas ouverte sur ce terminal (ou ce n'est pas un terminal).";
export function etatAbsente() {
    return { code: 'absente', message: MESSAGE_ABSENTE, largeur: LARGEUR_58MM, transport: null };
}
/** L'état tel que l'application le décrit en JSON, complété et typé. */
export function lireEtat(brut, transport) {
    const e = (brut || {});
    return Object.assign(Object.assign(Object.assign(Object.assign(Object.assign({ code: e.code || 'erreur', message: e.message || '', largeur: e.largeur || LARGEUR_58MM }, (e.modele ? { modele: e.modele } : {})), (e.pilote ? { pilote: e.pilote } : {})), (e.terminal ? { terminal: e.terminal } : {})), (e.capacites ? { capacites: lireCapacites(e.capacites) } : {})), { transport });
}
function lireCapacites(brut) {
    const c = (brut || {});
    const a = c.afficheur;
    return {
        massicot: c.massicot === true,
        etiquettes: troisEtats(c.etiquettes),
        tiroir: troisEtats(c.tiroir),
        afficheur: a && Number(a.largeur) > 0 && Number(a.hauteur) > 0
            ? Object.assign({ largeur: Number(a.largeur), hauteur: Number(a.hauteur) }, (a.monochrome === true ? { monochrome: true } : {})) : null,
    };
}
/** `true`, `false`, ou `null` quand le pilote ne sait pas. */
function troisEtats(valeur) {
    return valeur === true ? true : valeur === false ? false : null;
}
function lireTiroir(t) {
    return t.ok ? { ouvert: true } : { ouvert: false, code: t.code || 'erreur', message: t.message || "Le tiroir-caisse ne s'est pas ouvert." };
}
/**
 * Le verdict d'impression de l'application : un résultat, ou une
 * `ErreurImpression`. Celui du tiroir-caisse, s'il était demandé, suit dans
 * l'un comme dans l'autre.
 */
export function lireVerdict(brut) {
    const r = (brut || {});
    const tiroir = r.tiroir && typeof r.tiroir === 'object' ? lireTiroir(r.tiroir) : undefined;
    if (!r.ok) {
        const erreur = new ErreurImpression(r.code || 'erreur', r.message || "L'impression a échoué.");
        if (tiroir)
            erreur.tiroir = tiroir;
        throw erreur;
    }
    const resultat = { simulation: !!r.simulation };
    if (tiroir)
        resultat.tiroir = tiroir;
    return resultat;
}

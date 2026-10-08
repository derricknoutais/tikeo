import { ErreurImpression, etatAbsente, lireEtat, lireVerdict, type EtatImprimante, type OptionsEnvoi, type ResultatImpression } from './etat.ts';

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
export const VERSION_PONT = '4';

interface PontNatif {
    version(): string;
    etat(): string;
    imprimer(id: string, pngBase64: string, options: string): void;
    /** Protocole 2 et plus. */
    afficher?(id: string, pngBase64: string): void;
    /** Protocole 2 et plus. */
    effacer?(id: string): void;
    /** Protocole 3 et plus. */
    ouvrirTiroir?(id: string): void;
}

interface Attente {
    resoudre: (resultat: ResultatImpression) => void;
    rejeter: (erreur: Error) => void;
    minuteur: ReturnType<typeof setTimeout>;
}

/**
 * Les attentes vivent sur `window`, pas dans ce module : si deux copies du
 * paquet cohabitent dans une page (deux bundles), une réponse doit retrouver
 * la promesse de l'une comme de l'autre.
 */
interface Retours {
    attentes: Map<string, Attente>;
    retour(id: string, resultat: string): void;
    /** Le protocole que lit `retour` ; absent avant le 3. */
    version?: number;
}

type FenetreAvecPont = Window & { Tikeo?: PontNatif; __tikeo?: Retours };

function fenetre(): FenetreAvecPont | null {
    return typeof window === 'undefined' ? null : (window as FenetreAvecPont);
}

function pont(): PontNatif | null {
    const f = fenetre();
    return f && f.Tikeo ? f.Tikeo : null;
}

/** Vrai si la page est ouverte dans l'application Tikéo. */
export function pontDisponible(): boolean {
    return pont() !== null;
}

/** Version du protocole annoncée par l'application, ou `null` hors application. */
export function versionPont(): string | null {
    const p = pont();
    return p ? String(p.version()) : null;
}

/** L'état de l'imprimante par le pont — synchrone, c'est un appel direct. */
export function etatPont(): EtatImprimante {
    const p = pont();
    if (!p) return etatAbsente();
    try {
        return lireEtat(JSON.parse(p.etat()), 'pont');
    } catch (e) {
        return lireEtat({ code: 'erreur', message: `Réponse illisible de l'application : ${String(e)}` }, 'pont');
    }
}

/**
 * Envoie une image PNG (base64, sans le préfixe `data:`) à imprimer. Se
 * résout quand le reçu — ou la dernière étiquette — est SORTI.
 */
export function envoyerParPont(pngBase64: string, options: OptionsEnvoi = {}): Promise<ResultatImpression> {
    const details = JSON.stringify({
        avance: options.avance === undefined ? 3 : options.avance,
        support: options.support || 'recu',
        copies: options.copies || 1,
        tiroir: !!options.tiroir,
    });
    return demander((p, id) => p.imprimer(id, pngBase64, details), options.delai || 60000, "L'imprimante n'a pas répondu");
}

/** Ouvre le tiroir-caisse branché sur le terminal. */
export function ouvrirTiroirParPont(options: { delai?: number } = {}): Promise<ResultatImpression> {
    const p = pont();
    if (p && typeof p.ouvrirTiroir !== 'function') {
        return Promise.reject(new ErreurImpression('non-pris-en-charge', "Cette version de Tikéo ne pilote pas le tiroir-caisse : la mettre à jour."));
    }
    return demander((natif, id) => natif.ouvrirTiroir!(id), options.delai || 15000, "Le tiroir-caisse n'a pas répondu");
}

/** Affiche une image PNG (base64) sur l'écran client — ou l'efface si elle vaut `null`. */
export function afficherParPont(pngBase64: string | null, options: { delai?: number } = {}): Promise<ResultatImpression> {
    const p = pont();
    if (p && (typeof p.afficher !== 'function' || typeof p.effacer !== 'function')) {
        return Promise.reject(new ErreurImpression('non-pris-en-charge', "Cette version de Tikéo ne pilote pas l'écran client : la mettre à jour."));
    }
    return demander(
        (natif, id) => (pngBase64 === null ? natif.effacer!(id) : natif.afficher!(id, pngBase64)),
        options.delai || 15000,
        "L'écran client n'a pas répondu",
    );
}

let compteur = 0;

/** Un appel au pont, et la promesse de sa réponse, bornée dans le temps. */
function demander(appel: (p: PontNatif, id: string) => void, delai: number, sansReponse: string): Promise<ResultatImpression> {
    const f = fenetre();
    const p = pont();
    if (!f || !p) return Promise.reject(new ErreurImpression('absente', etatAbsente().message));

    const retours = installerRetours(f);
    const id = `i${Date.now().toString(36)}-${++compteur}`;

    return new Promise<ResultatImpression>((resoudre, rejeter) => {
        const minuteur = setTimeout(() => {
            retours.attentes.delete(id);
            rejeter(new ErreurImpression('delai', `${sansReponse} en ${Math.round(delai / 1000)} s.`));
        }, delai);
        retours.attentes.set(id, { resoudre, rejeter, minuteur });

        try {
            appel(p, id);
        } catch (e) {
            clearTimeout(minuteur);
            retours.attentes.delete(id);
            rejeter(new ErreurImpression('erreur', `L'application a refusé la demande : ${e instanceof Error ? e.message : String(e)}`));
        }
    });
}

/**
 * Le `retour` le plus récent l'emporte : celui d'une copie plus ancienne du
 * paquet lirait les verdicts sans ce que les protocoles suivants y ajoutent
 * (le tiroir). Les attentes déjà en cours sont gardées : le nouveau `retour`
 * les lit dans la même table, et son verdict en dit plus, jamais moins.
 */
function installerRetours(f: FenetreAvecPont): Retours {
    const existants = f.__tikeo;
    if (existants && (existants.version || 0) >= Number(VERSION_PONT)) return existants;

    const attentes = existants ? existants.attentes : new Map<string, Attente>();
    const retours: Retours = {
        attentes,
        version: Number(VERSION_PONT),
        retour(id, resultat) {
            const attente = attentes.get(id);
            // Réponse arrivée après le délai : la promesse est déjà rejetée.
            if (!attente) return;
            attentes.delete(id);
            clearTimeout(attente.minuteur);

            try {
                attente.resoudre(lireVerdict(JSON.parse(resultat)));
            } catch (e) {
                attente.rejeter(e instanceof ErreurImpression ? e : new ErreurImpression('erreur', "Réponse illisible de l'application."));
            }
        },
    };
    f.__tikeo = retours;
    return retours;
}

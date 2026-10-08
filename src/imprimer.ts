import type { Recu } from './document.ts';
import { dessinerRecu, type Environnement, type OptionsDessin, type Toile } from './dessin.ts';
import { ErreurImpression, etatAbsente, type EtatImprimante, type OptionsEnvoi, type ResultatImpression } from './etat.ts';
import { LARGEUR_58MM } from './metriques.ts';
import { envoyerParPont, etatPont, ouvrirTiroirParPont, pontDisponible } from './pont.ts';
import { envoyerAuServeur, etatServeur, ouvrirTiroirAuServeur, type OptionsServeur } from './serveur.ts';

export interface OptionsImpression extends OptionsDessin, OptionsServeur, OptionsEnvoi {
    /** Où dessiner, hors navigateur : les tests passent celui de Node. */
    environnement?: Environnement;
}

/**
 * L'état de l'imprimante, par le chemin disponible : le pont si la page est
 * ouverte dans l'application, sinon le service local, sinon « absente ».
 */
export async function etatImprimante(options: OptionsServeur = {}): Promise<EtatImprimante> {
    if (pontDisponible()) return etatPont();
    return (await etatServeur(options)) || etatAbsente();
}

/**
 * Dessine le reçu et l'imprime. Se résout quand il est sorti ; se rejette avec
 * une `ErreurImpression` dont le `code` dit quoi faire : `papier`, `capot`,
 * `surchauffe`, `refusee` (adresse à autoriser dans l'application), `absente`…
 *
 * Avec `tiroir: true`, le tiroir-caisse s'ouvre d'abord, puis le reçu sort ;
 * le résultat dit dans `tiroir` s'il s'est ouvert. Un reçu refusé d'avance
 * (plus de papier constaté avant l'envoi…) n'ouvre rien. Un reçu qui échoue
 * chez le terminal (papier épuisé en cours de route…) a pu l'ouvrir : l'erreur
 * le dit dans `tiroir`. N'appeler `ouvrirTiroir()` que s'il n'est pas ouvert.
 */
export async function imprimerRecu(recu: Recu, options: OptionsImpression = {}): Promise<ResultatImpression> {
    // Vérifié AVANT de dessiner : inutile de faire attendre le caissier pour
    // lui dire ensuite qu'il n'y a plus de papier.
    const etat = await etatImprimante(options);
    if (!etat.transport || (etat.code !== 'prete' && etat.code !== 'simulation')) {
        throw new ErreurImpression(etat.code, etat.message);
    }
    if (options.support === 'etiquette' && etat.capacites && etat.capacites.etiquettes === false) {
        throw new ErreurImpression('non-pris-en-charge', "L'imprimante de ce terminal n'imprime pas d'étiquettes.");
    }

    const toile = await dessinerRecu(recu, { ...options, largeur: options.largeur || etat.largeur }, options.environnement);
    const png = toile.toDataURL('image/png');
    const donnees = png.slice(png.indexOf(',') + 1);

    const envoi: OptionsEnvoi = { avance: options.avance, support: options.support, copies: options.copies, tiroir: options.tiroir, delai: options.delai };
    const resultat = await (etat.transport === 'pont' ? envoyerParPont(donnees, envoi) : envoyerAuServeur(donnees, { ...envoi, port: options.port }));

    // L'ancienne application EcoPrint (avant la 0.2, protocole 3) imprime sans rien dire du tiroir.
    if (options.tiroir && !resultat.tiroir) {
        resultat.tiroir = { ouvert: false, code: 'non-pris-en-charge', message: "L'ancienne application EcoPrint ne pilote pas le tiroir-caisse : la remplacer par Tikéo." };
    }
    return resultat;
}

/**
 * Ouvre le tiroir-caisse branché sur le terminal — sans reçu : vente sans
 * ticket, rendu de monnaie… Se rejette avec une `ErreurImpression` :
 * `non-pris-en-charge` si Tikéo sait que le terminal n'a pas de prise
 * (`capacites.tiroir === false` : Sunmi portable) ou si l'application est trop
 * ancienne, `absente` hors terminal, `refusee` si l'adresse n'est pas
 * autorisée. Quand `capacites.tiroir` vaut `null` (ZCS), le pilote ne peut pas
 * savoir : un terminal sans prise rend `erreur`, ou réussit sans rien ouvrir.
 *
 * Ouvrir la caisse sans vente est un geste à tracer : c'est à l'application
 * de dire qui y a droit, et de l'enregistrer.
 */
export async function ouvrirTiroir(options: OptionsServeur & { delai?: number } = {}): Promise<void> {
    const etat = await etatImprimante(options);
    if (!etat.transport || etat.code === 'refusee') throw new ErreurImpression(etat.code, etat.message);
    if (etat.capacites && etat.capacites.tiroir === false) {
        throw new ErreurImpression('non-pris-en-charge', "Ce terminal n'a pas de prise de tiroir-caisse.");
    }
    // Les transports rendent le verdict déjà lu : ils rejettent si le tiroir refuse.
    if (etat.transport === 'pont') await ouvrirTiroirParPont({ delai: options.delai });
    else await ouvrirTiroirAuServeur({ port: options.port, delai: options.delai });
}

/**
 * Le reçu tel qu'il sortira, en canvas noir et blanc. Sans recherche de
 * l'imprimante : à la largeur du pont s'il y en a un, en 58 mm sinon — un
 * aperçu ne doit pas attendre le réseau.
 */
export function apercuRecu(recu: Recu, options: OptionsDessin = {}, env?: Environnement): Promise<Toile> {
    const largeur = options.largeur || (pontDisponible() ? etatPont().largeur : LARGEUR_58MM);
    return dessinerRecu(recu, { ...options, largeur }, env);
}

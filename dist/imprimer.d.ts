import type { Recu } from './document.ts';
import { type Environnement, type OptionsDessin, type Toile } from './dessin.ts';
import { type EtatImprimante, type OptionsEnvoi, type ResultatImpression } from './etat.ts';
import { type OptionsServeur } from './serveur.ts';
export interface OptionsImpression extends OptionsDessin, OptionsServeur, OptionsEnvoi {
    /** Où dessiner, hors navigateur : les tests passent celui de Node. */
    environnement?: Environnement;
}
/**
 * L'état de l'imprimante, par le chemin disponible : le pont si la page est
 * ouverte dans l'application, sinon le service local, sinon « absente ».
 */
export declare function etatImprimante(options?: OptionsServeur): Promise<EtatImprimante>;
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
export declare function imprimerRecu(recu: Recu, options?: OptionsImpression): Promise<ResultatImpression>;
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
export declare function ouvrirTiroir(options?: OptionsServeur & {
    delai?: number;
}): Promise<void>;
/**
 * Le reçu tel qu'il sortira, en canvas noir et blanc. Sans recherche de
 * l'imprimante : à la largeur du pont s'il y en a un, en 58 mm sinon — un
 * aperçu ne doit pas attendre le réseau.
 */
export declare function apercuRecu(recu: Recu, options?: OptionsDessin, env?: Environnement): Promise<Toile>;

import type { Recu } from './document.ts';
import { dessinerRecu, environnementNavigateur, type Environnement, type Toile } from './dessin.ts';
import { ErreurImpression, type FormatEcran } from './etat.ts';
import { seuillerRgba } from './tramage.ts';
import { etatImprimante } from './imprimer.ts';
import { afficherParPont } from './pont.ts';
import { afficherAuServeur, type OptionsServeur } from './serveur.ts';

/**
 * L'écran client : le petit écran tourné vers le client, sur les terminaux
 * qui en ont un — un écran couleur (480 × 480 sur les ZCS qui en ont un), ou
 * un petit LCD noir et blanc (128 × 64 sur le Z100).
 *
 * On y montre ce qu'on veut — le panier, le total à payer, un QR de
 * paiement, le logo de la boutique — avec les MÊMES blocs qu'un reçu. Le
 * paquet le dessine à la taille de l'écran, en niveaux de gris sur un écran
 * couleur, et l'application l'y affiche. Sur un LCD, deux lignes courtes
 * (« À PAYER », le montant) : voir `ecranLcdExemple()`.
 */

export interface OptionsEcran extends OptionsServeur {
    /** Attente maximale de l'écran, en ms ; 15 000 par défaut. */
    delai?: number;
    /** Où dessiner, hors navigateur : les tests passent celui de Node. */
    environnement?: Environnement;
}

/** Blanc gardé autour du contenu, en pixels de l'écran. */
const MARGE = 16;
const MARGE_LCD = 2;

/**
 * Un LCD de 128 points de large ne loge pas « 14 500 FCFA » dans la plus
 * petite taille de texte : le contenu y est dessiné deux fois plus large,
 * réduit en lissant, puis remis en noir et blanc — le texte reste net, deux
 * fois plus petit.
 */
const SURECHANTILLONNAGE_LCD = 2;

/**
 * Le contenu à la taille exacte de l'écran : dessiné à sa largeur, réduit
 * s'il est trop haut pour tenir — un client doit tout voir d'un coup —, et
 * centré. Sur un LCD (`monochrome`), en noir et blanc pur.
 */
export async function dessinerEcran(contenu: Recu, format: FormatEcran, env: Environnement = environnementNavigateur()): Promise<Toile> {
    const facteur = format.monochrome ? SURECHANTILLONNAGE_LCD : 1;
    const marge = format.monochrome ? MARGE_LCD : MARGE;
    const page = await dessinerRecu(contenu, { largeur: format.largeur * facteur, marge: marge * facteur, noirEtBlanc: false }, env);

    const toile = env.creerToile(format.largeur, format.hauteur);
    const ctx = toile.getContext('2d');
    if (!ctx) throw new Error('Canvas 2D indisponible.');
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, format.largeur, format.hauteur);

    const hauteurUtile = format.hauteur - 2 * marge;
    const echelle = Math.min(1 / facteur, hauteurUtile / page.height);
    const largeur = Math.round(page.width * echelle);
    const hauteur = Math.round(page.height * echelle);
    ctx.imageSmoothingEnabled = true;
    ctx.drawImage(page as unknown as CanvasImageSource, Math.round((format.largeur - largeur) / 2), Math.round((format.hauteur - hauteur) / 2), largeur, hauteur);

    if (format.monochrome) {
        const pixels = ctx.getImageData(0, 0, format.largeur, format.hauteur);
        seuillerRgba(pixels.data, 128);
        ctx.putImageData(pixels, 0, 0);
    }
    return toile;
}

/**
 * Affiche le contenu sur l'écran client. Se rejette avec une
 * `ErreurImpression` : `non-pris-en-charge` si le terminal n'a pas d'écran
 * client, `absente` hors terminal, `refusee` si l'adresse n'est pas autorisée.
 */
export async function afficherClient(contenu: Recu, options: OptionsEcran = {}): Promise<void> {
    const etat = await etatImprimante(options);
    if (!etat.transport || etat.code === 'refusee') throw new ErreurImpression(etat.code, etat.message);

    const format = etat.capacites ? etat.capacites.afficheur : null;
    if (!format) {
        throw new ErreurImpression(
            'non-pris-en-charge',
            etat.capacites ? "Ce terminal n'a pas d'écran client." : "L'ancienne application EcoPrint ne pilote pas l'écran client : la remplacer par Tikéo.",
        );
    }

    const toile = await dessinerEcran(contenu, format, options.environnement);
    const png = toile.toDataURL('image/png');
    const donnees = png.slice(png.indexOf(',') + 1);

    // Les transports rendent le verdict déjà lu : ils rejettent si l'écran refuse.
    if (etat.transport === 'pont') await afficherParPont(donnees, { delai: options.delai });
    else await afficherAuServeur(donnees, { port: options.port, delai: options.delai });
}

/** Efface l'écran client. */
export async function effacerClient(options: OptionsEcran = {}): Promise<void> {
    const etat = await etatImprimante(options);
    if (!etat.transport || etat.code === 'refusee') throw new ErreurImpression(etat.code, etat.message);
    if (etat.transport === 'pont') await afficherParPont(null, { delai: options.delai });
    else await afficherAuServeur(null, { port: options.port, delai: options.delai });
}

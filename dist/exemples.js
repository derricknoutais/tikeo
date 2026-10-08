import { montant } from "./montant.js";
/**
 * Deux reçus prêts à imprimer : un ticket réaliste, et une mire qui éprouve
 * tout ce qu'une imprimante doit rendre. À imprimer depuis n'importe quel
 * projet pour diagnostiquer un terminal.
 */
/** Un ticket de bar-restaurant : articles, TVA 18 %, règlement, rendu, QR. */
export function recuExemple() {
    const articles = [
        ['Poulet braisé', 1, 6500],
        ['Régab 65 cl', 2, 1500],
        ['Jus de gingembre maison, sans sucre ajouté', 1, 1500],
        ['Brochettes de capitaine, sauce moyo', 2, 4500],
    ];
    const ttc = articles.reduce((total, [, quantite, prix]) => total + quantite * prix, 0);
    const ht = Math.round(ttc / 1.18);
    // Une boucle plutôt que flatMap : Chrome 69, le WebView du V2 Pro est en 62.
    const lignesArticles = [];
    for (const [libelle, quantite, prix] of articles) {
        lignesArticles.push({ type: 'ligne', gauche: libelle, droite: montant(quantite * prix, '') });
        if (quantite > 1)
            lignesArticles.push({ type: 'texte', texte: `  ${quantite} × ${montant(prix, '')}`, taille: 'petite' });
    }
    return {
        blocs: [
            { type: 'texte', texte: 'LE PALMIER', taille: 'titre', gras: true, alignement: 'centre' },
            { type: 'texte', texte: 'Bar · Restaurant — Libreville', taille: 'petite', alignement: 'centre' },
            { type: 'texte', texte: 'Tél. 011 55 22 33 · NIF 123456 A', taille: 'petite', alignement: 'centre' },
            { type: 'separateur' },
            { type: 'ligne', gauche: 'Reçu n° 2026-0042', droite: '28/09/2026 14:32', taille: 'petite' },
            { type: 'ligne', gauche: 'Table 7 — Serveuse : Aïcha', taille: 'petite' },
            { type: 'separateur' },
            ...lignesArticles,
            { type: 'separateur' },
            { type: 'ligne', gauche: 'Total HT', droite: montant(ht) },
            { type: 'ligne', gauche: 'TVA 18 %', droite: montant(ttc - ht) },
            { type: 'ligne', gauche: 'TOTAL TTC', droite: montant(ttc), taille: 'grande', gras: true },
            { type: 'separateur' },
            { type: 'ligne', gauche: 'Espèces', droite: montant(25000) },
            { type: 'ligne', gauche: 'Rendu', droite: montant(25000 - ttc), gras: true },
            { type: 'espace' },
            { type: 'qr', donnees: 'https://exemple.ga/r/2026-0042' },
            { type: 'texte', texte: 'Scannez pour recevoir la facture', taille: 'petite', alignement: 'centre' },
            { type: 'espace' },
            { type: 'texte', texte: 'Merci de votre visite !', gras: true, alignement: 'centre' },
        ],
    };
}
/**
 * La mire : tailles, graisses, accents, montants, passages à la ligne,
 * séparateurs, QR — et une image en demi-teintes si on en fournit une (un
 * dégradé, pour juger la trame).
 */
export function mire(options = {}) {
    const largeur = options.largeur || 384;
    return {
        blocs: [
            { type: 'texte', texte: 'MIRE', taille: 'titre', gras: true, alignement: 'centre' },
            { type: 'texte', texte: `tikéo — ${largeur} points de large`, taille: 'petite', alignement: 'centre' },
            { type: 'separateur', style: 'double' },
            { type: 'texte', texte: 'Petite (20) — Le vif zéphyr jubile sur les kumquats du clown gracieux.', taille: 'petite' },
            { type: 'texte', texte: 'Normale (24) — Portez ce vieux whisky au juge blond qui fume.' },
            { type: 'texte', texte: 'Normale grasse (24)', gras: true },
            { type: 'texte', texte: 'Grande (32)', taille: 'grande' },
            { type: 'texte', texte: 'Titre (40)', taille: 'titre' },
            { type: 'separateur' },
            { type: 'texte', texte: 'àâäçéèêëîïôöùûüÿ œæ' },
            { type: 'texte', texte: 'ÀÂÇÉÈÊËÎÏÔÙÛÜ Œ' },
            { type: 'texte', texte: '€ FCFA ° « » — – … ± × ÷' },
            { type: 'texte', texte: `${montant(1234567)} · ${montant(-2500)} · ${montant(99.5, '€', 2)}` },
            { type: 'separateur' },
            { type: 'ligne', gauche: 'Libellé court', droite: montant(500) },
            { type: 'ligne', gauche: 'Un libellé très long, qui passe à la ligne sans jamais toucher le montant', droite: montant(1250000) },
            { type: 'ligne', gauche: 'Montant trop large', droite: montant(1234567890123) },
            { type: 'texte', texte: 'REFERENCE-SANS-ESPACE-ADK-5904-XYZ-0001-TROP-LONGUE' },
            { type: 'separateur', style: 'tirets' },
            { type: 'separateur', style: 'plein' },
            { type: 'separateur', style: 'double' },
            { type: 'texte', texte: 'À gauche' },
            { type: 'texte', texte: 'Au centre', alignement: 'centre' },
            { type: 'texte', texte: 'À droite', alignement: 'droite' },
            { type: 'espace' },
            { type: 'qr', donnees: 'https://exemple.ga/mire?reçu=é' },
            ...(options.image ? [{ type: 'espace' }, { type: 'image', source: options.image }] : []),
            { type: 'espace' },
            { type: 'texte', texte: 'Fin de la mire', taille: 'petite', alignement: 'centre' },
        ],
    };
}
/**
 * Ce que voit le client pendant qu'on encaisse : le panier et le total, en
 * grand — pour l'écran client (480 × 480), pas pour l'imprimante.
 */
export function ecranExemple() {
    return {
        blocs: [
            { type: 'texte', texte: 'LE PALMIER', taille: 'titre', gras: true, alignement: 'centre' },
            { type: 'separateur', style: 'plein' },
            { type: 'ligne', gauche: 'Poulet braisé', droite: montant(6500, ''), taille: 'grande' },
            { type: 'ligne', gauche: '2 × Régab 65 cl', droite: montant(3000, ''), taille: 'grande' },
            { type: 'ligne', gauche: 'Jus de gingembre', droite: montant(1500, ''), taille: 'grande' },
            { type: 'ligne', gauche: '2 × Brochettes', droite: montant(9000, ''), taille: 'grande' },
            { type: 'separateur', style: 'double' },
            { type: 'texte', texte: 'À PAYER', taille: 'grande', alignement: 'centre' },
            { type: 'texte', texte: montant(20000), taille: 'titre', gras: true, alignement: 'centre' },
            { type: 'espace' },
            { type: 'texte', texte: 'Merci de votre visite !', alignement: 'centre' },
        ],
    };
}
/**
 * Pour un petit LCD noir et blanc (128 × 64) : deux lignes, lisibles de
 * l'autre côté du comptoir. Le reste du panier n'y tiendrait pas.
 */
export function ecranLcdExemple() {
    return {
        blocs: [
            { type: 'texte', texte: 'À PAYER', gras: true, alignement: 'centre' },
            { type: 'texte', texte: montant(20000), taille: 'grande', gras: true, alignement: 'centre' },
        ],
    };
}
/** Une étiquette d'article — pour le papier étiquette : nom, prix en grand, QR de la référence. */
export function etiquetteExemple() {
    return {
        blocs: [
            { type: 'texte', texte: 'Amortisseur avant G/D — LC Prado J12', gras: true, alignement: 'centre' },
            { type: 'texte', texte: montant(45000), taille: 'titre', gras: true, alignement: 'centre' },
            { type: 'qr', donnees: 'BK341340', taille: 120 },
            { type: 'texte', texte: 'BK341340', taille: 'petite', alignement: 'centre' },
        ],
    };
}

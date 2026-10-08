#!/usr/bin/env node
/**
 * Les échantillons de l'onglet « Test » de l'application Android, en PNG :
 * reçu d'exemple, mire et étiquette aux deux largeurs de papier (384 et 576
 * points), écran client couleur (480 × 480) et LCD (128 × 64).
 *
 * Dessinés par le paquet lui-même, avec sa police : l'application montre et
 * imprime exactement ce qu'une page web imprimerait. Versionnés dans
 * android/app/src/main/assets/apercus/, pour que l'APK se construise sans Node.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dessinerRecu } from '../dist/dessin.js';
import { dessinerEcran } from '../dist/ecran.js';
import { ecranExemple, ecranLcdExemple, etiquetteExemple, mire, recuExemple } from '../dist/exemples.js';
import { degrade, environnementNode } from '../test/outils.mjs';

const sortie = fileURLToPath(new URL('../android/app/src/main/assets/apercus', import.meta.url));
mkdirSync(sortie, { recursive: true });
const env = environnementNode();
const ecrire = (nom, toile) => writeFileSync(`${sortie}/${nom}.png`, toile.toBuffer('image/png'));

for (const largeur of [384, 576]) {
    ecrire(`recu-${largeur}`, await dessinerRecu(recuExemple(), { largeur }, env));
    ecrire(`mire-${largeur}`, await dessinerRecu(mire({ image: degrade(), largeur }), { largeur }, env));
    ecrire(`etiquette-${largeur}`, await dessinerRecu(etiquetteExemple(), { largeur }, env));
}
ecrire('ecran-480', await dessinerEcran(ecranExemple(), { largeur: 480, hauteur: 480 }, env));
ecrire('ecran-lcd', await dessinerEcran(ecranLcdExemple(), { largeur: 128, hauteur: 64, monochrome: true }, env));
console.log(`aperçus : ${sortie}`);

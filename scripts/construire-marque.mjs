/**
 * L'identité Tikéo dans l'application Android, tirée d'une seule géométrie :
 * celle de la planche « Tikéo Logo v2 » (Claude Design, octobre 2026).
 *
 *   - les icônes de lanceur, en PNG (Android 7) et adaptatives (Android 8 et plus,
 *     avec la couche monochrome des icônes à thème d'Android 13) ;
 *   - la petite icône de notification, d'une seule couleur ;
 *   - le verrou en SVG, pour le README.
 *
 * Le symbole est le terminal lui-même : corps, fente(s), voyant vert, fente
 * orange, ticket qui sort. La planche en donne trois dessins selon la taille
 * d'affichage — 192, 48 et 16 px : le dentelé du ticket perd des dents à mesure
 * que l'icône rapetisse.
 *
 * À relancer après un changement de la planche : `npm run marque`. Les fichiers
 * produits sont versionnés.
 */
import { createCanvas, Path2D } from '@napi-rs/canvas';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const racine = join(dirname(fileURLToPath(import.meta.url)), '..');
const res = join(racine, 'android/app/src/main/res');

export const COULEURS = {
    corps: '#3B3B3D',
    fente: '#F38120',
    voyant: '#6DBE4A',
    vertTexte: '#2A8139',
    blanc: '#FFFFFF',
};

/** Les trois dessins du symbole, dans le repère de la planche (centre en 0,0 ; boîte de 300). */
const DESSINS = {
    // « Icône d'application — 192 px » de la planche.
    192: {
        corps: { x: -84, y: -70, l: 168, h: 68, r: 15 },
        barre: { x: -58, y: -48, l: 74, h: 14, r: 7 },
        voyant: { cx: 56, cy: -36, r: 12 },
        fente: { x: -96, y: 10, l: 192, h: 46, r: 23 },
        fenteInterieure: { x: -62, y: 26, l: 124, h: 13, r: 6.5 },
        ticket: 'M-58,64 H58 V98 l-14.5,14 l-14.5,-14 l-14.5,14 l-14.5,-14 l-14.5,14 l-14.5,-14 l-14.5,14 l-14.5,-14 Z',
    },
    // « 48 px » : trois dents.
    48: {
        corps: { x: -84, y: -70, l: 168, h: 68, r: 15 },
        barre: { x: -58, y: -46, l: 74, h: 16, r: 8 },
        voyant: { cx: 56, cy: -36, r: 13 },
        fente: { x: -96, y: 10, l: 192, h: 46, r: 23 },
        fenteInterieure: { x: -62, y: 26, l: 124, h: 13, r: 6.5 },
        ticket: 'M-58,64 H58 V100 l-19.33,14 l-19.33,-14 l-19.33,14 l-19.33,-14 l-19.33,14 l-19.33,-14 Z',
    },
    // « 16 px » : le ticket devient un bord droit.
    16: {
        corps: { x: -84, y: -74, l: 168, h: 72, r: 14 },
        barre: { x: -56, y: -50, l: 68, h: 20, r: 10 },
        voyant: { cx: 54, cy: -40, r: 16 },
        fente: { x: -96, y: 12, l: 192, h: 50, r: 25 },
        fenteInterieure: { x: -60, y: 30, l: 120, h: 14, r: 7 },
        ticket: 'M-56,70 H56 V112 H-56 Z',
    },
};

/** Le centre vertical de chaque dessin : le symbole déborde plus vers le bas (ticket) que vers le haut. */
const CENTRE_Y = { 192: 21, 48: 22, 16: 19 };

const nombre = (n) => String(Math.round(n * 1000) / 1000);

/** Un rectangle arrondi, en données de chemin SVG / VectorDrawable. */
function rectangle({ x, y, l, h, r }) {
    return `M${nombre(x + r)},${nombre(y)} H${nombre(x + l - r)} A${nombre(r)},${nombre(r)} 0 0 1 ${nombre(x + l)},${nombre(y + r)} ` +
        `V${nombre(y + h - r)} A${nombre(r)},${nombre(r)} 0 0 1 ${nombre(x + l - r)},${nombre(y + h)} ` +
        `H${nombre(x + r)} A${nombre(r)},${nombre(r)} 0 0 1 ${nombre(x)},${nombre(y + h - r)} ` +
        `V${nombre(y + r)} A${nombre(r)},${nombre(r)} 0 0 1 ${nombre(x + r)},${nombre(y)} Z`;
}

function cercle({ cx, cy, r }) {
    return `M${nombre(cx - r)},${nombre(cy)} A${nombre(r)},${nombre(r)} 0 1 0 ${nombre(cx + r)},${nombre(cy)} A${nombre(r)},${nombre(r)} 0 1 0 ${nombre(cx - r)},${nombre(cy)} Z`;
}

/**
 * Les couches du symbole, dans l'ordre où elles se peignent. `palette` :
 * `icone` (corps blanc sur fond sombre, la variante des icônes d'application),
 * `clair` (corps sombre, sur fond clair : le verrou principal).
 */
function couches(taille, palette) {
    const d = DESSINS[taille];
    const icone = palette === 'icone';
    return [
        { chemin: rectangle(d.corps), couleur: icone ? COULEURS.blanc : COULEURS.corps },
        { chemin: rectangle(d.barre), couleur: icone ? COULEURS.corps : '#6B6762' },
        { chemin: cercle(d.voyant), couleur: COULEURS.voyant },
        { chemin: rectangle(d.fente), couleur: COULEURS.fente },
        { chemin: rectangle(d.fenteInterieure), couleur: icone ? COULEURS.corps : COULEURS.blanc },
        { chemin: d.ticket, couleur: icone ? COULEURS.blanc : '#CFC9C0' },
    ];
}

/** Une seule couleur : corps, fente et ticket pleins ; barre, voyant et fente intérieure évidés. */
function monochrome(taille) {
    const d = DESSINS[taille];
    return [
        rectangle(d.corps) + ' ' + rectangle(d.barre) + ' ' + cercle(d.voyant),
        rectangle(d.fente) + ' ' + rectangle(d.fenteInterieure),
        d.ticket,
    ];
}

// ---------------------------------------------------------------- Icônes PNG

/**
 * L'icône d'application de la planche : carré arrondi sombre (rayon 20/84),
 * symbole sur 66/84. La règle de la planche, sur la largeur visible du
 * symbole (192 unités sur 300) : sous 32 px, le dessin à trois dents.
 */
function iconePng(cote) {
    const largeurSymbole = (((cote * 66) / 84) * 192) / 300;
    const taille = largeurSymbole < 32 ? 48 : 192;
    const toile = createCanvas(cote, cote);
    const ctx = toile.getContext('2d');
    ctx.fillStyle = COULEURS.corps;
    ctx.beginPath();
    ctx.roundRect(0, 0, cote, cote, (cote * 20) / 84);
    ctx.fill();

    const echelle = ((cote * 66) / 84) / 300;
    ctx.translate(cote / 2, cote / 2 - CENTRE_Y[taille] * echelle);
    ctx.scale(echelle, echelle);
    for (const c of couches(taille, 'icone')) {
        ctx.fillStyle = c.couleur;
        ctx.fill(new Path2D(c.chemin));
    }
    return toile.toBuffer('image/png');
}

const DENSITES = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
for (const [densite, cote] of Object.entries(DENSITES)) {
    const fichier = join(res, `mipmap-${densite}`, 'icone.png');
    mkdirSync(dirname(fichier), { recursive: true });
    writeFileSync(fichier, iconePng(cote));
}

// ------------------------------------------------------- Icône adaptative (8+)

/**
 * Le premier plan d'une icône adaptative fait 108 dp, dont 72 dp au centre
 * sont visibles : c'est la tuile de 84 px de la planche, où le symbole occupe
 * 66/84 — les mêmes proportions que les icônes PNG. Les coins du corps,
 * points les plus éloignés (~118 unités), restent alors à 22 dp du centre,
 * bien dans le disque de 33 dp que tout masque de lanceur laisse voir.
 */
const DP_PAR_UNITE = (72 * 66) / 84 / 300;
const VUE = nombre(108 / DP_PAR_UNITE);
const MILIEU = 108 / DP_PAR_UNITE / 2;

function vecteur(chemins, commentaire) {
    return `<?xml version="1.0" encoding="utf-8"?>
<!-- ${commentaire} Produit par scripts/construire-marque.mjs : ne pas modifier à la main. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="${VUE}"
    android:viewportHeight="${VUE}">
    <group
        android:translateX="${nombre(MILIEU)}"
        android:translateY="${nombre(MILIEU - CENTRE_Y[192])}">
${chemins.map((c) => `        <path
            android:fillColor="${c.couleur}"${c.evide ? '\n            android:fillType="evenOdd"' : ''}
            android:pathData="${c.chemin}" />`).join('\n')}
    </group>
</vector>
`;
}

mkdirSync(join(res, 'drawable'), { recursive: true });
writeFileSync(join(res, 'drawable', 'icone_avant.xml'), vecteur(couches(192, 'icone'), "Premier plan de l'icône adaptative Tikéo."));
writeFileSync(
    join(res, 'drawable', 'icone_monochrome.xml'),
    vecteur(monochrome(192).map((chemin) => ({ chemin, couleur: '#FFFFFFFF', evide: true })), "Couche monochrome de l'icône (icônes à thème, Android 13)."),
);
mkdirSync(join(res, 'mipmap-anydpi-v26'), { recursive: true });
writeFileSync(join(res, 'mipmap-anydpi-v26', 'icone.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- L'icône Tikéo sur Android 8 et plus. Produit par scripts/construire-marque.mjs. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/icone_fond" />
    <foreground android:drawable="@drawable/icone_avant" />
    <monochrome android:drawable="@drawable/icone_monochrome" />
</adaptive-icon>
`);
mkdirSync(join(res, 'values'), { recursive: true });
writeFileSync(join(res, 'values', 'couleurs.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- Produit par scripts/construire-marque.mjs. -->
<resources>
    <color name="icone_fond">${COULEURS.corps}</color>
</resources>
`);

// ------------------------------------------------------ Icône de notification

/** 24 dp, blanche sur transparent comme Android l'exige : le dessin « 16 px », d'une seule couleur. */
writeFileSync(join(res, 'drawable', 'notification.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- Petite icône de notification Tikéo : le dessin « 16 px » de la planche, d'une seule couleur.
     Produit par scripts/construire-marque.mjs. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="230"
    android:viewportHeight="230">
    <group
        android:translateX="115"
        android:translateY="${nombre(115 - CENTRE_Y[16])}">
${monochrome(16).map((c) => `        <path
            android:fillColor="#FFFFFFFF"
            android:fillType="evenOdd"
            android:pathData="${c}" />`).join('\n')}
    </group>
</vector>
`);

// ------------------------------------------------------------ Verrou pour le README

const symbole = (taille, palette) => couches(taille, palette).map((c) => `<path fill="${c.couleur}" d="${c.chemin}"/>`).join('');
// Les sous-ensembles de Montserrat de l'application, embarqués : le mot TIKÉO ne dépend pas
// des polices de la machine qui affiche le README. Sur fond blanc, comme sur la planche :
// le verrou clair se perdrait sur le thème sombre de GitHub.
const police = (fichier) => readFileSync(join(racine, 'android/app/src/main/assets/polices', fichier)).toString('base64');
const style = `<style>@font-face{font-family:'Montserrat';font-weight:900;src:url(data:font/ttf;base64,${police('montserrat_black.ttf')}) format('truetype')}` +
    `@font-face{font-family:'Montserrat';font-weight:700;src:url(data:font/ttf;base64,${police('montserrat_bold.ttf')}) format('truetype')}</style>`;
mkdirSync(join(racine, 'docs'), { recursive: true });
writeFileSync(join(racine, 'docs', 'tikeo.svg'), `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 520 150" width="520" height="150" role="img" aria-label="Tikéo, par Ecolight">
${style}
<rect width="520" height="150" rx="14" fill="#FFFFFF"/>
<g transform="translate(75,${nombre(75 - CENTRE_Y[192] * 0.42)}) scale(0.42)">${symbole(192, 'clair')}</g>
<text x="160" y="86" font-family="Montserrat, Arial Black, sans-serif" font-weight="900" font-size="58" letter-spacing="1.7" fill="${COULEURS.corps}">TIK<tspan fill="${COULEURS.vertTexte}">ÉO</tspan></text>
<text x="162" y="114" font-family="Montserrat, Arial, sans-serif" font-weight="700" font-size="15" letter-spacing="4" fill="#57534E">PAR ECOLIGHT</text>
</svg>
`);

console.log('marque : icônes (5 densités + adaptative + monochrome), notification, docs/tikeo.svg');

import { strict as assert } from 'node:assert';
import { beforeEach, test } from 'node:test';
import { dessinerRecu } from '../dist/dessin.js';
import { afficherClient, dessinerEcran, effacerClient } from '../dist/ecran.js';
import { ErreurImpression } from '../dist/etat.js';
import { ecranExemple, etiquetteExemple, recuExemple } from '../dist/exemples.js';
import { imprimerRecu } from '../dist/imprimer.js';
import { afficherParPont, envoyerParPont } from '../dist/pont.js';
import { afficherAuServeur } from '../dist/serveur.js';
import { environnementNode, garderApercu, gris } from './outils.mjs';

const env = environnementNode();

/** Le service local simulé au niveau de `fetch`, comme dans serveur.test.mjs. */
function installerService(repondre) {
    const appels = [];
    globalThis.fetch = async (url, init = {}) => {
        appels.push({ url, init, corps: init.body ? JSON.parse(init.body) : null });
        return repondre(url, init);
    };
    return appels;
}
const reponse = (statut, corps) => ({ status: statut, ok: statut < 400, json: async () => corps });
const ETAT_ZCS = { code: 'prete', message: 'Prête', largeur: 576, pilote: 'zcs', capacites: { massicot: true, etiquettes: null, afficheur: { largeur: 480, hauteur: 480 } } };

beforeEach(() => {
    globalThis.window = {};
    delete globalThis.fetch;
});

test('en mode écran, le dessin garde ses nuances au lieu du noir et blanc', async () => {
    const recu = { blocs: [{ type: 'texte', texte: 'Total à payer : 20 000 FCFA' }] };
    const thermique = gris(await dessinerRecu(recu, {}, env));
    const ecran = gris(await dessinerRecu(recu, { noirEtBlanc: false }, env));
    assert.equal(thermique.filter((v) => v !== 0 && v !== 255).length, 0);
    assert.ok(ecran.filter((v) => v !== 0 && v !== 255).length > 100, 'les bords lissés des lettres disparaissent');
});

test('l’écran client reçoit une image à sa taille exacte, contenu centré', async () => {
    const toile = await dessinerEcran(ecranExemple(), { largeur: 480, hauteur: 480 }, env);
    garderApercu('ecran-client', toile);
    assert.equal(toile.width, 480);
    assert.equal(toile.height, 480);

    // Le contenu est centré : autant de blanc au-dessus qu'en dessous, à la marge près.
    const g = gris(toile);
    const lignesEncrees = [];
    for (let y = 0; y < 480; y++) if (g.subarray(y * 480, (y + 1) * 480).some((v) => v < 200)) lignesEncrees.push(y);
    const haut = lignesEncrees[0];
    const bas = 479 - lignesEncrees[lignesEncrees.length - 1];
    assert.ok(Math.abs(haut - bas) <= 40, `blanc en haut ${haut}, en bas ${bas}`);
});

test('un contenu trop haut est réduit pour tenir en entier, jamais coupé', async () => {
    const toile = await dessinerEcran(recuExemple(), { largeur: 480, hauteur: 480 }, env);
    const g = gris(toile);
    // Les bords haut et bas restent blancs : rien n'a débordé de l'écran.
    assert.ok(g.subarray(0, 480 * 8).every((v) => v > 240), 'le haut est rogné');
    assert.ok(g.subarray(480 * 472).every((v) => v > 240), 'le bas est rogné');
});

test('afficherClient envoie au service une image de 480 × 480', async () => {
    const appels = installerService((url) => (url.endsWith('/etat') ? reponse(200, ETAT_ZCS) : reponse(200, { ok: true })));
    await afficherClient(ecranExemple(), { environnement: env });

    const envoi = appels.find((a) => a.url.endsWith('/afficher'));
    const png = Buffer.from(envoi.corps.image, 'base64');
    assert.equal(png.readUInt32BE(16), 480);
    assert.equal(png.readUInt32BE(20), 480);
});

test('sans écran client, afficherClient le dit — et n’envoie rien', async () => {
    const sansEcran = { ...ETAT_ZCS, capacites: { ...ETAT_ZCS.capacites, afficheur: null } };
    const appels = installerService(() => reponse(200, sansEcran));
    await assert.rejects(afficherClient(ecranExemple(), { environnement: env }), (e) => e instanceof ErreurImpression && e.code === 'non-pris-en-charge');
    assert.equal(appels.filter((a) => a.url.endsWith('/afficher')).length, 0);
});

test('une application d’avant le protocole 2 est signalée, pas prise pour une panne', async () => {
    // Pas de capacités dans l'état : l'application ne connaît pas l'écran client.
    installerService(() => reponse(200, { code: 'prete', largeur: 384 }));
    await assert.rejects(afficherClient(ecranExemple(), { environnement: env }), (e) => e.code === 'non-pris-en-charge' && /remplacer par Tikéo/.test(e.message));

    installerService(() => reponse(404, { ok: false, code: 'introuvable' }));
    await assert.rejects(afficherAuServeur('AAAA'), (e) => e.code === 'non-pris-en-charge');
});

test('effacer l’écran passe par /effacer, sans image', async () => {
    const appels = installerService((url) => (url.endsWith('/etat') ? reponse(200, ETAT_ZCS) : reponse(200, { ok: true })));
    await effacerClient();
    const envoi = appels.find((a) => a.url.endsWith('/effacer'));
    assert.deepEqual(envoi.corps, {});
});

test('par le pont, afficher et effacer appellent l’application — ou disent qu’elle est trop ancienne', async () => {
    const appels = [];
    globalThis.window.Tikeo = {
        version: () => '2',
        etat: () => JSON.stringify(ETAT_ZCS),
        imprimer() {},
        afficher: (id, png) => {
            appels.push(['afficher', png]);
            globalThis.window.__tikeo.retour(id, '{"ok":true}');
        },
        effacer: (id) => {
            appels.push(['effacer']);
            globalThis.window.__tikeo.retour(id, '{"ok":true}');
        },
    };
    await afficherParPont('iVBORw0KGgo=');
    await afficherParPont(null);
    assert.deepEqual(appels, [['afficher', 'iVBORw0KGgo='], ['effacer']]);

    globalThis.window.Tikeo = { version: () => '1', etat: () => '{"code":"prete"}', imprimer() {} };
    await assert.rejects(afficherParPont('AAAA'), (e) => e.code === 'non-pris-en-charge');
});

test('une étiquette part avec son support et ses exemplaires', async () => {
    let options;
    globalThis.window.Tikeo = {
        version: () => '2',
        etat: () => JSON.stringify(ETAT_ZCS),
        imprimer: (id, png, o) => {
            options = JSON.parse(o);
            globalThis.window.__tikeo.retour(id, '{"ok":true}');
        },
    };
    await envoyerParPont('AAAA', { support: 'etiquette', copies: 3 });
    assert.deepEqual(options, { avance: 3, support: 'etiquette', copies: 3, tiroir: false });
});

test('une étiquette est refusée d’avance là où le pilote sait qu’il n’y en a pas', async () => {
    const sunmi = { code: 'prete', largeur: 384, pilote: 'sunmi', capacites: { massicot: false, etiquettes: false, afficheur: null } };
    installerService(() => reponse(200, sunmi));
    await assert.rejects(imprimerRecu(etiquetteExemple(), { support: 'etiquette', environnement: env }), (e) => e.code === 'non-pris-en-charge');
});

test('useEcranClient : seul le dernier panier part, et un terminal sans écran n’est pas une erreur', async () => {
    const { useEcranClient } = await import('../dist/vue.js');

    const appels = installerService((url) => (url.endsWith('/etat') ? reponse(200, ETAT_ZCS) : reponse(200, { ok: true })));
    const ecran = useEcranClient({ environnement: env });
    const panier = (n) => ({ blocs: [{ type: 'texte', texte: `${n} article(s)` }] });
    // Trois changements d'un coup : seul le dernier part.
    assert.deepEqual(await Promise.all([ecran.afficher(panier(1)), ecran.afficher(panier(2)), ecran.afficher(panier(3))]), [false, false, true]);
    assert.equal(appels.filter((a) => a.url.endsWith('/afficher')).length, 1);

    // Un affichage déjà parti va au bout ; celui dépassé pendant qu'il attendait son tour n'est jamais envoyé.
    const premier = ecran.afficher(panier(4));
    await new Promise((r) => setTimeout(r, 0));
    assert.deepEqual(await Promise.all([premier, ecran.afficher(panier(5)), ecran.afficher(panier(6))]), [true, false, true]);
    assert.equal(appels.filter((a) => a.url.endsWith('/afficher')).length, 3);
    assert.equal(ecran.disponible.value, true);

    const sansEcran = { ...ETAT_ZCS, capacites: { ...ETAT_ZCS.capacites, afficheur: null } };
    const appels2 = installerService(() => reponse(200, sansEcran));
    const autre = useEcranClient({ environnement: env });
    assert.equal(await autre.afficher(panier(1)), false);
    assert.equal(autre.disponible.value, false);
    assert.equal(autre.erreur.value, null);
    // Ensuite, plus aucune requête : sur un Chrome de bureau, chacune pourrait demander une autorisation.
    const avant = appels2.length;
    assert.equal(await autre.afficher(panier(2)), false);
    assert.equal(appels2.length, avant);
});

test('les capacités disent si l’écran client est un LCD noir et blanc', async () => {
    const { lireEtat } = await import('../dist/etat.js');
    const afficheur = (a) => lireEtat({ code: 'prete', capacites: { massicot: true, afficheur: a } }, 'serveur').capacites.afficheur;
    assert.deepEqual(afficheur({ largeur: 128, hauteur: 64, monochrome: true }), { largeur: 128, hauteur: 64, monochrome: true });
    assert.deepEqual(afficheur({ largeur: 480, hauteur: 480, monochrome: false }), { largeur: 480, hauteur: 480 });
    // Une application d'avant le LCD ne dit rien : un écran couleur.
    assert.deepEqual(afficheur({ largeur: 480, hauteur: 480 }), { largeur: 480, hauteur: 480 });
});

test('sur un LCD de 128 × 64 : noir et blanc pur, et le montant tient sur une ligne', async () => {
    const { ecranLcdExemple } = await import('../dist/exemples.js');
    const toile = await dessinerEcran(ecranLcdExemple(), { largeur: 128, hauteur: 64, monochrome: true }, env);
    garderApercu('ecran-lcd', toile);
    assert.equal(toile.width, 128);
    assert.equal(toile.height, 64);

    const g = gris(toile);
    assert.equal(g.filter((v) => v !== 0 && v !== 255).length, 0, 'des gris restent : le LCD les rendrait mal');

    // Deux lignes de texte, pas trois : « 20 000 FCFA » n'a pas été coupé en deux.
    // Un blanc de moins de 3 points (l'accent de « À ») ne sépare pas deux lignes.
    const encrees = [];
    for (let y = 0; y < 64; y++) encrees.push(g.subarray(y * 128, (y + 1) * 128).some((v) => v === 0));
    let lignes = 0;
    let blanc = Infinity;
    for (let y = 0; y < 64; y++) {
        if (!encrees[y]) blanc++;
        else {
            if (blanc >= 3) lignes++;
            blanc = 0;
        }
    }
    assert.equal(lignes, 2);
});

test('afficherClient dessine pour le LCD quand le terminal en a un', async () => {
    const lcd = { ...ETAT_ZCS, capacites: { ...ETAT_ZCS.capacites, afficheur: { largeur: 128, hauteur: 64, monochrome: true } } };
    const appels = installerService((url) => (url.endsWith('/etat') ? reponse(200, lcd) : reponse(200, { ok: true })));
    await afficherClient(ecranExemple(), { environnement: env });
    const png = Buffer.from(appels.find((a) => a.url.endsWith('/afficher')).corps.image, 'base64');
    assert.equal(png.readUInt32BE(16), 128);
    assert.equal(png.readUInt32BE(20), 64);
});

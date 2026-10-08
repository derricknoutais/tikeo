import { strict as assert } from 'node:assert';
import { beforeEach, test } from 'node:test';
import { tiroirEscPos, rasterEscPos } from '../dist/escpos.js';
import { ErreurImpression, lireEtat, lireVerdict } from '../dist/etat.js';
import { recuExemple } from '../dist/exemples.js';
import { imprimerRecu, ouvrirTiroir } from '../dist/imprimer.js';
import { VERSION_PONT } from '../dist/pont.js';
import { environnementNode } from './outils.mjs';

const env = environnementNode();

/** Le service local simulé au niveau de `fetch`, comme dans ecran.test.mjs. */
function installerService(repondre) {
    const appels = [];
    globalThis.fetch = async (url, init = {}) => {
        appels.push({ url, corps: init.body ? JSON.parse(init.body) : null });
        return repondre(url, init);
    };
    return appels;
}
const reponse = (statut, corps) => ({ status: statut, ok: statut < 400, json: async () => corps });
const etatAvecTiroir = (tiroir) => ({ code: 'prete', message: 'Prête', largeur: 576, pilote: 'zcs', capacites: { massicot: true, etiquettes: null, tiroir, afficheur: null } });

beforeEach(() => {
    globalThis.window = {};
    delete globalThis.fetch;
});

test('les capacités disent le tiroir : oui, non, ou inconnu', () => {
    const capacites = (tiroir) => lireEtat({ code: 'prete', capacites: { massicot: false, tiroir } }, 'serveur').capacites.tiroir;
    assert.equal(capacites(true), true);
    assert.equal(capacites(false), false);
    assert.equal(capacites(null), null);
    // Une application du protocole 2 ne le dit pas : inconnu, l'essai tranchera.
    assert.equal(capacites(undefined), null);
});

test('le verdict d’un reçu dit si le tiroir s’est ouvert — sans faire échouer le reçu', () => {
    assert.deepEqual(lireVerdict({ ok: true }), { simulation: false });
    assert.deepEqual(lireVerdict({ ok: true, tiroir: { ok: true } }), { simulation: false, tiroir: { ouvert: true } });
    assert.deepEqual(lireVerdict({ ok: true, tiroir: { ok: false, code: 'non-pris-en-charge', message: 'Pas de prise.' } }), {
        simulation: false,
        tiroir: { ouvert: false, code: 'non-pris-en-charge', message: 'Pas de prise.' },
    });
});

test('un reçu avec tiroir le demande à l’application, et rend son verdict', async () => {
    const appels = installerService((url) =>
        url.endsWith('/etat') ? reponse(200, etatAvecTiroir(true)) : reponse(200, { ok: true, tiroir: { ok: true } }),
    );
    const resultat = await imprimerRecu(recuExemple(), { tiroir: true, environnement: env });

    assert.equal(appels.find((a) => a.url.endsWith('/imprimer')).corps.tiroir, true);
    assert.deepEqual(resultat.tiroir, { ouvert: true });
});

test('une application d’avant le protocole 3 imprime le reçu : le tiroir est signalé non ouvert, pas tu', async () => {
    installerService((url) => (url.endsWith('/etat') ? reponse(200, etatAvecTiroir(undefined)) : reponse(200, { ok: true })));
    const resultat = await imprimerRecu(recuExemple(), { tiroir: true, environnement: env });
    assert.equal(resultat.tiroir.ouvert, false);
    assert.equal(resultat.tiroir.code, 'non-pris-en-charge');
    assert.match(resultat.tiroir.message, /remplacer par Tikéo/);
});

test('sans tiroir demandé, le résultat n’en parle pas', async () => {
    installerService((url) => (url.endsWith('/etat') ? reponse(200, etatAvecTiroir(true)) : reponse(200, { ok: true })));
    const resultat = await imprimerRecu(recuExemple(), { environnement: env });
    assert.equal('tiroir' in resultat, false);
});

test('ouvrirTiroir passe par POST /tiroir', async () => {
    const appels = installerService((url) => (url.endsWith('/etat') ? reponse(200, etatAvecTiroir(null)) : reponse(200, { ok: true })));
    await ouvrirTiroir();
    const envoi = appels.find((a) => a.url.endsWith('/tiroir'));
    assert.equal(envoi.url, 'http://127.0.0.1:17321/tiroir');
    assert.deepEqual(envoi.corps, {});
});

test('un terminal sans prise de tiroir est refusé d’avance, sans rien envoyer', async () => {
    const appels = installerService(() => reponse(200, etatAvecTiroir(false)));
    await assert.rejects(ouvrirTiroir(), (e) => e instanceof ErreurImpression && e.code === 'non-pris-en-charge');
    assert.equal(appels.filter((a) => a.url.endsWith('/tiroir')).length, 0);
});

test('le refus du pilote remonte tel quel ; une application trop ancienne (404) est signalée', async () => {
    installerService((url) =>
        url.endsWith('/etat') ? reponse(200, etatAvecTiroir(null)) : reponse(200, { ok: false, code: 'erreur', message: 'Le tiroir-caisse ne s’est pas ouvert (code -1).' }),
    );
    await assert.rejects(ouvrirTiroir(), (e) => e.code === 'erreur' && /code -1/.test(e.message));

    installerService((url) => (url.endsWith('/etat') ? reponse(200, etatAvecTiroir(undefined)) : reponse(404, { ok: false, code: 'introuvable' })));
    await assert.rejects(ouvrirTiroir(), (e) => e.code === 'non-pris-en-charge');
});

test('hors terminal, ouvrirTiroir dit que l’application est absente', async () => {
    globalThis.fetch = async () => {
        throw new TypeError('Failed to fetch');
    };
    await assert.rejects(ouvrirTiroir(), (e) => e.code === 'absente');
});

test('par le pont : ouvrirTiroir appelle l’application — ou dit qu’elle est trop ancienne', async () => {
    const appels = [];
    globalThis.window.Tikeo = {
        version: () => '3',
        etat: () => JSON.stringify(etatAvecTiroir(true)),
        imprimer: (id, png, options) => {
            appels.push(['imprimer', JSON.parse(options)]);
            globalThis.window.__tikeo.retour(id, JSON.stringify({ ok: true, tiroir: { ok: false, code: 'erreur', message: 'Bloqué.' } }));
        },
        ouvrirTiroir: (id) => {
            appels.push(['tiroir']);
            globalThis.window.__tikeo.retour(id, '{"ok":true}');
        },
    };
    await ouvrirTiroir();
    const resultat = await imprimerRecu(recuExemple(), { tiroir: true, environnement: env });
    assert.deepEqual(appels, [['tiroir'], ['imprimer', { avance: 3, support: 'recu', copies: 1, tiroir: true }]]);
    assert.deepEqual(resultat.tiroir, { ouvert: false, code: 'erreur', message: 'Bloqué.' });

    globalThis.window.Tikeo = { version: () => '2', etat: () => JSON.stringify({ code: 'prete', capacites: { massicot: false } }), imprimer() {} };
    await assert.rejects(ouvrirTiroir(), (e) => e.code === 'non-pris-en-charge' && /mettre à jour/.test(e.message));
});

test('ESC/POS : ESC p ouvre le tiroir branché sur l’imprimante, avant le reçu', () => {
    assert.deepEqual(Array.from(tiroirEscPos()), [0x1b, 0x70, 0x00, 25, 250]);
    assert.deepEqual(Array.from(tiroirEscPos(5)), [0x1b, 0x70, 0x01, 25, 250]);

    const avec = Array.from(rasterEscPos(() => false, 8, 1, { tiroir: true }));
    assert.deepEqual(avec.slice(0, 7), [0x1b, 0x40, 0x1b, 0x70, 0x00, 25, 250]);
    const sans = Array.from(rasterEscPos(() => false, 8, 1));
    assert.equal(sans.length, avec.length - 5);
});

test('useImprimante().ouvrirTiroir : vrai si le tiroir s’ouvre, l’erreur dans `erreur` sinon', async () => {
    const { useImprimante } = await import('../dist/vue.js');
    installerService((url) => (url.endsWith('/etat') ? reponse(200, etatAvecTiroir(true)) : reponse(200, { ok: true })));
    const imprimante = useImprimante();
    assert.equal(await imprimante.ouvrirTiroir(), true);
    assert.equal(imprimante.erreur.value, null);

    installerService(() => reponse(200, etatAvecTiroir(false)));
    assert.equal(await imprimante.ouvrirTiroir(), false);
    assert.match(imprimante.erreur.value, /tiroir/);
});

test('un reçu qui échoue chez le terminal dit quand même si le tiroir s’est ouvert', () => {
    assert.throws(
        () => lireVerdict({ ok: false, code: 'papier', message: 'Plus de papier.', tiroir: { ok: true } }),
        (e) => e instanceof ErreurImpression && e.code === 'papier' && e.tiroir.ouvert === true,
    );
    // Sans tiroir demandé, l'erreur n'en parle pas.
    assert.throws(() => lireVerdict({ ok: false, code: 'papier' }), (e) => e.code === 'papier' && e.tiroir === undefined);
});

test('le retour d’une copie plus ancienne du paquet est remplacé : le tiroir n’est pas perdu en route', async () => {
    // Une copie du protocole 2 a déjà installé son retour, qui ignore le tiroir.
    const attentes = new Map();
    globalThis.window.__tikeo = {
        attentes,
        retour(id) {
            const a = attentes.get(id);
            attentes.delete(id);
            clearTimeout(a.minuteur);
            a.resoudre({ simulation: false });
        },
    };
    globalThis.window.Tikeo = {
        version: () => '3',
        etat: () => JSON.stringify(etatAvecTiroir(true)),
        imprimer: (id) => globalThis.window.__tikeo.retour(id, JSON.stringify({ ok: true, tiroir: { ok: true } })),
    };
    const resultat = await imprimerRecu(recuExemple(), { tiroir: true, environnement: env });
    assert.deepEqual(resultat.tiroir, { ouvert: true });
    assert.equal(globalThis.window.__tikeo.version, Number(VERSION_PONT));
    assert.equal(globalThis.window.__tikeo.attentes, attentes, 'les attentes en cours sont gardées');

    // Une copie plus ancienne qui arrive après garde le retour le plus récent.
    const recent = globalThis.window.__tikeo;
    await imprimerRecu(recuExemple(), { environnement: env });
    assert.equal(globalThis.window.__tikeo, recent);
});

test('useImprimante().tiroir dit si le tiroir s’est ouvert, que le reçu sorte ou non', async () => {
    const { useImprimante } = await import('../dist/vue.js');
    let verdict = { ok: true, tiroir: { ok: false, code: 'erreur', message: 'Bloqué.' } };
    installerService((url) => (url.endsWith('/etat') ? reponse(200, etatAvecTiroir(null)) : reponse(200, verdict)));
    const imprimante = useImprimante();

    assert.equal(await imprimante.imprimer(recuExemple(), { tiroir: true, environnement: env }), true);
    assert.deepEqual(imprimante.tiroir.value, { ouvert: false, code: 'erreur', message: 'Bloqué.' });

    verdict = { ok: false, code: 'papier', message: 'Plus de papier.', tiroir: { ok: true } };
    assert.equal(await imprimante.imprimer(recuExemple(), { tiroir: true, environnement: env }), false);
    assert.deepEqual(imprimante.tiroir.value, { ouvert: true });

    verdict = { ok: true };
    await imprimante.imprimer(recuExemple(), { environnement: env });
    assert.equal(imprimante.tiroir.value, null);
});

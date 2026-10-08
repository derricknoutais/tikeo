import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

/**
 * Le contrat entre la page (src/pont.ts) et l'application (Kotlin) : le nom
 * de l'objet injecté, et celui du rappel par lequel l'application rend son
 * verdict. S'ils divergent, rien ne casse à la compilation : le reçu sort,
 * mais la page ne l'apprend jamais et finit en « délai dépassé ». C'est
 * arrivé au renommage sunmi-print → ecoprint.
 */
const lire = (chemin) => readFileSync(new URL(`../${chemin}`, import.meta.url), 'utf8');
const pontJs = lire('src/pont.ts');
const activite = lire('android/app/src/main/java/com/derricknoutais/tikeo/MainActivity.kt');
const pontKt = lire('android/app/src/main/java/com/derricknoutais/tikeo/PontImpression.kt');
const protocoleKt = lire('android/app/src/main/java/com/derricknoutais/tikeo/Protocole.kt');

test('l’application injecte le pont sous le nom que la page cherche', () => {
    const cote = pontJs.match(/type FenetreAvecPont = Window & \{ (\w+)\?: PontNatif/)[1];
    const injecte = activite.match(/addJavascriptInterface\(.*, "(\w+)"\)/)[1];
    assert.equal(injecte, cote);
});

test('l’application rappelle la page par le nom qu’elle écoute', () => {
    const ecoute = pontJs.match(/(\w+)\?: Retours \}/)[1];
    const rappelle = pontKt.match(/window\.(\w+)&&window\.\w+\.retour\(/)[1];
    assert.equal(rappelle, ecoute);
});

test('les deux côtés annoncent la même version de protocole', () => {
    const js = pontJs.match(/VERSION_PONT = '(\d+)'/)[1];
    const kt = protocoleKt.match(/const val VERSION = "(\d+)"/)[1];
    assert.equal(kt, js);
    assert.match(pontKt, /const val VERSION = Protocole\.VERSION/);
});

test('chaque méthode que la page appelle existe dans l’application, exposée au JavaScript', () => {
    const attendues = [...pontJs.match(/interface PontNatif \{([\s\S]*?)\n\}/)[1].matchAll(/^\s+(\w+)\??\(/gm)].map((m) => m[1]);
    const exposees = [...pontKt.matchAll(/@JavascriptInterface\s+fun (\w+)\(/g)].map((m) => m[1]);
    assert.deepEqual(attendues.sort(), exposees.sort());
});

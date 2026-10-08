<p align="center"><img src="docs/tikeo.svg" alt="Tikéo, par Ecolight" width="420"></p>

# Tikéo

> **Anciennement EcoPrint.** Depuis la v0.3.0 : paquet `@derricknoutais/tikeo`, application `com.derricknoutais.tikeo`, pont `window.Tikeo` — voir [Passer d'EcoPrint à Tikéo](#passer-decoprint-à-tikéo).

Imprimer des reçus sur l'imprimante intégrée des terminaux Android — **Sunmi**, **ZCS** — depuis une application web : Laravel, Vue, ou rien du tout. Et, sur les terminaux qui les ont : étiquettes, tiroir-caisse, écran client.

La page dessine le reçu en image noir et blanc, avec la police livrée par le paquet ; l'application Android **Tikéo** reconnaît le terminal et l'envoie à son imprimante. L'aperçu à l'écran est donc exactement ce qui sort : accents, « FCFA », QR compris. Et la page ne sait pas sur quelle marque elle tourne : le même `imprimerRecu()` imprime partout.

| | |
|---|---|
| `@derricknoutais/tikeo` | le paquet web, **sans framework** : décrire un reçu, le dessiner, l'imprimer, l'aperçu, ESC/POS |
| `@derricknoutais/tikeo/vue` | `useImprimante()` et le composant `<ApercuRecu>` |
| `android/` | l'application **Tikéo**, à installer sur chaque terminal |

Les pilotes d'imprimante vivent dans leurs propres dépôts, et Tikéo les intègre :

| Dépôt | Terminaux | État |
|---|---|---|
| [sunmi-print](https://github.com/derricknoutais/sunmi-print) | Sunmi V2 Pro, V2s, P2, T2… — et les marques qui reprennent le service Sunmi | imprime (vérifié sur un V2 Pro) |
| [zcs-print](https://github.com/derricknoutais/zcs-print) (privé) | ZCS Z90, Z91, Z92, Z100… — et les ZCS revendus sous une autre marque | imprime (vérifié sur un Z92S et un Z100), avec le SDK SmartPos de ZCS |

---

## Ce que les terminaux imposent

- **Une page web ne peut pas parler à l'imprimante.** Chaque fabricant ne l'ouvre qu'aux applications Android : service d'impression chez Sunmi, SDK propriétaire chez ZCS. D'où l'application Tikéo, et un pilote par marque.
- **Un Sunmi V2 Pro a deux moteurs web.** Son navigateur est un Chromium **74** — c'est là que tournent vos applications. Mais les applications Android affichent leurs pages avec le WebView du système, resté en version **62** : une application Vite n'y démarre même pas (l'import dynamique date de Chrome 63). Relevé sur un V2 Pro sous Android 7.1.2 : `org.chromium.chrome` 74.0.3710, `com.google.android.webview` 62.0.3202.
- D'où le mode à préférer sur ce terminal : **l'application web reste dans le navigateur**, et Tikéo, en service de fond, reçoit les reçus sur `http://127.0.0.1:17321`. Une requête d'une page https vers l'adresse de boucle locale n'est pas du contenu mixte — vérifié dans le Chromium 74 du V2 Pro.
- **Attention au navigateur par défaut** : sur ce V2 Pro, un lien https s'ouvre dans un vieux Chrome **56** (`com.android.chrome`), où une application Vite ne démarre pas. Ouvrir les applications depuis **Chromium**.
- **Un ZCS Z92S est récent** : Android 16, WebView et Chrome 143. Les deux modes y fonctionnent : son Chrome laisse une page https appeler `127.0.0.1` sans demande d'autorisation — vérifié.
- **58 mm = 384 points** à 203 dpi (80 mm = 576). Le V2 Pro et le Z92S n'ont pas de massicot : le papier avance de quelques lignes pour se détacher à la barre. Sur un ZCS qui en a un, Tikéo coupe le reçu une fois sorti.
- **Du texte envoyé tel quel à une imprimante thermique dépend de sa page de codes** : « PAYÉ » peut sortir « PAYÃ‰ ». Une image, jamais.

## Comment ça marche

```
page ── dessinerRecu() ─► canvas 384 points, Roboto embarquée, noir et blanc ─► PNG
     │
     ├─ dans le navigateur du terminal ─► http://127.0.0.1:17321/imprimer ─┐
     └─ dans l'application (mode coque) ─► window.Tikeo ────────────────┤
                                                                            ▼
                             Tikéo ─► pilote du terminal reconnu : Sunmi, ZCS… (sinon simulation)
                                                                            │
     la promesse se résout quand le reçu est SORTI ◄── verdict de l'imprimante
```

Au démarrage, Tikéo essaie les pilotes : ZCS d'abord (il ne se déclare que sur un terminal ZCS), puis Sunmi (partout où le service Sunmi existe). Aucun ne répond : **simulation**, rien n'est imprimé. Chaque pilote rend un verdict sur le reçu entier : la page sait qu'il est sorti, ou pourquoi il ne l'est pas — plus de papier, capot ouvert, tête trop chaude, batterie trop faible.

## Installation

### JavaScript

```bash
npm install github:derricknoutais/tikeo#v0.3.0
```

En développement, à côté du projet : `npm install ../tikeo`.

### L'application Android, sur chaque terminal

Les pilotes entrent dans la construction depuis leurs dépôts, clonés **à côté** de celui-ci :

```
~/Herd/tikeo        ← ce dépôt
~/Herd/sunmi-print   ← pilote Sunmi
~/Herd/zcs-print     ← pilote ZCS (privé), avec le SDK ZCS dans android/sdk/
```

Construire l'APK (Android Studio installé ; son Java suffit) :

```bash
cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

L'installer sur un terminal branché en USB, débogage USB activé :

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Puis, sur le terminal, ouvrir **Tikéo**. Au premier lancement, une **mise en route** en trois temps :

1. **Votre terminal** — le terminal et le pilote reconnus, le papier, ce que sait faire l'imprimante.
2. **Qui peut imprimer ?** — l'adresse de l'application web (`https://storit.stapog.com`…). Seule l'origine compte : l'aide sous le champ montre en direct ce qui sera retenu.
3. **Tout est prêt** — un reçu d'exemple, pour vérifier le papier et la tête.

Ensuite, trois onglets :

| Onglet | |
|---|---|
| **Accueil** | l'état de l'imprimante d'un coup d'œil, « Tester l'imprimante », « Ouvrir l'application ici » (mode coque, sur l'adresse principale), puis le détail : adresses, service local, terminal |
| **Adresses** | les adresses autorisées : ajouter, relire, retirer ; la première est la principale. Le certificat auto-signé du mode coque |
| **Test** | reçu d'exemple, mire ou étiquette — vus avant d'être imprimés —, écran client, tiroir-caisse, et le journal de ce qui s'est passé |

L'interface est en français ou en anglais (bouton FR / EN de l'en-tête). Le service local démarre avec l'application, puis à chaque démarrage du terminal.

Pour éprouver le pont du mode coque — la page web de test embarquée, qui imprime par `window.Tikeo` : **appui long** sur la ligne de version, en bas de l'accueil.

**Un terminal ne garde qu'une application d'impression** : les anciennes écoutent le même port. Avant d'installer Tikéo, désinstaller **EcoPrint** (versions 0.1 et 0.2) et, sur un Sunmi, **Sunmi Print** (v0.1.0 de sunmi-print) :

```bash
adb uninstall com.derricknoutais.ecoprint
```

Si Tikéo affiche « Arrêté — Port 17321 indisponible », c'est qu'une ancienne application tient encore le port : la désinstaller, puis rouvrir Tikéo — son service réessaie à chaque ouverture.

### L'identité

Le symbole est le terminal lui-même — corps sombre, voyant vert, fente orange, ticket qui sort —, d'après la planche « Tikéo Logo v2 » : couleurs `#3B3B3D` (corps et texte), `#F38120` (fente), `#6DBE4A` (voyant), `#2A8139` (vert du texte), Montserrat pour le mot TIKÉO. L'application en reprend les couleurs : l'en-tête et les actions dans le corps sombre, les repères (étapes, onglet actif) dans l'orange de la fente, l'état dans le vert du voyant. L'orange ne porte jamais de texte : blanc sur `#F38120` ne se lit pas assez.

`npm run marque` régénère les icônes de l'application (PNG pour Android 7, adaptatives et monochromes pour Android 8 et plus), l'icône de notification et `docs/tikeo.svg`, à partir de la géométrie de la planche (`scripts/construire-marque.mjs`). La police Montserrat de l'application ne contient que les lettres de TIKÉO et PAR ECOLIGHT (`android/app/src/main/assets/polices/`, SIL Open Font License).

## Passer d'EcoPrint à Tikéo

La v0.3.0 renomme tout :

| | EcoPrint (≤ 0.2) | Tikéo (≥ 0.3) |
|---|---|---|
| paquet web | `@derricknoutais/ecoprint` | `@derricknoutais/tikeo` |
| dépôt | `github:derricknoutais/ecoprint` | `github:derricknoutais/tikeo` |
| application Android | `com.derricknoutais.ecoprint` | `com.derricknoutais.tikeo` |
| pont du mode coque | `window.EcoPrint` | `window.Tikeo` (protocole 4) |

Dans une application web, changer la dépendance et les imports :

```bash
npm install github:derricknoutais/tikeo#v0.3.0
```

```ts
import { imprimerRecu } from '@derricknoutais/tikeo';      // était '@derricknoutais/ecoprint'
import { useImprimante } from '@derricknoutais/tikeo/vue'; // était '@derricknoutais/ecoprint/vue'
```

L'ancienne adresse `github:derricknoutais/ecoprint` redirige vers `tikeo` : les applications pas encore migrées continuent de s'installer.

Rien d'autre ne change : mêmes fonctions, même service local sur `127.0.0.1:17321`. Le passage peut se faire application par application — une page qui garde l'ancien paquet imprime avec Tikéo par le service local ; dans le mode coque de Tikéo, elle ne trouve plus `window.EcoPrint` et y passe aussi.

## Décrire un reçu

Un reçu est une liste de blocs — de simples objets JSON, que la page peut composer aussi bien qu'un contrôleur Laravel.

| Bloc | Champs | |
|---|---|---|
| `texte` | `texte`, `alignement`, `taille`, `gras` | passe à la ligne tout seul ; `\n` force un retour |
| `ligne` | `gauche`, `droite`, `taille`, `gras` | libellé à gauche, montant à droite, jamais coupé |
| `separateur` | `style` : `tirets`, `plein`, `double` | |
| `espace` | `hauteur` (points, 16 par défaut) | |
| `qr` | `donnees`, `taille`, `alignement`, `correction` | modules entiers, zone blanche, UTF-8 |
| `image` | `source`, `largeur`, `alignement`, `tramage` | logo en `seuil`, photo en `diffusion` |

`taille` : `petite` (20 points), `normale` (24), `grande` (32), `titre` (40). `alignement` : `gauche`, `centre`, `droite`.

```ts
import { montant, type Recu } from '@derricknoutais/tikeo';

const recu: Recu = {
    blocs: [
        { type: 'texte', texte: 'LE PALMIER', taille: 'titre', gras: true, alignement: 'centre' },
        { type: 'separateur' },
        { type: 'ligne', gauche: 'Poulet braisé', droite: montant(6500, '') },
        { type: 'ligne', gauche: 'TOTAL TTC', droite: montant(20000), taille: 'grande', gras: true },
        { type: 'qr', donnees: 'https://exemple.ga/r/2026-0042' },
    ],
};
```

Ou côté serveur, passé en prop Inertia :

```php
return Inertia::render('Ventes/Recu', ['recu' => ['blocs' => [
    ['type' => 'texte', 'texte' => $vente->boutique, 'taille' => 'titre', 'gras' => true, 'alignement' => 'centre'],
    ...$vente->lignes->map(fn ($l) => ['type' => 'ligne', 'gauche' => $l->libelle, 'droite' => number_format($l->total, 0, ',', ' ')]),
]]]);
```

Deux choix de mise en page méritent d'être connus :

- **Une ligne de ticket essaie deux dispositions** et garde la plus courte : le montant sur la première ligne, le libellé dans la place restante ; ou le libellé sur toute la largeur, le montant au bout de sa dernière ligne. Un gros montant ne hache plus un long libellé en tronçons.
- **Une référence trop longue se coupe après un tiret** (`ADK-` / `5904`) plutôt qu'au milieu d'un nombre.

`montant(12000)` donne « 12 000 FCFA », avec des espaces insécables : un montant ne se coupe jamais d'une ligne à l'autre. Écrit à la main plutôt que par `Intl` : les données ICU du WebView d'un terminal ne sont pas celles d'un Chrome récent.

## Imprimer

```ts
import { ErreurImpression, imprimerRecu } from '@derricknoutais/tikeo';

try {
    await imprimerRecu(recu); // se résout quand le reçu est sorti
} catch (e) {
    if (e instanceof ErreurImpression && e.code === 'papier') {
        // « Plus de papier. »
    }
}
```

| `code` | |
|---|---|
| `papier`, `capot`, `surchauffe` | l'imprimante le dit ; `message` est prêt à afficher |
| `occupee` | l'imprimante se prépare, un reçu est en cours, ou le pilote démarre |
| `refusee` | l'adresse de la page n'est pas autorisée dans Tikéo |
| `absente` | ni application ni service : navigateur de bureau, Tikéo pas installée — ou, sur un ZCS, SDK pas encore intégré |
| `delai` | aucun verdict dans le temps imparti |
| `non-pris-en-charge` | étiquette, tiroir-caisse ou écran client : ce terminal ne l'a pas, ou cette version de Tikéo ne le pilote pas encore |

`etatImprimante()` donne l'état sans imprimer, et la largeur du papier : le reçu est dessiné à la largeur de l'imprimante trouvée. Il dit aussi ce que sait faire le terminal :

```ts
const { capacites } = await etatImprimante();
// { massicot: true, etiquettes: null, tiroir: null, afficheur: { largeur: 480, hauteur: 480 } }
```

| | |
|---|---|
| `massicot` | le reçu est coupé une fois sorti — rien à demander, Tikéo le fait |
| `etiquettes` | `false` : pas de papier étiquette (Sunmi) ; `null` : le pilote ne peut pas le savoir d'avance, c'est l'essai qui tranche (ZCS) |
| `tiroir` | une prise de tiroir-caisse : `true` sur un Sunmi de comptoir (T…, D…), `false` sur un portable (V2 Pro…) ; `null` quand on ne peut pas le savoir d'avance — sur un ZCS (le SDK ne le dit pas), sur un Sunmi de modèle inconnu, ou avec l'ancienne application EcoPrint (avant le protocole 3) : c'est l'essai qui tranche |
| `afficheur` | l'écran client — `{ largeur, hauteur }`, plus `monochrome: true` pour un petit LCD noir et blanc —, ou `null` s'il n'y en a pas |

`capacites` est absent avec l'ancienne application EcoPrint 0.1 (protocole 1). Avec EcoPrint 0.2 avant le protocole 3, `tiroir` y vaut `null` ; l'essai répond alors `non-pris-en-charge`, avec un message qui demande de la remplacer par Tikéo.

### Étiquettes

Sur une imprimante qui accepte le papier étiquette, les mêmes blocs font une étiquette — un nom, un prix, un QR :

```ts
await imprimerRecu(
    {
        blocs: [
            { type: 'texte', texte: 'Plaquettes de frein AV', gras: true, alignement: 'centre' },
            { type: 'texte', texte: montant(18500), taille: 'titre', gras: true, alignement: 'centre' },
            { type: 'qr', donnees: 'BK341340', taille: 120 },
        ],
    },
    { support: 'etiquette', copies: 4, largeur: 320 }, // 4 étiquettes de 40 mm
);
```

L'imprimante passe en mode étiquette, imprime les exemplaires l'un après l'autre en se calant sur l'espace entre deux étiquettes, puis revient au reçu. `largeur` : celle de l'étiquette, en points (8 par mm) ; celle du rouleau par défaut. Rouleau étiquette chargé, sinon l'imprimante le dit.

### Tiroir-caisse

Le tiroir-caisse se branche sur la prise RJ11/RJ12 du terminal — « CASH BOX », « DRAWER » — : seuls les terminaux de comptoir en ont une. Tikéo l'ouvre par le pilote du terminal (`openDrawer()` chez Sunmi, `openBox()` chez ZCS).

Avec un reçu — le cas d'une vente payée en espèces :

```ts
try {
    const resultat = await imprimerRecu(recu, { tiroir: true });
    if (resultat.tiroir && !resultat.tiroir.ouvert) {
        // Le reçu est sorti, le tiroir non : resultat.tiroir.message dit pourquoi.
    }
} catch (e) {
    // Le reçu n'est pas sorti. Le tiroir a pu s'ouvrir quand même : e.tiroir le dit.
    if (!(e instanceof ErreurImpression && e.tiroir && e.tiroir.ouvert)) await ouvrirTiroir();
}
```

Le tiroir s'ouvre d'abord, et le reçu part aussitôt derrière, sans attendre que le tiroir ait répondu : il sort pendant que le caissier rend la monnaie. Un tiroir qui ne s'ouvre pas n'empêche pas le reçu : c'est `resultat.tiroir` qui le dit, pas une erreur. Un reçu refusé d'avance (plus de papier constaté avant l'envoi) n'ouvre rien ; un reçu qui échoue chez le terminal (papier épuisé en cours de route) a pu ouvrir le tiroir, et l'erreur le dit dans `tiroir`. Si la vente doit continuer sans reçu, n'appeler `ouvrirTiroir()` que si le tiroir n'est pas déjà ouvert — sinon la vente compterait une ouverture de trop.

Sans reçu — rendu de monnaie, vente sans ticket :

```ts
import { ouvrirTiroir } from '@derricknoutais/tikeo';

await ouvrirTiroir();
```

`ouvrirTiroir()` se rejette avec `non-pris-en-charge` quand Tikéo sait que le terminal n'a pas de prise (`capacites.tiroir === false` : un Sunmi portable) ou qu'il est trop ancien. Sur un ZCS (`tiroir: null`), le SDK ne le dit pas : sans prise, il rend `erreur` — ou réussit sans rien ouvrir.

Ouvrir la caisse sans vente est un geste à tracer : c'est à l'application de dire qui y a droit (un rôle, un code de responsable) et de l'enregistrer. Tikéo ne trie que les pages web — une page doit être autorisée, cadres intégrés compris (voir [Sécurité](#sécurité)) — et ne sait pas qui est devant la caisse. Comme pour l'impression, une requête locale sans en-tête `Origin` (un `curl` par `adb`, une autre application du terminal) et l'onglet Test de l'application ouvrent aussi le tiroir ; une application installée sur le terminal pourrait de toute façon l'ouvrir par le SDK du fabricant, sans Tikéo.

## L'écran client

Sur un terminal à deux écrans, le petit écran tourné vers le client affiche ce que la page veut — le panier, le total à payer, un QR de paiement, le logo — avec **les mêmes blocs qu'un reçu** :

```ts
import { afficherClient, effacerClient, montant } from '@derricknoutais/tikeo';

await afficherClient({
    blocs: [
        { type: 'texte', texte: 'LE PALMIER', taille: 'grande', gras: true, alignement: 'centre' },
        { type: 'separateur' },
        { type: 'ligne', gauche: '2 × Poulet braisé', droite: montant(13000, '') },
        { type: 'ligne', gauche: '1 × Jus de bissap', droite: montant(1500, '') },
        { type: 'separateur', style: 'double' },
        { type: 'texte', texte: 'À PAYER', alignement: 'centre' },
        { type: 'texte', texte: montant(14500), taille: 'titre', gras: true, alignement: 'centre' },
    ],
});

await effacerClient(); // vente terminée : l'écran revient au blanc
```

Le contenu est dessiné à la taille exacte de l'écran, en niveaux de gris (un écran n'est pas une tête thermique : les lettres gardent leurs bords lissés), centré, et réduit s'il est trop haut — le client voit tout d'un coup, rien n'est coupé. L'appeler à chaque article ajouté : l'image précédente est remplacée. Sans écran client, `afficherClient` se rejette avec `non-pris-en-charge` : l'appeler partout sans risque, et ignorer ce code.

Il y a deux sortes d'écrans client, et `capacites.afficheur` dit lequel :

| Écran | Format | Ce qui y tient |
|---|---|---|
| couleur (ZCS qui l'annoncent) | 480 × 480 | le panier entier, un QR, un logo |
| **LCD noir et blanc** (ZCS Z100) | 128 × 64, `monochrome: true` | deux lignes courtes : « À PAYER », le montant |

Sur le LCD, le contenu est dessiné deux fois plus grand puis réduit et remis en noir et blanc pur : « 20 000 FCFA » tient sur une ligne, net. Un panier entier y serait réduit jusqu'à l'illisible : afficher le total seul — `ecranLcdExemple()` en donne le modèle.

```ts
const { capacites } = await etatImprimante();
await afficherClient(capacites?.afficheur?.monochrome
    ? { blocs: [{ type: 'texte', texte: 'À PAYER', gras: true, alignement: 'centre' },
                { type: 'texte', texte: montant(total), taille: 'grande', gras: true, alignement: 'centre' }] }
    : panierComplet);
```

## Vue 3

```vue
<script setup lang="ts">
import { ApercuRecu, useImprimante } from '@derricknoutais/tikeo/vue';

const { imprimer, enCours, erreur } = useImprimante();
</script>

<template>
    <ApercuRecu :recu="recu" />
    <button :disabled="enCours" @click="imprimer(recu)">Imprimer</button>
    <p v-if="erreur" role="alert">{{ erreur }}</p>
</template>
```

`useImprimante()` rend aussi `ouvrirTiroir()`, qui se résout à `true` si le tiroir s'est ouvert et met l'erreur dans `erreur` sinon. Après `imprimer(recu, { tiroir: true })`, `tiroir.value` dit si le tiroir s'est ouvert (`{ ouvert, code, message }`) — que le reçu soit sorti ou non.

Pour l'écran client, `useEcranClient()` rend `afficher(contenu)`, `effacer()`, `disponible`, `enCours` et `erreur`. L'appeler à chaque changement du panier : les demandes passent une à une et seule la plus récente est envoyée. Un terminal sans écran client, ou un navigateur de bureau, n'est pas une erreur : `disponible` passe à `false` et les appels suivants ne font plus rien — pas même une requête vers `127.0.0.1`.

```ts
const ecran = useEcranClient();
watch(panier, (p) => ecran.afficher(contenuEcran(p)), { deep: true });
```

`useImprimante` ne cherche pas l'imprimante au montage : sur un Chrome de bureau récent, interroger `127.0.0.1` depuis un site public déclenche une demande d'accès au réseau local. Elle cherche quand on imprime, ou quand on appelle `detecter()` (`{ detecterAuMontage: true }` pour un écran réservé aux terminaux).

## Autres imprimantes : ESC/POS

```ts
import { dessinerRecu, versEscPos } from '@derricknoutais/tikeo';

const octets = versEscPos(await dessinerRecu(recu)); // à envoyer par Bluetooth, réseau, USB
```

Le reçu part en image tramée (`GS v 0`), que toutes les imprimantes thermiques comprennent : les accents sortent comme à l'aperçu, quelle que soit leur page de codes.

Un tiroir-caisse branché sur l'imprimante s'ouvre avec `versEscPos(toile, { tiroir: true })` — `ESC p` en tête du reçu —, ou seul avec `tiroirEscPos()` (broche 2 ; `tiroirEscPos(5)` pour la broche 5).

## Sécurité

- Le service n'écoute que sur `127.0.0.1` : injoignable depuis le réseau. Il ne répond qu'aux **origines autorisées** ; un refus arrive avec les en-têtes CORS, pour que la page puisse dire pourquoi. Une requête sans en-tête `Origin` ne vient pas d'un navigateur (un `curl` par `adb`) : elle passe, pour le diagnostic.
- Android injecte le pont dans toutes les pages et tous les cadres de la WebView : chaque appel vérifie que la page principale affichée est autorisée. Une page d'une autre origine reçoit `refusee` — mais un cadre (iframe) tiers intégré à une page autorisée passe ce contrôle, et peut imprimer ou ouvrir le tiroir. Ne pas intégrer de cadre tiers (paiement, publicité, carte) dans une page autorisée, ou l'isoler avec `<iframe sandbox>` sans `allow-scripts`.
- Mode coque : un certificat auto-signé n'est accepté que sur le réseau local (`10/8`, `172.16/12`, `192.168/16`), et seulement si la case est cochée.

## Compatibilité

- `dist/` et la page de test tournent dans **Chrome 62** : `test/compatibilite.test.mjs` refuse toute syntaxe ou API plus récente (`catch {}`, `flatMap`, `import()`, `import.meta`, `finally`…). Pas d'import dynamique : la police est importée statiquement, un bundler ne l'embarque que dans le code qui dessine.
- Aucune dépendance d'exécution n'exclut **Node 20** (`test/engines.test.mjs`) : Yarn 1 sur un serveur Forge refuserait sinon d'installer le projet — ce qui est arrivé à sunmi-scan avec ZXing 0.22.
- Une application ouverte **dans le navigateur** du terminal suit les règles de sunmi-scan (Chrome 74). Ouverte **dans Tikéo** (mode coque), elle doit tourner dans le WebView du système : Chrome 62 sur un V2 Pro : avec Vite, `@vitejs/plugin-legacy`.

## Développer le paquet

```bash
npm install
npm test          # compile, puis 100 tests : mise en page, dessin (Skia et la vraie police),
                  # QR relu par ZXing, ESC/POS, pont, service local, écran client, étiquettes, tiroir,
                  # montants, contrat page ↔ application, Chrome 62, Node 20
```

- `TIKEO_APERCUS=/un/dossier npm test` enregistre les reçus dessinés en PNG, pour les regarder.
- `npm run build` compile `dist/`, la page de test dans `android/app/src/main/assets/test/` et les échantillons de l'onglet Test dans `android/app/src/main/assets/apercus/` (versionnés : l'APK se construit sans Node).
- `npm run police` régénère `src/police-donnees.ts` depuis `polices/` — Roboto Medium et Bold, jeu latin, SIL Open Font License.
- Sur un émulateur, un téléphone ou une marque sans pilote, Tikéo passe en **simulation** : le pont affiche le reçu au lieu de l'imprimer, le service le garde dans `derniere-impression.png` (`adb pull /sdcard/Android/data/com.derricknoutais.tikeo/files/derniere-impression.png`).
- Version de développement : `chrome://inspect` depuis le poste, terminal branché en USB, pour déboguer la page ouverte dans l'application.

## Vérifié sur un vrai terminal

Sunmi V2 Pro (Android 7.1.2, imprimante POS-V2, 58 mm), le 28 septembre 2026, avec **Sunmi Print** — l'ancêtre de Tikéo, dont le pilote Sunmi et le service reprennent le code tel quel :

| Chemin | Moteur | Résultat |
|---|---|---|
| page de test dans l'application, pont direct | WebView 62 | reçu d'exemple imprimé (2,5 s), mire imprimée (3,1 s) |
| page **https** dans le navigateur → `http://127.0.0.1:17321` | Chromium 74 | service détecté, reçu imprimé (2,3 s) |

ZCS Z92S (Android 16, SDK SmartPos 2.0.9, 58 mm), le 29 septembre 2026, avec EcoPrint (l'ancien nom de Tikéo) :

| Chemin | Moteur | Résultat |
|---|---|---|
| page de test dans l'application, pont direct | WebView 143 | reçu d'exemple imprimé (2,4 s), mire imprimée (3,0 s) |
| page **https** dans le navigateur → `http://127.0.0.1:17321` | Chrome 143 | service détecté sans demande d'autorisation, reçu imprimé (2,2 s) |

ZCS **Z100** revendu sous la marque SPEEDSTAR (« BETA COMPUTERS LIMITED StarTab-A324G », Android 13, système `Z100mbs_EU_A13_V1.0.5`), le 3 octobre 2026, par le service local :

| Fonction | Résultat |
|---|---|
| reconnaissance | pilote `zcs`, malgré le fabricant annoncé : reconnu à `ro.zcs.platform.tag` |
| reçu 80 mm (576 points) | imprimé, verdict en 1,1 s |
| massicot | reçu coupé — le massicot répond « occupée » tant que le papier sort : le pilote réessaie (coupe au 3ᵉ essai, 0,9 s) |
| tiroir-caisse | ouvert (`openBox()`) |
| écran client | LCD 128 × 64 noir et blanc : « À PAYER / 20 000 FCFA » affiché, puis effacé |

Les durées sont celles du verdict de l'imprimante, papier sorti.

Le débogage USB d'un Z100 s'active par un **bouton à l'arrière** du terminal : sans lui, il ne se présente pas à l'ordinateur, même branché, même options pour les développeurs activées.

## Limites connues

- **Vérifiés sur un Z100** : 80 mm, massicot, tiroir-caisse, LCD client. **Pas encore vérifiés** : les étiquettes (pas de rouleau d'étiquettes essayé), le grand écran client couleur (aucun terminal essayé n'en a), le tiroir-caisse sur un Sunmi de comptoir.
- **Jeu latin seulement** : un caractère absent de la police embarquée (chinois, arabe…) est rendu par une police du système, ou pas du tout.
- **Signature de développement** : l'APK est signé avec la clé de débogage du poste qui le construit. Une mise à jour construite ailleurs ne s'installera pas par-dessus : prévoir une clé de publication avant d'équiper plusieurs terminaux.

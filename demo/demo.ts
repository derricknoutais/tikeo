/**
 * La page de test : embarquée dans l'application Android (« Tester
 * l'imprimante »), ouvrable aussi dans un navigateur de bureau pour l'aperçu.
 * Compilée en un seul script classique par scripts/construire-demo.mjs : elle
 * doit tourner sans bundler, dans le WebView du V2 Pro — Chrome 62.
 */
import {
    afficherClient,
    apercuRecu,
    ecranExemple,
    ecranLcdExemple,
    effacerClient,
    ErreurImpression,
    etatImprimante,
    etiquetteExemple,
    imprimerRecu,
    mire,
    ouvrirTiroir,
    recuExemple,
    versionPont,
    type EtatImprimante,
    type Recu,
    type ResultatTiroir,
} from '../src/index.ts';

const element = (id: string): HTMLElement => {
    const trouve = document.getElementById(id);
    if (!trouve) throw new Error(`#${id} absent de la page`);
    return trouve;
};

let recuAffiche: Recu = recuExemple();

function journal(message: string, genre: 'ok' | 'erreur' | 'info' = 'info'): void {
    const li = document.createElement('li');
    li.className = genre;
    const heure = document.createElement('time');
    heure.textContent = new Date().toLocaleTimeString('fr-FR');
    li.appendChild(heure);
    li.appendChild(document.createTextNode(message));
    const liste = element('journal');
    liste.insertBefore(li, liste.firstChild);
}

const TRANSPORTS = {
    pont: () => `ouverte dans Tikéo (pont direct, protocole ${versionPont()})`,
    serveur: () => 'navigateur — service local de Tikéo',
    aucun: () => 'aucune — aperçu seulement',
};

let dernierEtat: EtatImprimante | null = null;

async function afficherEtat(): Promise<void> {
    const etat = await etatImprimante();
    dernierEtat = etat;
    element('pont').textContent = TRANSPORTS[etat.transport || 'aucun']();
    const libelle = element('etat');
    libelle.textContent = etat.message || etat.code;
    libelle.className = etat.code === 'prete' || etat.code === 'simulation' ? 'etat-prete' : 'etat-autre';
    element('largeur').textContent = `${etat.largeur} points (${etat.largeur >= 576 ? '80' : '58'} mm)${etat.modele ? ` — ${etat.modele}` : ''}`;
    element('terminal').textContent = etat.terminal ? `${etat.terminal}${etat.pilote ? ` (pilote ${etat.pilote})` : ''}` : '—';
    const c = etat.capacites;
    element('capacites').textContent = !c
        ? '—'
        : `massicot ${ouiNon(c.massicot)} · étiquettes ${ouiNon(c.etiquettes)} · tiroir ${ouiNon(c.tiroir)} · écran client ${c.afficheur ? `${c.afficheur.largeur} × ${c.afficheur.hauteur}${c.afficheur.monochrome ? ' noir et blanc' : ''}` : 'non'}`;
}

function ouiNon(valeur: boolean | null): string {
    return valeur === null ? 'à l’essai' : valeur ? 'oui' : 'non';
}

/** Un dégradé noir → blanc, pour juger la trame de l'imprimante. */
function degrade(): string {
    const toile = document.createElement('canvas');
    toile.width = 300;
    toile.height = 60;
    const ctx = toile.getContext('2d');
    if (!ctx) return '';
    const g = ctx.createLinearGradient(0, 0, 300, 0);
    g.addColorStop(0, '#000');
    g.addColorStop(1, '#fff');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 300, 60);
    return toile.toDataURL('image/png');
}

async function montrer(recu: Recu): Promise<void> {
    recuAffiche = recu;
    try {
        const toile = (await apercuRecu(recu)) as HTMLCanvasElement;
        const cadre = element('apercu');
        while (cadre.firstChild) cadre.removeChild(cadre.firstChild);
        cadre.appendChild(toile);
    } catch (e) {
        journal(`Aperçu impossible : ${e instanceof Error ? e.message : String(e)}`, 'erreur');
    }
}

async function imprimer(): Promise<void> {
    const bouton = element('imprimer') as HTMLButtonElement;
    bouton.disabled = true;
    const debut = Date.now();
    try {
        const resultat = await imprimerRecu(recuAffiche);
        const duree = ((Date.now() - debut) / 1000).toFixed(1);
        journal(resultat.simulation ? `Simulation : reçu affiché par l'application (${duree} s).` : `Reçu imprimé (${duree} s).`, 'ok');
    } catch (e) {
        const code = e instanceof ErreurImpression ? `[${e.code}] ` : '';
        journal(`${code}${e instanceof Error ? e.message : String(e)}`, 'erreur');
    } finally {
        bouton.disabled = false;
        afficherEtat();
    }
}

element('voir-recu').addEventListener('click', () => montrer(recuExemple()));
element('voir-mire').addEventListener('click', () => montrer(mire({ image: degrade(), largeur: dernierEtat ? dernierEtat.largeur : 384 })));
element('actualiser').addEventListener('click', () => {
    afficherEtat().then(() => journal('État actualisé.'));
});
element('imprimer').addEventListener('click', imprimer);

/** Une action sur le terminal, et son verdict dans le journal. */
function essayer(bouton: string, action: () => Promise<unknown>, succes: string): void {
    element(bouton).addEventListener('click', () => {
        const b = element(bouton) as HTMLButtonElement;
        b.disabled = true;
        const debut = Date.now();
        action()
            .then(() => journal(`${succes} (${((Date.now() - debut) / 1000).toFixed(1)} s).`, 'ok'))
            .catch((e) => journal(`${e instanceof ErreurImpression ? `[${e.code}] ` : ''}${e instanceof Error ? e.message : String(e)}`, 'erreur'))
            .then(() => {
                b.disabled = false;
            });
    });
}

essayer('etiquette', () => imprimerRecu(etiquetteExemple(), { support: 'etiquette', copies: 1 }), 'Étiquette imprimée');
essayer(
    'ecran',
    () => {
        const format = dernierEtat && dernierEtat.capacites ? dernierEtat.capacites.afficheur : null;
        return afficherClient(format && format.monochrome ? ecranLcdExemple() : ecranExemple());
    },
    'Écran client affiché',
);
essayer('effacer', () => effacerClient(), 'Écran client effacé');
essayer('tiroir', () => ouvrirTiroir(), 'Tiroir-caisse ouvert');

function signalerTiroir(tiroir: ResultatTiroir | undefined): void {
    if (!tiroir) return;
    if (tiroir.ouvert) journal('Tiroir-caisse ouvert.', 'ok');
    else journal(`Tiroir-caisse : [${tiroir.code}] ${tiroir.message}`, 'erreur');
}
essayer(
    'recu-tiroir',
    () =>
        imprimerRecu(recuAffiche, { tiroir: true }).then(
            (resultat) => signalerTiroir(resultat.tiroir),
            (e) => {
                // Le reçu a échoué : le tiroir a pu s'ouvrir quand même.
                if (e instanceof ErreurImpression) signalerTiroir(e.tiroir);
                throw e;
            },
        ),
    'Reçu imprimé',
);

montrer(recuAffiche);
afficherEtat().then(() => {
    const transport = dernierEtat && dernierEtat.transport;
    journal(
        transport === 'pont' ? 'Page ouverte dans Tikéo : impression par le pont direct.'
        : transport === 'serveur' ? 'Service local de Tikéo détecté.'
        : "Ni application ni service : l'impression est impossible ici, l'aperçu reste exact.",
    );
});

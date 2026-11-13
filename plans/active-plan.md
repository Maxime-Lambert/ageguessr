# Partage du résultat de la partie du jour, façon Wordle

À la fin de la partie du jour, un bouton « Partager mon résultat » donne un texte court :
la date, le score, une case par manche, le lien du site. Rien des réponses, aucun compte
requis. Branche `feature/daily-share`, depuis `develop`. Décisions de l'utilisateur du
2026-11-13.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions

- **Texte** (EN source, FR traduit) :
  ```
  AgeGuessr · 13/11/2026 · 2340 / 3000
  🎯🟩🟥
  https://ageguessr.app
  ```
  - **La date** identifie le jour (pas de numéro de dossier). C'est le jour UTC de la
    partie (`TodayGame.date`, YYYY-MM-DD), formaté en UTC pour ne jamais glisser d'un
    jour selon le fuseau du joueur.
  - **Format de date traduit** : le format est lui-même un texte `$localize`
    (`@@game.shareDateFormat`) passé à `formatDate` d'Angular. FR `dd/MM/yyyy` →
    « 13/11/2026 » ; EN `MMM d, y` → « Nov 13, 2026 ». *Hypothèse* : en anglais,
    « 13/11/2026 » se lirait 11e jour du 13e mois pour un Américain ; le mois en lettres
    est sans ambiguïté. À changer en une ligne de traduction si l'utilisateur préfère.
  - **Le lien** est la racine du site, sans préfixe de langue : Caddy envoie chacun vers
    la sienne (comme `challengeUrl`).
- **Grille** : `squares()` reste celle des défis (🟩 ≥ 900, 🟨 ≥ 500, 🟥 dessous, ⬛ photo
  retirée). Une option `exact` y ajoute 🎯 pour une manche trouvée au 1er essai (le 1er
  essai exact termine la manche : `result.secondGuess === null`, 1 000 points). Seule la
  partie du jour l'active ; les défis gardent leur grille (décision de l'utilisateur).
- **Où** : seulement sur le résumé de la partie du jour (`game/game.html`, dans
  `app-day-summary`), donc seulement une partie complète. Pas sur la série libre : son
  partage, c'est le défi, qui existe déjà.
- **Partager ou copier** : comme le défi. Feuille de partage du système si le navigateur
  en a une (téléphones), sinon copie dans le presse-papiers et « Copié. » en
  `role="status"`. Un partage annulé par le joueur n'est pas une erreur ; une copie
  refusée affiche l'erreur générique. Cette logique existe déjà dans `challenge-page.ts`
  : elle est sortie dans une fonction `shareOrCopy(target, text)` de `game/share.ts`,
  réutilisée par la page défi et la partie du jour (pas une 2e copie).
  `share-challenge.ts` a deux boutons séparés (Partager / Copier) : il reste tel quel.
- **Pas de backend**, pas de donnée nouvelle, pas de traceur.

## Phase 1 : texte et grille
Status: Complete

- [x] `game/share.ts` : `squares(rounds, { exact = false } = {})` avec 🎯 pour un 1er essai
      exact quand `exact` ; `dailyShareUrl(origin)` (la racine) ;
      `shareOrCopy(target, text): Promise<'shared' | 'copied'>` (rejette seulement si la
      copie échoue, un partage annulé se résout `'shared'`).
- [x] `game/game-vocabulary.ts` : `dailyShareText(date, points, max, squares, url)` avec
      `@@game.dailyShareText` et le format `@@game.shareDateFormat` ; `formatDate`
      (`@angular/common`) en UTC avec le `LOCALE_ID` de la build.
- [x] Traductions FR dans `messages.fr.xlf` (`dd/MM/yyyy`, le texte du partage).
- [x] Tests unitaires (`share.spec.ts`, `game-vocabulary.spec.ts`) :
  - 🎯 seulement avec `exact` et seulement pour un 1er essai exact ; une manche à 950 en
    deux essais reste 🟩 ; ⬛ et 🟥 inchangés ;
  - la grille des défis reste sans 🎯 (test existant inchangé) ;
  - texte EN complet : « AgeGuessr · Nov 13, 2026 · 2340 / 3000\n🎯🟩🟥\nhttps://… » ;
  - date en UTC : `2026-01-01` reste le 1er janvier quel que soit le fuseau (suite lancée
    avec `TZ=America/Los_Angeles`) ;
  - `shareOrCopy` : partage si possible, copie sinon, partage annulé sans erreur, copie
    refusée qui rejette.
- [x] `challenge-page.ts` passe par `shareOrCopy` (son `canShare` inutilisé retiré) ; sa
      spec existante passe sans changement.

### Verification Plan
- `cd frontend && pnpm test` → tout passe.
- Résultat (2026-11-13) : `TZ=America/Los_Angeles pnpm test` → 66 fichiers, 454 tests verts.

### Phase Summary
`squares(rounds, { exact })`, `dailyShareUrl`, `shareOrCopy` dans `game/share.ts` ;
`dailyShareText(date, points, max, squares, url, locale)` dans `game-vocabulary.ts`, la
locale passée par l'appelant (`LOCALE_ID`), `formatDate` en `'UTC'`. Le texte du jour a
ses propres ids (`@@game.dailyShareText`, `@@game.shareDateFormat`). Les tests unitaires
sont tous dans `share.spec.ts` (rien ajouté à `game-vocabulary.spec.ts`).

## Phase 2 : bouton sur le résumé du jour
Status: Complete

- [x] `game/game.ts` + `game.html` : bouton « Partager mon résultat » (`GAME_TEXT.shareResult`,
      icône `LucideShare2` comme le défi) dans le résumé, avant « Comparer avec vos amis » ;
      « Copié. » en `role="status"` après une copie ; erreur générique si la copie échoue.
      Visible en invité comme connecté.
- [x] Tests :
  - **unitaire** (`game.spec.ts`, `SHARE_TARGET` remplacé) : bouton absent tant que la
    partie n'est pas finie ; feuille de partage avec le bon texte quand elle existe ;
    sinon copie et « Copied. » ; copie refusée → erreur ; une photo retirée → ⬛ ;
  - **interface** (`e2e/component/game.spec.ts`, et `e2e/component-fr/game.spec.ts` pour
    la date et le texte français, seul endroit où la build FR est testée) :
    `navigator.share` remplacé par `addInitScript` qui garde le texte ; partie finie
    mockée → le texte reçu est exactement celui attendu (emojis compris).
- [x] Capture : non nécessaire (un bouton de plus dans une rangée existante, déjà
      couverte par le test d'interface).

**Catégories omises** : pas d'intégration, de fonctionnel, d'architecture ni de mutation
backend (aucun code backend) ; pas de parcours QA e2e (le parcours « partie du jour »
n'est pas changé, le partage est un bouton terminal sans appel réseau).

### Verification Plan
- `cd frontend && pnpm test`, `pnpm exec ng lint`, `pnpm exec prettier --check .` → tout passe.
- `pnpm exec ng build --localize` puis « Partager mon résultat » et `dd/MM/yyyy` présents
  dans `dist/frontend/browser/fr`.
- Playwright : en CI seulement (WSL), vert sur la PR.
- Résultat (2026-11-13) : 458 tests verts dans 4 fuseaux (`TZ=Europe/Paris`,
  `America/Los_Angeles`, `Pacific/Kiritimati` +14, `Pacific/Pago_Pago` −11) ; lint et
  Prettier OK ; build FR : « Partager mon résultat » et `dd/MM/yyyy` présents. Les specs
  Playwright passent `tsc` ; leur exécution se fera en CI.

### Phase Summary
**Bug attrapé** : `formatDate('2026-11-13', …, 'UTC')` lit une date seule comme minuit
*local*, donc le 12 en UTC à l'est de Greenwich. La suite de la phase 1, lancée à Los
Angeles, ne pouvait pas le voir. Correctif : `new Date(`${date}T00:00:00Z`)`. D'où les
4 fuseaux ci-dessus, à refaire si ce code change.
`Game` : `shareResult(game)`, signal `shared` (`'shared' | 'copied' | 'failed' | null`),
« Copié. » en `role="status"`, erreur en `role="alert"`. `SHARE_TARGET` fourni par un faux
dans `game.spec.ts`. Playwright : `mockPlayedDay`, `captureShare` et `sharedTexts` dans
`e2e/component/game-mocks.ts` (feuille de partage remplacée par `addInitScript`) ; le lien
attendu vient du `baseURL` du projet.

## Phase 3 : documentation, revues et PR
Status: Not started

- [ ] `docs/roadmap.md` : cocher « Partage de résultat façon Wordle », ajouter la
      vérification en prod (téléphone : feuille de partage ; ordinateur : copie, coller
      dans un message).
- [ ] `docs/decisions/architecture.md` : une ligne près de `SHARE_TARGET` (texte du jour,
      🎯 réservé au jour, `shareOrCopy`).
- [ ] Revues `architecture-reviewer` et `code-reviewer` (imposé par `/pr` ; contenu
      partagé hors du site : vérifier qu'aucune réponse ne fuit), puis `/pr`.

### Verification Plan
- `pnpm test` et `./mvnw test` passent ; revues sans 🔴 ni 🟠 ouvert ; CI verte.

### Phase Summary
_(write when phase completes)_

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

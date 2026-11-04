# Refonte visuelle du frontend

Le site en production paraît brouillon : pas de hiérarchie, des composants recopiés à la
main, un menu à plat qui casse sur mobile. Ce plan refait la mise en page, l'UX, les
couleurs et la navigation. On valide d'abord des maquettes statiques, puis on pose un
socle de composants et on migre les écrans zone par zone. Plusieurs PR vers `develop`,
une par phase de code. Pas de PR empilées : chaque branche part de `develop` une fois la
précédente mergée.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Constat (captures prod du 2026-11-04, `/fr/`, 1440×900 et 390×844)

- **En-tête mobile cassé** : « Se connecter » passe par-dessus le wordmark.
- **Menu à plat** : jeu libre, connexion, inscription, langue et thème sont sur une
  seule rangée, au même poids visuel. Pas de menu de compte, pas de menu mobile.
- **Deux sélecteurs de langue concurrents** : « English » dans l'en-tête (langue du
  site) et « Indices en English / Français » sous le jeu.
- **Écran de jeu sans point focal** : une colonne étroite de 544 px sur desktop, une
  photo dans une carte avec des bandes noires, le libellé « Âge » collé au champ, des
  flèches de champ numérique natives, le lien « Enquête libre » en doublon de l'en-tête.
- **Verdict peu lisible** : les points par essai sont alignés à droite, loin de leur
  libellé. Le score de la manche n'est pas mis en valeur, et le crédit photo est noyé.
- **Couleurs** : les boutons et les sélections indigo saturé jurent sur le fond brun.
  Le filtre « vieille photo » est presque invisible, et le thème clair ne suit pas la
  préférence système (capture `colorScheme: light` rendue en sombre).
- **Code** : aucun composant d'UI partagé (`shared/` n'a que de l'i18n et des erreurs),
  24 boutons primaires recopiés avec des variantes (`opacity-50`, `opacity-60`), des
  marges choisies au cas par cas.
- **Bug de données vu en passant (hors périmètre)** : des indices musique collent deux
  mots, comme « Charles Aznavouret divers artistes » ou « George Michaelet Elton John ».
  `scripts/hints/common.py:116` joint le texte des cellules avec `""` et perd le `<br>`
  entre les deux. À corriger dans une PR `fix/` à part, avec une réimportation des
  indices concernés.

## Décisions

- **Captures** : jamais de suite Playwright en local (elle fait planter WSL). Le script
  `docs/decisions/mockups/shoot.mjs` (créé en Phase 1) lance un seul Chromium headless,
  une page à la fois, sans serveur de dev, avec `timeout 240`. Il vise des fichiers
  statiques (maquettes) ou l'URL de prod.
- **Maquettes** : HTML statique autonome dans `docs/decisions/mockups/`, avec les
  vraies polices (Fraunces, Inter) et les tokens de `styles.css` pour la direction qui
  les garde. Ce sont des documents de décision, pas du code de production.
- **Socle** : `@angular/cdk` (Menu, Dialog, Overlay, A11y) et nos propres composants
  dans `frontend/src/app/shared/ui/`. **Pas de Spartan** : il est lui-même construit sur
  le CDK et ajoute luxon, clsx et tw-animate-css, pour environ 8 composants qu'on
  restylerait de toute façon entièrement. Pas d'Angular Material ni de PrimeNG : leur
  look écraserait l'identité.
- **Direction A, « Archives soignées »** (validée le 2026-11-04) : détail et tokens dans
  `docs/decisions/design.md`, « Refonte visuelle (2026-11) ».

## Phase 1 : Maquettes (branche `chore/design-mockups`)
Status: Complete

- [x] Captures prod des écrans principaux (jeu, verdict, jeu libre, inscription,
      connexion, desktop et mobile), scratchpad de la session du 2026-11-04
- [x] `docs/decisions/mockups/shoot.mjs` : le script de captures minimal décrit plus haut
- [x] Maquette direction A, « Archives soignées » : on garde l'identité, avec une vraie
      hiérarchie, la photo en tirage avec légende et une palette d'accents revue
- [x] Maquette direction B, « Contemporain épuré » : neutre, la photo en grand, un accent
      plus franc
- [x] Chaque maquette couvre : en-tête desktop et mobile (menu ouvert), 1er essai,
      sens et choix de piste, 2e essai avec indice, verdict, rapport final, choix du jeu
      libre, inscription
- [x] Captures des maquettes via `shoot.mjs`, relues par l'agent (débordements, contraste)
- (Note : le 3e essai, le jeu libre et l'inscription n'existent qu'en desktop ;
      crédits des photos dans `mockups/README.md`)
- [x] Validation par l'utilisateur (2026-11-04) : **direction A**. Langue des pistes
      dans le menu + bascule FR/EN sur la coupure : oui. Vocabulaire enquête : gardé
- [x] `docs/decisions/design.md` : section « Refonte 2026-11 » qui renvoie aux maquettes
      et note les tokens modifiés
- [x] Contraste des nouveaux tokens vérifié par un calcul WCAG ponctuel (tout passe, sauf
      `input-border` et `danger` sur `surface-2`, d'où une règle d'usage). Le passage par
      `design-tokens.spec.ts` se fait en Phase 2, quand les tokens entrent dans `styles.css`

### Verification Plan
- `timeout 240 node docs/decisions/mockups/shoot.mjs` : sortie 0, une capture par écran
  maquetté
- `cd frontend && pnpm test` passe (tokens et contraste)

### Phase Summary
Fait le 2026-11-04. Le script de captures fonctionne (26 captures de maquettes en
environ 1 min, WSL stable). Il visait aussi la prod pour le constat. `pnpm test` passe :
la phase ne touche pas au code. La direction A est retenue et documentée dans
`design.md`. `architecture.md` signale que la décision « sans librairie » est
remplacée. Prochaine étape : Phase 2 sur `feature/ui-foundation`, depuis `develop` une
fois cette PR mergée.

## Phase 2 : Socle de composants (branche `feature/ui-foundation`)
Status: Complete

- [x] Ajouter `@angular/cdk` (même plage `^22.1.0` qu'Angular, 22.1.6 dans le lockfile) et
      son `overlay-prebuilt.css`
- [x] Tokens de la direction A dans `styles.css` (`surface-2`, `primary*`, `score`,
      `paper*`, ombres `overlay` et `print`), thème clair équivalent, paires ajoutées à
      `design-tokens.spec.ts`. Utilitaires `card` et `eyebrow`, filtre `archive-photo` de
      la maquette
- [x] Thème : préférence `dark` / `light` / `system`, `system` par défaut, qui suit
      `prefers-color-scheme` et ses changements (`Theme`, `LIGHT_SCHEME_QUERY`).
      `index.html` applique la même règle avant le démarrage d'Angular
- [x] `shared/ui` : directive `Button` (`button[appButton]`, `a[appButton]` : primary,
      secondary, ghost, danger ; md, sm ; block), `Field` + directive `TextInput`
      (`[appInput]`), `AgeInput` (ControlValueAccessor, spinbutton, − / +, flèches),
      `Segmented` (radiogroup au clavier), `Page` (narrow, default, wide)
- [x] Tests Vitest de chaque composant (rendu, variantes, aria, clavier)
- Déplacé en Phase 3 : `Menu` (CDK Menu) et `Sheet` mobile (CDK Dialog), construits avec
  l'en-tête, leur premier usage, plutôt qu'en abstraction sans utilisateur

### Verification Plan
- `cd frontend && pnpm test && pnpm lint && pnpm build` passent
- CI `frontend-test` verte sur la PR

### Phase Summary
Fait le 2026-11-04 sur `feature/ui-foundation`. `pnpm test` : 333 tests verts (dont 78
pour les tokens, le thème et `shared/ui`). Lint, format et build OK. Choix notables :
- Le bouton est une directive sur l'élément natif, pas un composant qui l'enveloppe : il
  garde `type`, `disabled`, `routerLink` et la soumission de formulaire sans rien
  réexposer. ESLint impose le préfixe `app`, donc des sélecteurs `appButton` et `appInput`.
- `Field` possède les ids, et le contrôle à l'intérieur les lit par injection
  (`TextInput`, `AgeInput`) : label, aide et erreur toujours reliés, sans id écrit à la main.
- `AgeInput` est un champ texte `inputmode="numeric"` avec `role="spinbutton"`, pas un
  `type="number"` (flèches natives minuscules, molette qui change la valeur). La plage
  reste aux validateurs du formulaire.
- Les écrans existants ne sont pas encore migrés : ils prennent les nouvelles couleurs
  automatiquement (mêmes noms de tokens), et les composants arrivent écran par écran à
  partir de la Phase 3.

## Phase 3 : En-tête et navigation (même branche, `feature/ui-foundation`)
Status: Complete

- [x] Menu de compte : **disclosure** `Dropdown` (`shared/ui/dropdown`), pas CDK Menu. Le
      panneau mélange des liens et des groupes radio, ce qu'un `role="menu"` n'accepte
      pas, et un menu prendrait les flèches dont les groupes radio ont besoin. Le bouton
      porte `aria-expanded`, le panneau suit le bouton dans le DOM (Tab y entre
      directement). Échap rend le focus au bouton, un clic dehors ou un lien suivi le ferme
- [x] `Sheet` mobile sur CDK Dialog (`Header.openSheet`) : focus piégé, Échap, fond
      cliquable, focus rendu au bouton Menu, fermeture à chaque `NavigationStart`
- [x] En-tête desktop : wordmark, Dossier du jour et Enquête libre (vocabulaire de
      `game-vocabulary.ts`, onglet actif souligné en or), Se connecter / S'inscrire et un
      bouton Préférences pour un invité, sinon le menu du pseudo (compte, admin,
      préférences, déconnexion)
- [x] Mobile : wordmark et bouton Menu seulement. Le panneau reprend les modes de jeu, le
      compte, les préférences et les boutons de connexion
- [x] `Preferences` (`features/layout/preferences`) : langue du site (la paire, l'autre
      langue reste un vrai lien `hreflang`), langue des indices et thème (`Segmented`)
- [x] Liens en doublon retirés sous les écrans de jeu. Le choix de la langue des indices
      y reste jusqu'à la Phase 4, qui le déplace sur la coupure. `ThemeToggle` supprimé
- [x] i18n : nouveaux textes `header.*` et `preferences.*` + `messages.fr.xlf`, unités
      obsolètes retirées (`header.account`, `header.free`, `header.nav`, `theme.to*`)
- [x] Specs e2e adaptées (header, account, hint-curation, legal EN/FR, journeys auth et
      french-account), exécutées par la CI seulement

### Verification Plan
- Tests Vitest de l'en-tête (invité, connecté, admin, mobile)
- `shoot.mjs` sur un build local ou sur la prod après release : pas de chevauchement à
  320 et 390 px

### Phase Summary
Fait le 2026-11-04. `pnpm test` : 343 tests verts, lint, format et build OK. Captures du
build servi en statique (`python3 -m http.server`, un seul Chromium) : en-tête desktop,
panneau Préférences, en-tête mobile à 360 px et panneau mobile conformes à la maquette,
sans chevauchement. Les specs e2e n'ont pas été lancées en local (WSL) : c'est la CI qui
les exécute.

## Phase 4 : Écrans de jeu (branche `feature/game-screens-redesign`)
Status: Complete

- [x] `CasePhoto` : le tirage monté (carton `paper`, légèrement incliné sur desktop),
      jamais recadré, au plus 45 % de la hauteur sur mobile pour garder la question et
      le champ à l'écran. Le crédit (auteur, licence, Commons) est sur le carton
- [x] Manche (`round`) : deux colonnes sur desktop, `CaseSteps` (une barre par affaire),
      sens en encart, pistes en cartes avec icône (`LeadIcon`) et aide, l'indice en
      coupure de journal, `Field` + `AgeInput`, focus déplacé à chaque étape comme avant
- [x] Verdict (`round-result`) : tampon « Affaire classée » (décoratif, le titre le dit
      aux lecteurs d'écran), nom en grand, score en or avec une barre par essai,
      coupure datée et sourcée, signalements
- [x] Rapport du jour (`day-summary`) : score en grand, vignettes, une ligne par affaire
- [x] Jeu libre (choix de la longueur en cartes), défi et partage (`Field`, `Button`),
      pages en `Page width="wide"`, `h1` réservé aux lecteurs d'écran pendant la partie
      (l'onglet actif de l'en-tête nomme déjà la page)
- [x] Bouton de signalement : déclencheur discret avec drapeau, ton `paper` sur la
      coupure, formulaire avec `Button`. Pas de Menu ni de Dialog : la liste des motifs
      reste un formulaire dans le flux, plus simple et déjà accessible
- [x] **Écart à la maquette** : pas de bascule FR/EN sur la coupure. La langue des
      indices est fixée au premier essai et ne vaut qu'à partir de la manche suivante
      (`hint-language.ts`) : une bascule sur l'indice affiché laisserait croire qu'on
      peut le traduire. Le choix reste dans les Préférences, `HintLanguagePicker` est
      supprimé des écrans de jeu
- [x] Traitement photo tranché (pas de duotone), roadmap cochée
- [x] Espaces explicites (`&ngsp;`) là où Angular collait les mots pour les lecteurs
      d'écran (« 593points », « Affaire 1Jane Doe »)
- [x] Budget du bundle initial : avertissement relevé de 500 à 550 kB. `@lucide/angular`
      est un seul module, donc esbuild met dans `main` toutes les icônes utilisées, même
      par les pages chargées à la demande : +45 kB bruts, +2 kB transférés (gzip)

### Verification Plan
- `pnpm test` (specs existantes du jeu adaptées, sans en perdre), CI e2e verte
- Captures prod après release, comparées à la maquette

### Phase Summary
Fait le 2026-11-04. `pnpm test` : 349 tests verts, lint, format et build OK. Captures du
build servi en statique avec l'API simulée par `page.route` (un seul Chromium) : 1er
essai, sens et pistes, indice et dernier essai, verdict, rapport, en desktop et à 390 px.
Conformes à la maquette A. Les specs e2e touchées (component-fr/game) ne tournent qu'en CI.

## Phase 5 : Auth, compte, légal, admin (branche `feature/secondary-screens-redesign`)
Status: Not started

- [ ] Inscription, connexion, mot de passe oublié et réinitialisation, vérification
      d'email : `Card` + `Field`
- [ ] Compte (pseudo, mot de passe, suppression en zone `danger`)
- [ ] Pages légales : typographie de lecture (largeur, interlignage, titres)
- [ ] Admin (curation, indices) : composants partagés, en restant en anglais
- [ ] Bandeau CGU harmonisé

### Verification Plan
- `pnpm test && pnpm lint && pnpm build`, CI verte

### Phase Summary
_(write when phase completes)_

## Phase 6 : Documentation
Status: Not started

- [ ] `docs/decisions/design.md` : la section Composants décrit le CDK et `shared/ui`
      (remplace « Tailwind pur, sans librairie »), et le traitement photo est tranché
- [ ] `docs/decisions/architecture.md` mis à jour s'il mentionne l'absence de librairie
- [ ] `docs/roadmap.md` : ligne « Refonte visuelle » ajoutée et cochée, duotone coché

### Verification Plan
- `grep -n "pas de librairie" docs/decisions/*.md` : ne renvoie plus de décision obsolète

### Phase Summary
_(write when phase completes)_

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

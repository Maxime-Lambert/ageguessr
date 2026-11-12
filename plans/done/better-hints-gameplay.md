# Mieux aider le 2e essai : chaud/froid, 3 pistes, indices expliqués

Branche `feature/better-hints-gameplay` (PR 1/2). Les joueurs disent que les indices
n'aident pas. On remplace le simple « plus jeune / plus vieux » par le sens plus une
tranche d'écart (1-5 / 6-15 / 16+ ans), et la saisie du 2e essai est limitée à cette
tranche. Le joueur choisit 3 catégories d'indice au lieu d'une (nombre réglable).
Célébrités est reformulée (« sur la photo, cette personne a l'âge qu'a X cette année »), les règles
expliquent la mécanique avec un exemple, et le résultat montre chaque indice avec son
année et « il y a N ans ».

Hors de cette PR (PR 2/2, plan séparé) : refonte des sources d'indices, classées selon les
vues annuelles de l'article de l'événement. On passe par Wikidata pour Sport, Techno/culture
pop (remplace Science) et Monde, et on reclasse Musique, Cinéma et Célébrités. Mesures du
spike du 2026-11-12, en faits « connus » par an : Monde EN 20-41, Monde FR 3-7, Science FR
1-3, Sport 0-4 avec les pages d'année actuelles.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions propres à cette feature

- **Chaud/froid avec le sens** (révise « Déroulé d'une manche », décision du 2026-11-12,
  demandée par l'utilisateur après les retours des joueurs). Après un 1er essai faux, le
  joueur apprend le sens et une tranche d'écart : `SLIGHTLY` (1-5 ans), `CLEARLY` (6-15),
  `FAR` (16 et plus). L'objection de 2026-11-02 portait sur une distance **sans** le sens,
  qui laisse deux zones dont la photo tranche presque toujours. Avec le sens, on obtient un
  seul intervalle de 5 ou 10 ans, que les indices aident à départager. Un mauvais 1er essai
  reste puni par ses propres points (16 ans d'écart, environ 100 points sur 500).
- **La tranche est calculée, jamais stockée.** On la dérive de `first_guess` et de l'âge à
  chaque lecture, comme `Direction` aujourd'hui. Les manches en cours au moment du
  déploiement passent donc au nouveau système sans migration.
- **La plage est calculée côté serveur, pour qu'il n'y ait qu'une règle.** `RoundView`
  envoie `direction`, `gap` (la tranche) et `range {min, max}`, bornés à 18-100. Le front
  limite la saisie à `range`. Le serveur ne refuse pas un 2e essai hors de la plage (18-100
  reste la seule règle) : validé par l'utilisateur, le joueur ne pénaliserait que lui-même.
- **Le score ne change pas** (`500 × e^(−écart/10)`). Le 2e essai rapportera plus en
  moyenne, ce qui est le but. À régler en jouant.
- **N pistes au choix** : `app.game.hints.per-round`, 3 par défaut. Le joueur choisit
  exactement `min(N, catégories offertes)` catégories distinctes. Avec 0 catégorie, il passe
  au 2e essai comme aujourd'hui. Chaque catégorie garde son tirage stable
  (`HintOffer.pick`, même hash), donc tous les joueurs qui choisissent Musique voient le
  même indice Musique.
- **Stockage des pistes : tableaux PostgreSQL** (migration `V20`). Dans `game_plays` et
  `free_series_plays`, `hint_category` et `hint_id` deviennent `hint_categories
  VARCHAR(16)[]` et `hint_ids UUID[]`, dans le même ordre. Les lignes existantes sont
  converties, avec `ARRAY[...]` ou un tableau vide. On mappe avec
  `@JdbcTypeCode(SqlTypes.ARRAY)` (Hibernate 6). Pourquoi pas une table fille : `RoundProgress`
  est un `@Embeddable` partagé par deux entités. Une `@ElementCollection` demanderait une
  table par entité et des `@AssociationOverride`, pour une liste de 3 éléments au plus, lue
  et écrite en bloc. **Ce qu'on perd** : la clé étrangère vers `hints`, qu'un tableau ne
  peut pas porter. C'est acceptable parce qu'un indice n'est jamais supprimé (un retrait
  passe par `REJECTED`) et que `HintPicker.shown` ignore déjà un id inconnu ou rejeté. Le
  `CHECK` sur les catégories est réécrit avec `<@ ARRAY[...]`.
- **Signalement par indice** : `hint_reported BOOLEAN` devient `reported_hint_categories
  VARCHAR(16)[]` (`V20`). Un `true` existant donne la catégorie de l'indice déjà montré.
  `ReportRoundCommand` reçoit `hintCategory`, obligatoire quand `target = HINT` et vérifié
  par le handler. On signale une fois par indice et par manche.
- **Reformulation de Célébrités, dans les données.** Migration `V21` sur les textes en base,
  sur le modèle de V17, et même changement dans `extract_wikidata.py` et `celebrities.json`.
  Sans ce dernier, les prochaines clés importées reviendraient à l'ancien texte : l'import
  n'écrase jamais une clé connue. FR « Personnalité née cette année-là : X, desc. » devient
  « Sur la photo, cette personne a l'âge qu'a X cette année (desc). » ; EN « Born that
  year: X, desc. » devient « On the photo, this person is the age X turns this year
  (desc). ». L'âge est **exact** : l'année de l'indice est l'année de la partie (ou de la
  série) moins l'âge, donc X a exactement cet âge pendant l'année. Avec « aujourd'hui », la
  date d'anniversaire aurait décalé d'un an (correction de l'utilisateur, 2026-11-12).
- **Explication de la mécanique** : un bandeau au-dessus des pistes et des règles
  réécrites, avec un exemple chiffré (« un fait d'il y a 40 ans → elle avait 40 ans sur la
  photo »). Aucun nombre de 1900 à 2099 dans l'exemple n'est lié à la manche.
- **Rien ne fuit avant la fin**, comme aujourd'hui. La tranche et la plage ne donnent pas
  l'âge exact. Les indices sortent sans leur année jusqu'au résultat.
  `GameFunctionalTest` le vérifie sur le JSON brut.

## Fichiers

Backend (`ageguessr-api/src/main/java/app/ageguessr/features/game/`) :
- `Direction.java` : inchangé. Nouveau `GapBand.java` (enum et `of(guess, age)`), nouveau
  `GuessRange.java` (record `min`/`max`, `of(guess, age)` borné à 18-100).
- `RoundProgress.java` : `hintCategories`, `hintIds` (listes), `reportedHintCategories`,
  `revealHints(List<HintCategory>, List<UUID>)`, `reportHint(HintCategory)`.
- `PlayedRound.java` : les accesseurs suivent.
- `HintOffer.java` : `pickAll(roundKey, categories, language)`, et la validation du choix
  (`required()`, nombre et catégories offertes).
- `GameProperties.java` : `hintsPerRound` (défaut 3, `@Min(1) @Max(6)`).
- `RoundView.java` : `gap`, `range`, `hintsToPick`, `hints: List<ShownHint>` (remplace
  `hint`).
- `RoundViews.java`, `RoundReveal.java` : `hints: List<GameHint>`, `reportedHints:
  List<HintCategory>` (remplacent `hint`, `hintReported`).
- `RevealHint/RevealHintCommand.java`, `RevealSeriesHint/RevealSeriesHintCommand.java` :
  `@NotEmpty @Size(max = 6) List<@NotNull HintCategory> categories`.
- `RevealHint/RevealHintHandler.java`, `RevealSeriesHint/RevealSeriesHintHandler.java` :
  choix multiple, erreurs `HINT_COUNT` (mauvais nombre ou doublon) et
  `CATEGORY_UNAVAILABLE` existante.
- `GameErrors.java` : `wrongHintCount()`.
- `ReportRound/ReportRoundCommand.java`, `ReportRound/ReportRoundHandler.java` :
  `hintCategory`.
- `GameTestSupportController.java` (`app.ageguessr`) : une 3e catégorie (`SPORT`) pour les
  parcours e2e.
- `src/main/resources/db/migration/V20__multiple_hints_per_round.sql`,
  `V21__reword_celebrity_hints.sql`.
- `src/main/resources/application*.yml` : `app.game.hints-per-round: 3`.

Scripts et données :
- `scripts/hints/extract_wikidata.py` (gabarit Célébrités),
  `ageguessr-api/src/main/resources/hints/celebrities.json` (textes régénérés, mêmes
  `dedupeKey`).

Frontend (`frontend/src/app/features/game/`) :
- `models.ts` : `GapBand`, `GuessRange`, `RoundView.gap/range/hintsToPick/hints`,
  `RoundReveal.hints/reportedHints`.
- `game-api.ts`, `series-api.ts`, `rounds-screen.ts` : `hint(date, position, categories[])`,
  signalement avec `hintCategory`.
- `round/round.ts|html` : texte de la tranche, saisie bornée à `range` (validateurs
  dynamiques), sélection multiple (`aria-pressed`, compteur « 2 / 3 », bouton de validation),
  bandeau d'explication, liste des indices.
- `round-player/round-player.ts` : `hintPicked` émet `HintCategory[]`.
- `round-result/round-result.ts|html` : un bloc par indice (année, « il y a N ans »,
  source, signalement par indice).
- `report-button/report-button.ts|html` : `hintCategory` en entrée.
- `game-vocabulary.ts` : `gapText(direction, band, range)`, nouvelles règles, aide des
  pistes.
- `how-to-play/*` : règles avec l'exemple.
- `frontend/src/locale/messages.fr.xlf` : toutes les nouvelles chaînes.
- `frontend/e2e/component/game-mocks.ts`, `component/*.spec.ts`, `component-fr/game.spec.ts`,
  `journeys/daily-game.spec.ts`.

Docs : `docs/decisions/architecture.md` (« Déroulé d'une manche », « État d'une partie »,
« Signalement… »), `docs/roadmap.md` (nouvelle ligne, et vérifs prod à faire à la main).

## Phase 1 : Tranche d'écart et plage (backend)
Status: Complete

- [x] `GapBand` : `SLIGHTLY` (1-5), `CLEARLY` (6-15), `FAR` (16+) ; `of(guess, age)`
      refuse un essai exact
- [x] `GuessRange.of(guess, age)` : côté du sens, bornes de la tranche, bornées à 18-100 ;
      jamais vide (si l'âge réel dépasse 100, `{100, 100}`)
- [x] `RoundView` : `gap` et `range` à côté de `direction`, renseignés par
      `RoundViews.inProgress`
- [x] Unitaires : bornes 5/6 et 15/16 des deux côtés, bornage à 18 et à 100, essai exact
      refusé
- [x] Fonctionnel (`GameFunctionalTest`, série) : `gap` et `range` dans le JSON brut après
      le 1er essai, absents avant et après un essai exact, toujours aucun âge

### Verification Plan
- `cd ageguessr-api && ./mvnw test -Dtest='GapBand*,GuessRange*,GameFunctionalTest,*Series*Functional*'`
  → BUILD SUCCESS

### Phase Summary
`GapBand` (1-5 / 6-15 / 16+) et `GuessRange` (bornée à 18-100, jamais vide) sont dans
`features/game/`, et `RoundView` expose `gap` et `range` à côté de `direction`. Un test
parcourt toutes les paires (essai, âge) de 18 à 100 et vérifie que la plage contient
toujours la réponse. Vérification : les tests ciblés passent (`GapBandTest`,
`GuessRangeTest`, `GameFunctionalTest`, `SeriesFunctionalTest`, `RoundViewsTest`).

## Phase 2 : Plusieurs pistes par manche (backend + migration)
Status: Complete

- [x] `V20__multiple_hints_per_round.sql` : tableaux sur `game_plays` et
      `free_series_plays`, conversion des lignes existantes, `CHECK` réécrits, anciennes
      colonnes supprimées, commentaires en français comme dans V13
- [x] `RoundProgress` / `PlayedRound` : listes, `revealHints`, `reportHint(category)`,
      `step()` inchangé dans le fond (liste vide = `AWAITING_HINT`)
- [x] `GameProperties.hintsPerRound` + yml
- [x] `HintOffer` : nombre requis, validation, `pickAll`
- [x] Handlers de pistes (jour et série), commandes, `GameErrors.wrongHintCount`
- [x] `RoundView.hintsToPick` / `hints`, `RoundReveal.hints` / `reportedHints`
- [x] Signalement par indice (`hintCategory`, `NOTHING_TO_REPORT` si la catégorie n'a pas
      été montrée ou si l'indice est rejeté)
- [x] `GameTestSupportController` : 3e catégorie
- [x] Unitaires : `HintOffer` (nombre requis quand moins de N catégories, doublons,
      catégorie non offerte, même tirage qu'avant pour une catégorie donnée),
      `RoundProgress` (révélation unique, signalement une fois par catégorie)
- [x] Intégration : V20 sur une base contenant une manche avec indice, une sans, une
      signalée (Testcontainers), puis lecture JPA des tableaux
- [x] Fonctionnel : choix de 3 → 3 indices sans année ; 2 ou 4 → 400 `HINT_COUNT` ; doublon
      → 400 ; catégorie absente → `CATEGORY_UNAVAILABLE` ; 2 catégories offertes → il en
      faut 2 ; résultat avec 3 indices, années et sources ; signalement de chaque indice
      une fois ; série et défi pareils ; deux joueurs, même catégorie → même indice

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → BUILD SUCCESS (suite complète : la migration touche
  tous les tests en base)

### Phase Summary
V20 transforme `hint_category`/`hint_id`/`hint_reported` en tableaux
(`hint_categories`, `hint_ids`, `reported_hint_categories`), avec ces contraintes : même
longueur, catégories connues, signalées ⊂ montrées. `RoundProgress` mappe les tableaux en
`List` (`@JdbcTypeCode(SqlTypes.ARRAY)`, Hibernate 7.4). Le nombre de pistes se règle par
`app.game.hints.per-round` (`HintProperties`, 3), et `HintOffer.pickAll` valide le choix
(`HINT_COUNT`, `CATEGORY_UNAVAILABLE`) et range les indices dans l'ordre des catégories.
Côté API : `RoundView.hintsToPick` et `hints` ; `RoundReveal.hints` et `reportedHints`.
`ReportRoundCommand.hintCategory` est facultatif quand la manche n'a montré qu'un indice
(anciennes manches, page chargée avant la release), sinon il vaut `HINT_CATEGORY_REQUIRED`.
Nouveau `HintMigrationsIntegrationTest` : Flyway monte un schéma à part jusqu'à V19, on y
insère des lignes à l'ancien format, puis on applique V20, et la même méthode servira pour
V21. Écart avec le plan : la propriété s'appelle `app.game.hints.per-round` et non
`app.game.hints-per-round`. Vérification : `./mvnw test`, 1 201 tests, 0 échec.

## Phase 3 : Reformulation de Célébrités (données)
Status: Complete

- [x] `extract_wikidata.py` : nouveau gabarit FR et EN
- [x] `celebrities.json` : textes réécrits, `dedupeKey` inchangées (régénérer depuis le
      cache `.cache/`, sans appel réseau ; vérifier le diff)
- [x] `V21__reword_celebrity_hints.sql` : `UPDATE hints` pour `category = 'CELEBRITIES'`
      avec `regexp_replace` sur les deux préfixes, idempotent, `≤ 512` caractères
- [x] Intégration : V21 réécrit une ligne à l'ancien format et ne touche pas une ligne
      déjà réécrite ni une autre catégorie
- [x] Unitaire ou test d'import : aucun texte Célébrités du JSON ne contient l'ancien
      préfixe ni un nombre de 1900 à 2099

### Verification Plan
- `grep -c "Personnalité née cette année-là\|Born that year" ageguessr-api/src/main/resources/hints/celebrities.json`
  → 0
- `cd ageguessr-api && ./mvnw test -Dtest='*Hint*'` → BUILD SUCCESS

### Phase Summary
Les formulations ne changent que le **préfixe** : dans le texte stocké, rien ne sépare de
façon fiable le nom de la description, qui contient des virgules.
- FR : « Sur la photo, cette personne a l'âge qu'a cette année X, desc. »
- EN : « On the photo, this person is as old as this celebrity turns this year: X, desc. »

C'est un écart avec la forme « (desc) » prévue. V21 réécrit les lignes qui commencent par
l'ancien préfixe, ce qui la rend idempotente : une correction manuelle n'est pas touchée.
`celebrities.json` a été transformé par remplacement du préfixe, pas régénéré : mêmes clés,
format conservé, 760 lignes. `extract_wikidata.py` est aligné. Tests ajoutés : V21 dans
`HintMigrationsIntegrationTest`, et `theShippedCelebritiesGiveTheAgeDirectly` dans
`ImportHintsIntegrationTest`. Vérification : le grep de l'ancien préfixe donne 0, et les
deux tests passent.

## Phase 4 : Interface
Status: Complete

- [x] `models.ts`, APIs, `rounds-screen.ts`
- [x] `round` : texte de la tranche (« Plus vieux · de 6 à 15 ans de plus que 40 »),
      saisie bornée à `range`, début du 2e essai au bord de la plage le plus proche du
      1er essai
- [x] `round` : sélection de N pistes au clavier, focus sur la 1re, bouton désactivé tant
      que le compte n'y est pas, message quand moins de N catégories sont offertes
- [x] `round` : bandeau d'explication au-dessus des pistes, puis les N indices façon
      coupure de journal
- [x] `round-result` : un bloc par indice (année · il y a N ans · source · signaler)
- [x] `report-button` : `hintCategory`
- [x] Règles (`game-vocabulary`, `how-to-play`) : tranche, 3 pistes, exemple chiffré
- [x] `messages.fr.xlf` : toutes les nouvelles chaînes traduites
- [x] Unitaires/interface (Vitest) : `round` (texte de la tranche, validateurs bornés,
      sélection exactement N, émission), `round-result` (N indices, signalement par
      indice), `game-vocabulary.gapText`
- [x] Composants Playwright (`e2e/component`, `component-fr`) : mocks mis à jour, parcours
      jeu en EN et FR, signalement d'un indice parmi 3 (lancés par la CI, pas en local :
      la suite fait planter WSL)
- [x] `journeys/daily-game.spec.ts` mis à jour (non lancé en CI, comme les autres
      parcours)

### Verification Plan
- `cd frontend && pnpm test` → tous verts
- `cd frontend && pnpm lint && pnpm build` → sans erreur (contrôle aussi les ids i18n
  manquants dans le xlf)
- Une seule capture `shoot.mjs` de l'écran des pistes et de l'écran de 2e essai, si
  besoin

### Phase Summary
Les pistes se choisissent par boutons bascule (`aria-pressed`) dans un `role=group`, avec un
compteur (« 2 of 3 picked », « Choix : 2 sur 3 ») et un bouton « Open these leads ». Une fois
N pistes choisies, les autres boutons sont désactivés. La tranche s'affiche ainsi : « Older
than 30, by 6 to 15 years » / « Plus de 30 ans, de 6 à 15 ans d'écart », avec « Between 36
and 45 » en dessous. La saisie et les validateurs suivent `range`, et repassent à 18-100 pour
la manche suivante, puisque c'est le même composant. Le 2e essai démarre au bord de la plage
le plus proche du 1er. L'écran de résultat montre un bloc par indice, chacun avec son bouton
de signalement (`hintCategory`). Les règles ont 4 points, dont `ruleHints` avec l'exemple
chiffré. Pas de capture `shoot.mjs`, faute d'en avoir eu besoin. Vérification : `pnpm test`
(423 verts), `pnpm lint`, `pnpm format:check` et `pnpm build` passent. Specs Playwright
mises à jour (mocks, `pickLeads`) mais non lancées en local.

## Phase 5 : Docs, revues, PR
Status: Complete

- [x] `architecture.md` : « Déroulé d'une manche » réécrit (historique : plus/moins du
      2026-11-02, puis chaud/froid avec le sens le 2026-11-12, et pourquoi), « État d'une
      partie » (tableaux, N pistes), « Signalement » (par indice), RoundView
- [x] `roadmap.md` : ligne de la PR, et vérifs prod à faire à la main (tranche, 3 pistes,
      signalement d'un indice parmi 3, textes Célébrités après V21)
- [x] Agents `architecture-reviewer` et `code-reviewer` (endpoints publics et données
      joueur), retours traités
- [x] `./mvnw test` + `pnpm test` verts, puis `/pr` vers `develop`

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → BUILD SUCCESS ; `cd frontend && pnpm test` → verts
- `gh pr checks` → tout vert sur la PR

### Phase Summary
Doc et roadmap à jour, revues traitées (voir le Final Recap). Les deux suites passent, et la PR
est ouverte vers `develop`.

## Tests : catégories retenues (`docs/testing-strategy.md`)

- **Unitaire** : oui, back (bornes des tranches et de la plage, validation du choix,
  `RoundProgress`) et front (composants et vocabulaire).
- **Intégration** : oui, deux migrations de données (V20 convertit des parties réelles,
  V21 réécrit des textes) et le mappage JPA des tableaux.
- **Fonctionnel** : oui, le contrat public de `/api/game/**` et des séries change, et la
  non-fuite de l'âge et de l'année se vérifie sur le JSON brut.
- **Interface** : oui, nouveaux écrans de choix et de résultat, en EN et en FR.
- **QA e2e** : parcours `daily-game` mis à jour, puisque le parcours critique change, mais
  il n'est lancé ni en CI ni en local (mémoire « No local Playwright »). Il rejoint la
  liste des vérifs à faire à la main.
- **Architecture** : aucune nouvelle règle. La dépendance `game → hints` reste à sens
  unique, et les tests ArchUnit existants le vérifient.
- **Mutation** : pas de travail dédié. Elle tourne sur la PR `develop → main`, et les
  bornes des tranches (`5`/`6`, `15`/`16`) sont les cibles évidentes, couvertes par les
  unitaires de la phase 1.

## Edge cases

- 1er essai exact : ni tranche, ni plage, ni pistes (inchangé).
- Moins de N catégories offertes : il faut toutes les choisir. Aucune : 2e essai direct.
- Essai près des bornes : 98 et « un peu plus vieux » donnent 99-100 ; âge réel au-delà de
  100 (la base l'autorise jusqu'à 120) : plage `{100, 100}`.
- Indice rejeté entre le choix et le résultat : il disparaît de la liste, et son
  signalement renvoie `NOTHING_TO_REPORT`.
- Manches en cours au déploiement : `AWAITING_HINT` passe au choix multiple ;
  `AWAITING_SECOND_GUESS` garde son indice unique converti, avec la tranche en plus.
- Anciennes manches finies : résultat avec 1 indice, signalement conservé.
- Double clic ou deux onglets sur la validation des pistes : verrou optimiste → 409
  `WRONG_STEP` (inchangé).
- Célébrités : une série jouée l'année après sa création décale d'un an (série de 30 jours au plus, accepté).

## Final Recap
Revues : aucun point bloquant.
- `architecture-reviewer` : trois commentaires ajoutés (garde de `pickAll`, `yearsAgo`
  partagé par tous les indices, lien entre `CATEGORIES` de l'e2e et `per-round`). Gardés
  sciemment : les deux commandes identiques (`RevealHintCommand` et
  `RevealSeriesHintCommand`, comme les autres paires jour/série du projet) et la formulation
  EN « this celebrity » (remplacement du préfixe seul).
- `code-reviewer` : la doc corrigeait mal le cas des bords. La plage peut faire moins de
  5 ans aux bornes 18-100, ce qui n'est pas une fuite.

Backend : `./mvnw test`, 1 203 tests, 0 échec. Front : `pnpm test` (423), `pnpm lint`,
`pnpm format:check` et `pnpm build` passent. Specs Playwright mises à jour, non lancées en
local.

## Deployment Plan
1. Merger la PR dans `develop`, puis la release `develop → main` (merge commit, voir la
   mémoire « Release PRs use merge commits »).
2. Au démarrage, Flyway applique V20 (conversion des parties en tableaux, sans perte : les
   manches en cours gardent leur indice) et V21 (textes Célébrités). Pas de réimport des
   indices : V21 réécrit déjà les lignes en base.
3. Le front et le back partent ensemble. Une page déjà ouverte avec l'ancien bundle
   enverra `{category}` et recevra un 400 : un rechargement suffit. Le signalement sans
   `hintCategory` reste accepté pour une manche à indice unique.
4. Vérifier à la main en prod la liste « Pistes et chaud/froid » de `docs/roadmap.md`.
5. Ensuite : planifier la PR 2/2 (sources d'indices classées selon les vues annuelles).

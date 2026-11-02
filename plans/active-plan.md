# Partie du jour jouable : guess, points et écran de jeu

Rendre la partie du jour jouable de bout en bout, avec ou sans compte. Pour chaque
photo : un 1er essai, puis « plus jeune / plus vieux », un indice choisi par catégorie,
un 2e essai, et enfin le résultat. Le résultat montre l'âge, les points, l'identité, le
crédit de la photo et la source de l'indice ; un récapitulatif clôt la journée. Le
tirage quotidien est activé en prod. Hors scope : signalements, historique et profil,
amis, partage, duotone, page d'accueil marketing.

## For Future Agents
Au fil du travail : cocher les cases `- [x]`. Quand une phase est finie, passer son
statut à `Complete` et écrire son **Phase Summary**. Lancer le **Verification Plan** de
la phase et noter le résultat avant de passer à la suivante. Ce fichier est la source de
vérité, pas la conversation.

## Contexte pour repartir à froid
- Branche : `feature/daily-guess`, créée depuis `develop` après le merge de la PR #29
  (sans upstream : `git push -u origin feature/daily-guess`). Ne jamais commiter sur
  `develop` ou `main`.
- Le tirage existe déjà (`plans/done/daily-draw.md`) :
  - `DrawDailyGameHandler.drawIfMissing(date)` tire la partie si elle manque ;
  - `DailyRoundRepository.findStatesOf(date)` calcule `PLAYABLE` / `NEUTRALIZED` à la
    lecture ;
  - `app.game.daily.draw-enabled` est **faux par défaut**, et cette PR le passe à vrai.
- **`RoundState.photoId` est l'id interne : il ne sort jamais de l'API**. Seul le
  `publicId` sort, via `/api/images/{publicId}`.
- `SecurityConfig` a déjà `PUBLIC_GAME_GET_PATHS` avec un commentaire : chaque endpoint
  de jeu y est ajouté, sinon le jeu sans compte tombe en 401.
- `RateLimitFilter` ne couvre que `/api/auth/` ; `RateLimiter` (Redis) est réutilisable.
- Indices (V11, `features/hints`) :
  - année de référence = **année du jour − `age_at_photo`** ;
  - au moins 5 indices APPROVED par année, catégorie et public (`ALL` + `FR` ou `EN`) ;
  - `CELEBRITIES` : `subject_qid` ne doit jamais être la personne à deviner.
- UI en anglais (`docs/decisions/design.md`). Les indices existent en FR et en EN.
- Voix « enquête » à l'essai : tout le vocabulaire de jeu vient d'**un seul fichier**,
  jamais en dur dans les templates (design.md, « Règle d'implémentation »).
- Photos : filtre CSS de vieillissement obligatoire (design.md, « Traitement des
  photos ») ; le duotone reste non tranché, hors scope.
- `/privacy` doit refléter le code : cookies, durées de conservation. Le commentaire en
  tête de `privacy.html` le rappelle.

## Décisions validées avec l'utilisateur (2026-11-02)
- **Déroulé** : 1er essai → « plus jeune » / « plus vieux » (aucune distance, pas de
  « brûlant ») → choix d'une catégorie d'indice → 2e essai. Saisie de 18 à 100.
- **Points** : jusqu'à 500 par essai, `round(500 × e^(−écart/10))`, à régler par
  playtest. La manche vaut la somme des deux essais (/1 000) ; un 1er essai exact
  termine la manche à 1 000. Les points du 1er essai ne s'affichent qu'au résultat. Les
  manches neutralisées sortent du score du jour.
- **Périmètre** : backend **et** écran de jeu (écran principal, indice, résultat avec
  crédit, enchaînement des 3 photos, récapitulatif), puis activation du tirage.
- **Architecture** : `game → hints` autorisé, comme `game → people` ; jamais l'inverse.
- **État d'une partie** : en base, table `game_plays`, rattachée au compte si le joueur
  est connecté, sinon à un id aléatoire dans un cookie invité httpOnly strictement
  nécessaire. Les parties invitées sont purgées au bout de 7 jours. Limite connue, comme
  Wordle : rien n'empêche de rejouer en navigation privée.
- **Même indice pour tous** pour une même (jour, manche, catégorie, langue).

## Décisions d'implémentation (validées par l'utilisateur le 2026-11-02)
- **Le jeu est à `/`**, comme un daily puzzle : on arrive et on joue. La démo de tokens
  de `home` disparaît. Son pitch (« Get warmer… from the year they were born ») est
  devenu faux. La page d'accueil marketing reste un item à part de la roadmap.
- **Langue des indices** : `fr` si `navigator.language` commence par `fr`, sinon `en`.
  Elle est envoyée avec le 1er essai et fixée dans `game_plays` pour la manche. Le reste
  de l'UI reste en anglais. Le nom révélé suit la même langue (`name_fr` ou `name_en`,
  avec repli sur l'autre).
- **Endpoints** (tous publics, JWT facultatif) :
  - `GET /api/game/today` : la date, et pour chaque manche :
    - position, statut, URL de l'image et état du joueur ;
    - le résultat complet, seulement si la manche est finie ;
    - le score du jour.

    Jamais d'âge, d'identité ni de crédit pour une manche non finie.
  - `POST /api/game/{date}/rounds/{position}/first-guess` `{age, language}` →
    direction et catégories disponibles, ou résultat si l'essai est exact.
  - `POST …/hint` `{category}` → texte de l'indice.
  - `POST …/second-guess` `{age}` → résultat.
  - Erreurs métier (409) :
    - `GAME_CLOSED` : la date n'est pas aujourd'hui ;
    - `ROUND_NEUTRALIZED` ;
    - `WRONG_STEP` : étape déjà jouée, ou pas encore permise ;
    - `CATEGORY_UNAVAILABLE`.
- **Machine à états d'une manche** : `NOT_STARTED → AWAITING_HINT → AWAITING_SECOND_GUESS
  → FINISHED`, ou directement `FINISHED` sur un 1er essai exact. L'indice est
  **obligatoire** avant le 2e essai, sauf si aucune catégorie n'est disponible : on
  passe alors directement à `AWAITING_SECOND_GUESS`.
- **Catégories disponibles** : celles qui ont au moins `app.game.hints.min-per-category`
  (5) indices APPROVED pour (année de référence, catégorie, `ALL` + langue). Un indice
  `CELEBRITIES` dont le `subject_qid` est la personne à deviner est exclu.
  - Fuite marginale assumée : une catégorie masquée dit que l'année est en dehors de sa
    couverture (par exemple pas de Célébrités avant 1932, donc plus de 94 ans).
  - Elles ne sont envoyées qu'**après** le 1er essai.
- **Indice déterministe** : parmi les indices éligibles triés par id, on prend l'indice
  d'index `hash(date, position, catégorie, langue) mod n`. On stocke son id dans
  `game_plays.hint_id` (`ON DELETE SET NULL`), pour qu'un joueur revoie toujours le même.
- **Points stockés à l'essai**, pas recalculés : régler la formule plus tard ne réécrit
  pas les parties passées.
- **Minuit** : on ne peut jouer que la date du jour. Une manche commencée à 23:59 et
  finie après minuit est refusée (`GAME_CLOSED`), et le front recharge la nouvelle
  partie. Accepter la veille pour une manche commencée serait possible plus tard.
- **Joueur connecté qui a aussi un cookie invité** : le compte gagne. Les parties
  invitées ne sont pas fusionnées dans le compte à la connexion (hors scope, noté pour
  l'historique en Semaine 5).
- **Cookie invité `ageguessr_guest`** :
  - UUID aléatoire, httpOnly, `Secure` en prod, `SameSite=Lax`, `Path=/api/game`,
    30 jours ;
  - posé au 1er POST s'il manque ; le GET sans cookie renvoie des manches
    `NOT_STARTED` ;
  - `SameSite=Lax` bloque les POST cross-site : sans jeton CSRF, au pire un site tiers
    ne pourrait que jouer à la place d'un invité. À faire valider par `code-reviewer`.
- **Limite par IP** sur `POST /api/game/**` (`app.rate-limit.game.*`, 60 / min), dans
  un second `RateLimitFilter` ou en généralisant l'actuel.
- **Purge** des lignes invitées dont `game_date` < aujourd'hui − 7 jours, chaque jour
  (`@Scheduled`). Les lignes d'un compte disparaissent avec lui (`ON DELETE CASCADE`).
- **Identité révélée** : nom + lien Wikipedia sans rien stocker,
  `https://www.wikidata.org/wiki/Special:GoToLinkedPage/<lang>wiki/<QID>`.
  *Hypothèse à vérifier en phase 1 à la main* : ce lien redirige vers l'article, ou
  reste sur Wikidata quand l'article manque.
- **Crédit** : auteur (`author_text`, sinon « Unknown author »), licence avec son lien,
  et lien vers la page Commons. L'indice est réaffiché avec son année et sa source (CC
  BY-SA).
- **Couleurs** : `hot` et `cold` (proximité) n'ont plus d'usage. Le sens s'affiche avec
  une flèche et un texte, en couleurs neutres. Les tokens restent, et `design.md` note
  qu'ils sont libres.

## Fichiers
Backend, à créer (`ageguessr-api/src/main/java/app/ageguessr/…`) :
- `resources/db/migration/V13__create_game_plays.sql`
- `features/game/GamePlay.java`, `GamePlayRepository.java`, `RoundStep.java`
  (`NOT_STARTED`…`FINISHED`), `Direction.java` (`OLDER`, `YOUNGER`)
- `features/game/Scoring.java` (formule pure)
- `features/game/GuestCookie.java` (création, lecture), `PlayerKey.java` (compte ou
  invité)
- `features/game/HintPicker.java` (catégories disponibles + choix déterministe, lit
  `HintRepository`)
- `features/game/RoundReveal.java` (construction du résultat : âge, points, identité,
  crédit, indice)
- `features/game/GetTodayGame/` : `GetTodayGameController`, `GetTodayGameHandler`,
  `TodayGameResponse`
- `features/game/SubmitFirstGuess/` : `…Command`, `…Controller`, `…Handler`, `…Response`
- `features/game/RevealHint/` : `…Command`, `…Controller`, `…Handler`, `…Response`
- `features/game/SubmitSecondGuess/` : `…Command`, `…Controller`, `…Handler`
- `features/game/PurgeGuestPlays/PurgeGuestPlaysScheduler.java`
- `TestSupportController` (profil `e2e`) : `POST /test-support/seed-daily-game`, qui
  crée 3 personnes de cases différentes avec photo APPROVED, image et indices, puis tire
  aujourd'hui
- Requêtes à ajouter : `HintRepository` (indices éligibles d'une année, d'une catégorie
  et d'un public), et, côté game, la photo + personne d'une manche

Backend, à modifier :
- `config/SecurityConfig.java` : `/api/game/**` public (GET et POST), JWT facultatif
- `shared/ratelimit/RateLimitFilter.java` (ou un second filtre) + propriétés
- `resources/application.properties` + `test/resources/application.properties` :
  `app.game.daily.draw-enabled=${GAME_DAILY_DRAW_ENABLED:true}`,
  `app.game.guest-cookie.*`, `app.game.guest-retention-days=7`,
  `app.game.hints.min-per-category=5`, `app.rate-limit.game.*`
- `test/.../architecture/ArchitectureTests.java` : exception `game → hints`, et
  `hints` ne dépend pas de `game`

Frontend, à créer (`frontend/src/app/features/game/`) :
- `game-api.ts` (service HttpClient), `models.ts`
- `game-vocabulary.ts` (tout le vocabulaire de jeu, voix enquête + repli neutre)
- `game/game.ts|html` (page `/` : enchaîne les manches et le récapitulatif)
- `round/round.ts|html` (photo filtrée, saisie, sens, choix de catégorie, indice, 2e
  essai)
- `round-result/round-result.ts|html` (verdict, points des deux essais, identité, crédit,
  indice avec année et source)
- `day-summary/day-summary.ts|html` (récapitulatif : score /3 000, une ligne par
  manche, manches retirées)
- Specs Vitest à côté de chaque fichier, `e2e/component/game.spec.ts`,
  `e2e/journeys/daily-game.spec.ts`

Frontend, à modifier :
- `app.routes.ts` (`/` → jeu), suppression de `features/home/`
- `features/legal/privacy/privacy.html` + `PRIVACY_UPDATED` : cookie
  `ageguessr_guest`, parties conservées (compte : avec le compte ; invité : 7 jours),
  en FR et en EN
- `styles.css` si une classe utilitaire de filtre photo manque

Docs : `docs/decisions/architecture.md` (exception `game → hints`, état des parties,
cookie invité, endpoints), `docs/decisions/design.md` (`hot`/`cold` libres, voix
enquête mise en œuvre), `docs/roadmap.md`.

## Phase 1 : Domaine backend (état, points, indices, résultat)
Status: Complete

- [x] V13 `game_plays` :
  - colonnes : `id`, `game_date`, `position` (FK vers `daily_rounds`), `user_id` (FK
    `users` `ON DELETE CASCADE`), `guest_id`, `language`, `first_guess`,
    `first_points`, `hint_category`, `hint_id` (FK `hints` `ON DELETE SET NULL`),
    `second_guess`, `second_points`, `started_at`, `finished_at` ;
  - contraintes : `CHECK num_nonnulls(user_id, guest_id) = 1`, `CHECK` 18-100 sur les
    essais, `CHECK` 0-500 sur les points ;
  - uniques partiels `(game_date, position, user_id)` et `(game_date, position,
    guest_id)`, et un index `(guest_id, game_date)` pour la purge.
- [x] Entité `GamePlay` (méthode `step()` dérivée des colonnes), `GamePlayRepository`,
      `RoundStep`, `Direction`
- [x] `Scoring.points(écart)` et `Scoring` d'une manche (1er exact = 500 + 500)
- [x] `HintPicker` : `availableCategories(year, language, personQid)` et
      `pick(date, position, category, language, year, personQid)`, déterministe
- [x] `RoundReveal` : âge, essais, points, identité (nom selon la langue, lien),
      crédit, indice (texte, année, source)
- [x] Vérifier à la main un lien `Special:GoToLinkedPage` : un QID avec article FR, et
      un sans. Noter le résultat ici et ajuster le lien si besoin.
- [x] `ArchitectureTests` : `game → hints` autorisé, `hints` ne dépend pas de `game`
- [x] Tests unitaires : `ScoringTest` (0, 1, 5, 10, 20, 82 ans d'écart, arrondi, jamais
      négatif), `HintPickerTest`, `GamePlayTest` (étapes), `RoundRevealTest`
- [x] Test d'intégration `GamePlaysIntegrationTest` :
  - contraintes V13 ;
  - cascade à la suppression du compte, `SET NULL` sur l'indice ;
  - unicité par joueur ;
  - requête des indices éligibles (public `ALL` + langue, exclusion de la personne).

### Verification Plan
- `cd ageguessr-api && ./mvnw test -Dtest='Scoring*,HintPicker*,GamePlay*,RoundReveal*,ArchitectureTests'`
  → vert

### Phase Summary
- **V13 `game_plays`** : une ligne créée au 1er essai (pas de ligne = `NOT_STARTED`).
  Uniques `(user_id, game_date, position)` et `(guest_id, …)` non partiels : Postgres ne
  compare jamais deux NULL dans un index unique. Index partiel `game_date WHERE guest_id
  IS NOT NULL` pour la purge. CHECK : un seul joueur, langue `EN`/`FR`, essais 18-100,
  points 0-500, catégorie connue, 2e essai complet (points + `finished_at`). FK
  `(game_date, position)` vers `daily_rounds`.
- **Domaine** (`features/game`) : `GamePlay` (`start`, `revealHint`, `secondGuess`,
  `step()` dérivé des colonnes, `acceptsSecondGuess(anyCategoryAvailable)`, `points()`),
  `PlayerKey` (créé dès cette phase : `GamePlay.start` en a besoin), `Language` (`EN`,
  `FR`, avec `audiences()` = `ALL` + la sienne), `RoundStep`, `Direction`, `Scoring`,
  `GameHint`, `HintOffer` (catégories offertes + choix déterministe), `HintPicker`
  (`offerFor(date, age, langue, qid)`, `shown(hintId, langue)` qui masque un indice
  rejeté), `RoundReveal` (auteur null laissé au front pour « Unknown author »).
- **Écart au plan** : pas de propriété `app.game.hints.min-per-category`. Le minimum est
  la constante `Hint.MINIMUM_PER_CATEGORY` (5), partagée avec la page de couverture
  admin, pour que les deux ne divergent jamais.
- **Une seule requête d'indices** : `HintRepository.findEligible(year, audiences,
  personQid)`, toutes catégories, triée par id. Le comptage et le choix se font en Java.
- **Choix déterministe** : `floorMod("date|position|CATEGORIE|LANGUE".hashCode(), n)`.
  `String.hashCode` est fixé par la spec Java, contrairement au hash d'une enum, qui
  change à chaque démarrage.
- **Lien Wikipedia vérifié le 2026-11-02** : `Special:GoToLinkedPage/frwiki/Q42` donne
  un 302 vers `fr.wikipedia.org/wiki/Douglas_Adams`. Pour un QID sans article dans la
  langue (Q110000000 en frwiki), Wikidata affiche sa propre page (200). C'est rare pour
  des personnes choisies par sitelinks ; on garde le lien sans rien stocker.
- **Tests** : `ScoringTest` (14), `DirectionTest` (3), `GamePlayTest` (11),
  `HintOfferTest` (5), `HintPickerTest` (4), `RoundRevealTest` (4),
  `GamePlaysIntegrationTest` (20), `ArchitectureTests` (7, dont
  `hints_do_not_depend_on_the_game`). Tous verts, ainsi que `HintConstraints*`,
  `GetHintCoverage*` et `DailyRounds*`.

## Phase 2 : Endpoints, cookie invité, sécurité, purge, activation du tirage
Status: Not started

- [ ] `GuestCookie` + `PlayerKey` :
  - compte si le JWT est valide ;
  - sinon cookie invité, créé au 1er POST s'il manque.
- [ ] `GetTodayGameHandler` :
  - appelle `drawIfMissing(today)`, puis `findStatesOf` ;
  - fusionne avec les `game_plays` du joueur ;
  - calcule le score du jour (manches PLAYABLE finies seulement) ;
  - ne renvoie jamais `photoId`, l'âge ni l'identité d'une manche non finie.
- [ ] `SubmitFirstGuessHandler`, `RevealHintHandler`, `SubmitSecondGuessHandler` :
  - Bean Validation (`@Min(18) @Max(100)`, langue `en`/`fr`, catégorie connue) ;
  - erreurs 409 `GAME_CLOSED`, `ROUND_NEUTRALIZED`, `WRONG_STEP`,
    `CATEGORY_UNAVAILABLE` ;
  - double soumission concurrente : l'unique V13 donne un 409, pas un 500.
- [ ] `SecurityConfig` : `/api/game/**` public, avec un commentaire sur le JWT
      facultatif (un jeton invalide reste un 401, et l'intercepteur front rafraîchit)
- [ ] Limite par IP sur `POST /api/game/**`
- [ ] `PurgeGuestPlaysScheduler` (quotidien, conditionné comme le tirage)
- [ ] `TestSupportController` : `seed-daily-game` (profil `e2e` uniquement), et son
      chemin public seulement sous ce profil (`TestSupportSecurityConfig`)
- [ ] `app.game.daily.draw-enabled` vrai par défaut (le jeu est jouable dans cette PR)
- [ ] Tests unitaires des trois handlers :
  - chaque transition et chaque refus ;
  - 1er essai exact = 1 000, sans indice ;
  - aucune catégorie disponible : passage direct au 2e essai ;
  - manche neutralisée entre les deux essais ;
  - la date d'hier refusée.
- [ ] Test fonctionnel `GameFunctionalTest` (MockMvc + Postgres) :
  - partie invitée complète, avec cookie posé puis relu ;
  - partie connectée complète, avec JWT ;
  - un invité et un compte ne se voient pas ;
  - un GET avant la fin ne contient ni âge, ni nom, ni crédit, ni `photoId` (on
    vérifie le JSON brut) ;
  - un 2e 1er essai renvoie 409 ;
  - la limite par IP renvoie 429 (contexte dédié, comme `RateLimitFunctionalTest`) ;
  - `GAME_CLOSED`.
- [ ] Test d'intégration de la purge : les lignes invitées de 8 jours sont supprimées,
      celles de 7 jours et celles d'un compte gardées

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → tout vert
- En local, avec `GAME_DAILY_DRAW_ENABLED=true` et au moins 3 photos APPROVED de cases
  différentes : `curl -c jar -b jar localhost:8080/api/game/today`, puis le 1er essai
  et la suite en `curl`, jusqu'au résultat

### Phase Summary
_(à écrire à la fin de la phase)_

## Phase 3 : Écran de jeu
Status: Not started

- [ ] `game-api.ts` + `models.ts` (les appels de jeu ; le cookie passe en same-origin
      via le proxy dev et Caddy)
- [ ] `game-vocabulary.ts` : *case*, *today's file*, *lead*, *verdict*, *case report*,
      « older / younger », messages d'erreur. Aucun texte de jeu en dur dans les
      templates.
- [ ] `round` :
  - photo avec le filtre de vieillissement ;
  - saisie 18-100, au clavier, mobile-first ;
  - flèche + texte pour le sens ;
  - boutons de catégorie (seulement celles disponibles), puis indice et 2e essai ;
  - accessibilité : labels, `aria-live` sur le sens et l'indice, focus déplacé à
    chaque étape.
- [ ] `round-result` :
  - âge réel, les deux essais et leurs points ;
  - nom + lien Wikipedia (nouvel onglet, `rel="noopener"`) ;
  - crédit : auteur, licence avec lien, page Commons ;
  - indice avec son année et sa source.
- [ ] `day-summary` : score /3 000 (ou /1 000 × manches jouables), une ligne par manche,
      « photo removed » pour une manche neutralisée
- [ ] `game` à `/` :
  - reprend l'état du jour au rechargement ;
  - enchaîne les manches ;
  - affiche une manche neutralisée comme retirée et passe à la suivante ;
  - gère le 409 `GAME_CLOSED` en rechargeant ;
  - gère une image en 404 (manche retirée) ;
  - suppression de `features/home`.
- [ ] `/privacy` FR + EN : cookie `ageguessr_guest` (30 jours, strictement nécessaire),
      conservation des parties, date de mise à jour
- [ ] Specs Vitest : service, vocabulaire (aucune clé vide), `round` (étapes,
      validation, focus), `round-result`, `day-summary`, `game` (reprise d'état,
      neutralisée, `GAME_CLOSED`)
- [ ] `e2e/component/game.spec.ts` (Playwright, réseau simulé) : rendu mobile et
      desktop, filtre photo, parcours d'une manche, manche retirée

### Verification Plan
- `cd frontend && pnpm test` → vert ; `pnpm lint` et le format, comme la CI
- `cd frontend && pnpm exec playwright test e2e/component/game.spec.ts` → vert
- `pnpm start` + l'API locale : jouer les 3 manches à la main, en mobile (devtools) et
  en desktop

### Phase Summary
_(à écrire à la fin de la phase)_

## Phase 4 : Parcours complet, documentation, reviews
Status: Not started

- [ ] `e2e/journeys/daily-game.spec.ts` (app complète, profil `e2e`) :
  - `seed-daily-game`, puis un invité joue les 3 manches jusqu'au récapitulatif ;
  - un rechargement en cours de manche reprend au bon endroit.
- [ ] `docs/decisions/architecture.md` :
  - exception `game → hints` ;
  - « État d'une partie » (table, cookie invité, purge, compte prioritaire, minuit) ;
  - endpoints et règle « rien avant la fin ».
- [ ] `docs/decisions/design.md` : `hot`/`cold` libérés, voix enquête mise en œuvre
      (fichier de vocabulaire)
- [ ] `docs/roadmap.md` : cocher guess, points, écran principal, indice, résultat avec
      crédit, enchaînement + récapitulatif, vocabulaire centralisé. Garder ouverts le
      signalement, le duotone, la page d'accueil et le cache HTTP des images.
- [ ] Agents `architecture-reviewer` et `code-reviewer` (endpoints publics, cookie,
      données de compte, RGPD), puis appliquer leurs retours

### Verification Plan
- `cd ageguessr-api && ./mvnw test` et `cd frontend && pnpm test` → vert
- `pnpm exec playwright test` (component + journeys, stack e2e lancée) → vert

### Phase Summary
_(à écrire à la fin de la phase)_

## Tests : catégories retenues
- **Unitaire backend** : oui. Formule de points, choix d'indice, étapes d'une manche,
  construction du résultat, handlers.
- **Intégration** : oui. Contraintes V13, cascades, requêtes d'indices, purge.
- **Fonctionnel** : oui. Les endpoints publics sont la surface anti-triche : rien
  avant la fin, cookie, JWT facultatif, 409, 429.
- **Unitaire front** : oui. Service, composants, vocabulaire.
- **Interface (Playwright component)** : oui. Nouvel écran principal, rendu mobile et
  filtre photo que jsdom ne voit pas.
- **QA e2e** : oui. La « partie quotidienne complète » est le parcours critique cité par
  `docs/testing-strategy.md`.
- **Architecture** : oui. Exception `game → hints` et son sens.
- **Mutation** : oui, sans action dédiée. `Scoring` et `HintPicker` sont purs et ciblés
  par PIT.

## Edge cases
- 1er essai exact : manche finie à 1 000, aucun indice demandé.
- Aucune catégorie disponible : 2e essai direct, sans indice.
- Indice rejeté après avoir été donné : `hint_id` à NULL, le résultat s'affiche sans
  indice.
- Célébrités : l'indice ne cite jamais la personne à deviner.
- Manche neutralisée avant, pendant (entre les essais) ou après la manche : refus des
  essais, la manche sort du score, et l'image 404 est gérée côté front.
- Double clic ou deux onglets : un seul 1er essai enregistré (unique V13 → 409).
- Minuit pendant une manche : `GAME_CLOSED`, puis le front recharge la nouvelle partie.
- Invité sans cookie qui fait un GET : manches `NOT_STARTED`, pas de cookie posé.
- JWT expiré : 401, rafraîchissement par l'intercepteur, puis la partie du compte.
- Partie du jour avec moins de 3 manches (pool trop petit) : score /1 000 × manches
  jouables.
- Nom sans version française : repli sur l'anglais, et inversement.
- Photo sans auteur : « Unknown author », licence et lien toujours affichés.

## Final Recap
_(à écrire quand toutes les phases sont terminées)_

## Deployment Plan
_(à écrire quand toutes les phases sont terminées)_

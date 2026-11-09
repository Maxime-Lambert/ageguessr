# Profil et historique des parties (Semaine 5)

Un joueur connecté retrouve sur `/profile` ses statistiques (parties jouées, score moyen,
meilleur score, série en cours et meilleure série) et l'historique de ses parties du
jour, manche par manche. Les parties jouées en invité avant la connexion sont rattachées
au compte. Le profil n'est visible que par son propriétaire. Les amis le verront à la
Semaine 6.

Branche : `feature/game-history`, depuis `develop`. Une seule PR.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions (validées le 2026-11-09)

- **Profil privé** : `/profile` est réservé au compte connecté (`authGuard`). Pas de page
  publique par pseudo : l'historique de chaque compte serait exposé à tous. La
  visibilité par les amis viendra avec la Semaine 6.
- **Parties invité rattachées au compte, à la connexion** : endpoint
  `POST /api/game/guest-plays/claim`, appelé par le frontend après une connexion réussie.
  L'appel est sous `/api/game` parce que le cookie `ageguessr_guest` a `Path=/api/game` :
  il n'est jamais envoyé à `/api/auth/login`. Ce n'est pas fait au passage dans une
  requête GET : une lecture ne doit pas écrire. L'endpoint est idempotent, et sans cookie
  il ne fait rien.
  - Une manche que le compte a déjà jouée l'emporte : la ligne invité de la même manche
    est supprimée, les autres passent au compte (`user_id` posé, `guest_id` vidé).
  - Le cookie invité est effacé dans la réponse : la prochaine partie sans connexion
    repart d'un nouvel invité.
  - L'inscription ne rattache rien : le compte n'est connecté qu'après la confirmation
    de l'email puis la connexion, qui fait le rattachement.
- **Pas de photo dans l'historique** : `architecture.md` (« Limites connues ») interdit de
  servir une ancienne image par son `publicId` actuel, qui relierait l'id du jour à une
  réponse connue. L'historique montre le nom, l'âge sur la photo, les essais et les
  points, sans image ni `publicId`.
- **Le passé n'est pas réécrit** (même principe que les points stockés à l'essai, V13) :
  - une manche neutralisée après avoir été jouée garde ses points et compte dans le
    maximum du jour ; son nom n'est plus affiché (photo rejetée, personne exclue) ;
  - une manche neutralisée et jamais jouée sort du maximum, comme le jour même.
  Cela règle l'« effet de bord à traiter avec l'historique » noté dans `architecture.md`
  (« Tirage quotidien »).
- **Calculs en Java, pas en SQL** : on lit toutes les manches jouées du compte (3 par
  jour au plus, environ 1 100 par an) et l'état des manches de ces jours, puis une classe
  pure calcule les jours, les séries et les statistiques. C'est testable unitairement et
  par mutation, et la taille reste faible. À revoir seulement si un compte dépasse
  plusieurs milliers de jours.
- **Page d'accueil publique** : fusionnée avec « Landing page de lancement » (Semaine 8).
  Le jeu reste à `/` (décision utilisateur du 2026-11-09).

## Hypothèses (à confirmer à la relecture du plan)

- **Jour complet** : chaque manche du jour a une partie terminée, ou est neutralisée sans
  avoir été jouée. C'est le critère des séries et des statistiques.
- **Série** : jours UTC consécutifs complets (le tirage est à minuit UTC). La série en
  cours compte jusqu'à aujourd'hui si la partie du jour est complète, sinon jusqu'à hier.
  Un jour du jour pas encore fini ne casse donc pas la série.
- **Statistiques** : parties jouées (jours complets), score moyen et meilleur score sur
  les jours complets (affichés « sur 3 000 », le maximum pouvant être plus bas un jour
  avec une manche neutralisée), série en cours, meilleure série. Pas de distribution des
  écarts pour l'instant.
- **Enquête libre et défis hors historique** : seules les parties du jour comptent. Les
  séries invité ne sont pas rattachées (elles expirent en 7 jours, et un défi garde le
  pseudo de l'invité).
- **Pagination** : 30 jours joués par page, du plus récent au plus ancien, avec un
  bouton « Plus ancien ».

## Phase 1 : Rattachement des parties invité
Status: Complete

- [x] `GamePlayRepository` : deux requêtes `@Modifying`, dans une même transaction.
      `deleteGuestPlaysAlreadyPlayedBy(guestId, userId)` supprime les lignes invité dont
      le compte a déjà la manche (même `game_date`, `position`), puis
      `moveGuestPlaysTo(guestId, userId)` passe les autres au compte
- [x] `features/game/ClaimGuestPlays/ClaimGuestPlaysHandler.java` : `@Transactional`,
      retourne le nombre de manches rattachées
- [x] `features/game/ClaimGuestPlays/ClaimGuestPlaysController.java` :
      `POST /api/game/guest-plays/claim`, 401 sans JWT, lit le cookie via `GuestCookie`,
      répond `{ "claimed": n }` et efface le cookie (`GuestCookie.clear()` à ajouter :
      même nom, même `Path`, `Max-Age=0`)
- [x] `SecurityConfig` : `/api/game/guest-plays/claim` authentifié (le reste de
      `/api/game/**` garde le JWT facultatif)
- [x] Frontend : `GameApi.claimGuestPlays()` (`features/game/game-api.ts`), appelé par
      `Login` après une connexion réussie, avant la navigation. Une erreur est ignorée :
      elle ne bloque jamais la connexion

**Tests**
- Intégration (`integration/game/ClaimGuestPlaysIntegrationTest`) : rattache les
  manches invité, supprime celle que le compte a déjà jouée, ne touche ni un autre
  invité ni un autre compte, garde les drapeaux de signalement (V16), relancé ne fait
  rien
- Fonctionnel (dans `functional/game/GameFunctionalTest`, qui a déjà la partie du jour en base) : 401 sans JWT, sans
  cookie → `claimed: 0`, avec cookie → manches visibles dans `GET /api/game/today` du
  compte et cookie effacé (`Max-Age=0`, `Path=/api/game`)
- Unitaire frontend : `login.spec.ts` appelle le rattachement après succès, navigue même
  s'il échoue ; `game-api.spec.ts`

### Verification Plan
- `cd ageguessr-api && ./mvnw test -Dtest='ClaimGuestPlays*'` : vert
- `cd frontend && pnpm exec ng test --include='src/app/features/{auth/login,game}/**/*.spec.ts'` : vert

### Phase Summary
Fait le 2026-11-09. `ClaimGuestPlaysIntegrationTest` (3 tests) et les 3 nouveaux tests
de `GameFunctionalTest` passent : la manche déjà jouée sur le compte l'emporte, un 2e
appel ne rattache rien, le cookie est effacé (`Max-Age=0`, `Path=/api/game`), 401 sans
compte. Frontend : 177 specs auth et jeu vertes ; `login.spec.ts` vérifie l'appel après
la connexion et la navigation même s'il échoue. Les mises à jour en masse (JPQL)
ignorent le verrou `@Version`, sans risque ici : seules les colonnes de joueur changent.

## Phase 2 : Historique et statistiques (API)
Status: Complete

- [x] `features/game/GetHistory/HistoryCalculator.java` (pur, sans Spring) : à partir des
      manches jouées et des états des manches de ces jours, construit les jours
      (`points`, `max`, `complete`), les statistiques et les séries, selon les décisions
      et hypothèses ci-dessus. `today` passé en paramètre (`Clock` UTC)
- [x] Requêtes : `GamePlayRepository.findByUserId` (l'ordre est fait par le calcul), et
      l'état des manches de ces dates en une requête (`DailyRoundRepository`, sur le
      modèle de `findStatesOf`, avec le nom FR/EN et l'âge sur la photo pour les manches
      jouées encore jouables)
- [x] `features/game/GetHistory/GetHistoryHandler.java` + `HistoryResponse.java` :
      `stats` (`gamesPlayed`, `averagePoints`, `bestPoints`, `currentStreak`,
      `bestStreak`) et `days` paginés (`page`, 30 par page, `hasMore`). Une manche :
      `position`, `name` (dans la langue de la partie, `null` si neutralisée), `ageAtPhoto`
      (`null` si neutralisée), `firstGuess`, `secondGuess`, `points`, `neutralized`.
      **Jamais** de `publicId` ni d'URL d'image
- [x] `features/game/GetHistory/GetHistoryController.java` : `GET /api/game/history?page=0`,
      401 sans JWT, `page` validé par `GetHistoryQuery` (`@Valid`, `@Min(0)`)
- [x] `SecurityConfig` : `/api/game/history` authentifié

**Tests**
- Unitaire (`features/game/GetHistory/HistoryCalculatorTest`) : jour complet, jour à
  moitié joué, manche neutralisée après jeu (points gardés, max inchangé), neutralisée
  sans jeu (hors max, jour quand même complet), série en cours avec et sans la partie du
  jour, série cassée par un jour manqué, meilleure série ancienne, aucune partie (tout à
  0), moyenne arrondie, changement d'année (31 décembre → 1er janvier)
- Intégration (`integration/game/GetHistoryIntegrationTest`) : les requêtes lisent les
  bonnes lignes (ordre, compte seul, jamais les lignes invité), le nom suit la langue
  de la partie, une photo rejetée donne `neutralized`
- Fonctionnel (dans `functional/game/GameFunctionalTest`) : 401 sans JWT, réponse d'un
  compte qui a joué hier et aujourd'hui (série 2), pagination (`hasMore`), `page=-1` →
  400, la réponse ne contient ni `publicId` ni `/api/images/`
- Architecture : les règles ArchUnit existantes couvrent le nouveau use case (package
  par use case, pas de dépendance entre features). Pas de nouvelle règle
- Mutation : PIT tourne en CI. `HistoryCalculator` est la cible principale (comparaisons
  de dates et bornes de séries) ; viser aucun mutant survivant sur cette classe

### Verification Plan
- `cd ageguessr-api && ./mvnw test` : vert
- `./mvnw test -Dtest='HistoryCalculatorTest,GetHistory*,HistoryFunctionalTest'` : vert

### Phase Summary
Fait le 2026-11-09. `./mvnw test` vert (toute la suite, ArchUnit compris).
`HistoryCalculatorTest` : 16 tests, une règle chacun. `GetHistoryIntegrationTest` :
pages de 30 jours, lignes du compte seul, page `Integer.MAX_VALUE` vide (le calcul de
l'index passe en `long` pour ne pas déborder). `GameFunctionalTest` : une manche du jour
pas finie n'a ni nom, ni âge, ni points, une manche finie les a, et la réponse ne
contient ni `publicId`, ni id interne, ni URL d'image ou Commons ; `page=-1` → 400, 401
sans compte. **Écart au plan** : le nom et l'âge ne sortent que pour une manche
**terminée** et encore jouable. Sans cette règle, l'historique aurait donné la réponse
de la manche du jour en cours. `RoundReveal.name` devient public pour être réutilisé.
Mutation : PIT tourne en CI, à relever à la PR.

## Phase 3 : Page profil (frontend)
Status: Complete

- [x] `GameApi.history(page)` et types `History*` dans `features/game/models.ts`
- [x] `features/game/profile/profile.ts|html` : `Page width="default"`, `h1` (pseudo),
      tuiles de statistiques (série en cours mise en avant en `score`, meilleure série,
      parties, moyenne, meilleur score), puis la liste des jours : date longue dans la
      langue du site, score `points / max`, une ligne par manche (nom ou « Affaire
      retirée », âge, essais, points). Bouton « Plus ancien » tant que `hasMore`
- [x] États : chargement, erreur avec « Réessayer », aucun jour joué (lien vers le dossier
      du jour)
- [x] Route `profile` (`authGuard`, titre `$localize`), entrée « Profil » dans le menu de
      compte et le panneau mobile (`header.html`, icône Lucide `user` ou `history`)
- [x] i18n : textes `profile.*` en `$localize` / `i18n`, traduits dans
      `messages.fr.xlf`, vocabulaire « enquête » via `game-vocabulary.ts` (dossier,
      affaire). `pnpm extract-i18n` pour `messages.xlf`

**Tests**
- Unitaire frontend (`profile.spec.ts`) : tuiles avec les bonnes valeurs, jour
  incomplet signalé, manche neutralisée sans nom, état vide, erreur puis réessai,
  « Plus ancien » charge la page suivante et l'ajoute ; `header.spec.ts` : l'entrée
  Profil n'apparaît que connecté
- Interface (`e2e/component/profile.spec.ts` + `e2e/component-fr/profile.spec.ts`) :
  page rendue avec l'API simulée, dates et nombres au format de la langue, mobile
  390 px sans débordement
- QA e2e (dans `e2e/journeys/daily-game.spec.ts`, en mode `serial` : les deux tests posent la partie du jour, ce qui efface les manches du jour) : jouer la partie du jour en invité
  (`seed-daily-game`), s'inscrire, confirmer, se connecter, ouvrir `/profile` : la
  partie y est, série 1. C'est la promesse de l'encart « Gardez votre série », un
  parcours critique

### Verification Plan
- `cd frontend && pnpm test && pnpm lint && pnpm build` : vert
- Captures du build (script `page.route` de la refonte, un seul Chromium) : `/fr/profile`
  desktop et 390 px, avec des données et vide
- Specs Playwright : CI seulement (WSL)

### Phase Summary
Fait le 2026-11-09. `pnpm test` : 369 specs vertes, lint et build OK. Captures du build
(`/fr/profile`, desktop et 390 px, API simulée) : conformes, tuiles en deux colonnes sur
mobile. **Bug attrapé par les specs** : Angular lit une date `YYYY-MM-DD` comme minuit
local ; la formater en UTC affichait la veille à l'est de Greenwich. Formatée dans le
fuseau local, la spec passe à Paris comme à New York (`TZ=America/New_York`). Libellé
« Vos essais : » avec ses deux-points dans la traduction (espace avant en français).
Bundle initial : 515 → 536 kB bruts (+4,8 kB transférés), deux icônes Lucide dans
`main` et un morceau commun d'Angular remonté par esbuild, sous l'avertissement de
550 kB. Specs Playwright (interface EN/FR, parcours) : CI seulement.

## Phase 4 : Documentation et légal
Status: Not started

- [ ] `docs/decisions/architecture.md` : section « Historique et profil » (décisions
      ci-dessus), et remplacer l'« effet de bord à traiter avec l'historique » par la
      règle retenue
- [ ] Politique de confidentialité (EN + FR, `privacy.html`) : les parties invité sont
      rattachées au compte à la connexion, puis gardées comme les parties du compte.
      Date de mise à jour (`legal-dates.ts`). Pas de nouvelle version des CGU
- [ ] `docs/roadmap.md` : Semaine 5 cochée, « Page d'accueil publique » retirée de la
      Semaine 4 et fusionnée dans « Landing page de lancement » (Semaine 8)
- [x] `plans/design-overhaul.md` déplacé dans `plans/done/`
- [ ] Ce plan déplacé dans `plans/done/` (nom : `game-history.md`) à la fin

### Verification Plan
- `grep -n "effet de bord" docs/decisions/architecture.md` : plus de note en suspens
- `cd frontend && pnpm test` : vert (spec des pages légales)

### Phase Summary
_(write when phase completes)_

## Edge cases à couvrir
- Compte sans aucune partie : statistiques à 0, liste vide, pas d'erreur
- Partie du jour commencée mais pas finie : jour visible, marqué incomplet, ne casse
  pas la série
- Manche neutralisée après jeu, avant jeu, et rejet annulé ensuite (la manche redevient
  jouable : le nom réapparaît)
- Rattachement : même manche jouée en invité et sur le compte (le compte gagne), double
  appel (idempotent), cookie d'un invité sans partie, cookie déjà purgé (lignes
  supprimées après 7 jours)
- Deux connexions simultanées qui rattachent le même cookie : la seconde ne trouve plus
  rien (pas de violation d'unicité)
- Compte supprimé : ses parties partent avec lui (`ON DELETE CASCADE`, déjà en place)
- Pagination : page au-delà de la fin → liste vide, `hasMore: false`

## Catégories de tests non retenues
Aucune : les sept s'appliquent. L'architecture n'ajoute pas de règle (les règles
existantes suffisent), et le QA e2e se limite au seul parcours invité → compte → profil.

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

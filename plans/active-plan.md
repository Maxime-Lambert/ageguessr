# Enquête libre et Défi (mode hors partie du jour)

Un mode de jeu en plus de la partie du jour.

- **Enquête libre** : le joueur choisit une série de 5, 10 ou 20 photos et la joue avec
  les mêmes règles que la partie du jour (1er essai, indice, 2e essai, même barème). Il
  enchaîne les séries autant qu'il veut.
- **Défi** : à la fin d'une série, « Défier des amis » donne un lien qui fait jouer
  exactement les mêmes photos avec les mêmes indices. Chacun ne joue qu'une fois, et un
  classement compare les scores.

## For Future Agents
Au fil du travail : cocher les cases `- [x]`. Quand une phase est finie, passer son
statut à `Complete` et écrire son **Phase Summary**. Lancer le **Verification Plan** de
la phase et noter le résultat avant de passer à la suivante. Ce fichier est la source de
vérité, pas la conversation.

**Playwright ne tourne jamais en local** (il fait planter WSL) : les specs s'écrivent ici,
mais c'est la CI de la PR qui les exécute (`docs/testing-strategy.md`).

## Contexte pour repartir à froid
- Branche `feature/free-investigation`, créée depuis `develop` après le merge de la PR #32
  (i18n).
- **Partie du jour** (`features/game`) :
  - `DrawDailyGame/DailyDraw` tire les photos (fonction pure, `RandomGenerator` injecté) :
    une seule personne par case (continent × genre), pas de photo rejouée avant 3 ans ;
  - `daily_rounds` (V12) donne les manches, `game_plays` (V13) les parties ;
  - `GamePlay` porte la logique d'une manche (`start`, `revealHint`,
    `finishWithSecondGuess`, `points`) et `Scoring` le barème ;
  - indices : `HintPicker.offerFor(gameDate, age, language, qid)` vise l'année
    `gameDate.year - age`. `HintOffer.pick` choisit le même indice pour tout le monde
    grâce à un hash de `date|position|catégorie|langue` ;
  - joueur : `PlayerKey` (compte ou invité, cookie `ageguessr_guest`), `PlayerResolver`.
    `PurgeGuestPlays` supprime les parties d'invités au bout de 7 jours ;
  - endpoints `/api/game/today` et `/api/game/{date}/rounds/{position}/first-guess|hint|second-guess`.
- **Front** (`frontend/src/app/features/game`) : `game/` (écran), `round/`,
  `round-result/`, `day-summary/`, `game-api.ts`, `game-vocabulary.ts` (voix enquête et
  repli neutre, en `$localize`), `hint-language`.
- **i18n obligatoire** (`CLAUDE.md`) : textes joueur marqués `i18n="@@…"` ou `$localize`,
  traduits dans `messages.fr.xlf` dans la même PR.

## Décisions validées avec l'utilisateur (2026-11-03)
- **Modèle** : il y a un seul mécanisme.
  - Le solo joue des séries de taille fixe, enchaînées avec « Nouvelle série ».
  - Le partage se fait **après** avoir joué une série : « Défier des amis » transforme la
    série en défi, et le créateur est le premier du classement.
  - On ne crée pas de défi sans l'avoir joué, et il n'y a pas de flux sans fin.
- **Longueurs** : trois réglages, 5, 10 ou 20 photos.
- **Accès** : tout le monde, invités compris (cookie invité, comme la partie du jour).
- **Une seule tentative** par joueur et par défi. En rouvrant le lien, on voit son
  résultat et le classement.
- **Comparaison**, de deux façons :
  - un classement du défi ;
  - un texte de résultat à copier.
- **Nom dans le classement** :
  - pour un compte, son pseudo ;
  - pour un invité, un **pseudo libre** saisi avant de jouer le défi, gardé uniquement
    dans ce défi.
- **Noms des modes** :
  - voix enquête : « Enquête libre » / « Free investigation » et « Défi » /
    « Challenge » ;
  - repli neutre : « Série » / « Series » et « Défi » / « Challenge ».
- **Pool : tout le pool, risque accepté.** Le mode libre tire parmi toutes les photos
  approuvées, sauf celles de la partie du jour en cours. Un joueur assidu peut reconnaître
  une photo dans une future partie du jour, et l'utilisateur l'accepte.
- **Conservation** :
  - une série non partagée est supprimée 7 jours après sa création ;
  - un défi, avec son classement et les pseudos d'invités, est supprimé 30 jours après
    son partage, et le lien affiche alors « Défi expiré » ;
  - supprimer son compte retire le joueur des classements.

## Décisions d'implémentation (proposées, à revoir si le code dit autre chose)
- **Dans `features/game`**, pas de nouvelle feature : ce sont les mêmes règles de jeu, et
  une feature `series` dépendrait de `game` (barème, indices, vues). Les nouveaux use cases
  sont des packages : `StartSeries/`, `GetSeries/`, `SubmitSeriesFirstGuess/`,
  `RevealSeriesHint/`, `SubmitSeriesSecondGuess/`, `ShareSeries/`, `GetChallenge/`,
  `JoinChallenge/`, `PurgeSeries/`.
- **Pas de duplication de la logique d'une manche** : on extrait de `GamePlay` ce qui ne
  dépend pas de la partie du jour (étapes, points, indice, 2e essai) dans un objet commun,
  utilisé par `GamePlay` et `SeriesPlay`. C'est la première tâche, avec les tests existants
  comme filet.
- **Tables** (V15) :
  - `free_series` : `id`, `size`, `created_on` (date, qui donne l'année des indices),
    `created_at`, créateur (`user_id` ou `guest_id`), `share_code` (unique, NULL tant que
    la série n'est pas partagée), `shared_at` ;
  - `free_series_rounds` : `series_id`, `position`, `photo_id` ;
  - `free_series_players` : `series_id`, `user_id` ou `guest_id`, `nickname` (invités
    seulement), `joined_at` ;
  - `free_series_plays` : même forme que `game_plays`, mais avec `series_id` et
    `position`, ainsi que les mêmes contraintes et index uniques par joueur et par manche.
  - `ON DELETE CASCADE` depuis `users` et depuis `free_series`.
- **Tirage `SeriesDraw`**, une fonction pure testée avec un seed fixe :
  - exclut les photos de la partie du jour en cours ;
  - préfère les photos que ce joueur n'a jamais vues (parties du jour et séries), puis
    complète avec les autres ;
  - fait tourner les cases continent × genre (la moins utilisée d'abord) au lieu de les
    interdire : 20 photos dépassent le nombre de cases ;
  - si le pool est trop petit, la série a moins de manches, comme la partie du jour.
- **Indices** : année = `created_on.year - age`. Le même indice est choisi pour tous les
  joueurs d'une série, avec un hash de `series_id|position|catégorie|langue`. Il n'y a rien
  à stocker.
- **Code de partage** : 8 caractères en base32 Crockford, tirés avec `SecureRandom`
  (40 bits, impossible à deviner en pratique). URL `/challenge/K7F2QX9M`. Seul le
  créateur, une fois la série terminée, peut la partager. Partager deux fois renvoie le
  même code.
- **Rejoindre un défi** : `POST /api/challenges/{code}/join`. Un compte n'envoie rien, un
  invité envoie `{nickname}` (mêmes règles que `@Username`). Il faut avoir rejoint le défi
  pour jouer ses manches. Le créateur est inscrit d'office.
- **Classement** :
  - seuls les joueurs qui ont fini apparaissent ;
  - tri par score total décroissant, puis par heure de fin ;
  - visible seulement par un joueur qui a fini le défi, pour qu'on ne joue pas en
    connaissant les scores à battre ;
  - jamais d'email ni d'identifiant interne : uniquement le nom affiché, le score et le
    rang.
- **Texte à partager** : nom du mode, score sur le maximum et une ligne d'emoji par
  manche (selon les points), puis le lien. Le texte suit la langue du site.
- **Limite de débit** : création de séries et `join` limités par IP (même filtre que
  `/api/auth/`), contre le moissonnage des âges et les pseudos en masse.
- **Front** :
  - route `/free` (choix de la longueur, jeu, bilan avec « Nouvelle série » et « Défier
    des amis ») ;
  - route `/challenge/:code` (présentation du défi et de son créateur, pseudo si invité,
    jeu, puis classement) ;
  - réutilise `round/` et `round-result/`. `game-api.ts` accepte une base d'URL de
    manche ;
  - lien « Enquête libre » dans l'en-tête et depuis le bilan de la partie du jour ;
  - le partage passe par `navigator.share` sur mobile, sinon par une copie dans le
    presse-papiers.

## Hypothèses (non tranchées, choix par défaut)
- La partie du jour reste l'accueil (`/`), et le mode libre est une page à part.
- Les séries ne comptent ni dans les stats ni dans l'historique du joueur. Elles ne
  touchent pas non plus à la règle « pas rejouée avant 3 ans » de la partie du jour.
- Une série commencée puis abandonnée reste reprenable tant qu'elle n'est pas purgée.

## Phase 1 : Backend, séries solo
Status: Complete

- [x] Extraire la logique d'une manche de `GamePlay` dans un objet commun. Les tests
      existants doivent rester verts sans modification.
- [x] V15 : tables `free_series`, `free_series_rounds`, `free_series_players`,
      `free_series_plays` (contraintes, index uniques, cascades)
- [x] Entités et repositories
- [x] `SeriesDraw` : pool sans les photos du jour, photos jamais vues d'abord, rotation
      des cases, série plus courte si le pool manque
- [x] `POST /api/series {size}` (5, 10 ou 20, sinon 400) → id et manches (sans âge)
- [x] `GET /api/series/{id}`, puis 1er essai, indice et 2e essai sur
      `/api/series/{id}/rounds/{position}/…`, réservés aux joueurs de la série (sinon 404)
- [x] Indices de série (année depuis `created_on`, hash avec `series_id`)
- [x] Limite de débit sur la création
- [x] Tests :
  - unitaires : `SeriesDraw` (seed fixe : exclusion du jour, jamais vues d'abord,
    rotation des cases, pool trop petit), choix de l'indice ;
  - intégration : contraintes de V15 et cascades ;
  - fonctionnels : série complète du début à la fin, âge jamais envoyé avant la
    révélation, 404 sur la série d'un autre, taille invalide en 400, double 1er essai en
    409 ;
  - architecture : règles existantes vertes.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert

### Phase Summary
- **`RoundProgress`** (`@Embeddable`) regroupe les règles d'une manche : étapes, points,
  indice, 2e essai. `GamePlay` et `FreeSeriesPlay` l'embarquent avec les mêmes colonnes.
  L'interface **`PlayedRound`** expose ses getters par des méthodes `default`, donc
  `RoundViews`, `RoundReveal` et les tests existants n'ont pas changé.
- **`GameRound.seriesId`** (null pour la partie du jour) et **`hintKey()`** : la clé de
  l'indice du jour reste exactement `date|position`, donc aucun indice ne change le jour
  de la mise en prod (test dédié). Une série utilise `series:<id>|position`.
- **`GameScore`** remplace `TodayGameResponse.Score` (même JSON), et sert aussi aux
  séries.
- **V15** : `free_series`, `free_series_rounds`, `free_series_players`,
  `free_series_plays`. Contraintes (taille 5/10/20, code base32 Crockford, un seul joueur
  par ligne, pseudo d'invité seulement), index uniques (une tentative par joueur et par
  manche, un `join` par joueur), cascades depuis `users` et `free_series`.
- **`SeriesDraw`** : sans les photos du jour, jamais vues d'abord, une personne une seule
  fois, rotation des cases. `SeriesCandidateRepository.findSeenBy` est en JPQL : l'autre
  id du joueur est null, et une requête SQL native typerait mal ce paramètre.
- **Endpoints**, tous sous `/api/game/` (déjà publics, JWT facultatif, comptés par la
  limite du jeu) :
  - `POST /api/game/series {size}` ;
  - `GET /api/game/series/{id}` ;
  - `GET /api/game/series/current` (204 sans série en cours) ;
  - `POST …/series/{id}/rounds/{position}/first-guess|hint|second-guess`.
  Une série inconnue, purgée ou d'un autre joueur donne le même 404.
- **Limite `series`** : 30 par heure et par IP (`SERIES_RATE_LIMIT_*`), sur
  `POST /api/game/series` et, en phase 2, sur le `join`.
- **Écart au plan** : un défi expiré est purgé, donc son code donne un 404 comme un code
  inconnu (pas de 410). Le front affichera « Défi introuvable ou expiré ».
- **Tests** : `SeriesDrawTest` (6 cas, dont 4 répétés 20 fois avec des seeds
  différents), `HintOfferTest` (+2), `FreeSeriesIntegrationTest` (13),
  `SeriesFunctionalTest` (6). `./mvnw test` : **1012 verts**.

## Phase 2 : Backend, défis
Status: Complete

- [x] `POST /api/game/series/{id}/share` : réservé au créateur, série terminée, renvoie le
      code (le même à chaque appel)
- [x] `GET /api/game/challenges/{code}` : présentation (créateur, taille), état du joueur,
      classement s'il a fini, 404 si le défi est inconnu ou expiré (purgé)
- [x] `POST /api/game/challenges/{code}/join` : pseudo obligatoire pour un invité, interdit
      pour un compte ; un seul `join` par joueur
- [x] Classement : joueurs qui ont fini, tri par score puis par heure de fin, nom affiché
      seulement
- [x] `PurgeSeries` (tâche planifiée) : série non partagée après 7 jours, défi 30 jours
      après son partage
- [x] Limite de débit sur `join`
- [x] Tests :
  - unitaires : génération du code, tri du classement ;
  - fonctionnels : partage puis `join` par un invité, puis un compte, puis classement ;
    partage refusé si la série n'est pas finie ou si on n'en est pas le créateur ;
    classement caché tant qu'on n'a pas fini ; pas de seconde tentative ; défi expiré en
    404 ; pseudo invalide en 400 ;
  - intégration : purge (7 et 30 jours, bornes comprises), suppression de compte puis
    classement.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert

### Phase Summary
- **`POST /api/game/series/{id}/share`** `{nickname?}` :
  - réservé au créateur (sinon 409 `NOT_CREATOR`, ou 404 pour qui n'est pas joueur) ;
  - la série doit être terminée (sinon 409 `SERIES_NOT_FINISHED`) ;
  - un créateur invité donne un pseudo (sinon 400 `NICKNAME_REQUIRED`), qui est posé
    sur sa ligne de joueur ;
  - partager de nouveau renvoie le même code. `ShareCodes` : 8 caractères Crockford tirés
    avec `SecureRandom`, et jusqu'à 5 essais en cas de collision.
- **`GET /api/game/challenges/{code}`** : public, sans cookie. Il renvoie le nom et le
  type du créateur, la taille, le nombre de manches et l'état du visiteur
  (`NOT_JOINED`, `PLAYING`, `FINISHED`). `seriesId`, le score et le classement ne sont
  donnés qu'au joueur ; le classement, seulement une fois qu'il a fini. Le code est lu
  sans tenir compte de la casse ni des espaces.
- **`POST /api/game/challenges/{code}/join`** `{nickname?}` : pose le cookie invité. Le
  pseudo est obligatoire pour un invité et ignoré pour un compte. Rejoindre de nouveau ne
  change rien, pas même le pseudo, et ne fait jamais repartir de zéro.
- **Classement** (`Challenges.leaderboard`) : seuls les joueurs qui ont fini toutes les
  manches jouables apparaissent. Tri par points puis par heure de fin ; à points égaux,
  même rang (1, 1, 3). Chaque ligne donne le nom, `guest`, les points, le max et `you`.
  Le nom d'un compte vient de `users.username` par une jointure SQL native, car `game` ne
  peut pas importer `auth` (ArchUnit). Il est donc toujours à jour, et disparaît avec le
  compte.
- **`PurgeSeries`** : tâche planifiée tous les jours à 00 h 40 UTC, `SeriesProperties`
  (`app.game.series.*`, 7 et 30 jours), désactivée dans les profils ponctuels. Le
  `DELETE` de masse vide le cache JPA (`clearAutomatically`).
- **Limite `series`** aussi sur `join` (regex sur le chemin).
- **Tests** : `ShareCodesTest` (2), `ChallengesLeaderboardTest` (2),
  `PurgeSeriesIntegrationTest` (2, bornes à ±1 minute), `SeriesFunctionalTest` (+4 :
  défi à deux invités, compte sous son nom d'utilisateur, refus du partage, code
  inconnu), `RateLimitFunctionalTest` (+1). `./mvnw test` : **1023 verts**.

## Phase 3 : Front, Enquête libre
Status: Complete

- [x] `game-api.ts` : base d'URL de manche (partie du jour ou série), appels des séries
- [x] Route `/free` : choix de la longueur, jeu (`round`, `round-result`), bilan
      (« Nouvelle série », « Défier des amis »), reprise d'une série en cours
- [x] Vocabulaire (`game-vocabulary.ts`, voix enquête et repli neutre) et
      `messages.fr.xlf`
- [x] Lien dans l'en-tête et depuis le bilan de la partie du jour
- [x] Specs Vitest : choix de la longueur, enchaînement des manches, nouvelle série,
      reprise

### Verification Plan
- `cd frontend && pnpm test && pnpm lint && pnpm format:check && pnpm build` → vert, sans
  traduction manquante

### Phase Summary
- **`RoundsScreen<T>`** (`features/game/rounds-screen.ts`, classe abstraite `@Directive`) :
  ce que `Game` faisait de ses manches (charger, jouer une étape, passer à la suivante,
  recharger sur un 409, réessayer une photo) est extrait. `Game` et `FreeGame` en
  héritent et ne disent que comment lire leurs manches et où envoyer une étape. Le
  template et la spec de `Game` sont inchangés (la spec fournit seulement le routeur).
- **`SeriesApi`** (séries et défis), et dans `models.ts` : `GameScore`, `PlayedRounds`,
  `Series`, `Challenge`, `LeaderboardEntry`, `SERIES_SIZES`.
- **`/free`** (`FreeGame`) :
  - au chargement, reprise de la série en cours (`/current`), sinon choix de la
    longueur ;
  - jeu, puis bilan (`DaySummary`, devenu générique avec `ng-content` pour la suite) et
    « Nouvelle enquête ». Une série plus courte que demandé le dit ; un pool vide affiche
    `NO_PHOTOS`.
- **Liens** (écart au plan) : l'en-tête est plein sur téléphone. Le lien « Enquête
  libre » de l'en-tête n'apparaît qu'à partir de `sm`. Sur les écrans de jeu, un lien
  vers l'autre mode est en bas, à côté du sélecteur d'indices, pour ne jamais repousser
  le champ Âge. Le bilan du jour a un bouton « Pas assez ? Ouvrez une enquête libre ».
- **Vocabulaire** (voix enquête / repli neutre) : `freeMode`, `chooseLength`,
  `newSeries`, `tryFree`, `seriesLength()`, `shorterSeries()`, `freeIntro`, `noPhotos`.
  15 messages traduits en français.
- **Specs** : `free-game.spec.ts` (5), `game.spec.ts` (+1). `pnpm test` **254 verts**,
  lint, format, build EN + FR OK.

## Phase 4 : Front, Défi et partage
Status: Not started

- [ ] « Défier des amis » : partage (`navigator.share`, sinon copie), texte de résultat
- [ ] Route `/challenge/:code` : présentation, pseudo pour un invité, jeu, classement,
      états « déjà joué » et « expiré »
- [ ] Textes FR et EN
- [ ] Specs Vitest : texte de partage, pseudo, classement, expiré, déjà joué

### Verification Plan
- `cd frontend && pnpm test && pnpm lint && pnpm format:check && pnpm build` → vert

### Phase Summary
_(à écrire à la fin de la phase)_

## Phase 5 : Parcours, documentation, reviews
Status: Not started

- [ ] Playwright component (EN et FR) : `/free` sur téléphone (photo et champ visibles),
      défi en invité jusqu'au classement. Exécuté par la CI uniquement.
- [ ] Journey : série, partage, puis un second joueur rejoint et apparaît dans le
      classement (non exécuté en CI, à signaler dans la PR)
- [ ] `/privacy` FR et EN : séries, défis, pseudos d'invités, durées (7 et 30 jours) ;
      date mise à jour
- [ ] Docs : `architecture.md` (séries, tirage, code de partage), `design.md` (noms des
      modes), `testing-strategy.md` si besoin, `roadmap.md`
- [ ] `architecture-reviewer` et `code-reviewer` (code de partage, pseudos, accès aux
      séries, moissonnage, RGPD), puis appliquer leurs retours

### Verification Plan
1. `./mvnw test` → vert
2. `pnpm test`, lint, format, `pnpm build` → vert
3. Reviewers → rien de bloquant ni d'important non traité
4. Après le push : CI verte (Playwright EN et FR compris)

### Phase Summary
_(à écrire à la fin de la phase)_

## Tests : catégories retenues
- **Unitaire backend** : oui, pour le tirage, le choix de l'indice, le code de partage et
  le classement.
- **Intégration** : oui, pour les contraintes de V15, les cascades et la purge.
- **Fonctionnel** : oui, pour la série complète, le partage, `join`, l'accès refusé et
  l'expiration.
- **Unitaire front** : oui, pour les écrans `/free` et `/challenge` et le texte de
  partage.
- **Interface (Playwright, en CI)** : oui, pour la mise en page sur téléphone et le défi
  en invité.
- **QA e2e** : oui, pour le parcours à deux joueurs. Il est écrit, mais ne tourne ni en
  local ni en CI.
- **Architecture** : règles existantes. Les nouveaux use cases restent dans
  `features/game`.
- **Mutation** : `SeriesDraw` et le classement, dans la CI vers `main`.

## Edge cases
- Pool plus petit que la longueur demandée : la série est plus courte, et l'écran le dit.
- Photo retirée (curation) en cours de série ou de défi : la manche est neutralisée, comme
  dans la partie du jour.
- Le créateur ouvre son propre lien de défi : il voit son résultat et le classement.
- Un invité efface ses cookies, puis rouvre le défi : c'est un nouveau joueur. C'est
  accepté, il n'y a pas d'identité plus forte pour un invité.
- Deux onglets jouent la même manche : 409, comme dans la partie du jour.
- Suppression du compte du créateur : le défi disparaît avec la série (cascade). C'est à
  confirmer à la review, sinon on garde le défi en anonymisant le créateur.
- Un défi est ouvert dans l'autre langue : les indices suivent la langue choisie par
  chaque joueur, comme dans la partie du jour.

## Final Recap
_(à écrire quand toutes les phases sont terminées)_

## Deployment Plan
_(à écrire quand toutes les phases sont terminées)_

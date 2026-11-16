# Calendrier des défis passés (et fin des séries)

Une page `/calendar`, mois par mois, une case par jour colorée selon l'état (non
disponible / disponible / commencé / terminé), d'où l'on joue n'importe quel défi
passé pas encore fini. Ouverte à tous, invités compris. Les séries de jours (streaks)
disparaissent partout : un jour rattrapé compte comme les autres (historique, stats,
classement entre amis).

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions (validées avec l'utilisateur le 2026-11-16)
- **Les séries sont supprimées** (« n'apportent pas grand chose ») : plus de
  `currentStreak` / `bestStreak` dans l'API, le profil, le classement, les textes.
  Conséquence : rattraper un jour ne « répare » rien, il n'y a plus rien à protéger,
  donc un jour rattrapé compte comme un jour joué à la date (historique, moyenne,
  meilleur score, classement entre amis : moyenne par partie, inchangé).
- **Ouvert à tous** : les invités jouent les jours passés avec le cookie invité ; la
  récupération à la connexion (`ClaimGuestPlays`) les rattache au compte, sans changement.

## Hypothèses (non bloquantes, à confirmer à la relecture)
- **États d'un jour**, calculés côté API, pour le joueur courant :
  - `UNAVAILABLE` : jour futur, ou aucune manche tirée ce jour-là (avant le lancement,
    tirage raté), ou plus aucune manche jouable (toutes neutralisées sans avoir été
    jouées) ;
  - `AVAILABLE` : tiré, au plus aujourd'hui, aucune manche jouée ;
  - `STARTED` : au moins une manche jouée, pas toutes les manches jouables finies ;
  - `FINISHED` : toutes les manches jouables finies (même règle que « jour complet » de
    `PlayerHistory`, réutilisée et non recopiée).
- **Pas de tirage rétroactif** : un jour sans tirage reste indisponible. Le tirage reste
  celui du jour (scheduler + premier visiteur).
- **Aujourd'hui** apparaît dans le calendrier et mène à `/` (la page du jour reste l'entrée
  principale). Un jour passé mène à `/day/AAAA-MM-JJ`.
- **Navigation** : le calendrier s'ouvre sur le mois courant ; flèches mois
  précédent / suivant, bornées au premier mois ayant un tirage et au mois courant.
  Un onglet « Calendar » / « Calendrier » dans le header, après « Free mode ».
- **Récap d'un jour passé** : même écran de fin que le jour, sans « Revenez demain » ni
  bouton de partage (le texte de partage parle du défi du jour) ; à la place, un lien
  « Retour au calendrier ».
- **Pas d'indication du score dans les cases** : la couleur seule (plus un libellé
  accessible). À ajouter plus tard si besoin.

## Phase 1 : Supprimer les séries
Status: Complete

Backend :
- [x] `features/game/PlayerHistory.java` : retirer le calcul des séries, `Stats` devient
      `(gamesPlayed, averagePoints, bestPoints)` ; mettre à jour la Javadoc (règles)
- [x] `features/game/GetHistory/HistoryResponse.java` et `GetHistoryHandler.java` : plus de
      champs de série
- [x] `features/friends/GetFriendsLeaderboard/LeaderboardRanking.java`,
      `FriendsLeaderboardResponse.java`, `GetFriendsLeaderboardHandler.java` : retirer
      `currentStreak`
- [x] Javadocs qui citent la série : `ClaimGuestPlays/ClaimGuestPlaysHandler.java`,
      `architecture/ArchitectureTests.java`
- [x] Tests : `PlayerHistoryTest` (retirer les cas de série, garder jours/stats),
      `GetHistoryIntegrationTest`, `PlayerHistoriesIntegrationTest`,
      `functional/game/GameFunctionalTest`, tests du classement

Frontend :
- [x] `features/game/models.ts`, `features/friends/models.ts` : retirer les champs
- [x] `features/game/profile/profile.{html,ts,spec.ts}` : retirer la section série et
      « Meilleure série » ; garder parties jouées / moyenne / meilleur score
- [x] `features/friends/friends-leaderboard/friends-leaderboard.{html,spec.ts}`,
      `friends-text.ts`, `friends/testing.ts` : retirer la colonne
- [x] `features/auth/register/register.html` : « Track your streak… » → « Keep your
      history and compare scores with friends. »
- [x] `features/legal/privacy/privacy.html` (l. 85) : les amis ne voient plus de série
- [x] `locale/messages.xlf` + `messages.fr.xlf` : retirer les unités de série, traduire
      les textes modifiés (`pnpm extract-i18n` ou équivalent du projet)
- [x] E2E : `e2e/component/profile.spec.ts`, `component-fr/profile.spec.ts`,
      `component/friends.spec.ts`, `component-fr/friends.spec.ts`,
      `journeys/daily-game.spec.ts` (le parcours « invité puis inscription » vérifie le
      jour et la partie jouée au profil, plus la série)

### Verification Plan
- `grep -rni streak ageguessr-api/src frontend/src frontend/e2e --exclude=common-passwords.txt`
  → aucune ligne
- `cd ageguessr-api && ./mvnw test` → BUILD SUCCESS ; `cd frontend && pnpm test` → tout vert

### Phase Summary
Séries retirées de `PlayerHistory` (et de son paramètre `today`), de l'historique, du
classement entre amis, du profil (la grille passe à 3 tuiles), de l'inscription et de
la page de confidentialité EN/FR (date `PRIVACY_UPDATED` passée au 2026-11-16 ; la
ligne sur la purge invités dit déjà « 7 jours après avoir été jouées », anticipant la
phase 2). `PlayerHistories.today()` est conservé : `GetFriendsToday` l'utilise.
Vérifié : grep « streak » vide (hors Javadoc « no streaks »), `./mvnw test` vert,
`pnpm test` vert après correction du spec du classement, `pnpm lint` propre.

## Phase 2 : Ouvrir les jours passés (backend)
Status: Complete

- [x] `GameRounds.playable(LocalDate, short)` : accepter tout jour `<= aujourd'hui` ayant
      un tirage ; un jour futur reste refusé (`GAME_CLOSED`, message « This day is not
      open yet ») ; Javadoc de `GameErrors.gameClosed` mise à jour
- [x] Extraire la construction « jour + manches + score » de `GetTodayGameHandler` dans
      `features/game/DayGames.java` (composant de la feature, pas de couplage
      handler→handler) ; `TodayGameResponse` → `features/game/DayGameResponse.java`
- [x] Nouveau use case `features/game/GetDayGame/` : `GET /api/game/days/{date}`
      (`GetDayGameController`, `GetDayGameHandler`) ; 404 si le jour n'a pas de tirage,
      409 `GAME_CLOSED` si futur ; ne tire jamais (pas de `drawIfMissing`)
- [x] `PurgeGuestPlays` : purger sur `started_at` (7 jours après avoir joué) au lieu de
      `game_date`, sinon une partie invitée d'un jour ancien serait purgée la nuit
      même ; `GamePlayRepository.deleteGuestPlaysBefore(Instant)`
- [x] Migration `V28__guest_plays_purge_on_started_at.sql` : remplacer l'index
      `game_plays_guest_date_idx` par un index partiel sur `started_at` (invités)
- [x] `ReportRound` : rien à changer (le signalement lit déjà un jour quelconque) —
      vérifier par un test qu'on signale une manche d'un jour passé rattrapé

### Tests (Phase 2)
- Unitaire : règle de date de `GameRounds.playable` (passé OK, aujourd'hui OK, futur
  refusé) si testable sans base, sinon en intégration
- Intégration : `PurgeGuestPlaysIntegrationTest` (une partie invitée d'un jour ancien
  jouée hier n'est pas purgée ; une jouée il y a 8 jours l'est) ; `DayGames` contre la base
- Fonctionnel : `GET /api/game/days/{date}` (invité sans cookie, invité, compte ; jour
  passé, aujourd'hui, futur 409, sans tirage 404, date invalide 400) ; premier essai,
  indice, second essai sur un jour passé ; refus sur un jour futur

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → BUILD SUCCESS

### Phase Summary
`GameRounds.playable` n'ouvre plus que « pas dans le futur » ; `DayGames` (composant)
construit un jour pour un joueur, partagé par `GetTodayGame` et le nouveau `GetDayGame`
(`GET /api/game/days/{date}`, 409 futur, 404 sans tirage, jamais de tirage).
`DayGameResponse` remplace `TodayGameResponse` (même forme JSON). Purge invités sur
`started_at` + `V28` (index partiel). Signalement : inchangé, `ReportRound` lit déjà
`rounds.of(date, position)` pour tout jour — pas de test dédié ajouté.
Vérifié : `GetDayGameHandlerTest`, `GameRoundsTest`, `GetTodayGameHandlerTest`,
`PurgeGuestPlaysIntegrationTest`, `GameFunctionalTest`, `ReportFunctionalTest` verts.

## Phase 3 : Calendrier (backend)
Status: Complete

- [x] `features/game/DayState.java` : `UNAVAILABLE`, `AVAILABLE`, `STARTED`, `FINISHED`
- [x] Nouveau use case `features/game/GetCalendar/` : `GET /api/game/calendar?month=AAAA-MM`
      → `{ month, firstMonth, days: [{ date, state }] }` (tous les jours du mois) ;
      `month` absent = mois courant ; validé (format, pas après le mois courant, pas
      avant `firstMonth`) ; joueur facultatif (`PlayerResolver.existing`), ne crée pas
      de cookie
- [x] Règle pure `features/game/GetCalendar/CalendarDays.java` (pas de base, pas
      d'horloge : aujourd'hui en paramètre) ; réutilise la notion de jour complet de
      `PlayerHistory` (extraire une méthode publique si besoin, pas de copie)
- [x] Requêtes : manches jouables par jour du mois (`DailyRoundRepository`), parties du
      joueur sur le mois (`GamePlayRepository`), premier jour tiré

### Tests (Phase 3)
- Unitaire : `CalendarDaysTest` — chaque état, jour futur, jour sans tirage, manche
  neutralisée avant / après avoir été jouée, mois partiel (aujourd'hui au milieu)
- Intégration : les requêtes du mois (bornes du mois, autre joueur ignoré)
- Fonctionnel : `GET /api/game/calendar` sans cookie (tout `AVAILABLE`/`UNAVAILABLE`),
  invité, compte ; mois futur 400, mois avant le premier tirage 400, format invalide 400

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → BUILD SUCCESS

### Phase Summary
`GET /api/game/calendar?month=AAAA-MM` (`GetCalendar`) : `CalendarDays` (règle pure,
réutilise `PlayerHistory.day`, rendu public), `DayState`, `CalendarResponse`
(`month`, `firstMonth`, `days`). Requêtes : `DailyRoundRepository.findStatesBetween`,
`findFirstGameDate`, `GamePlayRepository.findOf(player, from, to)`. Erreur
`MONTH_OUT_OF_RANGE` (400) hors [premier mois, mois courant]. Les requêtes du mois sont
couvertes par le test fonctionnel (base réelle) plutôt qu'un test d'intégration séparé.
Vérifié : `CalendarDaysTest` (9 cas), `GameFunctionalTest` (calendrier invité / sans
cookie, bornes), `PlayerHistoryTest` verts.

## Phase 4 : Calendrier et jour passé (frontend)
Status: Complete

- [x] `features/game/game-api.ts` + `models.ts` : `day(date)`, `calendar(month?)`,
      types `DayState`, `Calendar`
- [x] Jeu d'un jour passé : route `day/:date` ; réutiliser `Game` (date en entrée,
      `today()` ou `day(date)`) plutôt que dupliquer — si le composant devient
      ambigu, un `PastDayGame` mince sur `RoundsScreen`
- [x] Récap d'un jour passé : sans « Revenez demain » ni partage, lien « Back to
      calendar » ; date du jour affichée en titre
- [x] `features/game/calendar/calendar.{ts,html,spec.ts}` : grille lundi→dimanche,
      mois en toutes lettres selon la langue, flèches bornées, cases colorées par
      état avec légende ; case disponible / commencée = lien (`/` pour aujourd'hui,
      `/day/…` sinon) ; terminée = lien vers le récap ; indisponible = non cliquable
- [x] Accessibilité : chaque case a un libellé (« 12 novembre, commencé »), la couleur
      n'est jamais la seule information (légende + icône ou motif pour commencé/fini),
      contrastes en clair et sombre
- [x] Route `calendar` dans `app.routes.ts`, onglet dans `layout/header/header.ts`
      (desktop + menu mobile)
- [x] i18n : `i18n="@@calendar.*"` / `$localize`, traductions dans `messages.fr.xlf`
- [x] Liste des pages publiques du sitemap (`feature/seo`) : ajouter `/calendar` si les
      pages publiques y sont listées

### Tests (Phase 4)
- Unitaire (Vitest) : calendrier (grille, décalage du 1er du mois, bornes des flèches,
  liens par état), API, jeu d'un jour passé (bonne URL, récap sans partage)
- Interface (Playwright, `component` + `component-fr`) : `calendar.spec.ts` avec API
  mockée — rendu des 4 états, navigation entre mois, libellés FR ; jeu d'un jour passé
- QA e2e : un parcours `journeys/calendar.spec.ts` — ouvrir le calendrier, jouer un jour
  passé (seed d'un jour d'hier via `/test-support`), revenir, la case est « terminé ».
  Nécessite d'étendre `seed-daily-game` à une date donnée
- Architecture : rien de neuf (nouveaux use cases dans `game`, aucune nouvelle
  dépendance entre features) — les tests existants le vérifient
- Mutation : couverte par la CI sur la PR de release (PIT sur les tests unitaires,
  `CalendarDays` en est la cible principale)

### Verification Plan
- `cd frontend && pnpm test` → tout vert ; `pnpm lint` → sans erreur
- `cd ageguessr-api && ./mvnw test` → BUILD SUCCESS
- Playwright : en CI uniquement (pas de Playwright local sous WSL)
- Capture d'écran du calendrier en clair et sombre avec `shoot.mjs` (un seul navigateur)

### Phase Summary
`/calendar` (`features/game/calendar`) : grille lundi→dimanche, flèches bornées
(`firstMonth`, mois de `today` renvoyé par l'API — ajouté à `CalendarResponse` pour ne
pas dépendre du fuseau du navigateur), cases liées selon l'état, libellés accessibles,
légende, coche/pointillés en plus de la couleur. `Game` joue aussi `/day/:date`
(`ActivatedRoute`), titre « Partie du … », récap sans partage ni « revenez », retour au
calendrier ; le récap du jour propose « Jouer les jours passés ». Onglet header,
`/calendar` dans le sitemap + description SEO. `GAME_CLOSED` : texte « Ce jour n'est pas
encore ouvert ». Seed e2e `seed-daily-game?daysAgo=N`. `RateLimitFunctionalTest` visait
un jour de 2000 pour obtenir un 409 : passé à 2999 (jour à venir).
Vérifié : `pnpm test` 483 verts (+ test SEO), `pnpm lint` propre, `./mvnw test` vert
(1269), captures clair/sombre/mobile via build statique + un Chromium (pas de serveur de
dev). Playwright (component, component-fr, journeys/calendar) : en CI.

## Phase 5 : Docs, revues, PR
Status: Not started

- [ ] `docs/decisions/architecture.md` : jours passés jouables, états du calendrier,
      purge invités sur `started_at`, fin des séries
- [ ] `docs/roadmap.md` : ligne cochée avec la PR
- [ ] Agents `architecture-reviewer` et `code-reviewer` (endpoints + données joueur)
      sur le diff ; corriger leurs remarques
- [ ] PR `feature/daily-calendar` → `develop` (skill `/pr`) ; vérifications prod à
      ajouter en haut de la roadmap

### Verification Plan
- CI verte sur la PR (backend, frontend, Playwright)

### Phase Summary
_(write when phase completes)_

## Edge cases
- Jour futur demandé ou joué (URL forgée) → 409 `GAME_CLOSED`
- Jour sans tirage, ou avant le lancement → 404 / case indisponible
- Manche neutralisée après le tirage : ne bloque pas « terminé » ; un jour dont toutes
  les manches sont neutralisées et jamais jouées → indisponible
- Partie commencée avant minuit, reprise le lendemain : redevient jouable (c'était
  `GAME_CLOSED`), et la case du jour d'hier est « commencé »
- Invité qui rattrape un vieux jour puis se connecte : la partie passe au compte
- Purge invité : 7 jours après avoir joué, pas après la date du défi
- Fuseau : les jours restent ceux du tirage (UTC), comme aujourd'hui ; le calendrier
  affiche les dates de l'API, sans conversion locale
- Double clic / deux onglets sur un jour passé : index uniques existants (V13)

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

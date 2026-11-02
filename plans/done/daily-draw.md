# Tirage quotidien

Tirer chaque jour (minuit UTC) les photos de la partie, les mêmes pour tous les joueurs,
et les garder en base. Hors scope : l'endpoint de jeu, le guess, les points, le cache
Redis et le signalement (PR suivantes, voir « Décisions consignées pour la suite »).

## For Future Agents
Au fil du travail : cocher les cases `- [x]`. Quand une phase est finie, passer son
statut à `Complete` et écrire son **Phase Summary**. Lancer le **Verification Plan** de
la phase et noter le résultat avant de passer à la suivante. Ce fichier est la source de
vérité, pas la conversation.

## Contexte pour repartir à froid
- Branche : `feature/daily-draw`, créée depuis `origin/develop` (sans upstream : pousser
  avec `git push -u origin feature/daily-draw`). Ne jamais commiter sur `develop` ou
  `main`. La PR #28 (roadmap des indices) était encore ouverte au démarrage : l'utilisateur
  la merge lui-même.
- Exclure une personne supprime ses photos et leurs images, par un trigger (V4). Une
  photo `REJECTED` reste en base.
- `Person.gender` et `Person.continent` sont nullables (V9).
- `GetImageHandler` ne sert que les photos `APPROVED` de personnes non exclues, avec un
  404 unique pour tous les refus.
- Aucune photo ne doit être montrée aux joueurs avant l'affichage du crédit (Semaine 4).
  Cette PR n'expose donc **aucun endpoint public**.
- `ClockConfig` fournit une `Clock` UTC injectable : c'est elle qui donne « aujourd'hui »
  dans tout le code.

## Décisions validées avec l'utilisateur (2026-11-02)
- **Nombre de photos par jour** : 3 pour l'instant, en propriété
  (`app.game.daily.rounds`). L'utilisateur n'est pas sûr de garder ce chiffre.
- **Remise à zéro** : minuit UTC, même partie pour tous.
- **Pas de rejeu d'une photo avant 3 ans** (`app.game.daily.replay-years`).
- **Jamais deux personnes de la même case** (continent × genre) le même jour. La règle
  couvre aussi « jamais deux photos d'une même personne dans une partie ».
- **Pas de délai entre deux apparitions d'une même personne** : on devine l'âge sur une
  photo donnée, et connaître la personne n'aide pas.
- **Âges tirés au hasard pur**, sans pondération par tranche. Les règles qui portent sur
  l'âge révèlent de l'information (« la première a 22 ans, donc les autres non »).
- **Photo rejetée par un admin pendant son jour** : la manche est neutralisée pour tous.
  Le score du jour se calcule sur les manches restantes, et la manche s'affiche « photo
  retirée ». Un simple signalement ne neutralise rien, sinon un script pourrait vider
  les parties.
- **Architecture** : nouvelle feature `features/game`, autorisée à dépendre de `people`,
  jamais l'inverse. Les règles ArchUnit encodent cette exception et son sens.

## Décisions d'implémentation (prises dans ce plan, non encore validées)
- **Tirage paresseux et idempotent**, dans `DrawDailyGameHandler.drawIfMissing(date)`.
  Si la date a déjà ses manches, il les renvoie ; sinon il tire et les enregistre. Un
  `@Scheduled` à 00:00:05 UTC l'appelle pour aujourd'hui, et `ApplicationReadyEvent`
  aussi : un redémarrage après minuit rattrape le jour.
  - Pourquoi pas seulement le cron : si l'app est arrêtée à minuit, le jour n'a pas de
    partie. Pourquoi pas seulement le paresseux : sans endpoint public dans cette PR,
    rien ne le déclencherait en prod.
  - Plusieurs instances, ou le cron et le démarrage en même temps : la clé primaire
    `(game_date, position)` refuse le second tirage, et le perdant relit celui du
    gagnant.
- **Tirage en mémoire, pas en SQL** : une requête charge les candidats (~5 000 lignes :
  id photo, genre, continent). Une classe pure, `DailyDraw`, tire ensuite avec une
  `RandomGenerator` injectée. `ORDER BY random()` ne se teste pas de façon déterministe.
- **Hasard uniforme sur les photos**, pas sur les personnes : une personne qui a 3
  photos a 3 fois plus de chances d'être tirée. C'est le « hasard tout court » demandé,
  et chaque photo garde sa chance d'être jouée en 3 ans.
- **Case inconnue** (genre ou continent nul) : c'est une valeur à part entière (`null`
  compte comme une case). Une seule personne de genre inconnu par jour en Europe, par
  exemple. *Hypothèse* : il n'y a presque plus de nuls après les vagues d'ingestion. À
  mesurer en phase 1.
- **Pool trop petit** (dev, tests, pool épuisé) :
  - on garde la règle des cases et on relâche d'abord celle des 3 ans, en prenant les
    photos jouées il y a le plus longtemps ;
  - si les cases distinctes ne suffisent toujours pas, on tire moins de manches et on le
    signale en `log.error`, sans jamais planter.

  Avec 5 000 photos et 3 285 tirages en 3 ans, la prod ne devrait jamais y arriver.
- **Nouveau `publicId` à chaque tirage d'une photo**. Sinon, le couple id → réponse
  d'une ancienne partie, déjà publié, resservirait 3 ans plus tard
  (`architecture.md`, Anti-triche).
  - On le régénère au tirage, et pas au premier affichage, parce que le tirage n'arrive
    qu'une fois par jour et par photo.
  - Limite connue : un historique de partie qui servirait une ancienne image par son
    `publicId` actuel la relierait à sa réponse. L'historique (Semaine 5) devra servir
    ses images autrement.
- **Neutralisation calculée à la lecture, pas stockée.** Une manche est neutralisée si
  `photo_id` est nul (personne exclue, photo supprimée) ou si la photo n'est plus
  `APPROVED`.
  - `people` n'a ainsi pas à prévenir `game`, ce qui respecte le sens de dépendance.
  - Un rejet annulé aussitôt (touche U en curation) rétablit la manche.
  - Conséquence, à reprendre avec les scores (Semaine 5) : un rejet tardif neutralise
    aussi la manche dans l'historique.
- **Effacement respecté** : `daily_rounds.photo_id` est en `ON DELETE SET NULL`. La
  ligne garde la date et la position, mais plus rien sur la personne.
- **Pas de cache Redis** (validé par l'utilisateur, 2026-11-02). La table `daily_rounds`
  évite déjà de recalculer la partie, et lire 3 lignes par clé primaire ne coûte rien.
  Un cache imposerait que `people` invalide `game` (sens interdit), ou un TTL qui
  laisserait une photo rejetée en jeu. Dans la roadmap, « Cache Redis des parties du
  jour » devient « Cache HTTP des images » (`Cache-Control` long sur
  `/api/images/{publicId}`, possible puisque l'id change à chaque tirage), à faire avec
  l'endpoint de jeu.

## Décisions consignées pour la suite (hors scope, à écrire dans la doc en phase 3)
- **Guess** : le chaud/froid est remplacé par « plus jeune / plus vieux » après le 1er
  essai, sans bonus de proximité (« brûlant » refusé). Puis catégorie d'indice, puis 2e
  essai. Saisie de 18 à 100 ans.
- **Points** : chaque essai vaut jusqu'à 500 points selon l'écart (piste :
  500 × e^(−écart/10), à régler en playtest). Une manche vaut la somme des deux essais
  (/1 000) et la journée /3 000. Un 1er essai exact termine la manche à 1 000. Les points
  du 1er essai ne s'affichent qu'au résultat. Cela remplace la décision « majorité des
  points sur le 2e coup ».
- **Signalement des photos** : notifier l'admin de façon prononcée (email par exemple)
  quand plusieurs joueurs signalent une même photo, surtout si elle est dans la partie
  du jour.

## Fichiers
À créer :
- `ageguessr-api/src/main/resources/db/migration/V12__create_daily_rounds.sql`
- `ageguessr-api/src/main/java/app/ageguessr/features/game/DailyRound.java` (entité,
  clé composite `DailyRoundId`)
- `ageguessr-api/src/main/java/app/ageguessr/features/game/DailyRoundId.java`
- `ageguessr-api/src/main/java/app/ageguessr/features/game/DailyRoundRepository.java`
- `ageguessr-api/src/main/java/app/ageguessr/features/game/GameProperties.java`
  (`app.game.daily.rounds`, `app.game.daily.replay-years`)
- `ageguessr-api/src/main/java/app/ageguessr/features/game/GameConfig.java`
  (`@EnableConfigurationProperties`, bean `RandomGenerator`)
- `ageguessr-api/src/main/java/app/ageguessr/features/game/DrawDailyGame/DrawCandidate.java`
  (record : photoId, gender, continent, lastPlayedOn nullable)
- `ageguessr-api/src/main/java/app/ageguessr/features/game/DrawDailyGame/DailyDraw.java`
  (algorithme pur)
- `ageguessr-api/src/main/java/app/ageguessr/features/game/DrawDailyGame/DrawDailyGameHandler.java`
- `ageguessr-api/src/main/java/app/ageguessr/features/game/DrawDailyGame/DailyDrawScheduler.java`
- `ageguessr-api/src/main/java/app/ageguessr/features/game/DailyRoundStatus.java`
  (`PLAYABLE` / `NEUTRALIZED`, calculé à la lecture)
- Tests : `features/game/DrawDailyGame/DailyDrawTest.java`,
  `features/game/DrawDailyGame/DrawDailyGameHandlerTest.java`,
  `integration/game/DailyRoundsIntegrationTest.java`

À modifier :
- `ageguessr-api/src/main/resources/application.properties` (propriétés `app.game.daily.*`)
- `ageguessr-api/src/main/java/app/ageguessr/config/` : `@EnableScheduling`
  (`SchedulingConfig.java`)
- `ageguessr-api/src/test/resources/application.properties` : désactiver le cron et le
  tirage au démarrage en test (`app.game.daily.draw-on-startup=false`), sinon chaque
  contexte Spring tire une partie
- `ageguessr-api/src/test/java/app/ageguessr/architecture/ArchitectureTests.java`
- `docs/decisions/architecture.md`, `docs/roadmap.md`, `docs/testing-strategy.md` (si
  la règle ArchUnit y est citée)

## Phase 1 : Schéma et algorithme pur
Status: Complete

- [x] Mesurer en local (base restaurée ou dev) les photos `APPROVED` avec genre ou
      continent nul. Noter le chiffre ici. Ne requêter la prod qu'avec l'accord de
      l'utilisateur. **Résultat** : la base locale n'a aucune photo APPROVED (59
      PENDING, dont 1 de case inconnue, et 8 REJECTED). Mesure en prod à faire au
      déploiement (requête dans le Deployment Plan).
- [x] `V12__create_daily_rounds.sql` : `daily_rounds(game_date DATE, position SMALLINT
      CHECK (position >= 1), photo_id UUID REFERENCES person_photos(id) ON DELETE SET
      NULL, drawn_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (game_date,
      position))`. Ajouter :
  - un index sur `photo_id`, pour la règle des 3 ans et pour le `SET NULL` ;
  - un unique partiel `(game_date, photo_id) WHERE photo_id IS NOT NULL` ;
  - un commentaire SQL sur l'effacement et la neutralisation.
- [x] Entité `DailyRound`, `DailyRoundId`, `DailyRoundRepository`
- [x] `GameProperties` validées (`@Positive rounds`, `@Positive replayYears`,
      `drawOnStartup`) et `GameConfig`
- [x] `DrawCandidate` + `DailyDraw.draw(candidates, rounds, today, replayYears,
      random)` :
  - filtre les candidats jouables (jamais joués, ou joués avant `today − replayYears`) ;
  - tire au hasard uniforme, une manche à la fois, en retirant à chaque fois la case
    choisie ;
  - repli sur les photos jouées il y a le plus longtemps, puis tirage partiel.
- [x] `DailyDrawTest` (unitaire). Avec une `Random` à graine fixe, vérifier :
  - jamais deux fois la même case ;
  - la case `null` traitée comme une valeur à part entière ;
  - une photo jouée il y a 3 ans moins un jour exclue, à 3 ans pile admise ;
  - le repli vers les photos les moins récemment jouées quand les jouables manquent ;
  - le tirage partiel quand les cases manquent ;
  - 0 candidat donne 0 manche ;
  - `rounds` configurable (1, 3, 5) ;
  - l'uniformité, grossièrement : sur 10 000 tirages d'un pool à 3 photos par case,
    chaque photo sort entre 80 % et 120 % de la moyenne.

### Verification Plan
- `cd ageguessr-api && ./mvnw test -Dtest=DailyDrawTest` → tout vert
- `cd ageguessr-api && ./mvnw test -Dtest=ArchitectureTests` → rouge attendu
  (`game` → `people`) tant que la phase 2 n'a pas adapté la règle. Le noter.

**Résultat** : `DailyDrawTest` 13/13 vert. La règle ArchUnit a été adaptée dans la même
passe que la phase 2, donc pas de run rouge intermédiaire.

### Phase Summary
- V12 crée `daily_rounds` : clé primaire `(game_date, position)`, `photo_id` en
  `ON DELETE SET NULL`, un index sur `photo_id`, un unique partiel `(game_date,
  photo_id)` et `CHECK (position >= 1)`.
- `DailyDraw` reçoit `GameProperties` et une `RandomGenerator` (`SecureRandom` en
  prod, `Random` à graine fixe en test) et renvoie `Outcome(photoIds,
  replayedEarly)`.
- Écart avec le plan : les candidats viennent de `DrawCandidateRepository`, un
  repository Spring Data dans le use case, et pas de `DailyRoundRepository` : la requête
  ne sert qu'au tirage.
- `DrawCandidate` est public, pour la requête JPQL et le test d'intégration.

## Phase 2 : Handler, planification, règles d'architecture
Status: Complete

- [x] Requête des candidats dans `DailyRoundRepository` (JPQL). Elle prend les photos
      `APPROVED` de personnes non exclues, avec genre, continent et
      `MAX(daily_rounds.game_date)` (la dernière date jouée), en `LEFT JOIN`.
- [x] `DrawDailyGameHandler.drawIfMissing(LocalDate)`, `@Transactional` :
  - relit d'abord les manches existantes ;
  - sinon tire, régénère le `publicId` des photos tirées (`UUID.randomUUID()`) et
    insère les manches, puis `saveAndFlush` ;
  - sur `DataIntegrityViolationException` (tirage concurrent), relit dans une nouvelle
    transaction. Solution : `TransactionTemplate` ou un appel à travers le proxy, à
    choisir et à expliquer en commentaire.
  - Logs sans nom ni fichier Commons, uniquement date, nombre de manches et ids.
- [x] Lecture du statut d'une manche (`DailyRoundStatus`) : `NEUTRALIZED` si
      `photo_id` est nul ou si la photo n'est plus `APPROVED`. La méthode reste
      utilisable par la future PR de jeu.
- [x] `DailyDrawScheduler` : `@Scheduled(cron = "5 0 0 * * *", zone = "UTC")` et
      `@EventListener(ApplicationReadyEvent)`, si `drawOnStartup` est vrai. Les deux
      appellent `drawIfMissing(LocalDate.now(clock))`. Une exception est loguée et ne
      tue jamais le planificateur.
- [x] `config/SchedulingConfig` (`@EnableScheduling`)
- [x] `ArchitectureTests` :
  - remplacer `features_do_not_depend_on_each_other` par la même règle avec
    `game → people` comme seule exception ;
  - ajouter `people_does_not_depend_on_game` ;
  - commenter le pourquoi de l'exception.
- [x] `DrawDailyGameHandlerTest` (unitaire, Mockito) :
  - une date déjà tirée n'appelle ni le tirage ni l'écriture ;
  - un nouveau tirage régénère chaque `publicId` ;
  - un conflit à l'insertion relit les manches du gagnant ;
  - `LocalDate` vient de la `Clock` dans le scheduler.
- [x] `DailyRoundsIntegrationTest` (Testcontainers) :
  - la contrainte de clé primaire ;
  - exclure la personne passe `photo_id` à NULL et garde la ligne, sans cascade ;
  - photo REJECTED → `NEUTRALIZED`, et retour à APPROVED → `PLAYABLE` ;
  - la requête des candidats ignore les photos PENDING et REJECTED et les personnes
    exclues, et renvoie la bonne dernière date jouée ;
  - `drawIfMissing` deux fois → les mêmes manches ;
  - le `publicId` change en base après le tirage ;
  - deux appels concurrents (2 threads) → une seule partie.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → tout vert (y compris `ArchitectureTests`)
- `docker compose up -d` puis lancer l'API en local :
  `psql -h localhost -p 5433 -U ageguessr -c "SELECT * FROM daily_rounds"` → 3 lignes
  pour aujourd'hui (s'il y a au moins 3 cases avec des photos APPROVED en local), et un
  redémarrage n'en ajoute pas.

**Résultat** :
- `./mvnw test` : 791 tests, 0 échec.
- Le test de concurrence a bien provoqué `daily_rounds_pkey` sur Postgres, et le perdant
  a relu le gagnant.
- `spring-boot:run` en local : V12 appliquée, démarrage OK, tirage au démarrage
  exécuté. Il donne 0 manche (aucune photo approuvée en local), avec `log.error` et
  sans plantage. Un tirage vide n'enregistre rien et se retente au prochain appel.

### Phase Summary
- `drawIfMissing` passe par une `TransactionTemplate` : le perdant d'un tirage
  concurrent relit dans une nouvelle transaction.
- Les manches sont insérées **avant** la régénération des `publicId`, pour qu'un tirage
  concurrent échoue avant de verrouiller une ligne photo.
- `DailyRoundRepository.findStatesOf(date)` calcule `PLAYABLE` / `NEUTRALIZED` en
  `LEFT JOIN` (photo APPROVED et personne non exclue).
- `DailyDrawScheduler` est conditionné par `app.game.daily.draw-enabled`, faux en test
  et dans les profils `ingest` et `import-hints` : une ingestion qui passe minuit ne
  tire rien. Le planificateur catche les erreurs pour que l'API démarre quand même.
- `SchedulingConfig` porte `@EnableScheduling` dans `config/`.
- La javadoc de `Person.gender` / `Person.continent` mentionne maintenant l'usage par le
  tirage.

## Phase 3 : Documentation et reviews
Status: Complete

- [x] `docs/decisions/architecture.md` :
  - nouvelle section « Tirage quotidien » (règles, paresseux et idempotent,
    neutralisation à la lecture, repli, `publicId`) ;
  - mise à jour de « Système de points » (500 par essai, somme, 1er essai exact = 1 000) ;
  - remplacement du chaud/froid par plus jeune / plus vieux ;
  - exception `game → people` dans « Vertical Slice Architecture » ;
  - « équilibre fin au tirage quotidien » corrigé dans la section âges.
- [x] `docs/roadmap.md` :
  - cocher le tirage quotidien et corriger son texte : cases au lieu du délai par
    personne, hasard pur pour les âges ;
  - réécrire « Logique de guess » (plus jeune / plus vieux) et « Système de points » ;
  - ajouter la notification admin au signalement des photos ;
  - trancher « que devient la partie du jour si une photo est rejetée » ;
  - noter que le cache Redis est à réévaluer.
- [x] Agents `architecture-reviewer` et `code-reviewer`. La PR touche des données de
      personnes et un effacement RGPD (`SET NULL`) : le second s'applique.
- [x] Appliquer les retours de review, puis relancer `./mvnw test`

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → tout vert
- `cd frontend && pnpm test` → tout vert (aucun changement front attendu, mais la règle
  avant push l'exige)
- `grep -n "chaud" docs/roadmap.md docs/decisions/architecture.md` → plus aucune mention
  du chaud/froid comme mécanique retenue

**Résultat** :
- `./mvnw test` : 791 tests, 0 échec. Un premier run avait 99 erreurs de chargement de
  contexte (conteneur Postgres de Testcontainers injoignable en cours de run, problème
  d'environnement) ; le run suivant est vert sans aucun changement.
- `pnpm test` : 187 tests verts.
- `chaud` n'apparaît plus que comme mécanique écartée.

### Phase Summary
- Doc : `architecture.md` (exception `game → people`, « Tirage quotidien », « Déroulé
  d'une manche », « Système de points » réécrit, limite du `publicId`), `roadmap.md`
  (tirage coché, guess, points, cache HTTP, notification et neutralisation au
  signalement), `testing-strategy.md`.
- Reviews appliquées (voir « Retours de review »).
- **Ajout après coup** : `app.game.daily.draw-enabled` est **faux par défaut**. En prod,
  avant que le jeu soit jouable, chaque jour tiré sortirait 3 photos du pool pour 3 ans
  (~90 par mois). La PR de l'endpoint de jeu le passe à vrai ; c'est noté dans la
  roadmap.

## Retours de review (phase 3)
- `code-reviewer` : rien de bloquant ni d'important. Ids de photos retirés du log du
  tirage (appliqué). **Pour la PR de jeu** : `RoundState.photoId` est l'id interne,
  il ne doit jamais être sérialisé dans une réponse. N'exposer que le `publicId`.
- `architecture-reviewer` : rien de bloquant ni d'important.
  - `docs/testing-strategy.md` mentionne maintenant l'exception `game → people`
    (appliqué).
  - Coût du filtre de `DailyDraw` en O(candidats × manches) : négligeable à 3 manches,
    et le passer par case changerait le modèle de hasard (uniforme sur les cases au
    lieu des photos). Laissé tel quel.

## Tests : catégories retenues
- **Unitaire** : oui. `DailyDraw` (le cœur des règles) et `DrawDailyGameHandler`
  (idempotence, `publicId`, conflit).
- **Intégration** : oui. Contraintes V12, `ON DELETE SET NULL` combiné au trigger
  d'exclusion, requête des candidats, concurrence réelle sur Postgres.
- **Architecture** : oui. Nouvelle exception de dépendance `game → people`, et son sens
  verrouillé.
- **Mutation** : oui, sans action dédiée. `DailyDraw` est pure et ciblée par PIT ; les
  tests unitaires doivent tuer les mutants des bornes (3 ans pile, cases).
- **Fonctionnel** : non. Aucun endpoint dans cette PR ; il arrive avec la PR de jeu.
- **Unitaire front, Interface, QA e2e** : non. Aucun changement frontend, aucun
  parcours joueur nouveau.

## Edge cases
- Le cron et le démarrage tirent en même temps, ou plusieurs instances : une seule
  partie (clé primaire + relecture).
- Redémarrage à 23:59:59 puis minuit : deux dates distinctes, chacune tirée une fois.
- Pool vide ou trop petit : tirage partiel, `log.error`, pas d'exception remontée au
  planificateur.
- Toutes les photos jouables appartiennent à moins de 3 cases : tirage partiel plutôt
  que doublon de case.
- Photo tirée il y a exactement 3 ans : rejouable (borne incluse, testée).
- Personne exclue le jour même : `photo_id` NULL, manche neutralisée, ligne conservée.
- Photo rejetée puis réapprouvée le même jour : la manche redevient jouable.
- Un `publicId` régénéré ne doit jamais entrer en collision : l'index unique V4 le
  garantit, et un UUID v4 rend la collision négligeable.

## Final Recap
- Nouvelle feature `features/game`. Le tirage `DailyDraw` est pur et testé avec une
  graine fixe. `DrawDailyGameHandler.drawIfMissing(date)` est paresseux, idempotent et
  sûr en concurrence. `DailyDrawScheduler` tire à minuit UTC et au démarrage, et il est
  coupé par défaut.
- V12 crée `daily_rounds`, avec `photo_id` en `ON DELETE SET NULL` pour respecter
  l'effacement.
- Neutralisation calculée à la lecture (`findStatesOf`). Nouveau `publicId` à chaque
  tirage, et `SecureRandom` pour un tirage imprévisible.
- ArchUnit : `game → people` autorisé, et l'inverse verrouillé.
- Hors scope, consigné pour la suite : l'endpoint de jeu (y activer le tirage, ne jamais
  sérialiser `RoundState.photoId`), le guess plus jeune / plus vieux, les points sur
  500, le cache HTTP des images, la notification admin sur les signalements, et la
  neutralisation dans l'historique.

## Deployment Plan
1. Merger la PR #28 (roadmap des indices) si ce n'est pas fait, puis la PR de cette
   branche vers `develop`. La release `develop → main` se fait en **merge commit**
   (voir la mémoire du projet).
2. Le déploiement applique V12 par Flyway au démarrage de l'API. Aucune variable
   d'environnement n'est à ajouter : les trois propriétés ont des valeurs par défaut,
   et le tirage reste coupé.
3. Vérifier en prod (lecture seule) :
   - `SELECT version, success FROM flyway_schema_history WHERE version = '12'` → `t` ;
   - `SELECT count(*) FROM daily_rounds` → 0 (le tirage est coupé) ;
   - la mesure reportée de la phase 1 : `SELECT count(*) FILTER (WHERE p.gender IS NULL
     OR p.continent IS NULL), count(*) FROM person_photos ph JOIN people p ON p.id =
     ph.person_id WHERE NOT p.excluded AND ph.review_status = 'APPROVED'`. Si le nombre
     de cases inconnues n'est pas négligeable, envisager un rattrapage du genre et du
     continent avant le lancement.
4. Rien à annuler en cas de retour arrière : la table est vide et rien ne la lit encore.

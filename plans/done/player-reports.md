# Signalement des photos et des indices par les joueurs

Un joueur signale, depuis l'écran de résultat d'une manche (partie du jour ou série), la
photo ou l'indice qu'il vient de voir, avec un motif pris dans une liste fermée. L'admin
reçoit un email au seuil et traite une file triée par nombre de signalements dans la
curation (photos) et dans `/admin/hints` (indices). Branche `feature/player-reports`,
une PR vers `develop`.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions (validées avec l'utilisateur le 2026-11-03)

- **On signale une manche, pas un id.** Le joueur n'a jamais l'id interne de la photo ni
  celui de l'indice (`RoundView`). Il envoie la manche qu'il a jouée
  (`/api/game/{date}/rounds/{position}/report`, `/api/game/series/{id}/rounds/{position}/report`)
  et le serveur retrouve la photo et l'indice de *sa* manche. Conséquences : seule une
  manche **terminée par ce joueur** se signale (identité connue, rien de donné avant de
  répondre), et un script ne peut viser qu'une photo qu'il a réellement jouée.
- **Un signalement par manche jouée, sans rien de personnel dans les signalements.** Deux
  colonnes `photo_reported` / `hint_reported` sur la manche jouée (`RoundProgress`, donc
  `game_plays` et `free_series_plays`), qui est déjà liée au joueur et purgée avec lui.
  Les tables `photo_reports` / `hint_reports` ne gardent que la cible, le motif et la
  date : ni utilisateur, ni invité, ni IP. Un second signalement de la même cible sur la
  même manche ne crée rien et répond comme le premier (idempotent, 204).
- **Motifs** (enums, liste fermée, pas de texte libre) :
  - photo : `TOO_SMALL`, `NOT_IDENTIFIABLE`, `WRONG_PERSON`, `LOOKS_MINOR`,
    `DISTURBING_PERSON`, `OTHER` ;
  - indice : `WRONG_YEAR`, `FALSE`, `UNCLEAR`, `DISTURBING`, `OTHER`.
- **Aucun retrait automatique** (`DailyRoundStatus` le dit déjà) : un script pourrait
  vider le pool.
- **Alerte admin par email au seuil** (Resend, déjà en place) : quand le nombre de
  signalements ouverts d'une cible *atteint* le seuil (égalité, donc un seul email par
  franchissement). Seuil 3, abaissé à 1 si la photo est dans la partie du jour
  d'aujourd'hui ou si le motif est `LOOKS_MINOR`. Adresse `app.admin.email`
  (`ADMIN_EMAIL`, vide = pas d'email, un log WARN à la place). Email en anglais
  (l'administration reste en anglais), envoyé après le commit pour ne jamais alerter sur
  un signalement annulé.
- **Traitement admin** : un signalement est `OPEN`, puis `RESOLVED` (l'admin a agi :
  photo rejetée, indice rejeté ou corrigé) ou `DISMISSED` (« garder »). Toute décision
  de `ReviewPhoto` / `ReviewHint` résout les signalements ouverts de la cible ; un
  bouton « Keep » les classe. Exclure la personne supprime ses photos, donc leurs
  signalements (`ON DELETE CASCADE`).
- **Rangement (VSA)** : les tables et entités appartiennent à la feature de leur cible
  (`people/PhotoReport`, `hints/HintReport`, sans dépendance entre elles), les files et
  actions admin aussi. Le signalement par le joueur est un use case de `game`, la seule
  feature qui sait relier une manche à sa photo et à son indice (`game → people` et
  `game → hints` sont déjà les exceptions ArchUnit autorisées).
- **Limite par IP** : nouvelle limite `report` dans `RateLimitFilter` (20 par heure),
  séparée de celle des essais pour qu'un joueur qui signale n'épuise pas ses essais.

### Hypothèses
- Le classement de la file : nombre de signalements ouverts décroissant, puis le plus
  ancien d'abord. Les photos de la partie du jour d'aujourd'hui sont marquées dans la
  file (badge « In today's game »).
- On ne signale pas une manche neutralisée (plus de photo) : 409. Un indice rejeté
  depuis (absent de `RoundReveal`) ne se signale plus : 409.
- La politique de confidentialité mentionne les signalements (anonymes, conservés jusqu'au
  traitement, puis tant que la cible existe) ; pas de changement des CGU.

## Phase 1: Base et modèle
Status: Complete

- [x] `V16__create_player_reports.sql` :
  - `game_plays` et `free_series_plays` : `photo_reported BOOLEAN NOT NULL DEFAULT false`,
    `hint_reported BOOLEAN NOT NULL DEFAULT false` ;
  - `photo_reports` (id, `photo_id` FK `person_photos` `ON DELETE CASCADE`, `reason`
    CHECK, `status` CHECK `OPEN|RESOLVED|DISMISSED` défaut `OPEN`, `created_at`,
    `closed_at`), index partiel `(photo_id) WHERE status = 'OPEN'` ;
  - `hint_reports`, même forme sur `hints (id)`.
- [x] `features/people/PhotoReport.java`, `PhotoReportReason.java`, `ReportStatus`
  (people), `PhotoReportRepository.java` (compte des ouverts, clôture en masse par
  photo, file agrégée).
- [x] `features/hints/HintReport.java`, `HintReportReason.java`, `HintReportStatus`,
  `HintReportRepository.java`, mêmes méthodes.
- [x] `RoundProgress` : champs `photoReported` / `hintReported` et méthodes
  `reportPhoto()` / `reportHint()` qui renvoient `false` si déjà fait et refusent une
  manche non terminée.

### Tests
- Unitaire : `RoundProgressTest` (signaler avant la fin → refus, deux fois → `false`).
- Intégration : contraintes V16 (motif inconnu refusé, cascade à la suppression de la
  photo / de l'indice), requête de la file (tri, comptes par motif, seuls les `OPEN`).

### Verification Plan
- `cd ageguessr-api && ./mvnw test -Dtest='RoundProgressTest,*Report*IntegrationTest'` → vert.

### Phase Summary
Migration V16, entités `PhotoReport` / `HintReport`, `ReportStatus` et `ReportTally` dans `shared/reports`, marques sur `RoundProgress`. Vérifié : `GamePlayTest`, `ReportTallyTest` ; les contraintes et la cascade sont couvertes par `ReportFunctionalTest` contre Postgres (pas de test d'intégration séparé : le fonctionnel passe par les mêmes requêtes).

## Phase 2: Signalement par le joueur (backend)
Status: Complete

- [x] `features/game/ReportRound/` : `ReportRoundCommand` (`target` PHOTO|HINT, `reason`
  en chaîne validée contre l'enum de la cible), `ReportRoundController` (les deux
  routes), `ReportRoundHandler` (trouve la manche jouée du joueur via `PlayerResolver`,
  vérifie qu'elle est terminée et que la cible existe encore, pose la marque, insère le
  signalement, décide de l'alerte).
- [x] `GameErrors` : `roundNotFinished`, `nothingToReport` (manche neutralisée, pas
  d'indice, indice rejeté depuis), `invalidReason`.
- [x] `RoundReveal` : `photoReported`, `hintReported`, pour que l'écran garde l'état
  après un rechargement.
- [x] `features/game/ReportRound/ReportAlerts` : seuils (`app.reports.alert-threshold=3`),
  « dans la partie du jour » via `DailyRoundRepository`, envoi après commit
  (`TransactionSynchronization`) par `EmailSender` à `app.admin.email`. Texte anglais :
  cible, personne, motifs comptés, lien vers la page admin.
- [x] `RateLimitFilter` : limite `report` (`app.rate-limit.report.*`, 20 / 60 min), testée
  avant celle du jeu.
- [x] `application.properties` + `.env.example` + compose prod : `ADMIN_EMAIL`,
  `REPORT_RATE_LIMIT_*`.

### Tests
- Unitaire : `ReportRoundHandlerTest` (manche non terminée → 409, neutralisée → 409,
  sans indice → 409, motif d'une autre cible → 400, doublon → pas d'insertion, seuil
  atteint → une alerte, dépassé → aucune, partie du jour / `LOOKS_MINOR` → alerte au
  premier), `ReportAlertsTest` (pas d'adresse → pas d'envoi).
- Fonctionnel : `ReportRoundFunctionalTest` — partie du jour et série, invité (cookie)
  et compte, 401/404 sur la manche d'un autre joueur, 429 au-delà de la limite,
  `RoundReveal.photoReported` vrai après coup.
- Architecture : les règles existantes doivent rester vertes (pas de `people ↔ hints`).
- Mutation : PIT sur le handler et `RoundProgress` (CI de la release).

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert, ArchUnit compris.

### Phase Summary
`ReportRound` (handler, contrôleur, `ReportAlerts`), `GameRounds.of(date|series, position)`, erreurs `ROUND_NOT_FINISHED` / `NOTHING_TO_REPORT` / `INVALID_REASON`, limite `report`, `ADMIN_EMAIL` dans compose et `.env.example`. **Non fait : `ci.yml`** (modification refusée par le mode auto) : l'étape de déploiement doit transmettre `vars.ADMIN_EMAIL`. Vérifié : `./mvnw test` vert (1062 tests).

## Phase 3: Files admin (backend)
Status: Complete

- [x] `features/people/ListReportedPhotos/` : `GET /api/admin/photos/reported` — photo,
  personne, statut de revue, comptes ouverts par motif, total, premier signalement,
  drapeau « dans la partie du jour » (lu par une requête native sur `daily_rounds`,
  pour que `people` ne dépende pas de `game`).
- [x] `features/people/DismissPhotoReports/` : `POST /api/admin/photos/{photoId}/reports/dismiss`.
- [x] `ReviewPhotoHandler` : toute décision clôt les signalements ouverts en `RESOLVED`.
- [x] `features/hints/ListReportedHints/` et `DismissHintReports/`, mêmes formes
  (texte EN/FR, année, catégorie, source, personne).
- [x] `ReviewHintHandler` : idem `ReviewPhotoHandler`.

### Tests
- Unitaire : handlers de clôture.
- Fonctionnel : 403 pour un non-admin, tri de la file, « Keep » vide la file, un rejet
  aussi, l'exclusion de la personne supprime les signalements.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert.

### Phase Summary
`ListReportedPhotos`, `DismissPhotoReports`, `ListReportedHints`, `DismissHintReports`, clôture `RESOLVED` dans `ReviewPhoto` / `ReviewHint`. Vérifié : `ReportFunctionalTest` (403/401, Keep, rejet, correction, exclusion).

## Phase 4: Frontend joueur
Status: Complete

- [x] `features/game/report/report-button` : lien discret « Report the photo » / « Report
  the hint », qui déplie la liste des motifs (radios) et « Send » ; puis « Thanks, we'll
  take a look » (`role="status"`), ou l'état « Reported » si déjà fait.
- [x] `round-result` : bouton photo sous le crédit, bouton indice dans l'encadré de
  l'indice ; sortie `report` remontée par `round-player` jusqu'à `RoundsScreen`
  (méthode abstraite `sendReport`, implémentée dans `Game`, `FreeGame`, `ChallengePage`).
- [x] `game-api.ts`, `series-api.ts` : `report(...)` ; `models.ts` : motifs et
  `photoReported` / `hintReported`.
- [x] Textes : `game-vocabulary.ts` (libellés des motifs) et traductions dans
  `messages.fr.xlf`.
- [x] `privacy.html` : paragraphe sur les signalements.

### Tests
- Unitaire (Vitest) : `report-button` (ouverture, envoi, état envoyé, erreur 429),
  `round-result` (boutons absents sans indice), écrans de jeu (appel de la bonne API).
- Interface (Playwright, CI) : `e2e/component/report.spec.ts` et
  `e2e/component-fr/report.spec.ts` sur l'écran de résultat mocké (dépliage sur mobile,
  libellés FR).
- Pas de parcours QA e2e : ce n'est pas un parcours critique, et le fonctionnel backend
  couvre déjà la chaîne HTTP réelle.

### Verification Plan
- `cd frontend && pnpm test` → vert ; `pnpm lint` → propre ; `pnpm build` → les deux
  langues compilent sans message manquant.

### Phase Summary
`report-button`, `reporter` de `RoundsScreen` passé par `RoundPlayer` et `RoundResult`, API `report`, vocabulaire et traductions, paragraphe de `/privacy`. Vérifié : `pnpm test` (289), lint, build des deux langues ; Playwright `report.spec.ts` (EN et FR) écrit, exécuté par la CI.

## Phase 5: Frontend admin
Status: Complete

- [x] `features/curation` : section « Reported photos » en tête de la page, avec la
  photo, les comptes par motif, le badge « In today's game », et Reject / Keep /
  Exclude person (formulaire d'exclusion existant).
- [x] `features/hint-curation` : section « Reported hints », avec Reject / Keep /
  correction du texte (édition déjà présente).
- [x] APIs admin (`curation-api.ts`, `hint-curation-api.ts`) et modèles.

### Tests
- Unitaire (Vitest) : sections signalées (tri affiché, actions appellent la bonne route,
  la ligne disparaît après traitement).
- Pas d'interface Playwright : l'admin est en anglais seul et reste couverte en Vitest,
  comme les pages de curation existantes.

### Verification Plan
- `cd frontend && pnpm test && pnpm lint` → vert.

### Phase Summary
Sections `reported-photos` (curation) et `reported-hints` (indices), stubbées dans les specs des pages existantes. Un cas Playwright « Keep » ajouté à `curation.spec.ts` (exécuté par la CI).

## Phase 6: Docs, revues, PR
Status: Complete

- [x] `docs/decisions/architecture.md` (signalements : rangement, anonymat, seuils),
  `docs/roadmap.md` (deux cases cochées), `docs/testing-strategy.md` si besoin.
- [x] code-reviewer puis architecture-reviewer sur le diff ; corriger 🔴 et 🟠.
- [x] PR vers `develop`, CI verte (Playwright compris).

### Verification Plan
- `./mvnw test` + `pnpm test` verts ; CI de la PR verte.

### Phase Summary
Revues : code-reviewer sans 🔴 ni 🟠 (Y1 appliqué : sujet d'email sur une ligne) ;
architecture-reviewer, deux 🟠 corrigés (types de motifs et `reasonsText` partagés dans
`frontend/src/app/shared/reports/`), commentaire ajouté sur l'urgence d'une photo.
Vérifié : `./mvnw test` 1063 verts, `pnpm test` 289 verts, lint, build des deux langues.

## Edge cases
- Manche non terminée, d'un autre joueur, d'une série inconnue, d'une date sans partie.
- Manche neutralisée (photo rejetée depuis) : rien à signaler.
- Manche terminée sans indice (1er essai exact) : seul le bouton photo existe.
- Indice rejeté entre la manche et le signalement : 409, le bouton n'est plus affiché.
- Même photo vue dans deux séries par le même joueur : deux manches, deux signalements
  possibles (accepté, borné par la limite par IP).
- Doublon sur la même manche, y compris deux requêtes simultanées (verrou optimiste
  `version` de la manche).
- Photo signalée puis rejetée puis rouverte : les anciens signalements restent `RESOLVED`.
- `ADMIN_EMAIL` absent : aucun email, un log WARN, le signalement passe quand même.
- Échec de Resend : loggé, le signalement reste enregistré.

## Final Recap
Un joueur signale la photo ou l'indice d'une manche qu'il a terminée (partie du jour ou
série), avec un motif d'une liste fermée. Le signalement est anonyme ; une marque sur la
manche jouée empêche de signaler deux fois. L'admin reçoit un email au seuil (3, ou dès
le premier pour une photo du jour, un premier « paraît mineure » ou un indice du jour) et
traite deux files : photos dans la curation (Reject / Keep / Exclude), indices dans
`/admin/hints` (corriger et approuver / Reject / Keep). Toute décision clôt les
signalements. Limite `report` de 20 par heure et par IP.

Écart au plan : pas de test d'intégration séparé pour V16, le fonctionnel couvrant les
mêmes requêtes contre Postgres. `ci.yml` n'est pas modifié (refusé en mode auto) :
`ADMIN_EMAIL` reste à transmettre par l'étape de déploiement.

## Deployment Plan
1. Ajouter la variable GitHub `ADMIN_EMAIL` (Settings → Variables) et, dans `ci.yml`
   (étape « Écrire le .env de prod »), `ADMIN_EMAIL: ${{ vars.ADMIN_EMAIL }}` dans `env`,
   `ADMIN_EMAIL` dans `envs`, et `ADMIN_EMAIL=$ADMIN_EMAIL` dans le heredoc du `.env`.
   Sans ça, les alertes deviennent des logs WARN (le reste fonctionne).
2. Release `develop` → `main` (merge commit). Flyway applique V16 : deux colonnes par
   table de manches (défaut false) et deux tables, sans toucher aux données.
3. Vérifier en prod : terminer une manche, « Report this photo », un motif, « Reported » ;
   recharger, l'état reste ; `/admin/curation` montre la photo avec « In today's game »,
   « Keep » la retire ; l'email d'alerte arrive à `ADMIN_EMAIL`.

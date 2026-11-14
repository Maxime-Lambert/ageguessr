# Monitoring : santé réelle, fraîcheur du backup, alertes d'erreurs

Être prévenu sans regarder : site ou base en panne, backup arrêté, erreur du backend
(tirage du jour raté…). Branche `feature/monitoring`, PR vers `develop`.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions (2026-11-14)
- **Surveillance externe : UptimeRobot** (compte existant de l'utilisateur), plan
  gratuit : surveillances HTTP toutes les 5 min, alerte par email. Le plan gratuit n'a pas
  de heartbeat : la fraîcheur du backup passe donc par un endpoint HTTP, pas par un ping
  du script. Aucun secret à ajouter.
- **`/api/health` vérifie vraiment** : `SELECT 1` sur PostgreSQL et `PING` Redis, 503 si
  l'un échoue (le corps ne dit pas lequel : endpoint public). Le smoke test du deploy
  (`curl -sf`) en profite.
- **`/api/health/backup`** : 200 si le dernier dump réussi a moins de 26 h, 503 sinon
  (ou si aucun). Le script écrit l'heure du dernier succès dans un fichier d'un volume
  nommé `backup_status`, monté en écriture dans `backup` et **en lecture seule** dans
  `backend` : le backend ne voit jamais les dumps eux-mêmes.
- **Emails d'erreur** : un appender Logback (branché au démarrage sur le logger racine)
  transmet chaque log `ERROR` à `ErrorAlerts`, qui envoie à `ADMIN_EMAIL` via Resend :
  le premier tout de suite, puis au plus un email par 15 min, les erreurs entre deux
  regroupées (nombre + les 5 premières). Contenu limité : heure, logger, classe de
  l'exception, message tronqué à 300 caractères, pas de pile complète ni de requête.
  Envoi asynchrone (jamais de blocage du thread qui logue) ; les logs des classes d'email
  et d'alerte sont ignorés (pas de boucle si Resend échoue). Sans `ADMIN_EMAIL` : rien.
- Hors périmètre : métriques (Prometheus/Grafana), journal centralisé, alerte sur
  l'espace disque.

## Phase 1: Santé réelle
Status: Complete

- [x] `HealthController` : DB (`JdbcTemplate`) + Redis (`StringRedisTemplate`), 503 sinon
- [x] `/api/health/backup` : lit `app.backup.status-file`, seuil `app.backup.max-age`
      (26 h) ; permis sans authentification dans `SecurityConfig`
- [x] Tests : intégration (200 base et Redis up ; 503 Redis coupé si faisable, sinon
      unitaire avec mocks) ; unitaire de la fraîcheur (fichier absent, vieux, récent,
      illisible) avec une `Clock`

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert

### Phase Summary
`HealthController` vérifie `SELECT 1` + `PING` Redis (503 sinon, résultat réutilisé 5 s
après la revue sécurité), `/api/health/backup` via `BackupStatus` (fichier d'epoch, seuil
26 h, `Clock`). Tests : `BackupStatusTest`, `HealthControllerTest` (base/Redis coupés,
réutilisation), `HealthFunctionalTest` (backup public, 503 sans fichier).

## Phase 2: Backup → statut
Status: Complete

- [x] `pg-backup.sh` : après un dump réussi, écrit `date +%s` dans
      `$STATUS_DIR/last-success` (écriture atomique, optionnel si `STATUS_DIR` vide)
- [x] `test-backup.sh` : vérifie le fichier après `once`, et qu'un échec ne le touche pas
- [x] `docker-compose.prod.yml` : volume `backup_status` (rw dans backup, ro dans
      backend), `APP_BACKUP_STATUS_FILE` pour le backend

### Verification Plan
- shellcheck + `bash deploy/backup/test-backup.sh` → `OK`
- `docker compose -f docker-compose.prod.yml config -q` → valide

### Phase Summary
`pg-backup.sh` écrit `last-success` (atomique) si `STATUS_DIR` ; volume `backup_status`
rw dans backup, ro dans backend (`BACKUP_STATUS_FILE`). Vérifié : shellcheck, `test-backup.sh`
→ OK (fichier écrit, intact après un échec) ; compose local : l'uid 10001 du backend lit le
fichier.

## Phase 3: Emails d'erreur
Status: Complete

- [x] `shared/alerting/ErrorAlerts` (regroupement, cadence, contenu limité, `Clock`) et
      `ErrorAlertAppender` (Logback, filtre ERROR, exclusions), branché sur
      `ApplicationReadyEvent`, débranché à l'arrêt
- [x] Tests unitaires : 1er email immédiat, regroupement pendant 15 min puis résumé,
      troncature, échappement HTML, rien sans `ADMIN_EMAIL`, logs d'email ignorés
- [x] Test d'intégration : `log.error` dans un bean → un email dans `RecordingEmailSender`

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert

### Phase Summary
`shared/alerting` : `ErrorAlerts` (1er email immédiat, puis 1 / 15 min regroupé, 5
lignes, message tronqué à 300, échappé en UTF-8), `ErrorAlertAppender` (ThresholdFilter
ERROR, ignore email/alerting/`com.resend.`), `ErrorAlertsWiring` (branché à
ApplicationReadyEvent, débranché + attente 5 s à l'arrêt, flush chaque minute, désactivé en
tests). Tests unitaires + `ErrorAlertsFunctionalTest` (`@DirtiesContext`).

## Phase 4: Docs et réglage UptimeRobot
Status: Complete

- [x] `architecture.md` : section « Monitoring » (décisions, ce qui alerte quoi)
- [x] Roadmap : cocher Monitoring ; « À vérifier » : les deux surveillances UptimeRobot
      créées, une alerte reçue en coupant… (procédure de test sans casser la prod)
- [x] Instructions UptimeRobot pour l'utilisateur dans la PR (2 monitors HTTP)
- [x] Revues code-reviewer + architecture-reviewer

### Verification Plan
- `cd frontend && pnpm test` → vert (pas de changement attendu)

### Phase Summary
`architecture.md` « Monitoring » (tableau panne → alerte, règle sur `log.error`), roadmap
(Monitoring coché, vérification UptimeRobot ajoutée). Revues : architecture (0 bloquant ;
corrigés : appender qui fuyait entre contextes de test, plan, awaitTermination,
ThresholdFilter, visibilité) et sécurité (0 bloquant/important ; corrigés : cache 5 s,
`com.resend.` ignoré, règle documentée). Vérifié : `./mvnw test` 1242 verts.

## Tests par catégorie
- **Unitaire** : fraîcheur du backup, regroupement/cadence/contenu des alertes.
- **Intégration** : `/api/health` contre la vraie base (Testcontainers déjà en place),
  appender → `RecordingEmailSender`.
- **Script** : `test-backup.sh` étendu (fichier de statut).
- **Fonctionnel / interface / e2e / mutation** : aucun — pas d'écran ni de parcours
  joueur ; ArchUnit vérifiera le placement dans `shared/`.

## Edge cases
- Erreurs en rafale (base tombée : chaque requête logue) → un email, puis un résumé.
- Resend en panne → l'échec d'envoi est logué en `WARN`, jamais renvoyé en alerte.
- Premier déploiement : pas encore de fichier de statut → `/api/health/backup` en 503
  jusqu'au premier dump (fait au démarrage du service `backup`, quelques secondes).
- Horloge : âge calculé côté backend depuis l'epoch écrit par le script (UTC des deux
  côtés).

## Final Recap
Site, base, Redis et backup surveillés par UptimeRobot via deux endpoints publics muets ;
erreurs du backend envoyées par email à l'admin, regroupées, sans données de joueur.
Reste à l'utilisateur : créer les deux surveillances UptimeRobot.

## Deployment Plan
Release `develop` → `main` (merge commit). Au déploiement : volume `backup_status` créé,
`backup` recréé (nouvelle variable) et dump immédiat → `/api/health/backup` passe à 200 en
quelques secondes. Puis, dans UptimeRobot : deux monitors HTTP(S), 5 min,
`https://ageguessr.app/api/health` et `https://ageguessr.app/api/health/backup`.

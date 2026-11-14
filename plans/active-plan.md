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
Status: Not started

- [ ] `HealthController` : DB (`JdbcTemplate`) + Redis (`StringRedisTemplate`), 503 sinon
- [ ] `/api/health/backup` : lit `app.backup.status-file`, seuil `app.backup.max-age`
      (26 h) ; permis sans authentification dans `SecurityConfig`
- [ ] Tests : intégration (200 base et Redis up ; 503 Redis coupé si faisable, sinon
      unitaire avec mocks) ; unitaire de la fraîcheur (fichier absent, vieux, récent,
      illisible) avec une `Clock`

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert

### Phase Summary
_(write when phase completes)_

## Phase 2: Backup → statut
Status: Not started

- [ ] `pg-backup.sh` : après un dump réussi, écrit `date +%s` dans
      `$STATUS_DIR/last-success` (écriture atomique, optionnel si `STATUS_DIR` vide)
- [ ] `test-backup.sh` : vérifie le fichier après `once`, et qu'un échec ne le touche pas
- [ ] `docker-compose.prod.yml` : volume `backup_status` (rw dans backup, ro dans
      backend), `APP_BACKUP_STATUS_FILE` pour le backend

### Verification Plan
- shellcheck + `bash deploy/backup/test-backup.sh` → `OK`
- `docker compose -f docker-compose.prod.yml config -q` → valide

### Phase Summary
_(write when phase completes)_

## Phase 3: Emails d'erreur
Status: Not started

- [ ] `shared/alerting/ErrorAlerts` (regroupement, cadence, contenu limité, `Clock`) et
      `ErrorAlertAppender` (Logback, filtre ERROR, exclusions), branché sur
      `ApplicationReadyEvent`, débranché à l'arrêt
- [ ] Tests unitaires : 1er email immédiat, regroupement pendant 15 min puis résumé,
      troncature, échappement HTML, rien sans `ADMIN_EMAIL`, logs d'email ignorés
- [ ] Test d'intégration : `log.error` dans un bean → un email dans `RecordingEmailSender`

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert

### Phase Summary
_(write when phase completes)_

## Phase 4: Docs et réglage UptimeRobot
Status: Not started

- [ ] `architecture.md` : section « Monitoring » (décisions, ce qui alerte quoi)
- [ ] Roadmap : cocher Monitoring ; « À vérifier » : les deux surveillances UptimeRobot
      créées, une alerte reçue en coupant… (procédure de test sans casser la prod)
- [ ] Instructions UptimeRobot pour l'utilisateur dans la PR (2 monitors HTTP)
- [ ] Revues code-reviewer + architecture-reviewer

### Verification Plan
- `cd frontend && pnpm test` → vert (pas de changement attendu)

### Phase Summary
_(write when phase completes)_

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
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

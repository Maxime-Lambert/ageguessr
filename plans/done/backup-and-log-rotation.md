# Backup PostgreSQL et rotation des logs Docker

Sauvegarder chaque jour la base de prod sur le disque du VPS, garder 30 jours, savoir
restaurer, et l'annoncer dans `/privacy` ; borner la taille des logs Docker de chaque
conteneur du projet. Branche `chore/backup-and-log-rotation`, PR vers `develop`.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions (validées avec l'utilisateur le 2026-11-14)
- **Destination : disque du VPS seulement** (choix de l'utilisateur). Protège d'une
  erreur (migration ratée, suppression), pas de la perte du VPS. Limite écrite dans
  `docs/decisions/architecture.md` ; une copie hors VPS (restic + S3) reste possible plus
  tard sans changer le format des dumps.
- **Conservation : 30 jours** — 7 dumps quotidiens + des hebdomadaires (dimanche)
  supprimés au-delà de 30 jours. Aucun dump n'a plus de 30 jours : c'est la durée annoncée.
- **Service `backup` dans `docker-compose.prod.yml`**, image `postgres:18-alpine` (même
  version majeure que le serveur : un `pg_dump` plus ancien que le serveur refuse de
  tourner). Une boucle shell attend 03:00 UTC, pas `crond` : busybox crond ne transmet
  pas l'environnement (mot de passe) aux tâches. Au démarrage, un dump est fait tout de
  suite si le dernier a plus de 24 h (VPS redémarré, premier déploiement).
- **Format `pg_dump -Fc`** (custom, compressé) : `pg_restore` peut restaurer tout ou une
  table, et `pg_restore --list` vérifie qu'un fichier est lisible. Écrit dans un `.tmp`
  puis renommé : jamais de dump à moitié écrit pris pour un bon.
- **Dossier hôte `/opt/ageguessr/backups`** (bind mount, pas un volume nommé) : se copie
  avec un simple `scp` pour garder une copie ailleurs à la main. Droits 700 / fichiers 600.
- **Script versionné `deploy/backup/pg-backup.sh`**, copié sur le VPS par une 2e étape
  `ssh-deploy` du job deploy (même SHA épinglé) : testable seul, pas de `$$` d'échappement
  dans le YAML.
- **Healthcheck du service** : `unhealthy` si le dernier dump quotidien a plus de 26 h —
  visible dans `docker ps` en attendant le monitoring (Semaine 8).
- **Logs** : une ancre YAML `x-logging` (`json-file`, `max-size: 10m`, `max-file: 3`)
  sur chaque service du compose de prod, soit 30 Mo au plus par conteneur. Pas dans
  `/etc/docker/daemon.json` : le VPS est partagé avec un autre projet. Les logs ne
  contiennent pas de données personnelles (pas de journal d'accès, déjà annoncé).
- **`/privacy` (EN + FR)** : la suppression d'un compte est immédiate dans la base, mais
  ses données restent jusqu'à 30 jours dans les sauvegardes, puis disparaissent ;
  `PRIVACY_UPDATED` passe au jour de la PR.

## Hypothèse et limite connue
- Restaurer un dump fait revenir les comptes supprimés depuis ce dump. Pas de journal des
  suppressions à rejouer pour l'instant : la procédure de restauration le dit, et la
  roadmap reçoit une ligne « journal des suppressions à rejouer après restauration ».

## Phase 1: Script de backup et rotation
Status: Complete

- [x] `deploy/backup/pg-backup.sh` (sh POSIX/busybox, `set -eu -o pipefail`) : fonction
      `dump` (pg_dump -Fc vers `daily/ageguessr-YYYY-MM-DD.dump.tmp`, renommage, chmod
      600), `prune` (daily `-mtime +6`, weekly `-mtime +29`), copie en `weekly/` le
      dimanche, `loop` (dump au démarrage si besoin, puis attente de 03:00 UTC) ; logs
      sur stdout/stderr, un échec de dump est logué sans tuer la boucle
- [x] Mode `once` (un dump + prune, puis sortie) pour le test et la restauration manuelle
- [x] `deploy/backup/test-backup.sh` : lance un `postgres:18-alpine` jetable, crée une
      table, exécute `pg-backup.sh once`, vérifie le fichier (`pg_restore --list`), le
      restaure dans une base vide et compare une ligne ; vieillit des fichiers
      (`touch -d`) et vérifie que la rotation garde 7 quotidiens et supprime les
      hebdomadaires de plus de 30 jours
- [x] Job CI `backup-script` dans `.github/workflows/ci.yml` : `shellcheck` sur les deux
      scripts + `test-backup.sh`

### Verification Plan
- `shellcheck deploy/backup/*.sh` → aucune alerte (via `docker run koalaman/shellcheck`
  si absent)
- `bash deploy/backup/test-backup.sh` → se termine par `OK` (un seul conteneur postgres,
  supportable par WSL)

### Phase Summary
Script `deploy/backup/pg-backup.sh` (loop/once/prune) et `test-backup.sh` écrits ; job CI
`backup-script` (shellcheck + test) qui conditionne `publish-images`. Vérifié : shellcheck
propre (directive `shell=busybox` pour `pipefail`), `test-backup.sh` → `OK` (dump, chmod
600/700, copie hebdo, restauration dans une base vide, rotation 7 quotidiens / hebdo < 30 j,
.tmp orphelin supprimé, échec de pg_dump sans fichier laissé). Ajout hors plan : `trap` +
`sleep & wait` pour que `docker stop` arrête le conteneur en 0,4 s au lieu de 10 s.

## Phase 2: Compose de prod et déploiement
Status: Complete

- [x] `docker-compose.prod.yml` : ancre `x-logging` appliquée à postgres, redis,
      backend, edge, backup
- [x] Service `backup` : `postgres:18-alpine`, `restart: unless-stopped`,
      `depends_on: postgres (healthy)`, `PGHOST/PGUSER/PGDATABASE/PGPASSWORD`, montages
      `./backup:/backup:ro` et `./backups:/backups`, `TZ=UTC`, healthcheck 26 h
- [x] Job deploy : 2e étape `ssh-deploy` qui copie `deploy/backup/` vers
      `/opt/ageguessr/backup/` ; `mkdir -p backups && chmod 700 backups` dans le script
      de déploiement avant `up -d`

### Verification Plan
- `POSTGRES_PASSWORD=x JWT_SECRET=x RESEND_API_KEY=x DOMAIN=x IMAGE_TAG=x docker compose
  -f docker-compose.prod.yml config` → valide, `logging` présent sur les 5 services
- `docker compose -f docker-compose.prod.yml config --format json | jq '.services | map_values(.logging.options)'`
  → `max-size: 10m`, `max-file: 3` partout

### Phase Summary
Ancre `x-logging` sur les 5 services, service `backup` (healthcheck 26 h, `interval: 15m`,
sans `start_interval` qui demande Docker 25+). Job deploy : 2e `ssh-deploy` avec
`SCRIPT_BEFORE: mkdir -p` (plutôt que `rsync --mkpath`, qui demande rsync 3.2.3 des deux
côtés), `mkdir -p backups && chmod 700` avant `up -d`. Vérifié : `compose config` valide,
logging 10m/3 partout ; postgres + backup lancés depuis une copie de `/opt/ageguessr` :
dump au démarrage, healthcheck OK, pas de 2e dump après redémarrage.

## Phase 3: Politique de confidentialité et documentation
Status: Complete

- [x] `privacy.html` EN + FR, section 4 : données d'un compte supprimé jusqu'à 30 jours
      dans les sauvegardes ; `PRIVACY_UPDATED` au jour de la PR
- [x] `privacy.spec.ts` : test que les deux langues annoncent les 30 jours de sauvegarde
- [x] `docs/decisions/architecture.md` : section « Sauvegardes et logs » (décisions
      ci-dessus, limite « disque du VPS seulement », restauration qui ressuscite les
      comptes supprimés)
- [x] Procédure de restauration (dans la même section) : arrêter backend, `pg_restore
      --clean --if-exists` depuis le conteneur backup, relancer ; récupérer un dump par
      `scp`
- [x] `docs/roadmap.md` : cocher backup et rotation des logs (Semaine 8), ajouter la
      ligne « journal des suppressions », ajouter le test en prod (premier dump présent,
      `docker ps` healthy, `docker inspect` montre la rotation)

### Verification Plan
- `cd frontend && pnpm test` → tout vert
- `cd frontend && pnpm exec ng build` → pas de traduction manquante
- `cd ageguessr-api && ./mvnw test` → vert (aucun changement backend attendu)

### Phase Summary
`/privacy` EN + FR (ligne « Sauvegardes », compte supprimé « hors sauvegardes »),
`PRIVACY_UPDATED = 2026-11-14`, test dans `privacy.spec.ts`. `architecture.md` :
« Sauvegardes et logs » (décisions, restauration, limite) + renvoi depuis « Suppression des
données ». Roadmap : backup et logs cochés, 2 lignes ajoutées (journal des suppressions,
copie hors VPS). Le test en prod n'est pas encore dans la liste « À vérifier » : il sera
ajouté avec ceux des PR #70/#71 une fois fusionnées (même section, conflit sinon).
Vérifié : `pnpm test` 459 verts, `./mvnw test` vert.

## Tests par catégorie (docs/testing-strategy.md)
- **Intégration** : `test-backup.sh` contre un vrai PostgreSQL 18 (dump, restauration,
  rotation) — le cœur du risque.
- **Interface** : `privacy.spec.ts` (le texte annonce bien la durée, EN et FR).
- **Unitaire** : aucun — pas de logique applicative Java/TS nouvelle.
- **Fonctionnel / QA e2e** : aucun — pas de parcours joueur nouveau.
- **Architecture** : aucun — pas de package Java touché.
- **Mutation** : aucun — rien de nouveau dans le code muté.

## Edge cases
- Dump qui échoue (base indisponible) : `.tmp` supprimé, erreur loguée, boucle continue,
  healthcheck finit `unhealthy`.
- Redéploiement : le service `backup` ne redémarre que si sa config change ; s'il
  redémarre, pas de second dump le même jour (dernier dump < 24 h).
- Deux dumps le même jour (mode `once` manuel) : le fichier du jour est remplacé.
- Disque plein : `pg_dump` échoue, même chemin que l'échec ci-dessus.

## Final Recap
Dump quotidien de la base de prod (`pg-backup.sh`, service `backup`, 7 quotidiens +
hebdomadaires jusqu'à 30 jours, disque du VPS), testé en CI contre un vrai PostgreSQL 18 ;
logs Docker bornés à 3 × 10 Mo par conteneur ; `/privacy` EN + FR annonce les 30 jours et
leur base légale ; restauration et limites documentées dans `architecture.md`.
Revues : code-reviewer (0 bloquant/important ; corrigés : image shellcheck par digest et
`:ro`, conteneur `read_only`, base légale), architecture-reviewer (0 bloquant/important ;
corrigés : commentaire CI déplacé, rotation même si le dump échoue). Restent hors PR :
journal des suppressions à rejouer, copie hors VPS (lignes de la roadmap), et l'ajout du
test en prod à la liste « À vérifier » après fusion de #70/#71.

## Deployment Plan
Merge `develop` → `main` (merge commit) : le job deploy copie le script, crée
`/opt/ageguessr/backups`, et `up -d` démarre `backup` (premier dump immédiat). Les
conteneurs existants sont recréés pour prendre la rotation des logs (les anciens logs non
bornés disparaissent avec eux). À vérifier sur le VPS :
- `sudo ls -l /opt/ageguessr/backups/daily` → un `ageguessr-<date>.dump`, `-rw-------`
- `docker ps` → `ageguessr-prod-backup-1` `healthy` (après ~10 min)
- `docker inspect ageguessr-prod-backend-1 --format '{{json .HostConfig.LogConfig}}'`
  → `max-size 10m`, `max-file 3`
- le lendemain : `docker logs ageguessr-prod-backup-1` montre le dump de 03:00 UTC

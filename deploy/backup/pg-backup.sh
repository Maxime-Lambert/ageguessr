#!/bin/sh
# shellcheck shell=busybox
# Sauvegarde quotidienne de la base de prod (service "backup" de docker-compose.prod.yml).
# Tourne dans postgres:18-alpine (busybox sh) : pg_dump de la meme version majeure que le
# serveur. Connexion par les variables standard PGHOST, PGUSER, PGDATABASE, PGPASSWORD.
#
#   pg-backup.sh loop    dump au demarrage si le dernier a plus de 24 h, puis chaque jour
#                        a 03:00 UTC (pas crond : busybox crond ne transmet pas
#                        l'environnement, donc pas le mot de passe, a ses taches)
#   pg-backup.sh once    un dump et la rotation, puis sortie (restauration, test)
#   pg-backup.sh prune   la rotation seule
#
# Conservation (annoncee dans /privacy) : 7 dumps quotidiens, et le dump du dimanche garde
# en hebdomadaire jusqu'a 30 jours. Aucun fichier n'a plus de 30 jours.
set -eu
set -o pipefail

BACKUP_DIR="${BACKUP_DIR:-/backups}"
# Jour de la copie hebdomadaire (date +%u : 1 = lundi, 7 = dimanche).
WEEKLY_DAY="${WEEKLY_DAY:-7}"
DAILY="$BACKUP_DIR/daily"
WEEKLY="$BACKUP_DIR/weekly"

log() {
  echo "$(date -u '+%Y-%m-%dT%H:%M:%SZ') pg-backup: $*"
}

# Ecrit dans un .tmp puis renomme : un dump interrompu n'est jamais pris pour un bon.
dump() {
  mkdir -p "$DAILY" "$WEEKLY"
  chmod 700 "$BACKUP_DIR" "$DAILY" "$WEEKLY"
  file="$DAILY/ageguessr-$(date -u +%Y-%m-%d).dump"
  if ! pg_dump --format=custom --file="$file.tmp"; then
    rm -f "$file.tmp"
    log "ERREUR : pg_dump a echoue, aucun dump aujourd'hui" >&2
    return 1
  fi
  chmod 600 "$file.tmp"
  mv -f "$file.tmp" "$file"
  log "dump ecrit : $file ($(du -k "$file" | cut -f1) Ko)"
  if [ "$(date -u +%u)" = "$WEEKLY_DAY" ]; then
    # Lien dur : pas de place en plus, et le fichier survit a la rotation des quotidiens.
    ln -f "$file" "$WEEKLY/$(basename "$file")"
    log "copie hebdomadaire : $WEEKLY/$(basename "$file")"
  fi
}

# -mtime +6 : 7 jours ou plus (find compte les jours entiers ecoules), donc 7 quotidiens.
# -mtime +29 : 30 jours ou plus.
prune() {
  for dir in "$DAILY" "$WEEKLY"; do
    [ -d "$dir" ] || continue
    find "$dir" -name '*.dump.tmp' -mmin +60 -exec rm -f {} +
  done
  [ -d "$DAILY" ] && find "$DAILY" -name '*.dump' -mtime +6 -exec rm -f {} +
  [ -d "$WEEKLY" ] && find "$WEEKLY" -name '*.dump' -mtime +29 -exec rm -f {} +
  log "rotation faite : $(count "$DAILY") quotidiens, $(count "$WEEKLY") hebdomadaires"
}

count() {
  if [ -d "$1" ]; then find "$1" -name '*.dump' | wc -l | tr -d ' '; else echo 0; fi
}

# Un dump de moins de 24 h existe deja (redemarrage du conteneur le meme jour).
recent_dump() {
  [ -d "$DAILY" ] && [ -n "$(find "$DAILY" -name '*.dump' -mmin -1440)" ]
}

seconds_until_3am() {
  now=$(date -u +%s)
  target=$(date -u -d "$(date -u +%Y-%m-%d) 03:00:00" +%s)
  [ "$target" -gt "$now" ] || target=$((target + 86400))
  echo $((target - now))
}

# Un echec est logue, la boucle continue : le healthcheck du service passe "unhealthy"
# quand le dernier dump a plus de 26 h. La rotation tourne quand meme, pour que les vieux
# dumps ne restent pas au-dela des 30 jours annonces pendant une panne de la base.
backup_once() {
  if ! dump; then
    prune
    return 1
  fi
  prune
}

case "${1:-loop}" in
  once)
    backup_once
    ;;
  prune)
    prune
    ;;
  loop)
    # PID 1 du conteneur : sans trap, sh ignore SIGTERM et docker stop attend 10 s pour
    # le tuer. sleep en arriere-plan + wait, pour que le signal coupe l'attente.
    trap 'log "arret"; exit 0' TERM INT
    if recent_dump; then
      log "dernier dump de moins de 24 h, prochain a 03:00 UTC"
    else
      backup_once || true
    fi
    while true; do
      sleep "$(seconds_until_3am)" &
      wait $!
      backup_once || true
    done
    ;;
  *)
    echo "usage: $0 [loop|once|prune]" >&2
    exit 2
    ;;
esac

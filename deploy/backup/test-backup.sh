#!/usr/bin/env bash
# Test d'integration de pg-backup.sh contre un vrai PostgreSQL 18 (job CI backup-script).
# Un dump, sa restauration dans une base vide, puis la rotation sur des fichiers vieillis.
# Ne demande que Docker ; un seul conteneur postgres a la fois.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
image="postgres:18-alpine"
name="ageguessr-backup-test-$$"
work="$(mktemp -d)"
backups="$work/backups"
status="$work/status"
mkdir -p "$backups" "$status"

cleanup() {
  docker rm -f "$name" >/dev/null 2>&1 || true
  docker network rm "$name" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

fail() {
  echo "ECHEC : $*" >&2
  exit 1
}

docker network create "$name" >/dev/null
docker run -d --name "$name" --network "$name" \
  -e POSTGRES_DB=ageguessr -e POSTGRES_USER=ageguessr -e POSTGRES_PASSWORD=test \
  "$image" >/dev/null

for _ in $(seq 1 30); do
  # pg_isready seul repond pendant l'initdb : on attend une vraie requete.
  if docker exec "$name" psql -U ageguessr -d ageguessr -c 'select 1' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

psql_in() {
  docker exec -i "$name" psql -U ageguessr -v ON_ERROR_STOP=1 -qtA "$@"
}

psql_in -d ageguessr -c "create table player (id int primary key, name text);
  insert into player values (1, 'Ada'), (2, 'Grace');"

# Le script, comme en prod : dans l'image postgres, avec l'utilisateur de l'hote pour que
# les fichiers crees restent supprimables ici.
run_backup() {
  docker run --rm --network "$name" --user "$(id -u):$(id -g)" \
    -e PGHOST="$name" -e PGUSER=ageguessr -e PGDATABASE=ageguessr -e PGPASSWORD=test \
    -e WEEKLY_DAY="$(date -u +%u)" \
    -e STATUS_DIR=/status \
    -v "$here:/backup:ro" -v "$backups:/backups" -v "$status:/status" \
    "$image" sh /backup/pg-backup.sh "$@"
}

echo "== dump"
run_backup once
today="ageguessr-$(date -u +%Y-%m-%d).dump"
[ -f "$backups/daily/$today" ] || fail "pas de dump quotidien $today"
[ -f "$backups/weekly/$today" ] || fail "pas de copie hebdomadaire le jour WEEKLY_DAY"
[ "$(stat -c %a "$backups/daily/$today")" = 600 ] || fail "le dump n'est pas en 600"
[ "$(stat -c %a "$backups/daily")" = 700 ] || fail "le dossier n'est pas en 700"
[ -z "$(find "$backups/daily" -name '*.tmp')" ] || fail "un .tmp est reste"

last=$(cat "$status/last-success")
[ $(( $(date -u +%s) - last )) -lt 60 ] || fail "l'heure du dernier succes n'est pas notee"

echo "== restauration dans une base vide"
psql_in -d ageguessr -c "create database restored"
docker exec -i "$name" pg_restore -U ageguessr -d restored --no-owner <"$backups/daily/$today"
[ "$(psql_in -d restored -c 'select name from player where id = 2')" = Grace ] \
  || fail "la ligne restauree ne correspond pas"

echo "== rotation"
for days in 1 2 3 4 5 6 7 8 10; do
  f="$backups/daily/ageguessr-old-$days.dump"
  touch -d "$days days ago" "$f"
done
for days in 7 14 21 29 31 45; do
  touch -d "$days days ago" "$backups/weekly/ageguessr-old-$days.dump"
done
touch -d "2 hours ago" "$backups/daily/ageguessr-crashed.dump.tmp"
run_backup prune

daily=$(find "$backups/daily" -name '*.dump' | wc -l)
[ "$daily" -eq 7 ] || fail "$daily quotidiens au lieu de 7 (aujourd'hui + 1 a 6 jours)"
[ -f "$backups/daily/ageguessr-old-6.dump" ] || fail "le quotidien de 6 jours a disparu"
[ ! -f "$backups/daily/ageguessr-old-7.dump" ] || fail "le quotidien de 7 jours est reste"
[ -f "$backups/weekly/ageguessr-old-29.dump" ] || fail "l'hebdomadaire de 29 jours a disparu"
[ ! -f "$backups/weekly/ageguessr-old-31.dump" ] || fail "l'hebdomadaire de 31 jours est reste"
[ ! -f "$backups/weekly/ageguessr-old-45.dump" ] || fail "l'hebdomadaire de 45 jours est reste"
[ ! -f "$backups/daily/ageguessr-crashed.dump.tmp" ] || fail "le vieux .tmp est reste"
[ -f "$backups/weekly/$today" ] || fail "la copie hebdomadaire du jour a disparu"

echo "== echec de pg_dump"
psql_in -d ageguessr -c "alter user ageguessr password 'changed'"
rm "$backups/daily/$today"
echo 1000 >"$status/last-success"
if run_backup once; then fail "un pg_dump refuse doit faire echouer once"; fi
[ "$(cat "$status/last-success")" = 1000 ] || fail "un dump rate a touche l'heure du dernier succes"
[ ! -f "$backups/daily/$today" ] || fail "un dump rate a laisse un fichier"
[ -z "$(find "$backups/daily" -name '*.tmp')" ] || fail "un dump rate a laisse un .tmp"

echo OK

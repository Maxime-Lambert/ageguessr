#!/usr/bin/env bash
# Builds the edge image (Caddy + both builds of the frontend), runs it, and checks how it
# serves the two languages: no application test covers the Caddyfile. Usage, from the
# repository root: scripts/check-edge-locales.sh
set -euo pipefail

IMAGE=ageguessr-edge-check
NAME=ageguessr-edge-check
PORT=${PORT:-8099}
BASE="http://localhost:$PORT"

docker build -q -f Dockerfile.edge -t "$IMAGE" . >/dev/null
docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --rm --name "$NAME" -p "$PORT:80" "$IMAGE" >/dev/null
trap 'docker stop "$NAME" >/dev/null' EXIT
until curl -s -o /dev/null "$BASE/en/"; do sleep 0.5; done

failures=0
# expect_redirect <description> <path> <expected Location> [curl options...]
expect_redirect() {
  local description=$1 path=$2 expected=$3
  shift 3
  local headers
  headers=$(curl -s -o /dev/null -D - "$@" "$BASE$path")
  local status location cache
  status=$(awk 'NR==1 {print $2}' <<<"$headers")
  location=$(grep -i '^location:' <<<"$headers" | tr -d '\r' | cut -d' ' -f2 || true)
  cache=$(grep -i '^cache-control:' <<<"$headers" | tr -d '\r' | cut -d' ' -f2- || true)
  if [[ $status == 302 && $location == "$expected" && $cache == no-store ]]; then
    echo "ok   $description -> $location"
  else
    echo "FAIL $description: $status ${location:-no location} (cache: ${cache:-none}), expected 302 $expected"
    failures=$((failures + 1))
  fi
}
# expect_page <description> <path> <expected html lang>
expect_page() {
  local description=$1 path=$2 lang=$3
  local body
  body=$(curl -s "$BASE$path")
  if grep -q "<html lang=\"$lang\"" <<<"$body"; then
    echo "ok   $description -> index.html ($lang)"
  else
    echo "FAIL $description: not the $lang index.html"
    failures=$((failures + 1))
  fi
}

expect_redirect "/ without anything" / /en/
expect_redirect "/ with a French browser" / /fr/ -H 'Accept-Language: fr-FR,fr;q=0.9'
expect_redirect "/ with an English browser" / /en/ -H 'Accept-Language: en-GB,en;q=0.9,fr;q=0.8'
expect_redirect "the choice beats the browser" / /en/ -H 'Accept-Language: fr-FR' -b 'ageguessr_lang=en'
expect_redirect "the French choice" / /fr/ -b 'theme=dark; ageguessr_lang=fr'
expect_redirect "an unknown choice is ignored" / /fr/ -H 'Accept-Language: fr' -b 'ageguessr_lang=de'
expect_redirect "an old email link keeps its token" '/reset-password?token=abc' '/en/reset-password?token=abc'
expect_redirect "an old French legal link" '/privacy?lang=fr' /fr/privacy
expect_redirect "lang=fr keeps the other parameters" '/confirm-email?token=abc&lang=fr' '/fr/confirm-email?token=abc'
expect_page "the English site" /en/ en
expect_page "a French route" /fr/account fr
expect_page "an English route" /en/terms en

status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/health")
# No backend here: the proxy answers 502, which shows /api/* is not redirected.
if [[ $status == 502 ]]; then
  echo "ok   /api/* still goes to the backend"
else
  echo "FAIL /api/health answered $status, expected the proxy's 502"
  failures=$((failures + 1))
fi

if ((failures > 0)); then
  echo "$failures check(s) failed"
  exit 1
fi
echo "All checks passed"

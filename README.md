# Ageguessr 🎂

> Guess their age. Get warmer. Unlock a clue from the year that gives it away.

Ageguessr is a daily web game: guess the age of a handful of well-known people from
their photo. First guess gets you a hot/cold hint. Still off? Pick a category — music,
film, sports, science, world news, celebrities — and unlock a clue from a year you can
subtract from today's to get their age on the photo, before your second and final guess.

## Stack

- **Backend**: Spring Boot (Java 25), PostgreSQL, Redis
- **Frontend**: Angular, TypeScript, Signals, Tailwind CSS
- **Infra**: Docker, GitHub Actions, VPS, Caddy, Cloudflare

## Développement local

```bash
docker compose up                       # démarre PostgreSQL et Redis
cd ageguessr-api && ./mvnw spring-boot:run   # démarre l'API
cd frontend && pnpm start               # démarre le frontend
```

## Tests

Voir `docs/testing-strategy.md` pour le détail des catégories de tests.

Une fois après le clone, activer le hook pre-push qui fait tourner la suite
rapide (unitaire + intégration + architecture backend, unitaire frontend)
avant chaque push :

```bash
git config core.hooksPath .githooks
```

Playwright (tests interface + QA e2e) a besoin de bibliothèques système pour
son navigateur headless. Une fois après le clone :

```bash
cd frontend && pnpm exec playwright install-deps chromium
```

Cette commande demande un mot de passe `sudo` (installation de paquets système)
— à lancer manuellement dans un vrai terminal, elle ne peut pas être exécutée
par un agent sans accès interactif. En CI (`.github/workflows/ci.yml`), c'est
déjà automatisé via `pnpm exec playwright install --with-deps chromium`, aucune
action requise.

## Licence

MIT — voir [LICENSE](LICENSE), sauf les indices de `ageguessr-api/src/main/resources/hints/`,
tirés de Wikipédia (CC BY-SA 4.0) et de Wikidata (CC0) : voir le `README.md` de ce dossier.

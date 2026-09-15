# Ageguessr 🎂

> Guess their age. Get warmer. Unlock a clue from the year they were born.

Ageguessr is a daily web game: guess the age of a handful of well-known people from
their photo. First guess gets you a hot/cold hint. Still off? Pick a category — sports,
music, politics — and unlock a real historical event from their birth year before your
second and final guess.

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

MIT — voir [LICENSE](LICENSE)

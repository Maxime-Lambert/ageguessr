# Ageguessr — Project Constitution

## Stack
- **Backend** : Spring Boot (Java 25 LTS), Maven, PostgreSQL, Spring Data JPA/Hibernate, Flyway, Redis
- **Frontend** : Angular, TypeScript, Signals + services, Tailwind CSS
- **Auth** : Spring Security + JWT (bearer 1h) + Refresh Token rotatif (90 jours)
- **Infra** : Docker, GitHub Actions → OVH VPS

## Commandes
- Build backend : `cd ageguessr-api && ./mvnw compile`
- Tests backend : `cd ageguessr-api && ./mvnw test`
- Dev frontend : `cd frontend && pnpm start`
- Tests frontend : `cd frontend && pnpm test`
- Local complet : `docker compose up`
- Nouvelle migration : créer `ageguessr-api/src/main/resources/db/migration/V<N>__<nom>.sql`
  (Flyway, versionnement séquentiel, jamais de modification d'une migration déjà appliquée)

## Conventions
- Commits : `feat:` / `fix:` / `chore:` / `test:` / `docs:`
- Branches : `feature/kebab-case`, `fix/kebab-case`, `chore/kebab-case`, créées depuis `develop`
- Une PR = une feature = un plan dans `plans/`
- Tests écrits en même temps que le code, jamais après

## Git flow
- `main` : branche protégée (PR obligatoire, pas de push direct, pas de force-push) — reflète ce qui est prêt à déployer
- `develop` : branche d'intégration — toute feature/fix/chore part d'ici
- Cycle : `feature/xxx` (depuis `develop`) → PR vers `develop` → de temps en temps, PR `develop` → `main`
- Ne jamais commit directement sur `main` ou `develop`

## Architecture
- **Vertical Slice Architecture** — un package par feature, un package par use case
- Un seul module Maven `ageguessr-api/` (tests dans `src/test/java`, convention Maven standard —
  pas de module séparé comme un projet .NET) + `frontend/` pour Angular
- Structure type :
  - `ageguessr-api/src/main/java/app/ageguessr/features/<domaine>/<EntiteDomaine>.java`
    (entités dans leur package feature)
  - `ageguessr-api/src/main/java/app/ageguessr/features/<domaine>/<usecase>/<Fichiers>.java`
  - `ageguessr-api/src/main/java/app/ageguessr/shared/` pour les classes transversales entre features
  - `ageguessr-api/src/main/java/app/ageguessr/config/` pour la configuration Spring
- Package racine : `app.ageguessr` (reverse-domain de `ageguessr.app`)
- CQRS sans médiateur — handlers/services injectés directement via l'injection de dépendances Spring
- Validation : Jakarta Bean Validation (`@Valid` + annotations) sur toutes les commandes et queries
- Erreurs métier : exceptions custom (`NotFoundException`, `ValidationException`...) catchées par
  un `@RestControllerAdvice` global
- Erreurs infrastructure : exceptions standard propagées et loguées
- Voir `docs/decisions/architecture.md` pour le détail des choix

## Règles absolues
- Jamais de secrets ou clés API dans le code ou les fichiers versionnés
- `./mvnw test` (depuis `ageguessr-api/`) + `pnpm test` (depuis `frontend/`) passent avant tout push
- Expliquer chaque nouvelle notion et chaque choix d'implémentation non évident

## Workflow
- Développement 100% agentique (solo, pas de développeur humain écrivant du code directement) —
  les agents reviewers (`.claude/agents/`) sont le principal garde-fou qualité/sécurité, à ne
  jamais sauter
- Toute feature multi-session : créer un plan dans `plans/` avec le skill real-work
- Le plan file est la source de vérité, pas la conversation

## Routing
- Roadmap produit (checkboxes par semaine, à jour à chaque PR) : `docs/roadmap.md`
- Plan actif : `plans/active-plan.md`
- Décisions d'architecture : `docs/decisions/`
- Stratégie de tests : `docs/testing-strategy.md`

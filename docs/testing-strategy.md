# Stratégie de tests — Ageguessr

Sept catégories de tests, chacune avec un rôle précis et un outillage dédié. Une feature
n'a pas forcément besoin des sept — `/plan` doit justifier lesquelles s'appliquent (voir
`.claude/commands/plan.md`).

## Vue d'ensemble

| Catégorie | Outil | Emplacement | Vitesse |
|---|---|---|---|
| Unitaire backend | JUnit 5 + Mockito | `ageguessr-api/src/test/java/.../features/<F>/<UseCase>/*Test.java` | ms |
| Unitaire frontend | Vitest | `frontend/src/**/*.spec.ts` | ms |
| Intégration backend | JUnit 5 + Testcontainers | `ageguessr-api/src/test/java/.../integration/<F>/` | secondes |
| Fonctionnel backend | JUnit 5 + `@SpringBootTest` (MockMvc/WebTestClient) + Testcontainers | `ageguessr-api/src/test/java/.../functional/<F>/` | secondes |
| Interface (UI) | Playwright | `frontend/e2e/component/*.spec.ts` | secondes |
| QA / parcours utilisateur | Playwright (app complète) | `frontend/e2e/journeys/*.spec.ts` | secondes à minutes |
| Architecture | ArchUnit (JUnit 5) | `ageguessr-api/src/test/java/.../architecture/` | ms |
| Mutation | PIT (pitest) | config dans `ageguessr-api/pom.xml` | minutes |

## Détail par catégorie

### Unitaire backend
Teste un handler, un validator ou une règle métier en isolation totale — pas de base de
données, pas de réseau, dépendances mockées (Mockito) si nécessaire.
**Ne couvre pas** : le câblage JPA/Hibernate, le routing HTTP, la sérialisation.

### Unitaire frontend
Teste un composant ou un service Angular en isolation (jsdom), sans vrai navigateur.
**Ne couvre pas** : le rendu réel dans un navigateur, le CSS, les interactions complexes
multi-composants.

### Intégration backend
Teste un handler contre un vrai PostgreSQL (Testcontainers) : vérifie que la requête
Spring Data JPA fait ce qu'on attend, que les contraintes DB sont respectées.
**Ne couvre pas** : le pipeline HTTP (filtres de sécurité, routing) — c'est le rôle du
test fonctionnel.

### Fonctionnel backend
Teste un endpoint de bout en bout via `@SpringBootTest` (`webEnvironment = RANDOM_PORT`)
+ `MockMvc`/`WebTestClient` + vrai PostgreSQL : requête HTTP entrante → réponse HTTP
sortante, filtres Spring Security inclus. C'est la différence clé avec l'intégration :
ici on ne appelle pas le handler directement, on passe par le vrai pipeline Spring.
**Ne couvre pas** : le rendu frontend, les parcours multi-écrans.

### Interface (UI)
Teste le rendu et les interactions d'une page/d'un composant isolé dans un vrai
navigateur (Playwright), pour attraper ce que jsdom ne peut pas (CSS réel, comportement
navigateur). **Ne couvre pas** : l'intégration avec un vrai backend — utilise des mocks
réseau si besoin.

### QA / parcours utilisateur
Teste un parcours utilisateur complet (plusieurs pages, plusieurs actions) contre
l'application réelle : API réelle, PostgreSQL réel, Redis réel, frontend réel. Le niveau
le plus proche de l'usage réel, donc le plus lent et le plus coûteux à maintenir —
réservé aux parcours critiques (ex : partie quotidienne complète, inscription →
connexion → ajout d'ami).
**Ne couvre pas** : les cas limites unitaires, qui doivent être testés plus bas dans la
pyramide (plus rapide, plus précis sur la cause d'un échec).

### Architecture
Vérifie automatiquement les règles définies dans `docs/decisions/architecture.md` (pas
de dépendance croisée entre features, convention de package, pas de couplage
handler-à-handler). Empêche la dérive architecturale silencieuse au fil des features.

### Mutation
Modifie automatiquement le code de `ageguessr-api` (inverse une condition, change une
constante, etc.) et vérifie que la suite de tests existante détecte chaque mutation.
Mesure la qualité réelle des tests, pas seulement leur couverture de lignes. Seuil
informatif au démarrage du projet (le code est trop jeune pour un seuil strict) — à
durcir manuellement quand la base de code métier grandit.

## Enforcement

- **Hook pre-push local** (`.githooks/pre-push`) : sous-ensemble rapide (unitaire +
  intégration + architecture backend, lint + format + unitaire frontend). Activer une
  fois par clone : `git config core.hooksPath .githooks`.
- **CI GitHub Actions** (`.github/workflows/ci.yml`) : suite complète (incl. lint +
  format frontend) sauf mutation, bloquante sur les PR vers `develop` et `main`.
- **Mutation testing** (`.github/workflows/mutation.yml`) : sur PR vers `main`
  uniquement, informatif (n'échoue pas le build).

Playwright et PIT ne tournent jamais dans le hook local : trop lents pour un push à
chaque commit.

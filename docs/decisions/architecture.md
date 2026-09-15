# Décisions d'architecture — Ageguessr

## Structure générale

### Monorepo
**Décision** : un seul repo Git contenant backend, frontend et infra.

**Raison** : projet solo, développement 100% agentique, déploiements systématiquement
couplés (une feature full-stack = une seule PR, un seul déploiement). Deux repos séparés
créeraient de la friction sans apporter de valeur à cette échelle.

**Structure** :
```
ageguessr/
├── ageguessr-api/        ← Spring Boot, tout le code applicatif (src/main + src/test)
├── frontend/              ← Angular
├── docker-compose.yml
└── .github/workflows/
```

---

## Backend

### Vertical Slice Architecture
**Décision** : une feature = un package = tous les fichiers qui la concernent.

**Raison** : accès immédiat à tout le code d'un use case lors d'une modification. Une
architecture en couches (controllers/services/repositories globaux) suroptimise pour un
changement d'infrastructure (changer d'ORM, de base de données) qui n'arrivera pas sur
ce projet.

**Structure type** :
```
ageguessr-api/src/main/java/app/ageguessr/features/persons/
├── Person.java                      ← entité de domaine
├── GetDaily/
│   ├── GetDailyPersonsQuery.java
│   ├── GetDailyPersonsHandler.java
│   ├── GetDailyPersonsResponse.java
│   └── GetDailyPersonsController.java
├── SubmitGuess/
│   └── ...
ageguessr-api/src/main/java/app/ageguessr/shared/
    ├── exceptions/
    │   ├── NotFoundException.java
    │   └── ValidationException.java
ageguessr-api/src/main/java/app/ageguessr/config/
    └── GlobalExceptionHandler.java
ageguessr-api/src/test/java/app/ageguessr/features/persons/
    └── GetDaily/
        └── GetDailyPersonsHandlerTests.java
```

### Package racine
**Décision** : package racine `app.ageguessr`, pas `app.ageguessr.api`.

**Raison** : reverse-domain du nom de domaine `ageguessr.app`. Le suffixe `.api` serait
redondant — le nom du module sert à distinguer applicatif/tests, pas à préfixer chaque
package.

### CQRS sans médiateur
**Décision** : pattern Command/Query avec handlers injectés directement via l'injection
de dépendances Spring (`@Service` + constructeur, pas de `@Autowired` sur champ).

**Raison** : pas de framework de médiation (type MediatR côté .NET) entre le controller
et le handler — handlers directs plus simples à tracer en debug, sans dépendance
supplémentaire à apprendre ou maintenir.

**Pattern** :
```java
// Handler
@Service
@RequiredArgsConstructor
public class GetDailyPersonsHandler {
    private final PersonRepository personRepository;

    public GetDailyPersonsResponse handle(GetDailyPersonsQuery query) { ... }
}

// Controller
@RestController
@RequiredArgsConstructor
class GetDailyPersonsController {
    private final GetDailyPersonsHandler handler;

    @GetMapping("/api/daily")
    ResponseEntity<GetDailyPersonsResponse> get() {
        return ResponseEntity.ok(handler.handle(new GetDailyPersonsQuery()));
    }
}
```

### Validation
**Décision** : Jakarta Bean Validation (`@Valid` + annotations `@NotNull`, `@Min`...) sur
toutes les commandes et queries entrantes.

**Raison** : intégré nativement au framework (starter `validation`), pas de dépendance
tierce à ajouter, testable unitairement de manière isolée. Pour les règles de validation
complexes (cross-field, dépendant d'un appel base de données), un validator custom
(`Validator` Spring ou classe dédiée appelée explicitement par le handler) plutôt que de
forcer une annotation Bean Validation à faire ce pour quoi elle n'est pas conçue.

### Gestion des erreurs
**Décision** : exceptions custom pour les erreurs métier, `@RestControllerAdvice` global
pour la conversion en réponses HTTP.

**Raison** : compromis entre lisibilité (le Result pattern introduirait une librairie
supplémentaire) et contrats clairs (les exceptions custom documentent les cas d'erreur
dans le code).

**Règle** :
- Erreurs métier prévisibles → `NotFoundException`, `ConflictException`,
  `ForbiddenException` catchées par `GlobalExceptionHandler`
- Erreurs infrastructure (DB down, service externe inaccessible) → exception standard
  propagée, loguée, retournée en 500

### Migrations Flyway
**Décision** : migrations SQL versionnées (`V<N>__<nom>.sql` dans
`src/main/resources/db/migration/`), jamais automatiques au démarrage en local,
`ddl-auto=validate` (Hibernate ne génère jamais le schéma, seulement vérifie qu'il
correspond aux entités).

**Raison** : la génération automatique de schéma (`ddl-auto=update`) est dangereuse en
prod (pas de rollback facile, risque de perte de données). Flyway préféré à Liquibase :
migrations en SQL brut plus simples à relire et déboguer qu'un format XML/YAML abstrait.

### Lombok
**Décision** : Lombok (`@RequiredArgsConstructor`, `@Getter`/`@Setter` quand pertinent)
pour réduire le boilerplate d'injection de dépendances et des entités JPA.

**Raison** : évite le code répétitif des constructeurs d'injection et des accesseurs
JPA. Pour les DTOs (commands, queries, responses) immuables sans besoin JPA, préférer un
`record` Java natif à Lombok — plus simple, pas de génération de code à comprendre.

---

## Authentification

### JWT + Refresh Token rotatif
**Décision** : bearer JWT (1h) + refresh token rotatif (90 jours) stocké en base, via
Spring Security + OAuth2 Resource Server (validation du JWT côté serveur), refresh token
opaque généré et géré manuellement (pas de serveur OAuth complet).

**Raison** : le refresh token à usage unique (rotation) invalide le token précédent à
chaque renouvellement — si un token est volé, il est inutilisable après le premier
refresh légitime. `spring-boot-starter-oauth2-resource-server` est l'idiome Spring pour
valider un JWT bearer (signature, expiration) sans réinventer un filtre de sécurité —
même s'il n'y a pas de véritable serveur d'autorisation OAuth derrière, seulement une
émission de JWT maison à l'inscription/connexion.

**Implémentation** : à détailler dans un plan dédié (`plans/`) au moment de
l'implémenter — structure de la table `RefreshTokens` (`Token`, `UserId`, `ExpiresAt`,
`RevokedAt`), cookie `HttpOnly` (pas en localStorage), rotation à chaque refresh (nouveau
bearer + nouveau refresh token, ancien révoqué), job de nettoyage périodique des tokens
expirés/révoqués.

### Jeu sans compte, compte optionnel
**Décision** : le quiz quotidien est jouable sans compte. La connexion débloque
uniquement l'historique et le système d'amis.

**Raison** : réduit la friction d'entrée (cohérent avec le genre "daily puzzle" type
Wordle/GeoGuessr) — le compte est une fonctionnalité de rétention, pas une barrière
d'entrée.

### MVP : email/password uniquement
**Décision** : pas de social login pour le MVP.

**Raison** : complexité d'intégration OAuth non justifiée au stade MVP. À ajouter
(Google, autres) post-lancement si la demande existe.

---

## Domaine du jeu

### Sourcing des personnes (identité, date de naissance, photo)
**Décision** : Wikidata comme source structurée principale.
- Date de naissance : propriété `P569`
- Photo libre de droits : propriété `P18` (image Commons), ou à défaut interrogation des
  Structured Data on Commons (`depicts` = la personne) pour trouver une image alternative
  plus récente que celle de l'infobox Wikipedia
- Notoriété ("un minimum connu") : nombre de sitelinks Wikidata (nombre de langues avec
  un article) comme proxy, éventuellement croisé avec l'API Wikimedia Pageviews pour
  écarter les personnes quasi inconnues malgré un sitelink count correct

**Raison** : Wikidata + Wikipédia forment un écosystème cohérent et gratuit, couverture
mondiale (pas seulement anglo-saxonne), déjà structuré (propriétés typées, pas de
parsing HTML fragile). Alternative écartée : une API dédiée aux célébrités (souvent
payante, couverture inégale hors célébrités anglo-saxonnes, incompatible avec l'objectif
"personnalités mondialement connues" mais pas uniquement US/UK).

**Limite connue** : "photo la plus récente possible" n'est pas un champ structuré
Wikidata natif — nécessitera une heuristique (date de la photo dans les métadonnées
Commons, ou date d'upload en fallback) à valider sur un échantillon réel avant de
généraliser (vérifier sur un échantillon représentatif avant d'industrialiser
l'extraction, plutôt que de supposer que l'heuristique fonctionne à grande échelle sans
preuve).

### Sourcing des indices événementiels par catégorie
**Décision** : pages Wikipédia par année et catégorie (ex. `1976_in_music`,
`1976_in_sports`, `1976_in_politics`) comme source des indices "événement survenu
l'année de naissance de la personne".

**Raison** : reste dans l'écosystème Wikipedia déjà retenu pour les personnes — un seul
type de source à parser (Wikidata + Wikipedia) plutôt que d'ajouter un site tiers.

**Limite connue** : contenu semi-structuré (texte, pas une API propre), qualité
inégale selon les années/catégories — nécessitera curation manuelle sur un échantillon
avant d'industrialiser l'extraction, potentiellement une base d'indices pré-construite
une fois (ingestion ponctuelle) plutôt qu'un job récurrent nightly, les événements
historiques ne changeant pas.

### Anti-triche sur l'image
**Décision** : les images ne sont jamais servies depuis l'URL Wikimedia Commons brute —
un endpoint backend interne (`/api/images/{id}`) sert l'image par identifiant opaque,
sans exposer le nom de fichier Commons (qui contient souvent le nom de la personne) ni
le nom de la personne dans aucune requête réseau avant la résolution du round.

**Raison** : une URL Commons `Claude_Francois_1976.jpg` ou une recherche d'image
inversée révèlerait l'identité (donc l'âge) en quelques secondes — contournerait
totalement le jeu. Contrainte posée dès le schéma de données, pas un ajout a posteriori.

### Système de points
**Décision** : bonus plein si trouvé du premier coup ; sinon, l'essentiel des points est
déterminé par la précision de la deuxième tentative (après indice), avec un plafond
légèrement inférieur au bonus premier coup.

**Raison** : incite à essayer sérieusement dès la première tentative sans punir
excessivement l'usage de l'indice, qui reste la mécanique centrale du jeu. Formule exacte
à finaliser et affiner par playtest lors de l'implémentation (`plans/`), pas figée ici.

### Réinitialisation quotidienne
**Décision** : minuit UTC, même partie (mêmes personnes) pour tous les joueurs ce
jour-là.

**Raison** : cohérent avec le genre "daily puzzle" (Wordle, GeoGuessr Daily Challenge) —
simplifie aussi la comparaison de scores entre amis (même partie = comparaison
équitable).

### Système d'amis
**Décision** : système complet avec demandes d'ami (envoi/acceptation/refus), liste
d'amis persistante, comparaison des scores quotidiens et historique.

**Raison** : décision produit explicite — feature sociale à part entière, pas un simple
lien de partage éphémère. Impact structurel : nécessite un graphe de relations
utilisateur↔utilisateur en base (table `FriendRequests`/`Friendships`), à détailler dans
le plan dédié à cette feature.

---

## Frontend

### Angular Signals + services (pas NgRx)
**Décision** : deux responsabilités distinctes, pas interchangeables.

**Règle** :
- **Signals** (`signal()`, `computed()`) : état UI local (tentative en cours, indice
  sélectionné, étape du round)
- **Services Angular + `HttpClient`** : tout ce qui vient du serveur (personnes du jour,
  historique, amis) — cache et invalidation gérés explicitement dans le service, pas de
  librairie de cache serveur ajoutée tant qu'un besoin concret ne le justifie pas (ex.
  TanStack Query Angular si la complexité de cache le justifie un jour)

**Raison** : NgRx écarté pour le MVP — pattern Redux complet surdimensionné pour un état
applicatif de cette taille en solo. Les Signals natifs Angular suffisent sans dépendance
supplémentaire.

### CSS : Tailwind
**Décision** : Tailwind CSS v4 (plugin PostCSS). Pas de librairie de composants
(Shadcn/Radix) au démarrage.

**Raison** : une librairie de composants peut être ajoutée à tout moment si besoin de
composants complexes. Partir sans évite une dépendance non nécessaire au MVP.

### Tests unitaires : Vitest
**Décision** : Vitest, via le builder `@angular/build:unit-test` (défaut Angular CLI
récent).

**Raison** : défaut natif Angular depuis les versions récentes — aucune dépendance
supplémentaire à ajouter (Jest ou Karma auraient nécessité une configuration manuelle).

---

## Infrastructure

### Environnements
**Décision** : deux environnements uniquement — local et prod.

**Raison** : pas de budget pour un environnement de staging dédié. La CI/CD
(GitHub Actions) joue le rôle de validation intermédiaire : build + tests automatiques
avant chaque déploiement en prod.

### Docker
**Décision** : un seul `docker-compose.yml` pour le développement local
(PostgreSQL + Redis), un `docker-compose.prod.yml` pour la prod sur VPS.

**Note ports locaux** : PostgreSQL exposé sur le port hôte `5433` (pas `5432`) et Redis
sur `6380` (pas `6379`) — évite un conflit si un autre projet tourne en parallèle sur
cette machine avec ses propres conteneurs sur les ports par défaut.

### Hébergement
**Décision** : VPS partagé avec un autre projet personnel, une passerelle Caddy unique
au niveau du VPS (hors de ce repo) pour le HTTPS + le routage par domaine, et
Cloudflare pour le DNS — domaine `ageguessr.app`.

**Raison** : coût fixe et prévisible, expérience ops valorisable pour un projet solo.

**Mutualisation VPS et passerelle partagée** : un seul serveur ne peut avoir qu'un
processus qui écoute sur les ports 80/443 — deux projets sur la même machine ne peuvent
donc pas chacun faire tourner leur propre Caddy exposé publiquement. La solution : une
passerelle Caddy unique, minimale, qui ne fait que terminer le TLS et router par nom de
domaine vers le conteneur "edge" de chaque projet via un réseau Docker externe partagé
(`gateway`) — elle ne vit dans aucun des deux repos (n'est le code d'aucun des deux
projets), uniquement sur le VPS. Chaque projet garde son propre conteneur "edge" (sert
son propre frontend statique + reverse-proxy `/api/*` vers son propre backend), mais ce
conteneur ne termine plus le TLS lui-même et n'est plus jamais exposé directement sur
80/443 — il écoute en HTTP simple sur le réseau partagé, seule la passerelle lui parle.
Aucun partage de code, de base de données ni de configuration applicative entre projets
— uniquement le point d'entrée réseau, qui est par nature une ressource unique par
machine.

**Implémentation** (`Dockerfile.edge`, `Caddyfile` à la racine du repo) : le conteneur
edge du projet build le frontend, sert les fichiers statiques via `file_server`, et
route `/api/*` vers `backend:8080` en interne, sans retirer le préfixe (chaque
endpoint backend est mappé avec son `/api/...` complet, ex. `/api/auth/login` —
contrairement à un design où le préfixe ne serait qu'une convention d'edge) — écoute sur
`:80` sans bloc `tls` (adresse Caddy sans nom de domaine = pas d'HTTPS automatique
tenté). `docker-compose.prod.yml` attache le service `edge` à la fois au réseau interne
du projet (pour joindre `backend`) et au réseau externe `gateway` (pour être joignable
par la passerelle) ; `postgres`/`redis`/`backend` ne sont jamais exposés sur l'hôte.

### Migrations en production
**Décision** : migrations Flyway appliquées automatiquement au démarrage du conteneur
backend (`spring.flyway.enabled=true` déjà actif par défaut) — acceptable pour une
instance unique à faible trafic.

### Variables d'environnement
- **Local** : fichier `.env` (gitignored)
- **Prod** : secrets GitHub Actions injectés dans le VPS au déploiement
- Jamais de secrets dans le code ou dans un fichier versionné

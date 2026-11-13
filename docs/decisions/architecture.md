# Décisions d'architecture — AgeGuessr

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

**Dépendances entre features** : une feature n'importe pas une autre, avec trois
exceptions :
- `game → people` (2026-11-02) : le jeu repose sur les photos de `people` (âge, statut de curation,
  case, `publicId`), donc `game` peut lire et modifier ses entités ;
- `game → hints` (2026-11-02) : le jeu lit les indices d'une année (`HintRepository.findEligible`) ;
- `friends → game` (2026-11-10) : la comparaison du jour et le classement entre amis
  appliquent les règles du jeu (jour complet, maximum, manche neutralisée, moyenne,
  série), écrites une seule fois dans `game/PlayerHistory` et lues par
  `game/PlayerHistories`. Le code des invitations reprend aussi `game/ShareCodes`.

L'inverse est interdit : ni `people` ni `hints` ne savent rien du jeu, et le jeu ne sait
rien des amis. C'est pour ça
qu'une décision de curation (photo ou indice) est lue par le jeu, et pas poussée vers lui
(voir « Tirage quotidien »). Ces règles sont vérifiées par ArchUnit
(`ArchitectureTests`).

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
- Erreurs de requête de Spring MVC (mauvaise méthode, type de contenu non supporté…) →
  elles implémentent `org.springframework.web.ErrorResponse` et gardent leur statut 4xx.
  Le gestionnaire « attrape-tout » ne doit pas les transformer en 500. Un corps
  illisible (JSON malformé, champ primitif absent) → 400 `MALFORMED_REQUEST`, avec un
  message générique qui ne renvoie pas les détails du parseur

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

### Pseudo public, connexion par email
**Décision** : chaque compte a un pseudo (3 à 20 caractères `[A-Za-z0-9_-]`, unique sans
tenir compte de la casse, casse d'affichage conservée), choisi à l'inscription et
modifiable. La connexion reste par email uniquement.

**Raison** : le pseudo est ce que voient les amis et les classements ; l'email ne doit
jamais être affiché. Le pseudo est public, donc s'en servir comme identifiant de
connexion faciliterait le brute-force ciblé. Il n'est pas mis dans le JWT : modifiable,
il y serait périmé pendant jusqu'à une heure.

### Politique de mot de passe
**Décision** : un mot de passe choisi (inscription, réinitialisation, changement) fait au
moins 12 caractères et au plus 72 octets en UTF-8. Il est refusé s'il figure dans une
liste de mots de passe courants, s'il contient `ageguessr`, ou s'il contient le pseudo ou
la partie de l'email avant le `@` (pour ces deux derniers, seulement à partir de 4
caractères). Toutes les comparaisons ignorent la casse. Aucune règle de composition
(majuscule, chiffre, symbole). La connexion n'applique aucune de ces règles : les
comptes créés sous l'ancienne règle (8 caractères) continuent de fonctionner.

**Raison** :
- **Pas de composition** : le NIST (SP 800-63B rév. 4, 2025) l'interdit désormais. Les
  études sur de vrais mots de passe montrent que ces règles produisent des
  transformations prévisibles (`password` → `Password1!`), que les outils d'attaque
  appliquent d'office. La longueur et le refus des mots de passe connus protègent mieux.
- **12 caractères** : la CNIL (recommandation de 2022) demande environ 50 bits
  d'entropie quand l'accès est freiné après des échecs, ce qui est notre cas
  (verrouillage du compte et limite de tentatives). Sans composition imposée, il faut
  compter sur un alphabet de 26 lettres : 11 caractères donnent ~52 bits, et 12 laissent
  une marge. Le NIST demande 15 caractères quand le mot de passe est le seul facteur :
  on reste à 12 tant que la double authentification (TOTP, roadmap) n'existe pas, pour
  ne pas décourager l'inscription à un jeu.
- **Liste locale** (`src/main/resources/security/common-passwords.txt`, issue de
  SecLists, licence MIT, voir le `README.md` voisin) plutôt que l'API Have I Been Pwned :
  pas de tiers ni de dépendance réseau dans l'inscription. Seules les entrées d'au moins
  12 caractères du top 1 million sont gardées (~30 000), les autres étant déjà refusées
  par la longueur.
- **Où vit la règle** : le format reste dans la contrainte Bean Validation `@Password`.
  La liste et les mots du contexte sont dans `features/auth/PasswordPolicy`, appelé par
  les handlers, parce qu'il faut l'utilisateur, qui n'est connu au reset et au
  changement qu'une fois chargé. Codes d'erreur distincts (`PASSWORD_TOO_COMMON`,
  `PASSWORD_CONTAINS_PERSONAL_INFO`) pour un message précis côté front. La liste n'est
  jamais envoyée au navigateur.
- **Ordre** :
  - à l'inscription, la politique passe avant l'unicité de l'email, pour qu'un refus ne
    révèle pas un email pris ;
  - au changement, elle passe après la vérification du mot de passe actuel : sans lui,
    on n'apprend rien sur le compte, et l'échec compte dans le verrouillage ;
  - au reset, un refus annule la consommation du lien, qui reste utilisable pour un
    nouvel essai.

### Récupération du compte et actions sensibles
**Décision** : l'email est la racine de confiance du compte (il n'est pas modifiable
pour l'instant). Les actions sensibles passent par des liens à usage unique envoyés par
email (table `account_tokens`, colonne `purpose`, seul le hash SHA-256 est stocké) :
- **mot de passe oublié** : lien valable 1 h ; la réponse est identique que le compte
  existe ou non. Le reset révoque toutes les sessions, lève le verrouillage et marque
  l'email comme vérifié (suivre le lien prouve l'accès à la boîte) ;
- **notification après chaque changement de mot de passe** : contient un lien « Secure
  your account » (un reset valable 24 h). Un changement de mot de passe n'invalide pas
  les liens de reset en cours, sinon un intrus qui change le mot de passe deux fois
  tuerait le lien de récupération du propriétaire ;
- **changement de mot de passe** : exige le mot de passe actuel, garde la session
  courante et révoque les autres ;
- **suppression de compte** : exige le mot de passe **et** la confirmation d'un lien
  email (1 h). Un intrus qui n'a que le mot de passe ne peut pas faire l'irréversible.
  La page de confirmation demande un clic explicite : les scanners de liens des
  messageries ouvrent les liens des mails.

La re-saisie du mot de passe (changement, suppression) compte dans le même verrouillage
que la connexion (`AccountLockout`) : une session volée ne doit pas permettre de deviner
le mot de passe sans limite. Pour la même raison, ces handlers ne sont pas
`@Transactional` (une transaction annulerait l'échec enregistré juste avant l'exception).

**Limite connue** : l'existence d'un email reste déductible via l'inscription (409
`EMAIL_TAKEN`), choix antérieur conservé pour l'ergonomie ; le délai d'envoi d'un mail
sur « mot de passe oublié » n'ajoute donc pas de fuite. Un attaquant qui contrôle la
boîte mail contrôle le compte : seule une double authentification (TOTP, dans la
roadmap) protégerait de ce cas.

**Règle pour le futur changement d'email** : confirmation sur la nouvelle adresse **et**
notification à l'ancienne avec un lien d'annulation valable plusieurs jours. Sans ça, un
vol de compte devient définitif (l'intrus remplace l'email, le propriétaire ne peut plus
rien récupérer).

### Suppression des données (RGPD)
**Décision** : la suppression d'un compte est physique (`DELETE` de la ligne `users`).
**Toute table qui référence un utilisateur doit être en `ON DELETE CASCADE`** (ou, si
une donnée doit survivre pour d'autres joueurs, anonymisée explicitement), pour que ce
seul `DELETE` suffise au droit à l'effacement. Vérifié par
`CascadeDeleteIntegrationTest`, à étendre à chaque nouvelle table de ce type.

### Rôle administrateur
**Décision** : colonne `users.role` (`USER` / `ADMIN`, défaut `USER`), posée **à la
main en SQL**. Aucun endpoint ni écran ne la modifie.
- Le rôle voyage dans le JWT : claim `roles`, émis par `AuthTokenService` depuis la
  base à chaque émission, refresh compris.
- `JwtConfig` le convertit en autorité `ROLE_ADMIN`, et `SecurityConfig` exige ce rôle
  sur `/api/admin/**`.
- Le front lit le rôle via `/api/auth/me`, uniquement pour afficher le lien et garder
  la route. Le serveur reste seul juge.

**Raison** :
- un seul administrateur prévu : un rôle en base suffit, sans système de permissions ;
- l'absence d'interface pour devenir admin supprime toute possibilité
  d'auto-promotion ;
- le contrôle par chemin dans `SecurityConfig` évite que les features (`people`)
  dépendent de `auth`.

**Limites connues** :
- **Le compte admin n'est protégé que par son mot de passe** : pas de TOTP avant la
  Semaine 7 (roadmap). Choisir un mot de passe long et unique.
- Retirer le rôle en SQL prend effet au plus tard à l'expiration de l'access token
  (≤ 60 min), le refresh relisant le rôle en base.

### Pages légales et acceptation des CGU
**Décision** : trois pages statiques côté Angular (`features/legal/`) : `/legal-notice`,
`/terms` et `/privacy`. Chacune existe en anglais et en français : le template contient
les deux versions en blocs entiers (un texte juridique se relit en entier, hors du
catalogue de traduction) et la build de la langue choisit la sienne (`/en/terms`,
`/fr/terms`, voir « Internationalisation »).
- **Éditeur** : particulier non professionnel, identité non publiée (LCEN) : seuls
  l'email `contact@ageguessr.app` et les coordonnées de l'hébergeur OVH apparaissent.
- **Droit applicable** : le droit français, sans priver un consommateur étranger des
  règles impératives de son pays (Rome I, art. 6). La version française des CGU prévaut.
- **Âge minimum** : 13 ans pour un compte. En dessous, la loi américaine COPPA impose un
  consentement parental vérifiable. La base légale du compte est le contrat, pas le
  consentement : l'âge du consentement de l'art. 8 RGPD ne s'applique donc pas.
- **Acceptation** : une case non pré-cochée à l'inscription. L'API exige
  `RegisterCommand.acceptTerms` (`@AssertTrue`, booléen primitif). Elle enregistre
  `users.terms_version` (la valeur de `TermsVersion.CURRENT`) et
  `users.terms_accepted_at`. Le frontend affiche la même date (`TERMS_VERSION` dans
  `features/legal/legal-dates.ts`) : les deux constantes changent ensemble.
- **Ré-acceptation** (PR `feature/terms-reacceptance`) : `TermsVersion` ne change que
  pour une modification importante. Un compte dont `terms_version` est différent (ou
  NULL) reçoit `termsAccepted: false` dans `/api/auth/me`, et le site affiche un bandeau
  non bloquant (`features/auth/terms-banner`) : lien vers les CGU, « Accept », « Later »
  (masqué pour l'onglet, `sessionStorage`), et le rappel qu'un refus passe par la
  suppression du compte (CGU, article 9). `PUT /api/auth/me/terms` n'accepte que la
  version en vigueur (409 `TERMS_VERSION_OUTDATED` sinon) et garde la date de la première
  acceptation. L'API ne bloque rien d'autre : choix de l'utilisateur, le site étant le
  seul client. Exception front **`auth → legal`** : le bandeau lit la date des CGU dans
  `legal-dates.ts`.

**Raison** : le contenu change rarement et chaque modification doit être relue en PR. Un
CMS ou une table n'apporterait rien. Stocker la version acceptée est la seule manière de
prouver l'acceptation et de savoir qui doit ré-accepter quand les CGU changent.

**Règle** : la politique de confidentialité ne décrit que ce que le code fait réellement
(durées, cookies, sous-traitants). Tout changement de ces éléments dans le code (nouveau
cookie, nouveau sous-traitant, nouvelle durée, sauvegardes) s'accompagne de la mise à
jour de `/privacy` et de sa date.

---

## Domaine du jeu

### Sourcing des personnes (identité, date de naissance, photo)
**Décision** : le joueur devine **l'âge de la personne sur la photo**. Personnes
vivantes et décédées sont acceptées. Le pool est construit par une **ingestion
ponctuelle**, pas pendant les parties :
- **Liste des personnes** : humains (`P31 = Q5`) avec une image `P18`, **nés en 1930
  ou après** (`app.people.ingest.min-birth-year`), par sitelinks décroissants
  (notoriété), interrogés sur **QLever** (`qlever.dev`). Le SPARQL public de Wikidata
  dépasse son délai sur ce classement.
  - Pourquoi 1930 : sans ce filtre, 41 des 300 personnes les plus connues seulement
    sont vivantes, et les figures historiques n'ont presque jamais de photo à date
    corroborée par l'EXIF. Le jeu veut des photos récentes et de bonne qualité. Une
    ingestion par QID (`--qid`) ignore le filtre.
  - Six petites requêtes par page de 500 : classement, naissances, décès, images,
    labels, genre et continents. Elles sont assemblées en Java, environ 7 s par page.
    Une requête unique en jointure dépassait la limite de 30 s de QLever.
  - Labels : `en` ou `fr` exact, sinon une variante régionale (`en-gb`…), sinon le
    label `mul`. Depuis 2024, Wikidata fusionne les labels identiques entre langues en
    un seul label `mul` et retire le label `en` : sans ce repli, 14 des 200 personnes
    les plus connues (Douglas Adams, Shakira…) passaient pour sans nom.
- **Date de naissance** : `P569` au jour près, en calendrier grégorien, avec les rangs
  Wikidata (un `deprecated` est ignoré, un `preferred` l'emporte). Au moindre doute,
  la personne est écartée.
- **Photos candidates** : le `P18`, plus les fichiers Commons dont les Structured Data
  indiquent `depicts (P180) = la personne`. Format JPEG uniquement. Licences en
  allow-list : CC0, CC BY, CC BY-SA, domaine public.
- **Âge exact obligatoire** : l'âge doit être le même aux deux bornes de l'intervalle
  permis par la précision de la date. Une date de prise de vue n'est retenue que si :
  - elle est **déclarée** (SDC `P571` et/ou champ `date` de `{{Information}}`, parsé
    strictement, sans « circa », décennie ni intervalle) ;
  - elle est **corroborée par l'EXIF `DateTimeOriginal`** de l'appareil ;
  - aucune année du nom de fichier ne la contredit.

  Plus de repli sur la date d'upload : un tirage ancien numérisé porte souvent la date
  du scan.
- **Aucune photo d'une personne mineure** (décision du 2026-10-26) : âge calculé sur
  la photo d'au moins 18 ans, même si la personne est adulte aujourd'hui. Règle dans
  `PhotoEvaluator` (motif `MINOR`), et contrainte `CHECK (age_at_photo BETWEEN 18 AND
  120)` en base (V8) comme filet. La curation rejette en plus une photo où la personne
  paraît mineure : la date est alors fausse malgré l'EXIF.
- **Équilibre des âges** (décision du 2026-10-27), par tranches de 8 ans : 18-25,
  26-33… 74-81, 82+. Parmi les photos possibles d'une personne, `PhotoSelector`
  préfère celle dont la tranche compte le moins de photos en jeu (base + run en
  cours), sans jamais renoncer à une photo que les règles d'écart permettent. Mesure
  du 2026-10-27 : la répartition est déjà assez plate de 18 à 73 ans, le manque est
  au-delà de 74 ans. Le tirage quotidien ne rééquilibre pas les âges (décision du
  2026-11-02, voir « Tirage quotidien ») : seule la collecte y penche.
- **Hauteur minimale de 600 px** (décision du 2026-10-28, à ajuster) : la hauteur
  compte plus que la largeur, le jeu affichant un portrait. Les miniatures sont
  demandées à Commons **en hauteur** (`app.people.ingest.thumbnail-height`, 1 000 px) :
  en largeur (800 px), une photo paysage d'un grand original revenait à 533 px de haut.
  Commons n'agrandit jamais, donc une miniature sous 600 px signale un original trop
  petit : rejet `TOO_SMALL` (`app.people.ingest.min-image-height`) avant tout
  téléchargement. Les photos stockées avant ce changement se corrigent avec
  `--fix-small-images` : miniature plus haute si Commons en a une (statut inchangé),
  rejet automatique sinon (`reviewed_by` vide), même approuvée. La page de curation
  affiche la taille réelle de l'image et avertit sous 600 px.
- **Une photo par tranche d'au moins 10 ans** (en âge et en année civile) pour une même
  personne, avec un plafond de 5. Les deux valeurs sont des propriétés
  (`app.people.ingest.*`).
- **Curation manuelle obligatoire** : chaque photo est stockée `PENDING`, et seules les
  photos `APPROVED` sont servies ou jouées. Le statut `REJECTED` n'est jamais
  reproposé et libère son année et son âge.
  - Outil : page `/admin/curation`, réservée au rôle admin. Une photo à la fois, avec
    nom, dates, âge calculé, source Commons et autres photos de la personne. Raccourcis
    A / R / → / U.
  - Endpoints `/api/admin/photos/**` ; décisions tracées (`reviewed_at`,
    `reviewed_by`).
  - Personnes vivantes et décédées s'approuvent de la même façon (voir « Droit à
    l'image » plus bas).
- **Ligne éditoriale** (décision du 2026-10-26, automatisée et élargie le 2026-10-28) :
  rien qui puisse raviver un traumatisme. Les condamnations non violentes (fraude,
  corruption…) ne sont pas un motif d'exclusion.
  - **Critères automatiques** (`EditorialLine`, détail et mesures dans
    `plans/done/editorial-line.md`) : condamné ou poursuivi (`P1399`, `P1595`) pour
    crime de guerre, crime contre l'humanité, génocide, terrorisme, crime sexuel,
    pédocriminalité, traite ou meurtre ; dictateur (`P106`) ; membre d'un parti
    d'extrême droite (alignement `P1387` ou idéologie `P1142`) ; chef d'État ou de
    gouvernement d'un État classé « autoritaire » par l'indice de démocratie EIU 2020,
    ou d'un État autoritaire disparu (URSS, RDA, Yougoslavie, Zaïre, Irak baasiste).
    Volontairement larges : une exclusion de trop ne coûte rien au jeu (Gorbatchev ou
    Aung San Suu Kyi sont exclus aussi).
  - **Des critères, jamais une liste de noms** : le dépôt est public, et accoler un nom
    à un crime n'est pas à nous de le publier. Le résultat (des exclusions) vit en base.
  - Pas « condamné pour » en général : `P1399` signale Alan Turing (indécence) ou une
    fraude fiscale. Seuls les crimes listés comptent, sous-classes comprises.
  - Chaque vague charge la liste (~45 000 QID, < 10 s) et écarte ces personnes avant
    tout appel à Commons (`EDITORIAL_LINE`), même avec `--qid`.
    `--apply-editorial-line [--dry-run]` exclut celles déjà en base (motif
    `EDITORIAL_LINE`, leurs photos sont effacées, même approuvées).
  - **Restent manuels**, avec le bouton d'exclusion de la page de curation (motif
    `MANUAL`) : les dirigeants d'un pays actuellement en guerre (aucune source stable),
    et ce que Wikidata ignore (accusations graves, violences de masse sans
    condamnation). Le rappel est affiché sur la page de curation.
  - `people.exclusion_reason` (V10) distingue `MANUAL` (dont les demandes de retrait)
    d'`EDITORIAL_LINE` : les exclusions automatiques peuvent être revues si les
    critères changent, sans jamais toucher à une demande de retrait.
  - **RGPD** : les critères touchent à des opinions politiques (art. 9) et à des
    condamnations (art. 10). Les informations sont publiques et ne servent qu'à ne rien
    montrer de ces personnes ; rien n'en est conservé, seul le motif générique
    `EDITORIAL_LINE` (sans le critère) l'est. La section 8 de `/privacy` (EN et FR) le
    dit depuis le 2026-10-28. Les logs d'une vraie exclusion ne listent pas les QID
    (seulement en `--dry-run`, pour relecture).
- **Demande de retrait** : depuis la page de curation, recherche par nom (insensible
  aux accents, extension `unaccent`) ou par QID, puis « Exclure ». Les photos sont
  effacées par le trigger V4 et l'exclusion est confirmée à la personne sous un mois.
  - La ligne `people` d'une personne exclue (nom, naissance, QID, `excluded`) est
    conservée **tant que le jeu existe**, pour empêcher toute ré-ingestion ; elle est
    supprimée si le jeu s'arrête.
  - Pour une personne **pas encore ingérée**, noter le QID et l'exclure après
    l'ingestion. Une liste de blocage préalable sera ajoutée si le cas se présente.
- **Cible en photos** (décision du 2026-10-27) : au moins **5 000 photos jouables**,
  et une même photo pas rejouée avant 3 ans (règle du tirage quotidien, Semaine 3 :
  à 3 photos par jour, 3 ans en consomment 3 285). La cible de la collecte
  (`--limit`, `app.people.ingest.target-photos`, 5 000 par défaut) compte les photos
  **approuvées attendues** (décision du 2026-10-28) : approuvées, plus en attente au
  taux d'approbation. Le taux est observé dès 300 photos revues, supposé avant
  (`app.people.ingest.expected-approval-rate`, 0,4). Une case à qui il manque 100 photos
  approuvées en collecte donc ~250 en une vague, au lieu de converger par relances
  successives. Le but est le bon ordre de grandeur par case, pas la photo près.
- **Pool équilibré** (décision du 2026-10-26) : autant de photos de femmes que
  d'hommes, et autant par continent de nationalité.
  - 18 cases : 6 continents (Afrique, Amérique du Nord, Amérique du Sud, Asie, Europe,
    Océanie) × femmes, hommes, autre. La cible en photos est partagée entre les 12
    cases femmes et hommes (≈ 417 photos chacune pour 5 000). Les cases « autre » (non binaire,
    genderfluid, intersexe, valeurs contradictoires…) reçoivent leur poids réel sur
    Wikidata, ~0,11 % (décision du 2026-10-27), arrondi au-dessus : 1 photo par
    continent pour 5 000. Les femmes trans comptent comme femmes, les hommes trans comme
    hommes.
  - Chaque case suit son propre classement QLever, filtré par `P21` et par `P27` →
    `P30`, et les cases avancent à tour de rôle, une personne à la fois. Une case dont
    le classement s'épuise reste sous son quota, et le rapport le dit.
  - **Continent** : celui des pays de nationalité (`P27`, puis `P30` du pays).
    Eurasie et sous-continent indien comptent comme Asie, Amérique centrale comme
    Amérique du Nord, « Insular Oceania » et continent australien comme Océanie.
    Plusieurs continents : le moins représenté, dans l'ordre fixe Océanie, Afrique,
    Amérique du Sud, Asie, Amérique du Nord, Europe. Un Russe compte donc pour l'Asie.
  - Les photos déjà en base (de personnes non exclues) comptent dans leur case, au taux
    d'approbation si elles sont en attente : une relance reprend là où l'ingestion
    s'était arrêtée.
- **Vagues ciblées** (décision du 2026-10-28) : après la première passe en prod, pleine
  de footballeurs et pauvre en plus de 74 ans, une vague peut viser ce qui manque et
  écarter ce qui revient trop. Options de ligne de commande, pas de propriétés : le
  ciblage change d'une vague à l'autre.
  - `--exclude-occupations` / `--only-occupations` (QID de `P106`) : filtre dans la
    requête de classement QLever. Une personne écartée n'est jamais examinée. Toute
    personne ayant eu la profession est écartée, même avec une autre carrière :
    Wikidata ne dit pas laquelle la photo illustre. Les QID sont validés avant d'entrer
    dans le texte SPARQL.
  - `--cells` : ne parcourt que ces cases.
  - `--age-bands` : ne garde que les photos de ces tranches (`AGE_BAND_NOT_WANTED` sinon),
    et ne classe que les personnes assez âgées pour en avoir (année de naissance
    maximale dans la requête).
  - Le rapport compte les professions des personnes gardées (top 15, en QID) : cinéma
    et politique ne sont pas écartés, un tapis rouge ou une tribune datant peu une
    photo, contrairement à un maillot. À revoir sur les chiffres.
  - `--analyze` lit la base seule : par case et par tranche, approuvées / en attente /
    rejetées et approuvées attendues, taux d'approbation, et la prochaine vague à
    lancer (les cases d'abord, puis les tranches sous la moitié de la tranche
    médiane, à hisser vers la médiane).
- **Commande** : profil Spring `ingest` (`IngestPeopleRunner`), idempotente : une
  relance n'ajoute que ce qui manque.
  - `--limit=<n>` : cible de l'ingestion équilibrée, en photos approuvées attendues ;
  - `--analyze` : bilan du pool et prochaine vague, sans appel réseau ;
  - options de ciblage, voir « Vagues ciblées » ;
  - `--qid=<QID>` : une seule personne ;
  - `--sample=<n> [--offset=<rang>]` : n personnes du classement global à partir d'un
    rang, pour mesurer le rendement d'une partie de la liste ;
  - `--dry-run` : toutes les règles, téléchargements compris, sans lire ni écrire la
    base.

**Raison** : Wikidata et Commons forment un écosystème gratuit, structuré et à
couverture mondiale. Le reste découle de l'exigence d'un âge exact, et de l'équilibre
voulu :
- **Pourquoi équilibrer** : le classement brut par notoriété donne environ 74 %
  d'hommes, et une majorité d'Européens et de Nord-Américains.
- **Pourquoi le continent de nationalité et pas l'origine** : l'origine ethnique
  (`P172`) n'est renseignée que pour ~1 % des personnes, et c'est une donnée sensible
  (art. 9 RGPD). Limite assumée : la nationalité n'est pas l'origine.
- **Pourquoi ces règles de date** : le spike du 2026-10-26 sur 400 personnes
  (`plans/done/people-sourcing.md`) a montré qu'une date déclarée seule se trompe sur
  les photos anciennes numérisées. L'EXIF strict garde tout de même ~30 % des
  personnes, soit des dizaines de milliers.
- **Pourquoi la curation** : la revue visuelle de 46 photos retenues automatiquement en
  a trouvé ~43 % inutilisables (groupes, foules, scènes vues de loin, personne du
  tout). Les fichiers `depicts` ne listent que le sujet voulu par le contributeur.
  L'exigence produit est **une seule personne, cadrée au minimum du visage jusqu'au
  corps entier**. La curation manuelle a été préférée à la détection de visages
  locale et à la vision par LLM.

**Données personnelles** :
- Nom, dates de naissance et de décès, photos, genre et continent de nationalité de
  personnalités publiques, collectés indirectement (art. 14 RGPD). Genre et continent
  servent uniquement à l'équilibre du pool. Aucune transidentité n'est stockée : une
  femme trans est enregistrée `FEMALE`, comme les autres femmes.
- Base légale : intérêt légitime. Information donnée dans `/privacy` (section 8).
- `people.excluded` traite les demandes de retrait. Poser l'exclusion **supprime
  physiquement** les photos et images de la personne, par un trigger Postgres déclaré
  dans V4 : ça marche quel que soit le chemin (SQL à la main, page de curation). La
  ligne `people` reste, pour que l'ingestion ne réajoute jamais la personne.
- Les logs d'ingestion ne contiennent que des QID, jamais de nom ni de titre de fichier
  (qui contient souvent le nom). Une personne retirée ne subsiste donc pas dans les
  logs.

**Limites connues** :
- QLever, service tiers, répond 429 quand une requête dépasse son délai. Une ingestion
  peut donc s'arrêter ; on la relance (idempotente).
- **Droit à l'image des personnes vivantes : risque accepté** (décision du
  2026-10-26). Les licences libres de Commons couvrent le droit d'auteur, pas le droit
  à l'image (art. 9 du Code civil ; régimes différents à l'étranger), et un jeu n'est
  pas l'usage « information » que la jurisprudence tolère pour les personnalités
  publiques. Les personnes vivantes sont pourtant voulues, et même prioritaires : ce
  sont elles qui ont des photos récentes et de bonne qualité. Le risque est couvert
  par le retrait : toute demande mène à l'exclusion, qui efface les photos.
  - Options écartées, relevées par la revue `code-reviewer` : avis juridique,
    lancement limité aux personnes décédées. Écarter les fichiers marqués
    `{{Personality rights}}` sur Commons reste possible si les demandes de retrait
    se multiplient.
  - Aux États-Unis, le *right of publicity* (État par État, ex. Californie, New York)
    peut ouvrir droit à des dommages-intérêts même après retrait. À réexaminer si
    l'audience américaine devient significative.
- **Attribution CC BY / BY-SA** : l'auteur et la licence sont stockés, mais leur
  affichage à la révélation du round (Semaine 4) n'existe pas encore. Aucune photo ne
  doit être servie à des joueurs avant cet affichage.

### Sourcing des indices événementiels par catégorie
**Décision** (2026-10-30, détail et mesures dans le plan des indices) : un indice doit
permettre de calculer l'âge sur la photo. Son **année de référence est l'année du jour −
`age_at_photo`**, pas l'année de naissance, puisque l'année de la photo n'est pas montrée
au joueur.
- **Catégories** : Musique, Cinéma, Sport, Science et techno, Monde, Célébrités, et
  Littérature depuis le 2026-11-12 (V23 : parutions avec leur auteur, Nobel de
  littérature, Goncourt, Booker, Pulitzer de la fiction). Au moins
  5 indices par catégorie, par année et par public ; en dessous, la catégorie est masquée
  pour cette année.
- **Base pré-construite** : extraction en Python hors de l'application (`scripts/hints/`),
  sortie JSON versionnée, import idempotent par Java. Pas d'appel à un LLM, à Wikipédia
  ou à Wikidata depuis l'application.
- **Faits classés par notoriété** (2026-11-12, remplace les pages d'année Wikipédia,
  PR `feature/hint-notoriety`). Retours des joueurs : les indices n'étaient pas connus.
  - **Candidats Wikidata** par catégorie : éditions sportives avec vainqueur et pays hôte,
    Jeux olympiques, Ballon d'or ; événements, États et organisations internationales ;
    découvertes, Nobel scientifiques, lancements et premiers vols ; albums, chansons,
    Eurovision, Grammy ; films, Oscars, Palme d'or, César ; personnes ; romans, pièces,
    poèmes, nouvelles et BD, prix littéraires.
  - **Notoriété** : vues sur 12 mois de l'article du fait lui-même, dans la Wikipédia de
    la langue du joueur. Les premiers essais mesuraient la page la plus vue citée par une
    ligne d'une page d'année, ce qui classait un fait obscur au niveau d'un pays cité.
  - **Sélection** : par catégorie, année et langue, les 10 faits les plus vus au-dessus
    d'un plancher égal à 2 % de la médiane des meilleurs faits de la décennie, jamais sous
    500 vues (FR) ou 5 000 (EN). Un seuil unique laissait 18 années sur 84 jouables en
    français, les vues des faits anciens étant bien plus faibles.
  - **Complément du français** par les faits au-dessus du plancher anglais. Ce ne sont pas
    des traductions : le texte reprend le libellé français de Wikidata, exigé par la
    requête. Ils entrent donc approuvés, sans relecture.
  - **Un indice par langue** (`audience` FR ou EN) : un fait peut être retenu dans une
    langue et pas dans l'autre.
  - **Un fait sur plusieurs années** (une guerre de plus d'un an) donne deux indices, son
    début et sa fin ; ses moments marquants ont leur propre élément Wikidata.
  - **Faits très durs gardés**, au titre du devoir de mémoire (décision du 2026-11-12).
  - **Formulation** : gabarits par type (« Premier vol : X », « Création : X »,
    « … — vainqueur : Y, pays hôte : Z »). Le pays hôte est écarté quand il a été créé
    après le fait, Wikidata donnant le pays d'aujourd'hui. Un texte présent sur plusieurs
    années (« Tour de France — vainqueur : Eddy Merckx ») est écarté, il ne date rien.
  - **Mesure de référence** (extraction du 2026-11-12) : voir le tableau dans
    `plans/done/hint-notoriety.md`.
- **Formulation de Célébrités** (2026-11-12, V21) : « Sur la photo, cette personne a l'âge
  qu'a (ou qu'aurait) cette année X, desc. » plutôt que « Personnalité née cette année-là : X ». L'âge
  est exact, l'année de l'indice étant celle de la partie moins l'âge, et le joueur n'a
  plus de calcul à faire. « Ou qu'aurait » parce que l'extraction ne filtre pas les
  personnes décédées, nombreuses sur les années anciennes (revue du 2026-11-12). Limite
  acceptée : une série créée en fin d'année et jouée en janvier décale d'un an, puisque
  son année de référence est celle de sa création.
- **Relance annuelle de Célébrités** : aucun mineur n'est cité, donc l'extraction se
  relance à la main vers mars pour ajouter les personnes qui viennent d'avoir 18 ans. Les
  indices étant rangés par année de naissance, les autres n'ont pas à être mis à jour.
  Pas de job automatique : il écrirait en prod et devrait être surveillé pour ~2 % des
  photos (estimation) et une catégorie sur six. Un oubli ne fait que masquer la catégorie
  sur les photos de 18 ans.
- **Relecture** : aucun texte n'est traduit, aucun indice n'attend donc de relecture. La
  file de `/admin/hints` reste pour une source future qui en aurait besoin.
- **Signalement par les joueurs** : c'est le filet de sécurité de ces indices. Depuis
  l'écran de résultat, sur le modèle des photos, sans retrait automatique.
- **Ligne éditoriale** : Célébrités applique les critères de `EditorialLine.java`, Cinéma
  écarte les films de propagande. Une personne vivante n'est pas nommée à côté d'un crime :
  ces faits sont écartés à la main dans `LEFT_OUT` (`extract_notoriety.py`), par élément
  Wikidata.
- **Stockage** (table `hints`, V11) : catégorie, année, public (`FR`, `EN`, `ALL`), textes,
  source, `subject_qid`, `dedupe_key` unique et statut de relecture.
- **Personne nommée par un indice** (`subject_qid`, V24, 2026-11-12) : obligatoire en
  Célébrités, renseignée ailleurs dès qu'un indice nomme une personne (lauréat, auteur,
  interprète, vainqueur). Le jeu ne propose jamais un indice qui nomme la personne de la
  photo (`findEligible`) : « Parution : Harry Potter — J. K. Rowling » tombait sinon
  l'année de ses 32 ans sur sa propre photo. L'import complète ce champ sur un indice déjà
  importé qui ne l'avait pas, seule exception à « jamais touché » (ni texte ni décision).
  L'import (profil `import-hints`) n'ajoute que les clés inconnues : il n'écrase jamais une
  décision ni un texte corrigé. Tout nombre de 1900 à 2099 est refusé dans un texte, il
  donnerait la réponse, de même que l'année de l'indice écrite en deux chiffres (« Mai 68 »,
  « Windows 95 », « B-52 » en 1952 ; 2026-11-12, `HintText.containsItsYear`). Seule l'année
  de l'indice : un autre nombre à deux chiffres (« 12 Years a Slave ») reste permis.
- **Retrait d'une extraction** (V22, `retired_at`) : avec `app.hints.import.retire-absent`,
  l'import retire les clés absentes des fichiers, dans la même transaction que l'ajout des
  nouvelles, pour qu'aucune catégorie ne reste vide entre les deux. Un indice retiré n'est
  plus proposé, mais reste affiché là où il a déjà été montré, et sa décision de relecture
  est conservée. `REJECTED` ne convenait pas : le jeu ne montre que les indices
  `APPROVED`, l'historique et une manche en cours auraient perdu leurs indices. Retirer un
  indice précis (demande d'une personne citée) passe toujours par `REJECTED`.

**Raison** : Wikidata reste l'écosystème déjà retenu pour les personnes, et les faits
passés ne changent pas : un travail ponctuel, dont la sortie est contrôlée, suffit. Les
vues mesurent ce que les joueurs connaissent mieux que le nombre de langues d'un article.

**Limites connues** :
- Sans relecture, une erreur de Wikidata peut atteindre un joueur avant d'être signalée.
- Les vues sont celles d'une fenêtre de 12 mois : une actualité peut gonfler un fait
  ancien. Le plafond de 10 par an et le classement par décennie en limitent l'effet.
- Sport avant 1950 et Musique avant 1955 restent sous 5 indices la plupart des années,
  faute de faits connus dans Wikidata : ces catégories sont masquées ces années-là.
- Les textes viennent de Wikidata (CC0). Les anciens indices tirés de Wikipédia (CC BY-SA),
  encore affichés dans les manches passées, gardent leur attribution : l'écran de
  résultat déduit la licence de `sourceUrl`.

### Anti-triche sur l'image
**Décision** : les images ne sont jamais servies depuis l'URL Wikimedia Commons brute.
L'endpoint public `GET /api/images/{publicId}` sert une **copie ré-encodée** par un
identifiant opaque (UUID aléatoire, distinct de la clé primaire) :
- **ré-encodage à l'ingestion** : un JPEG neuf, écrit sans aucune métadonnée (EXIF,
  XMP, IPTC, commentaires). Les octets changent, donc l'image ne se retrouve pas sur
  Commons par son empreinte ;
- **rien ne sort de la source** : la réponse ne contient ni nom de fichier, ni
  `Content-Disposition`, ni ETag dérivé de Commons, ni URL source. Le nom de fichier,
  le SHA-1 Commons et l'URL source restent en base pour l'attribution à la révélation
  du round ;
- **un seul 404 pour tous les refus** : id mal formé, inconnu, interne, photo non
  approuvée, personne exclue. L'endpoint ne permet pas de sonder le pool ;
- **aucun appel sortant pendant une requête de jeu** : règle ArchUnit, `RestClient`
  n'est utilisable que dans l'ingestion et `config`.

**Raison** : une URL Commons `Claude_Francois_1976.jpg`, une recherche par empreinte
ou un EXIF « Claude François au Palais des sports » révèleraient l'identité, donc
l'âge, en quelques secondes. Ce serait contourner totalement le jeu.

**Limites connues** :
- La recherche d'image inversée (Google Lens…) reste possible sur les pixels eux-mêmes.
- Une fois une partie terminée, le couple `publicId` → réponse peut circuler. Le tirage
  quotidien donne donc un nouveau `publicId` à chaque photo tirée : l'ancien couple ne
  désigne plus rien quand la photo revient, au moins 3 ans plus tard. Un historique de
  parties ne devra pas servir une ancienne image par son `publicId` actuel, sinon il
  relierait l'id du jour à une réponse déjà connue.

### Tirage quotidien
**Décision** (2026-11-02) : chaque jour à minuit UTC, 3 photos (`app.game.daily.rounds`)
sont tirées dans `daily_rounds` (V12), les mêmes pour tous les joueurs.
- **Hasard uniforme sur les photos approuvées**, sans pondération par âge. Une règle
  sur les âges renseignerait le joueur (« la première a 22 ans, donc les autres non »).
  Une personne qui a 3 photos a 3 fois plus de chances d'être tirée.
- **Jamais deux personnes de la même case** (continent × genre) le même jour, ce qui
  exclut aussi deux photos d'une même personne. Un genre ou un continent inconnu compte
  comme une case à part.
- **Pas de rejeu d'une photo avant 3 ans** (`app.game.daily.replay-years`), borne
  incluse. Il n'y a pas de délai entre deux apparitions d'une même personne : on devine
  l'âge sur une photo donnée, et connaître la personne n'aide pas.
- **Pool trop petit** : la règle des cases tient, celle des 3 ans cède, en commençant
  par les photos jouées il y a le plus longtemps (`log.warn`). Sans assez de cases, le
  jour a moins de manches (`log.error`). Un tirage vide n'est pas enregistré et se
  retente au prochain appel.
- **Paresseux et idempotent** : `DrawDailyGameHandler.drawIfMissing(date)` renvoie la
  partie si elle existe, sinon la tire. Il est appelé à 00:00:05 UTC et au démarrage
  (rattrapage après une coupure à minuit), et par `GET /api/game/today` si le tirage a
  échoué. Tout cela est coupé si `app.game.daily.draw-enabled` est faux (tests,
  commandes ponctuelles) ; il est vrai par défaut depuis que le jeu est jouable. Deux
  tirages concurrents : la clé primaire
  `(game_date, position)` refuse le second, qui relit le premier.
- **Nouveau `publicId`** pour chaque photo tirée (voir « Anti-triche sur l'image »).
- **Photo retirée pendant son jour** : une manche est neutralisée pour tous si sa photo
  n'est plus `APPROVED` (rejet par un admin) ou a disparu (personne exclue,
  `photo_id` en `ON DELETE SET NULL`). Le score du jour se calcule alors sur les manches
  restantes. Un simple signalement ne neutralise rien, sinon un script pourrait vider
  les parties. La neutralisation est **calculée à la lecture**
  (`DailyRoundRepository.findStatesOf`), pas stockée : `people` n'a pas à prévenir
  `game`, et un rejet annulé rétablit la manche. Pour les parties passées, l'historique
  ne réécrit pas ce qui a été joué (voir « Historique et profil »).
- **Pas de cache Redis** : la table évite déjà de recalculer, et lire 3 lignes par clé
  primaire ne coûte rien. Un cache devrait être invalidé par `people` (sens interdit),
  ou expirer après un délai qui laisserait une photo rejetée en jeu. La lecture lourde
  est celle des images : elle passe par le cache HTTP du navigateur (`Cache-Control:
  max-age=86400, public` sur `/api/images/{publicId}`, l'id changeant à chaque tirage).
  Un jour suffit, une partie du jour ne se rejouant pas ; plus long, une photo rejetée
  après un signalement resterait dans le cache des navigateurs.

### Déroulé d'une manche
**Décision** (2026-11-12, remplace celle du 2026-11-02, PR `feature/better-hints-gameplay`) :
un 1er essai, puis le jeu donne **le sens et une tranche d'écart** : de 1 à 5 ans
(`SLIGHTLY`), de 6 à 15 (`CLEARLY`) ou 16 et plus (`FAR`). Le joueur choisit ensuite
**3 catégories d'indice** (`app.game.hints.per-round`, moins quand moins sont offertes),
puis fait un 2e essai, que l'écran garde dans la plage laissée par le sens et la tranche
(`RoundView.range`, bornée à 18-100). Saisie de 18 à 100 ans.

**Raison** : les retours des joueurs disaient que les indices n'aidaient pas. Un seul fait,
souvent obscur et daté à quelques années près, ne resserrait rien après un simple
« plus vieux ».
- **Pourquoi le chaud/froid revient** : le 2026-11-02, on l'avait écarté parce qu'une
  distance **sans** le sens laisse deux zones (dire 40 à 15-20 ans de la réponse laisse
  20-25 ou 55-60), et que la photo tranche presque toujours entre les deux. Avec le sens
  en plus, il ne reste qu'un intervalle de 5 ou 10 ans, et les indices servent à choisir
  dedans.
- **Un mauvais 1er essai reste puni** par ses propres points : 16 ans d'écart valent
  environ 100 points sur 500.
- **Ce qui est calculé, ce qui est stocké** : la tranche et la plage sont recalculées à
  chaque lecture, à partir du 1er essai et de l'âge, comme le sens, et rien n'est stocké.
  Les manches en cours au moment du déploiement sont donc passées d'elles-mêmes au nouveau
  système.
- **La plage n'est pas imposée par le serveur** : un 2e essai hors de la plage reste
  accepté de 18 à 100 (décision de l'utilisateur), puisque le joueur ne pénaliserait que
  lui-même.
- **Toujours pas de bonus de proximité** (« brûlant ») : la tranche la plus fine fait déjà
  5 ans. Aux bords de 18-100, le bornage peut réduire la plage davantage (essai 19,
  réponse 18 : plage [18, 18]). Ce n'est pas une fuite : le joueur le déduit lui-même du
  sens, de la tranche et des bornes publiques.
- **Chaque carte de piste s'explique** (2026-11-13, PR `feature/lead-help`) : une bulle
  donne le genre d'indice, la règle et un exemple dont l'écart est calculé depuis l'année
  de la partie (jamais écrit en dur). Elle s'ouvre selon la façon d'interagir
  (`pointerType`), pas selon la taille d'écran : survol de 400 ms à la souris, tap sur ⓘ
  au doigt, arrivée par Tab au clavier (pas sur `:focus-visible`, que le focus
  automatique de la 1re carte déclencherait à chaque manche). Une seule ouverte à la
  fois ; la carte la référence par `aria-describedby`, donc un lecteur d'écran l'entend
  sans l'ouvrir.

### Système de points
**Décision** (2026-11-02, remplace « l'essentiel des points sur le 2e essai ») : chaque
essai vaut jusqu'à **500 points** selon son écart à l'âge réel. Une manche vaut la somme
de ses deux essais (/1 000) et la journée /3 000. Un 1er essai exact termine la manche
à 1 000. Les points du 1er essai ne s'affichent qu'au résultat : les montrer avant le
2e essai redonnerait l'écart.

**Formule** : piste `500 × e^(−écart/10)` (0 an → 500, 5 ans → 303, 10 ans → 184,
20 ans → 68), à régler par playtest.

**Raison** : le 1er essai compte vraiment, ce qui pousse à le tenter sérieusement, et
le 2e rapporte en général plus, grâce au sens et à l'indice. Le « bonus premier coup »
vient de la somme elle-même, sans règle à part.

### État d'une partie
**Décision** (2026-11-02) : l'état de chaque manche d'un joueur est en base, dans
`game_plays` (V13), une ligne par joueur et par manche, créée au 1er essai. Jamais dans
le navigateur : l'âge n'y arrive qu'avec le résultat, et le serveur fait foi pour les
points.
- **Le joueur** est un compte (`user_id`, avec le JWT, facultatif sur `/api/game/**`)
  ou un invité (`guest_id`, l'UUID aléatoire du cookie `ageguessr_guest` : httpOnly,
  `SameSite=Lax`, `Path=/api/game`, 30 jours, posé au 1er essai). Le compte gagne s'il y
  a les deux ; les parties invitées ne sont pas fusionnées dans le compte à la connexion.
  Un jeton envoyé mais invalide donne un 401, pas une partie invitée : le front
  rafraîchit le jeton et le compte garde sa partie.
- **Étapes** : `NOT_STARTED → AWAITING_HINT → AWAITING_SECOND_GUESS → FINISHED`,
  dérivées des colonnes. Un 1er essai exact termine la manche. Le choix des pistes est
  obligatoire, sauf si aucune catégorie n'a au moins `Hint.MINIMUM_PER_CATEGORY` (5)
  indices éligibles (année de référence, public `ALL` + langue, jamais la personne à
  deviner) : le joueur passe alors directement au 2e essai.
- **Pistes** (2026-11-12) : le joueur choisit exactement `min(N, catégories offertes)`
  catégories distinctes (`app.game.hints.per-round`, N = 3), sinon `400 HINT_COUNT`.
- **Même indice pour tous** pour une même (jour, manche, catégorie, langue) : index
  tiré d'un hash stable, quelles que soient les autres catégories choisies. Les
  catégories et les ids sont gardés côte à côte dans `game_plays.hint_categories` et
  `hint_ids` (tableaux PostgreSQL, V20, dans l'ordre de `HintCategory`). Un tableau ne
  porte pas de clé étrangère : un indice n'est jamais supprimé (`REJECTED`), et le jeu
  ignore un id inconnu ou rejeté. Une table fille aurait demandé une table par entité
  (`RoundProgress` est embarqué dans deux tables) pour une liste de 6 éléments au plus.
- **Points stockés à l'essai** : régler la formule ne réécrit pas les parties passées.
- **Minuit** : seule la date du jour se joue (`409 GAME_CLOSED`). Une manche commencée
  à 23:59 se perd ; le front recharge la nouvelle partie.
- **Concurrence** (double clic, deux onglets) : les uniques de V13 et un verrou
  optimiste (`@Version`) donnent un `409 WRONG_STEP`, jamais un 500 ni une écriture
  écrasée.
- **Conservation** : parties invitées purgées 7 jours après le jour joué
  (`app.game.guest.retention-days`, tâche quotidienne). Celles d'un compte partent avec
  lui (`ON DELETE CASCADE`). Les deux sont décrites sur `/privacy`.
- **Limite connue**, comme Wordle : rien n'empêche de rejouer en navigation privée. Une
  limite par IP (`app.rate-limit.game.*`, 60 POST par minute) empêche seulement de créer
  des milliers de parties invitées.

**Endpoints** (publics, JWT facultatif) : `GET /api/game/today`, puis `POST
/api/game/{date}/rounds/{position}/first-guess` (`{guess, language}`), `…/hint`
(`{categories}`) et `…/second-guess` (`{guess}`). Tous renvoient un `RoundView`, la seule
forme sous laquelle une manche sort de l'API. **Rien de la réponse avant la fin** : ni
âge, ni identité, ni crédit (la page Commons nomme la personne), ni id interne de
photo, ni **année des indices** (année du jour − âge). Pendant la manche, un indice ne
sort qu'avec sa catégorie et son texte. La tranche d'écart et la plage (`gap`, `range`)
donnent un intervalle de 5 ans au moins, sauf aux bords de 18-100 (voir « Déroulé d'une
manche »). `GameFunctionalTest` vérifie le JSON brut à
chaque étape.

### Réinitialisation quotidienne
**Décision** : minuit UTC, même partie (mêmes personnes) pour tous les joueurs ce
jour-là.

**Raison** : cohérent avec le genre "daily puzzle" (Wordle, GeoGuessr Daily Challenge) —
simplifie aussi la comparaison de scores entre amis (même partie = comparaison
équitable).

### Enquête libre et Défi (séries hors partie du jour)
**Décision** (2026-11-03, PR `feature/free-investigation`) : en plus de la partie du
jour, un joueur joue des séries de 5, 10 ou 20 photos, avec les mêmes règles. À la fin
d'une série, son créateur peut la partager en défi : d'autres joueurs la rejoignent par
un code et jouent les mêmes photos avec les mêmes indices, une fois chacun, puis voient
un classement.

- **Pas de seed** : une série est enregistrée à sa création (V15 : `free_series`,
  `free_series_rounds`, `free_series_players`, `free_series_plays`). Un seed appliqué au
  pool donnerait d'autres photos dès qu'une photo est approuvée ou retirée en curation.
- **Mêmes règles, écrites une fois** : `RoundProgress` (`@Embeddable`) porte une manche
  (étapes, points, indice, 2e essai). `GamePlay` et `FreeSeriesPlay` l'embarquent avec
  les mêmes colonnes. `PlayedRound` l'expose aux vues et aux handlers.
- **Mêmes indices pour tous** : `GameRound.hintKey()` vaut `date|position` pour la
  partie du jour (la clé d'avant, donc aucun indice n'a changé), et
  `series:<id>|position` pour une série. Les indices visent l'année de création de la
  série moins l'âge.
- **Tirage** (`SeriesDraw`) :
  - tout le pool approuvé, sauf les photos de la partie du jour en cours. Risque
    accepté par l'utilisateur : un joueur assidu peut reconnaître une photo dans une
    future partie du jour ;
  - les photos jamais vues par le joueur d'abord ;
  - une seule photo par personne ;
  - rotation des cases continent × genre, puisque 20 photos dépassent le nombre de
    cases.
- **Accès** : une série n'est visible et jouable que par ses joueurs. Pour tous les
  autres, c'est le même 404 qu'une série inexistante. Un défi se lit par son code (8
  caractères en base32 Crockford, `SecureRandom`). Le classement n'est montré qu'à qui a
  fini, et ne contient que le nom affiché, le score et le rang.
- **Nom dans un défi** :
  - pour un compte, son nom d'utilisateur, lu par une jointure SQL sur `users` (le
    `game` du backend n'importe pas `auth`) ;
  - pour un invité, un pseudo au format d'un nom d'utilisateur, propre à ce défi et
    marqué « invité » dans le classement.
- **Conservation** (`PurgeSeries`, tous les jours) : une série non partagée part
  7 jours après sa création, un défi 30 jours après son partage. Un code expiré donne un
  404, comme un code inconnu.
- **Limite de débit** `series` : 30 par heure et par IP sur la création de séries et le
  `join`. Chaque série révèle l'âge de ses photos, et un script ne doit pas moissonner
  le pool.
- **Front** :
  - `RoundsScreen<T>` (classe de base) est partagée par la partie du jour (`/`), l'Enquête
    libre (`/free`) et le défi (`/challenge/:code`) ;
  - le lien partagé est sans préfixe de langue, pour que Caddy envoie chaque ami vers la
    sienne ;
  - exception front **`game → auth`** : le défi lit `Auth.isAuthenticated` pour savoir
    s'il faut demander un pseudo (même raison que `layout → auth`).

**Raison** : le jeu quotidien ne se joue qu'une fois par jour. Ce mode donne de quoi
jouer plus, et de quoi comparer ses scores entre amis sans attendre la feature Amis.

### Historique et profil
**Décision** (2026-11-09, PR `feature/game-history`, plan `plans/done/game-history.md`) :
un compte connecté voit sur `/profile` ses statistiques et ses parties du jour passées,
manche par manche (`GET /api/game/history`, 30 jours par page).

- **Privé** : seul le propriétaire le voit (`authGuard`, endpoint authentifié). Les amis
  ne voient pas l'historique ni le profil (décision du 2026-11-10) : seulement les points
  du jour, une fois leur propre partie finie, et la moyenne du classement (« Système
  d'amis »).
- **Limité** : `GET /api/game/history` partage le compteur des essais (60 par minute et
  par IP), seule lecture du jeu à charger tout l'historique d'un compte.
- **Le passé n'est pas réécrit**, comme les points stockés à l'essai : une manche
  neutralisée après avoir été terminée garde ses points et sa place dans le maximum du
  jour, mais son nom n'est plus affiché. Une manche neutralisée avant d'être terminée
  sort du maximum, comme le jour même. Un jour est complet quand chaque manche encore
  jouable est terminée.
- **Série** : jours UTC consécutifs complets (ceux du tirage). La série en cours compte
  jusqu'à aujourd'hui si la partie du jour est complète, sinon jusqu'à hier.
- **Rien de la réponse avant la fin** : le nom et l'âge d'une manche ne sortent que si
  elle est terminée et encore jouable, sinon l'historique donnerait la réponse de la
  manche du jour en cours. **Jamais de photo ni de `publicId`** (voir « Limites
  connues »).
- **Calculs en Java** (`PlayerHistory`, pur, partagé avec les amis depuis 2026-11-10) :
  toutes les manches du compte sont lues
  (3 par jour au plus), puis les jours, séries et statistiques sont calculés en mémoire.
  Testable unitairement et par mutation. À revoir si un compte dépasse plusieurs milliers
  de jours.
- **Parties invité rattachées à la connexion** : le frontend appelle
  `POST /api/game/guest-plays/claim` juste après une connexion réussie. Sous `/api/game`
  parce que le cookie invité a `Path=/api/game` et n'atteint jamais `/api/auth/login`.
  Une manche déjà jouée par le compte l'emporte, la ligne invité est supprimée ; les
  autres passent au compte, puis le cookie invité est effacé. Idempotent, jamais fait au
  passage d'une lecture. Les séries de l'enquête libre ne sont pas rattachées (elles
  expirent en 7 jours, et un défi garde le pseudo de l'invité).
- **Exception front `auth → game`** : la page de connexion appelle
  `GameApi.claimGuestPlays()` après le login. Le rattachement doit suivre la connexion
  elle-même, pas une page de jeu ouverte ensuite (le joueur peut aller directement sur
  son profil).

### Signalement des photos et des indices par les joueurs
**Décision** (2026-11-03, PR `feature/player-reports`) : depuis le résultat d'une manche
terminée (partie du jour ou série), un joueur signale la photo ou l'indice, avec un motif
d'une liste fermée. L'admin traite deux files triées par nombre de signalements ouverts.

- **On signale une manche, pas un id** :
  `POST /api/game/{date}/rounds/{position}/report` et
  `/api/game/series/{id}/rounds/{position}/report`. Le joueur n'a jamais l'id interne de
  la photo ni de l'indice (`RoundView`) : le serveur les retrouve depuis *sa* manche. Il
  faut donc l'avoir terminée (identité connue), et un script ne vise que des photos
  qu'il a jouées.
- **Anonymat** : `photo_reports` / `hint_reports` (V16) ne gardent que la cible, le motif,
  le statut et les dates. Le « un signalement par cible » est une marque sur la manche
  jouée (`RoundProgress.photoReported`, et depuis V20 `reportedHintCategories`, une fois
  par indice montré), déjà liée au joueur et purgée avec elle. Un second envoi ne change
  rien (204, idempotent). Un indice se désigne par sa catégorie (`hintCategory`),
  facultative quand la manche n'en a montré qu'un (manches d'avant V20, page chargée avant
  la release) ; sinon `400 HINT_CATEGORY_REQUIRED`.
- **Motifs** (enums de chaque feature) : photo `TOO_SMALL`, `NOT_IDENTIFIABLE`,
  `WRONG_PERSON`, `LOOKS_MINOR`, `DISTURBING_PERSON`, `OTHER` ; indice `WRONG_YEAR`,
  `FALSE`, `UNCLEAR`, `DISTURBING`, `OTHER`. Pas de texte libre : rien à modérer.
- **Aucun retrait automatique** : un script pourrait vider le pool. L'admin décide.
- **Alerte** (`ReportAlerts`) : un email à `ADMIN_EMAIL` quand les signalements ouverts
  d'une cible *atteignent* le seuil (`app.reports.alert-threshold`, 3), donc une fois par
  franchissement. Dès le premier pour une photo de la partie du jour, pour un premier
  `LOOKS_MINOR`, et pour un indice signalé depuis la partie du jour. Envoyé après le
  commit ; un échec d'envoi est loggé sans faire échouer le signalement. Sans adresse, un
  log WARN.
- **Traitement** : `OPEN`, puis `RESOLVED` (toute décision de `ReviewPhoto` /
  `ReviewHint`, correction de texte comprise) ou `DISMISSED` (« Keep »). Exclure la
  personne supprime ses photos, donc leurs signalements (`ON DELETE CASCADE`).
- **Rangement** : chaque table appartient à la feature de sa cible (`people/PhotoReport`,
  `hints/HintReport`), avec ses files et actions admin. `ReportStatus` et `ReportTally`
  (regroupement par cible et tri de la file) sont dans `shared/reports`, pour que
  `people` et `hints` les partagent sans dépendre l'une de l'autre. Le signalement par le
  joueur est un use case de `game`, seule feature qui relie une manche à sa photo et à
  son indice. Le badge « In today's game » de la file photo lit `daily_rounds` par une
  requête native, pour que `people` ne dépende pas de `game`.
- **Limite de débit** `report` : 20 par heure et par IP, à part des essais.

**Raison** : avec l'Enquête libre, un joueur voit tout le pool. Une photo de la mauvaise
personne, d'un mineur ou un indice faux doit pouvoir remonter vite, sans exposer de
donnée sur le joueur ni donner à un script le moyen de retirer des photos.

### Système d'amis
**Décision** (2026-11-10, PR `feature/friends`, plan `plans/done/friends.md`) : un compte
ajoute des amis par leur pseudo exact, voit ses amis et ses
demandes, compare les scores de la partie du jour une fois la sienne terminée, et un
classement par moyenne de points par partie.

- **Une table `friendships`** (V18) plutôt que deux : une demande et une amitié sont la
  même paire à deux statuts (`PENDING`, puis `ACCEPTED`). Un index unique sur
  `(LEAST, GREATEST)` des deux comptes garde une seule relation par paire, dans un sens
  comme dans l'autre, même sur deux clics simultanés. `ON DELETE CASCADE` des deux côtés :
  supprimer un compte supprime ses amitiés.
- **Pseudo exact, sans recherche partielle** : les comptes ne peuvent pas être listés.
  Un pseudo inconnu, un compte non vérifié et soi-même donnent le même 404.
- **Pas de lien d'invitation** : livré avec la feature (`friend_invites`, ajout en un
  clic), puis retiré le 2026-11-11 sur retour produit (V19 supprime la table) — la demande
  par pseudo, acceptée explicitement, reste le seul chemin. Un ancien lien
  `/friends/invite/:code` redirige vers `/friends`.
- **Rien d'automatique** : une demande croisée (B a déjà demandé A, A demande B) est
  refusée en 409 `INCOMING_REQUEST_EXISTS`, le frontend montre la demande à accepter.
  Refuser, annuler, retirer : la ligne est supprimée, l'autre n'est pas prévenu, et peut
  renvoyer une demande. Pas de liste de blocage pour l'instant.
- **Actions par id de relation**, jamais par id de compte : le frontend ne reçoit que
  l'id de la ligne `friendships` et le pseudo de l'autre, jamais son `users.id` ni son
  email. Une relation qui n'est pas la sienne répond 404, comme un id inconnu.
- **Pseudos lus en SQL natif sur `users`** (`FriendUsers`), comme le classement d'un
  défi : `friends` n'importe pas `auth`.
- **Front** : exception **`layout → friends`**, l'en-tête montre le nombre de demandes
  reçues (`FriendsApi.incomingCount`, lu une fois par session et à chaque lecture de la
  liste). Copier et partager le lien passent par `shared/share-target.ts`
  (`SHARE_TARGET`, sorti de `game/share.ts` pour servir aux deux features). Le
  récapitulatif du jour mène à `/friends` (exception `game → auth` déjà là pour le défi).
- **Comparaison du jour** (`GET /api/friends/today`) : `{locked: true}` et aucune donnée
  tant que la partie du jour de l'appelant n'est pas complète (les points d'un ami
  renseigneraient sur les manches qu'il reste à jouer). Ensuite, des points seulement :
  total, maximum et points par manche d'un ami qui a fini, « en cours » ou « pas joué »
  sinon. Jamais un essai, un nom ou un âge.
- **Classement** (`GET /api/friends/leaderboard`) : moyenne simple, sans minimum de
  parties (décision utilisateur) ; égalité départagée par le nombre de parties, puis même
  rang. Un joueur sans partie est en bas, sans rang.
- **Limites** (`app.friends.*`) : 200 amis, 50 demandes envoyées en attente. Compteur
  `friends` (30 par heure et par IP) sur l'envoi d'une demande et sur la lecture ou
  l'acceptation d'un code ; la comparaison et le classement, qui lisent toutes les
  manches d'un groupe, partagent le compteur des essais comme l'historique.
- **Pas de cache** : 201 comptes au plus, quelques milliers de manches en mémoire. Redis
  reste le stockage des limites de débit.

**Raison** : décision produit explicite, feature sociale à part entière et pas un simple
lien de partage éphémère.

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

**Mise à jour 2026-11-04** : ce besoin est arrivé avec la refonte visuelle. On ajoute
`@angular/cdk` (menus, dialogs, overlay, focus) et nos propres composants dans
`shared/ui`, sans librairie stylée (voir `design.md`, « Composants »).

### Internationalisation (i18n)
**Décision** (2026-11-02, PR `feature/i18n`) : tout le site joueur existe en anglais et
en français, avec l'i18n natif d'Angular (`@angular/localize`) et **une URL par
langue** : `/en/…` et `/fr/…`, une build par langue. L'administration reste en anglais
(sous `/en/admin/…` comme sous `/fr/admin/…`).
- **Catalogue** : langue source `en`, traduction dans `src/locale/messages.fr.xlf`
  (XLIFF, le format par défaut d'`ng extract-i18n`). Textes des templates marqués
  `i18n="@@zone.nom"` avec un **id explicite** : retoucher l'anglais ne fait pas perdre la
  traduction. Textes TS en `$localize`. `i18nMissingTranslation: error` : une traduction
  manquante casse la build, donc la CI (étape « Build (EN + FR) »).
- **Dev** : `pnpm start` sert `/en/` (port 4200), `pnpm start:fr` sert `/fr/` (port
  4201) : `ng serve` ne sert qu'une langue à la fois.
- **Prod (Caddy)** : `/en/*` et `/fr/*` servent chacune leur build. Toute autre URL hors
  `/api/*` redirige en 302 vers `/<langue><chemin>`, la langue venant du cookie
  `ageguessr_lang`, puis d'`Accept-Language`, sinon `en` (`?lang=fr` force `/fr/`). La
  redirection porte `Cache-Control: no-store` et `Vary: Cookie, Accept-Language` pour que
  Cloudflare ne serve pas la redirection d'un visiteur à tous. Les anciens liens (emails
  déjà envoyés, `/terms`) continuent de marcher. Vérifié par
  `scripts/check-edge-locales.sh`.
- **Langue du site** (`features/i18n/`) : `SiteLanguageService` lit `LOCALE_ID`. Le
  sélecteur de l'en-tête (avec `hreflang`) écrit le cookie `ageguessr_lang` (1 an,
  `SameSite=Lax`, lisible par le script : un choix de l'utilisateur, sans consentement
  requis) et recharge la même page dans l'autre préfixe.
- **Langue du compte** : `users.language` (V14, nullable : NULL = jamais choisie),
  envoyée à l'inscription et modifiée par `PUT /api/auth/me/language`. Elle gagne sur la
  page : après connexion ou restauration de session, le front recharge la page dans la
  langue du compte (et met le cookie à jour, d'où aucune boucle). Si l'enregistrement
  échoue au changement de langue, la page ne change pas et un message s'affiche.
- **Emails** dans la langue du compte (anglais si NULL), avec des liens préfixés
  (`<frontend-url>/fr/reset-password?token=…`) : `AccountEmailTexts`, `AccountLinks`.
- **Langue partagée** : `shared/i18n/Language` (`EN`, `FR`), utilisée par `auth` et
  `game` sans qu'elles dépendent l'une de l'autre. Le lien avec `HintAudience` est dans
  `HintPicker`.
  Côté front, même logique : les types et conversions (`SiteLanguage`, `ApiLanguage`,
  cookie) sont dans `shared/i18n/language.ts`, utilisés par `auth`, `game` et
  `features/i18n`.
- **Exception front `i18n ↔ auth`** : `SiteLanguageService` lit le compte (`Auth`) pour
  enregistrer la langue choisie, et l'inscription lit la langue de la page. Les deux
  features s'importent donc l'une l'autre. C'est accepté, comme `layout → auth` : il n'y
  a pas de règle d'architecture outillée côté front, et une interface intermédiaire
  ajouterait une abstraction pour un seul usage.
- **Langue des indices**, indépendante du site : sélecteur sur l'écran de jeu, retenu
  dans le stockage local (`ageguessr-hint-language`), par défaut la langue du site. Elle
  s'applique au prochain 1er essai : une manche commencée garde sa langue
  (`game_plays.language`).
- **Erreurs** : le front n'affiche jamais le `message` de l'API, seulement ses propres
  textes traduits, choisis par `errorCode`.

**Raison** : une URL par langue est indexable par langue (référencement) et ne demande
aucun chargement de traduction à l'exécution. Changer de langue recharge la page, ce
qui est acceptable pour un choix rare.

**Règle** : aucun texte visible par un joueur n'est écrit en dur dans une seule langue.
Tout nouveau texte joueur est marqué (`i18n` ou `$localize`) et traduit dans
`messages.fr.xlf` dans la même PR ; tout nouvel email existe dans les deux langues.

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

**Images buildées en CI, pas sur le VPS** : le job `publish-images` (push sur `main`)
builde `ageguessr-api` et `ageguessr-edge` sur le runner GitHub et les pousse sur GHCR
(`ghcr.io/maxime-lambert/...`), taguées par le SHA du commit. Le job `deploy` copie
uniquement `docker-compose.prod.yml` sur le VPS, écrit `IMAGE_TAG` dans le `.env`, puis
fait `pull` + `up -d`. Raison : le premier déploiement (2026-10-20) a échoué parce que
`ng build` était tué faute de mémoire sur le VPS (~4 Go au pic, dû au fichier unique de
12 Mo de `@lucide/angular` passé au linker Angular), en parallèle du build Maven, sur
une machine partagée avec un autre projet en prod. Builder ailleurs supprime ce risque
pour l'autre projet, rend le déploiement plus rapide, et permet un retour arrière en
relançant le workflow d'un commit précédent (même image, même SHA, re-téléchargée
depuis GHCR : les anciennes images locales sont supprimées à chaque déploiement). Le
`pull` a lieu avant la réécriture du `.env` : s'il échoue, `.env` garde l'`IMAGE_TAG`
du dernier déploiement réussi. Le jeton GitHub
transmis au VPS pour le `pull` est en lecture seule (`packages: read`) et expire à la
fin du job.

**IP du client à travers les proxys** : chaîne Cloudflare → passerelle Caddy du VPS →
Caddy edge → Spring. Le rate limiting de `/api/auth/*` partitionne par IP client, il
faut donc que Spring voie la vraie IP et qu'elle soit infalsifiable. Par défaut, chaque
Caddy remplace `X-Forwarded-For` par l'IP de la connexion entrante : tous les
utilisateurs partageraient alors un seul compteur (celui de l'IP de la passerelle), et
un seul client pourrait bloquer la connexion de tout le monde. Solution :
- la passerelle ne fait confiance qu'aux plages IP de Cloudflare (`trusted_proxies`) et
  lit l'IP client dans `CF-Connecting-IP` (Cloudflare écrase toujours cet en-tête) ;
  une connexion directe à l'IP du VPS garde son IP réelle ;
- elle transmet `X-Forwarded-For: {client_ip}` (une seule valeur, tout en-tête envoyé
  par le client est écrasé) ;
- l'edge fait confiance aux IP privées (seule la passerelle peut le joindre) et
  retransmet cette valeur telle quelle au backend ;
- Spring la lit via `ForwardedHeaderFilter` (`WebConfig`).
Les plages IP de Cloudflare sont copiées en dur dans le Caddyfile de la passerelle :
elles changent rarement, mais sont à revérifier de temps en temps
(https://www.cloudflare.com/ips/).

### Migrations en production
**Décision** : migrations Flyway appliquées automatiquement au démarrage du conteneur
backend (`spring.flyway.enabled=true` déjà actif par défaut) — acceptable pour une
instance unique à faible trafic.

### Variables d'environnement
- **Local** : fichier `.env` (gitignored)
- **Prod** : secrets GitHub Actions injectés dans le VPS au déploiement
- `ADMIN_EMAIL` (destinataire des alertes de signalement) : optionnel, une variable
  GitHub (`vars.ADMIN_EMAIL`) plutôt qu'un secret, à transmettre au `.env` du VPS par
  l'étape de déploiement de `ci.yml`
- Jamais de secrets dans le code ou dans un fichier versionné

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
`/terms` et `/privacy`. Chacune existe en anglais par défaut et en français avec
`?lang=fr`.
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
- **Liste des personnes** : humains (`P31 = Q5`) avec une image `P18`, par sitelinks
  décroissants (notoriété), interrogés sur **QLever** (`qlever.dev`). Le SPARQL public
  de Wikidata dépasse son délai sur ce classement.
  - Cinq petites requêtes par page de 500 : classement, naissances, décès, images,
    labels. Elles sont assemblées en Java, environ 7 s par page. Une requête unique en
    jointure dépassait la limite de 30 s de QLever.
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
  - `app.curation.allow-living-approval` (`false` par défaut) : le serveur refuse
    d'approuver une personne vivante tant que le droit à l'image n'est pas tranché.
- **Demande de retrait** : depuis la page de curation, recherche par nom (insensible
  aux accents, extension `unaccent`) ou par QID, puis « Exclure ». Les photos sont
  effacées par le trigger V4 et l'exclusion est confirmée à la personne sous un mois.
  - Pour une personne **pas encore ingérée**, noter le QID et l'exclure après
    l'ingestion. Une liste de blocage préalable sera ajoutée si le cas se présente.
- **Commande** : profil Spring `ingest` (`IngestPeopleRunner`, `--limit`, `--qid`),
  idempotente : une relance n'ajoute que ce qui manque.

**Raison** : Wikidata et Commons forment un écosystème gratuit, structuré et à
couverture mondiale. Le reste découle de l'exigence d'un âge exact :
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
- Nom, dates de naissance et de décès, et photos de personnalités publiques, collectés
  indirectement (art. 14 RGPD).
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
- **Droit à l'image des personnes vivantes, à trancher avant d'approuver la moindre
  photo d'une personne vivante.** Les licences libres de Commons couvrent le droit
  d'auteur, pas le droit à l'image (art. 9 du Code civil ; régimes différents à
  l'étranger). Un jeu n'est pas l'usage « information » que la jurisprudence tolère
  pour les personnalités publiques. Options relevées par la revue `code-reviewer` du
  2026-10-26 :
  - avis juridique ;
  - lancement limité aux personnes décédées (la date de décès est déjà connue) ;
  - écarter les fichiers marqués `{{Personality rights}}` sur Commons.
- **Ligne éditoriale à définir** : les personnes les plus connues incluent des figures
  comme des dictateurs ou des criminels (Adolf Hitler apparaît dans les 5 premières).
  La curation permet de les rejeter au cas par cas ; une politique explicite reste à
  décider.
- **Attribution CC BY / BY-SA** : l'auteur et la licence sont stockés, mais leur
  affichage à la révélation du round (Semaine 4) n'existe pas encore. Aucune photo ne
  doit être servie à des joueurs avant cet affichage.

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
- Le `publicId` est stable : une fois une partie terminée, le couple id → réponse peut
  circuler. La sélection quotidienne (Semaine 3) devra régénérer le `publicId` d'une
  photo resélectionnée.

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
- Jamais de secrets dans le code ou dans un fichier versionné

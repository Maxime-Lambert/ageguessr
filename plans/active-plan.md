# Curation manuelle des photos

Donner à un administrateur un outil pour valider une à une les photos ingérées. Une
photo n'entre dans le jeu que si elle montre **une seule personne, cadrée au minimum du
visage jusqu'au corps entier**. Cet outil sert aussi à traiter les demandes de retrait
(exclusion d'une personne, qui efface ses photos). ~43 % de rejets attendus, d'après
`plans/done/people-sourcing.md`.

## For Future Agents
Au fil du travail : cocher les cases `- [x]`. Quand une phase est finie, passer son
statut à `Complete` et écrire son **Phase Summary**. Lancer le **Verification Plan** de
la phase et noter le résultat avant de passer à la suivante. Ce fichier est la source de
vérité, pas la conversation.

## Contexte pour repartir à froid
- Branche : `feature/photo-curation`, créée depuis `feature/people-sourcing` (PR #17,
  pas encore mergée au moment d'écrire ce plan). **Rebaser sur `develop` dès que #17 est
  mergée**, avant d'ouvrir la PR.
- Acquis de #17 :
  - `person_photos.review_status` (`PENDING` / `APPROVED` / `REJECTED`), avec des
    index uniques âge/année partiels (`WHERE review_status <> 'REJECTED'`) ;
  - `GET /api/images/{publicId}` ne sert que les photos `APPROVED` ;
  - trigger V4 `people_excluded_deletes_photos` : passer `people.excluded` à vrai
    supprime les photos et leurs images ;
  - entités `Person`, `PersonPhoto`, `PhotoImage` dans `features/people/`.
- Auth existante (`features/auth/`) :
  - aucun rôle ;
  - JWT HS256 émis par `AuthTokenService.issueAccessToken` (claims `sub`, `email`),
    valable 60 min, et réémis depuis la base à chaque refresh ;
  - `config/SecurityConfig` : tout est authentifié sauf des allow-lists ;
  - `/api/auth/me` renvoie `ProfileResponse`.
- Front : `Auth` (signals `currentUser`, `isAuthenticated`, `sessionChecked$`),
  `authGuard`, `auth-interceptor` (ajoute le bearer), routes lazy dans `app.routes.ts`.
- Règle ArchUnit : les features ne dépendent pas les unes des autres. `people` ne doit
  donc rien importer de `auth` : le contrôle du rôle se fait dans `SecurityConfig`, par
  chemin.

## Décisions validées avec l'utilisateur (2026-10-26)
- **Rôle admin en base** : colonne `users.role` (`USER` / `ADMIN`, défaut `USER`),
  posée **à la main en SQL**. Aucune interface ni endpoint pour devenir admin.
- **Exclusion depuis la page de curation** : bouton « Exclure cette personne » avec
  confirmation explicite, plus une **recherche par nom ou QID** pour traiter une
  demande de retrait reçue par email.
- **Approbation des personnes vivantes bloquée par réglage** :
  `app.curation.allow-living-approval`, `false` par défaut. Tant qu'il est à `false`, le
  serveur refuse d'approuver la photo d'une personne sans date de décès, en attendant
  que le droit à l'image soit tranché. Le rejet reste toujours possible. Le front
  désactive le bouton « Approuver » et explique pourquoi.

## Hypothèses
- **Retrait d'une personne pas encore ingérée** : hors scope. `people` impose une date
  de naissance et un nom, donc on ne peut pas créer une ligne « bloquée d'avance » sans
  détourner le schéma. Procédure documentée : noter le QID, puis l'exclure après
  l'ingestion. Une table `blocked_wikidata_ids` sera ajoutée si le cas se présente
  vraiment.
- **Pas de TOTP pour l'admin** dans cette PR : il est à la roadmap (Semaine 7). Le
  compte admin n'est protégé que par son mot de passe ; à documenter comme risque
  accepté.
- **Retrait du rôle admin** : effectif au plus tard à l'expiration de l'access token
  (≤ 60 min), le refresh relisant le rôle en base. Délai jugé acceptable pour un
  administrateur unique.

## Décisions d'architecture de cette feature
- **Rôle dans le JWT** : claim `roles` (liste, par exemple `["ADMIN"]`, absent pour
  `USER`), émis par `AuthTokenService` depuis `users.role`.
  - Côté API, un `JwtAuthenticationConverter` (dans `config/JwtConfig`) transforme
    `roles` en autorités `ROLE_ADMIN`.
  - `SecurityConfig` : `/api/admin/**` exige `hasRole("ADMIN")`. On garde la règle par
    chemin plutôt que `@PreAuthorize` : une seule source de vérité, et `people` ne
    connaît pas `auth`.
- **`/api/auth/me`** expose `role` : le front l'utilise pour afficher le lien et
  garder la route. C'est purement cosmétique, le serveur reste seul juge.
- **Endpoints admin dans `features/people/`**, un package par use case :

  | Package | Endpoint |
  |---|---|
  | `ListPendingPhotos/` | `GET /api/admin/photos/pending?limit=20` |
  | `GetPhotoForReview/` | `GET /api/admin/photos/{photoId}/image` |
  | `ReviewPhoto/` | `PUT /api/admin/photos/{photoId}/review` |
  | `SearchPeople/` | `GET /api/admin/people?query=…` |
  | `ExcludePerson/` | `POST /api/admin/people/{personId}/exclusion` |

- **File d'attente** : les photos `PENDING`, triées par notoriété décroissante de la
  personne puis par date de prise de vue. Chaque élément porte ce qu'il faut pour
  juger :
  - nom EN/FR, QID ;
  - naissance, décès (ou « vivant ») ;
  - intervalle de prise de vue et précision, âge calculé ;
  - source de la date ;
  - lien vers la page Commons, licence, auteur ;
  - les **autres photos de la personne** (statut, âge), pour voir l'écart ;
  - `approvable` (faux pour une personne vivante tant que le réglage est à `false`) ;
  - le nombre total de photos en attente.
- **Image d'une photo à revoir** : endpoint admin distinct de l'endpoint public. Il
  sert la photo quel que soit son statut, avec `Cache-Control: no-store` (une photo en
  attente ne doit rester dans aucun cache partagé). Le front la charge via `HttpClient`
  (bearer ajouté par l'interceptor) et l'affiche en `blob:` URL, parce qu'un
  `<img src>` n'enverrait pas l'en-tête `Authorization`.
- **Revue** : corps `{"decision": "APPROVED" | "REJECTED" | "PENDING"}`. `PENDING`
  sert à **annuler** la dernière décision.
  - Toute transition est permise. Si elle viole un index unique âge/année (par exemple
    rouvrir une photo rejetée alors qu'une autre a pris sa place), l'API renvoie **409
    `PHOTO_SLOT_TAKEN`**.
  - Approuver une photo de personne vivante avec le réglage à `false` renvoie **409
    `LIVING_PERSON_APPROVAL_DISABLED`**.
  - On stocke `reviewed_at` et `reviewed_by` (V6), pour l'audit.
- **Écart de 10 ans à l'approbation** : il n'est **pas** revérifié. L'ingestion l'a
  déjà appliqué entre photos non rejetées, et la revue ne change ni l'âge ni l'année
  d'une photo.
- **Exclusion** : idempotente, irréversible depuis l'interface. Elle passe
  `excluded = true`, et le trigger V4 efface photos et images. La réponse indique le
  nombre de photos supprimées.
- **Migrations** :
  - `V5__add_user_role.sql` : `users.role` + `CHECK` ;
  - `V6__add_photo_review_audit.sql` : `person_photos.reviewed_at TIMESTAMPTZ`,
    `reviewed_by UUID REFERENCES users ON DELETE SET NULL`. Si l'admin supprime son
    compte, l'historique reste, anonymisé.
- **Front** :
  - nouvelle feature `frontend/src/app/features/curation/` : page `/admin/curation`,
    `adminGuard`, `CurationApi` ;
  - lien « Curation » dans l'en-tête, visible seulement pour un admin ;
  - raccourcis : **A** approuver, **R** rejeter, **U** annuler la dernière décision,
    **→** passer sans décider ;
  - photo suivante préchargée pour enchaîner sans attente ;
  - libellés en anglais comme le reste de l'interface, couleurs et formes issues des
    tokens de la charte (`docs/decisions/design.md`).

## Tests : catégories retenues
| Catégorie | S'applique ? | Pourquoi |
|---|---|---|
| Unitaire backend | **Oui** | Handlers : règle des vivants, transitions, 409 sur conflit d'index, exclusion idempotente, mapping de la file. Claim `roles` dans `AuthTokenService`, conversion `roles` → autorités. |
| Intégration backend | **Oui** | V5/V6 (défaut `USER`, `CHECK`, `reviewed_by` en `SET NULL` quand l'utilisateur est supprimé), ordre de la file, recherche par nom/QID insensible à la casse et aux accents, exclusion qui déclenche bien le trigger depuis le handler. |
| Fonctionnel backend | **Oui** | Matrice d'accès sur chaque endpoint admin : 401 anonyme, 403 `USER`, 2xx `ADMIN`. Parcours revue → l'image publique devient servie. Refus des vivants. Annulation. `/me` renvoie `role`. Rétrogradation prise en compte au refresh. |
| Unitaire frontend | **Oui** | `adminGuard`, `CurationApi`, composant : raccourcis clavier, bouton désactivé pour un vivant, libération des `blob:` URL, annulation, compteur. Lien d'en-tête conditionnel. |
| Interface (UI) | **Oui, 1 spec** | `frontend/e2e/component/curation.spec.ts` : page avec réseau simulé. Enchaînement au clavier, focus visible, rendu de l'image blob dans un vrai navigateur (ce que jsdom ne fait pas). |
| QA e2e | **Non** | Parcours interne à un seul administrateur, pas un parcours joueur critique. Il faudrait seeder un compte admin et des images en base réelle ; les tests fonctionnels et d'interface couvrent déjà les deux moitiés. |
| Architecture | **Oui (existant)** | `features_do_not_depend_on_each_other` garantit que `people` n'importe rien de `auth`. Pas de nouvelle règle : le contrôle d'accès est centralisé par chemin dans `SecurityConfig`, et c'est vérifié par les tests fonctionnels. |
| Mutation | **Oui (auto)** | `./scripts/run-mutation-tests.sh`, viser ≥ 90 % sur les nouveaux handlers. |

## Edge cases à couvrir
- Utilisateur `USER` sur `/api/admin/**` → 403. Anonyme → 401. Jeton expiré → 401.
- Admin rétrogradé en SQL → encore admin jusqu'à expiration du token, plus admin après
  refresh.
- Revue d'une photo inconnue, ou d'une personne déjà exclue (photo supprimée) → 404.
- Approuver une photo de vivant avec le réglage à `false` → 409, et le statut ne change
  pas.
- Rouvrir (`PENDING` / `APPROVED`) une photo `REJECTED` dont l'année ou l'âge est pris
  par une autre → 409 `PHOTO_SLOT_TAKEN`.
- Deux onglets admin qui revoient la même photo → la dernière décision gagne. Pas de
  verrou : un seul administrateur.
- Exclure une personne déjà exclue → 200, 0 photo supprimée.
- File vide → message « Nothing left to review », raccourcis inactifs.
- Image introuvable pour une photo (ne devrait pas arriver) → l'élément s'affiche avec
  une erreur, et le rejet reste possible.
- Recherche : chaîne vide, QID exact (`Q42`), nom avec accents ou casse différente,
  plus de 20 résultats (limite).
- Raccourcis clavier ignorés quand le focus est dans un champ texte (la recherche).
- Décision envoyée pendant qu'une autre est en cours → ignorée côté front (pas de
  double envoi).

---

## Phase 1 : Rôle admin
Status: Complete

- [x] `ageguessr-api/src/main/resources/db/migration/V5__add_user_role.sql`.
- [x] `features/auth/UserRole.java` (`USER`, `ADMIN`) ; `User.role` (`@Enumerated`).
- [x] `AuthTokenService.issueAccessToken` : claim `roles` pour un admin.
- [x] `config/JwtConfig` : bean `JwtAuthenticationConverter` (`roles` → `ROLE_*`).
- [x] `config/SecurityConfig` : `/api/admin/**` → `hasRole("ADMIN")`.
- [x] `ProfileResponse.role` ; front `AuthUser.role`,
      `features/curation/admin-guard.ts`.
- [x] Tests : unitaires (`AuthTokenServiceTest`, conversion des rôles), intégration
      (défaut `USER`, `CHECK`), fonctionnels (`/me` renvoie le rôle ; matrice d'accès
      sur un endpoint admin de test), front (`adminGuard`).

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert.
- `cd frontend && pnpm test && pnpm lint` → vert.

### Phase Summary
**Backend** :
- `V5` : `users.role` (défaut `USER`, `CHECK users_role`). `UserRole` ; `User.role`
  vaut `USER` par défaut via `@Builder.Default` : sans ça, Hibernate insérerait `NULL`
  et contournerait le défaut SQL.
- `AuthTokenService.ROLES_CLAIM = "roles"`, émis seulement pour un admin et relu en
  base à chaque émission (refresh compris).
- `JwtConfig.jwtAuthenticationConverter` : `roles` devient `ROLE_*`.
- `SecurityConfig` : `/api/admin/**` exige `hasRole("ADMIN")`.
- `ProfileResponse.role`.

**Constat** : Spring Security 7 ajoute d'office une autorité `FACTOR_BEARER` (facteur
d'authentification, pour les règles MFA). Ce n'est pas une permission : les tests
filtrent `ROLE_*` et `SCOPE_*`.

**Frontend** :
- `AuthUser.role` (type `UserRole`) ; `features/curation/admin-guard.ts` (invité vers
  `/login` avec `returnUrl`, utilisateur simple vers `/`).
- Specs et mocks e2e adaptés au nouveau champ.

**Tests** :
- `AuthTokenServiceTest` (2), `JwtConfigTest` (3), `UserRepositoryIntegrationTest` (+2).
- `AdminAccessFunctionalTest` (5) : un chemin admin sans handler sert de sonde (401
  anonyme, 403 `USER`, 404 `ADMIN`), plus une promotion SQL effective au refresh
  suivant seulement. `MeFunctionalTest` vérifie `role`.
- Front : 141/141, lint OK, Prettier OK, Playwright component 38/38.


## Phase 2 : File d'attente, image et revue (backend)
Status: Complete

- [x] `V6__add_photo_review_audit.sql` ; `PersonPhoto.reviewedAt`, `reviewedBy`.
- [x] Propriété `app.curation.allow-living-approval=false` (+ fichier de test).
- [x] `ListPendingPhotos/` (query, handler, controller, réponse) et requête de
      repository (tri sitelinks puis date).
- [x] `GetPhotoForReview/` (`no-store`).
- [x] `ReviewPhoto/` (commande `@Valid`, règle des vivants, conversion de la violation
      d'index en `ConflictException` avec `errorCode`).
- [x] Tests unitaires, intégration et fonctionnels de ces trois use cases.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert.
- Manuel : ingestion locale `--limit=10`, rôle admin posé en SQL, puis
  `curl -H "Authorization: Bearer …" localhost:8080/api/admin/photos/pending` → JSON
  attendu ; une revue `APPROVED` rend l'image publique servie.

### Phase Summary
**Livré** :
- `V6` : `reviewed_at`, `reviewed_by` (FK `users`, `ON DELETE SET NULL`), plus un index
  partiel `person_photos_pending_idx`.
- `CurationProperties.canApprove(Person)` : une règle unique pour le drapeau
  `approvable` de la file et pour la revue. `CurationConfig` l'active.
- `PendingPhoto` (projection JPQL `new …(ph, p)`), et dans `PersonPhotoRepository` :
  `findPending` (sitelinks décroissants, puis date, puis id pour un ordre stable),
  `countPending`, `findByPersonIdIn` (les autres photos des personnes du lot, en une
  seule requête).
- `ListPendingPhotos/` : `?limit` vaut 20 par défaut, compris entre 1 et 50. Le défaut
  est posé dans le constructeur compact du record, avant la validation.
- `GetPhotoForReview/` : tout statut, `Cache-Control: no-store`.
- `ReviewPhoto/` : `PUT`, `{"decision": …}`. 409 `LIVING_PERSON_APPROVAL_DISABLED` ;
  la violation d'index partiel devient 409 `PHOTO_SLOT_TAKEN` ; personne exclue → 404.

**Tests** :
- unitaires : 15 ;
- intégration : 29 (+3 : ordre de la file, lecture groupée, `reviewed_by` remis à
  `NULL` quand le compte admin est supprimé) ;
- fonctionnels : `CurationFunctionalTest` (9). Il obtient de vrais jetons en créant les
  utilisateurs directement puis en appelant `AuthTokenService.issueAccessToken`.

Vérification manuelle (curl sur données locales) reportée à la Phase 4, avec la page.


## Phase 3 : Recherche et exclusion (backend)
Status: Complete

- [x] `SearchPeople/` (nom EN/FR ou QID, `unaccent` + `ILIKE` si disponible sinon
      normalisation Java, limite 20).
- [x] `ExcludePerson/` (idempotent, renvoie le nombre de photos supprimées).
- [x] Tests unitaires, intégration (le trigger efface bien les photos) et
      fonctionnels.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert.

### Phase Summary
**Livré** :
- `V7` : extension `unaccent` (« trusted » depuis PG 13, activable par le propriétaire
  de la base).
- `PersonRepository.searchByName` : requête native,
  `unaccent(lower(...)) LIKE unaccent(lower('%' || :fragment || '%'))` sur `name_en`
  et `name_fr`, sitelinks décroissants, `LIMIT`. Le handler échappe `\`, `%` et `_`.
- `SearchPeople/` : `GET /api/admin/people?query=` (2 à 100 caractères). Un QID exact
  (insensible à la casse) est cherché par identifiant, le reste par nom. Chaque
  résultat donne le nombre de photos et le statut `excluded`.
- `ExcludePerson/` : `POST /api/admin/people/{id}/exclusion`, idempotent, renvoie
  `deletedPhotos`. Le trigger V4 fait la suppression. Log avec le QID seul.

**Tests** :
- unitaires : 8 ;
- intégration : +3 (accents et casse dans les deux langues, ordre et plafond, `%`
  échappé) ;
- fonctionnels : `CurationFunctionalTest` passe à 12 (matrice d'accès étendue aux
  5 endpoints, parcours recherche → exclusion → photos et images effacées, endpoint
  public en 404, seconde exclusion sans effet).


## Phase 4 : Page de curation (frontend)
Status: Complete

- [x] `features/curation/curation-api.ts` (HttpClient, image en blob).
- [x] `features/curation/curation/curation.{ts,html}` : photo, informations, boutons,
      raccourcis, préchargement, annulation, compteur, file vide.
- [x] `features/curation/exclusion/` : recherche et exclusion avec confirmation.
- [x] Route `/admin/curation` (`authGuard` + `adminGuard`), lien d'en-tête pour admin.
- [x] Tests unitaires et 1 spec d'interface Playwright.

### Verification Plan
- `cd frontend && pnpm test && pnpm lint && npx prettier --check src` → vert.
- `cd frontend && pnpm test:e2e -- e2e/component/curation.spec.ts` → vert (projet Playwright `component`).
- Manuel : enchaîner 10 photos au clavier sur les données locales.

### Phase Summary
**Livré** (`frontend/src/app/features/curation/`) :
- `models.ts`, `curation-api.ts` (image en `responseType: 'blob'`).
- `curation/` :
  - file locale complétée par lots de 20 dès qu'il reste moins de 5 photos, sans
    doublon ni photo passée ;
  - préchargement de la photo courante et de la suivante ;
  - URL `blob:` libérées quand la photo sort, ou si la réponse arrive après qu'elle est
    sortie ;
  - raccourcis A / R / → / U via `host: {'(document:keydown)'}`, ignorés dans un champ
    ou avec une touche modificatrice (Ctrl+R recharge la page, il ne rejette pas) ;
  - messages pour `LIVING_PERSON_APPROVAL_DISABLED`, `PHOTO_SLOT_TAKEN` et 404 ;
    « Approuver » désactivé et expliqué pour une personne vivante.
- `exclusion/` : recherche puis exclusion en deux clics (pas de `confirm()`
  navigateur). Le message rappelle de confirmer à la personne qui a fait la demande.
  L'événement `excluded` recharge la file.
- Route `/admin/curation` (`adminGuard`) et lien « Curation » dans l'en-tête pour un
  admin.

**Bug attrapé par les tests** : `(ngSubmit)` sur un `<form>` sans `[formGroup]` ne se
déclenche jamais avec `ReactiveFormsModule` seul. La recherche n'aurait pas marché.
Corrigé avec un `FormGroup`.

**Tests** :
- unitaires front : **165/165** (+24) ;
- Playwright component : **42/42** (+4 dans `curation.spec.ts` : image blob réellement
  décodée, A puis U au clavier, A sans effet sur une personne vivante, saisie dans la
  recherche sans effet, utilisateur simple renvoyé à l'accueil, pas de défilement
  horizontal à 360 px).

**Smoke test backend sur données réelles (2026-10-26)** :
- ingestion locale `--limit=5` : V4 à V7 appliquées à la base de dev, `unaccent`
  compris ;
- compte admin jetable créé en SQL ; jeton signé avec le secret local ;
- file : Obama, Trump, Hitler, Poutine, Mandela, avec `approvable` correct (vivants
  bloqués) ;
- image admin : 200, `no-store`, 82 Ko ; 401 sans jeton ;
- vivant : approbation 409, rejet puis annulation OK ; décédé : approbation OK ;
- recherche « bara » trouve Barack Obama ;
- compte supprimé ensuite : `reviewed_by` passe à `NULL`, `reviewed_at` est conservé ;
  les 10 comptes locaux sont intacts.

**Point éditorial soulevé, à trancher par l'utilisateur** : les personnes les plus
connues incluent des figures comme Adolf Hitler. La curation permet de les rejeter au
cas par cas ; une politique éditoriale explicite reste à décider.


## Phase 5 : Docs, revues, PR
Status: Not started

- [ ] `docs/decisions/architecture.md` : rôles et curation, procédure de retrait
      (y compris pour une personne pas encore ingérée), risque « admin sans TOTP ».
- [ ] `docs/roadmap.md` : cocher la curation.
- [ ] Agents `architecture-reviewer` et `code-reviewer` ; corriger.
- [ ] Rebaser sur `develop` (après merge de #17), tests complets + mutation, `/pr`.

### Verification Plan
- Backend, frontend, mutation verts ; CI de la PR verte.

### Phase Summary
_(à écrire quand la phase est finie)_

## Final Recap
_(à écrire quand toutes les phases sont finies)_

## Deployment Plan
_(à écrire quand toutes les phases sont finies : migrations V5/V6, rôle admin à poser en
SQL en prod, réglage `allow-living-approval`)_

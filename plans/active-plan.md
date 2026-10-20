# Finalisation de l'authentification — Ageguessr

Clore l'authentification : pseudo choisi à l'inscription (unique, modifiable), mot de
passe oublié, page compte (pseudo, mot de passe), suppression de compte, et une vraie
navigation frontend (accueil, liens connexion/inscription, pseudo + déconnexion dans
l'en-tête). Branche `feature/auth-completion` depuis `develop`.

## Contexte pour repartir à froid

- Le backend d'auth existe (plan archivé `plans/done/authentication.md`) : register,
  confirm-email, resend-verification, login (+ verrouillage), refresh rotatif, logout,
  me. Package unique `features/auth/<UseCase>/`.
- Les pages frontend `/register`, `/login`, `/check-email`, `/confirm-email` existent
  et fonctionnent en prod, mais **rien n'y mène** : `app.html` affiche une carte
  d'accueil codée en dur au-dessus du `<router-outlet>` sur toutes les pages, et
  l'en-tête n'a que le toggle de thème.
- UI en anglais (audience internationale), docs/commentaires du repo en français.

## Décisions validées avec l'utilisateur (2026-10-20)

- **Pseudo** : unique sans tenir compte de la casse, 3 à 20 caractères
  `[A-Za-z0-9_-]`, choisi à l'inscription, **modifiable** depuis la page compte.
  Casse d'affichage conservée telle que saisie.
- **Connexion par email uniquement** (le pseudo est public, ne pas en faire un
  identifiant de connexion).
- **Inclus** : mot de passe oublié, page compte, suppression de compte.
- **Récupération après vol de compte** (demandé par l'utilisateur : « s'il se fait
  prendre son compte, il ne peut plus rien faire ? ») :
  1. Le mail « your password was changed » contient un lien **« Wasn't you? Secure
     your account »** = token de reset valable **24 h** (au lieu d'1 h) → un clic,
     nouveau mot de passe, toutes les sessions révoquées.
  2. **Suppression de compte confirmée par email** en plus du mot de passe : un
     attaquant qui n'a que le mot de passe ne peut pas faire l'irréversible.
  3. Règle écrite pour le futur changement d'email (hors périmètre ici).
  4. Hors périmètre : attaquant qui contrôle la boîte mail → 2FA (TOTP) ajoutée à la
     roadmap.

## Décisions d'architecture de cette feature

- **Tout reste dans `features/auth/`** : nouveaux use cases `ForgotPassword/`,
  `ResetPassword/`, `ChangeUsername/`, `ChangePassword/`, `DeleteAccount/`. Un package
  `features/account/` dépendrait de `User` (dans `features/auth`) et violerait la règle
  ArchUnit « les features ne dépendent pas les unes des autres ».
- **Endpoints** (tous sous `/api/auth/`, donc couverts par le rate limit IP existant) :
  - `POST /api/auth/forgot-password` `{email}` → **toujours 204** (pas d'énumération
    des comptes), email envoyé seulement si le compte existe.
  - `POST /api/auth/reset-password` `{token, newPassword}` → 204 ; 404
    `INVALID_RESET_TOKEN` si token inconnu/expiré/consommé.
  - `PATCH /api/auth/me/username` `{username}` → 200 `MeResponse` ; 409
    `USERNAME_TAKEN`.
  - `POST /api/auth/me/password` `{currentPassword, newPassword}` → 204 ; 403
    `INVALID_CURRENT_PASSWORD`.
  - `POST /api/auth/me/deletion` `{password}` → **202** (email de confirmation envoyé,
    rien n'est supprimé) ; 403 `INVALID_CURRENT_PASSWORD`.
  - `POST /api/auth/confirm-account-deletion` `{token}` → 204 + cookie refresh effacé ;
    404 `INVALID_DELETION_TOKEN`. Public : le lien peut être ouvert sur un autre
    appareil, non connecté.
  - `forgot-password`, `reset-password`, `confirm-account-deletion` ajoutés à
    `PUBLIC_AUTH_PATHS`.
- **Codes d'erreur 409 distincts** : `ConflictException` prend un `errorCode` (comme
  `ForbiddenException`) → `EMAIL_TAKEN` / `USERNAME_TAKEN`, pour que le formulaire
  affiche l'erreur sur le bon champ. L'existence d'un email était déjà révélée par
  `register` (choix antérieur, inchangé) ; celle d'un pseudo est publique par nature.
- **Tokens de reset et de suppression** : une seule nouvelle table `account_tokens`
  avec une colonne `purpose` (`PASSWORD_RESET`, `ACCOUNT_DELETION`), même forme que
  `email_verification_tokens` (hash SHA-256 via `OpaqueTokenGenerator`, jamais le token
  brut), usage unique, `ON DELETE CASCADE`. Une table plutôt que deux : même cycle de
  vie, même code ; un token d'un usage n'est jamais accepté pour l'autre (filtre par
  `purpose`, testé). TTL : reset demandé **1 h**
  (`app.account-token.password-reset-ttl-minutes=60`), lien « secure your account »
  **24 h** (`app.account-token.secure-account-ttl-hours=24`), suppression **1 h**
  (`app.account-token.deletion-ttl-minutes=60`). `email_verification_tokens` reste
  tel quel (déjà en prod, pas de migration de données pour rien).
- **Un changement de mot de passe n'invalide pas les liens de reset en cours** : sinon
  un attaquant qui change le mot de passe deux fois tuerait le lien de récupération du
  propriétaire. Seul un reset réussi invalide les autres tokens de reset.
  Un reset réussi : nouveau hash, `failedLoginAttempts=0`, `lockedUntil=null`,
  `emailVerified=true` (cliquer le lien prouve la possession de l'email), invalide les
  autres tokens de reset du compte, **révoque tous les refresh tokens** (déconnecte
  partout — c'est le but si le compte était compromis).
- **Changement de mot de passe** : exige le mot de passe actuel, révoque tous les
  refresh tokens **sauf celui de la session courante** (lu dans le cookie) → l'utilisateur
  reste connecté ici, déconnecté ailleurs. Email de notification « your password was
  changed » (aussi après un reset), avec le lien « secure your account » (24 h).
- **Suppression de compte** : exige le mot de passe **puis** la confirmation par le
  lien email ; suppression physique de la ligne `users` à la confirmation ; les tokens partent par `ON DELETE CASCADE` (déjà en place, testé par
  `CascadeDeleteIntegrationTest`). Règle documentée dans `architecture.md` : toute
  future table liée à un utilisateur (parties, amis) doit être en `ON DELETE CASCADE`
  ou anonymisée — c'est le droit à l'effacement RGPD.
- **La page de confirmation de suppression exige un clic explicite** (« Delete my
  account permanently ») au lieu d'appeler l'API au chargement : certains scanners de
  liens des messageries ouvrent les liens des mails (et parfois exécutent le JS), ce
  qui supprimerait le compte sans action de l'utilisateur.
- **Règle pour le futur changement d'email** (dans `architecture.md`) : confirmation
  sur la nouvelle adresse + notification à l'ancienne avec un lien d'annulation
  valable plusieurs jours, sinon un vol de compte devient définitif.
- **Migration `V2__add_username_and_password_reset.sql`** : colonne `username
  VARCHAR(20)` ajoutée nullable, **backfill** des comptes existants en prod
  (`'player-' || left(replace(id::text,'-',''), 8)`, conforme au format), puis
  `NOT NULL` + index unique sur `LOWER(username)`. Le compte de test éventuel de
  l'utilisateur en prod pourra changer ce pseudo depuis la page compte.
- **`MeResponse`** gagne `username` ; le JWT ne change pas (le pseudo est modifiable,
  le mettre dans un token d'1 h le rendrait périmé).
- **Frontend** :
  - Nouvelle feature `features/home/` (page d'accueil = route `''`, carte actuelle
    sortie de `app.html`) et `features/layout/header/` (logo → `/`, toggle thème,
    invité : « Log in » / « Sign up » ; connecté : pseudo → `/account`, « Log out »).
  - Nouvelles pages `features/auth/forgot-password/`, `reset-password/`, `account/`
    (sections : infos, changer le pseudo, changer le mot de passe, zone de danger
    suppression avec confirmation par mot de passe).
  - Champ `username` ajouté à `/register` ; lien « Forgot password? » sur `/login`.
  - **Course au démarrage** : `tryRestoreSession()` est asynchrone, donc un
    rechargement direct de `/account` ferait rediriger `authGuard` vers `/login` avant
    la fin de la restauration. `Auth` expose un état `sessionChecked` et le guard
    attend sa résolution avant de décider.
  - `guestGuard` sur `/login` et `/register` : un utilisateur connecté est renvoyé
    vers `/`.
  - Validation du pseudo côté client = même regex que le backend (le backend reste la
    seule source de vérité).
- **Hypothèses** (non bloquantes, à corriger si l'utilisateur n'est pas d'accord) :
  pas de vérification de disponibilité du pseudo en direct pendant la saisie (erreur
  à la soumission) ; pas de liste de pseudos réservés/interdits pour l'instant ;
  changer d'email hors périmètre.

## For Future Agents

Cocher au fur et à mesure ; quand une phase est finie, passer son statut à `Complete`,
lancer son Verification Plan et écrire son Phase Summary avant de passer à la suite.
Tests écrits en même temps que le code. Après l'implémentation : relecture par
`code-reviewer` (auth + données utilisateur) **et** `architecture-reviewer`, jamais
sautées. Tout nouveau clé dans `src/main/resources/application.properties` doit être
recopiée dans `src/test/resources/application.properties` (le fichier de test
remplace celui de main, il ne le complète pas).

## Phase 1 : Pseudo (backend)
Status: Complete

- [x] `ageguessr-api/src/main/resources/db/migration/V2__add_username_and_account_tokens.sql`
      (colonne `username` + backfill + `NOT NULL` + index unique `LOWER(username)` ;
      table `account_tokens` (`purpose`) + index, `ON DELETE CASCADE`)
- [x] `features/auth/User.java` : champ `username` ; `UserRepository` :
      `existsByUsernameIgnoreCase`
- [x] `shared/exceptions/ConflictException.java` : `errorCode` ;
      `config/GlobalExceptionHandler.java` le renvoie ; `RegisterHandler` →
      `EMAIL_TAKEN` / `USERNAME_TAKEN`
- [x] `shared/validation/Username.java` : contrainte Bean Validation composée
      (`@NotBlank @Pattern("^[A-Za-z0-9_-]{3,20}$")`), réutilisée par register et
      change-username
- [x] `Register/RegisterCommand.java` + `RegisterHandler` + `RegisterResponse` :
      `username`
- [x] `Me/MeResponse.java` → déplacé en `features/auth/ProfileResponse.java` (+ `username`,
      `ProfileResponse.from(User)`) : partagé par `Me` et `ChangeUsername`, éviter qu'un
      use case dépende du package d'un autre
- [x] `ChangeUsername/{ChangeUsernameCommand,ChangeUsernameController,ChangeUsernameHandler}.java`
      (même pseudo à la casse près pour soi-même = autorisé, ex. `max` → `Max`)

**Tests**
- Unitaire : `RegisterHandlerTest` (pseudo pris → `USERNAME_TAKEN`, email pris →
  `EMAIL_TAKEN`), `ChangeUsernameHandlerTest` (pris par un autre → 409, son propre
  pseudo avec une autre casse → OK).
- Intégration : `UserRepositoryIntegrationTest` (index unique insensible à la casse
  sur `username`, `existsByUsernameIgnoreCaseAndIdNot`). Backfill de V2 vérifié à la
  main sur un Postgres 18 jetable (V1 + 2 comptes + V2 → `player-3c2991b4`,
  `player-7e0074ab`, format valide ; doublon à la casse près refusé par l'index).
- Fonctionnel : `RegisterFunctionalTest` (pseudo invalide → 400, pris → 409
  `USERNAME_TAKEN`), `MeFunctionalTest` (renvoie `username`),
  `ChangeUsernameFunctionalTest` (401 sans token, 200, 409, 400).
- `AuthFlows.registerAndVerify` prend un pseudo.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → BUILD SUCCESS

### Phase Summary
Pseudo en place côté backend. `./mvnw test` : 85 tests, 0 échec. Écarts au plan :
migration nommée `V2__add_username_and_account_tokens.sql` (crée aussi `account_tokens`
pour la phase 2) ; `MeResponse` devenu `ProfileResponse` à la racine de la feature.
Les tests fonctionnels construisent le corps d'inscription via
`AuthFlows.registerBody(...)` / `AuthFlows.randomUsername()` (pseudo unique par appel,
les tests partagent une base).

## Phase 2 : Mot de passe oublié, changement de mot de passe, suppression (backend)
Status: Complete

- [x] `features/auth/AccountToken.java` + `AccountTokenPurpose.java` +
      `AccountTokenRepository.java` (dont `consumeAllActiveForUser(userId, purpose,
      now)`, `@Transactional @Modifying`)
- [x] `features/auth/AccountTokenService.java` (émission d'un token par usage +
      validation/consommation) et `features/auth/AccountEmails.java` (mails reset,
      notification « password changed » + lien 24 h, confirmation de suppression) ;
      clés `app.account-token.*` dans les deux `application.properties`
- [x] `ForgotPassword/{Command,Controller,Handler}.java`
- [x] `ResetPassword/{Command,Controller,Handler}.java`
- [x] `RefreshTokenRepository.revokeAllActiveForUserExcept(userId, keepHash, now)`
- [x] `ChangePassword/{Command,Controller,Handler}.java` (lit le cookie refresh)
- [x] `DeleteAccount/{Command,Controller,Handler}.java` (vérifie le mot de passe,
      envoie le mail, 202)
- [x] `ConfirmAccountDeletion/{Command,Controller,Handler}.java` (supprime, efface le
      cookie)
- [x] `SecurityConfig.PUBLIC_AUTH_PATHS` : `forgot-password`, `reset-password`
- [x] `TestSupportController` : `/test-support/verification-token` lit le token du
      dernier mail reçu quel qu'il soit → renommer en `/test-support/last-email-token`
      (et adapter le journey existant)
- [x] `docs/decisions/architecture.md` : section Authentification (reset, révocation
      des sessions) + règle RGPD `ON DELETE CASCADE` pour les futures tables + règle du futur changement
      d'email

**Tests**
- Unitaire : `ForgotPasswordHandlerTest` (compte inconnu → aucun email, aucune
  exception), `ResetPasswordHandlerTest` (expiré / consommé / inconnu → 404 ; succès →
  hash changé, verrou levé, email vérifié, sessions révoquées, autres tokens
  invalidés), `ChangePasswordHandlerTest` (mauvais mot de passe → 403 ; succès →
  sessions révoquées sauf la courante, liens de reset en cours toujours valides, mail
  de notification avec lien 24 h), `DeleteAccountHandlerTest` (mauvais mot de passe →
  403, rien envoyé ; bon → mail, compte intact), `ConfirmAccountDeletionHandlerTest`
  (token de reset refusé, expiré, consommé ; succès → suppression).
- Intégration : `RefreshTokenRepository.revokeAllActiveForUserExcept`,
  `AccountTokenRepository.consumeAllActiveForUser` (ne touche que le `purpose` donné) ;
  `CascadeDeleteIntegrationTest` étendu aux `account_tokens`.
- Fonctionnel : `PasswordResetFunctionalTest` (forgot → token de l'email → reset →
  ancien mot de passe refusé, nouveau accepté, ancien refresh token rejeté ; forgot
  sur email inconnu → 204), `ChangePasswordFunctionalTest` (session courante toujours
  valide, autre session révoquée, lien « secure » du mail utilisable),
  `DeleteAccountFunctionalTest` (202, compte toujours là ; confirmation → 204, puis
  login → 401, `/me` avec l'ancien JWT → 404).
- Architecture : règles ArchUnit existantes suffisent (nouveaux use cases dans
  `features/auth`, aucune nouvelle règle).

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → BUILD SUCCESS

### Phase Summary
Backend complet. `./mvnw test` : 125 tests, 0 échec (17 nouveaux fonctionnels,
5 intégration, 21 unitaires). Écarts et ajouts par rapport au plan :
- **Demande de suppression = `POST /api/auth/me/deletion`** (pas `DELETE /api/auth/me`
  avec un body, que certains proxys/clients suppriment).
- **Erreurs de token = 404 `NOT_FOUND` générique** (pas de codes
  `INVALID_RESET_TOKEN`/`INVALID_DELETION_TOKEN`) : le frontend n'a besoin que du statut.
- **`AccountLockout`** extrait de `LoginHandler` (même politique pour login et
  re-saisie du mot de passe) + **`CurrentPasswordVerifier`** ; `LoginHandlerTest`
  inchangé hormis le constructeur, ses 8 tests passent.
- **`AccountTokenRepository.markConsumed`** = `UPDATE ... WHERE consumed_at IS NULL`
  (deux soumissions simultanées du même lien : une seule passe).
- `TestSupportController` : `/test-support/last-email-token` (journey existant adapté).
- Docs : `architecture.md` sections « Pseudo public », « Récupération du compte et
  actions sensibles », « Suppression des données (RGPD) ».

## Phase 3 : Navigation, accueil et inscription avec pseudo (frontend)
Status: Complete

- [x] `frontend/src/app/features/home/home.{ts,html,spec.ts}` (contenu de la carte
      actuelle de `app.html`) + route `''`
- [x] `frontend/src/app/features/layout/header/header.{ts,html,spec.ts}` ;
      `app.html` = header + `<router-outlet>` seulement
- [x] `features/auth/auth.ts` : `sessionChecked`, `register(email, username,
      password)`, `forgotPassword`, `resetPassword`, `changeUsername`,
      `changePassword`, `deleteAccount` ; `models.ts` : `username`
- [x] `features/auth/auth-guard.ts` : attend `sessionChecked` ; `guest-guard.ts`
- [x] `register/` : champ `username` (validation regex, erreur `USERNAME_TAKEN` sur
      le champ) ; `login/` : lien « Forgot password? »
- [x] `docs/decisions/design.md` : rien à ajouter (en-tête construit avec les tokens
      existants, aucune nouvelle décision visuelle)

**Tests**
- Unitaire (Vitest) : `header.spec.ts` (invité / connecté / clic logout), `home.spec.ts`,
  `auth.spec.ts` (nouvelles méthodes, `sessionChecked`), `auth-guard.spec.ts`
  (attend la restauration), `guest-guard.spec.ts`, `register.spec.ts` (pseudo invalide,
  `USERNAME_TAKEN`).
- Interface (Playwright component) : `register.spec.ts` (champ pseudo + erreurs),
  nouveau `header.spec.ts` (liens invité, pseudo une fois connecté, réseau mocké).

### Verification Plan
- `cd frontend && pnpm lint && pnpm format:check && pnpm test` → tout vert
- `cd frontend && pnpm test:e2e` → tout vert
- `pnpm build` → OK, budgets respectés

### Phase Summary
Navigation et inscription avec pseudo en place. Écarts : `Auth.sessionChecked` (signal)
+ `sessionChecked$` attendu par `authGuard`/`guestGuard` ; `authGuard` renvoie vers
`/login?returnUrl=…` et la connexion y revient (chemins internes uniquement, testé contre
`//evil.com`, `/\evil.com`, `https://…`) ; `logout()` vide l'état local même si l'appel
échoue ; route `**` → accueil. **Bug trouvé par le test Playwright mobile** : en-tête
connecté avec un pseudo de 20 caractères débordait de 18 px à 360 px de large → le pseudo
est le seul élément qui rétrécit (`min-w-0` + `truncate`). Ajout d'un
`frontend/.prettierignore` (`test-results/`, `playwright-report/`) : Prettier ne lit pas
le `.gitignore` racine, donc `format:check` (et le hook pre-push) échouait après un
passage local de Playwright — problème antérieur à cette feature.

## Phase 4 : Mot de passe oublié, page compte, suppression (frontend)
Status: Complete

- [x] `features/auth/forgot-password/` (formulaire email → message neutre « if an
      account exists, we sent a link »)
- [x] `features/auth/reset-password/` (lit `?token=`, nouveau mot de passe +
      confirmation, succès → lien login ; token invalide → message + lien vers
      forgot-password)
- [x] `features/auth/account/` (protégée par `authGuard`) : infos, changer le pseudo,
      changer le mot de passe, supprimer le compte (mot de passe → message « check
      your email to confirm »)
- [x] `features/auth/confirm-account-deletion/` (lit `?token=`, bouton explicite,
      jamais d'appel au chargement ; succès → déconnexion locale → message final)
- [x] Routes `forgot-password`, `reset-password`, `account`, `confirm-account-deletion`

**Tests**
- Unitaire (Vitest) : un spec par page (succès, erreurs API, validation).
- Interface (Playwright component) : `forgot-password.spec.ts`,
  `reset-password.spec.ts`, `account.spec.ts`, `confirm-account-deletion.spec.ts`
  (aucune requête avant le clic) (réseau mocké).
- QA e2e (Playwright journeys, app complète) : `auth.spec.ts` étendu —
  inscription avec pseudo → confirmation → connexion → pseudo visible dans l'en-tête →
  changement de pseudo → déconnexion ; nouveau `password-reset.spec.ts` (forgot →
  token via `/test-support` → reset → connexion avec le nouveau mot de passe) ;
  `delete-account.spec.ts` (demande → token via `/test-support` → confirmation →
  reconnexion impossible). Justification : ce
  sont les parcours critiques du compte, un bug ici bloque ou perd un utilisateur.
- Mutation : PIT tourne sur la PR vers `main` (informatif) ; regarder le rapport
  pour les nouveaux handlers.

### Verification Plan
- `cd frontend && pnpm lint && pnpm format:check && pnpm test && pnpm test:e2e` → vert
- Journeys contre l'app complète en local (backend profil `e2e` + `pnpm start`) :
  `pnpm test:e2e:journeys` → vert

### Phase Summary
Pages `forgot-password`, `reset-password`, `account`, `confirm-account-deletion` en
place. `resetPassword` et `confirmAccountDeletion` vident l'état local (toutes les
sessions sont révoquées côté serveur). Vérifs : `pnpm lint`, `format:check`, `pnpm test`
(73 tests, 17 fichiers), `pnpm test:e2e` (25 tests d'interface), `pnpm build` (initial
293 kB). Journeys (4 parcours : inscription → renommage → déconnexion, reset, lien
« secure your account » après changement, suppression) **verts contre l'app complète**,
lancés à la main : Postgres/Redis jetables (ports 5434/6381, `docker run --rm`), backend
`DB_PORT=5434 REDIS_PORT=6381 JWT_SECRET=<celui de src/test/resources>
RESEND_API_KEY=unused AUTH_RATE_LIMIT_MAX_ATTEMPTS=1000 ./mvnw spring-boot:run
-Dspring-boot.run.profiles=e2e`, puis `pnpm exec playwright test --project=journeys`.
Helpers partagés dans `frontend/e2e/journeys/support.ts` et
`frontend/e2e/component/session.ts`. Note : le premier `pnpm test:e2e` après ajout de
fichiers peut échouer en masse pendant la compilation à froid de `ng serve` (relancé :
25/25 stable).

## Phase 5 : Relectures, PR, vérification en prod
Status: In progress

- [x] `code-reviewer` + `architecture-reviewer` sur le diff, corrections appliquées
      (2026-10-20) :
  - **Bug antérieur trouvé (en prod aujourd'hui)** : `BCryptPasswordEncoder` lève une
    exception au-delà de 72 **octets** → inscription avec un mot de passe de 73
    caractères, ou 40 « é », ou 20 emoji = **500**. Corrigé : contrainte partagée
    `shared/validation/Password` (`@NotBlank @Size(min=8) @MaxUtf8Bytes(72)`) sur
    register / reset / change, miroir front (`features/auth/password-rules.ts`). Tests
    fonctionnels de non-régression vérifiés en retirant la borne (→ 500).
  - CORS : `PATCH` ajouté aux méthodes autorisées (utilisé par change-username).
  - Reset : le mail de notification part **après** le commit (`TransactionTemplate`),
    plus d'appel réseau Resend dans la transaction.
  - `AccountEmailsTest` (contenu, liens et durées des 3 mails) ; `submitting` remis à
    `false` sur forgot-password.
  - **Reportés** (non bloquants, à planifier) : délai de réponse de forgot-password
    (compte existant = envoi de mail, plus lent ; sans effet tant que register révèle
    l'email en 409, voir `architecture.md`) ; job de nettoyage des tokens
    expirés/consommés (`account_tokens`, `refresh_tokens`, `email_verification_tokens`) ;
    fusion d'`email_verification_tokens` dans `account_tokens` à la prochaine migration
    de cette table.
- [x] `docs/roadmap.md` : cocher « Inscription/connexion (frontend) » et noter pseudo /
      reset / compte / suppression ; ajouter une ligne « Double authentification (TOTP) »
- [ ] PR `feature/auth-completion` → `develop`, puis release `develop` → `main`
      (**merge commit**, merge par l'utilisateur)
- [ ] En prod (par l'utilisateur, vraie boîte mail) : inscription → mail Resend →
      confirmation → connexion → mot de passe oublié → suppression du compte de test.
      Si aucun mail n'arrive : vérifier le domaine dans Resend (case non cochée du plan
      de déploiement).

### Verification Plan
- CI de la PR verte (backend, frontend, Playwright)
- Après release : `curl -s https://ageguessr.app/api/health` → `OK`, et le parcours
  manuel ci-dessus validé par l'utilisateur

### Phase Summary
_(à écrire une fois la phase terminée)_

## Edge cases à couvrir

- Pseudo : casse (`Max` vs `max` = même pseudo), longueur 2 / 21, caractères
  interdits (espace, `@`, emoji, accents), espaces autour (refusés, pas de trim
  silencieux), changer pour son propre pseudo avec une autre casse.
- Inscription : email et pseudo pris en même temps → l'erreur email d'abord (ordre
  stable, testé).
- Reset : token expiré, déjà utilisé, inconnu ; deux demandes successives (les deux
  liens valides jusqu'à l'utilisation de l'un, qui invalide l'autre) ; reset d'un
  compte verrouillé (verrou levé) ou non vérifié (devient vérifié) ; email inconnu →
  même réponse qu'un email connu ; lien « secure your account » utilisé après 2 h
  (valide) ; attaquant qui change le mot de passe deux fois → le premier lien
  « secure » du propriétaire marche toujours.
- Changement de mot de passe : mauvais mot de passe actuel ; pas de cookie refresh
  (révoquer toutes les sessions) ; nouveau mot de passe < 8 caractères.
- Suppression : mauvais mot de passe ; demande sans confirmation → compte intact ;
  token de suppression utilisé comme token de reset (et inversement) → refusé ; JWT
  encore valide après suppression → `/me` 404, pas de 500 ; cookie refresh effacé ;
  confirmation ouverte sur un autre appareil non connecté → fonctionne.
- Frontend : rechargement direct de `/account` connecté (pas de redirection parasite),
  `/account` en invité → `/login`, `/login` connecté → `/`, `/reset-password` sans
  `?token=`.

## Final Recap
_(à écrire une fois toutes les phases terminées)_

## Deployment Plan
_(à écrire une fois toutes les phases terminées)_

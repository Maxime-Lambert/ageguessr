# Politique de mot de passe : 12 caractères minimum + mots de passe interdits

Tout mot de passe **choisi** (inscription, réinitialisation, changement) doit faire au
moins 12 caractères. Il ne doit figurer ni dans une liste de mots de passe courants issus
de fuites, ni contenir le nom du site, le pseudo ou l'email de l'utilisateur. Pas de règle
de composition. La connexion et la confirmation par mot de passe actuel ne changent pas :
les comptes existants avec un mot de passe de 8 à 11 caractères continuent de fonctionner.

## Contexte pour repartir à froid
- Branche : `feature/password-policy` (depuis `develop`).
- Règle actuelle : `shared/validation/Password.java` (`@NotBlank @Size(min=8)
  @MaxUtf8Bytes(72)`, `@ReportAsSingleViolation`), posée sur `RegisterCommand.password`,
  `ResetPasswordCommand.newPassword`, `ChangePasswordCommand.newPassword`. Miroir front :
  `frontend/src/app/features/auth/password-rules.ts` (`NEW_PASSWORD_VALIDATORS`).
- Le plan précédent (finalisation de l'auth) est archivé dans `plans/done/auth-completion.md`.

## Décisions validées avec l'utilisateur (2026-10-20)
- **12 caractères minimum, sans règle de composition.** Sources :
  - NIST SP 800-63B rév. 4 (2025) : pas de composition imposée ; 15 caractères minimum
    quand le mot de passe est le seul facteur ; vérification contre une liste de mots
    de passe compromis ou courants et contre les mots propres au contexte (nom du
    service, identifiant).
  - CNIL, recommandation de 2022 : environ 50 bits d'entropie suffisent quand l'accès
    est freiné après des échecs (c'est notre cas : verrouillage du compte et limite de
    tentatives). Sans composition imposée, on compte un alphabet de 26 lettres : 11
    caractères donnent ~52 bits. 12 laisse une marge.
  - 15 serait trop dissuasif pour un jeu grand public. À réévaluer quand le TOTP
    (roadmap) existera.
- **Liste locale** plutôt que l'API Have I Been Pwned : aucune dépendance réseau ni
  tiers dans le parcours d'inscription.

## Décisions d'architecture de cette feature
- **Source de la liste** : `Pwdb_top-1000000.txt` de SecLists
  (github.com/danielmiessler/SecLists, licence MIT), classée par fréquence dans les
  fuites. On ne garde que les entrées d'au moins 12 caractères : une entrée plus courte
  est déjà refusée par la longueur. On les passe en minuscules et on déduplique, ce qui
  donne **≈ 29 950 entrées, ≈ 427 Ko**.
  - Fichier versionné : `ageguessr-api/src/main/resources/security/common-passwords.txt`.
  - Licence MIT et commande de régénération dans
    `ageguessr-api/src/main/resources/security/README.md`.
  - Le top 10 millions filtré ferait 700 000 entrées (10 Mo dans le repo, ~60 Mo de
    tas) : coût disproportionné pour des mots de passe rarement essayés en premier.
- **Comparaison** : insensible à la casse. Le mot de passe passe en minuscules
  (`Locale.ROOT`), puis on cherche une correspondance exacte dans un `Set` chargé une
  fois au démarrage. Sans ça, `Qwerty123456` contournerait `qwerty123456`.
- **Mots du contexte** : on refuse un mot de passe dont la version en minuscules
  **contient** :
  - `ageguessr` ;
  - le pseudo ;
  - la partie de l'email avant le `@`.

  Pour le pseudo et l'email, la règle ne s'applique qu'à partir de 4 caractères :
  sinon un pseudo « max » interdirait tout mot de passe contenant « max ».
- **Où vit la règle** : `features/auth/PasswordPolicy.java`, un `@Component` à la racine
  de la feature, comme `CurrentPasswordVerifier`. Il expose
  `check(String password, String username, String email)`.
  - Elle n'est pas en Bean Validation, parce que les mots du contexte demandent
    l'utilisateur. Au reset et au changement, il n'est connu qu'une fois chargé par le
    handler.
  - Les règles de format (longueur min et max) restent dans `@Password`, en Bean
    Validation, côté front comme côté back.
- **Code d'erreur** : `ValidationException` gagne un `errorCode`, sur le modèle de
  `ConflictException`. Le constructeur actuel `(message)` garde `VALIDATION_ERROR`.
  `PasswordPolicy` lève `PASSWORD_TOO_COMMON` (liste ou nom du site) ou
  `PASSWORD_CONTAINS_PERSONAL_INFO` (pseudo ou email). `GlobalExceptionHandler` renvoie
  le code en 400, et le front affiche un message précis sous le champ.
- **Ordre dans les handlers** :
  - Register : la politique passe **avant** les vérifications d'unicité, pour ne pas
    révéler un email pris à quelqu'un dont le mot de passe est de toute façon refusé.
  - Reset : après la validation du token. Sans token valide, on ne connaît pas
    l'utilisateur.
  - Change : **après** `CurrentPasswordVerifier`. Un attaquant qui ne connaît pas le
    mot de passe actuel n'obtient aucune information, et l'échec compte toujours dans
    le verrouillage.
  - Dans tous les cas, l'appel se fait avant le hachage bcrypt et toute écriture.
- **Front** : `Validators.minLength(12)` et indice « At least 12 characters. Avoid common
  passwords. ». La liste n'est **pas** envoyée au navigateur (427 Ko pour un contrôle que
  le backend fait déjà).

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Phase 1 : Backend
Status: Complete

- [x] `ageguessr-api/src/main/resources/security/common-passwords.txt` (généré, une
      entrée par ligne) + `security/README.md` (source, licence MIT, commande :
      `awk 'length($0)>=12' Pwdb_top-1000000.txt | tr A-Z a-z | sort -u`)
- [x] `shared/exceptions/ValidationException.java` : `errorCode` (constructeur
      `(message)` → `VALIDATION_ERROR`) ; `config/GlobalExceptionHandler.java` le renvoie
- [x] `shared/validation/Password.java` : `@Size(min = 12)` + message et Javadoc
- [x] `features/auth/PasswordPolicy.java` : chargement de la liste au démarrage (échec
      au démarrage si le fichier manque, plutôt que d'accepter tous les mots de passe
      sans rien dire), `check(password, username, email)`
- [x] Appels dans `Register/RegisterHandler`, `ResetPassword/ResetPasswordHandler`,
      `ChangePassword/ChangePasswordHandler` selon l'ordre ci-dessus
- [x] Mots de passe des tests passés à ≥ 12 caractères hors liste (`tortoise-violin-42`,
      `old-/new-tortoise-violin`, etc.), remplacés sur place plutôt que via une constante
      `AuthFlows` : chaque test garde des valeurs distinctes et lisibles
- [x] `docs/decisions/architecture.md` : section « Politique de mot de passe » (sources
      NIST/CNIL, choix de la liste, pourquoi pas de composition)

**Tests**
- Unitaire : `PasswordPolicyTest` couvre :
  - entrée de la liste, y compris avec une autre casse ;
  - `ageguessr` dans le mot de passe ;
  - pseudo et partie locale de l'email contenus, y compris avec une autre casse ;
  - pseudo de 3 caractères ignoré ;
  - mot de passe sûr accepté ;
  - la liste chargée n'est pas vide.

  Handlers : la politique est appelée avant l'encodeur et le repository (Register) ;
  après le vérificateur du mot de passe actuel (Change).
- Fonctionnel : `RegisterFunctionalTest`, `PasswordResetFunctionalTest`,
  `ChangePasswordFunctionalTest` vérifient :
  - 11 caractères → 400 `VALIDATION_ERROR` ;
  - mot de passe de la liste → 400 `PASSWORD_TOO_COMMON` ;
  - mot de passe contenant le pseudo → 400 `PASSWORD_CONTAINS_PERSONAL_INFO`.

  `LoginFunctionalTest` : un compte créé avec un mot de passe de 8 caractères
  (inséré directement en base, l'API le refuserait désormais) se connecte toujours.
- Intégration : aucun nouveau. Pas de nouvelle requête ni de migration.
- Architecture : les tests ArchUnit existants couvrent l'emplacement de
  `PasswordPolicy`. Pas de nouvelle règle.
- Mutation : PIT sur `PasswordPolicy` si la config le permet, sinon ce sont les tests
  unitaires ci-dessus qui tuent les mutants évidents (bornes 4 et 12, casse).

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → BUILD SUCCESS

### Phase Summary
Terminée le 2026-10-20. `./mvnw test` : 156 tests, 0 échec (133 avant).
- Liste : 29 954 entrées, 427 Ko (après la correction de la phase 3).
- Les handlers reçoivent `PasswordPolicy` par injection. Leurs tests unitaires le
  mockent ; `PasswordPolicyTest` utilise une liste d'une entrée, plus un test sur la
  vraie liste.
- Fonctionnel ajouté au-delà du plan : au reset, un refus laisse le lien utilisable
  (la transaction est annulée, la consommation du token aussi). À l'inscription, un mot
  de passe courant sur un email déjà pris renvoie 400, pas 409.
- PIT non lancé (pas de config ciblée) ; les bornes 4/12 et la casse sont couvertes par
  les tests unitaires.

## Phase 2 : Frontend
Status: Complete

- [x] `password-rules.ts` : `minLength(12)` ; `PASSWORD_HINT`, et messages pour
      `PASSWORD_TOO_COMMON` et `PASSWORD_CONTAINS_PERSONAL_INFO`
- [x] `register`, `reset-password`, `account` (changement de mot de passe) : indice mis à
      jour, message précis sous le champ pour les deux codes d'erreur (comme
      `USERNAME_TAKEN`), effacé à la saisie
- [x] Mots de passe des specs Vitest, Playwright `e2e/component` et `e2e/journeys`
      (`support.ts`) passés à ≥ 12 caractères hors liste

**Tests**
- Unitaire (Vitest) : 11 caractères → pas d'envoi ; réponse 400 `PASSWORD_TOO_COMMON`
  → message sous le champ (register, reset, account).
- Interface (Playwright) : register affiche le message « too common » sur un 400 mocké.
- QA e2e : les 4 parcours existants relancés en local (ils couvrent register, reset,
  change). Pas de nouveau parcours : aucun nouvel écran.

### Verification Plan
- `cd frontend && pnpm test` → tous verts
- `cd frontend && pnpm exec playwright test --project=component` → tous verts
- Parcours en local (Postgres 5434 / Redis 6381 jetables, backend profil `e2e`) :
  `pnpm test:e2e:journeys` → 4 verts

### Phase Summary
Terminée le 2026-10-20.
- `pnpm test` : 85 tests verts (77 avant). Playwright interface : 27 verts. Les 4
  parcours complets passent en local.
- Essai direct sur le backend local :
  - `qwerty123456` → `PASSWORD_TOO_COMMON` ;
  - `elevenchars` → `VALIDATION_ERROR` ;
  - mot de passe contenant le pseudo → `PASSWORD_CONTAINS_PERSONAL_INFO` ;
  - `tortoise-violin-42` → 201.
- Ajouts au-delà du plan :
  - l'indice passe en rouge si le champ est quitté avec moins de 12 caractères ;
  - `autocomplete="new-password"` sur le mot de passe de l'inscription, pour que les
    gestionnaires de mots de passe proposent d'en générer un.
- Attention : la PR #9 (curseur des boutons), encore ouverte, ajoute un test Playwright
  avec `password123`. La nouvelle règle bloque désormais ce mot de passe côté
  formulaire, donc ce test devra être corrigé quand l'une des deux branches sera
  rebasée sur l'autre.

## Phase 3 : Relectures, PR
Status: Complete

- [x] `code-reviewer` + `architecture-reviewer` sur le diff, corrections appliquées
      (2026-10-20). Aucun point bloquant. Corrigé :
  - la liste filtrait en octets (`LC_ALL=C awk`) : 28 entrées cyrilliques de moins de
    12 caractères passaient. Régénérée en comptant les caractères (29 954 entrées),
    commande du `README.md` corrigée ;
  - normalisation Unicode NFC du mot de passe, des mots du contexte et de la liste, pour
    qu'un « é » décomposé corresponde à un « é » précomposé (+ test) ;
  - `RegisterHandlerTest` : derniers `password123` remplacés.

  Laissé tel quel : le bloc d'indice sous le champ est répété dans 3 formulaires. Chacun
  a ses propres erreurs, donc un composant `<app-password-field>` ne se justifiera qu'au
  4e formulaire.
- [x] `docs/roadmap.md` à jour
- [x] PR `feature/password-policy` → `develop` (#10), release `develop` → `main` (#11,
      merge commit). Déployé le 2026-10-20 ; en prod, le JS servi affiche l'indice de 12
      caractères et `qwerty123456` à l'inscription renvoie 400 `PASSWORD_TOO_COMMON`

### Verification Plan
- CI de la PR verte

### Phase Summary
Relectures faites et corrections appliquées. `./mvnw test` : 157 tests, 0 échec ;
`pnpm test` : 85 tests verts. PR ouverte vers `develop`.

## Edge cases à couvrir
- Casse : `QWERTY123456` refusé comme `qwerty123456`.
- Pseudo court (`max`) : n'interdit pas les mots de passe contenant « max ».
- Email `a@x.com` : partie locale trop courte, ignorée.
- Unicode : 12 caractères accentués acceptés (24 octets, sous la limite de 72).
- Comptes existants à 8-11 caractères : connexion et suppression du compte (mot de passe
  actuel) inchangées. Seul un **nouveau** mot de passe suit la nouvelle règle.
- Changement de mot de passe avec un mot de passe actuel faux et un nouveau mot de
  passe courant : 403 `INVALID_CURRENT_PASSWORD` (le mot de passe actuel est vérifié
  d'abord), pas 400.

## Final Recap
Un nouveau mot de passe doit faire au moins 12 caractères, sans règle de composition.
Il est refusé s'il figure parmi les ~30 000 mots de passe courants (top 1 million de
Pwdb, filtré à 12 caractères et plus) ou s'il contient `ageguessr`, le pseudo ou la
partie de l'email avant le `@`. Le frontend affiche un message précis sous le champ. La
connexion ne change pas, donc les comptes existants fonctionnent toujours.

## Deployment Plan
- Aucune migration ni variable d'environnement. La liste est embarquée dans le jar
  (≈ 427 Ko, quelques Mo de tas une fois chargée).
- Le déploiement suit le chemin habituel : merge dans `develop` par l'utilisateur, puis
  release `develop` → `main` en **merge commit**, ce qui lance la CI et le déploiement.
- Smoke test après déploiement : un `POST /api/auth/register` avec `qwerty123456`
  renvoie 400 `PASSWORD_TOO_COMMON`.

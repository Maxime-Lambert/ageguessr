# Site en français et en anglais (i18n)

Le site existe en deux versions, `/en/` et `/fr/`, et le joueur passe de l'une à l'autre.
La langue d'un compte est enregistrée, et ses emails partent dans cette langue. La
langue des indices se choisit à part, sur l'écran de jeu. Les pages d'administration
restent en anglais. On continue ensuite à développer avec l'i18n en tête : aucun texte
visible par un joueur n'est écrit en dur dans une seule langue.

## For Future Agents
Au fil du travail : cocher les cases `- [x]`. Quand une phase est finie, passer son
statut à `Complete` et écrire son **Phase Summary**. Lancer le **Verification Plan** de
la phase et noter le résultat avant de passer à la suivante. Ce fichier est la source de
vérité, pas la conversation.

## Contexte pour repartir à froid
- Branche `feature/i18n`, créée depuis `develop` après la release #31 (sans upstream).
- Aujourd'hui, l'UI est entièrement en anglais (`design.md`, « Anglais, ton direct »).
  Les pages légales contiennent les deux langues dans un même template, choisies par
  `?lang=fr` (`legal-language.ts`, `language-switch.ts`). La langue des indices vient de
  `navigator.language` (`browserLanguage()` dans `game-api.ts`), sans aucun réglage :
  c'est le problème qui a lancé cette feature.
- Le front est construit par `Dockerfile.edge` (`pnpm build` → `/srv`) et servi par
  Caddy (`Caddyfile` : `try_files {path} /index.html`). `/api/*` part vers le backend.
- Emails : `AccountEmails` (réinitialisation, alerte de changement de mot de passe,
  suppression) et `EmailVerificationService`. Ils sont en anglais, avec des liens
  `app.frontend-url + chemin`.
- Langue du jeu : enum `features/game/Language` (`EN`, `FR`, `audiences()` → `HintAudience`),
  stockée dans `game_plays.language`.

## Décisions validées avec l'utilisateur (2026-11-02)
- **Site entier en FR et EN**, et la langue se change depuis le site. L'i18n est
  désormais une règle de développement.
- **i18n natif d'Angular, une URL par langue** : `/en/…` et `/fr/…`, une build par
  langue. C'est meilleur pour le référencement. Changer de langue recharge la page.
- **Langue enregistrée sur le compte** : migration V14, emails dans la langue du compte.
  Sans compte : langue du navigateur, retenue sur l'appareil.
- **Langue des indices** : un sélecteur sur l'écran de jeu. Par défaut, elle suit la
  langue du site.
- **Administration** (`/admin/curation`, `/admin/hints`) : reste en anglais.

## Décisions d'implémentation (validées le 2026-11-02)
- **`@angular/localize`** :
  - langue source `en`, traduction `fr` ;
  - catalogue au **format XLIFF** (`src/locale/messages.fr.xlf`), le format par défaut
    d'`ng extract-i18n` et le standard des outils de traduction : l'utilisateur veut les
    choix standard d'Angular ;
  - `i18nMissingTranslation: "error"` : une traduction manquante casse la build, donc
    la CI ;
  - textes des templates marqués `i18n` avec un **id explicite et stable**
    (`@@game.round.question`), pour qu'une retouche de l'anglais ne fasse pas perdre la
    traduction ; textes TS en `$localize`.
- **Build** :
  - `pnpm build` produit `dist/frontend/browser/en/` et `…/fr/` (`localize: true`,
    `baseHref` `/en/` et `/fr/`). Angular pose alors `<html lang>` tout seul ;
  - en dev, `pnpm start` sert l'anglais et `pnpm start:fr` le français, car `ng serve`
    ne sert qu'une langue à la fois.
- **Caddy** :
  - `/en/*` et `/fr/*` servent chacune sa build (`try_files` vers son propre
    `index.html`) ;
  - toute autre URL hors `/api/*`, `/` comprise, redirige en **302** vers
    `/<langue><chemin>`. La langue vient d'abord du cookie `ageguessr_lang`, puis de
    `Accept-Language` (`fr…` → `fr`), sinon `en` ;
  - la redirection porte `Cache-Control: no-store` et `Vary: Cookie, Accept-Language` :
    Cloudflare ne doit pas servir à tous la redirection d'un seul visiteur ;
  - les anciens liens (`/terms`, `/privacy?lang=fr`, liens d'emails déjà envoyés)
    continuent ainsi de marcher. `?lang=fr` force `/fr/`.
- **Sélecteur de langue du site** dans l'en-tête, avec `hreflang` :
  - lien vers la même page dans l'autre préfixe ;
  - il écrit le cookie `ageguessr_lang` (1 an, `SameSite=Lax`, lisible par le script :
    c'est un choix de l'utilisateur, exempt de consentement, listé sur `/privacy`) ;
  - si le joueur est connecté, il enregistre la langue sur le compte avant de changer
    de page.
- **Joueur connecté** : la langue du compte gagne. Après la connexion ou la
  restauration de session, si la langue du compte diffère de la page, le front recharge
  la même page dans l'autre préfixe (et met le cookie à jour).
- **Langue des indices** :
  - sélecteur FR / EN sur l'écran de jeu, retenu dans le stockage local
    (`ageguessr-hint-language`) ;
  - par défaut, la langue du site (`LOCALE_ID`), à la place de `navigator.language` ;
  - elle s'applique au prochain 1er essai : la langue d'une manche commencée reste
    fixée (`game_plays.language`), pour que l'indice déjà donné ne change pas.
- **Langue partagée** : `shared/Language` (`EN`, `FR`), utilisée par `auth` (compte) et
  `game` (manche). Les features ne dépendent pas l'une de l'autre, et `shared/` ne
  dépend d'aucune feature. Le lien vers `HintAudience` passe donc de l'enum à
  `HintPicker`. Les valeurs en base ne changent pas (`EN` / `FR`).
- **Backend** :
  - V14 : `users.language VARCHAR(2)` **nullable**, avec un `CHECK` (`EN` ou `FR`). NULL
    veut dire « jamais choisie » : c'est le cas du compte existant. Le front enregistre
    alors la langue de la page (venue du navigateur) à la restauration de session. Les
    emails d'un compte à NULL partent en anglais ;
  - `POST /api/auth/register` accepte `language` (facultatif, NULL sinon, pour un
    client plus ancien) ;
  - `ProfileResponse` renvoie `language` ;
  - nouveau `PUT /api/auth/me/language {language}` (authentifié) ;
  - les 4 emails existent en FR et EN, selon la langue du compte, et leurs liens portent
    le préfixe (`<frontend-url>/fr/reset-password?token=…`).
- **Messages d'erreur** : le front n'affiche jamais le `message` de l'API, seulement ses
  propres textes traduits, choisis par `errorCode`. C'est à vérifier écran par écran,
  et à corriger là où ce n'est pas le cas.
- **Pages légales** : on garde les deux versions déjà rédigées, mais le template choisit
  selon `LOCALE_ID` au lieu de `?lang`. `LanguageSwitch` et `legal-language.ts`
  disparaissent au profit du sélecteur global. Les dates légales suivent `LOCALE_ID`.
- **Voix « enquête » en français**, dans `game-vocabulary.ts` en `$localize` :

  | Notion | Enquête | Repli neutre |
  |---|---|---|
  | une personne | *Affaire* | *Manche* |
  | la partie du jour | *Le dossier du jour* | *La partie du jour* |
  | catégorie d'indice | *Piste* | *Indice* |
  | résultat | *Affaire classée* | *Résultat* |
  | récapitulatif | *Rapport d'enquête* | *Récapitulatif* |

- **Hors scope** : sitemap et balises `<link rel="alternate">` dans le `<head>` de
  chaque page (le sélecteur porte déjà `hreflang`), traduction de l'administration,
  troisième langue.

## Fichiers
Backend (`ageguessr-api/src/main/…`) :
- à créer :
  - `resources/db/migration/V14__add_user_language.sql` ;
  - `java/app/ageguessr/shared/Language.java` ;
  - `features/auth/ChangeLanguage/` (`…Command`, `…Controller`, `…Handler`) ;
  - textes des emails FR / EN (`features/auth/EmailTexts.java` ou équivalent).
- à modifier :
  - `features/game/Language.java`, à supprimer au profit de `shared/Language` ;
  - `HintPicker` (audiences), `GamePlay`, les commandes du jeu ;
  - `features/auth/User.java`, `ProfileResponse`, `Register/RegisterCommand` +
    handler ;
  - `AccountEmails`, `EmailVerificationService` ;
  - `ArchitectureTests`, si une règle doit nommer `shared`.

Frontend (`frontend/`) :
- `package.json` (`@angular/localize`, scripts `start:fr`, `extract-i18n`),
  `angular.json` (`i18n`, `localize`, configuration `fr`), `src/main.ts` / setup des
  tests (`@angular/localize/init`)
- `src/locale/messages.xlf` (extrait) + `src/locale/messages.fr.xlf` (traduit)
- tous les templates et TS non admin : `layout/header`, `layout/footer`,
  `auth/*` (register, login, check-email, confirm-email, forgot-password,
  reset-password, account, confirm-account-deletion, password-rules), `game/*`
  (`game-vocabulary.ts` en `$localize`), `legal/*` (choix par `LOCALE_ID`),
  `theme-toggle`, `app.routes.ts` (titres)
- à créer : `features/i18n/` (`site-language.ts` : langue courante, URL de l'autre
  langue, cookie ; `language-switch/` dans l'en-tête), sélecteur de langue des indices
  dans `features/game`
- `auth.ts` : `language` à l'inscription, appel au changement de langue, redirection
  vers la langue du compte
- `playwright.config.ts` : `baseURL` en `/en/`, un second serveur de dev en `fr` pour
  les tests FR
- `privacy.html` (+ date) : cookie `ageguessr_lang`, stockage
  `ageguessr-hint-language`, langue du compte

Infra et docs :
- `Dockerfile.edge` (copie des deux builds), `Caddyfile` (locales + redirection)
- `.github/workflows/ci.yml` : la build localisée tourne dans `frontend-test`, pour
  qu'une traduction manquante bloque la PR
- `docs/decisions/architecture.md` (section i18n), `docs/decisions/design.md` (bilingue,
  voix enquête en FR), `docs/testing-strategy.md`, `docs/roadmap.md`, `CLAUDE.md`
  (règle : tout texte joueur passe par l'i18n)

## Phase 1 : Backend (langue du compte, emails)
Status: Complete

- [x] `shared/Language` ; migrer `game` dessus (`HintPicker` fait le lien avec
      `HintAudience`)
- [x] V14 `users.language` + entité `User`
- [x] Inscription avec `language` ; `ProfileResponse.language`
- [x] `PUT /api/auth/me/language`
- [x] Emails FR / EN (sujet + corps), liens avec le préfixe de la langue
- [x] Tests :
  - unitaires : textes des emails dans les deux langues, liens préfixés, handler de
    changement de langue ;
  - fonctionnels : inscription en `FR` puis email reçu en français, changement de langue
    puis profil, langue invalide en 400, sans jeton en 401 ;
  - intégration : `CHECK` de V14, NULL accepté ;
  - architecture : `shared` ne dépend d'aucune feature (règle existante ou nouvelle).

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert

### Phase Summary
- **`shared/i18n/Language`** (`EN`, `FR`, `code()` → `en` / `fr`, `DEFAULT = EN`,
  `orDefault`). `features/game/Language` est supprimé. Le lien avec les publics
  d'indices est maintenant `HintPicker.audiences(Language)`, et `RoundReveal` utilise
  `language.code()` pour le lien Wikipedia.
- **V14** : `users.language` nullable, avec un `CHECK` (`users_language`). Champ
  `User.language`, et `ProfileResponse.language` (null tant que la langue n'est pas
  choisie).
- **Inscription** : `RegisterCommand.language`, facultatif. Une valeur inconnue donne
  un 400 (Jackson).
- **`PUT /api/auth/me/language`** (`features/auth/ChangeLanguage/`), authentifié. PUT,
  car la langue se pose en entier et le front peut la renvoyer sans effet de bord.
  Elle compte dans la limite `/api/auth/` (20 par minute), comme le reste du compte.
- **Emails** :
  - `AccountEmailTexts` regroupe les 4 textes en FR (vouvoiement) et en EN ;
  - `AccountLinks` construit `<frontend-url>/<en|fr><chemin>?token=…` ;
  - `AccountEmails` et `EmailVerificationService` les utilisent ;
  - un compte sans langue reçoit l'anglais et des liens `/en/`.
- **ArchUnit** : nouvelle règle `shared_does_not_depend_on_features`.
- **Tests** :
  - `AccountEmailsTest` : 5 tests, dont 2 en français ;
  - `RegisterHandlerTest` vérifie la langue enregistrée ;
  - `AccountLanguageFunctionalTest` (5) : inscription en FR et email français,
    inscription sans langue (NULL et anglais), changement de langue puis email suivant
    en français, 400, 401 ;
  - `UserRepositoryIntegrationTest` (+2) : NULL et FR gardés, `DE` refusé.
  - `./mvnw test` : **908 verts**.

## Phase 2 : Socle i18n du front
Status: Not started

- [ ] `@angular/localize`, `angular.json` (source `en`, `fr` → `messages.fr.xlf`,
      `localize`, `baseHref`, `i18nMissingTranslation: error`), scripts
- [ ] `features/i18n/site-language.ts` : langue courante (`LOCALE_ID`), URL de la même
      page dans l'autre langue, cookie `ageguessr_lang`
- [ ] Sélecteur de langue dans l'en-tête (enregistre aussi sur le compte si connecté)
- [ ] Redirection vers la langue du compte après la connexion ou la restauration de
      session
- [ ] Langue des indices : sélecteur sur l'écran de jeu, stockage local, défaut = langue
      du site ; `browserLanguage()` supprimé
- [ ] Dates via `LOCALE_ID` (pages légales)
- [ ] Specs Vitest : langue courante et URL de l'autre langue, cookie, sélecteur (avec et
      sans compte), redirection vers la langue du compte, langue des indices (défaut,
      choix retenu, appliqué au prochain 1er essai)

### Verification Plan
- `cd frontend && pnpm test && pnpm lint && pnpm format:check` → vert
- `pnpm build` → `dist/frontend/browser/en` et `…/fr` présents

### Phase Summary
_(à écrire à la fin de la phase)_

## Phase 3 : Traduction des écrans joueur
Status: Not started

- [ ] Marquer tous les textes non admin (`i18n` avec id `@@…`, `$localize` en TS) :
      en-tête, pied de page, auth, compte, jeu, thème, titres des routes, messages
      d'erreur
- [ ] `game-vocabulary.ts` en `$localize`, voix enquête et repli neutre dans les deux
      langues
- [ ] Pages légales : choix de la version par `LOCALE_ID`, suppression de `?lang` et de
      `LanguageSwitch`
- [ ] Vérifier qu'aucun écran n'affiche le `message` de l'API
- [ ] `messages.fr.xlf` complet, **vouvoiement** (l'utilisateur corrigera au cas par cas
      ce qui ne sonne pas naturel), voix enquête gardée en français
- [ ] Specs existantes mises à jour (elles tournent dans la langue source)

### Verification Plan
- `pnpm test`, lint, format → vert ; `pnpm build` sans traduction manquante
- `pnpm start:fr` : chaque écran joueur en français, aucun reste d'anglais hors admin

### Phase Summary
_(à écrire à la fin de la phase)_

## Phase 4 : Déploiement des deux langues (Caddy, image, CI)
Status: Not started

- [ ] `Dockerfile.edge` : les deux builds dans `/srv/en` et `/srv/fr`
- [ ] `Caddyfile` : `handle /en/*`, `handle /fr/*`, redirection 302 du reste (cookie,
      puis `Accept-Language`, sinon `en`), `?lang=fr`, `no-store` + `Vary`
- [ ] Script `scripts/check-edge-locales.sh` : construit l'image edge, la lance et
      vérifie les redirections et les deux `index.html` en `curl`
- [ ] CI : build localisée dans `frontend-test`

### Verification Plan
- `scripts/check-edge-locales.sh` → tous les cas OK :
  - `/` sans rien → `/en/` ;
  - `/` avec `Accept-Language: fr-FR` → `/fr/` ;
  - cookie `ageguessr_lang=en` + `Accept-Language: fr` → `/en/` ;
  - `/privacy?lang=fr` → `/fr/privacy` ;
  - `/fr/account` → `index.html` français ;
  - `/api/health` passe toujours au backend.

### Phase Summary
_(à écrire à la fin de la phase)_

## Phase 5 : Parcours, documentation, reviews
Status: Not started

- [ ] Playwright component : `baseURL` en `/en/`, projet `component-fr` sur un second
      serveur de dev ; cas FR (écran de jeu, sélecteur de langue, sélecteur d'indices)
- [ ] Journeys : liens d'emails préfixés, inscription en français jusqu'à l'email
      français
- [ ] `/privacy` FR + EN : cookie `ageguessr_lang`, stockage `ageguessr-hint-language`,
      langue du compte ; date mise à jour
- [ ] Docs : `architecture.md` (i18n), `design.md` (bilingue, voix enquête FR),
      `testing-strategy.md`, `roadmap.md`, `CLAUDE.md` (règle i18n)
- [ ] `architecture-reviewer` et `code-reviewer` (cookie, redirections, données de
      compte), puis appliquer leurs retours

### Verification Plan
- `./mvnw test`, `pnpm test`, lint, format, `pnpm build` → vert
- Playwright component (`en` et `fr`) et journeys → vert

### Phase Summary
_(à écrire à la fin de la phase)_

## Tests : catégories retenues
- **Unitaire backend** : oui, pour les textes et liens des emails et le changement de
  langue.
- **Intégration** : oui, pour la contrainte de V14.
- **Fonctionnel** : oui, pour l'inscription en FR, le changement de langue et les
  erreurs 400 / 401.
- **Unitaire front** : oui, pour la langue courante, les sélecteurs, la redirection
  vers la langue du compte et la langue des indices. Les specs tournent dans la langue
  source (anglais) : la traduction elle-même est vérifiée par la build
  (`i18nMissingTranslation: error`), pas par des specs.
- **Interface (Playwright)** : oui, avec un projet FR. C'est le seul endroit qui voit
  la build française et le rechargement au changement de langue.
- **QA e2e** : oui, pour l'inscription en français jusqu'à l'email français : le
  parcours critique de l'i18n côté compte.
- **Architecture** : oui, pour `shared/Language` (`shared` ne dépend d'aucune feature).
- **Mutation** : pas de cible nouvelle. La logique ajoutée est surtout de la
  configuration et des textes.
- **Infra** : script `check-edge-locales.sh`, car la redirection Caddy n'est couverte
  par aucun test applicatif.

## Edge cases
- Ancien lien sans préfixe (`/terms`, lien d'email envoyé avant la release) : redirection
  vers la langue du visiteur, avec le chemin et les paramètres conservés (`?token=`).
- `?lang=fr` sur une ancienne URL de page légale : `/fr/…`.
- Cookie `ageguessr_lang` avec une valeur inconnue : ignoré.
- Connecté avec un compte en FR sur `/en/…` : rechargement en `/fr/…` après la
  restauration de session, sans boucle (le cookie est mis à jour avant).
- Changement de langue du site en cours de manche : la manche garde sa langue
  d'indice. Le sélecteur d'indices garde son propre choix.
- Changement de langue alors que l'enregistrement sur le compte échoue : la page change
  quand même. Le compte sera corrigé au prochain changement, et un message le signale.
- Compte existant (créé avant V14) : langue NULL. La langue de la page est enregistrée à
  la première restauration de session ; d'ici là, ses emails partent en anglais.
- Nom révélé et lien Wikipedia : ils suivent la langue de la manche, comme
  aujourd'hui.
- Pages admin dans la build FR : en anglais, sous `/fr/admin/…`.

## Final Recap
_(à écrire quand toutes les phases sont terminées)_

## Deployment Plan
_(à écrire quand toutes les phases sont terminées)_

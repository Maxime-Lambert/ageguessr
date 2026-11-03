# Ré-acceptation des CGU

Quand les CGU changent (`TermsVersion.CURRENT`), un joueur connecté qui a accepté une
version antérieure voit un bandeau non bloquant qui l'invite à lire et accepter la
nouvelle version. L'acceptation est enregistrée (version + date). Branche
`feature/terms-reacceptance`, une PR vers `develop`.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions (validées avec l'utilisateur le 2026-11-03)

- **Bandeau non bloquant**, sous l'en-tête, sur tout le site, pour un compte dont
  `terms_version` ≠ `TermsVersion.CURRENT` (NULL compris : comptes d'avant V3). Il
  contient la date de la nouvelle version, un lien vers `/terms`, « Accepter », et
  « Plus tard » qui le masque pour la session (`sessionStorage`). Il rappelle qu'un refus
  se traduit par la suppression du compte, depuis `/account` (ce que disent déjà les CGU,
  article 9).
- **Front seulement** : l'API n'interdit rien à un compte en retard. Elle expose
  `termsAccepted` dans `ProfileResponse` (`/api/auth/me` et chaque réponse de profil) et
  enregistre l'acceptation.
- **Accepter une version précise** : `PUT /api/auth/me/terms` avec `{ "version": "…" }`.
  Si la version envoyée n'est pas `CURRENT` (CGU changées entre l'affichage et le clic),
  409 `TERMS_VERSION_OUTDATED` : le front recharge le profil, le bandeau reste. PUT : accepter
  deux fois la même version ne change rien, sauf la date d'acceptation qui n'est pas
  réécrite (preuve de la première acceptation).
- **Pas d'email** pour cette PR : le bandeau est « l'information » prévue par les CGU. Un
  email aux comptes à chaque changement important reste possible plus tard (hors scope).
- **Rangement** : use case `features/auth/AcceptTerms/` ; bandeau dans
  `frontend/src/app/features/auth/terms-banner/`, inséré dans `app.html`.

### Hypothèses
- Un changement mineur (coquille) ne change pas `TermsVersion` : la règle « bump à
  chaque modification » devient « bump à chaque modification importante », écrite dans
  `TermsVersion` et `legal-dates.ts`.
- La politique de confidentialité n'est pas concernée (aucune nouvelle donnée : les
  colonnes existent depuis V3).

## Phase 1: Backend
Status: Not started

- [ ] `ProfileResponse` : `termsAccepted` (`TermsVersion.CURRENT.equals(user.getTermsVersion())`).
- [ ] `User.acceptTerms(String version, Instant now)` : refuse une autre version que
  `CURRENT`, ne réécrit pas la date si déjà acceptée.
- [ ] `features/auth/AcceptTerms/` : `AcceptTermsCommand(@NotBlank @Size(max = 20) version)`,
  `AcceptTermsController` (`PUT /api/auth/me/terms`, authentifié), `AcceptTermsHandler`
  (409 `TERMS_VERSION_OUTDATED`).
- [ ] `TermsVersion` : javadoc « bump à chaque modification importante, qui demande une
  ré-acceptation ».

### Tests
- Unitaire : `AcceptTermsHandlerTest` (version courante enregistrée avec la date, autre
  version → 409, deuxième acceptation garde la date), `ProfileResponse` (NULL et ancienne
  version → false).
- Fonctionnel : `AcceptTermsFunctionalTest` — `/me` d'un compte en retard dit false,
  `PUT` le passe à true, 401 sans jeton, 400 sans version, 409 sur une ancienne version.
- Architecture et mutation : règles existantes, PIT sur le handler à la release.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert.

### Phase Summary
_(write when phase completes)_

## Phase 2: Frontend
Status: Not started

- [ ] `AuthUser.termsAccepted` ; `Auth.acceptTerms(version)` qui met à jour `currentUser`.
- [ ] `features/auth/terms-banner/` : visible si connecté, `termsAccepted` faux et pas
  masqué pour la session ; « Accepter » (envoie `TERMS_VERSION`), « Plus tard », lien
  `/terms`, lien `/account` ; 409 → recharge le profil, autre erreur → message.
- [ ] `app.html` : le bandeau sous `<app-header />`.
- [ ] Textes `$localize` + `messages.fr.xlf`.

### Tests
- Unitaire (Vitest) : affiché ou non selon l'état, « Accepter » envoie la bonne version et
  le fait disparaître, « Plus tard » le masque pour la session, 409 recharge `/me`.
- Interface (Playwright, CI) : `e2e/component/terms-banner.spec.ts` et sa version
  `component-fr` (compte en retard simulé : bandeau, lien vers les CGU, acceptation ;
  tient sur un téléphone).
- Pas de parcours QA e2e : un écran, un appel, déjà couverts par le fonctionnel et
  l'interface.

### Verification Plan
- `cd frontend && pnpm test && pnpm lint && pnpm build` → verts.

### Phase Summary
_(write when phase completes)_

## Phase 3: Docs, revues, PR
Status: Not started

- [ ] `docs/decisions/architecture.md` (« Pages légales et acceptation des CGU »),
  `docs/roadmap.md`.
- [ ] code-reviewer puis architecture-reviewer ; corriger 🔴 et 🟠.
- [ ] PR vers `develop`, CI verte.

### Verification Plan
- Tests backend et frontend verts ; CI de la PR verte.

### Phase Summary
_(write when phase completes)_

## Edge cases
- Compte créé avant V3 (`terms_version` NULL) : bandeau.
- CGU changées pendant que le bandeau est affiché : 409, le profil est rechargé, le
  bandeau reste avec la nouvelle date au prochain chargement du bundle.
- Front ancien (cache) avec un `TERMS_VERSION` en retard sur l'API : 409, pas
  d'acceptation d'une version jamais affichée.
- Déconnexion puis reconnexion dans la même session après « Plus tard » : reste masqué
  (même onglet) ; nouvel onglet : réapparaît.
- Invité : jamais de bandeau.

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

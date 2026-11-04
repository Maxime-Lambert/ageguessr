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
Status: Complete

- [x] `ProfileResponse` : `termsAccepted` (`TermsVersion.CURRENT.equals(user.getTermsVersion())`).
- [x] `User.acceptTerms(String version, Instant now)` : refuse une autre version que
  `CURRENT`, ne réécrit pas la date si déjà acceptée.
- [x] `features/auth/AcceptTerms/` : `AcceptTermsCommand(@NotBlank @Size(max = 20) version)`,
  `AcceptTermsController` (`PUT /api/auth/me/terms`, authentifié), `AcceptTermsHandler`
  (409 `TERMS_VERSION_OUTDATED`).
- [x] `TermsVersion` : javadoc « bump à chaque modification importante, qui demande une
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
`User.hasAcceptedCurrentTerms` / `acceptTerms` (refuse une autre version que `CURRENT`, garde la première date), `ProfileResponse.termsAccepted`, slice `AcceptTerms`. Vérifié : `AcceptTermsHandlerTest`, `AcceptTermsFunctionalTest`, `./mvnw test` 1070 verts.

## Phase 2: Frontend
Status: Complete

- [x] `AuthUser.termsAccepted` ; `Auth.acceptTerms(version)` qui met à jour `currentUser`.
- [x] `features/auth/terms-banner/` : visible si connecté, `termsAccepted` faux et pas
  masqué pour la session ; « Accepter » (envoie `TERMS_VERSION`), « Plus tard », lien
  `/terms`, lien `/account` ; 409 → recharge le profil, autre erreur → message.
- [x] `app.html` : le bandeau sous `<app-header />`.
- [x] Textes `$localize` + `messages.fr.xlf`.

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
`AuthUser.termsAccepted`, `Auth.acceptTerms`, `terms-banner` sous l'en-tête, traductions. Vérifié : `pnpm test` 295 verts, lint, build des deux langues ; Playwright `terms-banner.spec.ts` EN (dont téléphone) et FR, exécutés par la CI.

## Phase 3: Docs, revues, PR
Status: Complete

- [x] `docs/decisions/architecture.md` (« Pages légales et acceptation des CGU »),
  `docs/roadmap.md`.
- [x] code-reviewer puis architecture-reviewer ; corriger 🔴 et 🟠.
- [x] PR vers `develop`, CI verte.

### Verification Plan
- Tests backend et frontend verts ; CI de la PR verte.

### Phase Summary
Docs `architecture.md` et `roadmap.md`. Revues : sécurité/RGPD sans 🔴 ni 🟠 (garde ajoutée dans `User.acceptTerms`) ; architecture, un 🟠 corrigé (javadoc déplacée dans `User`), `@Transactional` gardé (lecture + écriture groupées).

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
Un compte dont les CGU acceptées ne sont pas celles en vigueur (ou aucune) voit un bandeau
non bloquant : date du changement, lien vers les CGU, « Accept », « Later » (pour
l'onglet), et le rappel qu'un refus passe par la suppression du compte. L'acceptation
n'enregistre que la version en vigueur, avec la date de la première acceptation. L'API ne
bloque rien d'autre. Pas d'écart au plan.

## Deployment Plan
1. Release `develop` → `main` (merge commit). Pas de migration : les colonnes existent
   depuis V3.
2. En prod, tous les comptes créés avant le 2026-11-03 verront le bandeau. Vérifier avec
   un tel compte : bandeau visible, « Accept » le fait disparaître, il ne revient pas après
   rechargement ; en base, `terms_version = '2026-11-03'` et `terms_accepted_at` renseigné.

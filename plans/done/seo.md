# SEO léger

Rendre AgeGuessr lisible par les moteurs et les aperçus de liens (WhatsApp, Discord, X…)
sans SSR : description et Open Graph traduits dans le `index.html` de chaque langue,
`robots.txt` et `sitemap.xml` à la racine, canonical/hreflang et `noindex` posés à chaque
navigation. Branche `feature/seo`. Le pré-rendu (SSG) viendra avec la landing page
(décision du 2026-11-15 : la page d'accueil est le jeu, le gain serait faible).

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

### Décisions et hypothèses
- **Une seule source pour les textes** : description et titre d'aperçu en `$localize`
  (`@@seo.*`) dans `features/seo/seo-texts.ts`, utilisés à l'exécution ; le script
  post-build lit les mêmes ids dans `messages.xlf` (anglais) et `messages.fr.xlf`
  (français) pour les écrire dans chaque `index.html`. Angular ne traduit pas
  `index.html`, et les robots d'aperçu n'exécutent pas le JavaScript.
- **Statique (index.html, par langue)** : `meta description`, `og:type/site_name/title/
  description/url/image/locale(+alternate)`, `twitter:card summary_large_image`.
  Pas de canonical statique : il serait faux sur toutes les routes sauf l'accueil.
- **Dynamique (`SeoService`, à chaque `NavigationEnd`)** : `link rel=canonical`
  (`<origine>/<langue><chemin>`, sans query), `link rel=alternate hreflang` en/fr et
  `x-default` (chemin sans préfixe, que Caddy redirige selon le visiteur), `meta
  description` de la route (`data.description`, sinon celle du site), `meta robots
  noindex` si `data.noindex`. Google exécute le JavaScript : c'est lui que ça sert.
- **noindex** : register, login, check-email, confirm-email, forgot-password,
  reset-password, account, profile, friends, confirm-account-deletion, admin/*,
  challenge/:code (contenu propre à un lien partagé). Indexées : `/`, `free`,
  `how-to-play`, `legal-notice`, `terms`, `privacy`.
- **Sitemap** : les 6 routes indexées × 2 langues, chacune avec ses `xhtml:link`
  hreflang en/fr/x-default. Domaine `https://ageguessr.app` (constante du script, rien de
  secret).
- **robots.txt** : tout autorisé sauf `/api/`, `/en/admin`, `/fr/admin` ; ligne
  `Sitemap:`.
- **Image d'aperçu** `og-image.png` 1200×630, sans texte à traduire (wordmark Fraunces,
  couleurs du thème sombre, motif photo sépia), générée une fois comme les icônes et
  documentée dans `public/README.md`. Servie sous `/en/` et `/fr/` (public/ est copié par
  langue).
- **Caddy** : `/robots.txt` et `/sitemap.xml` servis depuis `/srv` avant la redirection
  de langue (sinon ils partiraient en 302 vers `/en/robots.txt`).

## Phase 1 : textes et service dynamique
Status: Complete

- [x] `features/seo/seo-texts.ts` : `@@seo.title` (titre d'aperçu), `@@seo.description`
      (site), descriptions de `how-to-play`, `free` et des 3 pages légales.
- [x] `features/seo/seo.service.ts` : écoute `NavigationEnd`, lit les `data` de la route
      la plus profonde, pose canonical, alternates, description, robots (retire
      `noindex` en quittant une page privée). Langue via `LOCALE_ID`.
- [x] `app.routes.ts` : `data: { description }` sur les pages indexées, `data: {
      noindex: true }` sur les autres (liste ci-dessus).
- [x] Démarrage du service dans `app.config.ts` (`provideAppInitializer`) ou `App`.
- [x] i18n : `messages.xlf` réextrait, cibles FR dans `messages.fr.xlf`.
- [x] Tests Vitest `seo.service.spec.ts` : canonical et alternates d'une route EN et FR,
      query retirée, description de route puis retour à celle du site, `noindex` posé
      puis retiré, une seule balise de chaque à force de naviguer.

### Verification Plan
- `cd frontend && pnpm test` : tous verts, dont `seo.service.spec.ts`.
- `pnpm lint` et `pnpm format:check` passent.

### Phase Summary
`Seo` (`features/seo/seo.ts`, `@Service()`), démarré par `provideAppInitializer`. Routes :
`describedAs(SEO_TEXT.x)` sur les 5 pages décrites, `PRIVATE` (noindex) sur 13 routes.
Textes EN/FR revus pour dire « l'âge de la personne sur la photo » (pas l'année).
Vérifié : `pnpm test` 469 tests verts (dont 6 `seo.spec.ts`), lint et format OK.

## Phase 2 : post-build statique, robots, sitemap, image, Caddy
Status: Complete

- [x] `frontend/scripts/seo-postbuild.mjs` : fonctions pures exportées (`readTranslations`,
      `injectMeta(html, meta)`, `sitemapXml(routes)`, `robotsTxt()`), puis un `main` qui
      réécrit `dist/frontend/browser/{en,fr}/index.html` et écrit `robots.txt` et
      `sitemap.xml` à la racine de `dist/frontend/browser/`. Échappement HTML des textes.
      Échec explicite si un id `seo.*` manque.
- [x] `package.json` : `"build": "ng build && node scripts/seo-postbuild.mjs"` ;
      `"test": "ng test && node --test scripts/"`.
- [x] `frontend/scripts/seo-postbuild.test.mjs` (node:test) : meta injectées une fois
      avant `</head>`, textes échappés, FR lu depuis la cible, id manquant → erreur,
      sitemap avec 12 `<url>` et leurs alternates, robots avec `Sitemap:`.
- [x] `public/og-image.png` (1200×630) généré, procédé ajouté à `public/README.md`.
- [x] `Caddyfile` : `handle /robots.txt` et `handle /sitemap.xml` → `file_server` sur
      `/srv`, avant le `handle` de redirection.
- [x] `scripts/check-edge-locales.sh` : `/robots.txt` et `/sitemap.xml` en 200 sans
      redirection ; `/fr/` contient `og:locale" content="fr_FR"` et la description FR ;
      `/en/og-image.png` en 200.
- [x] Docs : section SEO dans `docs/decisions/architecture.md` ; roadmap Semaine 7 cochée
      (« SEO léger, SSR reporté à la landing page ») et vérification prod ajoutée
      (aperçu d'un lien dans WhatsApp/Discord, Search Console : sitemap soumis).

### Verification Plan
- `cd frontend && pnpm build` puis `grep -c 'og:description' dist/frontend/browser/fr/index.html`
  → 1, et la description FR présente ; `dist/frontend/browser/sitemap.xml` contient 12
  `<url>`.
- `pnpm test` (Vitest + node:test) vert.
- `scripts/check-edge-locales.sh` → « All checks passed » (si Docker local tient la
  build ; sinon le noter et compter sur la vérification prod).

### Phase Summary
`node --test` demande un glob (`'scripts/*.test.mjs'`), pas un dossier. Le titre de
`index.html` devient aussi `seo.title` (meilleur titre dans Google pour l'accueil, qui n'a
pas de `title` de route). Image : wordmark + « AgeGuessr.app » plutôt qu'une grille de
cases, illisible hors contexte.
Vérifié : `pnpm build` → 12 `<url>`, balises FR dans `fr/index.html` ;
`scripts/check-edge-locales.sh` → « All checks passed » (19 vérifications, dont les 6
nouvelles) ; `pnpm test` vert (469 Vitest + 7 node:test).

## Phase 3 : reviews et PR
Status: Not started

- [x] architecture-reviewer sur le diff, corrections (description explicite de l'accueil,
      erreur lisible si une trans-unit n'a ni source ni cible).
- [x] code-reviewer (contenu public, robots/noindex des pages privées) : rien à corriger,
      pas de mise à jour de `/privacy` (aucun tiers, cookie ni traceur).
- [ ] PR `feature/seo` → `develop`, CI verte.

### Verification Plan
- `gh pr checks <n>` : tous les jobs `pass`.

### Phase Summary
_(write when phase completes)_

## Final Recap
Les liens partagés ont une carte d'aperçu traduite, Google a un canonical, des alternates
et un sitemap par langue, et les pages privées sont exclues de l'index.

## Deployment Plan
1. Merger la PR dans `develop`, puis la release `develop → main` (merge commit).
2. Rien à faire sur le VPS : l'image edge porte `robots.txt`, `sitemap.xml` et le
   Caddyfile.
3. Vérification prod listée en tête de `docs/roadmap.md` (aperçu WhatsApp/Discord,
   Search Console).

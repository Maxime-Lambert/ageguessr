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
Status: Not started

- [ ] `features/seo/seo-texts.ts` : `@@seo.title` (titre d'aperçu), `@@seo.description`
      (site), descriptions de `how-to-play`, `free` et des 3 pages légales.
- [ ] `features/seo/seo.service.ts` : écoute `NavigationEnd`, lit les `data` de la route
      la plus profonde, pose canonical, alternates, description, robots (retire
      `noindex` en quittant une page privée). Langue via `LOCALE_ID`.
- [ ] `app.routes.ts` : `data: { description }` sur les pages indexées, `data: {
      noindex: true }` sur les autres (liste ci-dessus).
- [ ] Démarrage du service dans `app.config.ts` (`provideAppInitializer`) ou `App`.
- [ ] i18n : `messages.xlf` réextrait, cibles FR dans `messages.fr.xlf`.
- [ ] Tests Vitest `seo.service.spec.ts` : canonical et alternates d'une route EN et FR,
      query retirée, description de route puis retour à celle du site, `noindex` posé
      puis retiré, une seule balise de chaque à force de naviguer.

### Verification Plan
- `cd frontend && pnpm test` : tous verts, dont `seo.service.spec.ts`.
- `pnpm lint` et `pnpm format:check` passent.

### Phase Summary
_(write when phase completes)_

## Phase 2 : post-build statique, robots, sitemap, image, Caddy
Status: Not started

- [ ] `frontend/scripts/seo-postbuild.mjs` : fonctions pures exportées (`readTranslations`,
      `injectMeta(html, meta)`, `sitemapXml(routes)`, `robotsTxt()`), puis un `main` qui
      réécrit `dist/frontend/browser/{en,fr}/index.html` et écrit `robots.txt` et
      `sitemap.xml` à la racine de `dist/frontend/browser/`. Échappement HTML des textes.
      Échec explicite si un id `seo.*` manque.
- [ ] `package.json` : `"build": "ng build && node scripts/seo-postbuild.mjs"` ;
      `"test": "ng test && node --test scripts/"`.
- [ ] `frontend/scripts/seo-postbuild.test.mjs` (node:test) : meta injectées une fois
      avant `</head>`, textes échappés, FR lu depuis la cible, id manquant → erreur,
      sitemap avec 12 `<url>` et leurs alternates, robots avec `Sitemap:`.
- [ ] `public/og-image.png` (1200×630) généré, procédé ajouté à `public/README.md`.
- [ ] `Caddyfile` : `handle /robots.txt` et `handle /sitemap.xml` → `file_server` sur
      `/srv`, avant le `handle` de redirection.
- [ ] `scripts/check-edge-locales.sh` : `/robots.txt` et `/sitemap.xml` en 200 sans
      redirection ; `/fr/` contient `og:locale" content="fr_FR"` et la description FR ;
      `/en/og-image.png` en 200.
- [ ] Docs : section SEO dans `docs/decisions/architecture.md` ; roadmap Semaine 7 cochée
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
_(write when phase completes)_

## Phase 3 : reviews et PR
Status: Not started

- [ ] architecture-reviewer sur le diff, corrections.
- [ ] code-reviewer (contenu public, robots/noindex des pages privées), corrections.
- [ ] PR `feature/seo` → `develop`, CI verte.

### Verification Plan
- `gh pr checks <n>` : tous les jobs `pass`.

### Phase Summary
_(write when phase completes)_

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

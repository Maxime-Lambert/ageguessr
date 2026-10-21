# Charte graphique : finalisation (roadmap Semaine 1)

Clore les trois cases « charte » de la Semaine 1 : les tokens de couleur passent WCAG AA
en sombre comme en clair, le favicon et le wordmark suivent la charte, et les pistes
encore ouvertes de `design.md` sont tranchées (ton) ou reportées explicitement (duotone).

## Contexte pour repartir à froid
- Branche : `chore/design-charter` (depuis `develop`, à jour de la PR #12).
- La charte est déjà largement décidée et codée :
  - décisions : `docs/decisions/design.md` ;
  - tokens sombre/clair : `frontend/src/styles.css` (`@theme` + classe `.light`) ;
  - polices auto-hébergées (`@fontsource-variable/fraunces`, `@fontsource/inter`) ;
  - bascule de thème : `frontend/src/app/features/theme/` ;
  - icônes : `@lucide/angular` installé ;
  - wordmark : `frontend/src/app/features/layout/header/header.html:2`
    (`font-display text-2xl`) ;
  - favicon provisoire : `frontend/public/favicon.svg` (« A » en Georgia, `rx=12`).
- Ce qui manque : vérification des contrastes, `--color-danger` absent de `design.md`
  (le CSS y renvoie pourtant), favicon hors charte, ton et duotone non tranchés.

## Décisions validées avec l'utilisateur (2026-10-21)
- **Ton « dossier d'enquête » adopté à l'essai**, réversible à faible coût :
  - glossaire dans `design.md`, avec pour chaque notion du jeu un terme « enquête » et
    un terme « neutre » de repli ;
  - un critère explicite pour garder ou abandonner l'essai ;
  - règle pour la Semaine 4 : tout le vocabulaire de jeu vient d'un seul fichier, jamais
    écrit en dur dans les templates. Abandonner l'essai = modifier ce fichier seul ;
  - les écrans de compte et de connexion restent en vocabulaire neutre.
- **Duotone reporté à la Semaine 4**, noté comme décision différée : il faut de vraies
  photos Commons pour trancher.

## Constat de départ : contrastes actuels (ratios WCAG 2.x)
Seuils : 4.5:1 pour le texte normal (1.4.3), 3:1 pour les bordures de champs de
formulaire et les indicateurs graphiques (1.4.11).

| Paire | Sombre | Clair | Usage réel |
|---|---|---|---|
| foreground / background, surface | 14.5 / 13.3 | 14.4 / 16.3 | ok |
| muted / background, surface | 5.8 / 5.3 | **4.24** / 4.8 | `text-muted` (27×) |
| accent / background, surface | **4.09 / 3.73** | 6.5 / 7.3 | `text-accent`, `hover:text-accent` (liens) |
| danger / background, surface | **3.85 / 3.51** | 5.1 / 5.8 | `text-danger` (24×, erreurs) |
| blanc / accent | 4.52 | 7.5 | boutons `bg-accent` |
| blanc / danger | 4.80 | 5.9 | bouton de suppression |
| blanc / hot, cold | **≈3.9 / ≈3.5** | à mesurer | badges de démo sur `home.html` |
| border / background, surface | **1.43 / 1.30** | **1.32 / 1.49** | séparateurs, cartes **et champs de formulaire** |

En gras : sous le seuil.

## Décisions d'architecture de cette feature
- **Bordures : deux tokens.** `--color-border` reste décoratif (séparateurs, cartes), où
  1.4.11 ne s'applique pas. On ajoute `--color-input-border` à 3:1 minimum, utilisé par
  les 4 classes d'`<input>` existantes. Monter `--color-border` à 3:1 alourdirait tous
  les séparateurs et casserait l'ambiance sombre et douce.
- **Ajuster la luminosité, pas la teinte.** Chaque token corrigé garde sa teinte (indigo,
  brique, papier) ; on ne bouge que la luminosité, du minimum nécessaire. L'identité
  décrite dans `design.md` ne change pas.
- **Accent et danger utilisés à la fois en texte et en fond.** En sombre, éclaircir
  l'accent pour le texte fait baisser le contraste du blanc posé dessus. Si une valeur
  unique ne passe pas les deux usages, on change `--color-accent-foreground` (texte sur
  bouton) plutôt que d'ajouter un 2e token d'accent. On ajoute
  `--color-danger-foreground` sur le même principe, et le bouton de suppression arrête
  d'utiliser `text-white` en dur.
- **Hot/cold** : en jeu, ce sont des indicateurs graphiques (seuil 3:1 contre le fond,
  déjà atteint). Les badges de démo de `home.html` affichent du texte blanc dessus : on
  ajoute des tokens `-foreground` pour qu'ils passent 4.5:1, et on retire le `text-white`.
- **Garde-fou automatique** : un test Vitest lit `styles.css`, extrait les tokens des
  deux thèmes et vérifie chaque paire utilisée contre son seuil. Un futur ajustement de
  couleur qui casse le contraste fait échouer la CI.
- **Favicon** :
  - « A » de Fraunces **converti en tracé SVG** (`<path>`), pour ne pas dépendre des
    polices installées chez le visiteur (le `<text>` actuel retombe sur Georgia) ;
  - instance de la police variable figée en graisse 600, `opsz` 144 (optique « titre »),
    via un script Node jetable (`fontkit` + `wawoff2` + `@resvg/resvg-js`, pas de venv
    Python sur la machine). La méthode est notée dans `frontend/public/README.md` ;
    **révisé en Phase 2 : graisse 700, `opsz` 9**, voir le Phase Summary ;
  - fond `--color-background` sombre, lettre `--color-accent`, coin `rx` ≈ 3/64 (« coins
    peu arrondis », comme `--radius-sm`) ;
  - fichiers : `favicon.svg` (navigateurs modernes), `favicon.ico` 32×32 (anciens
    navigateurs, onglets Windows), `apple-touch-icon.png` 180×180 (écran d'accueil iOS,
    fond plein sans transparence). Pas de manifeste PWA : rien ne le justifie encore ;
  - licence : Fraunces est sous SIL OFL 1.1, qui autorise d'embarquer les contours d'un
    glyphe dans un logo. On le note dans `design.md`.

## Hypothèses
- ~~Le builder `@angular/build:unit-test` permet de lire `styles.css` dans un spec~~
  Confirmé : `import styles from '../styles.css' with { loader: 'text' }` (loader texte
  d'esbuild), typé par `frontend/src/text-imports.d.ts`.
- Le wordmark actuel (`font-display text-2xl`, Fraunces par défaut) suit déjà la charte.
  On vérifie seulement la graisse et l'`opsz` effectifs, sans refonte.

---

## Phase 1 — Contrastes et tokens
- [x] Test Vitest `frontend/src/app/design-tokens.spec.ts` : parse `styles.css` et vérifie
      les paires du tableau ci-dessus pour les deux thèmes. Il doit d'abord **échouer**
      sur les valeurs actuelles.
- [x] Ajuster dans `frontend/src/styles.css` : `muted` (clair), `accent` et `danger`
      (sombre), `accent-foreground` si besoin ; ajouter `--color-input-border`,
      `--color-danger-foreground`, `--color-hot-foreground`, `--color-cold-foreground`
- [x] Remplacer `border-border` par `border-input-border` sur les classes d'`<input>` (en réalité 14 champs dans 6 templates)
- [x] Remplacer `text-white` par les tokens `-foreground` (`home.html`,
      `confirm-account-deletion.html`)
- [x] Contrôle visuel des deux thèmes sur l'accueil, la connexion et le compte

### Phase Summary
Valeurs retenues (luminosité seule, teinte conservée) :
- sombre : `accent` #6467E8 → #7779EB, `danger` #C24B3F → #CC675D ;
- clair : `muted` #79705E → #746B5A ;
- `input-border` : sombre #736855, clair #928772 (teinte de `muted`, plus sobre que
  celle de `border`).

En sombre, le blanc ne passe plus sur l'accent ni sur danger (3.7) : tous les
`-foreground` y sont #17130F (texte sombre). En clair, ils restent blancs.

Le test garde-fou échouait sur 15 paires avant correction, 36/36 après.
`pnpm test` : 121 verts ; Playwright `component` : 28 verts.

### Tests
- **Unitaire frontend** : `design-tokens.spec.ts`, la seule garantie automatique.
- **Interface (Playwright)** : les specs existants de `e2e/component/` passent sans
  changement. Pas de nouveau spec : aucune interaction ne change.
- **Omis** :
  - aucun test backend (unitaire, intégration, fonctionnel, architecture, mutation) :
    rien ne change côté API ;
  - pas de QA e2e : aucun parcours utilisateur ne change.

## Phase 2 — Favicon et wordmark
- [x] Générer le tracé du « A » (Fraunces **700, `opsz` 9** — voir Phase Summary) → `frontend/public/favicon.svg`
- [x] Générer `favicon.ico` (32×32) et `apple-touch-icon.png` (180×180)
- [x] `frontend/src/index.html` : `<link rel="icon" href="favicon.ico" sizes="32x32">`,
      `<link rel="icon" type="image/svg+xml" href="favicon.svg">`,
      `<link rel="apple-touch-icon" href="apple-touch-icon.png">`
- [x] `frontend/public/README.md` : commande de régénération et licence OFL
- [x] Vérifier le wordmark : graisse et `opsz` effectifs dans le navigateur, ajuster les
      classes de `header.html` seulement si l'écart est visible

### Phase Summary
- Instance révisée : `opsz` 144 donnait des déliés invisibles à 32 px. L'optique la plus
  petite (`opsz` 9, graisse 700) reste lisible en onglet, vérifié sur un rendu 32 px
  agrandi.
- Le « A » est en `--color-accent` sombre corrigé (#7779EB).
- Wordmark inchangé : Fraunces 400, `opsz` automatique selon la taille, conforme.
- Caddy (`try_files {path} /index.html`) renvoie 200 pour un fichier absent : le test
  vérifie le type MIME, pas seulement le statut.
- Playwright `component` : 29 verts.

### Tests
- **Interface (Playwright)** : dans `e2e/component/header.spec.ts`, vérifier que les trois
  `<link>` d'icône existent et que leurs fichiers répondent 200. Un lien cassé ne se voit
  pas dans les autres tests.
- **Omis** : unitaire (aucune logique), le reste comme en Phase 1.

## Phase 3 — Documentation
- [ ] `docs/decisions/design.md` :
  - tableau des couleurs mis à jour, avec les nouveaux tokens et leur raison ;
  - `--color-danger` documenté ;
  - nouvelle section « Accessibilité des couleurs » : seuils, règle des deux bordures,
    test garde-fou ;
  - duotone : « reporté à la Semaine 4, à trancher sur de vraies photos » ;
  - ton « dossier d'enquête » adopté à l'essai : glossaire, critère, règle du fichier de
    vocabulaire unique, écrans auth neutres ;
  - section « Nom et logo » : favicon livré, licence OFL.
- [ ] `docs/roadmap.md` :
  - cocher « Charte graphique définie » et « Mise en place technique » ;
  - cocher « Logo/wordmark + favicon » ;
  - ajouter en Semaine 4 : « Vocabulaire de jeu centralisé (essai du ton enquête) » et
    « Trancher le duotone sur de vraies photos ».

### Tests
Aucun (documentation).

## Phase 4 — Relectures et PR
- [ ] Agent `architecture-reviewer` sur le diff. `code-reviewer` n'est pas requis :
      aucune donnée utilisateur, aucun endpoint, aucune auth, aucune source externe.
- [ ] `pnpm test` + `pnpm lint` + specs Playwright `component` verts ; `./mvnw test` vert
      (règle avant tout push, même sans changement backend)
- [ ] PR `chore/design-charter` → `develop`

### Verification Plan
- `design-tokens.spec.ts` échoue avant la Phase 1 et passe après.
- En prod après release : `/favicon.svg`, `/favicon.ico` et `/apple-touch-icon.png`
  répondent 200, et l'onglet affiche le nouveau « A ».

## Edge cases à couvrir
- Thème clair forcé par la bascule **et** préférence système sombre : le test lit les
  deux blocs de tokens, pas la préférence du navigateur.
- Bouton désactivé (`disabled:opacity-50`) : exempté par WCAG (composant inactif), on ne
  le teste pas.
- `hover:text-accent` sur un lien de l'en-tête : l'état survolé doit passer 4.5:1 lui
  aussi, il est couvert par la paire accent / background.
- Favicon dans un onglet clair et dans un onglet sombre : le fond plein du carré garantit
  la lisibilité dans les deux.
- iOS : `apple-touch-icon.png` sans transparence, sinon iOS remplit le fond en noir.

## Final Recap
_(à remplir en fin de feature)_

## Deployment Plan
- Aucune migration, aucune variable d'environnement, aucun changement backend.
- Chemin habituel : merge dans `develop`, puis release `develop` → `main` en **merge
  commit**.
- Les navigateurs gardent le favicon en cache : vérifier avec une navigation privée.

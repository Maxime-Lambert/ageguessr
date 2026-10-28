# Hauteur minimale des photos

Des photos trop petites arrivent en curation (moins de 60 % du cadre de la page de
curation), et certaines ont peut-être été approuvées. Une photo jouable doit mesurer au
moins **600 px de haut** (décision de l'utilisateur du 2026-10-28, à ajuster ensuite) :
la hauteur compte plus que la largeur, le jeu affichant un portrait.

Empilée sur `feature/exclude-footballers` (PR #22), qui touche les mêmes fichiers.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions
- **Miniatures demandées en hauteur** (`iiurlheight`, propriété
  `app.people.ingest.thumbnail-height`, 1 000 px) au lieu de la largeur (800 px). En
  largeur, une photo paysage d'un grand original donnait 800 × 533 et serait rejetée à
  tort. En hauteur, la miniature ne descend sous 600 px que si l'original est trop
  petit (Commons n'agrandit jamais). Les paysages pèsent plus lourd (1 500 × 1 000
  au lieu de 800 × 533).
- **Rejet à la sélection** : nouveau motif `PhotoRejection.TOO_SMALL` dans
  `PhotoCandidateFilter`, sur la hauteur de la miniature annoncée par Commons
  (`thumbheight`), donc avant tout téléchargement et avant le choix des photos d'une
  personne (une photo trop petite ne prend pas la place d'une autre).
  Propriété `app.people.ingest.min-image-height` (600).
- **Photos déjà en base** : commande ponctuelle `--fix-small-images`. Pour chaque photo
  non rejetée de moins de 600 px de haut, redemande la miniature en hauteur : si elle
  atteint 600 px, l'image est remplacée (même photo, plus grande, statut inchangé) ;
  sinon la photo passe en `REJECTED` (`reviewed_by` vide : rejet automatique), même
  approuvée. La place libérée revient à la vague suivante.
- **Page de curation** : affiche la taille réelle de l'image (`naturalWidth` ×
  `naturalHeight`, lus par le navigateur), sans changer l'API.

## Phase 1 : Rejet à l'ingestion et correction des photos stockées
Status: Not started

- [ ] `IngestProperties` : `thumbnailWidth` → `thumbnailHeight` (1 000),
      `minImageHeight` (600) ; `application.properties` (+ test).
- [ ] `CommonsClient.fetchFiles` : `iiurlheight` ; `CommonsFile.thumbnailHeight`
      (`thumbheight`).
- [ ] `PhotoCandidateFilter.check` : `TOO_SMALL` sous la hauteur minimale.
- [ ] `PeopleStore` : photos non rejetées sous la hauteur minimale (id, fichier) ;
      remplacement de l'image ; rejet automatique.
- [ ] `IngestPeopleHandler.fixSmallImages` + option `--fix-small-images` (seule, comme
      `--analyze`) ; bilan logué : agrandies, rejetées (dont approuvées).
- [ ] Docs : `architecture.md` (sourcing), roadmap.

### Tests
- **Unitaire** : `PhotoCandidateFilterTest` (599 rejetée, 600 gardée) ;
  `CommonsClientTest` (`iiurlheight` envoyé, `thumbheight` lu) ; handler (`TOO_SMALL`
  compté ; `fixSmallImages` agrandit ou rejette) ; runner (`--fix-small-images` seule).
- **Intégration** (`PeopleStoreIntegrationTest`) : sélection des petites images non
  rejetées, remplacement, rejet d'une photo approuvée.
- **Mutation** : PIT sur `IngestPeople`, test strength ≥ 97 %.
- **Omises** : fonctionnel (aucun endpoint), ArchUnit (aucune nouvelle dépendance).

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → 0 échec.
- Base locale : `--fix-small-images` → les photos < 600 px sont agrandies ou rejetées ;
  `SELECT count(*) FROM person_photo_images i JOIN person_photos p ON p.id = i.photo_id
  WHERE i.height < 600 AND p.review_status <> 'REJECTED'` → 0.

### Phase Summary
_(write when phase completes)_

## Phase 2 : Taille affichée en curation
Status: Not started

- [ ] `curation.ts` / `curation.html` : taille lue au chargement de l'image, affichée
      sous la photo.
- [ ] Tests : unitaire Vitest (taille affichée après `load`).

### Verification Plan
- `cd frontend && pnpm test` et lint → 0 échec.

### Phase Summary
_(write when phase completes)_

## Phase 3 : Revues et PR
Status: Not started

- [ ] `code-reviewer` et `architecture-reviewer` ; corriger 🔴 et 🟠.
- [ ] Final Recap, Deployment Plan, archivage `plans/done/min-image-height.md`, PR.

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

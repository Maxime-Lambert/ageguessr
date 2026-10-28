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
Status: Complete

- [x] `IngestProperties` : `thumbnailWidth` → `thumbnailHeight` (1 000),
      `minImageHeight` (600) ; `application.properties` (+ test).
- [x] `CommonsClient.fetchFiles` : `iiurlheight` ; `CommonsFile.thumbnailHeight`
      (`thumbheight`).
- [x] `PhotoCandidateFilter.check` : `TOO_SMALL` sous la hauteur minimale.
- [x] `PeopleStore` : photos non rejetées sous la hauteur minimale (id, fichier) ;
      remplacement de l'image ; rejet automatique.
- [x] `IngestPeopleHandler.fixSmallImages` + option `--fix-small-images` (seule, comme
      `--analyze`) ; bilan logué : agrandies, rejetées (dont approuvées).
- [x] Docs : `architecture.md` (sourcing), roadmap.

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
**Livré** : miniatures en hauteur (1 000 px), `CommonsFile.thumbnailHeight`, rejet
`TOO_SMALL` dans `PhotoCandidateFilter`, `--fix-small-images` (`SmallImagesReport`,
`PeopleStore.smallImages` / `replaceImage` / `rejectTooSmall`), docs.

**Vérification** : backend 683/683. Base locale (75 photos des essais) :
`--fix-small-images` → 10 trouvées sous 600 px, 2 agrandies, 8 rejetées (originaux
trop petits) ; ensuite, aucune photo non rejetée sous 600 px (minimum 607).

## Phase 2 : Taille affichée en curation
Status: Complete

- [x] `curation.ts` / `curation.html` : taille lue au chargement de l'image, affichée
      sous la photo.
- [x] Tests : unitaire Vitest (taille affichée après `load`).

### Verification Plan
- `cd frontend && pnpm test` et lint → 0 échec.

### Phase Summary
**Livré** : taille réelle (`naturalWidth` × `naturalHeight`) sous la photo, avertissement
sous 600 px (`MIN_IMAGE_HEIGHT`, miroir de la propriété backend).

**Vérification** : front 170/170, lint, Prettier, `tsc` OK.

## Phase 3 : Revues et PR
Status: Complete

- [x] `code-reviewer` et `architecture-reviewer` ; corriger 🔴 et 🟠.
- [x] Final Recap, Deployment Plan, archivage `plans/done/min-image-height.md`, PR.

### Phase Summary
**Revues** : aucun 🔴. `architecture-reviewer` : 2 🟠 corrigés (`--analyze
--fix-small-images` ignorait la correction en silence : contrôle des options factorisé
dans `rejectOthers`, modes de maintenance exclusifs ; test du ré-encodage qui échoue) ;
🟡 appliqué : taille effacée au changement de photo. `code-reviewer` : anti-triche
(même chemin `ImageReencoder`), liste blanche d'hôtes de `ImageDownloader`, aucun nom
dans les logs, personnes exclues : rien à signaler ; 🟡 appliqué : `SmallImage.toString`
sans nom de fichier.

**Vérification finale** : backend **687/687**, front **170/170**, lint, Prettier, `tsc`
OK. PIT sur `IngestPeople` : **97 %** (605 / 633). Les survivants utiles (bornes à
600 px, contenu remplacé, `reviewed_by` effacé) ont reçu un test.

## Final Recap
**Livré** (PR `feature/min-image-height`, empilée sur #22) : plus de photo de moins de
600 px de haut dans le pool.
- Miniatures demandées en hauteur (1 000 px) : une photo paysage d'un grand original
  n'est plus réduite à 533 px.
- Rejet `TOO_SMALL` avant téléchargement et avant le choix des photos d'une personne.
- `--fix-small-images` : agrandit les photos stockées quand Commons a mieux (statut
  inchangé), les rejette sinon, même approuvées. Base locale : 10 trouvées, 2
  agrandies, 8 rejetées.
- Page de curation : taille réelle et avertissement sous 600 px.

**Écarts** : la première idée (rejeter toute photo stockée sous 600 px) aurait perdu
des paysages issus de grands originaux ; d'où les miniatures en hauteur et
l'agrandissement avant rejet.

**Hors scope** : ajuster le seuil sur les données de prod (requête de répartition
dans le Deployment Plan).

## Deployment Plan
1. **Après le merge de #22**, merge de cette PR dans `develop` (GitHub la recible
   quand la branche de #22 est supprimée), puis release `develop` → `main` en merge
   commit. Aucune migration. Variables optionnelles : `INGEST_THUMBNAIL_HEIGHT` (1 000)
   remplace `INGEST_THUMBNAIL_WIDTH` (jamais définie en prod), `INGEST_MIN_IMAGE_HEIGHT`
   (600).
2. **Corriger les photos stockées**, avant toute nouvelle vague et avant `--analyze`,
   depuis le VPS dans `tmux` (une requête Commons par lot de 50 fichiers, plus un
   téléchargement par photo agrandie) :
   `cd /opt/ageguessr && docker compose -f docker-compose.prod.yml run --rm backend --spring.profiles.active=prod,ingest --fix-small-images`
   Lire le bilan : trouvées, agrandies, rejetées (dont approuvées).
3. **Vérifier** : aucune photo non rejetée sous 600 px :
   `cd /opt/ageguessr && docker compose -f docker-compose.prod.yml exec postgres psql -U ageguessr -d ageguessr -c "SELECT count(*) FROM person_photo_images i JOIN person_photos p ON p.id = i.photo_id WHERE i.height < 600 AND p.review_status <> 'REJECTED'"`
   → 0.
4. **Ensuite**, `--analyze` et la vague suivante (`plans/done/ingest-focus.md`).

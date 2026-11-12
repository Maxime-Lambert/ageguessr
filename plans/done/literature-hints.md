# Catégorie Littérature (7e catégorie d'indice)

Ajouter une catégorie Littérature : parutions d'œuvres (romans, pièces, poèmes, nouvelles,
bandes dessinées) avec leur auteur, et grands prix littéraires (Nobel de littérature,
Goncourt, Booker, Pulitzer de la fiction). Même extraction et même règle que les six autres.
Toujours 3 pistes à choisir, maintenant parmi 7. Branche `feature/literature-hints`,
depuis `develop`. Décision de l'utilisateur du 2026-11-12, après la mesure ci-dessous.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

L'explication des pistes (bouton ⓘ et bulle par catégorie) est une PR séparée, qui
suivra celle-ci pour couvrir directement les 7 catégories.

## Référence : mesure du 2026-11-12

Script d'essai (non versionné) branché sur `extract_notoriety.py`, mêmes vues
(2025-11-01 → 2026-10-31), même règle (top 10 au-dessus de 2 % de la médiane des meilleurs
de la décennie, jamais sous 500 / 5 000 vues), complément FR depuis l'anglais.

| Langue | 1930s | 1940s | 1950s | 1960s | 1970s | 1980s | 1990s | 2000s | 2010s | Années < 5 | Total |
|---|---|---|---|---|---|---|---|---|---|---|---|
| FR (médiane/an) | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 2 | 811 (21 complétés) |
| EN (médiane/an) | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 4 | 801 |

Échantillon 1985 FR : « Parution : « La Servante écarlate » — Margaret Atwood »,
« Parution : « Le Parfum » — Patrick Süskind », « Prix Goncourt : Yann Queffélec ».
Défauts vus : un pacte de l'ONU et le Petit Robert (classe « written work », trop large),
quelques œuvres sans auteur (« Pourquoi pas Evans ? »).

## Décisions

- **Sources** : œuvres de classe roman, œuvre littéraire, livre, pièce, poème, nouvelle,
  bande dessinée (`Q8261`, `Q7725634`, `Q571`, `Q25379`, `Q5185279`, `Q49084`, `Q1004`),
  date `P577`, auteur `P50`. Pas « written work » (`Q47461344`) : il ramène traités et
  dictionnaires. Prix : `Q37922`, `Q187300`, `Q160082`, `Q833633`, lauréat humain.
- **Formulation** : « Parution : « X » — Auteur » / « Published: "X" — Author » ; sans
  auteur connu, le titre seul. Prix : « Prix Goncourt : Lauréat », comme les autres prix.
- **Au plus 2 œuvres par auteur et par an**, comme les artistes en Musique.
- **Pistes par manche** : toujours 3 (`app.game.hints.per-round`), la borne passe à 7.
- **Icône** : `lucideBookOpen`.
- **Migration** `V23__add_literature_category.sql` : les contraintes `CHECK` de catégorie
  de `hints`, `game_plays` et `free_series_plays` acceptent `LITERATURE`. `VARCHAR(16)`
  suffit.
- **Import** : les autres catégories ne changent pas, un import ordinaire (sans
  `retire-absent`) suffit ; le relancer avec l'option ne retirerait rien de plus.

## Phase 1 : backend
Status: Complete

- [x] `HintCategory.LITERATURE` ; `HintRules` accepte la catégorie (liste dérivée de l'enum,
      à vérifier).
- [x] `V23__add_literature_category.sql` (trois contraintes).
- [x] `HintProperties.perRound` : `@Max(7)`.
- [x] Vérifier que rien d'autre ne liste les catégories en dur (`grep CELEBRITIES`).
- [x] Tests : `HintRulesTest` (une ligne `LITERATURE` acceptée), intégration (un indice
      `LITERATURE` importé puis offert par `findEligible`, une manche qui le révèle et le
      signale, contraintes V23). Pas de mutation dédiée : un ajout à une énumération.

### Verification Plan
- `cd ageguessr-api && ./mvnw clean test` → tout passe.

### Phase Summary
`HintCategory.LITERATURE`, V23 (contraintes de `hints`, `game_plays`,
`free_series_plays`), `@Max(7)` sur `perRound`, `@Size(max = 7)` sur `RevealHintCommand`
et `RevealSeriesHintCommand` (oubli relevé par `architecture-reviewer`). `HintRules`,
`HintOffer`, la couverture dérivent déjà de l'enum.
**Ajout après la revue `code-reviewer` (🟠)** : un indice pouvait nommer la personne à
deviner (« Parution : Harry Potter — J. K. Rowling » l'année de ses 32 ans sur sa photo ;
même trou en Musique, Cinéma, Sport, Nobel, seul Célébrités était protégé). V24 :
`subject_qid` permis dans toutes les catégories (toujours obligatoire en Célébrités),
`HintRules` l'accepte, et l'import le complète sur un indice déjà importé qui ne l'avait
pas (`fillSubject`, compteur `named`), sans toucher texte ni décision. `findEligible`
filtrait déjà sur ce champ. Tests : `HintRulesTest`, `ImportHintsHandlerTest`
(`namesTheSubjectOfAHintImportedWithoutOne`), `ImportHintsIntegrationTest`
(`aHintNamingThePersonToGuessIsNeverOfferedOnTheirPhoto`), `GameFunctionalTest`
(manche avec un indice Littérature).

## Phase 2 : extraction
Status: Complete

- [x] `extract_notoriety.py` : requêtes `LITERATURE` (`book`, `award`), `SITELINKS` 12,
      `TOP` 80, fichier `literature.json`, formulation, plafond par auteur (`PER_ARTIST`
      étendu à `book`).
- [x] Générer `literature.json` ; les six autres fichiers doivent rester identiques.
- [x] Relire 1 année par décennie (FR et EN) ; écarter par `LEFT_OUT` ce qui n'est pas de
      la littérature ou nomme une personne vivante à côté d'un crime.
- [x] `resources/hints/README.md` et `scripts/hints/README.md` (tableau des sources).

### Verification Plan
- `python3 scripts/hints/extract_notoriety.py --only LITERATURE --report` → au moins
  80/84 années à 5 indices dans chaque langue.
- `git diff --stat` sur les six autres fichiers → aucun changement.
- `ImportHintsIntegrationTest.everyLineOfTheShippedFilesIsAccepted` passe.

### Phase Summary
`literature.json` : 799 indices FR (3 années sous 5), 789 EN (4 années sous 5).
Avec la Littérature, au moins 4 catégories jouables chaque année en FR et en EN.
`LEFT_OUT` : « The Story of Ferdinand » (auteur faux dans Wikidata), une encyclique, le
wiki SCP (retiré aussi de Science, remplacé par le Superjet 100), le Livre d'Urantia.
Sujet (personne nommée) dans tous les fichiers : Sport 1 118, Musique 1 023,
Littérature 1 424, Cinéma 290, Science 105, Monde 58, Célébrités 1 479. Clés et textes
des six anciens fichiers inchangés (Science : SCP seulement). Relu 1936-2012, FR et EN :
titres de romans policiers et de fiction seulement côté « crime », rien à écarter.

## Phase 3 : frontend
Status: Complete

- [x] `HintCategory` (`features/game/models.ts`, `features/hint-curation/models.ts`) ;
      `CATEGORY_LABELS` (« Literature » / « Littérature » dans `messages.fr.xlf`) ; libellé
      admin (anglais) ; `LeadIcon` (`lucideBookOpen`).
- [x] La grille des pistes (`round.html`, 2 colonnes puis 3) tient 7 cartes : vérifier
      le rendu, la 7e carte ne doit pas paraître orpheline (sinon pleine largeur).
- [x] Couverture admin : colonne Littérature.
- [x] Tests : `lead-icon`, `round.spec.ts` (7 catégories offertes, 3 à choisir), couverture
      admin. Pas de test e2e nouveau : le parcours de jeu ne change pas.

### Verification Plan
- `cd frontend && pnpm test` → tout passe ; `pnpm exec ng lint` sans erreur.

### Phase Summary
Type, libellés (« Literature » / « Littérature »), icône `lucideBookOpen`, libellé
admin. Grille des pistes : une carte seule en dernière ligne prend toute la largeur
(4 à 7 catégories selon l'année). `pnpm test` 429 tests, lint propre.

## Phase 4 : documentation, revues et PR
Status: Complete

- [x] `docs/decisions/architecture.md` (catégories, sources), `docs/roadmap.md` (ligne
      cochée + vérification en prod).
- [x] Revues `code-reviewer` (sourcing externe) et `architecture-reviewer`, puis `/pr`.

### Verification Plan
- `./mvnw test` et `pnpm test` passent ; revues sans 🔴 ni 🟠 ouvert.

### Phase Summary
Revues : `code-reviewer` (🟠 fuite d'identité, corrigée par V24 ; 🟡 œuvres non
littéraires et commentaire, appliqués), `architecture-reviewer` (🟠 `RevealSeriesHintCommand`,
corrigé), puis une seconde revue `code-reviewer` du correctif : aucun point.
`./mvnw test` 1 227 tests, `pnpm test` 429 tests, tout passe.

## Final Recap
7e catégorie Littérature (~800 indices par langue) : au moins 4 catégories jouables
chaque année en FR et en EN. Correctif de sécurité au passage : un indice ne nomme
jamais la personne de la photo, dans toutes les catégories (V24, `subject_qid`).

## Deployment Plan
1. Merger la PR dans `develop`, puis la release `develop → main` (merge commit) ; elle
   embarque aussi `fix/two-digit-year-in-hints` et `fix/age-input-focus`. Flyway applique
   V23 et V24 au démarrage (contraintes seulement, rapide).
2. Sur le VPS, un seul import :
   `docker compose -f docker-compose.prod.yml run --rm backend --spring.profiles.active=prod,import-hints --app.hints.import.retire-absent=true`
   Rapport attendu : `read=10263`, `added=1598`, `named=2592`, `retired=9`, `refused={}`.
3. `/admin/hints` : colonne Littérature, aucune année sous 4 catégories ; en jeu, la piste
   Littérature (icône livre), plus de « Mai 68 ».

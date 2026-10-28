# Ligne éditoriale automatique (profils à risque)

La ligne éditoriale (`docs/decisions/architecture.md`, 2026-10-26) exclut les auteurs
de violences de masse et les dirigeants d'un pays en guerre, mais à la main, sur la
page de curation : la curation de la première passe en prod ne l'a pas fait.
L'utilisateur (2026-10-28) demande de l'automatiser et de l'élargir : criminels de
guerre, dictateurs et dirigeants autoritaires, personnes condamnées ou poursuivies pour
des crimes graves (dont sexuels), membres de l'extrême droite. Une commande exclut ces
personnes de la base (leurs photos sont effacées), et chaque vague les écarte d'office.

Empilée sur `feature/min-image-height` (#23), elle-même sur #22.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions
- **Des critères Wikidata, pas de liste nominative** : le dépôt est public. Une liste
  de noms accolés à « accusé de crimes sexuels » serait un risque de diffamation. Le
  code ne contient que des critères (QID de crimes, d'alignements politiques, de pays),
  et le résultat (des exclusions) vit en base, sans motif détaillé par personne.
- **Quatre critères** (mesurés le 2026-10-28 sur tous les humains de Wikidata, < 4 s au
  total) :
  1. **Condamné ou poursuivi** (`P1399` « condamné pour » ou `P1595` « inculpation »,
     sous-classes comprises) pour crime de guerre, crime contre l'humanité, génocide,
     terrorisme, viol, agression sexuelle, abus sexuel, abus sexuel sur mineur,
     pédopornographie, traite (sexuelle ou non), viol sur mineur, **meurtre** (choix de
     l'utilisateur) : 8 178 personnes. Pas « condamné pour » en général : il signale
     Turing (indécence) ou Messi (fraude fiscale).
  2. **Dictateur** (`P106` = `Q183318`) : 12 personnes, Wikidata l'utilise peu.
  3. **Extrême droite** : membre (`P102`) d'un parti dont l'alignement (`P1387`) est
     « right-wing extremism », « far-right » ou « alt-right », ou dont l'idéologie
     (`P1142`) est fascisme, néofascisme, nazisme, néonazisme, nationalisme blanc,
     suprémacisme blanc : 34 061 personnes. **Tous les membres**, pas seulement les
     politiques (choix de l'utilisateur).
  4. **Dirigeant autoritaire** : a occupé (`P39`) un poste sous-classe de chef d'État ou
     de chef de gouvernement d'un pays classé « régime autoritaire » (score < 4) par
     l'indice de démocratie EIU 2020 (`P8328` sur Wikidata, l'année la mieux couverte :
     163 pays), plus Libye et Afghanistan (absents de Wikidata pour 2020) et les États
     disparus URSS, RDA, Yougoslavie (RSFY, RFY), Zaïre, Irak baasiste : 3 374
     personnes. **Large** (choix de l'utilisateur) : attrape aussi Gorbatchev, Eltsine,
     Aung San Suu Kyi, Morsi, Karzaï ; une exclusion de trop ne coûte rien au jeu.
- **Les dirigeants d'un pays actuellement en guerre** restent manuels (bouton
  d'exclusion) : la liste des guerres change, aucune source stable dans Wikidata.
- **Liste chargée une fois par vague** (`WikidataClient.fetchEditorialExclusions`,
  quatre requêtes), puis écartée en Java avant tout appel à Commons : motif
  `PersonSkip.EDITORIAL_LINE`. Les requêtes de classement ne changent pas. `--qid`
  n'y échappe pas : c'est une règle, pas un ciblage.
- **Motif d'exclusion en base** (migration V10, `people.exclusion_reason`) : `MANUAL`
  (bouton de la page de curation, demandes de retrait) ou `EDITORIAL_LINE`. Distinguer
  les deux permet de revoir les exclusions automatiques si les critères changent, sans
  toucher aux demandes de retrait.
- **Commande** `--apply-editorial-line` (mode de maintenance, accepte `--dry-run`) :
  exclut les personnes stockées qui répondent aux critères (`excluded = true`, le
  trigger V4 efface leurs photos, même approuvées). Bilan : personnes par critère,
  photos effacées dont approuvées ; avec `--dry-run`, rien n'est écrit et les QID sont
  listés pour relecture.

## Phase 1 : Critères et filtre à l'ingestion
Status: Complete

- [x] `EditorialLine` : QID des crimes, alignements, idéologies, pays, et enum des
      critères.
- [x] `WikidataClient.fetchEditorialExclusions()` : QID → critères, quatre requêtes.
- [x] `IngestPeopleHandler` : liste chargée au début de chaque run, `PersonSkip.EDITORIAL_LINE`.
- [x] Tests unitaires (requêtes envoyées, assemblage, personne écartée sans appel Commons).

### Verification Plan
- `./mvnw test` → 0 échec.
- Appel réel : les quatre requêtes répondent, ~44 700 QID, < 30 s.

### Phase Summary
**Livré** : `EditorialLine` (critères, requêtes), `WikidataClient.fetchEditorialExclusions`,
`PersonSkip.EDITORIAL_LINE` vérifié en premier dans `ingest` (avant la base et Commons,
`--qid` compris).

**Vérification** : appel réel : 44 746 QID chargés en 8 s ; dry-run sur les 30
personnes les plus connues : 2 écartées par la ligne éditoriale.

## Phase 2 : Motif d'exclusion et commande
Status: Complete

- [x] `V10__add_exclusion_reason.sql` : colonne, `MANUAL` pour les exclusions
      existantes, contrainte (exclu ⇔ motif présent).
- [x] `ExclusionReason` (enum), `Person.exclusionReason` ; `ExcludePersonHandler`
      pose `MANUAL`.
- [x] `PeopleStore` : QID des personnes stockées non exclues, exclusion
      `EDITORIAL_LINE`, comptage des photos effacées.
- [x] `--apply-editorial-line [--dry-run]`, `EditorialLineReport`.
- [x] Docs : `architecture.md` (ligne éditoriale), rappel de la page de curation si
      besoin, roadmap.

### Tests
- **Unitaire** : handler (exclusion des seules personnes stockées qui répondent, dry
  run sans écriture, bilan par critère) ; runner (mode exclusif, `--dry-run` accepté).
- **Intégration** : V10 (contrainte, motif posé) ; `PeopleStore` (exclusion
  `EDITORIAL_LINE`, photos effacées par le trigger) ; `ExcludePersonHandler` pose
  `MANUAL`.
- **Mutation** : PIT sur `IngestPeople` ≥ 97 %.
- **Omises** : fonctionnel (endpoint d'exclusion inchangé hors motif, couvert par ses
  tests existants), interface (aucun écran).

### Verification Plan
- `./mvnw test`, `pnpm test` → 0 échec.
- Base locale : `--apply-editorial-line --dry-run` puis sans `--dry-run`.

### Phase Summary
**Livré** : V10 (`people.exclusion_reason`, contrainte exclu ⇔ motif), `ExclusionReason`,
`Person.exclude(reason)` (remplace `setExcluded(true)`), `ExcludePersonHandler` pose
`MANUAL`, `--apply-editorial-line [--dry-run]` (`EditorialLineReport`), rappel de la page
de curation, `architecture.md`, roadmap.

**Vérification** : backend 700/700, front 170/170. Le test de contrainte a trouvé un bug :
`NULL IN (...)` vaut `NULL`, qu'un `CHECK` laisse passer, donc une exclusion sans motif
était acceptée ; `IS NOT NULL` explicite ajouté (V10 n'était appliquée nulle part).
Base locale : dry-run puis application : 6 personnes exclues (5 dirigeants
autoritaires, 1 extrême droite), 8 photos effacées, motif `EDITORIAL_LINE` en base.

## Phase 3 : Revues et PR
Status: Not started

- [ ] `code-reviewer` (données personnelles, sourcing externe, légal) et
      `architecture-reviewer` ; corriger 🔴 et 🟠.
- [ ] Final Recap, Deployment Plan, archivage `plans/done/editorial-line.md`, PR.

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

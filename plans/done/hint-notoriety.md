# Indices plus connus : notoriété mesurée par les vues (PR 2/2)

Remplacer les indices actuels (lignes des pages d'année Wikipédia, classées par nombre de
langues) par des faits Wikidata classés selon les vues sur 12 mois de l'article du fait
lui-même, avec un plancher par catégorie et par décennie. Objectif : au moins
3 catégories jouables (5 indices ou plus) par an et par langue, 10 indices visés.
Branche `feature/hint-notoriety`, depuis `develop`.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

Le script d'essai `scripts/hints/spike_notoriety.py` (jamais versionné, supprimé) a servi
de base à `scripts/hints/extract_notoriety.py`.
Cache dans `scripts/hints/.cache/`. QLever renvoie des 429 sous charge : pas de requête
de comptage, relancer plutôt que paralléliser les requêtes SPARQL.

## Référence : 5e mesure du spike (2026-11-12)

Vues du 2025-11-01 au 2026-10-31, article de l'élément dans la langue du joueur.
Règle : par catégorie, année et langue, les 10 faits les plus vus au-dessus d'un plancher
égal à 2 % de la médiane des meilleurs faits de la décennie, et jamais sous 500 vues (FR)
ou 5 000 (EN). En FR, le manque est complété par des faits au-dessus du plancher EN, qui
ont déjà un libellé français dans Wikidata (228 indices sur 4 441).

Indices retenus, médiane par an (FR / EN) :

| Catégorie | 1930s | 1940s | 1950s | 1960s | 1970s | 1980s | 1990s | 2000s | 2010s | Années < 5 (FR/EN) |
|---|---|---|---|---|---|---|---|---|---|---|
| Sport | 1.5 / 1 | 0.5 / 0.5 | 6 / 4 | 7 / 5 | 10 | 10 / 9 | 10 | 10 | 10 | 18 / 27 |
| Monde | 9 / 8.5 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 1 / 2 |
| Science | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 0 / 0 |
| Musique | 0 | 0 | 2.5 | 10 | 10 | 10 | 10 | 10 | 10 | 24 / 24 |
| Cinéma | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 0 / 0 |
| Célébrités | 10 | 10 | 10 | 10 | 10 | 10 | 10 | 9 | 0.5 | 9 / 9 |

- Années avec au moins 3 catégories à 10 indices : 84/84 en FR et en EN.
- Au moins 4 catégories à 5 indices : FR 84/84, EN 83/84. Aucune année sous 3.
- Célébrités 2010s vide : attendu, aucun mineur n'est cité.
- Planchers calculés : de 500 (Sport/Musique anciens) à 9 708 (Cinéma 2010s) en FR,
  de 5 000 à 92 277 (Célébrités 1940s) en EN.

Défauts relevés dans les échantillons (1934, 1946, 1952, 1956), corrigés en phase 1 :
- Miss Univers classé en Sport ; « Match 10 de la Coupe du monde » (une rencontre, pas
  une édition) ;
- « Oscar de la meilleure actrice : Un tramway nommé Désir » : le lauréat lu est le film ;
- « Lancement : National Basketball Association », « Lancement : INSEE » : une
  organisation est créée, pas lancée ;
- « Découverte ou invention : 1862 Apollo » : un astéroïde ;
- cinq albums de Johnny Cash la même année ;
- le pays hôte est celui d'aujourd'hui (Russie pour un fait soviétique).

## Décisions

- **Tout vient de Wikidata.** Les textes sont des gabarits remplis par les libellés
  Wikidata (CC0), dans chaque langue. Les fichiers d'indices ne dérivent plus de
  Wikipédia : `resources/hints/README.md` passe en CC0. Les vues Wikimedia ne servent
  qu'au classement et ne sont pas redistribuées.
- **Un indice par langue** (`audience` FR ou EN), clé `notoriety-<cat>-<lang>-<qid>`
  (`-start` / `-end` pour une guerre). Un même fait peut être retenu dans une langue et
  pas dans l'autre, puisque le classement suit les vues de chaque Wikipédia.
- **Les faits FR complétés depuis l'anglais entrent approuvés.** Ce ne sont pas des
  traductions : le texte utilise le libellé français de Wikidata, exigé par la requête.
  Rien n'attend donc de relecture dans `/admin/hints` ; `science_translations.json`
  disparaît. *Hypothèse à confirmer à la validation du plan.*
- **Retrait des anciens indices par une colonne `retired_at`, pas par `REJECTED`.**
  - `REJECTED` ferait disparaître les indices des manches déjà jouées : `HintPicker.shown`
    ne montre que les `APPROVED`, si bien que l'historique et une manche en cours
    perdraient leurs indices.
  - Un indice retiré n'est plus proposé (`findEligible`), mais reste affiché là où il a
    déjà été montré. La décision de relecture (`APPROVED` / `REJECTED`) est conservée.
  - Le retrait se fait dans la même transaction que l'ajout des nouveaux, par une option
    de l'import (`app.hints.import.retire-absent`). Ainsi aucune catégorie ne reste vide
    entre le déploiement et l'import.
  - L'import ne supprime toujours rien. Une clé retirée qui revient dans les fichiers est
    réactivée.
- **Pays hôte** : écarté quand l'État n'existait pas encore comme tel l'année du fait :
  `P571` pour la plupart, et une table des États successeurs (`NOT_A_HOST`), Wikidata
  datant la Russie de 1263 (corrigé en phase 1, le test `P571` seul ne suffisait pas).
- **Musique** : au plus 2 indices par artiste et par an. **Science** : au plus 4 premiers
  vols par an (ajouté en phase 1).
- **Noms à côté d'un crime** (ligne éditoriale) : les textes ne sont plus des phrases,
  seulement des libellés d'événements. `LEFT_OUT` passe d'empreintes de texte à des QID,
  et les classes attentat, massacre et assassinat sont relues à la main en phase 4.
- **Catégorie Science** : le nom ne change pas. Elle couvre découvertes, Nobel
  scientifiques, lancements et premiers vols.
- **Les vues sont figées** à l'extraction (fenêtre de 12 mois donnée en constante). Une
  relance future reprend la mesure ; l'import n'ajoute que le nouveau.

## Phase 1 : extracteur de production
Status: Complete

- [x] Créer `scripts/hints/extract_notoriety.py` à partir du spike, en ne gardant que les
      requêtes retenues (`QUERIES` sauf POP), `text()`, `dated()`, la règle par décennie
      (`TARGET` 10, `MINIMUM` 5, `DECADE_FLOOR_SHARE` 0,02, `DECADE_ABSOLUTE` 500 / 5 000)
      et le complément FR depuis l'anglais. Retirer les options d'essai `--passes`,
      `--relative` et `--report` de l'ancien rapport.
- [x] Sortie au format de `HintFileEntry`, un fichier par catégorie dans
      `ageguessr-api/src/main/resources/hints/`, avec `sourceUrl` = page Wikidata de
      l'élément et `sourceKind` = `wikidata-notoriety`. Célébrités garde `subjectQid` et
      le texte V21.
- [x] Corrections de source :
  - Sport sans les concours de beauté (`Q58863414`), sans les rencontres (`Q16466010`)
    ni les éléments `P361` d'une édition ;
  - lauréat d'un prix d'interprétation : la personne (qualificatif `P1346`/`P1686`),
    sinon écarter la ligne ;
  - organisations (`Q43229`) en « Création : X » / « Founded: X » ;
  - Science sans corps célestes mineurs (`Q3863`, `Q6999`) ;
  - au plus 2 indices Musique par artiste et par an ;
  - pays hôte écarté quand son `P571` est postérieur à l'année.
- [x] Célébrités : garder la règle « aucun mineur » (âge ≥ 18 le jour de l'extraction) de
      `extract_wikidata.py`, et le critère `EditorialLine.java`.
- [x] `--report` : tableaux de la section Référence (médiane par décennie, planchers,
      années sous 3 catégories, complétés depuis l'anglais), `--samples Y,…` pour la
      relecture.
- [x] Supprimer `extract_events.py`, `extract_music.py`, `extract_wikidata.py`,
      `science_translations.json` et `spike_notoriety.py`. Garder `common.py` en ne
      retirant que ce qui ne sert plus.

### Verification Plan
- `python3 scripts/hints/extract_notoriety.py --report --samples 1934,1952,1975,2010`
  → au moins 3 catégories à 5 indices pour 84/84 années dans chaque langue, aucun des
  défauts listés dans les échantillons.
- `grep -E '\b(19|20)[0-9]{2}\b'` sur les textes produits → aucune ligne (HintRules
  refuserait l'indice).

### Phase Summary
`scripts/hints/extract_notoriety.py` remplace les trois extracteurs et le spike (supprimés,
avec `science_translations.json` ; `common.py` réduit à Wikidata, cache et sortie).
Écarts au plan, décidés en cours de route :
- Les tests de classe trop lourds pour QLever (corps céleste, organisation, compétition
  sportive) passent après coup, par lots, sur les seuls candidats (`CLASS_CHECKS`, 500
  sinon).
- Sport ne garde que les éditions de compétition sportive (`Q13406554`) : la classe écarte
  à elle seule Miss Univers, le Lion d'or, les référendums, les voitures « vainqueurs »
  des 24 Heures du Mans. Perte : la Partie du siècle et le But du siècle.
- Les organisations sortent de Science (« Création : NBA », INSEE, CNBC) au lieu d'y être
  reformulées : Monde garde les organisations internationales.
- Au plus 4 premiers vols par an en Science : les prototypes militaires remplissaient la
  catégorie. Science reste à 10 indices toutes les années.
- Pays hôte : Wikidata date la Russie de 1263, donc le test sur `P571` ne suffit pas.
  `NOT_A_HOST` liste les États successeurs (ex-URSS, ex-Yougoslavie, Tchéquie,
  Slovaquie) et les années où ils n'étaient pas hôtes.
- `ENGLISH_NAME` : un libellé français vandalisé dans Wikidata (une chanteuse
  britannique) est remplacé par le nom anglais.
- Films : « Sortie du film « X » » ; Palme d'or et Oscar du meilleur film rattachés au
  film, prix d'interprétation et de réalisation à la personne.
Vérification : 84/84 années avec au moins 3 catégories à 5 indices dans chaque langue,
aucune année dans un texte (contrôle sur les 8 674 indices), sortie identique à la relance.

## Phase 2 : retrait des anciens indices (backend)
Status: Complete

- [x] `V22__retire_hints.sql` : colonne `retired_at TIMESTAMPTZ NULL` sur `hints`. L'index
      partiel `hints_approved_year_category_idx` est recréé avec
      `AND retired_at IS NULL`.
- [x] `Hint.retiredAt`. `HintRepository.findEligible` exclut les retirés.
      `HintPicker.shown` ne change pas : un indice retiré déjà montré reste lisible.
- [x] `ImportHintsHandler` : avec `app.hints.import.retire-absent=true`, dans la même
      transaction, `retired_at = now` pour les clés absentes des fichiers et
      `retired_at = null` pour une clé retirée qui revient. Le rapport compte les retirés
      et les réactivés. Sans l'option, comportement actuel.
- [x] `GetHintCoverage` ne compte que les indices non retirés (c'est la couverture
      jouable, `countApproved` devient `countPlayable`). La file de relecture ignore aussi
      un indice en attente retiré.
- [x] Écran de résultat : « Source : Wikidata (CC0) » pour un indice Wikidata, « Wikipedia
      (CC BY-SA) » pour un ancien indice encore affiché dans une manche passée (déduit de
      `sourceUrl`, `round-result.ts`). Ajouté en cours de route : le texte en dur aurait
      donné une attribution fausse.
- [x] Tests :
  - unitaires `ImportHintsHandlerTest` : retrait, réactivation, option désactivée,
    décision de relecture conservée ;
  - intégration `ImportHintsIntegrationTest` (Testcontainers) : `findEligible` ignore un
    retiré, `shown` l'affiche encore, la couverture l'ignore.
  Pas de test d'interface ni e2e : aucun écran ne change, et le parcours de jeu est
  couvert par les tests existants. Pas de mutation dédiée : la logique est un filtre
  de requête, couvert par l'intégration.

### Verification Plan
- `cd ageguessr-api && ./mvnw clean test` → tout passe.
- `cd frontend && pnpm test` → tout passe (aucun changement attendu côté front).

### Phase Summary
V22 (`retired_at`), `findEligible` et la couverture (`countPlayable`) ignorent les
retirés, la file de relecture aussi ; `HintPicker.shown` les montre encore. Option
`app.hints.import.retire-absent` (`HINTS_RETIRE_ABSENT`), retrait par lots de 1 000 clés,
une ligne refusée ne retire pas son indice. Ajout hors plan : l'écran de résultat déduit
la source et la licence de `sourceUrl` (Wikidata CC0 / Wikipedia CC BY-SA).
Vérification : `./mvnw test` 1 214 tests, `pnpm test` 425 tests, tout passe.

## Phase 3 : génération des fichiers
Status: Complete

- [x] Vider les caches `spike2-*` et lancer l'extraction des 6 catégories vers
      `ageguessr-api/src/main/resources/hints/`.
- [x] Lancer l'import en local avec `retire-absent` sur une base qui contient les anciens
      indices : 0 refus, environ 8 900 ajoutés, tous les anciens retirés.

### Verification Plan
- Import local : rapport sans `refused`, puis `/admin/hints` (couverture) → aucune
  année sous 3 catégories en FR comme en EN.

### Phase Summary
8 674 indices (FR 4 382, EN 4 292), 240 indices FR complétés depuis l'anglais. Import
simulé sur Postgres (Testcontainers, test jetable non versionné) : anciens fichiers de
`develop` importés (7 978), puis nouveaux avec `retire-absent` → 8 674 ajoutés,
7 978 retirés, 0 refus, 8 674 indices jouables.

Indices réellement livrés, par décennie (1930s = 1932-1939, 2010s = 2010-2015 ;
Célébrités s'arrête en 2008, aucun mineur) :

FR

| Catégorie | 1930s | 1940s | 1950s | 1960s | 1970s | 1980s | 1990s | 2000s | 2010s | Total | Années ≥ 5 | Années à 10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Sport | 11 | 15 | 50 | 76 | 87 | 89 | 96 | 100 | 60 | 584 | 62/84 | 38/84 |
| Monde | 65 | 100 | 87 | 96 | 100 | 95 | 100 | 100 | 60 | 803 | 83/84 | 73/84 |
| Science | 76 | 89 | 90 | 100 | 100 | 100 | 100 | 100 | 60 | 815 | 84/84 | 74/84 |
| Musique | 1 | 0 | 37 | 92 | 100 | 100 | 100 | 100 | 60 | 590 | 60/84 | 54/84 |
| Cinéma | 80 | 100 | 100 | 100 | 100 | 100 | 100 | 100 | 60 | 840 | 84/84 | 84/84 |
| Célébrités | 80 | 100 | 100 | 100 | 100 | 100 | 100 | 70 | 0 | 750 | 75/84 | 73/84 |
| **Total** | 313 | 404 | 464 | 564 | 587 | 584 | 596 | 570 | 300 | **4 382** | | |

Catégories jouables par année (FR) : jamais moins de 4 ; 4 catégories 21 ans,
5 catégories 14 ans, 6 catégories 49 ans.

EN

| Catégorie | 1930s | 1940s | 1950s | 1960s | 1970s | 1980s | 1990s | 2000s | 2010s | Total | Années ≥ 5 | Années à 10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Sport | 6 | 12 | 36 | 60 | 84 | 86 | 91 | 99 | 60 | 534 | 54/84 | 30/84 |
| Monde | 62 | 99 | 85 | 96 | 95 | 91 | 100 | 100 | 60 | 788 | 82/84 | 68/84 |
| Science | 75 | 88 | 90 | 100 | 100 | 100 | 100 | 100 | 60 | 813 | 84/84 | 74/84 |
| Musique | 1 | 0 | 36 | 91 | 100 | 100 | 100 | 100 | 60 | 588 | 60/84 | 54/84 |
| Cinéma | 80 | 100 | 100 | 100 | 100 | 100 | 100 | 100 | 60 | 840 | 84/84 | 84/84 |
| Célébrités | 61 | 99 | 100 | 100 | 100 | 100 | 100 | 69 | 0 | 729 | 74/84 | 66/84 |
| **Total** | 285 | 398 | 447 | 547 | 579 | 577 | 591 | 568 | 300 | **4 292** | | |

Catégories jouables par année (EN) : au moins 3 ; 3 catégories 2 ans, 4 catégories
22 ans, 5 catégories 16 ans, 6 catégories 44 ans.

## Phase 4 : relecture manuelle d'un échantillon
Status: Complete

- [x] Une année par décennie, toutes catégories, dans les deux langues : formulation,
      exactitude de l'année, notoriété.
- [x] Monde : relire les classes attentat, massacre et assassinat. Ajouter dans
      `LEFT_OUT` (par QID) tout fait qui nomme une personne vivante à côté d'un crime.
- [x] Noter les défauts restants et les corriger dans l'extracteur (retour à la phase 3).

### Verification Plan
- Liste des faits relus et de leur verdict, ajoutée au Phase Summary.

### Phase Summary
Relu en FR : 1934, 1938, 1946, 1949, 1952, 1961, 1975, 1983, 1997, 2006, 2010, 2014 ;
en EN : 1934, 1946, 1952, 1956, 1967, 1989. Défauts trouvés et corrigés (voir phase 1) :
Lion d'or et référendums en Sport, organisations en Science, avions militaires en
surnombre, pays hôte Russie avant 1991, libellé vandalisé, équipe de cricket non
raccourcie.
Monde, faits liés à un crime (~140 lignes relues, motif assassinat / massacre /
attentat / affaire / meurtre / fusillade…) : deux nomment une personne vivante (victimes
d'un enlèvement à Cleveland, affaire Blanchard) → `LEFT_OUT`. Les autres nomment un lieu,
un groupe ou une victime décédée. `LEFT_OUT` écarte aussi deux fausses découvertes
(une figure du folklore, une phrase).
Restes acceptés : un interprète parfois discutable (la reprise la plus liée l'emporte,
« Do-Re-Mi — Fergie »), quelques doublons proches (« Cour de justice » et « Cour de
justice de l'Union européenne »).

## Phase 5 : documentation et PR
Status: Complete

- [x] `docs/decisions/architecture.md` : la décision des indices est réécrite (sources
      Wikidata, vues annuelles, plancher par décennie, complément FR, `retired_at`, CC0).
      Les paragraphes « Sport et Célébrités, écarts » et « Relecture limitée aux
      traductions » sont mis à jour.
- [x] `scripts/hints/README.md` (commande unique, relance annuelle de Célébrités) et
      `resources/hints/README.md` (licence CC0).
- [x] `docs/roadmap.md` : cocher la ligne « Indices plus connus », ajouter la vérification
      en prod (import avec `retire-absent`, couverture).
- [x] Revues `code-reviewer` (sourcing externe) et `architecture-reviewer`, puis `/pr`.

### Verification Plan
- `./mvnw test` et `pnpm test` passent. Revues sans point 🔴 ni 🟠 ouvert.

### Phase Summary
Décision réécrite dans `docs/decisions/architecture.md`, README des scripts et des
fichiers d'indices (CC0), roadmap cochée avec la vérification en prod. Revues : aucun 🔴
ni 🟠 côté sécurité ; côté architecture, deux 🟠 corrigés (commentaire orphelin dans
`round-result.ts`, ligne vide dans un test). Mineurs corrigés aussi : URL invalide gérée
par `URL.canParse`, `@Modifying(clearAutomatically = true)`, Javadoc de `GameHint`.
Laissé : `source()` appelé dans le template plutôt qu'un pipe (1 à 3 appels par écran).
`./mvnw test` 1 214 tests et `pnpm test` 425 tests passent.

## Final Recap
Les indices viennent désormais de Wikidata, classés par les vues sur 12 mois de l'article
de chaque fait : 8 674 indices (FR 4 382, EN 4 292), au moins 3 catégories jouables chaque
année dans les deux langues (FR : jamais moins de 4). Un seul extracteur
(`scripts/hints/extract_notoriety.py`) remplace les trois anciens. Les anciens indices
sont retirés par l'import (`retired_at`, V22) sans disparaître des manches passées.
L'écran de résultat attribue chaque indice selon sa source. Limites : Sport avant 1950
et Musique avant 1955 restent presque vides ; les vues d'une fenêtre de 12 mois peuvent
être gonflées par l'actualité.

## Deployment Plan
1. Merger la PR dans `develop`, puis la release `develop → main` (merge commit). Flyway
   applique V22 au démarrage (colonne nullable, index recréé : rapide).
2. Une fois l'API déployée, lancer l'import en prod avec le retrait, dans la même
   commande que d'habitude plus `--app.hints.import.retire-absent=true` (ou
   `HINTS_RETIRE_ABSENT=true`). Rapport attendu : ~8 674 ajoutés, les anciens indices
   retirés (7 978 au plus), 0 refus.
3. `/admin/hints` : couverture sans année sous 3 catégories, file de relecture vide.
4. En jeu : un indice affiche « Source : Wikidata (CC0) » ; une manche jouée avant
   l'import garde ses indices (« Wikipedia (CC BY-SA) »).
Retour arrière : relancer l'import avec les anciens fichiers et `retire-absent` réactive
les anciennes clés et retire les nouvelles ; rien n'est supprimé.

# Cibler une vague d'ingestion (professions, cases, âges)

La première passe d'ingestion en prod (2026-10-27) a ramené beaucoup de footballeurs, et
une photo en maillot donne beaucoup d'indices. Plus généralement, chaque nouvelle vague
doit pouvoir **viser ce qui manque** (cases continent × genre, tranches d'âge) et
**écarter ce qui revient trop** (une profession). L'ingestion reçoit donc des options de
ciblage en ligne de commande, et son rapport compte les professions des personnes gardées
pour décider avec des chiffres (cinéma, politique). Rien de ce qui est déjà en base n'est
rejeté ni supprimé.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions
- **Options de ligne de commande, pas de propriétés** : le ciblage change d'une vague à
  l'autre, il se lit dans la commande lancée (et dans le log de démarrage, qui affiche la
  commande). Options, toutes combinables, valeurs séparées par des virgules :
  - `--exclude-occupations=Q937857` : écarte toute personne ayant une de ces professions
    (`P106`), même avec une autre carrière (George Weah) : Wikidata ne dit pas quelle
    profession illustre la photo ;
  - `--only-occupations=Q…` : ne garde que les personnes ayant une de ces professions ;
  - `--cells=AFRICA/FEMALE,OCEANIA/MALE` : ne parcourt que ces cases ;
  - `--age-bands=66-73,74-81,82+` : ne garde que les photos de ces tranches.
- **Les filtres de profession sont dans la requête de classement QLever** (`PAGE` et
  `CELL_PAGE`) : les personnes écartées ne sont jamais téléchargées ni examinées, donc
  aucun coût, et n'apparaissent pas dans le rapport. Les QID sont validés
  (`WikidataId.isValid`) avant d'entrer dans le texte de la requête.
- **Le ciblage d'âge agit à deux endroits** : dans la requête, une année de naissance
  maximale (il faut avoir au moins l'âge de la plus jeune tranche visée aujourd'hui :
  `--age-bands=74-81,82+` → né en 1952 ou avant en 2026), ce qui évite d'examiner des
  personnes trop jeunes ; puis au choix des photos, une photo valide hors des tranches
  visées est écartée avec le motif `AGE_BAND_NOT_WANTED` (compté dans le rapport).
- **Cases** : sans `--cells`, la relance remplit déjà seulement les cases sous leur quota
  (photos non rejetées en base). `--cells` sert à restreindre davantage. Les quotas ne
  changent pas : pour ajouter des photos âgées au-delà de 5 000, relever `--limit`.
- **Rapport** : `kept people by occupation`, les 15 professions les plus fréquentes chez
  les personnes gardées (une personne compte pour chacune de ses professions), en QID.
  La profession vient de la requête `PROFILES` (un `UNION` de plus, sans requête
  supplémentaire).
- **`--qid` refuse toute option de ciblage** : nommer quelqu'un est un choix délibéré.
  `--sample` (classement simple) refuse `--cells`, qui n'a de sens qu'avec les cases.
- **Cinéma et politique** : pas d'exclusion décidée. Contrairement au maillot de foot
  (sponsors, modèles datés), un tapis rouge ou une tribune donnent peu d'indices sur la
  date. Mesurer d'abord leur part avec le nouveau compteur.

QID utiles (Wikidata) : `Q937857` joueur de football, `Q33999` acteur, `Q10800557`
acteur de cinéma, `Q82955` homme / femme politique, `Q177220` chanteur, `Q36180`
écrivain, `Q2066131` sportif.

**Hypothèses** (non bloquantes) :
- Les sous-classes ne sont pas suivies (`wdt:P106`, pas `wdt:P106/wdt:P279*`) : Wikidata
  met `Q937857` directement sur les footballeurs, et le chemin `P279*` ralentirait la
  requête.
- Les footballeurs déjà en base ne remontent plus dans les classements filtrés : la
  relance ne leur ajoute pas de photo, et leurs photos non rejetées continuent de compter
  dans le quota de leur case.

## Phase 1 : Options de ciblage
Status: Complete

- [x] `AgeBand.ALL` (libellés des 9 tranches) et `AgeBand.start(label)`.
- [x] `IngestFocus` (record) : professions exclues, professions seules, cases, tranches ;
      validation à la construction (QID valides, tranches connues, une profession pas à
      la fois exclue et seule) ; `NONE`.
- [x] `RankingFilter` (record) : années de naissance min / max, professions exclues /
      seules ; produit les clauses SPARQL (aucune si rien à filtrer).
- [x] `WikidataClient` : `fetchPeople` et `fetchCell` reçoivent un `RankingFilter` ;
      `PROFILES` ramène aussi `P106` ; `PersonRecord.occupations`.
- [x] `IngestPeopleCommand.focus` ; `IngestPeopleRunner` lit les quatre options et
      refuse les combinaisons sans sens.
- [x] `IngestPeopleHandler` : cases filtrées, `RankingFilter` construit depuis les
      propriétés, le focus et la date du jour, photos hors tranches écartées
      (`PhotoRejection.AGE_BAND_NOT_WANTED`).
- [x] `IngestReport` : professions des personnes gardées (top 15).
- [x] Docs : `docs/decisions/architecture.md` (sourcing), `docs/roadmap.md`.

### Tests
- **Unitaire** :
  - `IngestFocusTest` : QID invalide, tranche inconnue, profession exclue et seule à la
    fois → refus ; `includes(cell)` et `wantsAge(age)` avec et sans ciblage.
  - `RankingFilterTest` : aucune clause sans filtre ; clause d'exclusion, de profession
    seule, d'année maximale ; QID triés (texte de requête stable).
  - `WikidataClientTest` : les requêtes de `fetchCell` et `fetchPeople` contiennent les
    clauses ; `fetchPerson` n'en a pas ; les professions arrivent dans `PersonRecord`.
  - `IngestPeopleRunnerTest` : lecture des quatre options, refus (QID invalide, case
    inconnue, tranche inconnue, ciblage avec `--qid`, `--cells` avec `--sample`).
  - `IngestPeopleHandlerTest` : seules les cases ciblées sont parcourues ; année maximale
    tirée de la plus jeune tranche ; photo hors tranche écartée et comptée.
  - `IngestReportTest` : top des professions, trié par nombre décroissant.
- **Mutation** (PIT sur `IngestPeople`) : test strength au moins égale à celle de
  `feature/people-pool-balance`.
- **Omises** : intégration (aucune requête JPA ni migration), fonctionnel (aucun
  endpoint), interface et QA e2e (aucun écran), ArchUnit (aucune nouvelle dépendance
  entre packages).

### Edge cases
- Aucune option → requêtes identiques à aujourd'hui (tests existants inchangés).
- Valeur malformée ou tentative d'injection SPARQL (`Q1 }`, `foo`) → refus au démarrage.
- `--cells` avec une case « autre » (`EUROPE/OTHER`) → acceptée.
- `--age-bands=82+` seul → né au plus tard en `année courante - 82`.
- Personne avec plusieurs professions dont une exclue → écartée.
- QLever : les clauses ne doivent pas faire dépasser la limite de 30 s, offsets profonds
  compris.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → 0 échec.
- `cd frontend && pnpm test` → 0 échec (rien ne change côté front, règle avant push).
- PIT sur `IngestPeople` → test strength rapportée dans la Phase Summary.
- Mesure réelle sans écriture (`--dry-run`), même échantillon sans puis avec
  `--exclude-occupations=Q937857` : `Q937857` disparaît du top des professions, temps
  des requêtes de classement sous 30 s.

### Phase Summary
**Livré** : `IngestFocus`, `RankingFilter`, options `--exclude-occupations`,
`--only-occupations`, `--cells`, `--age-bands`, motif `AGE_BAND_NOT_WANTED`, `P106` dans
`PROFILES`, top 15 des professions dans le rapport. Docs : `architecture.md`, roadmap.

**Vérification** : backend 672/672. Mesure réelle 2026-10-28, `--offset=10000
--sample=150 --dry-run`, sans puis avec `--exclude-occupations=Q937857` :

| | Sans filtre | Avec filtre |
|---|---|---|
| Durée | 414 s | 442 s |
| Personnes gardées / photos | 105 / 126 | 111 / 131 |
| Footballeurs (`Q937857`) parmi les gardées | 32 (30 %) | 0 |
| Entraîneurs (`Q628099`) | 12 | absents du top 15 (anciens joueurs) |
| Acteurs (`Q33999`) | 29 | 52 (47 %) |

Aucune réponse 429 de QLever, durée comparable : la clause `FILTER NOT EXISTS` ne pèse
pas sur le classement. Sans les footballeurs, le cinéma domine (acteur `Q33999`, acteur
de cinéma `Q10800557`, acteur de télévision `Q10798782`, scénariste `Q28389`,
réalisateur `Q2526255`) : à surveiller dans le rapport de la prochaine vague, avant de
décider d'un plafond ou d'une exclusion.

## Phase 2 : Quotas au taux d'approbation et `--analyze`
Status: Complete

Demande de l'utilisateur (2026-10-28) : analyser automatiquement les photos approuvées
pour régler la prochaine vague, en tenant compte du fait que ~40 % des photos seulement
passeront la curation. But : des ordres de grandeur semblables, pas la photo près.
Décision de l'utilisateur : les quotas comptent les photos en attente au taux attendu.

- [x] `PersonRepository.countPhotosByCell` et `PersonPhotoRepository.countByAge` groupés
      aussi par statut de revue ; `ReviewCounts`, `PoolCounts`,
      `PeopleStore.poolCounts()`.
- [x] `ApprovalRate` : observé dès 300 photos revues (approuvées / (approuvées +
      rejetées)), sinon `app.people.ingest.expected-approval-rate` (0,4).
- [x] `IngestPeopleHandler.balance` : une case part de approuvées + en attente × taux,
      chaque photo ajoutée compte pour le taux ; le taux est logué au démarrage.
- [x] `PoolAnalysis` et `--analyze` (lecture seule, `--limit` comme cible, options Spring
      acceptées) : tableau par case et par tranche, taux, prochaine vague. Une seule
      vague suggérée à la fois : la vague des cases déplace la médiane des tranches.
- [x] Tests : `ApprovalRateTest`, `PoolAnalysisTest`, handler (taux 0,5, analyse sans
      appel réseau), runner, `PeopleStoreIntegrationTest` (comptes par statut).
- [x] Essai réel de `--analyze` sur la base locale (75 photos en attente, rien de revu) :
      taux supposé 0,40, ~12 461 photos à collecter pour les cases, 82+ « lacking ».

### Verification Plan
- `./mvnw test` → 0 échec.
- `--analyze` sur la base locale → tableau complet et prochaine vague.

### Phase Summary
**Livré** : comptages par statut de revue (`ReviewCounts`, `PoolCounts`), `ApprovalRate`
(observé dès 300 photos revues dont au moins une approuvée, sinon 0,4), quotas des
cases en photos approuvées attendues, `PoolAnalysis` et `--analyze`.

**Vérification** : `--analyze` sur la base locale (75 photos en attente) : taux supposé
0,40, ~12 461 photos à collecter pour les cases, 82+ signalée « lacking ». Le premier
essai a révélé que `--spring.profiles.active` (passé aussi en prod) était refusé :
corrigé, seules les options d'ingestion sont refusées avec `--analyze`. Une seule
vague suggérée à la fois (la vague des cases déplace la médiane des tranches).

## Phase 3 : Revues et PR
Status: Complete

- [x] `code-reviewer` (sourcing externe : texte SPARQL construit depuis la ligne de
      commande) et `architecture-reviewer` ; corriger tous les 🔴 et 🟠.
- [x] Final Recap, Deployment Plan, archivage dans `plans/done/ingest-focus.md`.
- [x] PR `feature/exclude-footballers` → `develop`.

### Verification Plan
- Toutes les suites à 0 échec après les corrections de revue.

### Phase Summary
**Revues** : aucun 🔴. `architecture-reviewer` : 4 🟠 corrigés (ordre d'imports,
javadocs qui parlaient encore de photos « en jeu » dans `IngestPeopleCommand`, `Cell`
et `Run`) ; 🟡 appliqués : commentaire sur le taux jamais nul, `ReviewCountsTest`
séparé, test du taux observé à 1,0 ; 🟡 non appliqués : `PoolAnalysis` en classe
plutôt qu'en record (méthodes dérivées et rendu texte), double validation des QID
(défense en profondeur voulue). `code-reviewer` : injection SPARQL bloquée par
`WikidataId` à deux niveaux, aucun nom dans les logs, professions jamais stockées ;
1 🟡 corrigé : un taux observé de 0 (300 photos revues, aucune approuvée) bloquait le
remplissage des cases et faisait diviser `--analyze` par zéro, il retombe sur le taux
supposé.

**Vérification finale** : backend **675/675**, front **168/168**. PIT sur
`IngestPeople` : test strength **97 %** (571 / 598 ; 95 % sur
`feature/people-pool-balance`). Survivants du nouveau code : une borne équivalente dans
`PoolAnalysis.toCollect` (0 manquant donne 0 à collecter des deux côtés) ; le libellé
« lacking » et `bands()` ont reçu une assertion après le run.

## Final Recap
**Livré** (PR `feature/exclude-footballers` → `develop`) : des vagues d'ingestion qui
visent ce qui manque au pool et écartent ce qui revient trop.
- **Ciblage** : `--exclude-occupations`, `--only-occupations` (QID de `P106`, filtrés
  dans la requête QLever), `--cells`, `--age-bands` (année de naissance maximale dans
  la requête, photos hors tranches écartées en `AGE_BAND_NOT_WANTED`).
- **Footballeurs** : 30 % des personnes gardées au rang 10 000, 0 avec le filtre, sans
  surcoût mesurable.
- **Professions** : top 15 dans le rapport. Sans les footballeurs, le cinéma domine
  (47 % d'acteurs dans l'échantillon) : à surveiller avant de décider.
- **Taux d'approbation** : quotas en photos approuvées attendues (en attente × taux,
  observé dès 300 photos revues, 0,4 avant), pour converger en une vague.
- **`--analyze`** : bilan par case et par tranche, sans appel réseau, et la prochaine
  vague à lancer.

**Écarts par rapport au plan initial, tous décidés avec l'utilisateur** :
- le filtre « footballeurs » en propriété est devenu des options de ligne de commande
  génériques (demande de l'utilisateur : se concentrer sur ce qui manque) ;
- le taux d'approbation et `--analyze` sont ajoutés (demande en cours de route ; choix
  de l'utilisateur : compter les photos en attente au taux) ;
- aucune exclusion du cinéma ni de la politique : décision reportée aux chiffres.

**Hors scope, à faire ensuite** :
- Décider d'un plafond ou d'une exclusion pour le cinéma, sur le rapport de la
  prochaine vague.
- Extraire l'ingestion équilibrée de `IngestPeopleHandler` (déjà notée par la PR
  précédente ; le handler a encore grossi).

## Deployment Plan
1. **Merge de la PR dans `develop`**, puis release `develop` → `main` en **merge
   commit**. Aucune migration. Variable optionnelle nouvelle :
   `INGEST_EXPECTED_APPROVAL_RATE` (0,4 par défaut), inutile de la définir.
2. **Changement de sens de la cible** : `--limit` (5 000) compte désormais les photos
   approuvées attendues. Les 5 013 photos de la première passe, toutes en attente,
   valent ~2 005 photos attendues au taux supposé : une vague lancée sans curation
   collecterait encore ~7 500 photos (~7 h, ~1,3 Go). **Curer d'abord au moins 300
   photos** pour que le taux observé remplace le taux supposé.
3. **Bilan** (depuis le VPS, lecture seule, quelques secondes) :
   `cd /opt/ageguessr && docker compose -f docker-compose.prod.yml run --rm backend --spring.profiles.active=prod,ingest --analyze`
4. **Vague suivante** : la commande suggérée par `--analyze`, plus l'exclusion des
   footballeurs, dans `tmux` :
   `cd /opt/ageguessr && docker compose -f docker-compose.prod.yml run --rm backend --spring.profiles.active=prod,ingest --limit=5000 --exclude-occupations=Q937857`
   Vérifier l'espace disque du VPS avant (174 Ko par photo collectée). Lire dans le
   rapport `kept people by occupation` et `balance (photos/quota)`.
5. **Ensuite** : relancer `--analyze`. Pas besoin de curer entre deux vagues (les photos
   en attente comptent au taux), mais la curation affine le taux. Quand les cases sont
   pleines, `--analyze` propose la vague des tranches d'âge rares
   (`--age-bands=74-81,82+` et une cible relevée).

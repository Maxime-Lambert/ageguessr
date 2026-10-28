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
Status: Not started

- [ ] `AgeBand.ALL` (libellés des 9 tranches) et `AgeBand.start(label)`.
- [ ] `IngestFocus` (record) : professions exclues, professions seules, cases, tranches ;
      validation à la construction (QID valides, tranches connues, une profession pas à
      la fois exclue et seule) ; `NONE`.
- [ ] `RankingFilter` (record) : années de naissance min / max, professions exclues /
      seules ; produit les clauses SPARQL (aucune si rien à filtrer).
- [ ] `WikidataClient` : `fetchPeople` et `fetchCell` reçoivent un `RankingFilter` ;
      `PROFILES` ramène aussi `P106` ; `PersonRecord.occupations`.
- [ ] `IngestPeopleCommand.focus` ; `IngestPeopleRunner` lit les quatre options et
      refuse les combinaisons sans sens.
- [ ] `IngestPeopleHandler` : cases filtrées, `RankingFilter` construit depuis les
      propriétés, le focus et la date du jour, photos hors tranches écartées
      (`PhotoRejection.AGE_BAND_NOT_WANTED`).
- [ ] `IngestReport` : professions des personnes gardées (top 15).
- [ ] Docs : `docs/decisions/architecture.md` (sourcing), `docs/roadmap.md`.

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
_(write when phase completes)_

## Phase 2 : Revues et PR
Status: Not started

- [ ] `code-reviewer` (sourcing externe : texte SPARQL construit depuis la ligne de
      commande) et `architecture-reviewer` ; corriger tous les 🔴 et 🟠.
- [ ] Final Recap, Deployment Plan, archivage dans `plans/done/ingest-focus.md`.
- [ ] PR `feature/exclude-footballers` → `develop`.

### Verification Plan
- Toutes les suites à 0 échec après les corrections de revue.

### Phase Summary
_(write when phase completes)_

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

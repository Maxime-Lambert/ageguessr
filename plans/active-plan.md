# Équilibre du pool de personnes et mesure du rendement

Mesurer combien de personnes et de photos les règles d'ingestion donnent hors des
personnes très connues, puis ingérer un pool **équilibré entre hommes et femmes et
entre continents**, **sans aucune photo d'une personne de moins de 18 ans**. Wikidata
reste la seule source tant qu'elle donne assez de photos.

> Branche `feature/people-pool-balance`, depuis `develop` (curation mergée, PR #18).

## For Future Agents
Au fil du travail : cocher les cases `- [x]`. Quand une phase est finie, passer son
statut à `Complete` et écrire son **Phase Summary**. Lancer le **Verification Plan** de
la phase et noter le résultat avant de passer à la suivante. Ce fichier est la source de
vérité, pas la conversation.

## Décisions de l'utilisateur (2026-10-26)
- Tout le monde un tant soit peu connu convient : la notoriété ne sert qu'à l'ordre de
  traitement.
- **Égalité hommes / femmes.**
- **Égalité entre origines**, approchée par le **continent de nationalité** (Wikidata
  `P27` → `P30`). Pas le groupe ethnique (`P172`) :
  - renseigné pour ~1 % des candidats seulement (8 520 sur 737 094) ;
  - donnée sensible au sens de l'art. 9 du RGPD (origine ethnique).

  Limite assumée : la nationalité n'est pas l'origine, et certains continents sont très
  mélangés.
- **Aucune photo d'une personne mineure** (âge calculé < 18 ans), même si la personne
  est adulte aujourd'hui.
- Pas d'autre source que Wikidata tant que le volume suffit. Chercher les dates de
  naissance ailleurs est remis à plus tard (RGPD plus fragile, sources rarement plus
  précises).

## Chiffres de départ (QLever, 2026-10-26)
Humains avec une image `P18`, nés en 1930 ou après : **737 094**, dont **636 961** avec
une naissance au jour.

| Genre (`P21`) | Personnes |
|---|---|
| Masculin | 540 773 |
| Féminin | 192 241 |
| Autres valeurs (non binaire, intersexe, femme trans, homme trans…) | ~1 550 |
| Non renseigné | 2 472 |

| Continent de nationalité | Personnes (une personne peut compter deux fois) |
|---|---|
| Europe | 373 681 |
| Amérique du Nord | 145 587 |
| Asie | 139 434 |
| Amérique du Sud | 40 312 |
| Eurasie | 24 524 |
| Afrique | 24 003 |
| Océanie | 16 975 (+ 467 « Insular Oceania ») |
| Sans nationalité | 52 623 |

Inconnue principale : la part de personnes qui ont au moins une photo à date
corroborée par l'EXIF. ~30 % sur les 400 plus connues (spike de
`plans/done/people-sourcing.md`), inconnue plus bas dans la liste et selon les
continents.

## Hypothèses
- **6 continents** : Afrique, Amérique du Nord, Amérique du Sud, Asie, Europe, Océanie.
  « Eurasie » compte comme Asie, « Insular Oceania » comme Océanie.
- **Plusieurs nationalités sur plusieurs continents** : la personne compte pour le
  continent le moins représenté, dans l'ordre fixe Océanie, Afrique, Amérique du Sud,
  Asie, Amérique du Nord, Europe. C'est déterministe et favorise l'équilibre.
- **Sans nationalité** : hors quotas, donc pas ingérée par la liste équilibrée
  (toujours possible avec `--qid`).
- **Genres autres qu'homme / femme** : ni exclus ni comptés dans le quota
  hommes / femmes. Une petite case « autre » par continent, prise en entier (~1 550
  personnes au total, avant le filtre photo).
- **Parts égales** : 6 continents × 2 genres = 12 cases d'une part égale de la cible
  (3 000 → 250 par case). Une case épuisée reste incomplète et le rapport le dit ; on
  ne reporte pas sa part ailleurs sans décision (à revoir après la mesure, Phase 2).
- L'équilibre porte sur les **personnes ingérées**, pas sur les photos approuvées. Les
  rejets de curation peuvent le décaler ; un compteur par case sur la page de curation
  est hors périmètre (à proposer si l'écart se voit).

## Phase 1 : Pas de photo de mineur
Status: Complete

- [x] `PhotoEvaluator` : `MIN_AGE = 18`, une photo d'âge calculé < 18 est rejetée
      avec un nouveau motif `PhotoRejection.MINOR` (compté à part dans le rapport, pour
      la mesure).
- [x] Migration `V8__forbid_minor_photos.sql` : supprime les photos existantes d'âge
      < 18 (aucune en prod, l'ingestion n'y a pas tourné ; quelques-unes en local),
      puis `CHECK (age_at_photo >= 18)` sur `person_photos` comme filet.
- [x] Rappel sur la page de curation : rejeter une photo où la personne paraît
      mineure (date fausse malgré l'EXIF).
- [x] `/privacy` section 8 (EN et FR) : aucune photo d'une personne mineure.

### Verification Plan
- `./mvnw test` : `PhotoEvaluatorTest` couvre 17 ans 364 jours (rejet), 18 ans pile
  (gardée) ; test d'intégration : l'insertion d'une photo d'âge 17 viole la contrainte.
- `docker compose up -d postgres` puis démarrage de l'API : V8 passe sur la base
  locale, `SELECT count(*) FROM person_photos WHERE age_at_photo < 18` → 0.

### Phase Summary
**Livré** :
- `PhotoEvaluator.MIN_AGE = 18` : une photo d'âge calculé < 18 est rejetée en `MINOR`,
  après le contrôle `AMBIGUOUS_AGE`. Une date en intervalle à cheval sur le 18e
  anniversaire reste donc `AMBIGUOUS_AGE` (testé), et une photo du jour de naissance
  passe la borne « pendant la vie » puis tombe en `MINOR`. Le rapport compte `MINOR`
  sans changement de code : il compte déjà les rejets par motif.
- `V8__forbid_minor_photos.sql` : supprime les photos d'âge < 18 (les images suivent
  par `ON DELETE CASCADE`), puis remplace `person_photos_age_range` par
  `BETWEEN 18 AND 120`.
- Page de curation : « Reject a photo where the person looks under 18: its date is
  wrong, even though the EXIF agreed. »
- `/privacy` section 8, EN et FR : jamais de photo prise avant les 18 ans de la
  personne. `PRIVACY_UPDATED` passe au 2026-10-27.

**Correction en passant** : le test Playwright `curation.spec.ts` « reviews photos from
the keyboard and can undo » était instable (3 échecs sur 8 runs). Il appuyait sur U
pendant qu'une décision était en cours, et la page ignore alors les raccourcis. Il
attend maintenant « Nothing left to review. » avant U, et la requête d'annulation par
`expect.poll`. 60/60 sur 15 répétitions.

**Vérification** :
- backend **565/565** (+5 : 17 ans et 364 jours rejetée, 18 ans pile gardée,
  intervalle à cheval sur le 18e anniversaire, contrainte à 17 refusée et à 18 acceptée
  en base) ;
- front **167/167**, lint, `tsc` et Prettier OK, Playwright component **42/42** ;
- base locale : V8 appliquée, 0 photo d'âge < 18, contrainte
  `CHECK (age_at_photo >= 18 AND age_at_photo <= 120)`.

## Phase 2 : Mesure du rendement (sans écriture en base)
Status: Not started

- [ ] `IngestPeopleRunner` : options `--offset=<rang>` (début de la liste) et
      `--dry-run` (toutes les règles, téléchargement et ré-encodage compris, mais rien
      n'est écrit). Le dry-run est porté par `IngestPeopleCommand` ; `PeopleStore`
      n'est pas appelé.
- [ ] `IngestReport` : en plus des compteurs actuels, répartition par genre et par
      continent des personnes vues et gardées, et nombre moyen de photos par personne
      gardée.
- [ ] Récupération du genre (`P21`) et du continent (`P27` → `P30`) : nouvelle
      requête de détail dans `WikidataClient`, restreinte aux QID de la page comme les
      quatre autres ; règles de continent dans une classe pure `ContinentResolver`.
- [ ] Lancer 4 échantillons de 200 personnes : rangs 0, 10 000, 100 000, 500 000.
      Noter les rapports dans ce plan.
- [ ] Analyse : rendement par profondeur, par genre, par continent ; temps par
      personne ; taille disque par photo. En déduire si 250 personnes par case sont
      atteignables, et la cible réaliste. **Présenter les chiffres à l'utilisateur avant
      la Phase 3.**

### Verification Plan
- `./mvnw test` : tests unitaires `ContinentResolver` (Eurasie → Asie, plusieurs
  continents → le moins représenté, aucun → vide), `IngestPeopleRunnerTest`
  (`--offset`, `--dry-run`, valeurs invalides), `IngestPeopleHandlerTest` (dry-run :
  `PeopleStore` jamais appelé), `WikidataClientTest` (requête de détail genre /
  continent).
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest -Dspring-boot.run.arguments="--offset=10000 --limit=200 --dry-run"`
  → rapport loggé ; `SELECT count(*) FROM people` inchangé avant / après.

### Phase Summary
_(à écrire)_

## Phase 3 : Ingestion équilibrée
Status: Not started

- [ ] Migration `V9__add_person_gender_and_continent.sql` : colonnes `gender`
      (`MALE` / `FEMALE` / `OTHER`) et `continent` sur `people`, nullable (personnes
      déjà ingérées : remplies à leur prochain passage).
- [ ] `Person` : champs `gender`, `continent` (enums `Gender`, `Continent` dans
      `features/people`).
- [ ] `WikidataClient.fetchPeople` : classement par case, avec filtres `P21` et
      continent dans la requête de classement QLever (mesurer le temps, limite de
      30 s).
- [ ] `IngestPeopleHandler` : traite les 12 cases (+ cases « autre ») à tour de rôle
      jusqu'à leur quota, en comptant les personnes déjà en base de la case, pour que
      la relance reste idempotente.
- [ ] Rapport : quota, gardées et état « épuisée » par case.
- [ ] Docs : `architecture.md` (sourcing : équilibre, raison du continent plutôt que de
      l'ethnie, mineurs), `roadmap.md`, `/privacy` section 8 (genre et continent de
      nationalité stockés, venant de Wikidata).

### Verification Plan
- `./mvnw test` : unitaires du handler (tour de rôle, quota atteint, case épuisée,
  personnes déjà en base comptées) ; intégration `PeopleStore` (colonnes V9).
- Ingestion locale `--limit=60` → 5 personnes par case dans le rapport, et
  `SELECT gender, continent, count(*) FROM people GROUP BY 1, 2` cohérent.

### Phase Summary
_(à écrire)_

## Phase 4 : Revues et PR
Status: Not started

- [ ] `code-reviewer` (sourcing externe, données personnelles) et
      `architecture-reviewer` ; corriger tous les 🔴 et 🟠.
- [ ] `./mvnw test`, `pnpm test`, lint, Playwright component, PIT sur `IngestPeople`.
- [ ] Final Recap, Deployment Plan, archivage dans `plans/done/`.

### Verification Plan
- Toutes les suites à 0 échec ; PIT : test strength ≥ celle de `feature/people-sourcing`.

### Phase Summary
_(à écrire)_

## Tests par catégorie
| Catégorie | S'applique | Pourquoi |
|---|---|---|
| Unitaire backend | **Oui** | Règle des 18 ans, `ContinentResolver`, options du runner, dry-run, tour de rôle et quotas. |
| Intégration backend | **Oui** | Contrainte V8, colonnes V9 via `PeopleStore`. |
| Fonctionnel backend | Non | Aucun endpoint nouveau ni modifié ; l'ingestion est une commande. |
| Unitaire frontend | **Oui, léger** | Rappel « mineur » sur la page de curation, texte de `/privacy`. |
| Interface (Playwright) | Non | Aucun nouveau comportement d'interface, seulement du texte. |
| QA e2e | Non | Pas de parcours utilisateur nouveau. |
| Architecture | Non, existants | Les règles ArchUnit actuelles couvrent les nouveaux fichiers ; pas de nouvelle frontière. |
| Mutation | **Oui** | `IngestPeople` porte les règles métier (âge, continent, quotas). |

## Edge cases
- Photo à 17 ans 364 jours vs 18 ans pile ; date de photo en intervalle à cheval sur
  le 18e anniversaire (déjà rejetée en `AMBIGUOUS_AGE`, à vérifier).
- Personne avec deux genres déclarés, ou un genre déprécié : rang `preferred` d'abord,
  comme pour les dates de naissance.
- Nationalité d'un pays disparu (URSS, Yougoslavie) : `P30` souvent présent sur ces
  entités, sinon personne hors quotas. À compter dans la mesure.
- Case épuisée avant son quota (ex. Océanie / femmes).
- Personne déjà en base sans genre ni continent (avant V9) : remplie au prochain
  passage, et comptée dans sa case seulement une fois remplie.

## Final Recap
_(à écrire quand toutes les phases sont finies)_

## Deployment Plan
_(à écrire quand toutes les phases sont finies : V8 et V9, aucune variable obligatoire
nouvelle)_

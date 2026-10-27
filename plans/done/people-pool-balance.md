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

## Décisions de l'utilisateur (2026-10-27)
- Femmes trans et hommes trans comptés comme femmes et hommes ; continent le moins
  représenté pour plusieurs nationalités (un Russe compte pour l'Asie) : validés.
- **Cases « autre » proportionnelles à Wikidata** (~0,11 %), et non au même plafond
  que les cases femmes / hommes : avec le même plafond elles auraient fait 10 à 12 %
  du pool (mesure : 20/20 dans 5 continents sur 6 pour une cible de 240).
- **Équilibre des âges** par tranches de 8 ans (18-25, 26-33… 82+) : à la collecte,
  préférer la photo de la tranche la moins remplie ; le reste au tirage quotidien
  (Semaine 3, ajouté à la roadmap).

- **Cible en photos, pas en personnes** : au moins 5 000 photos jouables, et une photo
  jamais rejouée avant 3 ans (règle du tirage, Semaine 3). La cible compte les photos
  en jeu (en attente + approuvées), quotas par case en photos ; on relance la collecte
  après chaque passe de curation pour compléter.

## Hypothèses
- **6 continents** : Afrique, Amérique du Nord, Amérique du Sud, Asie, Europe, Océanie.
  « Eurasie » compte comme Asie, « Insular Oceania » comme Océanie.
- **Plusieurs nationalités sur plusieurs continents** : la personne compte pour le
  continent le moins représenté, dans l'ordre fixe Océanie, Afrique, Amérique du Sud,
  Asie, Amérique du Nord, Europe. C'est déterministe et favorise l'équilibre.
- **Sans nationalité** : hors quotas, donc pas ingérée par la liste équilibrée
  (toujours possible avec `--qid`).
- **Femmes trans et hommes trans** comptés comme femmes et hommes (décidé en
  Phase 2, 2026-10-27 : la valeur Wikidata dit leur genre).
- **Genres autres qu'homme / femme** (non binaire, intersexe, valeurs contradictoires) :
  ni exclus ni comptés dans le quota hommes / femmes. Une petite case « autre » par continent, prise en entier (~1 550
  personnes au total, avant le filtre photo).
- **Parts égales** : 6 continents × 2 genres = 12 cases d'une part égale de la cible
  (3 000 → 250 par case). Une case épuisée reste incomplète et le rapport le dit ; on
  ne reporte pas sa part ailleurs sans décision (à revoir après la mesure, Phase 2).
- ~~L'équilibre porte sur les **personnes ingérées**~~ (remplacé en Phase 3 : il porte
  sur les **photos en jeu**, en attente ou approuvées), pas sur les photos approuvées. Les
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
Status: Complete

- [x] `IngestPeopleRunner` : options `--offset=<rang>` (début de la liste) et
      `--dry-run` (toutes les règles, téléchargement et ré-encodage compris, mais rien
      n'est écrit). Le dry-run est porté par `IngestPeopleCommand` ; `PeopleStore`
      n'est pas appelé.
- [x] `IngestReport` : en plus des compteurs actuels, répartition par genre et par
      continent des personnes vues et gardées, et nombre moyen de photos par personne
      gardée.
- [x] Récupération du genre (`P21`) et du continent (`P27` → `P30`) : nouvelle
      requête de détail dans `WikidataClient`, restreinte aux QID de la page comme les
      quatre autres ; règles de continent dans une classe pure `ContinentResolver`.
- [x] Lancer 4 échantillons de 200 personnes : rangs 0, 10 000, 100 000, 500 000.
      Noter les rapports dans ce plan.
- [x] Analyse : rendement par profondeur, par genre, par continent ; temps par
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
**Livré** : `--offset`, `--sample` (ajouté : `--limit` compte les personnes gardées,
inutilisable pour un échantillon de taille fixe), `--dry-run` ; genre et continent par
une 6e requête QLever (~0,5 s / 500 personnes) ; rapport par case, photos par personne,
Ko par image, durée. Correctif trouvé en route : labels `mul` (14 « sans nom » dans le
top 200).

**Mesures du 2026-10-27** (dry-run, 200 personnes du classement global par rang) :

| Rangs | Gardées | Photos / gardée | Ko / image | Durée | Rejets personne |
|---|---|---|---|---|---|
| 0–199 | 163 (82 %)* | 1,52 | 162 | 613 s | 22 sans photo, 14 sans nom*, 1 naissance |
| 10 000–10 199 | 147 (74 %) | 1,19 | 173 | 434 s | 51 sans photo |
| 100 000–100 199 | 118 (59 %) | 1,01 | 175 | 341 s | 78 sans photo, 4 naissance |
| 500 000–500 199 | 90 (45 %) | 1,01 | 166 | 296 s | 78 sans photo, 32 naissance |

\* avant le correctif `mul` : ~88 % une fois les 14 « sans nom » récupérés.

- Le rendement est bien plus haut que les ~30 % du spike (qui comptait sans le filtre
  1930 : les personnes nées après 1930 ont des photos numériques avec EXIF).
- Rejets `MINOR` : 54 photos dans le top 200, 47, 16, puis 1.
- Par genre et continent, le rendement des femmes et des petits continents est du
  même ordre que celui des hommes et de l'Europe dans chaque échantillon (effectifs
  faibles par case).

**Taille des cases** (QLever, personnes avec `P18` nées en 1930 ou après, avant
résolution du continent) : la plus petite case femmes / hommes est Océanie / femmes
(~5 000), puis Afrique / femmes (~6 250), Amérique du Sud / femmes (~10 700).

**Conclusion** :
- 250 personnes par case sont atteignables partout, même au rendement le plus bas
  (45 %) : aucune case femmes / hommes ne s'épuisera. Les cases « autre » restent
  petites (quelques centaines de personnes avant le filtre photo), comme prévu.
- Coût estimé pour 3 000 personnes : ~4 300 personnes examinées à ~2 s, soit
  **~2,5 h**, et ~3 000 × 1,2 photo × 170 Ko ≈ **0,6 Go**.
- Après curation (~43 % de photos rejetées, et la plupart des personnes n'ont qu'une
  photo), il resterait de l'ordre de **1 700 à 1 900 personnes jouables**, soit ~150
  par case.

## Phase 3 : Ingestion équilibrée
Status: Complete

- [x] Migration `V9__add_person_gender_and_continent.sql` : colonnes `gender`
      (`MALE` / `FEMALE` / `OTHER`) et `continent` sur `people`, nullable (personnes
      déjà ingérées : remplies à leur prochain passage).
- [x] `Person` : champs `gender`, `continent` (enums `Gender`, `Continent` dans
      `features/people`).
- [x] `WikidataClient.fetchPeople` : classement par case, avec filtres `P21` et
      continent dans la requête de classement QLever (mesurer le temps, limite de
      30 s).
- [x] `IngestPeopleHandler` : traite les 12 cases (+ cases « autre ») à tour de rôle
      jusqu'à leur quota, en comptant les personnes déjà en base de la case, pour que
      la relance reste idempotente.
- [x] Rapport : quota, gardées et état « épuisée » par case.
- [x] Docs : `architecture.md` (sourcing : équilibre, raison du continent plutôt que de
      l'ethnie, mineurs), `roadmap.md`, `/privacy` section 8 (genre et continent de
      nationalité stockés, venant de Wikidata).

- [x] Cases « autre » proportionnelles (`Cell.quota`, `OTHER_GENDER_SHARE = 0.0011`).
- [x] Tranches d'âge : `AgeBand`, compteurs du rapport (photos valides et gardées par
      tranche), `PhotoSelector` qui préfère la tranche la moins remplie
      (`PeopleStore.photosByAgeBand` + run en cours).
- [x] Mesure de l'effet : collecte équilibrée en dry-run `--limit=240`, avant / après.
      Part des photos gardées, avant → après : 74-81 de 6,7 % à 10,2 %, 82+ de 2,2 % à
      4,2 % ; les tranches 18-25 à 66-73 entre 10 et 15 % chacune. 82+ reste rare
      (64 photos valides seulement sur 275 personnes examinées) : le tirage
      compensera.
- [x] Cible en photos (décision du 2026-10-27) : `target-photos=5000`, quotas des
      cases en photos en jeu (`PersonRepository.countPhotosByCell`), arrêt du parcours
      simple au nombre de photos ; règle des 3 ans notée en Semaine 3 de la roadmap.
- [x] Essai réel `--limit=60` (photos) écrit en base locale.

### Verification Plan
- `./mvnw test` : unitaires du handler (tour de rôle, quota atteint, case épuisée,
  personnes déjà en base comptées) ; intégration `PeopleStore` (colonnes V9).
- Ingestion locale `--limit=60` → 5 personnes par case dans le rapport, et
  `SELECT gender, continent, count(*) FROM people GROUP BY 1, 2` cohérent.

### Phase Summary
**Livré** : V9 (`people.gender`, `people.continent`), `Cell` et ses quotas, requête de
classement par case (`WikidataClient.fetchCell`, 0,6 à 3,4 s), tour de rôle entre
cases dans `IngestPeopleHandler`, photos déjà en base comptées par case
(`PersonRepository.countPhotosByCell`), cible en photos (`target-photos=5000`), cases
« autre » proportionnelles, préférence de la tranche d'âge la moins remplie
(`PhotoSelector`, `AgeBand`), rapport par case et par tranche. Docs :
`architecture.md`, `roadmap.md`, `/privacy` section 8.

**Vérification** : backend **628/628** ; essai réel `--limit=60` sur la base locale en
377 s : 49 personnes, 70 photos, 18 cases à leur quota (5 ; quelques-unes à 6 quand la
dernière personne apporte deux photos ; « autre » à 1), âges de 18 à 89 ans, aucun nom
anglais manquant. Relance immédiate : 0 personne vue, 0 s.

**Limite constatée** : une personne ingérée avant V9 n'a ni genre ni continent tant
que le classement de sa case ne la recroise pas ; si c'est le cas, sa photo existante
n'est pas comptée pendant ce run (la case peut dépasser son quota de ses photos). Sans
effet en prod, où l'ingestion n'a jamais tourné.

## Phase 4 : Revues et PR
Status: Complete

- [x] `code-reviewer` (sourcing externe, données personnelles) et
      `architecture-reviewer` ; corriger tous les 🔴 et 🟠.
- [x] `./mvnw test`, `pnpm test`, lint, Playwright component, PIT sur `IngestPeople`.
- [x] Final Recap, Deployment Plan, archivage dans `plans/done/`.

### Verification Plan
- Toutes les suites à 0 échec ; PIT : test strength ≥ celle de `feature/people-sourcing`.

### Phase Summary
**Revues du 2026-10-27** :
- `code-reviewer` : aucun 🔴 ni 🟠. Injection SPARQL / SQL écartée (QID constants ou
  validés), logs limités aux QID, genre et continent hors art. 9 (aucune
  transidentité stockée), `/privacy` exacte dans les deux langues. 🟡 appliqués :
  `PersonRecord.toString` réduit au QID ; `entityId` valide le QID. 🟡 non appliqués :
  citer l'art. 14 dans `/privacy` (contenu déjà conforme), lois hors UE (pour mémoire).
- `architecture-reviewer` : aucun 🔴 ; 🟠 `entityId` (même correctif) et fin de ligne
  manquante dans `qlever-profiles.json`, corrigés ; 🟡 hypothèse du plan périmée,
  corrigée. 🟡 non appliqués : taille de `IngestPeopleHandler` (extraire
  `BalancedIngestion` au prochain ajout), record `Run` qui porte une map modifiable,
  `IngestReport.toString` dense.

**Vérification finale** : backend **633/633** ; front **168/168**, lint, `tsc`,
Prettier OK ; Playwright component **42/42** ; PIT : test strength **94 %** au global
et **95 %** sur `IngestPeople` (465 / 487 ; 94 % sur `feature/people-sourcing`), aucun
survivant dans `AgeBand`, `Cell`, `ContinentResolver`, `GenderResolver`,
`PhotoSelector`, `PhotoEvaluator`. Les survivants utiles (moyennes du rapport, résultat
de `fetchCell`, nom français) ont reçu un test après le run.

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
**Livré** (PR `feature/people-pool-balance` → `develop`) : un pool de photos jouable,
équilibré et sans mineur, dont la taille se règle en photos.
- **Aucune photo de mineur** : rejet `MINOR` sous 18 ans, contrainte V8 en base,
  rappel sur la page de curation, mention dans `/privacy`.
- **Mesure** : `--sample`, `--offset`, `--dry-run`, rapport par case et par tranche
  d'âge. Rendement de 88 % en haut de la liste à 45 % au rang 500 000, bien au-dessus
  des ~30 % attendus.
- **Équilibre** : 18 cases (6 continents de nationalité × femmes, hommes, autre),
  chacune avec son classement QLever, à tour de rôle. Quotas en photos en jeu ; cases
  « autre » à leur poids réel (~0,11 %). Genre et continent stockés (V9).
- **Âges** : préférence de la tranche de 8 ans la moins remplie au choix des photos
  d'une personne ; 74-81 passe de 6,7 % à 10,2 % des photos, 82+ de 2,2 % à 4,2 %.
- **Cible** : 5 000 photos en jeu par défaut ; relance après curation pour compléter.
- **Correctif** : labels `mul` de Wikidata (14 « sans nom » dans le top 200).

**Écarts par rapport au plan initial, tous décidés avec l'utilisateur ou imposés par le
réel** :
- cible et quotas en **photos** au lieu de personnes (5 000 photos, pas de répétition
  avant 3 ans au tirage) ;
- cases « autre » proportionnelles au lieu de prises en entier ;
- équilibre des âges, absent du plan initial ;
- femmes et hommes trans comptés comme femmes et hommes ;
- `--sample` ajouté (`--limit` ne convenait pas à un échantillon) ;
- aucune case femmes / hommes ne s'épuise : la question du report de quota ne se pose
  pas.

**Hors scope, à faire ensuite** :
- Ingestion en production, curation, relances jusqu'à 5 000 photos approuvées.
- Tirage quotidien (Semaine 3) : tranches d'âge équilibrées, pas de photo rejouée
  avant 3 ans.
- Suppression différée des images des photos rejetées (~0,6 Go), si l'utilisateur la
  veut.
- Extraire l'ingestion équilibrée de `IngestPeopleHandler` au prochain ajout.

## Deployment Plan
1. **Merge de la PR dans `develop`**, puis release `develop` → `main` en **merge
   commit**. Cette release embarque aussi la curation (#18) : poser le rôle admin en
   SQL après le déploiement (voir `plans/done/photo-curation.md`).
2. **Au démarrage de l'API en prod**, Flyway applique V8 (aucune photo en prod, donc
   rien à supprimer) et V9 (deux colonnes vides). Aucune variable obligatoire
   nouvelle ; `INGEST_TARGET_PHOTOS` (5 000 par défaut) remplace
   `INGEST_TARGET_PEOPLE`, qui n'était défini nulle part.
3. **Avant l'ingestion** : vérifier l'espace disque du VPS. Il faut ~0,9 Go pour une
   première passe, ~1,5 Go au total après les relances (les photos rejetées gardent
   leur image), à ajouter au volume des backups.
4. **Ingestion** (depuis le VPS, dans `/opt/ageguessr/` où la CI copie le compose et
   écrit le `.env`, avec l'image de l'API et les profils `prod,ingest`) :
   `cd /opt/ageguessr && docker compose -f docker-compose.prod.yml run --rm backend --spring.profiles.active=prod,ingest`
   (cible par défaut de 5 000 photos). Lancer dans `tmux` ou `screen` pour survivre à
   une coupure SSH. Première passe ~4 h. En cas d'arrêt, relancer : elle reprend sans
   doublon.
5. **Après chaque passe** : lire le bilan dans les logs (`balance (photos/quota)`,
   photos par tranche d'âge), puis curation sur `/admin/curation`. Relancer la même
   commande pour remplacer les photos rejetées, jusqu'à ~5 000 photos approuvées
   (~8 800 photos collectées en tout, ~7 h de collecte).

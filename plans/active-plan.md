# Indices événementiels par catégorie et par année

Constituer, pour chaque année de référence (voir ci-dessous) et chaque catégorie
(Musique, Cinéma, Sport, Science et techno, Monde, Célébrités), au moins 5 indices
jouables.
**On commence par un prototype sur la musique**, de l'extraction à la validation en
local. Une fois validé, les cinq autres catégories suivent dans la même vague de
développement et la même PR. L'affichage en jeu, le tirage et le signalement sont hors
scope (Semaines 3 et 4).

## For Future Agents
Au fil du travail : cocher les cases `- [x]`. Quand une phase est finie, passer son
statut à `Complete` et écrire son **Phase Summary**. Lancer le **Verification Plan** de
la phase et noter le résultat avant de passer à la suivante. Ce fichier est la source de
vérité, pas la conversation.

## Contexte pour repartir à froid
- Branche : `feature/hint-ingestion`, créée depuis `develop`. Ne jamais commiter sur
  `develop` ou `main`. `docs/roadmap.md` porte des modifications non commitées (écran de
  résultat, signalement, ingestion cochée), à reprendre dans cette PR.
- Règles ArchUnit : les features ne dépendent pas les unes des autres ; les entités sont
  à la racine de leur feature ; `RestClient` est interdit hors de
  `features.people.IngestPeople`. `WikimediaHttp` et `ReviewStatus` ne sont donc pas
  réutilisables depuis une nouvelle feature `hints`.
- Le dépôt est sous licence MIT et public. Les textes issus de Wikipédia sont sous CC
  BY-SA : ils ne peuvent pas passer sous MIT.
- Aucune photo ne doit être servie avant l'affichage du crédit (Semaine 4) : le pilote ne
  montre rien aux joueurs.

## Décisions validées avec l'utilisateur (2026-10-30)
- **Un indice doit permettre de calculer l'âge sur la photo** (précision de
  l'utilisateur). L'année de naissance réelle ne le permet pas, car l'année de la photo
  n'est pas montrée au joueur. **Année de référence d'un indice = année du jour −
  `age_at_photo`** : une personne de 30 ans sur la photo, en 2026, reçoit un indice de
  1996, et le joueur calcule 2026 − 1996. Confirmé par l'utilisateur, pour toutes les
  catégories. Remplace « a clue from the year they were born » du README.
- **Catégories** : Musique, Cinéma, Sport, Science et techno, Monde, Célébrités. Elles
  n'avaient jamais été fixées (seulement des exemples). La télévision est écartée.
- **Célébrités** : une personne connue dont l'âge aujourd'hui est celui de la photo,
  c'est-à-dire née l'année de référence. Jamais la personne à deviner. C'est un indice
  comme les autres, stocké dans `hints` et issu d'une **nouvelle extraction Wikidata**,
  sans lien avec la table `people`. Mort ou vivant : seule compte l'année de naissance.
- **Minimum : 5 indices validés** par catégorie, par année et par public. Calcul sur le
  pool réel : 3 285 manches en 3 ans sur 4 895 photos, soit 67 % du pool. Les années les
  plus représentées (années 1980, ~111 photos par an) reviennent ~74 fois : à 5 indices,
  un indice y revient au plus ~15 fois en 3 ans, soit tous les 2 à 3 mois, s'il est
  choisi à chaque fois. Viser 10 candidats là où les sources le permettent.
- **Année sous le minimum** : la catégorie est masquée pour cette année. Si trop de
  catégories sont masquées, il faudra chercher d'autres sources pour combler.
- **Une source précise par catégorie** plutôt que les seules pages « YYYY in … ».
- **Rédaction** : Claude Code, hors prod (sélection, texte court). Aucune clé API ni
  appel au LLM en production.
- **Relecture limitée aux traductions** : l'utilisateur ne relit pas les indices un par
  un, il valide les sources. Un indice tiré d'une page dans sa propre langue entre
  directement en jeu ; seul un indice traduit passe en attente de validation. Une
  traduction se fait donc en dernier recours, pour combler une année sous le minimum.
  Confirmé : tout relire serait trop long, l'utilisateur fait confiance au reste.
- **Signalement des indices par les joueurs**, sur le modèle des photos (Semaine 4) :
  c'est le filet de sécurité des indices que personne ne relit. Depuis l'écran de
  résultat, où l'indice est réaffiché avec son année et sa source. Liste fermée, sans
  texte libre ni compte : mauvaise année, information fausse, incompréhensible, cet
  indice me dérange, autre. Rien de personnel en base, aucun retrait automatique.
- **Une seule vague de développement** : la musique sert de prototype. Une fois validée
  en local, toutes les catégories sont faites d'un coup, dans une seule PR, et partent
  ensemble en prod. La page de validation reste donc dans le scope, même sans traduction
  en musique.
- **Monde sans filtre** : guerres, attentats et dictatures sont autorisés. C'est une
  exception assumée à « rien qui puisse raviver un traumatisme », qui reste la règle pour
  les personnes du pool. Aucun humain ne relit ces indices avant le jeu. Un signalement
  « me dérange » permet de les rejeter au cas par cas, sans changer cette règle.

## Mesures du 2026-10-30 (spike hors dépôt, sur 1930-2008)
Candidats bruts par an. Scripts jetables : ils sont à réécrire en Phase 1.

| Catégorie | Médiane EN | Médiane FR | Années < 5 EN | Années < 5 FR |
|---|---:|---:|---:|---:|
| Monde (pages de l'année) | 139 | 121 | 0 | 0 |
| Sport | 70 | 44 | 0 | 0 |
| Science | 28 | 17 | 0 | 2 |
| Musique (événements) | 47 | 8 | 0 | 16 |
| Cinéma (événements) | 6 | 3 | 24 | 55 |

Sources structurées pour la musique :

| Source | Années | Limite |
|---|---:|---|
| `Liste des titres musicaux numéro un en France en Y` | 54 (1955-2008) | Peut contenir des albums dans une seconde table |
| `List of Billboard number-one singles of Y` + `… Hot 100 number ones of Y` | 69 (1940-2008) | Formats de tableaux variables : parseur du spike non fiable |
| `List of Billboard 200 number-one albums of Y` | 64 (1945-2008) | Pas encore parsé |
| `Billboard Year-End Hot 100 singles of Y` | 63 (1946-2008) | Pas encore parsé |
| `Catégorie:Chanson de Y` (fr) | 79, au moins 10 par an | Classement par langues favorable aux titres américains |
| `Category:Y songs` (en) | 79, au moins 10 par an | Contient des hymnes nationaux, à écarter |
| Albums Wikidata (≥ 15 sitelinks) | 54 | Biais metal ; rien avant 1950 |

Pièges constatés :
- Une date `P577` Wikidata peut être une sortie à l'étranger : toujours prendre la plus
  ancienne (`MIN`).
- Les pages `YYYY in music` n'ont plus de section Events depuis 2010 : sans effet ici,
  aucune personne du pool n'est née après ~2008.
- Pas de liste d'albums numéro un en France par année.
- Le 33 tours n'existant pas avant 1948, les albums ne comblent pas 1930-1948.
- Les pages anglaises contiennent de la vie privée (paternité d'enfants) : à écarter.

## Pool réel (prod, 2026-10-30)
4 895 photos approuvées, personnes nées de 1930 à 2007.

| Décennie | 1930 | 1940 | 1950 | 1960 | 1970 | 1980 | 1990 | 2000 (→ 2007) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Photos | 307 | 548 | 624 | 751 | 776 | 1 110 | 687 | 92 |

Photos par âge sur la photo (les années de référence sont celles de 2026) :

| Âge | 18-25 | 26-33 | 34-41 | 42-49 | 50-57 | 58-65 | 66-73 | 74-81 | 82-94 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Photos | 771 | 817 | 709 | 608 | 594 | 565 | 423 | 316 | 92 |
| Années | 2001-2008 | 1993-2000 | 1985-1992 | 1977-1984 | 1969-1976 | 1961-1968 | 1953-1960 | 1945-1952 | 1932-1944 |

Les années d'avant 1949, les plus fragiles pour la musique, ne concernent que les âges
de 78 ans et plus : ~220 photos sur 4 895, soit ~4,5 % (répartition estimée dans la
tranche 74-81).

## Sources retenues

| Catégorie | Public FR | Public EN | Comble les manques |
|---|---|---|---|
| Musique | Numéros un France, succès de l'année | Billboard singles et albums | Catégories de chansons par année |
| Cinéma | Box-office France | Wikidata (films les plus connus, date `MIN`) | Wikidata |
| Sport | Palmarès communs | Palmarès communs | Pages « in sports » pour 1940-1946 |
| Science et techno | `Y en science` | `Y in science` | Traduction en dernier recours (2 années FR < 5) |
| Monde | Page `Y` FR | Page `Y` EN | |
| Célébrités | Wikidata (naissance `P569`, classement par sitelinks), article FR requis | Wikidata, article EN requis | |

## Décisions d'architecture
- **Extraction en Python, hors de l'application** (`scripts/hints/`), lancée à la main.
  Les événements passés ne changent pas : c'est un travail ponctuel, dont la sortie est
  relue. Du code Java ajouterait des parseurs de wikitext à maintenir dans
  l'application, sans gain. La sortie est un JSON versionné, importé par Java.
- **Nouvelle feature `features/hints/`**, indépendante de `people` : un indice dépend
  d'une année, pas d'une personne. Son enum de statut est propre à la feature. Aucun
  appel réseau côté Java, donc aucune exception à la règle `RestClient`.
- **`audience`** : `ALL` (les deux textes obligatoires), `FR` ou `EN`. Le minimum de 5
  se compte par public : indices `ALL` + indices de ce public.
- **Import idempotent** par clé stable (`dedupe_key`) : une relance ajoute les nouveaux
  indices et ne touche jamais une décision de curation ni un texte édité à la main.
- **Statut à l'import** : `APPROVED` pour un indice issu d'une page dans sa langue,
  `PENDING` pour un indice traduit (`translated` dans le fichier). Ne remplace pas les
  contrôles automatiques : année absente du texte, vie privée écartée à la sélection,
  audit d'un échantillon par Claude Code. Un indice déjà `APPROVED` peut passer
  `REJECTED` à la suite d'un signalement.
- **Licence** : `resources/hints/README.md` indique que ces fichiers sont sous CC BY-SA,
  hors MIT. Chaque indice garde `source_url`, pour l'attribution en jeu.
- **Un indice ne contient jamais d'année** : tout nombre de 1900 à 2099 dans le texte
  est refusé à l'import, sinon il donnerait ou fausserait la réponse. *In the Year 2525*
  reste donc accepté.
- **Célébrités** : `subject_qid` garde le QID de la célébrité citée, pour ne jamais la
  proposer sur sa propre photo et pour retrouver ses indices en cas de demande de
  retrait. L'extraction applique les critères de la ligne éditoriale. Le texte vient du
  nom et de la description courte Wikidata dans la langue du public : ce n'est pas une
  traduction, donc pas de relecture.
- **Relance annuelle de Célébrités, à la main, vers mars** (décision de l'utilisateur,
  2026-10-30) : un job automatique a été envisagé puis écarté. Les indices sont rangés par
  année de naissance, donc rien n'est à mettre à jour quand les âges avancent : il ne
  manque chaque année que la nouvelle génération de 18 ans, soit ~2 % des photos
  (estimation) pour une catégorie sur six. Un workflow qui écrit en prod et qu'il faut
  surveiller ne se justifie pas. La relance suit le chemin normal : extraction, fichier
  commité dans une PR, import. Un oubli ne casse rien, la catégorie reste masquée.

## Hypothèses
- « Année » veut dire année de première sortie (*La Vie en rose* : 1945 ou 1947 selon la
  source). Le parti pris : la date de la source principale, sans relecture ; le
  signalement « mauvaise année » rattrape les erreurs.
- Le tirage quotidien lira uniquement les indices `APPROVED` (Semaine 3).
- L'année de référence glisse d'un an chaque 1er janvier. **Les indices couvrent 1932 à
  2015 (demande de l'utilisateur)** : à 18 ans minimum, aucune mise à jour avant 2034.
  Les pages `YYYY in music` n'ont plus de section Events depuis 2010, sans effet : le
  pilote s'appuie sur les classements.
- Célébrités : seules des personnes ayant déjà 18 ans le jour de l'extraction sont
  citées (date de naissance complète). La première extraction couvre donc 1932-2008 sans
  les personnes nées fin 2008 encore mineures, et la relance annuelle ajoute les
  suivantes. De janvier à la relance, la catégorie reste masquée sur les photos de
  18 ans. Mars suppose au moins 5 personnes majeures de la nouvelle année à cette date : à
  confirmer à la première relance.
- Un indice peut citer une personne du pool. Ce n'est gênant que s'il s'agit de la
  personne à deviner : à traiter par le tirage (Semaine 3).

## Fichiers
Créés :
- `scripts/hints/extract_music.py`, `scripts/hints/README.md`
- `ageguessr-api/src/main/resources/hints/music.json`, `.../hints/README.md`
- `ageguessr-api/src/main/resources/db/migration/V11__create_hints.sql`
- `ageguessr-api/src/main/java/app/ageguessr/features/hints/` : `Hint.java`,
  `HintCategory.java`, `HintAudience.java`, `HintReviewStatus.java`,
  `HintRepository.java`
- `.../features/hints/ImportHints/` : `ImportHintsRunner.java`,
  `ImportHintsHandler.java`, `HintFileEntry.java`
- `.../features/hints/ListPendingHints/`, `.../ReviewHint/`, `.../GetHintCoverage/` :
  commande ou query, handler, response, controller
- `frontend/src/app/features/hint-curation/` : page, service d'API, modèles, specs
- `ageguessr-api/src/main/resources/application-import-hints.properties`

Modifiés :
- `frontend/src/app/app.routes.ts` (route `/admin/hints`, `adminGuard`)
- `docs/decisions/architecture.md` (section « Sourcing des indices », qui disait
  Wikipédia seule), `docs/roadmap.md`, `README.md` (catégories)
- `frontend/src/app/features/legal/privacy/privacy.html` si `code-reviewer` juge que les
  noms cités dans les indices relèvent de la section 8

## Phase 1 : Extraction du pilote musique
Status: Complete

- [x] Obtenir la borne réelle des naissances : 1930-2007 (voir « Pool réel »)
- [x] `extract_music.py` : numéros un France, classés par semaines en tête, albums de
      la seconde table séparés
- [x] Parser Billboard fiable sur 1940-2015 (formats anciens et Hot 100), Billboard 200.
      `Billboard Year-End` non utilisé : inutile pour atteindre la cible
- [x] Repli `Catégorie:Chanson de Y` et `Category:Y songs`, en écartant hymnes, chants
      politiques et militaires, et redirections ; chansons en français d'abord côté FR
- [x] Rapport de couverture par année et par public, contrôlé contre le minimum de 5
- [x] Sélection et mise en forme (10 par année et par public), sans aucune traduction,
      `music.json`, notice de licence

### Verification Plan
- `python3 scripts/hints/extract_music.py --report` : au moins 5 candidats pour chaque
  année et chaque public entre 1932 et 2015, ou liste explicite des années manquantes.
- Relecture d'un échantillon de 3 années par décennie : année exacte, artiste et titre
  corrects, aucune année dans le texte.

### Phase Summary
**Résultat** : 1 680 indices dans `music.json`, 840 par public, soit 10 pour chacune des
84 années, 0 traduction. Contrôles passés : aucune année dans un texte, aucune clé en
double, toutes les sources renseignées.

| Public | Classements | Albums | Catégories |
|---|---:|---:|---:|
| FR | 537 | 0 | 303 |
| EN | 722 | 19 | 99 |

Côté FR, les catégories couvrent tout jusqu'en 1949, la majorité des années 1950, et
presque plus rien après. Il n'y a plus aucune entrée de catégorie à partir de 2000.

**L'audit a trouvé ce que le rapport ne voyait pas** : chant de la Jeunesse hitlérienne
(une redirection), chants soviétiques et de parti, double face Billboard mal découpée,
bandes originales créditées « Soundtrack », classements français 2009-2015 ignorés
(tableaux à deux lignes d'en-tête). Tout est corrigé. Limites de l'audit : échantillon par
décennie, année et faits non recoupés avec une autre source.

**Fiabilité** : `Liste des titres musicaux numéro un en France en 1955` porte un bandeau
Wikipédia sur la fiabilité de ses sources ; celle de 1990 cite le SNEP (les autres années
n'ont pas été vérifiées). Les années creuses reposent sur la date des catégories, moins
sûre.

**Écarts** : le repli « succès de l'année » du spike est retiré, les catégories suffisent.

## Phase 2 : Schéma et import
Status: Complete

- [x] V11 : table `hints` (`category`, `year`, `audience`, `text_en`, `text_fr`,
      `source_url`, `source_kind`, `subject_qid`, `dedupe_key`, `review_status`,
      `reviewed_at`, `reviewed_by`), unicité sur `dedupe_key`, contraintes de cohérence
      `audience`/textes. `source_kind` permet de retrouver les indices d'une source
      moins sûre (dates des catégories Wikipédia) ; `subject_qid` n'est rempli que pour
      Célébrités
- [x] Entités et enums de `features/hints/`
- [x] `ImportHintsRunner` sous profil `import-hints`, sans serveur HTTP, sortie explicite
      comme `IngestPeopleRunner`
- [x] Import idempotent, avec rejet motivé (année dans le texte, texte manquant, année
      hors bornes)

### Verification Plan
- `cd ageguessr-api && ./mvnw test` : tout passe.
- Import lancé deux fois en local : le second ajoute 0 ligne.

### Phase Summary
**Résultat** : V11 et la feature `hints` sont en place. 744 tests backend passent, dont les
contraintes de V11 sur un vrai Postgres, l'import des fichiers livrés et la conservation
d'une décision à la relance.

Essai réel en local : premier import 1 680 ajoutés, 0 refusé (840 FR et 840 EN, tous
`APPROVED`, 1932-2015) ; second import 0 ajouté, 1 680 déjà présents.

**Écarts** : `subject_qid` est ajouté à V11, obligatoire pour `CELEBRITIES` et interdit
ailleurs. L'année d'un indice doit être comprise entre 1900 et l'année en cours. Les tests
de contraintes sont dans `integration/hints/` : ils utilisent `UserRepository`, et l'ArchUnit
interdit à `features/hints`, même en test, de dépendre de `auth`.

**À savoir pour la prod** : le profil `import-hints` charge tout le contexte, donc il a besoin
de `JWT_SECRET` et `RESEND_API_KEY`, comme l'ingestion. Aucun mail n'est envoyé.

## Phase 3 : Validation des traductions et couverture
Status: Complete

- [x] `GET /api/admin/hints/pending`, `PUT /api/admin/hints/{id}/review` (décision + texte
      corrigé, un indice `APPROVED` pouvant passer `REJECTED`),
      `GET /api/admin/hints/coverage`
- [x] Page `/admin/hints` réduite aux indices traduits, au clavier (mêmes raccourcis que
      `/admin/curation`), texte modifiable
- [x] Tableau de couverture : années × catégories × publics, en évidence sous 5 indices
      validés

### Verification Plan
- `cd ageguessr-api && ./mvnw test` et `cd frontend && pnpm test` : tout passe.
- Essai réel en local : `music.json` n'ayant aucune traduction, importer en plus un
  fichier de test d'indices marqués `translated`, puis valider, rejeter, annuler et voir
  la couverture changer. Ce fichier n'est pas versionné dans `resources/hints/`.

### Phase Summary
**Résultat** : endpoints admin et page `/admin/hints` livrés (validation au clavier, texte
corrigeable, couverture sous la file, lien dans l'en-tête). Backend, frontend et Playwright
passent, et un test fonctionnel suit la couverture après valider, rejeter et annuler.

**Non fait** : l'essai manuel en local, API lancée, a été refusé par le contrôle des
permissions (jeton admin forgé, arrêt de processus). La séquence est couverte par le test
fonctionnel, et la page par Playwright avec réseau mocké : page et vraie API n'ont donc pas été
essayées ensemble. À faire à la main avec un vrai compte admin.

**Écarts** : `HintText` à la racine de la feature porte les règles communes à l'import et à la
correction. La couverture va de 1932 à l'année en cours moins 18 (2008 aujourd'hui).

## Phase 4 : Validation du prototype
Status: Complete

- [x] Mesurer en local : 0 année sous le minimum en musique, 0 traduction, audit de la phase 1
- [x] **Décision avec l'utilisateur** (2026-10-30) : continuer avec les autres catégories

### Verification Plan
- `GET /api/admin/hints/coverage` en local : décompte des années sous le minimum,
  comparé aux photos par décennie.

### Phase Summary
_(à écrire quand la phase est terminée)_

## Phase 5 : Les cinq autres catégories
Status: Not started

Ne démarre qu'après la décision de la Phase 4. Même branche et même PR.

- [ ] Cinéma : box-office France, Wikidata avec date `MIN`
- [ ] Sport : palmarès, repli 1940-1946
- [ ] Science et techno : `Y en science` / `Y in science`
- [ ] Monde : pages de l'année dans chaque langue, sans filtre de contenu
- [ ] Célébrités : extraction Wikidata par année de naissance (1932-2008), classée par
      sitelinks, avec description courte et ligne éditoriale, personnes majeures
      uniquement, relance annuelle documentée dans `scripts/hints/README.md`
- [ ] Solutions de comblement si le masquage reste trop fréquent
- [ ] Traductions en dernier recours, validées sur `/admin/hints`

### Verification Plan
- Rapport de couverture de chaque extraction : au moins 5 candidats par année et par
  public, ou années manquantes listées.

### Phase Summary
_(à écrire quand la phase est terminée)_

## Phase 6 : Revue, mise en production et clôture
Status: Not started

- [ ] Reviewers `architecture-reviewer` et `code-reviewer`, corrections
- [ ] Compléter « Sourcing des indices » dans `docs/decisions/architecture.md`, mettre à
      jour `docs/roadmap.md`
- [ ] PR vers `develop`, release, import de toutes les catégories en prod
- [ ] Validation des traductions en prod
- [ ] Final Recap, Deployment Plan, archivage dans `plans/done/hint-ingestion.md`

### Verification Plan
- `GET /api/admin/hints/coverage` en prod : décompte des années sous le minimum.
- `git diff --stat` : docs à jour, plan archivé, plus de `plans/active-plan.md`.

### Phase Summary
_(à écrire quand la phase est terminée)_

## Tests : catégories retenues

| Catégorie | S'applique ? | Pourquoi |
|---|---|---|
| Unitaire backend | Oui | Validation à l'import, année dans le texte, calcul de couverture, décisions de curation |
| Unitaire frontend | Oui | Page de curation, raccourcis, tableau de couverture, service d'API |
| Intégration backend | Oui | Contraintes de V11, import idempotent, décision conservée à la relance |
| Fonctionnel backend | Oui | Endpoints admin : 401 sans jeton, 403 sans rôle, 200 admin, 404, validation |
| Interface (UI) | Oui | Page admin clavier, responsive et thèmes clair/sombre, avec réseau mocké |
| QA e2e | Non | Outil réservé à l'admin, aucun parcours joueur tant que le jeu n'existe pas |
| Architecture | Déjà couvert | Les règles existantes s'appliquent à `hints` ; aucune règle nouvelle |
| Mutation | Oui, informatif | Lancé sur PR vers `main`, utile sur les règles d'import |

Les scripts Python n'ont pas de tests automatiques : ils sont ponctuels, leur rapport
contrôle la couverture, un échantillon est audité, seuls les indices traduits sont relus
et le signalement des joueurs rattrape le reste.

## Edge cases
- Fichier d'import invalide ou partiellement invalide : les lignes refusées sont
  listées, les autres importées, sans `dedupe_key` dupliqué.
- Relance après curation : ni le statut ni le texte corrigé ne changent.
- Source modifiée entre deux extractions : nouvelle `dedupe_key`, nouvel indice,
  `APPROVED` ou `PENDING` selon qu'il est traduit ou non. L'ancien indice reste en base.
- Titre resté numéro un sur deux années : compté pour l'année de sa première entrée.
- Même titre classé plusieurs fois dans l'année : semaines additionnées.
- Reprise d'une chanson ancienne : date de la version classée.
- Texte contenant une année dans un titre (*1999*, Prince) : refusé à l'import, à
  reformuler ou à rejeter. Un nombre hors de 1900-2099 (*In the Year 2525*) passe.
- Année de guerre sans classement : le repli s'applique, sinon l'année est listée
  sous le minimum.

## Final Recap
_(à écrire quand tout est terminé)_

## Deployment Plan
_(à écrire quand tout est terminé)_

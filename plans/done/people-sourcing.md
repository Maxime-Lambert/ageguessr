# Sourcing des personnes : Wikidata, photos datées, proxy d'image

Construire le pool de personnes du jeu. On sélectionne des personnalités connues sur
Wikidata, avec leur date de naissance au jour près. Pour chacune, on garde les photos
Commons dont la date de prise de vue donne un **âge sur la photo sans ambiguïté**. On
stocke une copie ré-encodée de chaque image et on la sert par un identifiant opaque via
`GET /api/images/{id}`. Les indices événementiels (pages « YYYY in … ») ne font **pas**
partie de ce plan : ils auront leur propre plan.

## For Future Agents
Au fil du travail : cocher les cases `- [x]`. Quand une phase est finie, passer son
statut à `Complete` et écrire son **Phase Summary**. Lancer le **Verification Plan** de
la phase et noter le résultat avant de passer à la suivante. Ce fichier est la source de
vérité, pas la conversation.

## Contexte pour repartir à froid
- Branche : `feature/people-sourcing` (depuis `develop`).
- Seule feature existante : `features/auth`. Packages de use case en PascalCase
  (`features/auth/ChangePassword/`), entités à la racine de la feature (règle ArchUnit
  `entities_live_in_the_feature_root_package`), features indépendantes entre elles (règle
  `features_do_not_depend_on_each_other`).
- Exemple d'appel HTTP sortant : `shared/email/ResendEmailSender.java`. Il n'existe pas
  encore de `RestClient` dans le projet ; `spring-boot-starter-webmvc` le fournit.
- `config/SecurityConfig.java` : tout est authentifié sauf `PUBLIC_AUTH_PATHS`. Un
  commentaire y rappelle qu'il faut ajouter les endpoints du jeu à une allow-list, sinon
  un invité prend un 401.
- Dernière migration : `V3__add_terms_acceptance.sql`. Celle de ce plan sera `V4`.
- Charte photo : `docs/decisions/design.md`, « Traitement des photos ». Le filtre de
  vieillissement est fait en CSS côté front, donc **pas** dans l'image stockée. Le duotone
  reste à trancher en Semaine 4.
- Wikidata et Commons sont joignables depuis l'environnement de dev (vérifié le
  2026-10-26).

## Décisions validées avec l'utilisateur (2026-10-26)
- **Le joueur devine l'âge de la personne sur la photo**, pas son âge actuel. Personnes
  vivantes et décédées acceptées. Ça **change** la décision de
  `docs/decisions/architecture.md` (« photo la plus récente possible ») : la contrainte
  devient « photo **datée de façon fiable** ». Le repli sur la date d'upload disparaît,
  parce qu'elle ne dit rien de la date de prise de vue.
- **Âge sans ambiguïté uniquement.** Une photo n'est gardée que si l'âge calculé est le
  même aux deux bornes de l'intervalle de dates possible. Exemple : naissance le
  1950-03-10, photo datée « 1976-07 » → la photo est prise entre le 1976-07-01 et le
  1976-07-31, donc l'âge vaut 26 aux deux bornes : on garde. Avec une photo datée
  « 1976 », l'âge vaut 25 au 1976-01-01 et 26 au 1976-12-31 : on rejette.
- **Plusieurs photos par personne** (relation 1→N). Une personne peut revenir avec une
  autre photo, donc un autre âge.
- **Copie locale ré-encodée** : la vignette Commons est téléchargée une fois, à
  l'ingestion, puis ré-encodée en JPEG. Ça supprime les métadonnées EXIF/XMP/IPTC (nom
  de la personne, légende, date). Les octets sont stockés en Postgres. Aucun appel à
  Commons pendant une partie.
- **Ingestion par commande manuelle** : un runner Spring activé par le profil `ingest`.
  Il démarre sans serveur web, ingère puis s'arrête. Pas d'endpoint admin et pas de job
  planifié.

## Décisions validées après le spike (2026-10-26)
- **QLever** (`https://qlever.dev/api/wikidata`) pour la sélection des personnes, parce
  que le SPARQL public de Wikidata dépasse son délai. Ce service n'est appelé qu'à
  l'ingestion.
- **Date corroborée obligatoire (EXIF strict)** : une photo n'est gardée que si l'EXIF
  `DateTimeOriginal` de l'appareil tombe dans l'intervalle de la date déclarée (SDC
  `P571` ou `{{Information}}`), et si aucune année du nom de fichier ne la contredit.
  Les photos sans EXIF sont rejetées pour l'instant ; on pourra élargir plus tard avec
  un autre signal.
- **Âge exact conservé** : les dates à l'année seule n'ajoutent que 5 à 9 % de
  personnes (voir le Phase Summary de la Phase 1).

## Décisions validées avec l'utilisateur (2026-10-26, après la Phase 3)
- **Au plus une photo par année civile et une par âge pour une même personne.** Les
  deux règles sont nécessaires : une même année peut couvrir deux âges (avant et après
  l'anniversaire), et un même âge deux années. Elles sont appliquées à deux endroits :
  - dans `PhotoSelector` à l'ingestion ;
  - en base, par les index uniques `(person_id, age_at_photo)` et
    `(person_id, EXTRACT(YEAR FROM taken_from))`, plus un `CHECK` qui garantit qu'une
    photo tient dans une seule année.
- **Écart minimal entre deux photos d'une même personne** (décision du 2026-10-26,
  après la précédente) : `app.people.ingest.min-years-between-photos`, **10 ans** par
  défaut. Deux photos doivent être distantes d'au moins N ans **en âge et en année
  civile**. À N = 1, on retrouve exactement la règle « une photo par âge et par
  année ».
  - En base, seul ce plancher d'un an est imposé (index uniques) : l'écart est un
    réglage produit, pas un invariant de données.
  - Mesuré sur les 58 personnes gardées du spike (cap 5) :

    | Écart minimal | Photos/personne | Personnes avec ≥ 2 photos |
    |---|---|---|
    | 1 an | 2,31 | 57 % |
    | 5 ans | 1,55 | 40 % |
    | 10 ans | 1,24 | 22 % |

  - L'écart ne change pas le nombre de personnes gardées, seulement le nombre de photos
    par personne.
  - **À réévaluer après la première vraie ingestion** (Phase 5 et production) : si le
    volume de photos est trop faible, l'utilisateur envisage de descendre à 5 ans. Il
    suffit de changer la propriété, sans toucher au code.
- **Nombre de photos par personne plafonné et configurable** :
  `app.people.ingest.max-photos-per-person`, 5 par défaut. Les photos déjà stockées
  comptent dans le plafond et bloquent leur année et leur âge lors d'une
  réingestion.
- **Photos regroupées par personne** (`person_photos.person_id`). **Contrainte
  transmise à la Semaine 3** (sélection quotidienne) :
  - jamais deux photos d'une même personne dans la même partie ;
  - un délai minimal entre deux apparitions d'une même personne, quelle que soit la
    photo.

  Ces règles se raisonnent par personne, pas par photo. Elles sont reportées dans
  `docs/roadmap.md`.

## Décisions validées avec l'utilisateur (2026-10-26, après le premier vrai run)
- **Exigence produit** : une photo ne montre qu'**une seule personne**, cadrée au
  minimum du visage jusqu'au corps entier.
- **Vérification par curation manuelle** (choix de l'utilisateur, contre la vision
  Claude et la détection de visages locale). Sur le run de 30 personnes, j'ai revu
  visuellement les 46 photos :
  - 21 conviennent ;
  - 5 sont limites (personne seule mais petite dans le cadre) ;
  - **20 sont inutilisables (~43 %)** : groupes, foules, scène vue de loin, voire
    **personne du tout** (Pelé : un stade ; Ariana Grande 2026 : un jeu de lumières).

  Cause : les fichiers `depicts` ne listent que le sujet principal voulu par le
  contributeur, pas toutes les personnes dans le cadre. Les `P18` sont presque toujours
  de bons portraits.
- **Découpage** :
  - **Cette PR** : `person_photos.review_status` (`PENDING` / `APPROVED` /
    `REJECTED`, défaut `PENDING`). Seules les photos `APPROVED` sont servies.
  - Une photo `REJECTED` n'est jamais reproposée (`knownFiles`). Elle ne compte plus
    dans le plafond ni dans l'écart minimal : les index uniques âge/année sont
    **partiels** (`WHERE review_status <> 'REJECTED'`).
  - **PR suivante** `feature/photo-curation` (plan dédié) : rôle admin, endpoints
    approuver/rejeter, page Angular qui enchaîne les photos en attente avec des
    raccourcis clavier.
- V4 est modifiée plutôt que d'ajouter une V5 : elle n'a été appliquée que sur la base
  locale de dev, réinitialisée à la main (tables `people*` et ligne V4 de
  `flyway_schema_history`, comptes locaux conservés).

## Hypothèses (non tranchées, à confirmer par le spike de la Phase 1)
- **Notoriété : pas de seuil, un objectif de taille.** L'ingestion parcourt les
  humains ayant un `P18`, par sitelinks décroissants, jusqu'à avoir retenu
  `app.people.ingest.target-people` personnes (défaut **3 000**, environ 2,7 ans de
  parties à 3 personnes/jour sans répétition). Les plus connus passent d'abord, et
  augmenter la cible fait descendre naturellement vers les moins connus.
- **Candidats photo** : `P18` de l'item, plus les fichiers Commons dont les Structured
  Data indiquent `depicts (P180) = la personne`, récupérés par la recherche Commons
  `haswbstatement:P180=Qxxx filetype:bitmap`.
- **Choix des photos d'une personne** (`PhotoSelector`) : on trie par date la plus
  précise, puis `P18` d'abord, puis nom de fichier (pour que deux exécutions donnent le
  même résultat). On garde ensuite chaque photo à au moins N ans (âge et année) de
  toutes celles déjà gardées, jusqu'au plafond. Avec un écart de 10 ans, on stockera
  environ 1,24 photo par personne. Volume estimé : 3 000 × ~1,25 × ~100 Ko ≈
  **0,4 Go** dans Postgres. L'espace disque du VPS et la durée
  des backups sont à vérifier avant l'ingestion en production (Deployment Plan).
- **Sources de date de prise de vue (tranché par le spike)** :
  1. **date déclarée** : SDC `P571` (structurée, avec précision) et/ou le champ `date` de
     `{{Information}}` (`extmetadata.DateTimeOriginal`), parsé strictement (`YYYY-MM-DD`
     avec un éventuel horaire, `YYYY-MM`, `YYYY`, `D Month YYYY`, `Month D, YYYY`,
     préfixes « Taken on/in »). On rejette `circa`, les siècles, les décennies,
     `between`/`before`/`after`, « unknown », « upload » et les intervalles. Si les deux
     sources existent, elles doivent se recouper ; on retient alors l'intersection.
     Sinon, la photo est rejetée ;
  2. **corroboration** : l'EXIF `DateTimeOriginal` (jamais `DateTime`) doit tomber dans
     l'intervalle retenu, et aucune année (1800-2029) du nom de fichier ne doit le
     contredire.
- **Photo de la bonne personne, seule** : on exclut les fichiers qui ont plusieurs `P180`
  humains (photos de groupe). On exclut aussi les non-photographies (peintures,
  dessins, statues, timbres) si on peut les détecter : SDC `P31`, catégories, MIME
  `image/jpeg`. La méthode est à mesurer dans le spike.
- **Identifiant public stable** : chaque image a un `public_id` UUID aléatoire, distinct
  de la clé primaire. Limite connue : une fois une partie terminée, le couple
  id → réponse peut circuler. La Semaine 3 (moteur de jeu) devra régénérer le
  `public_id` quand une photo est resélectionnée. On le note, mais ce n'est pas fait ici.

## Décisions d'architecture de cette feature
- **Package** : `features/people/`.
  - Entités à la racine : `Person`, `PersonPhoto`, `PhotoImage`.
  - Use cases : `IngestPeople/` (clients Wikidata/Commons, règles de date, ré-encodage,
    runner) et `GetImage/` (endpoint).
- **Trois tables** (`people`, `person_photos`, `person_photo_images`). Les octets de
  l'image sont dans une table séparée parce que Hibernate ne sait pas charger un `bytea`
  en lazy sans bytecode enhancement. Sans cette séparation, lister les photos d'une
  personne chargerait toutes les images en mémoire.
- **Règle de date en objet pur, sans Spring** : `PhotoDate` (intervalle min/max dérivé
  de la précision) et `AgeAtPhoto.of(birthDate, photoDate)` → `Optional<Integer>`, vide
  si l'âge est ambigu. C'est le cœur métier : il doit être testé exhaustivement en
  unitaire et résister au mutation testing.
- **Règles sur la date de naissance** : `P569` avec une précision au jour (11),
  calendrier grégorien, sans valeurs contradictoires. Un `preferred rank` l'emporte, un
  `deprecated` est ignoré, deux valeurs `normal` différentes excluent la personne. Le
  calendrier julien (`Q1985786`) est exclu plutôt que converti : c'est rare pour des
  personnes photographiées.
- **Bornes de cohérence** : la photo doit être prise entre la naissance et le décès
  (s'il y en a un), pas dans le futur, et l'âge doit être compris entre 0 et 120 ans.
- **Clients HTTP** : `RestClient` avec un timeout de connexion et un timeout de lecture.
  - User-Agent conforme à la politique Wikimedia :
    `Ageguessr/1.0 (https://ageguessr.app; contact@ageguessr.app)`.
  - Paramètre `maxlag=5` sur l'API Commons.
  - Requêtes séquentielles avec une pause configurable. Sur un 429/503, on attend
    (`Retry-After` ou backoff) avec un nombre de tentatives borné.
- **Téléchargement d'image sécurisé** :
  - on n'accepte que des URLs `https://upload.wikimedia.org/` (allow-list d'hôte contre
    le SSRF, même si l'URL vient de l'API Commons) ;
  - on demande la vignette en 800 px de large (`iiurlwidth`) et on plafonne la réponse
    en octets ;
  - on lit les dimensions avant de décoder (garde anti « decompression bomb »), puis on
    ré-encode avec `ImageIO` en JPEG qualité ~0,85.
- **Ce qui ne doit jamais fuiter avant la fin d'un round** : le nom du fichier Commons,
  le SHA-1 Commons (on peut chercher un fichier par son SHA-1 via l'API), l'URL source
  et le QID. `GET /api/images/{id}` ne renvoie que les octets, avec `Content-Type:
  image/jpeg`, sans `Content-Disposition` ni ETag dérivé de Commons.
  `Cache-Control: public, max-age=86400`.
- **Licence et attribution** : les images Commons sont libres, mais CC BY / BY-SA
  imposent de créditer l'auteur et d'indiquer la licence. Un filtre CSS appliqué à
  l'image en fait une adaptation, toujours sous BY-SA. On stocke donc l'auteur en texte
  brut (`extmetadata.Artist` est du HTML : on le nettoie), le nom court et l'URL de la
  licence, et l'URL de la page Commons. **L'affichage du crédit se fera à la révélation
  du round (Semaine 4)**, jamais avant, sinon il donnerait la réponse. Licences
  acceptées : allow-list (`CC0`, `PD*`, `CC BY*`, `CC BY-SA*`), tout le reste est
  rejeté.
- **RGPD (personnes vivantes)** : nom, date de naissance et photo de personnalités
  publiques sont des données personnelles collectées indirectement (art. 14 RGPD).
  - On ajoute un booléen `people.excluded` pour les demandes de retrait. Une personne
    exclue n'est plus jamais réingérée ni servie.
  - On ajoute une courte section à `/privacy` (EN + FR) : source des données, base
    légale (intérêt légitime), contact pour demander un retrait.
  - Le droit à l'image des personnes vivantes (bandeau `{{Personality rights}}` sur
    Commons) est **à évaluer par l'agent `code-reviewer`**. Si nécessaire, la question
    remonte à l'utilisateur avant la PR.
- **Idempotence** : relancer l'ingestion met à jour les données en se basant sur
  `wikidata_id` et `(person_id, commons_file)`. Elle ne supprime jamais une photo déjà
  ingérée (elle pourra avoir servi dans une partie passée, et une licence libre est
  irrévocable).
- **Pas de mise en cache Redis** de l'endpoint image : les octets viennent de Postgres
  par clé primaire, et le cache HTTP fait le reste. On verra en Semaine 3 si la charge
  le justifie.

## Tests : catégories retenues
| Catégorie | S'applique ? | Pourquoi |
|---|---|---|
| Unitaire backend | **Oui** | `PhotoDate`, `AgeAtPhoto`, parsing des dates, règles `P569`, filtres licence et multi-personnes, parsing des réponses Wikidata/Commons sur des fixtures JSON enregistrées pendant le spike (`MockRestServiceServer`), ré-encodage (EXIF supprimé, garde de taille), logique d'idempotence du handler d'ingestion (clients mockés). |
| Intégration backend | **Oui** | Migration V4, contraintes d'unicité, cascade de suppression, upsert, chargement de l'image sans charger la photo (et inversement). |
| Fonctionnel backend | **Oui** | `GET /api/images/{id}` via le vrai pipeline Spring Security : accessible en invité, 404 sur un id inconnu, id malformé, en-têtes (pas de `Content-Disposition`, `Cache-Control`), méthodes autres que GET refusées, exclusion appliquée. |
| Architecture | **Oui** | Nouvelle règle : seul le package `IngestPeople` peut dépendre des clients Wikidata/Commons. Ça garantit qu'aucun appel réseau externe ne se glisse dans le chemin d'une requête de jeu. |
| Mutation | **Oui (auto)** | Tourne sur la PR vers `main`. Les règles de date sont exactement le genre de code où un `<` muté en `<=` passe inaperçu. |
| Unitaire frontend | **Oui, minime** | Seulement pour la section ajoutée à `/privacy`, si le composant a déjà des specs de contenu. |
| Interface (UI) | Non | Aucun nouvel écran. L'affichage de la photo arrive en Semaine 4. |
| QA e2e | Non | Aucun nouveau parcours utilisateur. |
| Appels réseau réels | **Hors CI** | Wikimedia en CI serait lent, flaky et impoli. La validation réelle se fait par le spike (Phase 1) et une ingestion locale sur un petit lot (Phase 5), avec les résultats notés ici. |

## Edge cases à couvrir
- Naissance connue seulement à l'année ou au mois → personne exclue.
- Plusieurs `P569` contradictoires ; `preferred` et `deprecated` ranks ; calendrier
  julien.
- Photo antérieure à la naissance, postérieure au décès ou dans le futur → rejetée.
- Photo le jour de l'anniversaire, le 29 février (naissance un 29/02, photo une année
  non bissextile).
- Date de photo au mois, dans le mois de naissance → ambiguë, rejetée. Dans un autre
  mois → acceptée.
- Date au format `1976-07-14 15:30:00`, `circa 1976`, `1970s`, `1976–1977`, HTML dans le
  champ → seul le premier format est accepté.
- Sources de date contradictoires (`P571` ≠ `{{Information}}`) → photo rejetée.
- Photo de groupe (plusieurs `P180` humains), peinture, statue, fichier SVG ou TIFF.
- Licence hors allow-list ; auteur HTML contenant un lien ou du script → texte brut
  uniquement.
- URL de vignette hors `upload.wikimedia.org` ; réponse trop lourde ; image aux
  dimensions démesurées ; image corrompue.
- Fichier déjà ingéré (rejouer l'ingestion ne crée pas de doublon) ; personne passée
  `excluded` entre deux ingestions.
- 429 / 503 / `maxlag` de Wikimedia → attente puis reprise, abandon propre de la
  personne après N échecs sans faire échouer tout le lot.
- `GET /api/images/{id}` : UUID inconnu, chaîne non-UUID, image d'une personne exclue.

---

## Phase 1 : Spike, valider les heuristiques sur des données réelles
Status: Complete

Code jetable dans le scratchpad, **pas** dans le repo. Seuls les constats (dans ce plan)
et les fixtures JSON (dans `src/test/resources`) sont conservés.

- [x] Écrire une requête SPARQL qui liste les humains au-dessus d'un seuil de sitelinks
      avec `P569` au jour près, sans timeout (pagination par tranche si nécessaire).
      Noter la requête retenue et le nombre de personnes par seuil (30 / 50 / 80).
- [x] Sur un échantillon de 50 personnes variées (époques, pays, domaines), récupérer
      les candidats photo (`P18` + `haswbstatement:P180`) et, pour chaque fichier :
      `P571`, `extmetadata.DateTimeOriginal`, EXIF `DateTimeOriginal`, licence, auteur,
      nombre de `P180`, MIME.
- [x] Mesurer : la part de fichiers datés par chaque source, les désaccords entre
      sources, et le rendement final (personnes avec ≥ 1 photo à âge non ambigu).
- [x] Vérifier à la main (visuellement) 15 photos retenues : bonne personne, seule,
      photographie, et âge apparent cohérent avec l'âge calculé.
- [x] Décider de l'ordre des sources de date, du sort de l'EXIF, du seuil de sitelinks
      et du plafond de photos par personne. Mettre à jour la section « Hypothèses ».
- [x] Enregistrer les fixtures JSON représentatives dans
      `ageguessr-api/src/test/resources/people/` (réponses SPARQL, `wbgetentities`,
      `imageinfo`/`extmetadata`, `wbgetentities` MediaInfo, recherche Commons).
- [x] **Point d'arrêt** : si le rendement extrapolé est inférieur à 1 000 personnes, en
      parler à l'utilisateur avant la Phase 2 (alternatives : tolérer ±1 an, baisser
      le seuil de notoriété).

### Verification Plan
- Les constats chiffrés sont écrits dans ce fichier (section Phase Summary).
- `ls ageguessr-api/src/test/resources/people/` → fixtures présentes.
- `git status` → aucun script du spike dans le repo.

### Phase Summary
**Premier passage (2026-10-26)**. Mesures faites avec des scripts jetables dans le
scratchpad, sur 400 humains tirés au hasard (`ORDER BY MD5(QID)`) : 100 par tranche de
sitelinks 5-9 / 10-19 / 20-49 / ≥ 50. On n'a regardé que la photo `P18` ; les fichiers
SDC `depicts` restent à mesurer.

- **Taille du pool** (QLever, 2026-10-26) : 1 421 857 humains ont un `P18`. Parmi eux,
  502 919 ont au moins 5 sitelinks, 230 779 au moins 10, 79 228 au moins 20 et 11 016
  au moins 50. La notoriété n'est pas le facteur limitant.
- **Le SPARQL public de Wikidata (WDQS) dépasse son délai** sur ces requêtes
  d'agrégat. **QLever** (`https://qlever.dev/api/wikidata`, Université de Fribourg)
  répond en quelques secondes. Ce choix est à valider, puisque QLever est un service
  tiers utilisé uniquement à l'ingestion.
- **L'API Wikidata renvoie souvent `maxlag`** (retard de réplication > 5 s, pendant
  plus d'une minute ce jour-là). Le client doit attendre et réessayer longtemps, ou
  passer les lectures d'entités par QLever.
- **Rendement par personne (photo `P18` seule)** :

  | Sitelinks | Naissance au jour | Photo datée (≥ année) | dont au jour | Âge exact |
  |---|---|---|---|---|
  | 5-9 | 91 % | 60 % | 54 % | 54 % |
  | 10-19 | 93 % | 63 % | 54 % | 56 % |
  | 20-49 | 91 % | 66 % | 56 % | 58 % |
  | ≥ 50 | 91 % | 62 % | 51 % | 53 % |

  Les photos datées seulement à l'année ne rapportent que 5 à 9 % de personnes en plus.
  Exiger un âge exact ne coûte donc presque rien.
- **Motifs de rejet** (sur 400) : pas de date (58), photo hors de la vie de la
  personne, typiquement une tombe ou une statue (28), naissance pas au jour (34),
  plusieurs `P180` (21), avant 1839, c'est-à-dire une peinture (5), sources
  contradictoires (3).
- **Piège découvert à la vérification visuelle** (1 photo sur 4 regardées) : une vieille
  photo numérisée porte la date d'upload ou de scan. Exemple : *Marie-Thérèse
  Kerschbaumer (1988) by Guenter Prust.jpg* est datée « 2012-11-26 », ce qui donnerait
  76 ans au lieu de ~52. Mesures de corroboration sur les 221 photos à âge exact :
  - 118 ont un EXIF `DateTimeOriginal` dans l'intervalle déclaré (date confirmée par
    l'appareil) ;
  - 85 n'ont pas d'EXIF ;
  - 8 ont un EXIF contradictoire ;
  - 10 ont une année dans le nom de fichier qui contredit la date.
- **Projection prudente** (EXIF obligatoire et concordant) : ~30 % des personnes ayant
  un `P18`, soit **~69 000 personnes à ≥ 10 sitelinks** et ~3 300 à ≥ 50. Avant même
  d'ajouter les fichiers SDC `depicts`, c'est très au-dessus de l'objectif de 1 000.
- **Risque résiduel** : la photo d'un tirage ancien prise avec un appareil numérique a
  un EXIF qui confirme… la date de reproduction. Il faudra un contrôle visuel sur
  échantillon à l'ingestion réelle (Phase 5).

**Second passage (2026-10-26) : apport des fichiers `depicts`, en EXIF strict.** Mesure
sur 120 personnes du même échantillon (naissance au jour). Pour chaque personne :
recherche `haswbstatement:P180=<QID> filetype:bitmap` (50 résultats max), plus le
`P18`.
- Personnes avec au moins 1 photo valide : **58/120 (48 %)**, contre 39/120 (33 %) avec
  le `P18` seul. Le gain est surtout net pour les plus connus (≥ 20 sitelinks : 37/62
  contre 20/62).
- 10,4 fichiers candidats par personne en moyenne. Parmi les personnes gardées :
  **8,4 photos valides et 2,5 âges distincts** en moyenne.
- Motifs de rejet par fichier (1 252 fichiers) : plusieurs `P180` (352), sans EXIF
  (189), non daté (76), hors de la vie (61), âge ambigu (48), sources contradictoires
  (13), EXIF contradictoire (12), année du nom de fichier contradictoire (11).
- **Piège EXIF** : `metadata` contient aussi `DateTime`, la date de modification par un
  logiciel, à ne **jamais** confondre avec `DateTimeOriginal` (fixture Q123344).
  `metadata` peut aussi valoir `[{"name": "_error", …}]`.
- **Format réel de `maxlag`** : HTTP 200, corps `{"error": {"code": "maxlag"}}`, en-tête
  `Retry-After: 5`. Le retry doit lire le corps de la réponse, pas seulement le code
  HTTP. Pendant le spike, l'API Wikidata a été en `maxlag` pendant plus d'une minute.
  On n'a pas besoin de `wbgetentities` sur Wikidata : la requête QLever fournit tout.
- **La requête QLever renvoie une ligne par combinaison** `P18` × `P569` × labels.
  Le client regroupe par personne, et la pagination se fait sur les personnes (une
  sous-requête `SELECT DISTINCT ?person … ORDER BY DESC(?sitelinks) LIMIT/OFFSET`),
  pas sur les lignes. Le `P18` arrive sous forme d'URL `Special:FilePath/<nom encodé>`.
- Fixtures enregistrées dans `ageguessr-api/src/test/resources/people/` (320 Ko,
  documentées dans le `README.md` du dossier).

**Conclusions** :
- Le pool potentiel se compte en dizaines de milliers de personnes. Ce qui limite,
  c'est le volume à stocker, pas la disponibilité des photos (voir Hypothèses
  révisées).
- Les photos sans EXIF (≈ 15 % des fichiers) restent écartées, comme décidé avec
  l'utilisateur.

## Phase 2 : Schéma de données et entités
Status: Complete

- [x] `ageguessr-api/src/main/resources/db/migration/V4__create_people_tables.sql` :
  - `people` (`id uuid pk`, `wikidata_id varchar unique not null`, `name_en`,
    `name_fr`, `birth_date date not null`, `death_date date null`,
    `sitelinks int not null`, `excluded boolean not null default false`,
    `created_at`, `updated_at`) ;
  - `person_photos` (`id uuid pk`, `person_id fk on delete cascade`,
    `commons_file varchar not null`, `taken_from date not null`,
    `taken_to date not null`, `age_at_photo smallint not null`,
    `date_source varchar not null`, `license_short_name`, `license_url`,
    `author_text`, `source_url`, `public_id uuid unique not null`, `created_at`,
    `unique (person_id, commons_file)`, `check (taken_from <= taken_to)`,
    `check (age_at_photo between 0 and 120)`) ;
  - `person_photo_images` (`photo_id uuid pk fk on delete cascade`,
    `content bytea not null`, `content_type varchar not null`, `width int`,
    `height int`).
- [x] `features/people/Person.java`, `PersonPhoto.java`, `PhotoImage.java` (entités).
- [x] `features/people/PersonRepository.java`, `PersonPhotoRepository.java`,
      `PhotoImageRepository.java`.
- [x] Tests d'intégration
      `ageguessr-api/src/test/java/app/ageguessr/integration/people/PeopleRepositoriesIntegrationTest.java` :
      unicités, cascade, `findByPublicId`, chargement séparé de l'image.

### Verification Plan
- `cd ageguessr-api && ./mvnw test -Dtest='PeopleRepositoriesIntegrationTest,ArchitectureTests'`
  → vert (ArchUnit valide l'emplacement des entités).
- `./mvnw test` complet → vert (`ddl-auto=validate` vérifie que les entités et la V4
  concordent).

### Phase Summary
_(provisoire)_ Écarts par rapport à la liste ci-dessus :
- `age_at_photo` en `INT` plutôt qu'en `SMALLINT`, parce qu'un `int` Java contre un
  `smallint` ferait échouer `ddl-auto=validate`.
- Contraintes ajoutées : `people_has_a_name` (au moins un label EN ou FR) et
  `people_death_after_birth`.
- Énumération `PhotoDateSource` (`STRUCTURED_DATA`, `INFORMATION_TEMPLATE`, `BOTH`) à la
  racine de la feature.
- Index uniques `(person_id, age_at_photo)` et `(person_id, EXTRACT(YEAR FROM
  taken_from))`, et `CHECK person_photos_interval_within_a_year` (décision de
  l'utilisateur, voir plus haut). Postgres accepte bien l'index sur
  `EXTRACT(YEAR FROM taken_from)`.
- La requête du futur endpoint est déjà dans le repository :
  `PhotoImageRepository.findServableByPublicId` (jointure qui ignore les personnes
  exclues).

Vérifié le 2026-10-26, une fois Docker relancé : `PeopleRepositoriesIntegrationTest`
(20 tests) est vert, et `./mvnw test` complet donne 306 tests verts. `ddl-auto=validate`
confirme donc que les entités et la V4 concordent.

## Phase 3 : Règles métier des dates (cœur pur)
Status: Complete

- [x] `features/people/IngestPeople/PhotoDate.java` : intervalle `[from, to]` construit
      depuis une date et sa précision (année / mois / jour).
- [x] `features/people/IngestPeople/AgeAtPhoto.java` : `Optional<Integer> of(LocalDate
      birth, PhotoDate photo, LocalDate deathOrNull, LocalDate today)`, avec les bornes
      de cohérence.
- [x] `features/people/IngestPeople/PhotoDateParser.java` : parsing strict du champ
      `{{Information}}` (HTML retiré, formats acceptés listés plus haut).
- [x] `features/people/IngestPeople/PhotoDateResolver.java` : combine les sources selon
      l'ordre décidé au spike ; rejette en cas de contradiction.
- [x] `features/people/IngestPeople/BirthDateSelector.java` : règles `P569` (précision,
      ranks, calendrier).
- [x] Tests unitaires de chacune de ces classes (tous les edge cases de date listés
      plus haut).

### Verification Plan
- `cd ageguessr-api && ./mvnw test -Dtest='PhotoDate*Test,AgeAtPhotoTest,BirthDateSelectorTest'`
  → vert.
- `cd ageguessr-api && ./mvnw pitest:mutationCoverage -DtargetClasses='app.ageguessr.features.people.IngestPeople.*Date*,app.ageguessr.features.people.IngestPeople.AgeAtPhoto'`
  → noter le score de mutation ici, viser ≥ 90 % sur ces classes.

### Phase Summary
Classes livrées (`features/people/IngestPeople/`), avec deux regroupements par rapport à
la liste :
- `PhotoDate` : intervalle, `fromWikibase`, `intersection`, `contains`.
- `PhotoDateParser` : `{{Information}}` strict, plus l'EXIF `DateTimeOriginal`.
- `BirthStatement` et `BirthDateSelector`.
- `AgeAtPhoto` et `PhotoDateResolver` sont fusionnés dans **`PhotoEvaluator`**, qui
  renvoie un `PhotoEvaluation` scellé (`Accepted(date, source, age)` ou
  `Rejected(PhotoRejection)`). Le motif de rejet servira au bilan d'ingestion par
  motif, ce qu'un `Optional` aurait perdu.

Ajouté ensuite, à la demande de l'utilisateur : **`PhotoSelector`**. Il impose un
écart minimal configurable (en âge et en année) entre deux photos d'une même personne,
applique le plafond par personne et tient compte des photos déjà stockées. 17 tests
unitaires.

Résultats :
- **124 tests unitaires verts.** Tous les cas du spike y sont, dont le piège
  Kerschbaumer et les dates réelles rejetées.
- **Mutation : 100/100 mutants tués (100 %)** sur `IngestPeople.*`.
- Constat : avec `Period.between`, une personne née le 29 février a 1 an le **1er mars**
  d'une année non bissextile. Ce comportement est figé par un test.

**Problème d'outillage découvert** (hors scope, à traiter dans une branche
`fix/mutation-testing`) : PIT n'a jamais fonctionné sur ce projet.
- `pitest-maven` 1.19.1 ne lit pas les classes Java 25 (`Unsupported class file major
  version 69`).
- `pitest-junit5-plugin` est absent, donc PIT ne voit pas les tests JUnit 5.
- `scripts/run-mutation-tests.sh` ne compile pas avant de muter.
- En CI, `continue-on-error: true` masquait l'échec : le step affiche « success » en
  ~30 s.

Correctif vérifié en local : `pitest-maven` 1.30.1 avec la dépendance de plugin
`org.pitest:pitest-junit5-plugin:1.2.3`, plus `test-compile` avant `mutationCoverage`.
Le `pom.xml` de cette branche est resté inchangé.

## Phase 4 : Clients Wikidata et Commons
Status: Complete

- [x] `config/WikimediaClientConfig.java` : bean `RestClient` (User-Agent, timeouts).
      Propriétés `app.wikimedia.*` dans `application.properties` (pause entre
      requêtes, nombre de tentatives, seuil de sitelinks, plafond de photos, largeur de
      vignette, taille max d'image).
- [x] `features/people/IngestPeople/WikidataClient.java` : requête SPARQL du spike, puis
      `wbgetentities` (labels en/fr, `P569`, `P570`, `P18`, sitelinks).
- [x] `features/people/IngestPeople/CommonsClient.java` : recherche
      `haswbstatement:P180`, `imageinfo` (URL de vignette, MIME, `extmetadata`,
      métadonnées EXIF) et MediaInfo (`P571`, `P180`, `P31`), avec `maxlag` et retry
      sur 429/503.
- [x] `features/people/IngestPeople/PhotoCandidateFilter.java` : licence en allow-list,
      une seule personne représentée, photographie, MIME.
- [x] `features/people/IngestPeople/AttributionSanitizer.java` : auteur HTML → texte
      brut.
- [x] Tests unitaires sur les fixtures du spike (`MockRestServiceServer`), retry/backoff
      compris, plus des tests des filtres et du nettoyage.

### Verification Plan
- `cd ageguessr-api && ./mvnw test -Dtest='WikidataClientTest,CommonsClientTest,PhotoCandidateFilterTest,AttributionSanitizerTest'`
  → vert.

### Phase Summary
Livré (`features/people/IngestPeople/` sauf mention contraire) :
- **`WikimediaHttp`** : couche HTTP commune aux deux clients.
  - Pause de politesse avant chaque requête.
  - Retry sur 429, 5xx, erreur réseau, JSON tronqué et `maxlag`, qui arrive en HTTP 200.
  - `Retry-After` respecté (plafonné à 60 s), sinon backoff exponentiel. Pas de retry
    sur les autres 4xx.
  - Les attentes passent par l'interface `Sleeper`, simulée dans les tests.
- **`WikidataClient`** : requête QLever paginée par personne (sous-requête
  `DISTINCT … ORDER BY DESC(?sitelinks) ?person LIMIT/OFFSET`), avec regroupement des
  lignes en `PersonRecord`.
  - Vérifiée en réel : ~28 s par page, d'où des pages de 500 personnes
    (`app.people.ingest.page-size`).
- **`DeathStatement` / `DeathDateSelector`** : mêmes règles de rang que la naissance.
  - Une date de décès au mois ou à l'année est acceptée, en gardant son premier jour
    (prudent).
  - Une date inconnue (`somevalue`) ou à la décennie exclut la personne.
  - Cas réel : Theodor Schwann, avec deux dates de décès dont une `preferred`.
- **`CommonsClient`** : recherche `depicts`, `imageinfo` par lots de 50, MediaInfo
  (`P571` grégoriens non dépréciés, compte des `P180` non dépréciés).
  - `statements: []` est traité comme « aucune donnée ».
  - Seul l'EXIF `DateTimeOriginal` est lu, jamais `DateTime`.
- **`PhotoEvaluator`** accepte désormais **plusieurs** `P571` par fichier : ils doivent
  se recouper, sinon la photo est rejetée (`CONFLICTING_DATES`).
- **`PhotoCandidateFilter`** :
  - JPEG uniquement ;
  - allow-list de licences par regex : `CC0`, `CC BY`, `CC BY-SA` (+ version et port),
    `Public domain`, `PD-*` ;
  - `NC`/`ND` refusés par token, pour que « PD-Finland » reste accepté ;
  - « Attribution », GFDL et « Copyrighted free use » refusés.
- **`AttributionSanitizer`** : jsoup (nouvelle dépendance `org.jsoup:jsoup:1.23.2`)
  réduit le HTML de l'auteur en texte, tronqué à 1 024 caractères.
- `config/WikimediaClientConfig` : `RestClient` dédié (User-Agent, timeouts, pas de
  redirections). Il est construit avec `RestClient.builder()`, parce que Spring Boot 4
  ne fournit le bean `RestClient.Builder` qu'avec `spring-boot-starter-restclient`.
- `WikimediaProperties` et `IngestProperties` sont des records `@ConfigurationProperties`
  **validés au démarrage**. Dans `src/test/resources/application.properties`, ils
  pointent vers des hôtes `.invalid` : aucun test ne peut joindre Wikimedia.

Vérifications :
- **`SpikeRegressionTest`** : sur les 26 fichiers enregistrés de Mallika Sherawat, la
  chaîne Java (parsing, filtre, évaluateur) retrouve exactement les 5 photos du spike.
  Le sélecteur à 10 ans en garde 2 : 2016 (39 ans) et 2006 (29 ans).
- `./mvnw test` complet : **391 tests verts**, plus 3 ajoutés ensuite.
- Mutation sur `IngestPeople.*` : **222/227 tués**. Les 3 vrais trous ont été comblés
  par des tests. Les 2 survivants restants sont équivalents (`>` contre `>=` au plafond
  de 60 s, même résultat).

## Phase 5 : Ré-encodage, handler et runner d'ingestion
Status: Complete

- [x] `features/people/IngestPeople/ImageDownloader.java` : allow-list
      `upload.wikimedia.org`, plafond d'octets.
- [x] `features/people/IngestPeople/ImageReencoder.java` : contrôle des dimensions avant
      décodage, puis ré-encodage JPEG sans métadonnées.
- [x] `features/people/IngestPeople/IngestPeopleHandler.java` : orchestration par
      personne, transaction par personne, idempotence, `excluded` respecté, échec d'une
      personne loggé sans arrêter le lot. Bilan final loggé (personnes vues, retenues,
      photos ajoutées, rejets par motif).
- [x] `features/people/IngestPeople/IngestPeopleRunner.java` : `ApplicationRunner`
      `@Profile("ingest")`. Options : `--limit=N`, `--qid=Q…` (une seule personne).
- [x] `ageguessr-api/src/main/resources/application-ingest.properties` :
      `spring.main.web-application-type=none`.
- [x] Tests unitaires : `ImageReencoderTest` (un JPEG avec EXIF en entrée → aucun EXIF
      en sortie, image trop grande rejetée, fichier corrompu rejeté),
      `ImageDownloaderTest` (hôte refusé, taille dépassée), `IngestPeopleHandlerTest`
      (clients mockés : rejeu sans doublon, personne exclue ignorée, échec isolé).
- [x] Ingestion réelle en local : `--limit=30`. Vérifier en base et visuellement 10
      images servies. Noter le bilan ici. *(fait sur les 46 images, voir le Phase
      Summary)*
- [x] Ajouté : statut de curation `review_status`, avec un rejet jamais reproposé qui
      libère son année et son âge.
- [x] Relancer `--limit=30` après le correctif des labels et noter le bilan final.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert.
- `docker compose up -d postgres redis && cd ageguessr-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest -Dspring-boot.run.arguments=--limit=30`
  → le process s'arrête seul, avec un bilan dans les logs.
- `docker compose exec postgres psql -U ageguessr -c "select count(*) from people; select count(*) from person_photos;"`
  → valeurs cohérentes avec le bilan.
- Relancer la même commande → aucun nouveau doublon.
- `exiftool` (ou `identify -verbose`) sur une image extraite de la base → aucune
  métadonnée EXIF/XMP/IPTC.

### Phase Summary
_(provisoire)_

**Livré** :
- `ImageDownloader` : allow-list `upload.wikimedia.org` **et `thumb.wikimedia.org`**,
  HTTPS seul, sans port ni userinfo. Plafond d'octets lu en flux, avec la retry de
  `WikimediaHttp`, généralisée aux octets.
- `ImageReencoder` : dimensions vérifiées avant décodage (20 M pixels max),
  conversion RVB, JPEG qualité 0,85 sans aucune métadonnée.
- `IngestPeopleHandler`, `PeopleStore` (une transaction par personne), `IngestReport`,
  `PersonSkip`.
- `IngestPeopleRunner` (`--limit`, `--qid` validé par regex).
- Profil `ingest` avec **`server.port=-1`** : `web-application-type=none` faisait
  échouer `SecurityConfig`, faute de bean `HttpSecurity`.
- Le runner **termine explicitement la JVM** (`SpringApplication.exit` +
  `System.exit`) : sans ça, les pools et le client HTTP la gardaient en vie après le
  bilan.
- Nouveau cas : une date de naissance modifiée sur Wikidata entre deux runs donne
  `BIRTH_DATE_CHANGED` (personne laissée intacte, warning pour vérification
  manuelle).

**Refonte du client QLever (2026-10-26 après-midi)** : la requête unique de la Phase 4
(classement + naissances + décès + images + labels en jointure) prenait ~28 s le matin,
juste sous la limite de 30 s de QLever. Elle a ensuite dépassé cette limite sans
retour en arrière : 429 « Operation timed out… Sort on ?person » pendant plus d'une
heure, alors que les requêtes simples répondaient en 0,1 s. Les succès du matin
venaient probablement du cache de QLever.

`WikidataClient` fait maintenant **5 petites requêtes** et assemble le résultat en
Java (`assemble`). Mesures sur une vraie page de 500 personnes :

| Requête | Durée |
|---|---|
| Classement | 3,6 s |
| Naissances (`VALUES` des QID de la page) | 1,2 s |
| Décès | 1,2 s |
| Images | 0,3 s |
| Labels | 0,8 s |
| **Total** | **≈ 7 s** |

Effets de bord positifs : plus de produit cartésien de lignes, et une page vide ne
déclenche aucune requête de détail. Fixtures `qlever-{page,births,deaths,images,labels}.json`
réenregistrées sur 7 personnes, dont Ronaldo.

**Second run réel `--limit=30` (client refondu, base remise à zéro)** :
- 141 personnes vues, 30 gardées, 42 photos (toutes `PENDING`), 299 s.
- **0 personne sans nom anglais** (correctif des labels confirmé).
- `NO_NAME` vaut 2 : des personnes sans aucun label `en*` ni `fr*`.
- Plus de personnes vues qu'au premier run : l'ancienne requête unique exigeait un
  `P569` dans sa jointure et écartait silencieusement les personnes sans date de
  naissance. Elles sont maintenant vues, puis comptées en `BIRTH_DATE_UNUSABLE` (43).
- Projection : ~8 h pour 3 000 personnes.
- 1 échec : **HTTP 414 (URI trop longue)** sur Q3874799, parce que 50 longs titres de
  fichiers ne tiennent pas dans une URL. **Corrigé** : `fetchFiles` passe en POST
  (formulaire), ce que l'API MediaWiki accepte pour les lectures. Test
  `sendsTitlesTooLongForAUrlInTheBody` ajouté.

**Risque relevé** : QLever renvoie un **429 quand une requête dépasse son délai**
(`"Operation timed out. Last operation: Sort…"`), pas seulement en cas de quota. Le
2026-10-26 après-midi, même la petite requête de fixture a échoué pendant plus de 10
minutes. Le client retente bien les 429. Mais si QLever reste surchargé plus longtemps
que nos tentatives, la page en cours échoue et le run s'arrête proprement : il suffit
de relancer (idempotent).

À prévoir pour la production :
- lancer l'ingestion aux heures creuses ;
- éventuellement mettre en cache la liste des personnes (QID + sitelinks), ce qui
  éviterait de retrier 1,4 M de lignes à chaque page.

**Bugs trouvés en conditions réelles et corrigés** :
1. Wikimedia sert les vignettes depuis `thumb.wikimedia.org`, et en 960 px (taille
   standard) au lieu de 800.
2. La JVM ne s'arrêtait pas.
3. Le profil `ingest` ne démarrait pas.
4. **Labels QLever** : `FILTER(LANG(?l) = "en")` rate le label anglais de Cristiano
   Ronaldo, alors que `STRSTARTS(LANG(?l), "en")` le trouve. On prend maintenant toutes
   les variantes `en*` / `fr*` et on préfère la langue exacte en Java.

**Run réel `--limit=30` (avant le correctif des labels)** :
- 78 personnes vues, 30 gardées (38 %), **44 photos**, **237 s**.
- Personnes écartées : `BIRTH_DATE_UNUSABLE` 16, `NO_NAME` 4 (en partie à cause du bug
  de labels), `NO_VALID_PHOTO` 28.
- Photos rejetées : `GROUP_PHOTO` 777, `NOT_SELECTED` 367, `NOT_DATED` 229,
  `AMBIGUOUS_AGE` 148, `LICENSE_NOT_ALLOWED` 145, `BEFORE_PHOTOGRAPHY` 34,
  `EXIF_CONFLICT` 28, `NO_EXIF_DATE` 28, `CONFLICTING_DATES` 26,
  `FILE_NAME_YEAR_CONFLICT` 21.
- **172 Ko par image en moyenne** (max 389 Ko) ; 43 dates `BOTH`, 2 `{{Information}}`
  seules, 1 SDC seule.
- Projection pour 3 000 personnes : ~7 900 personnes vues, **~6 h 30** d'ingestion,
  ~0,8 Go d'images.
- Relancer le même QID n'ajoute que ce qui manque (2006 déjà là, 2016 ajoutée), et le
  process s'arrête seul.
- Images extraites de la base : uniquement les segments APP0, DQT, SOF0, DHT et SOS.
  Ni EXIF, ni commentaire, ni nom, ni auteur, ni URL.
- Revue visuelle : ~43 % inutilisables, d'où la curation manuelle (voir les décisions).

## Phase 6 : Endpoint `GET /api/images/{id}`
Status: Complete

- [x] `features/people/GetImage/GetImageQuery.java`, `GetImageHandler.java`,
      `GetImageController.java`. Un id inconnu ou une personne exclue renvoie
      `NotFoundException`, avec le même 404 dans les deux cas pour ne rien laisser
      deviner.
- [x] `config/SecurityConfig.java` : `GET /api/images/*` en `permitAll`, et mettre à
      jour le commentaire sur l'allow-list.
- [x] Règle ArchUnit dans `architecture/ArchitectureTests.java` : seules les classes de
      `..people.IngestPeople..` peuvent dépendre de `WikidataClient`, `CommonsClient` et
      `ImageDownloader`.
- [x] Test fonctionnel
      `ageguessr-api/src/test/java/app/ageguessr/functional/people/GetImageFunctionalTest.java`.

### Verification Plan
- `cd ageguessr-api && ./mvnw test -Dtest='GetImageFunctionalTest,ArchitectureTests'`
  → vert.
- Avec l'API lancée sur les données de la Phase 5 :
  `curl -sI http://localhost:8080/api/images/<public_id>` → `200`,
  `Content-Type: image/jpeg`, pas de `Content-Disposition`, sans header
  `Authorization`.

### Phase Summary
- Slice `features/people/GetImage/` : `GetImageQuery`, `GetImageHandler`,
  `GetImageController`, `ImageResponse`.
- **Un seul 404 pour tous les refus** : id mal formé (l'id est reçu en `String`, pas en
  `UUID`, pour éviter un 400 qui distinguerait les cas), id inconnu, id interne,
  photo `PENDING` ou `REJECTED`, personne exclue.
- Réponse : `Content-Type: image/jpeg`, `Cache-Control: max-age=86400, public`. Pas de
  `Content-Disposition` ni d'ETag. `X-Content-Type-Options: nosniff` est déjà ajouté
  par Spring Security.
- `SecurityConfig` : `PUBLIC_GAME_GET_PATHS = {"/api/images/*"}` ouvert en GET
  seulement. Les sous-chemins, `/api/images` et les autres méthodes restent
  authentifiés.
- Règle ArchUnit **`only_the_people_ingestion_makes_outbound_http_calls`** : aucune
  classe hors de `..IngestPeople..` et `config..` ne dépend de `RestClient`. Elle est
  plus large que la règle prévue, qui visait les clients eux-mêmes : ceux-ci sont déjà
  package-private, donc inaccessibles depuis `GetImage` par construction.
- `GetImageFunctionalTest` : 5 tests verts.


## Phase 7 : Documentation, confidentialité, revues, PR
Status: Complete

- [x] `docs/decisions/architecture.md` : réécrire « Sourcing des personnes » (âge sur la
      photo, photo datée de façon fiable, plus de repli sur la date d'upload, sources
      de date retenues, stockage ré-encodé, ingestion manuelle, attribution à la
      révélation, `excluded`).
- [x] Section `/privacy` (EN + FR) dans `frontend/src/app/features/legal/` : données des
      personnalités, source, base légale, demande de retrait via
      contact@ageguessr.app. Mettre à jour les specs existantes.
- [x] `docs/roadmap.md` : cocher « Client Wikidata », « Stratégie photo » (reformulée en
      « photo datée »), « Endpoint proxy d'image ». « Schéma de données final » reste
      partiel : les personnes sont faites, les catégories et événements restent pour
      le plan des indices.
- [x] Agent `architecture-reviewer` sur le diff ; corriger.
- [x] Agent `code-reviewer` sur le diff (sourcing externe, RGPD, droit à l'image,
      licences, SSRF) ; corriger, ou remonter la question du droit à l'image à
      l'utilisateur.
- [x] `./mvnw test` + `pnpm test` + `pnpm lint` verts, puis `/pr` vers `develop`.

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert ; `cd frontend && pnpm test && pnpm lint` →
  vert.
- Rapports des deux agents reviewers : aucun point bloquant ouvert.
- CI de la PR verte.

### Phase Summary
**Docs** :
- `architecture.md` réécrit : « Sourcing des personnes » et « Anti-triche sur
  l'image ».
- `/privacy` section 8 (EN + FR, date passée au 2026-10-26).
- `roadmap.md` (Semaine 2, prérequis avant approbation, crédit photo en Semaine 4).
- `testing-strategy.md` (exception d'emplacement pour les tests d'intégration de
  classes package-private).

**Revues du 2026-10-26** : aucun point bloquant. Tout est corrigé dans cette PR, à la
demande de l'utilisateur (« corrige tout d'un coup »).

`architecture-reviewer` :
- 🟠 Double constructeur avec `@Autowired` (`WikimediaHttp`, `ImageDownloader`) →
  constructeur unique `@RequiredArgsConstructor`. `Sleeper` devient un bean, et
  `lombok.config` recopie `@Qualifier` sur le constructeur généré.
- 🟡 `WikimediaClientConfig` déplacé de `config/` vers `IngestPeople/`
  (package-private). La règle ArchUnit `RestClient` ne tolère donc plus que
  `IngestPeople..`.
- 🟡 `BirthStatement.Rank` remplacé par l'enum partagé `WikidataRank`.
- 🟡 Filtre de rang dédupliqué dans `WikidataRank.best`.

`code-reviewer` :
- 🟠 Effacement physique : un **trigger Postgres dans V4** supprime photos et images
  dès qu'une personne passe `excluded` ; la ligne `people` reste.
- 🟠 Droit à l'image des personnes vivantes → **prérequis avant d'approuver la moindre
  photo d'une personne vivante** (`architecture.md` et roadmap). C'est une décision
  produit/juridique, pas du code.
- 🟡 QID renvoyés par QLever validés par la regex avant interpolation dans `VALUES` ;
  ligne ignorée sinon.
- 🟡 Réponses JSON plafonnées à 50 Mo (`WikimediaHttp.MAX_JSON_BYTES`).
- 🟡 Logs d'ingestion : QID seuls, ni nom ni titre de fichier.
- 🟡 `/privacy` : « sources accessibles au public », intérêt légitime précisé,
  suppression confirmée sous un mois.
- 🟡 Attribution CC BY / BY-SA : prérequis inscrit à la Semaine 4 (crédit à la
  révélation) avant de servir des photos aux joueurs.

**Run complet après corrections (2026-10-26)** :
- backend `./mvnw test` : **483/483** ;
- frontend `pnpm test` : **138/138** ; lint et Prettier OK ;
- `./scripts/run-mutation-tests.sh` : code 0 en 279 s.
  - Global : 453/571 tués (79 %), test strength 93 %.
  - **`IngestPeople` : 350/371 tués (94 %), test strength 96 %.**
  - `GetImage` était à 0/4 : il n'est couvert que par le test fonctionnel, exclu de
    PIT. `GetImageHandlerTest` a été ajouté (3 tests).

Nouveaux tests :
- `WikidataRankTest` ;
- plafond JSON ;
- QID mal formés ignorés et jamais interpolés ;
- trigger d'exclusion, plus le contre-test « une mise à jour sans exclusion garde les
  photos ».


## Final Recap
**Livré** (PR `feature/people-sourcing` → `develop`) : la chaîne complète qui construit
le pool de personnes du jeu.
- **Liste** : personnalités Wikidata classées par notoriété via QLever, en 5 petites
  requêtes par page.
- **Dates** : naissance au jour près et décès, avec les rangs Wikidata.
- **Photos** : candidates Commons (`P18` + `depicts`), gardées seulement si la date de
  prise de vue donne un **âge exact**. Date déclarée, corroborée par l'EXIF, non
  contredite par le nom de fichier.
- **Sélection** : au moins 10 ans d'écart entre deux photos d'une même personne, 5 au
  maximum.
- **Stockage** : images ré-encodées sans métadonnées dans Postgres, statut de curation
  `PENDING`.
- **Endpoint** anti-triche `GET /api/images/{publicId}` qui ne sert que les photos
  `APPROVED`, avec un seul 404 pour tous les refus.
- **Effacement physique** des photos d'une personne exclue (trigger Postgres).
- **Section 8 de `/privacy`** (EN + FR) pour les personnalités (art. 14 RGPD).

**Chiffres clés** :
- Spike : ~30 % des personnes avec `P18` ont une photo à âge exact corroborée par
  l'EXIF, soit des dizaines de milliers de personnes.
- Runs réels de 30 personnes : ~5 min, ~1,4 photo par personne, ~170 Ko par image.
- **~43 % des photos automatiques montrent un groupe, une foule ou personne**, d'où la
  curation manuelle obligatoire.
- Tests : 483 backend, 138 frontend. Mutation `IngestPeople` : 94 %.

**Écarts par rapport au plan initial, tous décidés avec l'utilisateur ou imposés par le
réel** :
- âge sur la photo plutôt qu'âge actuel ;
- EXIF strict ;
- écart de 10 ans au lieu d'une photo par année et par âge ;
- curation manuelle ;
- client QLever refondu (la requête unique dépassait le délai) ;
- vignettes servies par `thumb.wikimedia.org` ;
- `fetchFiles` en POST (HTTP 414) ;
- profil `ingest` avec `server.port=-1` et arrêt explicite de la JVM ;
- labels QLever lus via `STRSTARTS`.

**Hors scope, à faire ensuite** :
- **`feature/photo-curation`** : rôle admin, endpoints approuver/rejeter, page Angular,
  pose de `excluded`.
- **Droit à l'image** des personnes vivantes à trancher avant toute approbation.
- **Crédit photo** (auteur + licence) à la révélation du round, en Semaine 4, avant de
  servir des photos aux joueurs.
- Régénération du `publicId` d'une photo resélectionnée (Semaine 3).
- Plan dédié aux indices événementiels.

**Problème d'outillage trouvé en route et corrigé à part** : PIT n'avait jamais
fonctionné (PR #16, mergée).

## Deployment Plan
1. **Merge de la PR dans `develop`**, puis release `develop` → `main`, en **merge
   commit** (voir la mémoire du projet sur les releases).
2. **Au démarrage de l'API en prod**, Flyway applique V4 : trois tables vides, plus le
   trigger. Rien n'est servi tant qu'aucune photo n'est `APPROVED`. Le déploiement est
   donc sans effet visible pour les joueurs.
3. **Ne pas lancer l'ingestion en production avant** :
   - la PR `feature/photo-curation`, sinon les photos s'accumulent sans moyen de les
     valider ;
   - la vérification de l'espace disque du VPS. Ordre de grandeur : ~3 000 personnes ×
     ~1,4 photo × ~170 Ko ≈ **0,7 Go** dans Postgres, à ajouter au volume des backups.
4. **Lancer l'ingestion** (quand les conditions ci-dessus sont réunies) :
   - en dehors des heures de pointe de QLever, depuis le VPS, avec l'image de l'API et
     le profil `ingest`. Par exemple :
     `docker compose -f docker-compose.prod.yml run --rm api --spring.profiles.active=prod,ingest --limit=3000`
     (adapter au nom réel du service et aux profils de prod) ;
   - durée attendue : **~8 h**. En cas d'arrêt (QLever en 429 prolongé, coupure), la
     relancer : elle reprend sans doublon ;
   - aucun secret supplémentaire : Wikidata, QLever et Commons sont publics. Le
     User-Agent est configurable via `WIKIMEDIA_USER_AGENT`.
5. **Après l'ingestion** : contrôler le bilan dans les logs (personnes gardées, rejets
   par motif) et la taille de la base. Puis passe de curation.

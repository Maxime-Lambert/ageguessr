# Roadmap AgeGuessr

Source de vérité de l'avancement produit, découpée en semaines indicatives.
À cocher à chaque PR mergée (pas besoin d'attendre la fin d'une semaine pour avancer sur
la suivante).

## À vérifier à la main en prod

Tests que seuls un compte et la boîte mail admin permettent de faire. À cocher, puis à
retirer une fois tous faits.

- [ ] Calendrier (`feature/daily-calendar`) : onglet « Calendrier », le mois en cours
      s'affiche avec aujourd'hui entouré ; jouer un jour passé depuis sa case, revenir :
      la case est « Terminée » ; le profil n'a plus de « série » ; le lendemain, une
      partie commencée la veille se termine encore
- [ ] Partage en défi (`fix/daily-share-challenge`, `fix/share-says-age`) : partie du
      jour finie, « Partager mon résultat », coller : « AgeGuessr · <date> », « J'ai
      deviné l'âge de 3 personnes en photo 📸 », la grille et le score, puis un qualificatif et « Tu fais mieux ? 👉 <site> » ; pareil
      en anglais (« Can you beat that? ») ; plus de bouton « Comparer avec vos amis » sous
      le récapitulatif, même connecté
- [ ] SEO (`feature/seo`) : coller `https://ageguessr.app` dans WhatsApp ou Discord
      montre la carte (image, titre et description dans la langue) ;
      `https://ageguessr.app/robots.txt` et `/sitemap.xml` s'affichent ; dans Google
      Search Console, ajouter le domaine, soumettre `https://ageguessr.app/sitemap.xml` et
      inspecter `/fr/how-to-play` (canonical `…/fr/how-to-play`, indexable) et
      `/fr/login` (exclue par `noindex`)
- [ ] Export des données (`feature/personal-data-export`) : sur `/account`, « Télécharger
      mes données » enregistre `ageguessr-data-<date>.json` ; il contient l'email, le pseudo,
      les parties jouées et les amis, aucun hash ni nom de personne d'une manche ; `/privacy`
      mentionne le téléchargement
- [ ] Purge des comptes (`feature/account-purge`) : le lendemain du déploiement,
      `docker logs ageguessr-prod-backend-1 | grep "Accounts purged"` montre une ligne
      (00:50 UTC) ; `/privacy` section 4 annonce 7 jours et 3 ans
- [ ] Backup et logs (`chore/backup-and-log-rotation`) : `sudo ls -l
      /opt/ageguessr/backups/daily` montre un dump `-rw-------` ; `docker ps` montre
      `ageguessr-prod-backup-1` `healthy` ; `docker inspect ageguessr-prod-backend-1
      --format '{{json .HostConfig.LogConfig}}'` donne `max-size 10m`, `max-file 3` ; le
      lendemain, `docker logs ageguessr-prod-backup-1` montre le dump de 03:00 UTC
- [x] Monitoring (`feature/monitoring`) : dans UptimeRobot, deux surveillances HTTP(S)
      sur `https://ageguessr.app/api/health` et `https://ageguessr.app/api/health/backup`
      (5 min, alerte par email), toutes deux « Up » ; bouton de test de notification reçu ;
      le premier email d'erreur du backend arrivera à la première vraie erreur (pas de
      moyen sûr d'en provoquer une en prod)

- [x] Accès DBeaver (`chore/prod-db-access`) : définir le mot de passe de
      `ageguessr_readonly` puis configurer DBeaver (procédure dans
      `docs/decisions/architecture.md`, « Accès à la base de prod ») ; un `SELECT` sur
      `users` répond, un `UPDATE` est refusé (« permission denied ») ; depuis ton PC, sans
      tunnel, `nc -zv <vps> 15432` échoue

## Semaine 1 — Fondations
- [x] Initialisation projet Spring Boot (Java 25) + Angular (Claude Code)
- [x] Docker Compose local fonctionnel (PostgreSQL + Redis)
- [x] Authentification complète (inscription, connexion, JWT + refresh token rotatif,
      jeu possible sans compte) — PR #1
- [x] Charte graphique définie (`docs/decisions/design.md`) — couleurs vérifiées WCAG AA,
      typo, formes, icônes, ton « enquête » à l'essai — PR `chore/design-charter`
- [x] Mise en place technique de la charte (tokens Tailwind, polices, toggle thème,
      test de contraste des tokens) — PR `chore/design-charter`
- [x] Logo/wordmark AgeGuessr + favicon (« A » Fraunces, SVG + ICO + Apple touch icon)
      — PR `chore/design-charter`
- [x] Mentions légales, CGU et politique de confidentialité (EN + FR, droit français,
      acceptation des CGU versionnée à l'inscription, 13 ans minimum) — PR
      `feature/legal-pages`
- [x] CI/CD GitHub Actions → déploiement automatique sur le VPS (images buildées en CI,
      poussées sur GHCR) — PR #2, #4, release #5 ; en ligne sur https://ageguessr.app
- [x] Structure de base du Caddyfile pour l'app réelle (domaine `ageguessr.app`,
      passerelle Caddy partagée du VPS) — PR #2

## Semaine 2 — Données du jeu
- [x] Client Wikidata via QLever (classement par sitelinks, `P569`/`P570` avec rangs et
      précision, `P18`, labels EN/FR) — PR `feature/people-sourcing`
- [x] Photos datées avec âge exact : date déclarée (SDC/`{{Information}}`) corroborée
      par l'EXIF, au moins 10 ans d'écart entre deux photos d'une même personne,
      validées sur échantillon réel (spike de 400 personnes, puis 2 runs de 30) —
      PR `feature/people-sourcing`
- [x] Endpoint proxy d'image anti-triche (`/api/images/{id}`, copie ré-encodée sans
      métadonnées, jamais l'URL Commons) — PR `feature/people-sourcing`
- [x] Curation manuelle des photos : rôle admin (SQL), approuver/rejeter/annuler,
      page `/admin/curation` au clavier, demandes de retrait (recherche + exclusion)
      — PR `feature/photo-curation`
- [x] Ligne éditoriale du pool : exclusion des auteurs de violences de masse et des
      dirigeants d'un pays en guerre, rappelée sur la page de curation — PR
      `feature/photo-curation`
- [x] Droit à l'image des personnes vivantes : risque accepté, couvert par le retrait
      sur demande ; ingestion limitée aux personnes nées en 1930 ou après — PR
      `feature/photo-curation`
- [x] Pool équilibré et sans mineur : aucune photo de moins de 18 ans, 18 cases
      continent de nationalité × genre (« autre » à son poids réel), préférence des
      tranches d'âge rares, cible de 5 000 photos en jeu, mesure du rendement
      (`--sample`, `--dry-run`) — PR `feature/people-pool-balance`
- [x] Ingestion en production (cible 5 000 photos en jeu), puis curation et relances
      jusqu'à 5 000 photos approuvées. Curation terminée le 2026-10-30 ; quelques erreurs
      sont passées, d'où le signalement par les joueurs (Semaine 4)
- [x] Vagues d'ingestion ciblées : professions exclues ou seules (footballeurs écartés
      pour la suite), cases, tranches d'âge ; quotas comptés en photos approuvées
      attendues (en attente × taux d'approbation) ; `--analyze` pour voir le pool et la
      prochaine vague — PR `feature/exclude-footballers`
- [x] Photos d'au moins 600 px de haut : miniatures demandées en hauteur, rejet des
      originaux trop petits, correction des photos déjà stockées (`--fix-small-images`),
      taille affichée en curation — PR `feature/min-image-height`
- [x] Ligne éditoriale automatique : condamnés ou poursuivis pour crimes graves,
      dictateurs et dirigeants autoritaires, extrême droite, écartés de chaque vague et
      exclus de la base (`--apply-editorial-line`), motif d'exclusion en base (V10) — PR
      `feature/editorial-line`
- [x] Ingestion des indices (Musique, Cinéma, Sport, Science et techno, Monde,
      Célébrités), au moins 5 par année, par catégorie et par public, sur 1932-2008 pour
      Célébrités et 1932-2015 ailleurs : extraction, table `hints`, import idempotent, page
      `/admin/hints` (traductions et couverture), PR `feature/hint-ingestion`. 7 978
      indices importés en prod et traductions validées. Seuls les indices
      traduits sont relus, les autres entrent directement en jeu. Célébrités : extraction
      relancée à la main chaque année vers mars, pour ajouter les personnes qui viennent
      d'avoir 18 ans
- [x] Schéma de données final : personnes et photos (V4, PR `feature/people-sourcing`),
      indices (V11, PR `feature/hint-ingestion`)

## Semaine 3 — Moteur de jeu
- [x] Tirage quotidien : 3 photos par jour (configurable), minuit UTC et au démarrage,
      table `daily_rounds` (V12). Hasard uniforme, jamais deux personnes de la même case
      (continent × genre) le même jour, pas de rejeu d'une photo avant **3 ans**, pas de
      pondération par âge ni de délai par personne. Nouveau `publicId` à chaque tirage.
      Une photo rejetée ou une personne exclue neutralise la manche pour tous, et un
      simple signalement ne neutralise rien (voir `docs/decisions/architecture.md`,
      « Tirage quotidien ») — PR `feature/daily-draw`
- [x] Logique de guess en 2 étapes : 1er essai, puis « plus jeune / plus vieux » (le
      chaud/froid donnait trop d'aide, voir « Déroulé d'une manche »), sélection d'une
      catégorie d'indice, 2e essai. Saisie de 18 à 100 ans. Endpoints publics, JWT
      facultatif, sans jamais révéler l'âge, l'identité, le crédit ni l'année de
      l'indice avant la réponse. État en base (`game_plays`, V13), cookie invité
      `ageguessr_guest`, purge des parties invitées après 7 jours, limite par IP. Tirage
      activé par défaut (voir « État d'une partie ») — PR `feature/daily-guess`
- [x] Système de points : jusqu'à 500 points par essai selon l'écart (piste
      `500 × e^(−écart/10)`, à régler par playtest), manche = somme des deux essais, un
      1er essai exact vaut 1 000. Manches neutralisées hors du score du jour
- [x] Cache HTTP des images : `Cache-Control: max-age=86400, public` sur
      `/api/images/{publicId}`, en place depuis PR `feature/people-sourcing` (testé dans
      `GetImageFunctionalTest`). Volontairement pas plus long : une photo rejetée après
      un signalement (personne qui paraît mineure, par exemple) ne doit pas rester des
      mois dans le cache des navigateurs, et une partie du jour ne se rejoue pas le
      lendemain. Remplace le cache Redis des parties : la table `daily_rounds` évite déjà
      de recalculer

## Semaine 4 — Interface de jeu
- [x] Écran principal : photo, input d'âge, « plus jeune / plus vieux » après le 1er
      essai. Le jeu est à `/` — PR `feature/daily-guess`
- [x] Sélection de catégorie d'indice + affichage de l'indice
- [x] Écran de résultat (âge réel, identité révélée, points gagnés, **crédit de la photo :
      auteur + licence**, obligatoire avant de servir des photos CC BY / BY-SA aux joueurs ;
      indice réaffiché avec son année et sa source, pour l'attribution CC BY-SA)
  - Identité : nom (`name_fr`/`name_en`, déjà en base) + lien Wikipedia sans rien stocker,
    via `https://www.wikidata.org/wiki/Special:GoToLinkedPage/<lang>wiki/<QID>`
    (vérifié le 2026-11-02 : 302 vers l'article ; sans article dans la langue, Wikidata
    affiche sa propre page)
  - Fonction principale (optionnel) : P106 est lu à l'ingestion mais pas stocké. Il
    faudrait une colonne + le libellé fr/en, et un rattrapage des personnes existantes
    (une requête QLever par lot de QID). Choisir « la » fonction principale n'est pas
    évident (P106 n'a pas d'ordre fiable) : préférer la description courte Wikidata
    (`schema:description`), plus parlante (« actrice et chanteuse française »)
- [x] Signalement d'une photo par les joueurs, depuis l'écran de résultat (identité
      connue, donc « mauvaise personne » signalable, et aucun indice donné avant de
      répondre) — PR `feature/player-reports`
  - Joueur : petit bouton « Signaler l'image » puis motif dans une liste fermée (image
    trop petite, personne pas clairement identifiable sur la photo, ce n'est pas la
    bonne personne, la personne paraît mineure, cette personne me dérange, autre), sans
    texte libre ni compte. « Cette personne me dérange » vise la ligne éditoriale et
    mène plutôt à une exclusion qu'à un rejet de la photo. Rien de personnel en base :
    ni IP, ni utilisateur. Limite par IP
    via `RateLimiter`, le filtre actuel ne couvrant que `/api/auth/`
  - Base : table `photo_reports` en `ON DELETE CASCADE` sur la photo, pour qu'une
    exclusion efface aussi les signalements
  - Admin : file triée par nombre de signalements dans la page de curation, avec
    rejeter (`ReviewPhotoHandler` accepte déjà APPROVED → REJECTED), garder (les
    signalements sont classés) ou exclure la personne. Aucun moyen de retrouver une
    photo approuvée aujourd'hui
  - Aucun retrait automatique : un script anonyme pourrait vider le pool
  - Notification prononcée de l'admin (email par exemple) quand plusieurs joueurs
    signalent une même photo, surtout si elle est dans la partie du jour, pour réagir
    vite (seuil à fixer)
  - Photo de la partie du jour rejetée : tranché avec le tirage quotidien. La manche
    est neutralisée pour tous les joueurs et sort du score du jour (`/api/images`
    répond alors 404)
- [x] Signalement d'un indice par les joueurs, depuis l'écran de résultat : c'est le
      filet de sécurité des indices, que personne ne relit (sauf les traductions) — PR
      `feature/player-reports`
  - Joueur : mêmes règles que pour les photos, avec les motifs mauvaise année,
    information fausse, incompréhensible, cet indice me dérange, autre. Sans texte
    libre ni compte, rien de personnel en base, limite par IP
  - Base : table `hint_reports` en `ON DELETE CASCADE` sur l'indice, séparée de
    `photo_reports` (les features ne dépendent pas l'une de l'autre)
  - Admin : file triée par nombre de signalements dans `/admin/hints`, avec rejeter,
    garder ou corriger le texte
  - Aucun retrait automatique. Un indice rejeté n'est plus tiré ; sous 5 indices, la
    catégorie est masquée pour cette année
- [x] Enchaînement des 3 personnes du jour + récapitulatif final
- [x] Vocabulaire de jeu centralisé dans un seul fichier (essai du ton « enquête »,
      critère de validation au premier playtest — voir `docs/decisions/design.md`)
- [x] Trancher le duotone des photos sur de vraies photos Commons : pas de duotone, un
      filtre léger et le carton crème du tirage (`design.md`, « Traitement des photos »)
- [x] Site en anglais et en français (`/en/`, `/fr/`, sélecteur dans l'en-tête), langue
      enregistrée sur le compte et emails dans cette langue, langue des indices choisie
      sur l'écran de jeu, administration en anglais — PR `feature/i18n`
- [x] Enquête libre : séries de 5, 10 ou 20 photos hors partie du jour, et Défi :
      une série partagée par un lien, jouée une fois par chaque ami, avec un classement
      — PR `feature/free-investigation`

- [x] Refonte visuelle, direction A « Archives soignées » (`plans/design-overhaul.md`) :
      maquettes (`docs/decisions/mockups/`, PR `chore/design-mockups`), socle CDK +
      `shared/ui` et navigation (PR #40), écrans de jeu (PR #41), auth, compte, pages
      légales, admin et bandeau CGU (PR `feature/secondary-screens-redesign`)

## Semaine 5 — Compte utilisateur + historique
- [x] Inscription/connexion (frontend) + navigation (en-tête invité/connecté) — PR
      `feature/auth-completion`
- [x] Pseudo public unique et modifiable, mot de passe oublié, page compte (pseudo, mot
      de passe), suppression de compte confirmée par email — PR `feature/auth-completion`
- [x] Politique de mot de passe : 12 caractères minimum, sans composition, refus des mots
      de passe courants et de ceux contenant le pseudo ou l'email — PR
      `feature/password-policy`
- [x] Page profil `/profile`, privée : série en cours et meilleure série, dossiers
      classés, score moyen et meilleur score — PR `feature/game-history`
- [x] Historique des parties du jour passées, manche par manche, 30 jours par page, sans
      photo (anti-triche). Les parties jouées en invité sont rattachées au compte à la
      connexion — PR `feature/game-history`

## Semaine 6 — Amis
- [x] Demandes d'ami (envoi, acceptation, refus, annulation) par pseudo exact — PR
      `feature/friends` (le lien d'invitation livré avec a été retiré le 2026-11-11,
      `chore/remove-friend-invite-link`)
- [x] Liste d'amis (retrait confirmé), demandes reçues comptées dans le menu — PR
      `feature/friends`
- [x] Comparaison des scores du jour entre amis, visible une fois sa propre partie finie,
      points seulement — PR `feature/friends`
- [x] Leaderboard entre amis : moyenne par partie, sans minimum de parties — PR
      `feature/friends`

## Semaine 7 — Polish
- [x] SEO léger : description et aperçu de lien (Open Graph, image) traduits dans
      `index.html`, `robots.txt`, `sitemap.xml`, canonical/hreflang et `noindex` des pages
      privées posés à chaque navigation ; SSR reporté à la landing page — PR `feature/seo`
- [x] Export des données personnelles (droit d'accès et de portabilité en self-service) :
      fichier JSON téléchargé depuis `/account`, chaque feature exporte ses tables, test de
      couverture sur le schéma — PR `feature/personal-data-export`
- [x] Purge des comptes jamais vérifiés (7 jours) et inactifs (3 ans, email 30 jours
      avant), annoncée dans `/privacy` — PR `feature/account-purge`
- [x] Ré-acceptation des CGU à chaque nouvelle version (`users.terms_version` déjà
      enregistré) — bandeau non bloquant, PR `feature/terms-reacceptance`
- ~~Double authentification (TOTP)~~ — écartée (nov. 2026) : disproportionnée pour une
      dizaine de joueurs et un enjeu faible, ~3 300 lignes de code d'auth à maintenir
      (voir `docs/decisions/architecture.md`)
- [x] Calendrier des parties passées (`/calendar`, mois par mois, 4 états par jour,
      ouvert aux invités) et fin des séries de jours — PR `feature/daily-calendar`
- [x] Partage de résultat façon Wordle (grille/emoji, pas de compte requis) : sur le
      récapitulatif du jour, date, score, une case par manche (🎯 pour un 1er essai exact)
      et le lien du site ; feuille de partage sur téléphone, copie ailleurs — PR
      `feature/daily-share` ; puis un titre qualifiant le résultat, une légende des cases
      et une invitation à jouer — PR `fix/daily-share-explained` ; puis un défi plutôt
      qu'un compte rendu (le principe en une ligne, « Tu fais mieux ? »), sans légende —
      PR `fix/daily-share-challenge`
- [x] Aider davantage pour le 2e essai (retours des joueurs : les indices n'aidaient pas) :
      sens + tranche d'écart (1-5 / 6-15 / 16+ ans) à la place du simple plus/moins,
      saisie limitée à la plage restante, 3 pistes de catégories différentes au lieu
      d'une, Célébrités qui donne l'âge directement (V21), règles avec un exemple chiffré,
      signalement indice par indice — PR `feature/better-hints-gameplay`
- [x] Catégorie Littérature (7e) : parutions avec leur auteur et grands prix littéraires,
      ~800 indices par langue, au moins 4 catégories jouables chaque année en FR et en EN
      — PR `feature/literature-hints`
- [x] Indices plus connus : faits Wikidata classés par les vues sur 12 mois de leur
      propre article, les 10 plus vus par catégorie, année et langue au-dessus d'un
      plancher par décennie ; 8 674 indices, au moins 3 catégories jouables chaque année
      en FR comme en EN ; anciens indices retirés par l'import (V22) — PR
      `feature/hint-notoriety`
- [x] Explication des pistes (retours des joueurs : indices pas assez clairs) : chaque
      carte a une bulle avec le genre d'indice, la règle et un exemple calculé depuis
      l'année de la partie, ouverte par ⓘ seul (survol, tap ou Entrée) — PR
      `feature/lead-help`, puis `fix/lead-help-on-info-only`

## Semaine 8 — Stabilisation et déploiement
- [x] Tests d'intégration sur les endpoints critiques : vraie API (Spring + MockMvc +
      Testcontainers) dans les tests de chaque feature
- [x] Tests E2E sur les parcours principaux (Playwright, en CI) : inscription/connexion,
      partie du jour, défi, mot de passe oublié, suppression de compte, compte en
      français (`frontend/e2e/journeys`), plus les specs par écran (`e2e/component`)
- [x] Monitoring : `/api/health` vérifie PostgreSQL et Redis, `/api/health/backup` la
      fraîcheur du dernier dump, surveillés par UptimeRobot ; erreurs du backend envoyées
      par email à l'admin, regroupées — PR `feature/monitoring`
- [x] Rotation des logs Docker : 3 × 10 Mo par conteneur du compose de prod — PR
      `chore/backup-and-log-rotation`
- [x] Backup PostgreSQL : dump quotidien sur le disque du VPS, 30 jours au plus, annoncé
      dans `/privacy`, restauration documentée — PR `chore/backup-and-log-rotation`
- ~~Journal des suppressions de compte~~ — écarté (nov. 2026) : ne servirait qu'après
      la restauration d'un backup ; si elle arrive, re-supprimer à la main les comptes
      supprimés depuis la date du dump
- ~~Copie des backups hors du VPS~~ — écartée (nov. 2026) : OVH Object Storage est
      payant et l'audience petite ; copie manuelle documentée dans
      `docs/decisions/architecture.md` (« Sauvegardes et logs »)
- [x] Accès à la base de prod depuis DBeaver : tunnel SSH vers `127.0.0.1:15432`, rôle en
      lecture seule — PR `chore/prod-db-access`
- [ ] Déploiement prod final et smoke tests : la prod tourne et chaque déploiement
      passe un smoke test ; à cocher quand la section « À vérifier à la main » est vide
- ~~Landing page de lancement~~ — écartée (nov. 2026) : le jeu à `/` sert de page
      d'accueil

## Retours testeurs du 2026-11-23

Quatre joueurs sur la prod. Décisions prises avec l'utilisateur le 2026-11-23.

- [x] Petites corrections : formulations FR, bulle ⓘ au clic seulement (plus de survol)
      et aussi sur les indices révélés, cartes d'indice d'une seule taille, nom du
      résultat visiblement cliquable, confettis sur un âge exact, récap avec l'âge et les
      essais, partage une ligne par manche (`🟨 499/1000`), compte à rebours vers la
      prochaine partie — PR `fix/tester-feedback-quick-wins`
- [x] Partie libre : « Série » devient « Partie libre » (*Free play*) partout, bouton
      « Abandonner » (avec confirmation) sur une partie en cours, plus de durée estimée
      sur les longueurs — PR `feature/free-play`
- [x] En-tête fixe, compact, avec une icône « ? » vers « Comment jouer » (le pied de page
      n'est pas visible en partie ni en haut des pages légales) — PR `feature/sticky-header`
- [x] Nouvelles catégories d'indice : Télévision (grosses émissions et séries) et Jeux
      vidéo (dès 1972 environ) ; on ne montre que les catégories disponibles, la fuite
      sur l'année est acceptée (les indices viennent après le premier essai) ; 9 au plus,
      grille 3 × 3 — PR `feature/tv-and-video-game-hints`
- [ ] Stats du jour, invités compris : « mieux que 72 % des joueurs », âge moyen donné
      par manche ; affichées une fois la partie finie, au-delà d'une dizaine de joueurs

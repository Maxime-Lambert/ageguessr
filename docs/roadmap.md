# Roadmap AgeGuessr

Source de vérité de l'avancement produit, découpée en semaines indicatives.
À cocher à chaque PR mergée (pas besoin d'attendre la fin d'une semaine pour avancer sur
la suivante).

## À vérifier à la main en prod

Tests que seuls un compte et la boîte mail admin permettent de faire. À cocher, puis à
retirer une fois tous faits.

- [x] Bandeau de ré-acceptation des CGU (release #39, confirmé le 2026-11-09)
- [ ] Signaler une photo et un indice depuis l'écran de résultat d'une manche terminée
- [ ] Retrouver ces signalements dans `/admin/curation` (photos) et `/admin/hints`
      (indices), puis rejeter, garder ou exclure
- [ ] Recevoir l'email d'alerte admin quand plusieurs joueurs signalent la même photo
      (`ADMIN_EMAIL` du `.env` de prod)
- [ ] Indices musique corrigés par la migration V17 (release #45) : dans `/admin/hints`,
      1989, « Pour toi Arménie » de Charles Aznavour et divers artistes
- [ ] Profil (release #48) : jouer la partie du jour en invité, se connecter sur le même
      navigateur, ouvrir « Mon profil » : la partie y est, série de 1
- [ ] Profil : une manche non terminée ne montre ni nom, ni âge, ni points ; « Dossiers
      plus anciens » charge la page suivante quand il y a plus de 30 jours
- [ ] Politique de confidentialité (EN + FR) : date du 2026-11-10, paragraphe sur les
      parties invité rattachées au compte et ce que voient les amis
- [ ] Parcours e2e « invité → compte → profil » (`e2e/journeys/daily-game.spec.ts`) :
      jamais lancé, les parcours ne tournent dans aucune CI — à lancer à la main
- [ ] Amis (release de `feature/friends`) : deux comptes, une demande par pseudo, le
      compteur dans le menu de l'autre, accepter ; la comparaison du jour reste fermée tant
      que sa propre partie n'est pas finie, puis montre les points de l'ami
- [ ] Amis : plus de lien d'invitation sur `/friends` ; un ancien lien
      `/friends/invite/…` ouvre la page Amis
- [ ] Parcours e2e « amis » (`e2e/journeys/daily-game.spec.ts`, dernier test) : jamais
      lancé, comme les autres parcours — à lancer à la main
- [ ] Pistes et chaud/froid (`feature/better-hints-gameplay`) : après un 1er essai faux,
      « Plus de 30 ans, de 6 à 15 ans d'écart » et « Entre 36 et 45 ans », saisie bornée ;
      choisir 3 pistes, puis voir 3 indices ; au résultat, chaque indice a son année et son
      propre signalement ; un indice Célébrités dit « l'âge qu'a (ou qu'aurait) cette année X » (V21)
- [ ] Parcours e2e `daily-game` mis à jour pour les 3 pistes : à lancer à la main, comme
      les autres
- [ ] Littérature, année en deux chiffres, personne nommée (`feature/literature-hints`,
      `fix/two-digit-year-in-hints`) : en prod, un seul import avec
      `--app.hints.import.retire-absent=true` ; rapport attendu `read=10263`, `added=1598`,
      `named=2592`, `retired=9`, `refused={}` ; la piste Littérature apparaît (icône livre),
      « Mai 68 » n'est plus proposé, `/admin/hints` a une colonne Littérature
- [ ] Indices par notoriété (`feature/hint-notoriety`) : en prod, lancer l'import avec
      `--app.hints.import.retire-absent=true` (rapport : ~8 674 ajoutés, les anciens
      retirés, 0 refus), puis dans `/admin/hints` aucune année sous 3 catégories ; en jeu,
      un indice dit « Source : Wikidata (CC0) » et une manche passée garde ses anciens
      indices
- [ ] Retours du 2026-11-11 (`fix/smoke-test-feedback`) : connecté, finir la 1re manche
      du jour puis recharger (sur l'écran de score ou après « Affaire suivante ») →
      reprise à la 2e manche ; le lien d'un email d'alerte ouvre la file admin même avec un
      compte en français ; « Envoyer » du signalement d'indice lisible en thème sombre
- [ ] Explication des pistes (`feature/lead-help`) : sur téléphone, un tap sur ⓘ ouvre la
      bulle d'une carte, un tap ailleurs la ferme, la bulle de la colonne de droite reste
      dans l'écran ; sur ordinateur, la bulle s'ouvre après un court survol de la carte et
      se ferme en sortant, le clic choisit toujours la piste ; Tab sur une carte l'ouvre,
      Échap la ferme ; l'exemple dit « il y a 29 ans » pour Titanic en 2026
- [ ] Partage du résultat du jour (`feature/daily-share`) : partie finie, « Partager mon
      résultat » ; sur téléphone la feuille de partage s'ouvre, sur ordinateur « Copié. »
      puis coller dans un message : date du jour en français (13/11/2026), le score, une
      case par manche, 🎯 pour une manche trouvée du 1er coup, le lien du site

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
- [ ] SEO (meta tags, sitemap, Angular SSR si pertinent pour le référencement)
- [ ] Export des données personnelles (droit d'accès et de portabilité en self-service ;
      suppression déjà livrée en Semaine 5, pages légales en Semaine 1, pas de bannière
      cookies nécessaire tant qu'il n'y a que des traceurs strictement nécessaires)
- [ ] Purge des comptes jamais vérifiés et des comptes inactifs (durées à fixer, puis à
      annoncer dans la politique de confidentialité)
- [x] Ré-acceptation des CGU à chaque nouvelle version (`users.terms_version` déjà
      enregistré) — bandeau non bloquant, PR `feature/terms-reacceptance`
- [ ] Double authentification (TOTP), seule protection si la boîte mail d'un joueur est
      compromise (voir `docs/decisions/architecture.md`, « Récupération du compte »)
- [x] Partage de résultat façon Wordle (grille/emoji, pas de compte requis) : sur le
      récapitulatif du jour, date, score, une case par manche (🎯 pour un 1er essai exact)
      et le lien du site ; feuille de partage sur téléphone, copie ailleurs — PR
      `feature/daily-share` ; puis un titre qualifiant le résultat, une légende des cases
      et une invitation à jouer — PR `fix/daily-share-explained`
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
      l'année de la partie ; survol à la souris, ⓘ au doigt, Tab au clavier — PR
      `feature/lead-help`

## Semaine 8 — Stabilisation et déploiement
- [ ] Tests d'intégration sur les endpoints critiques
- [ ] Tests E2E sur les parcours principaux (Playwright)
- [ ] Monitoring (uptime, erreurs) + rotation des logs Docker (`json-file` sans
      `max-size` aujourd'hui, conservation illimitée)
- [ ] Backup PostgreSQL (annoncer sa durée de conservation dans `/privacy`)
- [ ] Déploiement prod final et smoke tests
- [ ] Landing page de lancement, qui tient aussi lieu de page d'accueil publique (fusion
      décidée le 2026-11-09 : le jeu reste à `/`)

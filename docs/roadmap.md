# Roadmap Ageguessr

Source de vérité de l'avancement produit, découpée en semaines indicatives.
À cocher à chaque PR mergée (pas besoin d'attendre la fin d'une semaine pour avancer sur
la suivante).

## Semaine 1 — Fondations
- [x] Initialisation projet Spring Boot (Java 25) + Angular (Claude Code)
- [x] Docker Compose local fonctionnel (PostgreSQL + Redis)
- [x] Authentification complète (inscription, connexion, JWT + refresh token rotatif,
      jeu possible sans compte) — PR #1
- [x] Charte graphique définie (`docs/decisions/design.md`) — couleurs vérifiées WCAG AA,
      typo, formes, icônes, ton « enquête » à l'essai — PR `chore/design-charter`
- [x] Mise en place technique de la charte (tokens Tailwind, polices, toggle thème,
      test de contraste des tokens) — PR `chore/design-charter`
- [x] Logo/wordmark Ageguessr + favicon (« A » Fraunces, SVG + ICO + Apple touch icon)
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
- [ ] Ingestion des indices (Musique, Cinéma, Sport, Science et techno, Monde,
      Célébrités), au moins 5 par année, par catégorie et par public, sur 1932-2015 :
      prototype musique validé en local, puis toutes les catégories dans la même PR.
      Une source précise par catégorie. Seuls les indices traduits sont relus, les
      autres entrent directement en jeu (voir `plans/active-plan.md`). Célébrités :
      extraction relancée à la main chaque année vers mars, pour ajouter les personnes
      qui viennent d'avoir 18 ans
- [ ] Schéma de données final : personnes et photos faites (V4, PR
      `feature/people-sourcing`) ; indices à faire (V11) avec le plan des indices

## Semaine 3 — Moteur de jeu
- [ ] Algorithme de sélection quotidienne (3 personnes/jour, minuit UTC). Une même
      photo n'est pas rejouée avant **3 ans** (le pool vise au moins 5 000 photos
      approuvées, 3 ans en consomment 3 285). Les règles portent sur la personne, pas
      sur la photo : jamais deux photos d'une même personne dans une partie, et un
      délai minimal entre deux apparitions d'une même personne. Tirer les tranches d'âge
      de 8 ans (18-25 … 82+) de façon équilibrée, la collecte n'y penchant que
      faiblement (voir `docs/decisions/architecture.md`)
- [ ] Logique de guess en 2 étapes : 1ère tentative → chaud/froid, sélection d'une
      catégorie d'indice, 2e tentative
- [ ] Système de points (bonus 1er coup, majorité des points sur la précision du 2e coup
      — formule à finaliser par playtest)
- [ ] Cache Redis des parties du jour déjà calculées

## Semaine 4 — Interface de jeu
- [ ] Écran principal : photo, input d'âge, jauge chaud/froid
- [ ] Sélection de catégorie d'indice + affichage de l'indice
- [ ] Écran de résultat (âge réel, identité révélée, points gagnés, **crédit de la photo :
      auteur + licence**, obligatoire avant de servir des photos CC BY / BY-SA aux joueurs ;
      indice réaffiché avec son année et sa source, pour l'attribution CC BY-SA)
  - Identité : nom (`name_fr`/`name_en`, déjà en base) + lien Wikipedia sans rien stocker,
    via `https://<lang>.wikipedia.org/wiki/Special:GoToLinkedPage/<lang>wiki/<QID>`
    (repli sur la page Wikidata si l'article n'existe pas dans la langue)
  - Fonction principale (optionnel) : P106 est lu à l'ingestion mais pas stocké. Il
    faudrait une colonne + le libellé fr/en, et un rattrapage des personnes existantes
    (une requête QLever par lot de QID). Choisir « la » fonction principale n'est pas
    évident (P106 n'a pas d'ordre fiable) : préférer la description courte Wikidata
    (`schema:description`), plus parlante (« actrice et chanteuse française »)
- [ ] Signalement d'une photo par les joueurs, depuis l'écran de résultat (identité
      connue, donc « mauvaise personne » signalable, et aucun indice donné avant de
      répondre)
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
  - À trancher avec le tirage quotidien (Semaine 3) : que devient la partie du jour si
    une de ses photos est rejetée (`/api/images` répond alors 404)
- [ ] Signalement d'un indice par les joueurs, depuis l'écran de résultat : c'est le
      filet de sécurité des indices, que personne ne relit (sauf les traductions)
  - Joueur : mêmes règles que pour les photos, avec les motifs mauvaise année,
    information fausse, incompréhensible, cet indice me dérange, autre. Sans texte
    libre ni compte, rien de personnel en base, limite par IP
  - Base : table `hint_reports` en `ON DELETE CASCADE` sur l'indice, séparée de
    `photo_reports` (les features ne dépendent pas l'une de l'autre)
  - Admin : file triée par nombre de signalements dans `/admin/hints`, avec rejeter,
    garder ou corriger le texte
  - Aucun retrait automatique. Un indice rejeté n'est plus tiré ; sous 5 indices, la
    catégorie est masquée pour cette année
- [ ] Enchaînement des 3 personnes du jour + récapitulatif final
- [ ] Page d'accueil publique
- [ ] Vocabulaire de jeu centralisé dans un seul fichier (essai du ton « enquête »,
      critère de validation au premier playtest — voir `docs/decisions/design.md`)
- [ ] Trancher le duotone des photos sur de vraies photos Commons

## Semaine 5 — Compte utilisateur + historique
- [x] Inscription/connexion (frontend) + navigation (en-tête invité/connecté) — PR
      `feature/auth-completion`
- [x] Pseudo public unique et modifiable, mot de passe oublié, page compte (pseudo, mot
      de passe), suppression de compte confirmée par email — PR `feature/auth-completion`
- [x] Politique de mot de passe : 12 caractères minimum, sans composition, refus des mots
      de passe courants et de ceux contenant le pseudo ou l'email — PR
      `feature/password-policy`
- [ ] Page profil (statistiques, historique visible)
- [ ] Historique des parties passées (score, streak quotidien)

## Semaine 6 — Amis
- [ ] Demandes d'ami (envoi, acceptation, refus)
- [ ] Liste d'amis
- [ ] Comparaison des scores du jour entre amis
- [ ] Leaderboard entre amis (historique cumulé)

## Semaine 7 — Polish
- [ ] SEO (meta tags, sitemap, Angular SSR si pertinent pour le référencement)
- [ ] Export des données personnelles (droit d'accès et de portabilité en self-service ;
      suppression déjà livrée en Semaine 5, pages légales en Semaine 1, pas de bannière
      cookies nécessaire tant qu'il n'y a que des traceurs strictement nécessaires)
- [ ] Purge des comptes jamais vérifiés et des comptes inactifs (durées à fixer, puis à
      annoncer dans la politique de confidentialité)
- [ ] Ré-acceptation des CGU à chaque nouvelle version (`users.terms_version` déjà
      enregistré)
- [ ] Double authentification (TOTP), seule protection si la boîte mail d'un joueur est
      compromise (voir `docs/decisions/architecture.md`, « Récupération du compte »)
- [ ] Partage de résultat façon Wordle (grille/emoji, pas de compte requis)

## Semaine 8 — Stabilisation et déploiement
- [ ] Tests d'intégration sur les endpoints critiques
- [ ] Tests E2E sur les parcours principaux (Playwright)
- [ ] Monitoring (uptime, erreurs) + rotation des logs Docker (`json-file` sans
      `max-size` aujourd'hui, conservation illimitée)
- [ ] Backup PostgreSQL (annoncer sa durée de conservation dans `/privacy`)
- [ ] Déploiement prod final et smoke tests
- [ ] Landing page de lancement

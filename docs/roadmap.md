# Roadmap Ageguessr

Source de vérité de l'avancement produit, découpée en semaines indicatives.
À cocher à chaque PR mergée (pas besoin d'attendre la fin d'une semaine pour avancer sur
la suivante).

## Semaine 1 — Fondations
- [x] Initialisation projet Spring Boot (Java 25) + Angular (Claude Code)
- [x] Docker Compose local fonctionnel (PostgreSQL + Redis)
- [x] Authentification complète (inscription, connexion, JWT + refresh token rotatif,
      jeu possible sans compte) — PR #1
- [ ] Charte graphique définie (`docs/decisions/design.md`) — couleurs, typo, formes,
      icônes, ton (actuellement uniquement esquissé, pas de session de design dédiée)
- [ ] Mise en place technique de la charte (tokens Tailwind, polices, toggle thème)
- [ ] Logo/wordmark Ageguessr + favicon
- [x] Mentions légales, CGU et politique de confidentialité (EN + FR, droit français,
      acceptation des CGU versionnée à l'inscription, 13 ans minimum) — PR
      `feature/legal-pages`
- [x] CI/CD GitHub Actions → déploiement automatique sur le VPS (images buildées en CI,
      poussées sur GHCR) — PR #2, #4, release #5 ; en ligne sur https://ageguessr.app
- [x] Structure de base du Caddyfile pour l'app réelle (domaine `ageguessr.app`,
      passerelle Caddy partagée du VPS) — PR #2

## Semaine 2 — Données du jeu
- [ ] Client Wikidata (recherche par notoriété/sitelinks, extraction date de naissance
      `P569`, photo `P18`)
- [ ] Stratégie "photo la plus récente possible" (heuristique Commons à valider sur un
      échantillon réel avant généralisation — voir `docs/decisions/architecture.md`)
- [ ] Endpoint proxy d'image anti-triche (`/api/images/{id}`, jamais l'URL Commons brute)
- [ ] Ingestion + curation des indices événementiels par catégorie et par année
      (pages Wikipedia "YYYY in music/sports/politics...", parsing + vérification sur
      échantillon avant d'industrialiser)
- [ ] Schéma de données final : personnes, catégories d'indices, événements par année

## Semaine 3 — Moteur de jeu
- [ ] Algorithme de sélection quotidienne (3 personnes/jour, minuit UTC, pas de
      répétition trop proche dans le temps)
- [ ] Logique de guess en 2 étapes : 1ère tentative → chaud/froid, sélection d'une
      catégorie d'indice, 2e tentative
- [ ] Système de points (bonus 1er coup, majorité des points sur la précision du 2e coup
      — formule à finaliser par playtest)
- [ ] Cache Redis des parties du jour déjà calculées

## Semaine 4 — Interface de jeu
- [ ] Écran principal : photo, input d'âge, jauge chaud/froid
- [ ] Sélection de catégorie d'indice + affichage de l'indice
- [ ] Écran de résultat (âge réel, identité révélée, points gagnés)
- [ ] Enchaînement des 3 personnes du jour + récapitulatif final
- [ ] Page d'accueil publique

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

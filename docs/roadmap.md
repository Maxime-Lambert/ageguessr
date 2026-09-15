# Roadmap Ageguessr

Source de vérité de l'avancement produit, découpée en semaines indicatives.
À cocher à chaque PR mergée (pas besoin d'attendre la fin d'une semaine pour avancer sur
la suivante).

## Semaine 1 — Fondations
- [x] Initialisation projet Spring Boot (Java 25) + Angular (Claude Code)
- [x] Docker Compose local fonctionnel (PostgreSQL + Redis)
- [ ] Authentification complète (inscription, connexion, JWT + refresh token rotatif,
      jeu possible sans compte)
- [ ] Charte graphique définie (`docs/decisions/design.md`) — couleurs, typo, formes,
      icônes, ton (actuellement uniquement esquissé, pas de session de design dédiée)
- [ ] Mise en place technique de la charte (tokens Tailwind, polices, toggle thème)
- [ ] Logo/wordmark Ageguessr + favicon
- [ ] Mentions légales + CGU minimales (audience internationale — juridiction à
      clarifier, avant toute ouverture publique même en beta)
- [ ] CI/CD GitHub Actions → déploiement automatique sur le VPS
- [ ] Structure de base du Caddyfile pour l'app réelle (domaine `ageguessr.app`)

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
- [ ] Inscription/connexion (frontend)
- [ ] Page profil
- [ ] Historique des parties passées (score, streak quotidien)

## Semaine 6 — Amis
- [ ] Demandes d'ami (envoi, acceptation, refus)
- [ ] Liste d'amis
- [ ] Comparaison des scores du jour entre amis
- [ ] Leaderboard entre amis (historique cumulé)

## Semaine 7 — Polish
- [ ] SEO (meta tags, sitemap, Angular SSR si pertinent pour le référencement)
- [ ] RGPD / conformité internationale (bannière cookies si besoin, suppression de
      compte — mentions légales/CGU déjà livrées en Semaine 1)
- [ ] Partage de résultat façon Wordle (grille/emoji, pas de compte requis)

## Semaine 8 — Stabilisation et déploiement
- [ ] Tests d'intégration sur les endpoints critiques
- [ ] Tests E2E sur les parcours principaux (Playwright)
- [ ] Monitoring (uptime, erreurs)
- [ ] Backup PostgreSQL
- [ ] Déploiement prod final et smoke tests
- [ ] Landing page de lancement

# Accès DBeaver à la base de prod, et copie hors VPS écartée

Permettre à l'utilisateur de consulter la base de prod depuis DBeaver en local, sans
l'exposer à Internet et sans risque d'écriture accidentelle. Écarter la copie automatique
des backups hors du VPS (coût, audience) et documenter une copie manuelle gratuite.

Branche : `chore/prod-db-access` (depuis `develop`).

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions
- **Tunnel SSH, jamais de port public** : Postgres est publié sur `127.0.0.1:15432` du VPS
  seulement. Une adresse de loopback n'est pas joignable depuis l'extérieur, quel que soit
  le pare-feu (Docker contourne ufw pour les ports publiés, mais pas pour une liaison
  `127.0.0.1`). DBeaver passe par son tunnel SSH (port 2222, clé de l'utilisateur). Le
  port 15432 plutôt que 5432 : le VPS est partagé avec un autre projet.
- **Rôle `ageguessr_readonly`** (migration `V29`) : `LOGIN` sans mot de passe (donc aucune
  connexion réseau possible tant que l'utilisateur ne l'a pas défini à la main sur le VPS),
  `SELECT` seulement, sur les tables actuelles et futures (`ALTER DEFAULT PRIVILEGES`).
  Aucun mot de passe dans le dépôt. La garantie est côté base, pas côté DBeaver, et le mot
  de passe du superutilisateur `ageguessr` ne quitte pas le VPS.
- **Copie hors VPS écartée** : OVH Object Storage est payant, même pour quelques
  centimes, et l'audience est petite. Le filet de sécurité est une copie manuelle sur le PC
  de l'utilisateur. Elle respecte les 30 jours de `/privacy` en ne gardant que la dernière
  copie complète. Une exportation du contenu seul (personnes, photos, indices) peut être
  gardée sans limite : aucune donnée de joueur, `reviewed_by` n'est que l'UUID de l'admin.

## Phase 1 : config, migration, doc
Status: Complete

- [x] `docker-compose.prod.yml` : `ports: ["127.0.0.1:15432:5432"]` sur `postgres`
- [x] `V29__create_readonly_role.sql` : rôle idempotent (`DO … IF NOT EXISTS`), `CONNECT`
      sur la base courante, `USAGE` et `SELECT` sur le schéma courant, droits par défaut
- [x] Test d'intégration `integration/database/ReadonlyRoleIntegrationTest` : `SET ROLE` →
      `SELECT` passe, `INSERT`, `UPDATE`, `DELETE` et `DROP` refusés, y compris sur une
      table créée après la migration
- [x] `docs/decisions/architecture.md` : accès DBeaver (réglages, mot de passe à définir
      à la main), copie manuelle (complète et contenu seul), copie automatique écartée
- [x] `docs/roadmap.md` : copie hors VPS barrée et écartée ; vérification manuelle de
      l'accès DBeaver

### Verification Plan
- `cd ageguessr-api && ./mvnw test` → vert
- `docker compose -f docker-compose.prod.yml config` (`.env` factice) → valide

### Phase Summary
Fait. Revues : architecture, un point mineur (test déplacé dans `integration/database/`).
Sécurité, rien de bloquant ; appliqué : les colonnes `*_hash` sont exclues du rôle (droits
par colonne, calculés dans `V29`), `FOR ROLE` explicite, test `pg_authid`, disque chiffré
exigé pour la copie manuelle et DBeaver, copie manuelle annoncée dans `/privacy` (EN et FR),
copie de contenu limitée elle aussi à la dernière. `./mvnw test` et `pnpm test` verts ;
`docker compose config` montre `host_ip: 127.0.0.1`, `published: 15432`.

## Tests : catégories
- **Intégration** : droits réels du rôle dans PostgreSQL. C'est la seule garantie qui
  compte.
- **Pas d'unitaire, de fonctionnel, d'interface, de QA, d'architecture ni de mutation** :
  aucun code Java ni écran nouveau, seulement une migration SQL et de la config.

## Edge cases
- Le port 15432 est déjà pris sur le VPS : `up -d` échouerait et le déploiement avec. À
  vérifier par l'utilisateur **avant** la release (`ss -ltn | grep 15432` → rien).
- Le conteneur `postgres` est recréé au déploiement (nouveau port) : quelques secondes de
  coupure de la base, le temps que le pool du backend se reconnecte.
- Restauration d'un dump sur une machine neuve : le rôle est global au serveur, donc absent
  du dump. `pg_restore` signale les `GRANT` en erreur sans s'arrêter, puis on rejoue le SQL
  de `V29` à la main.

## Final Recap
- Postgres de prod publié sur `127.0.0.1:15432` du VPS seulement, à joindre par un tunnel
  SSH depuis DBeaver.
- Rôle `ageguessr_readonly` (`V29`) : lecture seule, sans les hachages, sans mot de passe
  tant que l'admin ne l'a pas défini.
- Copie automatique hors VPS écartée ; copie manuelle (complète ou contenu seul) documentée
  et annoncée dans `/privacy`.

## Deployment Plan
1. **Avant la release** : sur le VPS, `ss -ltn | grep 15432` ne doit rien afficher, sinon
   `up -d` échoue et le site tombe.
2. Release `develop` → `main` (merge commit) : `V29` s'applique au démarrage du backend.
   Le conteneur `postgres` est recréé à cause du nouveau port, ce qui coupe la base
   quelques secondes.
3. `/api/health` → 200.
4. L'admin définit le mot de passe du rôle et configure DBeaver (procédure dans
   `docs/decisions/architecture.md`, « Accès à la base de prod »).

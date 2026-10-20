# Déploiement production — Ageguessr

Mettre en ligne Ageguessr sur le VPS déjà utilisé par un autre projet personnel
(partage d'infra uniquement, voir `docs/decisions/architecture.md` section
"Hébergement"). Ce plan touche **trois endroits différents** : le repo `ageguessr`
(ce repo), le repo de l'autre projet déjà en prod sur ce VPS, et le VPS lui-même
(fichiers hors de tout repo Git, dans `/opt/gateway/`).

## Contexte pour repartir à froid

Le VPS (`51.83.68.37`, SSH port `2222`, user `ubuntu`, clé `~/.ssh/id_ed25519`,
config déjà dans `~/.ssh/config` sous le host `51.83.68.37`) héberge déjà un autre
projet en prod (`/opt/watodoo/`, conteneurs `watodoo-prod-*`) dont le conteneur Caddy
(`watodoo-prod-edge-1`) **occupe actuellement les ports 80/443 de la machine**. Un seul
processus peut écouter sur ces ports — Ageguessr ne peut donc pas déployer son propre
Caddy exposé publiquement comme cet autre projet l'a fait.

**Solution retenue** : une passerelle Caddy unique au niveau du VPS (`/opt/gateway/`,
pas dans un repo Git — c'est de l'infra partagée, pas le code d'un projet), seule à
publier 80/443, qui route par nom de domaine vers le conteneur `edge` de chaque projet
via un réseau Docker externe partagé nommé `gateway`. Chaque projet garde son propre
conteneur edge (frontend statique + reverse-proxy `/api/*`), mais celui-ci ne termine
plus le TLS lui-même et n'est plus jamais publié sur un port hôte.

## État exact au moment de la pause (vérifié en direct, pas supposé)

- **Repo `ageguessr`** : branche `feature/deployment-setup` poussée, PR
  [#2](https://github.com/Maxime-Lambert/ageguessr/pull/2) ouverte et verte
  (CI backend+frontend passent), **pas encore mergée**. Contient
  `ageguessr-api/Dockerfile`, `Dockerfile.edge`, `Caddyfile`,
  `docker-compose.prod.yml`, `.dockerignore`, `/api/health`, et le fix du job
  `deploy` du CI (noms de variables). Tout a été validé par un build + un run
  complet en local (voir description de la PR pour le détail), y compris un bug
  réel trouvé et corrigé (le `Caddyfile` retirait `/api` avant de proxyfier, alors
  que chaque contrôleur Spring inclut déjà `/api/...` dans son mapping).
- **VPS** :
  - Réseau Docker externe `gateway` : **créé** (`docker network create gateway`).
  - `/opt/gateway/Dockerfile`, `/opt/gateway/Caddyfile` (un seul bloc `watodoo.app`
    pour l'instant), `/opt/gateway/docker-compose.yml` : **écrits**. Le
    `docker-compose.yml` référence en `external: true` les volumes
    `watodoo-prod_caddy_data`/`watodoo-prod_caddy_config` déjà existants (pour
    réutiliser le certificat Let's Encrypt déjà émis pour `watodoo.app` plutôt que
    d'en redemander un).
  - `/opt/gateway/.env` (doit contenir `CLOUDFLARE_API_TOKEN=...`) : **PAS encore
    créé** — l'utilisateur devait le créer lui-même en SSH (un token Cloudflare
    fraîchement créé, scopé `Zone:DNS:Edit`, pas celui de l'autre projet — voir "Pourquoi"
    ci-dessous). **Vérifier avec `ls -la /opt/gateway/.env` avant de continuer.**
  - Image Docker de la passerelle : **pas encore buildée** (bloqué sur le `.env`
    manquant).
  - L'autre projet (`/opt/watodoo/`) : **pas touché du tout**, toujours en train de
    publier 80/443 directement, toujours en ligne normalement.
  - `ageguessr.app` : DNS **pas encore pointé vers Cloudflare** (action côté
    registrar, à faire par l'utilisateur, indépendante du reste).

**Pourquoi un nouveau token plutôt que réutiliser celui de l'autre projet** : celui-ci
est probablement scopé uniquement sur sa propre zone DNS — inutilisable pour
`ageguessr.app` une fois cette zone ajoutée à Cloudflare. Range aussi les deux projets
sur des identifiants distincts plutôt que de faire fuiter un secret d'un projet vers
l'autre.

## For Future Agents

Cocher au fur et à mesure. **Avant toute action sur le VPS touchant l'autre projet
(Phase 2), relire "État exact" ci-dessus avec des commandes réelles (`docker ps`,
`ls`), ne jamais supposer que l'état décrit ici est encore exact si du temps a
passé.** Chaque commande de coupure/bascule sur le VPS doit être confirmée
explicitement avec l'utilisateur avant exécution — c'est une prod partagée avec un
autre projet, pas seulement celle d'Ageguessr.

## Phase 1 : Passerelle (additive, sans risque pour l'autre projet)
Status: Completed

- [x] Créer le réseau Docker externe `gateway` sur le VPS
- [x] Écrire `/opt/gateway/{Dockerfile,Caddyfile,docker-compose.yml}` (Caddyfile déplacé ensuite dans
      `/opt/gateway/conf/` et monté, voir Phase 3)
- [x] Vérifier que l'utilisateur a bien créé `/opt/gateway/.env` avec
      `CLOUDFLARE_API_TOKEN` (nouveau token, pas celui de l'autre projet)
- [x] Builder l'image de la passerelle (`cd /opt/gateway && docker compose build`) —
      ne PAS faire `up` tout de suite, le port 80/443 est encore tenu par l'autre
      projet
- [x] Vérifier que le build inclut bien le plugin `github.com/caddy-dns/cloudflare`
      (`docker compose run --rm caddy caddy list-modules | grep cloudflare`)

### Verification Plan
- `docker images | grep gateway` montre une image construite
- La commande `list-modules` ci-dessus liste bien le module cloudflare

### Phase Summary
`/opt/gateway/.env` créé par l'utilisateur avec un token Cloudflare neuf. Image
buildée avec succès, `caddy list-modules` confirme la présence de
`dns.providers.cloudflare`. La passerelle est prête mais pas démarrée (pas de `up`,
conformément au plan — le port 80/443 est encore tenu par `watodoo-prod-edge-1`).

---

## Phase 2 : Bascule de l'autre projet vers le réseau partagé (touche une prod live — demander confirmation explicite juste avant)
Status: Completed (Watodoo#24 mergée dans `develop` ; `develop` → `main` de watodoo non vérifié)

**État exact (vérifié en direct à la pause du 2026-10-14)** : branche
`chore/caddy-passerelle-partagee` créée depuis `develop` dans
`/home/lord/source/watodoo`, commit `7230030` poussé sur
`origin/chore/caddy-passerelle-partagee` (**pas de PR ouverte** — volontairement
incomplet, voir ci-dessous). CI du repo `watodoo` verte sur ce push (tests
backend + frontend). `watodoo` tourne toujours normalement en prod, rien n'a
encore été touché côté VPS pour cette phase.

- [x] Dans le repo de l'autre projet (`/home/lord/source/watodoo`, PAS ce repo) —
      **partiellement fait**, détail ci-dessous :
  - [x] `docker-compose.prod.yml` : retiré `ports: ["80:80", "443:443"]` et
        `CLOUDFLARE_API_TOKEN` du service `edge` (+ volumes `caddy_data`/
        `caddy_config`, plus utilisés par ce conteneur), attaché au réseau
        externe `gateway`
  - [x] `Dockerfile.edge` : retiré le build Caddy custom (`xcaddy` + plugin
        `caddy-dns/cloudflare`), repassé sur `caddy:2-alpine` standard — plus
        nécessaire puisque ce conteneur ne gère plus lui-même le certificat
        Let's Encrypt (DNS-01 désormais géré une seule fois par la passerelle)
  - [x] `docs/decisions/architecture.md` : section "Hébergement" mise à jour
        (nouvelle sous-section "Passerelle Caddy partagée du VPS" expliquant
        le changement et pourquoi, section Cloudflare mise à jour pour dire
        que c'est maintenant la passerelle qui détient le token)
  - [x] `Caddyfile` : bloc `tls { dns cloudflare ... }` retiré, adresse
        `{$DOMAIN}` remplacée par `:80` (fait le 2026-10-15 avec accord explicite
        de l'utilisateur, commit `657da14`). `caddy validate` : OK.
- [x] Committer ce dernier changement (`Caddyfile`) — commit `657da14`
- [x] Pousser `657da14` (hook pre-push : 40/40 tests OK)
- [x] Ouvrir la PR vers `develop` :
      [Watodoo#24](https://github.com/Maxime-Lambert/Watodoo/pull/24). Merger dans
      `develop` est sans risque (le job `deploy` de watodoo ne tourne que sur push
      vers `main`) ; c'est la PR `develop` → `main` qui ne doit partir qu'au moment
      de la bascule ci-dessous.
- **Attention (constaté le 2026-10-15 dans `.github/workflows/ci.yml` de watodoo)** :
  le job `deploy` (push sur `main`) fait `rsync -az --delete` du repo vers
  `/opt/watodoo/` puis `docker compose -f docker-compose.prod.yml up -d --build`
  → **merger `develop` → `main` déclenche à lui seul la bascule de l'edge**. Ordre
  retenu : bascule manuelle complète (passerelle comprise) d'abord, merge vers `main`
  ensuite (redéploiement alors sans effet visible). Le CI écrit encore
  `CLOUDFLARE_API_TOKEN` dans le `.env` de watodoo : inutilisé, à nettoyer plus tard.
- Les accès SSH au VPS sont refusés à Claude par le mode auto (« Production
  Reads ») : les étapes VPS sont exécutées par l'utilisateur, guidé pas à pas.
- [x] Sur le VPS : synchroniser ces fichiers dans `/opt/watodoo/` — fait le
      2026-10-19 à la main : sauvegarde des anciennes versions dans
      `~/watodoo-backup-2026-10-19/`, upload dans `~/watodoo-staging/` puis
      `sudo cp` (fichiers de `/opt/watodoo/` non modifiables par `ubuntu` ; `cp`
      préserve propriétaire/droits des fichiers écrasés)
- [x] Pré-builder la nouvelle image edge de l'autre projet SANS encore relancer le
      conteneur (`docker compose -f docker-compose.prod.yml build edge`) — fait le 2026-10-19,
      **avec `sudo`** : `/opt/watodoo/.env` n'est pas lisible par `ubuntu`, toute
      commande `docker compose` dans `/opt/watodoo` doit passer par `sudo`
- [x] **Confirmé par l'utilisateur** : recréation de l'edge watodoo
      (`sudo docker compose -f docker-compose.prod.yml up -d --no-deps edge`) puis
      démarrage immédiat de la passerelle (`cd /opt/gateway && sudo docker compose
      up -d`), enchaînés dans une seule commande le 2026-10-19 (~14:16 UTC)
- [x] `curl -sI https://watodoo.app/` → `HTTP/2 200`, certificat valide (réutilisé
      depuis les volumes `watodoo-prod_caddy_*`)
- [x] Merger [Watodoo#24](https://github.com/Maxime-Lambert/Watodoo/pull/24) dans
      `develop`, puis `develop` → `main` (le redéploiement CI est alors sans effet
      visible : fichiers identiques à ceux déjà en place)
- **Constaté pendant la bascule** : `docker compose` a averti que
  `INGESTION_ADMIN_KEY`, `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` sont absents du
  `/opt/watodoo/.env` actuel (sans impact ici : `--no-deps`, backend non recréé).
  Le CI de watodoo réécrit ce `.env` avec ces clés à chaque déploiement → vérifier
  que les secrets GitHub correspondants existent avant le merge vers `main`.
  Ne jamais recréer le backend watodoo à la main avec ce `.env`.
  Vérifié le 2026-10-19 : les secrets existent bien (environnement GitHub
  `production` de watodoo). Ils manquent seulement parce que `develop` de watodoo
  contient 5 PR pas encore en prod (#19 à #23, dont l'ingestion IGDB et le
  verrouillage de compte) → **merger `develop` → `main` sur watodoo déploie aussi
  ces features : décision produit de l'utilisateur, pas une simple formalité**.
- Le merge de Watodoo#24 par Claude est refusé par le mode auto (« Production
  Deploy ») → c'est l'utilisateur qui merge.

### Verification Plan
- Le site de l'autre projet répond en HTTPS sans erreur de certificat après la
  bascule
- Le conteneur `watodoo-prod-edge-1` n'a plus de port publié dans `docker ps`
- Le conteneur de la passerelle a bien les ports 80/443

### Phase Summary
Bascule faite le 2026-10-19 sans incident, coupure de quelques secondes. `docker ps` :
`gateway-caddy-1` publie 80/443, `watodoo-prod-edge-1` n'a plus aucun port publié.
watodoo.app répond en HTTPS 200. Reste le merge de Watodoo#24 jusqu'à `main` pour
que le repo watodoo reflète l'état réel du VPS.

---

## Phase 3 : Déploiement d'Ageguessr
Status: Completed

- [ ] Pointer le DNS de `ageguessr.app` vers Cloudflare (action utilisateur, côté
      registrar — indépendante des phases précédentes, peut être faite en parallèle)
- [x] Ajouter le bloc `ageguessr.app` dans `/opt/gateway/Caddyfile`, rebuild +
      redémarrer la passerelle (un seul bloc supplémentaire, n'affecte pas le bloc
      de l'autre projet)
  - Prérequis : la zone `ageguessr.app` est **active** dans Cloudflare, et le token
    Cloudflare de la passerelle couvre aussi cette zone (il a été créé quand seule
    `watodoo.app` y était → probablement à étendre). Sinon le challenge DNS-01
    échoue, et Let's Encrypt limite les échecs de validation.
  - Bloc prévu (même forme que celui de watodoo, à aligner sur le fichier réel) :
    `ageguessr.app { tls { dns cloudflare {env.CLOUDFLARE_API_TOKEN} }
    reverse_proxy ageguessr-prod-edge-1:80 }` + `www.ageguessr.app` en redirection
    permanente vers `https://ageguessr.app{uri}`. Le nom du conteneur vient de
    `name: ageguessr-prod` dans `docker-compose.prod.yml` → `ageguessr-prod-edge-1`.
    Tant que le conteneur n'existe pas, Caddy démarre quand même et répond 502.
  - Constaté le 2026-10-19 : le Caddyfile de la passerelle était copié dans l'image
    (`COPY Caddyfile`), donc chaque modif = rebuild + recréation = coupure watodoo.
    Changement préparé en même temps que l'ajout du bloc : Caddyfile déplacé dans
    `/opt/gateway/conf/` et monté en lecture seule (`./conf:/etc/caddy:ro`),
    `COPY` retiré du Dockerfile → à l'avenir, `sudo docker compose exec caddy caddy
    reload --config /etc/caddy/Caddyfile` sans coupure. Sauvegarde de l'ancien
    `/opt/gateway` dans `~/gateway-backup-2026-10-19/`.
  - Fait le 2026-10-19 (~16:33 UTC) : fichiers en place, passerelle recréée, watodoo
    toujours en 200. Premier essai ACME en échec (propagation DNS trop lente pour
    Let's Encrypt), réussi au retry automatique de Caddy : certificats Let's Encrypt
    `ageguessr.app` et `www.ageguessr.app` obtenus (vérifiés en direct contre le VPS
    avec `curl --resolve ageguessr.app:443:51.83.68.37` : apex → 502 attendu, www →
    301 vers l'apex).
  - DNS Cloudflare : la zone a importé les enregistrements OVH (A `213.186.33.5` =
    parking OVH sur `@` et `www`, CNAME `ftp`, TXT `1|www…` et `3|welcome`).
    Correction demandée à l'utilisateur : A `@` et `www` → `51.83.68.37` (Proxied),
    suppression `ftp` + 2 TXT OVH, ajout `_dmarc` `v=DMARC1; p=none;`. MX/SPF OVH
    conservés volontairement (réception mail `@ageguessr.app`).
- [ ] Créer un compte Resend, vérifier le domaine d'envoi, récupérer une clé API
      (action utilisateur)
- [x] DNS Cloudflare corrigé par l'utilisateur (vérifié le 2026-10-19 : apex → 502
      via Cloudflare, www → 301, restes OVH supprimés, `_dmarc` présent)
- [ ] Créer une clé SSH de déploiement dédiée à Ageguessr (ne pas réutiliser celle
      de l'autre projet), l'ajouter aux `authorized_keys` du VPS
  - Décision 2026-10-19 : utilisateur dédié `ageguessr-deploy` (groupe `docker`, pas
    de sudo, propriétaire de `/opt/ageguessr`) plutôt que `ubuntu` (qui a sudo).
    Limite assumée : le groupe `docker` équivaut à root sur la machine, donc c'est
    surtout une séparation des clés et des fichiers, pas une vraie isolation.
  - `JWT_SECRET` doit être du Base64 (`JwtConfig` le décode) → généré avec
    `openssl rand -base64 32` et passé via `--body "$(...)"` (pas de retour à la
    ligne final, qui ferait échouer le décodage Base64 de Java).
- [x] Configurer les secrets/variables GitHub Actions du repo `ageguessr` (vérifié le
      2026-10-20 : tous présents dans l'environnement GitHub `production`) :
      `VPS_HOST` (`51.83.68.37`), `VPS_USER` (`ageguessr-deploy`), `VPS_SSH_KEY` (la nouvelle
      clé dédiée), `POSTGRES_PASSWORD` (générer), `JWT_SECRET` (générer via
      `openssl rand -base64 32`), `RESEND_API_KEY`, variable `DOMAIN`
      (`ageguessr.app`)
- [x] Relecture de la PR #2 par `code-reviewer` + `architecture-reviewer` (2026-10-19),
      corrections appliquées sur la branche :
  - **IP client** (signalé comme « spoofing X-Forwarded-For » ; le mécanisme décrit
    était faux : chaque Caddy écrase XFF par défaut. Vrai problème : Spring voyait
    l'IP de la passerelle pour tout le monde → un seul compteur de rate limit
    partagé par tous les utilisateurs). Corrigé : passerelle `trusted_proxies` =
    plages Cloudflare + `client_ip_headers CF-Connecting-IP` + `header_up
    X-Forwarded-For {client_ip}` ; edge `trusted_proxies private_ranges` + même
    `header_up`. Testé en local de bout en bout (XFF forgé ignoré, vraie IP
    transmise) + test fonctionnel `partitionsByForwardedClientIp`. Détail dans
    `docs/decisions/architecture.md`.
  - Backend en utilisateur non-root (`app`, uid 10001) — image buildée et vérifiée.
  - En-têtes de sécurité sur les fichiers statiques (HSTS, nosniff, X-Frame-Options,
    Referrer-Policy, Permissions-Policy).
  - `HealthController` déplacé dans `config/` + `HealthFunctionalTest`.
  - Refusé : monter Postgres sur `/var/lib/postgresql/data` (faux pour Postgres 18,
    PGDATA = `/var/lib/postgresql/18/docker`, VOLUME = `/var/lib/postgresql` ;
    commentaire ajouté dans `docker-compose.prod.yml`).
  - **Reporté (à faire plus tard)** : mot de passe Redis (Redis isolé sur le réseau
    interne du projet), limites mémoire/CPU + `no-new-privileges` sur les conteneurs
    (VPS partagé), Content-Security-Policy (à régler avec Angular), n'accepter que
    Cloudflare sur 80/443 au niveau pare-feu, et côté watodoo : son edge ne fait pas
    confiance à la passerelle → son backend voit l'IP de la passerelle pour tous
    (même problème que ci-dessus, à corriger dans le repo watodoo).
- [x] Appliquer le nouveau Caddyfile de la passerelle (IP client) sur le VPS :
      `conf/Caddyfile` + `caddy reload`, sans coupure — fait par l'utilisateur le
      2026-10-19 (ancienne version : `~/gateway-backup-2026-10-19/Caddyfile.avant-ip-client`)
- [x] PR [#2](https://github.com/Maxime-Lambert/ageguessr/pull/2) mergée sur `develop`
      (2026-10-19)
- [x] Merger la PR de release [#3](https://github.com/Maxime-Lambert/ageguessr/pull/3)
      `develop` → `main` (fait par l'utilisateur le 2026-10-20)
- **Premier déploiement en échec (run 39391331362, 2026-10-20)** : `ng build` tué sans
  message pendant `docker compose up --build` sur le VPS. Reproduit en local : le build
  échoue à 2 Go de RAM et passe à 4 Go. Cause : `@lucide/angular` = un seul fichier de
  12 Mo que le linker Angular doit traiter entièrement. Rien n'a été démarré (échec au
  build) : ageguessr.app toujours en 502, watodoo.app toujours en 200.
- [x] Correctif choisi par l'utilisateur : builder les images en CI et les pousser sur
      GHCR, le VPS ne fait plus que `pull` (on garde Lucide). Branche
      `fix/build-images-in-ci` (contient aussi le commit de suivi du plan de
      `chore/deployment-plan-progress`). Détail dans `docs/decisions/architecture.md`.
- [x] PR [#4](https://github.com/Maxime-Lambert/ageguessr/pull/4) → `develop`, puis
      release [#5](https://github.com/Maxime-Lambert/ageguessr/pull/5) → `main`
      (2026-10-20). La release #3 ayant été mergée en *rebase*, `main` et `develop`
      avaient divergé (#5 en conflit) : réconcilié par un merge `-s ours` de `main` dans
      `develop` ([#6](https://github.com/Maxime-Lambert/ageguessr/pull/6), aucun fichier
      modifié). **Les releases `develop` → `main` se mergent toujours en « Create a merge
      commit ».**
  - `/opt/ageguessr/` contient encore une copie complète du repo laissée par le rsync du
    déploiement raté : inutile désormais (seuls `docker-compose.prod.yml` et `.env`
    servent), à nettoyer à la main.
- [x] Smoke test du CI (`/api/health`) vert (run 39401388701) ; vérifié en direct :
      apex 200 + en-têtes de sécurité, www → 301, watodoo.app toujours 200
- Test manuel de l'inscription en prod : reporté au plan suivant (`plans/active-plan.md`,
  finalisation de l'auth) — les pages existaient mais n'étaient reliées à aucune
  navigation.

### Phase Summary
Ageguessr en ligne sur https://ageguessr.app depuis le 2026-10-20 (release #5). Premier
essai en échec (OOM de `ng build` sur le VPS) → images buildées en CI et poussées sur
GHCR, le VPS ne fait plus que `pull`. Reste à faire à la main sur le VPS : supprimer la
copie du repo laissée dans `/opt/ageguessr/` (garder `.env` et
`docker-compose.prod.yml`), puis à terme les sauvegardes `~/*-backup-2026-10-19/`.

## Final Recap
Passerelle Caddy partagée sur le VPS (seule à publier 80/443, TLS DNS-01 Cloudflare,
vraie IP client transmise), watodoo basculé derrière elle sans incident, Ageguessr
déployé automatiquement à chaque push sur `main` (tests → images GHCR → pull sur le VPS
→ smoke test). Reportés : mot de passe Redis, limites mémoire/CPU des conteneurs, CSP,
pare-feu limité à Cloudflare, IP client côté watodoo (voir Phase 3), et le
`develop` → `main` de watodoo (décision produit de l'utilisateur).

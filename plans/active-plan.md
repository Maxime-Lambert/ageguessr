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
Status: In progress

- [x] Créer le réseau Docker externe `gateway` sur le VPS
- [x] Écrire `/opt/gateway/{Dockerfile,Caddyfile,docker-compose.yml}`
- [ ] Vérifier que l'utilisateur a bien créé `/opt/gateway/.env` avec
      `CLOUDFLARE_API_TOKEN` (nouveau token, pas celui de l'autre projet)
- [ ] Builder l'image de la passerelle (`cd /opt/gateway && docker compose build`) —
      ne PAS faire `up` tout de suite, le port 80/443 est encore tenu par l'autre
      projet
- [ ] Vérifier que le build inclut bien le plugin `github.com/caddy-dns/cloudflare`
      (`docker compose run --rm caddy caddy list-modules | grep cloudflare`)

### Verification Plan
- `docker images | grep gateway` montre une image construite
- La commande `list-modules` ci-dessus liste bien le module cloudflare

### Phase Summary
_(à écrire une fois la phase terminée)_

---

## Phase 2 : Bascule de l'autre projet vers le réseau partagé (touche une prod live — demander confirmation explicite juste avant)
Status: Not started

- [ ] Dans le repo de l'autre projet (`/home/lord/source/watodoo`, PAS ce repo) :
      modifier `Caddyfile` pour retirer le bloc `tls { dns cloudflare ... }` et
      changer l'adresse du site de `{$DOMAIN}` à `:80` (sert du HTTP simple en
      interne, la passerelle termine le TLS) ; modifier `docker-compose.prod.yml`
      pour retirer `ports: ["80:80", "443:443"]` et `CLOUDFLARE_API_TOKEN` du
      service `edge`, et l'attacher en plus au réseau externe `gateway`
- [ ] Committer et pousser ce changement dans le repo de l'autre projet (créer une
      PR comme d'habitude pour ce repo, pas de push direct sur `main`/`develop`)
- [ ] Sur le VPS : synchroniser ces fichiers dans `/opt/watodoo/` (le déploiement
      normal de ce projet le fait déjà via son propre CI — ou copier manuellement
      pour tester avant de merger)
- [ ] Pré-builder la nouvelle image edge de l'autre projet SANS encore relancer le
      conteneur (`docker compose -f docker-compose.prod.yml build edge`)
- [ ] **Confirmer avec l'utilisateur juste avant cette étape** : recréer le
      conteneur edge de l'autre projet (`docker compose -f docker-compose.prod.yml
      up -d edge`) — il n'écoute plus alors sur 80/443, le site de l'autre projet
      sera injoignable jusqu'à la fin de cette phase
- [ ] Démarrer la passerelle (`cd /opt/gateway && docker compose up -d`) — elle
      reprend 80/443 et route vers `watodoo-prod-edge-1:80` en interne
- [ ] Vérifier immédiatement que le site de l'autre projet répond toujours en HTTPS
      normalement (`curl -sI https://<son-domaine>/`)

### Verification Plan
- Le site de l'autre projet répond en HTTPS sans erreur de certificat après la
  bascule
- Le conteneur `watodoo-prod-edge-1` n'a plus de port publié dans `docker ps`
- Le conteneur de la passerelle a bien les ports 80/443

### Phase Summary
_(à écrire une fois la phase terminée)_

---

## Phase 3 : Déploiement d'Ageguessr
Status: Not started

- [ ] Pointer le DNS de `ageguessr.app` vers Cloudflare (action utilisateur, côté
      registrar — indépendante des phases précédentes, peut être faite en parallèle)
- [ ] Ajouter le bloc `ageguessr.app` dans `/opt/gateway/Caddyfile`, rebuild +
      redémarrer la passerelle (un seul bloc supplémentaire, n'affecte pas le bloc
      de l'autre projet)
- [ ] Créer un compte Resend, vérifier le domaine d'envoi, récupérer une clé API
      (action utilisateur)
- [ ] Créer une clé SSH de déploiement dédiée à Ageguessr (ne pas réutiliser celle
      de l'autre projet), l'ajouter aux `authorized_keys` du VPS
- [ ] Configurer les secrets/variables GitHub Actions du repo `ageguessr` :
      `VPS_HOST` (`51.83.68.37`), `VPS_USER` (`ubuntu`), `VPS_SSH_KEY` (la nouvelle
      clé dédiée), `POSTGRES_PASSWORD` (générer), `JWT_SECRET` (générer via
      `openssl rand -base64 32`), `RESEND_API_KEY`, variable `DOMAIN`
      (`ageguessr.app`)
- [ ] Merger la PR [#2](https://github.com/Maxime-Lambert/ageguessr/pull/2) sur
      `develop`, puis PR `develop` → `main` pour déclencher le premier déploiement
      réel (le job `deploy` du CI ne tourne que sur push vers `main`)
- [ ] Vérifier le smoke test du CI (`/api/health`) et tester manuellement
      l'inscription complète en prod

### Phase Summary
_(à écrire une fois la phase terminée)_

## Final Recap
_(à écrire une fois toutes les phases terminées)_

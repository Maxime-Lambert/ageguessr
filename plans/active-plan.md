# Pages légales : mentions légales, CGU, politique de confidentialité

Publier les trois pages légales obligatoires avant toute ouverture publique. Elles
existent en anglais et en français, et le pied de page y donne accès depuis tout le
site. À l'inscription, on fait accepter les CGU, on fait confirmer l'âge minimum de
13 ans, et on enregistre la version des CGU acceptée et la date d'acceptation.

## Contexte pour repartir à froid
- Branche : `feature/legal-pages` (depuis `develop`, à jour de la PR #12). La PR #13
  (`chore/design-charter`) est ouverte en parallèle ; les deux seront mergées dans
  `develop` puis releasées ensemble vers `main`.
- Roadmap : Semaine 1, « Mentions légales + CGU minimales ». La case RGPD de la Semaine 7
  ne gardera que l'export des données.
- Pas de pied de page aujourd'hui : `frontend/src/app/app.html` ne contient que l'en-tête
  et le `router-outlet`.
- Inscription : `features/auth/Register/RegisterCommand.java` (email, username,
  password), formulaire `frontend/src/app/features/auth/register/`.

## Décisions validées avec l'utilisateur (2026-10-21)
- **Éditeur anonyme, statut de particulier non professionnel.** La LCEN (loi n° 2004-575)
  le permet : la page publie seulement un email de contact et les coordonnées de
  l'hébergeur. L'identité de l'éditeur est déclarée à l'hébergeur, qui la transmet à la
  justice sur demande. Aucun nom ni adresse personnelle en ligne.
- **Contact : `contact@ageguessr.app`.** C'est une redirection vers la boîte perso, à
  configurer par l'utilisateur (redirection email OVH ou équivalent) : Resend ne fait
  qu'envoyer. **Prérequis avant la release** : la redirection fonctionne.
- **Trois pages** : mentions légales, CGU, politique de confidentialité. Le RGPD (art. 13)
  impose l'information des personnes dès la collecte, et des emails sont déjà collectés
  en prod.
- **Âge minimum de 13 ans pour créer un compte.** Le jeu sans compte reste ouvert à tous.
  - En dessous de 13 ans, la loi américaine COPPA impose un consentement parental
    vérifiable, impossible à gérer pour nous.
  - Pour un compte, la base légale du traitement est l'exécution du contrat (RGPD
    art. 6.1.b), pas le consentement : l'âge du consentement numérique (15 ans en
    France, RGPD art. 8) ne s'applique donc pas à ce traitement.

## Faits vérifiés (2026-10-21)
- **Hébergeur** : OVH SAS, SAS au capital de 50 000 000 €, RCS Lille Métropole
  424 761 419, 2 rue Kellermann, 59100 Roubaix, France (page « Mentions légales »
  d'ovhcloud.com).
  - Le **téléphone**, exigé par la LCEN pour l'hébergeur, n'est pas confirmé : **à
    vérifier** sur la page de contact OVH avant d'écrire la page.
- **Resend** (envoi des emails) :
  - les données sont stockées aux **États-Unis**, quelle que soit la région d'envoi ;
  - les transferts sont couverts par les clauses contractuelles types de la Commission
    (dans le DPA de Resend) et par la certification EU-US Data Privacy Framework ;
  - les emails et logs sont conservés 30 jours (resend.com/security/gdpr).
- **Cloudflare** (constaté par DNS le 2026-10-21) :
  - les serveurs DNS du domaine sont chez Cloudflare (`*.ns.cloudflare.com`) ;
  - le site passe par le proxy Cloudflare (les enregistrements A pointent sur des IP
    Cloudflare, 188.114.96-97.x) ;
  - Cloudflare voit donc l'IP et les requêtes de chaque visiteur : c'est un
    **sous-traitant à déclarer** dans la page de confidentialité (entreprise
    américaine, conditions de transfert à vérifier comme pour Resend).
- **VPS** : datacenter OVH de Gravelines (GRA), France — confirmé par l'utilisateur.
- **Passerelle Caddy partagée du VPS** (`gateway-caddy-1`, autre dépôt) : n'enregistre
  **aucun log d'accès**. Sur ~18 h, `docker logs` ne montre que la maintenance TLS/ACME,
  aucune entrée `http.log.access` ni IP de visiteur. Le `Caddyfile` de ce dépôt ne
  journalise pas les accès non plus. La page de confidentialité peut donc dire que le
  serveur ne conserve pas les IP des visiteurs, hormis le compteur de rate limiting
  d'une minute. Cloudflare, lui, les voit.
  - Hors périmètre : la rotation des logs Docker n'est pas configurée (`json-file` sans
    `max-size`, conservation illimitée). Il n'y a pas de donnée personnelle dedans
    aujourd'hui, mais ça le deviendrait si un log d'accès était activé un jour. À
    ajouter en Semaine 8 (« Monitoring »).
- **Email du domaine** : les enregistrements MX pointent vers OVH (`mx1/2/3.mail.ovh.net`),
  donc le domaine a une offre email OVH. L'adresse `contact@` se crée côté OVH, sans
  toucher au DNS.
- **Données réellement traitées** (inventaire du code) :
  - compte : email, pseudo, hash bcrypt du mot de passe, statut de vérification de
    l'email, compteur d'échecs de connexion et verrouillage, date de création ;
  - jetons (refresh, vérification d'email, actions sur le compte) : stockés hachés, avec
    une date d'expiration, supprimés en cascade avec le compte ;
  - adresse IP : un compteur Redis par IP pour le rate limiting de `/api/auth/*`, qui
    expire avec sa fenêtre (1 minute) ;
  - navigateur :
    - cookie `ageguessr_refresh_token` (HttpOnly, 90 jours, strictement nécessaire à la
      session) ;
    - `localStorage` `ageguessr-theme` (préférence choisie par l'utilisateur) ;
    - le jeton d'accès reste en mémoire, il n'est pas stocké.
  - Aucun outil de mesure d'audience ni cookie tiers. **Pas de bannière de
    consentement** : les traceurs strictement nécessaires et les préférences demandées
    par l'utilisateur sont exemptés (CNIL, lignes directrices cookies de 2020).
  - Aucun log applicatif ne contient d'email ou d'IP (vérifié par recherche dans
    `ageguessr-api`).

## Hypothèses (à confirmer par l'utilisateur à la validation du plan)
- **Comptes existants** : créés avant l'acceptation des CGU, ils gardent
  `terms_accepted_at` à `NULL`. Ce sont très peu de comptes (tests). On ne force pas de
  ré-acceptation ; le mécanisme viendra avec la première modification des CGU.
- **Textes** : rédigés à partir des obligations connues (LCEN, RGPD art. 13, lignes
  directrices CNIL). Ce sont des bases solides, **pas un conseil juridique**. Une
  relecture par un professionnel reste conseillée avant une ouverture à grande échelle.

## Décisions d'architecture de cette feature
- **Pages statiques côté Angular, sans backend.** Le contenu vit dans les templates :
  - `features/legal/legal-notice/`, route `/legal-notice` ;
  - `features/legal/terms/`, route `/terms` ;
  - `features/legal/privacy/`, route `/privacy`.

  Le texte change rarement, et chaque changement doit passer par une PR relue. Une base
  de données ou un CMS n'apporterait rien.
- **Anglais et français sur la même route** : `?lang=fr` affiche la version française,
  avec un lien de bascule en haut de page. Les deux versions sont dans le même template
  (`@if`), avec un `lang="fr"` sur le bloc français pour les lecteurs d'écran.
  - Pourquoi le français : la loi Toubon impose le français pour les conditions
    proposées à des consommateurs en France.
  - Pourquoi l'anglais : la langue du site.
  - Les CGU précisent qu'en cas de divergence, la version française prévaut. Le droit
    applicable est le droit français.
- **Droit applicable et juridiction** :
  - droit français, tribunaux français ;
  - sans priver un consommateur de l'UE des protections impératives de son pays de
    résidence (règlement Rome I, art. 6), clause indispensable face à une audience
    internationale ;
  - mention de la plateforme européenne de règlement en ligne des litiges et de la
    médiation de la consommation : **à vérifier pendant la rédaction** si elle reste
    exigée pour un service gratuit d'un particulier. On l'inclut si le doute persiste.
- **Pied de page** : `features/layout/footer/`, sur toutes les pages, avec les liens
  « Legal notice · Terms · Privacy » et `contact@ageguessr.app`. Mobile-first, tokens de
  la charte (`text-muted`, `border-border`).
- **Acceptation des CGU à l'inscription** :
  - une case à cocher obligatoire, non pré-cochée : « I'm at least 13 and I accept the
    Terms and the Privacy Policy », avec les liens ;
  - côté API : `RegisterCommand.acceptTerms` annoté `@AssertTrue`. Sans ça, un client
    qui appelle l'API directement contournerait l'acceptation ;
  - en base : migration `V3__add_terms_acceptance.sql`, colonnes `users.terms_version`
    (`VARCHAR(20)`, la date de version des CGU) et `users.terms_accepted_at`
    (`TIMESTAMPTZ`), nullables pour les comptes existants ;
  - la version courante est une constante unique `features/auth/TermsVersion.java`
    (`CURRENT = "2026-10-21"`). Le frontend affiche la même date en « Last updated » ;
    un test fonctionnel vérifie que la valeur enregistrée est cette constante.
  - **Pourquoi stocker la version** : quand les CGU changeront, on saura qui a accepté
    quoi et qui doit ré-accepter. Sans ça, impossible de prouver l'acceptation.
- **Durées de conservation annoncées** : seulement ce qui est vrai aujourd'hui.
  - Compte : jusqu'à sa suppression par l'utilisateur.
  - Jetons : expirés selon les durées de `application.properties`, supprimés avec le
    compte.
  - IP : 1 minute.
  - Emails chez Resend : 30 jours.
  - Il n'existe pas encore de purge des comptes inactifs ou jamais vérifiés. On
    l'ajoute à la roadmap Semaine 7 au lieu d'annoncer une durée fausse. La page dit
    « until you delete your account ».

---

## Phase 1 — Acceptation des CGU à l'inscription (backend)
- [x] `V3__add_terms_acceptance.sql` : `terms_version`, `terms_accepted_at`
- [x] `User` : deux champs `termsVersion`, `termsAcceptedAt` (remplis via le builder, comme les autres)
- [x] `TermsVersion.CURRENT`
- [x] `RegisterCommand` : `@AssertTrue boolean acceptTerms`
- [x] `RegisterHandler` : enregistre version et date à la création

### Tests
- **Unitaire** : `RegisterHandlerTest`, l'utilisateur créé porte `TermsVersion.CURRENT`
  et une date d'acceptation.
- **Fonctionnel** : `RegisterFunctionalTest` (nouveau, ou dans l'existant s'il couvre déjà
  l'inscription) :
  - `acceptTerms: false` ou absent → 400 ;
  - `acceptTerms: true` → 201 et colonnes renseignées en base.
- **Intégration** : couverte par le test fonctionnel, qui lit la ligne en base. Pas de
  requête de repository nouvelle à tester isolément.
- **Architecture** : les tests ArchUnit existants doivent rester verts ; pas de nouvelle
  règle.
- **Mutation** : informative, sur PR vers `main`.

- [x] **Bug trouvé en route, corrigé** : `GlobalExceptionHandler` transformait en 500 les
      erreurs de requête standard de Spring : JSON malformé, mauvaise méthode (405),
      mauvais type de contenu (415), route inconnue. C'est confirmé en prod :
      `curl -X PUT https://ageguessr.app/api/auth/login` renvoyait 500. Correction :
      - `HttpMessageNotReadableException` → 400 `MALFORMED_REQUEST`, avec un message
        générique ;
      - les exceptions qui implémentent `org.springframework.web.ErrorResponse` gardent
        leur statut et leurs en-têtes (`Allow`) ;
      - test : `functional/errors/RequestErrorsFunctionalTest`.

### Phase Summary
- `acceptTerms` est un `boolean` primitif : absent, Jackson 3 refuse le `null`
  (`FAIL_ON_NULL_FOR_PRIMITIVES`) → 400 `MALFORMED_REQUEST` ; `false` → 400
  `VALIDATION_ERROR`. Un `Boolean` nul passerait `@AssertTrue`.
- `./mvnw test` : 161 tests, 0 échec.
- **À ne pas déployer seul** : tant que le frontend n'envoie pas `acceptTerms`
  (Phase 2), l'inscription renvoie 400. Le backend et le frontend partent dans la même
  release.

## Phase 2 — Inscription et pied de page (frontend)
- [x] `register` : case à cocher obligatoire, liens vers `/terms` et `/privacy` (nouvel
      onglet, pour ne pas perdre le formulaire), `acceptTerms` envoyé à l'API
- [x] `features/layout/footer/` et ajout dans `app.html`
- [x] Mettre à jour les helpers des parcours e2e qui remplissent l'inscription
      (`e2e/journeys/support.ts`, `e2e/component/register.spec.ts`)

### Tests
- **Unitaire frontend** :
  - le formulaire est invalide tant que la case n'est pas cochée ;
  - la requête envoyée contient `acceptTerms: true` ;
  - le pied de page affiche les 3 liens et le contact.
- **Interface (Playwright)** :
  - `register.spec.ts` : la soumission sans case cochée affiche une erreur et
    n'appelle pas l'API ;
  - `footer.spec.ts` : chaque lien mène à sa page, sans défilement horizontal à 360 px.
- **QA e2e** : les parcours existants (`auth`, `delete-account`, `password-reset`) passent
  avec la case cochée. Pas de nouveau parcours : il n'est que prolongé.

### Phase Summary
- Case `acceptTerms` non pré-cochée (`Validators.requiredTrue`), envoyée à l'API par
  `Auth.register(…, acceptTerms)`. Liens CGU et confidentialité en `target="_blank"`.
- Pied de page `app-footer` : `app.html` passe en colonne flex, et le contenu prend
  `flex-1`, ce qui garde le pied de page en bas de l'écran.
- `pnpm test` : 89 verts. Sur cette branche, pas de spec de tokens : il arrive avec la
  PR #13.
- Playwright : le test « Terms dans un nouvel onglet » attend `/terms`, il ne passera
  qu'avec la Phase 3 (route absente → redirection vers l'accueil).

## Phase 3 — Les trois pages (frontend)
- [ ] `features/legal/legal-notice/` : éditeur non professionnel, contact, hébergeur OVH
      (téléphone vérifié), crédits (polices OFL ; photos Wikimedia, à compléter en
      Semaine 2)
- [ ] `features/legal/terms/` :
  - objet ;
  - accès sans compte et avec compte ;
  - âge minimum de 13 ans ;
  - règles de conduite (pseudo non offensant, pas de triche automatisée) ;
  - suspension et suppression ;
  - propriété intellectuelle et contenus tiers ;
  - absence de garantie de disponibilité ;
  - responsabilité ;
  - modification des CGU ;
  - droit applicable et juridiction ;
  - version française prévalente.
- [ ] `features/legal/privacy/` :
  - responsable de traitement et contact ;
  - données, finalités et bases légales (contrat pour le compte, intérêt légitime pour
    la sécurité et le rate limiting) ;
  - durées de conservation ;
  - sous-traitants : OVH (UE), Cloudflare (proxy et DNS, États-Unis), Resend
    (États-Unis, clauses types et DPF) ;
  - cookies et stockage local ;
  - droits (accès, rectification, effacement via la page compte, portabilité,
    opposition, limitation) ;
  - réclamation auprès de la CNIL ;
  - mineurs ;
  - modifications.
- [ ] Routes dans `app.routes.ts`, `?lang=fr` avec lien de bascule, `<title>` par page
- [ ] Contrôle visuel mobile et desktop, dans les deux thèmes

### Tests
- **Unitaire frontend** : chaque page affiche l'anglais par défaut et le français avec
  `?lang=fr` (bloc `lang="fr"` présent) ; la date de version des CGU est celle attendue.
- **Interface (Playwright)** : `legal.spec.ts`, les trois pages s'affichent dans les deux
  langues, la bascule fonctionne, pas de défilement horizontal à 360 px.
- **Omis** : backend, pas concerné par cette phase.

## Phase 4 — Documentation
- [ ] `docs/decisions/architecture.md` : section « Pages légales » (éditeur anonyme, droit
      applicable, versionnement des CGU, langues)
- [ ] `docs/decisions/design.md` : retirer « Mentions légales / CGU / RGPD » de « À
      définir »
- [ ] `docs/roadmap.md` :
  - cocher la case de la Semaine 1 ;
  - réduire la case RGPD de la Semaine 7 à l'export des données ;
  - ajouter en Semaine 7 « Purge des comptes jamais vérifiés et des comptes inactifs »
    et « Ré-acceptation des CGU à chaque nouvelle version ».

## Phase 5 — Relectures et PR
- [ ] Agent `code-reviewer` : auth, données utilisateur, conformité RGPD et légale
- [ ] Agent `architecture-reviewer`
- [ ] `./mvnw test`, `pnpm test`, lint, Playwright `component` et `journeys` verts
- [ ] PR `feature/legal-pages` → `develop`

### Verification Plan
- Après release : les 3 pages répondent en prod dans les deux langues.
- Une inscription en prod enregistre `terms_version` = `TermsVersion.CURRENT`.
- Un email envoyé à `contact@ageguessr.app` arrive bien.

## Edge cases à couvrir
- Appel direct de l'API sans `acceptTerms` : 400, le compte n'est pas créé.
- `acceptTerms` envoyé en chaîne `"true"` : Jackson le convertit, c'est accepté. On le
  documente sans le bloquer.
- Lien CGU cliqué depuis le formulaire d'inscription : il s'ouvre dans un nouvel onglet,
  le formulaire garde sa saisie.
- `?lang=xx` inconnu : la page s'affiche en anglais.
- Pied de page sur une page courte : il reste en bas de l'écran (`min-h-screen` +
  flex), pas au milieu.
- Comptes existants sans acceptation : connexion inchangée.

## Final Recap
_(à remplir en fin de feature)_

## Deployment Plan
- La migration Flyway `V3` ne fait qu'ajouter des colonnes nullables : pas de
  verrouillage long, pas de perte de données.
- **Prérequis côté utilisateur** : la redirection `contact@ageguessr.app` fonctionne.
- Ce sera une release commune avec la PR #13 : `develop` → `main` en **merge commit**.
- Smoke test : `/legal-notice`, `/terms` et `/privacy` répondent en prod, et un
  `POST /api/auth/register` sans `acceptTerms` renvoie 400.

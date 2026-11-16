# Purge des comptes non vérifiés et inactifs

Limitation de la conservation (RGPD) : un compte jamais vérifié est supprimé 7 jours après
sa création ; un compte sans activité depuis 3 ans est supprimé, après un email
d'avertissement 30 jours avant. Durées choisies le 2026-11-15. Branche
`feature/account-purge`. Roadmap Semaine 7.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

### Décisions
- **Activité** = connexion ou renouvellement de session (`users.last_active_at`, V25).
  Une session vivante se renouvelle au moins tous les 90 jours (durée du refresh token) :
  pas besoin de toucher la base à chaque requête. Valeur initiale des comptes existants :
  leur dernière session connue, sinon leur création.
- **Avertissement unique** (`users.inactivity_warned_at`) envoyé quand l'inactivité
  atteint 3 ans − 30 jours, remis à NULL à la prochaine activité. Suppression seulement si
  l'avertissement date d'au moins 30 jours : un job arrêté quelques jours ne raccourcit
  jamais le préavis. Un email qui échoue n'est pas marqué envoyé : réessayé le lendemain.
- **Admins exclus** de la purge des inactifs (le rôle se pose à la main en SQL ; supprimer
  le seul admin fermerait la curation). Les non vérifiés n'ont jamais d'activité : seule la
  date de création compte.
- **Suppression** = `DELETE FROM users` : les cascades (règle RGPD existante) font le
  reste, comme la suppression demandée par le joueur.
- **Job quotidien** à 00:50 UTC (après la purge des séries), désactivable
  (`app.auth.retention.purge-enabled`), comme les purges du jeu.
- Durées en propriétés (`app.auth.retention.*`), à garder en accord avec `/privacy`.

## Phase 1 : backend
Status: Complete

- [x] V25 : `last_active_at` (NOT NULL, défaut `now()`, rempli pour l'existant),
      `inactivity_warned_at`.
- [x] `User` : champs, `markActive(Instant)` ; appelé par `LoginHandler` et
      `RefreshHandler` (Clock injecté au login).
- [x] `AccountRetentionProperties` + `AuthConfig` ; propriétés dans
      `application.properties` (et tests : purge désactivée).
- [x] `PurgeAccounts/` : handler (non vérifiés, avertissements, inactifs → compteurs),
      scheduler. Requêtes dans `UserRepository`.
- [x] Email `inactivityWarning` FR/EN (`AccountEmailTexts`, `AccountEmails`), lien vers
      `/login` dans la langue du compte, date de suppression.
- [x] Tests : `LoginHandlerTest`/`RefreshHandlerTest` (activité marquée, avertissement
      levé) ; `PurgeAccountsIntegrationTest` (bornes 7 jours, avertissement à 3 ans − 30 j,
      pas deux fois, suppression après préavis seulement, admin épargné, actif épargné,
      email en échec non marqué) ; `AccountEmailsTest` (textes FR/EN).

### Verification Plan
- `cd ageguessr-api && ./mvnw test` vert.

### Phase Summary
Les deux colonnes sont en lecture seule dans `User` (`insertable/updatable = false`) et
écrites par `UserRepository.markActive`/`markWarned` : un `save()` d'un `User` construit
en mémoire aurait écrit NULL dans `last_active_at` (NOT NULL). Suppression des inactifs
avant les avertissements : un compte averti ce jour ne part jamais ce jour. Ajout hors
plan : `lastActiveAt` et `inactivityWarnedAt` dans l'export des données (`AccountData`).
Email FR neutre (« votre compte n'a pas servi », pas « vous ne vous êtes pas connecté »).
Vérifié : `./mvnw test` complet vert.

## Phase 2 : front légal et docs
Status: Complete

- [x] `/privacy` FR + EN, section 4 : non vérifiés 7 jours, inactifs 3 ans avec
      avertissement ; date mise à jour.
- [x] `architecture.md` (RGPD), roadmap cochée + vérification prod.

### Verification Plan
- `cd frontend && pnpm test`, lint, format verts.

### Phase Summary
`/privacy` FR + EN : sections 3 (date de dernière activité, email d'avertissement) et 4
(3 ans, 30 jours, 7 jours). Date déjà au 2026-11-15 (export du même jour).
Vérifié : `pnpm test` 472 + 7, lint, format.

## Phase 3 : reviews et PR
Status: Not started

- [x] architecture-reviewer (import statique), code-reviewer : « 3 ans » = 1 096 jours
      (jamais moins de 3 années civiles), marge de 12 h sur le préavis pour que la
      suppression tombe au passage J+31 annoncé par l'email, doc précisée sur les admins.
- [ ] PR → `develop`, CI verte, merge.

### Verification Plan
- `gh pr checks <n>` tous `pass`.

### Phase Summary
_(write when phase completes)_

## Final Recap
Les comptes jamais vérifiés partent après 7 jours, les comptes inactifs après 3 ans et
un email 30 jours avant ; une connexion suffit à garder son compte.

## Deployment Plan
1. Merger dans `develop`, puis release `develop → main` (merge commit) : Flyway applique
   V25 au démarrage (ajout de colonnes, remplissage depuis les sessions existantes).
2. Aucune variable d'environnement obligatoire (défauts : 7, 1096, 30, activé).
3. Premier passage la nuit suivante à 00:50 UTC ; vérification listée dans la roadmap.

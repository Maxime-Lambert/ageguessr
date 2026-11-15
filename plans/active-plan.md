# Double authentification (TOTP)

Facultative, activée depuis `/account` avec une app d'authentification (Google
Authenticator, Aegis, 1Password…). Une fois active, la connexion demande le mot de passe
puis un code à 6 chiffres. Secours choisi le 2026-11-15 : un lien par email désactive la
2FA (pas de codes de secours). Branche `feature/two-factor`. Roadmap Semaine 7.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

### Décisions
- **Ce que ça protège** : un mot de passe fuité (réutilisé ailleurs, deviné). **Pas** une
  boîte mail compromise : le secours par email, choix assumé de l'utilisateur, permet à
  qui contrôle la boîte de désactiver la 2FA. Les emails d'activation / désactivation
  préviennent le propriétaire. Variante possible plus tard : désactivation par email
  différée de quelques jours, annulable.
- **TOTP RFC 6238** codé à la main (HMAC-SHA1, pas de 30 s, 6 chiffres, fenêtre ±1 pas),
  ~40 lignes testées sur les vecteurs de la RFC, plutôt qu'une dépendance. **Rejeu
  interdit** : le dernier pas accepté est gardé (`totp_last_step`), un code déjà utilisé
  est refusé.
- **Secret chiffré au repos** : AES-256-GCM, clé `TOTP_ENCRYPTION_KEY` (base64, 32 octets)
  dans le `.env` du VPS. **À ajouter en prod avant de déployer** : sans elle, le backend
  ne démarre pas. Clé de test fixe dans les propriétés de test.
- **Migration V26** : `users.totp_secret` (chiffré, base64), `totp_enabled_at`,
  `totp_last_step`. Secret en attente tant que `totp_enabled_at` est NULL.
- **Activation** : `POST /api/auth/me/totp/setup` (mot de passe, comme les autres actions
  sensibles) → secret + URI `otpauth://` ; `POST /api/auth/me/totp/enable` (code) → active,
  email de notification. **Désactivation** depuis le compte : mot de passe + code.
- **Connexion en deux temps** : si la 2FA est active, `POST /api/auth/login` répond
  `{ "totpRequired": true, "challenge": … }` sans jetons ; `POST /api/auth/login/totp`
  (challenge + code) émet les jetons. Challenge = jeton opaque 5 min (`account_tokens`,
  purpose `TOTP_LOGIN`, hash seul stocké, usage unique). Un code faux compte dans
  `AccountLockout`. Le renouvellement de session ne redemande rien (session déjà prouvée).
- **Secours** : depuis l'étape du code, « Je n'ai plus accès à mon app » (challenge
  requis, donc mot de passe déjà prouvé) → email avec lien 1 h (`TOTP_RECOVERY`) → page
  `/disable-two-factor` avec clic explicite (les scanners de liens ouvrent les mails) →
  2FA désactivée, sessions révoquées, email de notification.
- **QR code côté front** (dépendance `qrcode`), le secret affiché aussi en texte pour la
  saisie manuelle. Rien du secret dans les logs.
- **Export** : `totpEnabledAt` seulement, jamais le secret.

## Phase 1 : cœur backend
Status: Complete

- [x] `Totp` (génération de secret base32, code d'un pas, vérification ±1 avec pas
      minimal) + tests sur les vecteurs RFC 6238.
- [x] `SecretCipher` (AES-GCM, IV aléatoire par chiffrement) + propriété
      `app.totp.encryption-key` validée au démarrage + tests.
- [x] V26 + champs `User` + `AccountData` (`totpEnabledAt`).

### Verification Plan
- `./mvnw test` vert.

### Phase Summary
`Totp` (RFC 6238, base32 maison, `codeAt` public pour les tests qui jouent l'app),
`SecretCipher` (AES-256-GCM, échec au démarrage si la clé n'a pas 32 octets), V26
(contrainte : activée ⇒ secret présent). Clé câblée dans `ci.yml` (step de déploiement),
`docker-compose.prod.yml`, `.env.example` ; clé de test aléatoire dans les propriétés de
test. Vérifié : `TotpTest` (vecteurs RFC), `SecretCipherTest`.

## Phase 2 : endpoints backend
Status: Complete

- [x] Setup / enable / disable (`features/auth/SetUpTotp`, `EnableTotp`, `DisableTotp`).
- [x] Login en deux temps (`Login` renvoie le challenge, `LoginTotp` émet les jetons).
- [x] Secours (`RequestTotpRecovery`, `ConfirmTotpRecovery`), emails FR/EN (activée,
      désactivée, lien de secours).
- [x] `ProfileResponse.totpEnabled`.
- [x] Tests unitaires des handlers + fonctionnels du parcours complet (rejeu refusé,
      challenge expiré / réutilisé, verrouillage, secours).

### Verification Plan
- `./mvnw test` vert.

### Phase Summary
Routes : `/api/auth/me/two-factor/{setup,enable,disable}`, `/api/auth/login/totp`,
`/api/auth/login/totp/recovery`, `/api/auth/disable-two-factor` (les trois dernières
publiques). `AccountTokenService.find` (sans consommer) : un code mal tapé garde le
challenge. Le login ne remet pas le compteur d'échecs à zéro avant le code. Vérifié :
`./mvnw test` complet vert, dont `TwoFactorFunctionalTest` (activation, rejeu,
verrouillage, challenge expiré / réutilisé, secours, désactivation, export sans secret).

## Phase 3 : frontend
Status: Not started

- [ ] `/account` : section 2FA (activer : mot de passe → QR + secret → code ; désactiver).
- [ ] `/login` : étape du code, lien « plus accès à mon app ».
- [ ] Page `/disable-two-factor` (noindex), i18n FR, `/privacy` (secret chiffré, date).
- [ ] Tests Vitest ; Playwright composant de l'étape de code si utile.

### Verification Plan
- `pnpm test`, lint, format verts.

### Phase Summary
_(write when phase completes)_

## Phase 4 : docs, reviews, PR
Status: Not started

- [ ] `architecture.md` (« Récupération du compte » mis à jour), roadmap, vérification prod.
- [ ] architecture-reviewer + code-reviewer, corrections.
- [ ] PR → `develop`, CI verte, merge. Prévenir : `TOTP_ENCRYPTION_KEY` à ajouter en prod
      avant la release.

### Verification Plan
- `gh pr checks <n>` tous `pass`.

### Phase Summary
_(write when phase completes)_

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

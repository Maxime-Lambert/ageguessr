# Export des données personnelles

Droit d'accès et de portabilité en self-service : depuis `/account`, un joueur connecté
télécharge un fichier JSON avec toutes les données que le site garde sur lui. Branche
`feature/personal-data-export`. Roadmap Semaine 7.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

### Décisions
- **Chaque feature exporte ses propres tables** : interface `PersonalDataSection`
  dans `shared/personaldata/` (nom de section, tables couvertes, `export(userId)`), une
  implémentation par feature (auth, game, friends, people, hints). Le handler d'export
  (auth) reçoit toutes les sections par injection (`List<PersonalDataSection>`) : aucune
  feature n'importe une autre (ArchitectureTests), et chaque feature reste seule à
  connaître ses tables.
- **Garde-fou** : un test d'intégration liste dans `information_schema` toutes les
  colonnes qui référencent `users(id)` et exige que chaque table soit couverte par une
  section. Une nouvelle table liée à un joueur casse le test tant qu'elle n'est pas
  exportée, comme `CascadeDeleteIntegrationTest` pour la suppression.
- **Contenu** : compte (email, pseudo, langue, rôle, dates, CGU, verrouillage),
  sessions et liens email (dates seulement, **jamais un hash** de mot de passe ou de
  jeton), parties du jour et séries (essais, points, catégories d'indices, signalements,
  dates), séries créées et rejointes, amitiés (pseudo de l'autre, sens, statut, dates),
  et pour un admin les photos et indices relus (id, date).
- **Jamais la réponse d'une manche** : ni personne, ni photo, ni texte d'indice. Une
  manche en cours ne doit pas pouvoir se tricher par l'export ; l'âge deviné et les
  points suffisent à la portabilité.
- **Endpoint** `GET /api/auth/me/data` : JSON avec `Content-Disposition: attachment;
  filename="ageguessr-data-<date>.json"`. Sous `/api/auth/`, donc déjà limité par le
  rate limiting auth. Pas de re-saisie du mot de passe : la session voit déjà tout ça
  (compte, historique, amis) ; l'export n'expose rien de plus.
- **Front** : bouton « Télécharger mes données » sur `/account`, téléchargement via
  `Blob` ; `/privacy` (FR + EN) dit qu'on peut le faire soi-même.

## Phase 1 : backend
Status: Complete

- [x] `shared/personaldata/PersonalDataSection.java` (+ javadoc de la règle).
- [x] Sections : `auth/AccountData` (users, refresh_tokens, email_verification_tokens,
      account_tokens), `game/GameData` (game_plays, free_series, free_series_players,
      free_series_plays), `friends/FriendData` (friendships), `people/PhotoReviewData`
      (person_photos), `hints/HintReviewData` (hints). SQL natif lecture seule.
- [x] `auth/ExportPersonalData/` : query, handler (assemble les sections dans un ordre
      fixe, `exportedAt`), controller.
- [x] Tests : unitaire du handler (ordre, sections assemblées) ; intégration
      `PersonalDataCoverageIntegrationTest` (toutes les FK vers users couvertes) ;
      fonctionnel `ExportPersonalDataFunctionalTest` (401 sans jeton, contenu après
      inscription + une manche jouée + une amitié, pas de `passwordHash` ni de hash,
      pas de nom de personne, en-tête attachment, données d'un autre joueur absentes).

### Verification Plan
- `cd ageguessr-api && ./mvnw test` vert, dont ArchitectureTests.

### Phase Summary
`Rows` (shared/personaldata) lit TIMESTAMPTZ, INT nullable et VARCHAR[] (un
`java.sql.Array` n'est pas sérialisable). Compte supprimé entre l'émission du jeton et
l'export : 404 plutôt qu'une 500. Le test du jeu vit dans `GameFunctionalTest`, qui sait
déjà préparer une partie : manche en cours et manche finie, `assertNoAnswerIn`.
Vérifié : `./mvnw test` complet vert (ArchitectureTests compris).

## Phase 2 : frontend et docs
Status: Complete

- [x] `Auth.downloadData()` (ou API dédiée) + bouton sur `/account`, message d'erreur.
- [x] i18n FR, texte `/privacy` FR + EN (droits : téléchargement en self-service).
- [x] Tests Vitest du bouton (requête, nom de fichier, erreur).
- [x] Docs : `architecture.md` (section RGPD), roadmap cochée + vérification prod.

### Verification Plan
- `cd frontend && pnpm test`, `pnpm lint`, `pnpm format:check` verts.

### Phase Summary
`FILE_SAVER` (features/auth/file-saver.ts, lien `blob:` cliqué puis révoqué) derrière un
jeton pour les specs ; nom du fichier lu dans `Content-Disposition`. `/privacy` daté du
2026-11-15.
Vérifié : `pnpm test` 472 + 7, lint et format OK.

## Phase 3 : reviews et PR
Status: Not started

- [x] architecture-reviewer (rien de bloquant), code-reviewer : `Cache-Control: no-store`,
      `Content-Disposition` exposé en CORS, points d'une manche en cours masqués (avec
      l'essai et la formule, ils donnaient l'âge exact).
- [ ] PR → `develop`, CI verte, merge.

### Verification Plan
- `gh pr checks <n>` tous `pass`.

### Phase Summary
_(write when phase completes)_

## Final Recap
Un joueur télécharge depuis `/account` un JSON de tout ce que le site garde sur lui ;
chaque feature exporte ses tables et un test sur le schéma réel interdit d'en oublier une.

## Deployment Plan
1. Merger dans `develop`, puis release `develop → main` (merge commit).
2. Aucune migration, aucune variable d'environnement.
3. Vérification prod listée en tête de `docs/roadmap.md`.

# Essai : vocabulaire neutre (sans lexique enquête)

Voir ce que donne le jeu sans la métaphore enquête (« affaire », « dossier », « piste »,
« enquête libre »), en EN et en FR, **visuel enquête inchangé** (décision de l'utilisateur,
2026-11-14). Branche `feature/neutral-voice` : un essai, fusionné seulement si
l'utilisateur le garde.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Décisions
- `VOICE = NEUTRAL_VOICE` dans `game-vocabulary.ts` : les termes déjà doublés passent au
  repli prévu par `docs/decisions/design.md` (manche, partie du jour, indice, résultat,
  récapitulatif, série).
- Les textes qui contournent `VOICE` (règles, chargement, profil, amis, partage, bulles
  des indices, titre de page) sont réécrits en neutre **sur place**, mêmes IDs i18n : pas
  de second jeu de textes pour un essai. Si l'essai est gardé, une PR de suivi supprime
  `INVESTIGATION_VOICE` ; sinon la branche n'est pas fusionnée.
- Partage du jour : « Partie du <date> : <qualificatif> » (brillant, bien joué, pas mal,
  pas facile, ça ira mieux demain), invitation « Toi aussi, joue sur <site> ».
- Hors périmètre : visuel (coupures de journal, tampons, sépia), emails, admin.

## Phase 1: Textes neutres
Status: Complete

- [x] `VOICE = NEUTRAL_VOICE`
- [x] `GAME_TEXT` : ruleGuess, ruleHints, rulePoints, playToday, loading, noGame, closed,
      comeBack ; `dailyShareHeadline`, `game.share.invite`
- [x] `lead-help-texts.ts` : `leadHelpLabel`
- [x] `profile.ts`, `friends-text.ts`, `app.routes.ts` (titre de la série)
- [x] `messages.fr.xlf` : cibles correspondantes
- [x] Specs unitaires et e2e (component, component-fr, journeys) aux nouveaux textes

### Verification Plan
- `grep -n -i -E "enqu|dossier|affaire|piste" frontend/src/locale/messages.fr.xlf | grep target`
  → seulement les cibles de `INVESTIGATION_VOICE` (`game.voice.*`), plus rien d'affiché
- `grep -rn -i -E "\b(case|file|lead|investigation)s?\b" frontend/src/app --include=*.ts`
  → plus de texte affiché (seulement commentaires, noms de code, `game.voice.*`)
- `cd frontend && pnpm test` → vert ; `pnpm exec ng build` → pas de traduction manquante

### Phase Summary
`VOICE = NEUTRAL_VOICE` ; 29 textes réécrits en neutre sur place (EN dans le TS, FR dans
`messages.fr.xlf`), 5 IDs du partage renommés (`game.share.wellPlayed/notBad/tough/
tomorrow`). Specs unitaires et e2e (component, component-fr, journeys) mis à jour. Vérifié :
plus aucune cible FR avec enquête/dossier/affaire/piste hors `game.voice.*` ; `pnpm test`
463 verts ; `ng build` sans traduction manquante. Playwright : validé par la CI seulement
(journeys jamais lancés).

## Phase 2: Docs et PR
Status: Complete

- [x] `docs/decisions/design.md` : l'essai neutre (date, branche, périmètre mots seuls)
- [ ] ~~Captures EN/FR~~ abandonné : shoot.mjs ne sait capturer que les maquettes ou la
      prod, et un serveur de dev local fait planter WSL ; l'utilisateur juge en lançant
      l'app ou sur la liste des textes de la PR
- [x] Revue architecture-reviewer, PR vers `develop` marquée « essai »

### Verification Plan
- `cd frontend && pnpm test` → vert ; `cd ageguessr-api && ./mvnw test` → vert

### Phase Summary
`design.md` documente l'essai. Revue architecture : 0 bloquant/important ; corrigés :
titres de tests encore en vocabulaire enquête, « 1 rounds »/« 1 manches » → singulier
(`game.*.lengthOne`). Vérifié : `pnpm test` 464 verts, `ng build` sans traduction
manquante. PR en brouillon vers `develop`.

## Final Recap
Le jeu tourne en vocabulaire neutre (EN + FR), visuel enquête inchangé. PR brouillon : à
fusionner seulement si l'utilisateur garde l'essai ; dans ce cas, PR de suivi pour
supprimer `INVESTIGATION_VOICE` et les cibles `game.voice.*`.

## Deployment Plan
Rien de spécial : frontend seul, déployé avec la prochaine release si la PR est fusionnée.

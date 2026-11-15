# Partage du jour en défi, sans bouton « Comparer avec vos amis »

Retour du 2026-11-15 sur le partage expliqué : « c'est mieux, mais il y a moyen d'avoir un
truc qui fait plus envie ». Direction choisie par l'utilisateur parmi trois : « Défi
direct ». Et le bouton « Comparer avec vos amis » du récapitulatif du jour est à retirer.
Branche `fix/daily-share-challenge`.

## For Future Agents
Plan court d'un correctif, rempli une fois le travail fait.

## Phase 1 : message et récapitulatif
Status: Complete

- [x] `dailyShareText` (`game-vocabulary.ts`) : « AgeGuessr · <date> », « J'ai deviné
      l'âge de N personnes en photo 📸 » (singulier pour 1, ligne omise si toutes retirées), grille
      et score formaté selon la langue (« 2 450 / 3 000 »), puis le qualificatif et
      « Tu fais mieux ? 👉 <site> ». Plus de légende des cases.
- [x] `dailyShareHeadline(day, …)` devient `dailyShareVerdict(points, max)`, mêmes
      seuils ; le dernier palier dit « Rude journée ! » (« ça ira mieux demain » ne se lit
      pas devant « Tu fais mieux ? »).
- [x] `Game` passe le nombre de photos jouées (manches non neutralisées) ; le bouton
      `/friends` et l'injection d'`Auth` disparaissent (le menu mène déjà aux amis).
- [x] i18n : `messages.xlf` réextrait, `messages.fr.xlf` à jour (anciens ids retirés).
- [x] Tests : `share.spec.ts` (texte complet, singulier, toutes photos retirées, seuils
      du qualificatif, jour UTC), `game.spec.ts` (texte partagé, aucun lien `/friends`
      même connecté).

Catégories de tests : unitaires et interface seulement. Pas d'intégration ni de backend
(aucun changement d'API), pas de QA e2e (le parcours de partage n'en a pas, vérification
manuelle en prod listée dans la roadmap).

### Verification Plan
- `pnpm test` (463 tests), `pnpm lint`, `ng build --localize` passent ; « Tu fais
  mieux » présent dans le bundle `fr`, « Comparer avec vos amis » absent.

## Final Recap
Le partage du jour se lit comme un défi qu'un ami qui ne connaît pas le jeu comprend, et le
récapitulatif n'offre plus que partager et jouer en libre.

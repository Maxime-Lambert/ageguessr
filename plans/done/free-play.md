# Free play

Second PR from the testers' feedback of 2026-11-23. « Série » becomes « Partie libre »
(*Free play*) everywhere a player reads it, an unfinished game can be abandoned, and the
estimated duration goes from the length cards. User decisions (2026-11-23): the name
« Partie libre », the duration removed rather than raised, abandon confirmed first.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Phase 1: Abandon, backend
Status: Complete

- [x] 1. `features/game/AbandonSeries/` : `DELETE /api/game/series/{id}` → 204. The
      handler finds the series as one of its players (`SeriesViews.ofPlayer`, else the
      usual 404), refuses a non-creator (`NOT_CREATOR`) and a finished or shared series
      (`SERIES_FINISHED`, new in `GameErrors`), then deletes it (V15 cascades its rounds,
      players and plays)
- [x] 2. `notCreator` message generalised (« may do this »), the code unchanged

Tests: functional (`SeriesFunctionalTest`, real Postgres) — abandon then no current
series, 404 on the series, its plays gone, a second abandon 404, a stranger 404; a
finished series 409 `SERIES_FINISHED`, a challenge friend 409 `NOT_CREATOR`, the
challenge still readable. No unit test: the handler is three guards and a delete, all
covered end to end. No migration.

### Verification Plan
- `./mvnw test -Dtest=SeriesFunctionalTest` passes, then the full `./mvnw test`

### Phase Summary
Done 2026-11-23. 12 functional tests pass, full suite too.

## Phase 2: Front
Status: Complete

- [x] 3. Rename: `freeMode` « Free play / Partie libre » (nav, heading, challenge links),
      `title.free`, `newSeries` « New game / Nouvelle partie », `tryFree` « Not enough? Try
      free play / Pas assez ? Essayez la partie libre », SEO description. IDs kept
- [x] 4. Duration removed (`seriesDuration` and its FR unit gone)
- [x] 5. `SeriesApi.abandon`; `FreeGame`: « Abandon this game » above a round, then an
      inline confirm (« Abandon » / « Keep playing »), back to the lengths on 204, an
      error kept on screen otherwise. The two small buttons prevent mousedown so the age
      field keeps its focus (its blur showed « enter an age »)

Tests: interface — confirm then cancel sends nothing, confirm sends DELETE and shows
the lengths, a failure keeps the round with an alert, no abandon on a finished game,
lengths without « min »; header and e2e labels renamed (CI runs e2e).

### Verification Plan
- `pnpm test`, `pnpm lint`, `pnpm build`; screenshot of the confirm on a mocked build

### Phase Summary
Done 2026-11-23. 503 specs pass, lint and build clean; screenshot showed the focus issue,
fixed with the mousedown guard.

## Final Recap
Stacked on `fix/tester-feedback-quick-wins` (both edit the xlf and the roadmap). The
architecture doc records the abandon and the name. Reviews: see the PR.

## Deployment Plan
No migration, no config. After deploy: start a free game, play one round, « Abandonner
cette partie » → confirm → the lengths; reload: no game picked up.

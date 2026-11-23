# Daily stats

Fifth PR from the testers' feedback of 2026-11-23: compare with the others who played the
same day, accounts or not, and recall the average age they gave. User decisions
(2026-11-23): guests count, shown once the game is finished, above about ten players.
Hypotheses taken on « on enchaîne tout » (no further question): the share counts the
players strictly below; the average is of the first guesses (before hints); computed on
request, in memory.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Phase 1: Backend
Status: Complete

- [x] `features/game/GetDayStats/`: `GET /api/game/days/{date}/stats`, public with the
      JWT optional. 409 `GAME_CLOSED` for a day to come, 404 for a day without a game, 409
      `DAY_NOT_FINISHED` until the asker finished the day (no player either)
- [x] `DayStats` (pure): players who finished every playable round; share strictly below
      over the others; first-guess mean per playable round; under `MINIMUM_PLAYERS` (10),
      the count alone
- [x] `GamePlayRepository.findByGameDate`, `GameErrors.dayNotFinished`

Tests: unit `DayStatsTest` (minimum, ties, quitters and withdrawn rounds left out),
functional `GameFunctionalTest.theStatsOfTheDayAreForThoseWhoFinishedIt` (409 before,
200 after with 10 players, a day to come).

### Verification Plan
- targeted tests, then `./mvnw test`

### Phase Summary
Done 2026-11-23. 3 unit and 1 functional tests pass.

## Phase 2: Frontend
Status: Complete

- [x] `GameApi.stats`, `DayStats` model; `Game` asks once per day when the summary shows,
      a failure only leaves the stats out
- [x] `DaySummary` input `stats`: « Mieux que 72 % des 143 joueurs qui ont fini cette
      partie » under the score, « Moyenne des joueurs : 44 ans » under each round; nothing
      under the minimum; free play passes none
- [x] FR translations

Tests: interface — Game asks the stats only once the summary shows, shows them, keeps
the summary on a failure; DaySummary with and without stats. Teardown flushes a stats
request the other Game tests do not care about.

### Verification Plan
- `pnpm test`, `pnpm lint`, `pnpm build`; a phone screenshot of the summary

### Phase Summary
Done 2026-11-23. 513 specs pass; screenshot read well once the average went to its own
line.

## Final Recap
Docs: `architecture.md` « Stats du jour »; `/privacy` (EN, FR) declares the aggregate
stats (code-reviewer 🟡, GDPR transparency), says « free play » and the abandon, updated
2026-11-23 (no Terms version change). Reviews: architecture-reviewer 🟠 a JSDoc moved
off `day()` and the stats request not cancelled with the page (`takeUntilDestroyed`;
not `onCleanup`, which a rerun of the effect would trigger) fixed; 🟡 `short` position and
the play delegates in the test applied, the duplicated day guards and the `active()`
read left. code-reviewer: no blocking point; its 🟡 on a full `game_date` index and a
cache left, tracked in the architecture doc (in-memory at today's audience).

## Deployment Plan
No migration, no config. After deploy: finish today's game; with 10 players or more
the share and the averages show, under 10 nothing.

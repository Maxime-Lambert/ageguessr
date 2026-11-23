# Tester feedback — quick wins

First of five PRs from the testers' feedback of 2026-11-23 (four players, prod). This one:
French wording, the ⓘ help on click only, lead cards of one size, the result name visibly
a link, a confetti burst on an exact age, a fuller summary, a line-per-round share, a
countdown to the next game. Frontend only, no API change.

User decisions (2026-11-23):
- ⓘ help: **option A**, click/tap/Enter only, no hover. Closed by a click elsewhere,
  Escape, or a second click on ⓘ.
- Lead cards keep one size whatever their number (no card spanning the last row).
- Share: one line per round, `🟨 499/1000`.
- The four PRs after this one are listed in `docs/roadmap.md` (« Retours testeurs »).

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Phase 1: Wording and calendar
Status: Complete

- [x] 1. `messages.fr.xlf` targets (sources unchanged unless noted):
      - `calendar.intro` → « Vous avez raté un jour ? Les parties précédentes restent
        jouables. » (EN source → « Missed a day? Past daily games can still be played. »)
      - `game.ruleHints` → « Puis vous choisissez quelques indices : chacun remonte à autant
        d'années que son âge sur la photo. Un film sorti il y a 40 ans signifie qu'elle avait
        40 ans. »
      - `game.pickHintHelp` → « Chacun remonte à autant d'années que l'âge de la personne
        sur la photo : une chanson sortie il y a 40 ans signifie qu'elle avait 40 ans. »
        (« Chacune » was wrong, « indice » is masculine)
      - `game.rulePoints` → « Chaque essai rapporte jusqu'à 500 points : plus il est proche,
        plus il rapporte. Un premier essai exact rapporte 1000 points. Une nouvelle partie
        commence chaque jour à minuit UTC. »
      - `game.leadHelp.music.kind` → « …à l'Eurovision ou aux Grammy Awards. »
- [x] 2. Calendar legend: drop the « No game » item (an empty slot says it). The square's
      aria-label keeps the state for screen readers.
- [x] 3. `docs/roadmap.md`: a « Retours testeurs du 2026-11-23 » section listing the five
      PRs and the decisions (Partie libre rename + abandon + duration removed; sticky
      header with « ? »; TV and video game categories, the year leak accepted, one card
      size; daily stats incl. guests, shown after the game above ~10 players).

Tests: interface — calendar legend has 3 items, a past-unavailable day still labelled.
Build fails on a missing FR target, so `pnpm build` checks the xlf.

### Verification Plan
- `pnpm exec ng test --include='src/app/features/game/calendar/**/*.spec.ts'` passes
- `pnpm build` passes

### Phase Summary
Done 2026-11-23. Wording as listed; `calendar.intro` EN source shortened too. Legend down
to 3 items (test added). Roadmap section added. Verification: calendar spec and build pass.

## Phase 2: Lead cards
Status: Complete

- [x] 4. `LeadCard`: remove the hover opening (`pointerenter`, `HOVER_DELAY_MS`, the
      hover timer) and the close on `pointerleave`. Any click on ⓘ toggles. Keep the close
      on focus leaving (keyboard), the document `pointerdown` outside and Escape (`Round`).
      Update the class doc comment.
- [x] 5. `round.html`: drop the `col-span` classes on the last card; the grid keeps 2/3
      columns, a lone last card has the width of the others. Update the comment.
- [x] 6. Revealed hints (second guess, `round.html`, and result, `round-result.html`):
      an ⓘ next to the category label opens the same help (kind, rule, example).
      Extract the help bubble from `LeadCard` into a small `LeadHelp` component (button +
      popover, click-only, Escape/outside closes) reused by both, so the behaviour is one
      piece of code.

Tests: interface — hovering ⓘ opens nothing; a click opens, a second click closes;
outside click and Escape close; Enter toggles; keyboard focus leaving closes; 7 cards on
desktop all the same width (no `col-span`); ⓘ on a revealed hint opens its help.

### Verification Plan
- `pnpm exec ng test --include='src/app/features/game/{lead-card,lead-help,round,round-result}/**/*.spec.ts'`

### Phase Summary
Done 2026-11-23. `lead-help/` holds `LeadHelpPanel` (draws kind, rule, example) and
`LeadHelpButton` (ⓘ with its own open state, closes on outside pointerdown, Escape, focus
leaving). `LeadCard` keeps the round-controlled open state, click toggles. Verification:
62 specs of the five folders pass.

## Phase 3: Result, summary, share, countdown
Status: Complete

- [x] 7. Result: the person's name underlined (dotted) with a `lucideExternalLink` icon and
      an sr-only « (Wikipedia, new tab) » label, i18n'd.
- [x] 8. Exact age (`exact()`): a one-shot confetti burst, home-made (~40 absolutely
      positioned spans, CSS keyframes, removed after the animation), `aria-hidden`, none
      under `prefers-reduced-motion: reduce`. No dependency: a 40-line component beats a
      library for one effect.
- [x] 9. Summary (`DaySummary`): each line adds the real age and the guesses
      (« 42 ans · essais 38 → 41 », or « 42 ans · trouvé du premier coup »). Data already in
      `RoundView.result`.
- [x] 10. Share (`dailyShareText`, `shareText`): one line per round `🟨 499/1000`, a
      withdrawn photo `⬛ —`, then the total `2 458 / 3 000` on its own line. The max per
      round comes from the score (`max / rounds`). `squares()` becomes `scoreLines()`.
- [x] 11. Countdown: `NextGameCountdown` component (« Prochaine partie dans 5 h 12 min »,
      « dans 3 min », then « Une nouvelle partie est disponible » with a reload link),
      ticking each minute on a signal, timer cleared on destroy. Replaces `comeBack` on
      today's summary. Rules (`rulePoints`) keep « minuit UTC » and add the local time
      (« soit 1 h chez vous ») via `Intl.DateTimeFormat` in the browser's zone.

Tests: unit — countdown text at 5 h 12, 3 min, 0 (fake timers); share lines (exact,
neutralized, total formatting FR/EN); local midnight label. Interface — confetti on exact
only, none with reduced motion; summary shows age and guesses; name link has the icon.
No QA journey change beyond updating the share-text assertion in the daily e2e (CI runs
it, never locally). No backend, architecture or mutation test: no Java change.

### Verification Plan
- `pnpm test`, `pnpm lint`, `pnpm format:check`, `pnpm build` pass
- One `shoot.mjs` screenshot of the result (exact) and the summary on a mocked build

### Phase Summary
Done 2026-11-23. `scoreLines()` replaces `squares()` (daily, challenge, share-challenge);
`rulePoints` lost its midnight sentence, now `ruleNewGame(localMidnight())`; `comeBack`
removed, `NextGameCountdown` (output `playNext`, no live region: it would speak every
minute). FR countdown reads « dans 5 h 12 ». Verification: `pnpm test` 500 pass, lint,
prettier, build clean; screenshots (summary, leads with ⓘ open, exact result with
confetti, mobile leads) on a mocked FR build look right.

## Edge cases
- Countdown crossing midnight while the summary is open: shows the « available » state,
  no auto-reload (the player may be reading).
- Series share with no withdrawn photo vs. a challenge where all are withdrawn.
- `max / rounds` when every round is neutralized: no division, total line only.
- ⓘ inside a disabled card (3 picked): still usable (stays outside the card button).

## Final Recap
Frontend only, three phases shipped as planned. Reviews: code-reviewer found nothing (the
share lines carry points only, the summary details appear after the game). The
architecture-reviewer's one 🟠, `docs/decisions/architecture.md` still describing the
hover, was fixed; of its 🟡, the revealed hint's help id became `revealed-help-*` (distinct
on purpose from the cards' `lead-help-*`), and the help example counting from the current
year on a past day stays, the known trade-off already documented in `Round`.
Verification: `pnpm test` 500 pass, `./mvnw test` passes, lint, prettier, build clean.

## Deployment Plan
No migration, no config. Merge to develop, ship with the next release; after deploy,
check in prod: ⓘ opens on click only, a lone last lead card has the others' width, the
share text has one line per round, the summary counts down to the next game, an exact
first guess throws confetti.

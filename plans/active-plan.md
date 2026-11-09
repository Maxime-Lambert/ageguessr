# UX review fixes

Fix the eight points of the product review of 2026-11-09 (prod screenshots, a free
series played). Frontend only, no API change. User decisions (2026-11-09): « il y a
autant d'années » wording, first-visit rules card, rules + duration on the free page,
30-day calendar on the profile.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Phase 1: Game screens
Status: Not started

- [ ] 1. Hint help: `game.pickHintHelp` says the fact is from as many years ago as the
      age on the photo (the API picks `gameYear − age`, `HintPicker`), and the reveal
      shows « 1960 · il y a 66 ans » (`round-result.html`)
- [ ] 2. `HowToPlay` component (the rules in 3 lines) + `RulesCard` on the daily game
      and the free series, dismissed for good with « Compris » (localStorage,
      `ageguessr-rules-seen`, try/catch); `/how-to-play` page linked from the footer
- [ ] 3. Final guess field starts one year past the first guess, in the direction given
      (clamped to MIN_AGE..MAX_AGE), instead of empty
- [ ] 4. Age input placeholder « ? » in `text-muted` instead of a faint « 00 »
- [ ] 5. Free page: the rules under the length cards, an estimated duration on each
      card (~30 s per case)
- [ ] 6. Photo report moved off the primary button's row, to its own line below it

Tests (unit/interface, Vitest + Playwright component): round (prefill both directions,
clamp), round-result (« years ago »), rules card (shown, dismissed, storage throws),
free-game (durations, rules), age-input (placeholder), footer link. No QA journey: no
new critical path. No backend, architecture or mutation test: no Java change.

### Verification Plan
- `cd frontend && pnpm exec ng test --include='src/app/features/game/**/*.spec.ts'` passes
- `pnpm build` passes (FR translations complete)

### Phase Summary
_(write when phase completes)_

## Phase 2: Profile and header
Status: Not started

- [ ] 7. Profile: 30-day calendar (UTC days ending today, from history page 0): empty
      = not played, score colour by points/max, outlined = not finished; bigger
      « Not finished » badge
- [ ] 8. Header (guest, desktop): preferences trigger shows a globe + the site language
      code instead of an unlabeled settings icon; accessible name contains it

### Verification Plan
- `pnpm test` passes, `pnpm lint` and `pnpm format:check` pass
- Screenshots of game, free page, profile with `shoot-local.mjs`-style mocked build

### Phase Summary
_(write when phase completes)_

## Final Recap
_(write when all phases complete)_

## Deployment Plan
_(write when all phases complete)_

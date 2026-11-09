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
Status: Complete

- [x] 1. Hint help: `game.pickHintHelp` says the fact is from as many years ago as the
      age on the photo (the API picks `gameYear − age`, `HintPicker`), and the reveal
      shows « 1960 · il y a 66 ans » (`round-result.html`)
- [x] 2. `HowToPlay` component (the rules in 3 lines) + `RulesCard` on the daily game
      and the free series, dismissed for good with « Compris » (localStorage,
      `ageguessr-rules-seen`, try/catch); `/how-to-play` page linked from the footer
- [x] 3. Final guess field starts one year past the first guess, in the direction given
      (clamped to MIN_AGE..MAX_AGE), instead of empty
- [x] 4. Age input placeholder « ? » in `text-muted` instead of a faint « 00 »
- [x] 5. Free page: the rules under the length cards, an estimated duration on each
      card (~30 s per case)
- [x] 6. Photo report moved off the primary button's row, to its own line below it

Tests (unit/interface, Vitest + Playwright component): round (prefill both directions,
clamp), round-result (« years ago »), rules card (shown, dismissed, storage throws),
free-game (durations, rules), age-input (placeholder), footer link. No QA journey: no
new critical path. No backend, architecture or mutation test: no Java change.

### Verification Plan
- `cd frontend && pnpm exec ng test --include='src/app/features/game/**/*.spec.ts'` passes
- `pnpm build` passes (FR translations complete)

### Phase Summary
Done 2026-11-09. All six items shipped as listed; game, round, round-result, free-game,
rules-card, how-to-play(-page), age-input specs updated or added. E2E: `skipRules` in the
game mocks, a first-visit rules test on a phone, the prefill asserted in the daily game.
Verification: affected specs pass, `pnpm build` passes.

## Phase 2: Profile and header
Status: Complete

- [x] 7. Profile: 30-day calendar (UTC days ending today, from history page 0): empty
      = not played, score colour by points/max, outlined = not finished; bigger
      « Not finished » badge
- [x] 8. Header (guest, desktop): preferences trigger shows a globe + the site language
      code instead of an unlabeled settings icon; accessible name contains it

### Verification Plan
- `pnpm test` passes, `pnpm lint` and `pnpm format:check` pass
- Screenshots of game, free page, profile with `shoot-local.mjs`-style mocked build

### Phase Summary
Done 2026-11-09. Calendar with `data-day` squares, sr-only summary, legend; badge
enlarged. Header trigger « Preferences · EN/FR » with a globe. Footer nav renamed « Site
information ». Verification: `pnpm test` 381+ pass, lint and prettier clean, screenshots
of game (rules, prefill, reveal), free page and profile checked on a mocked build.

## Final Recap
Frontend only. Reviewed by architecture-reviewer: nothing blocking; its one actionable
yellow (a direct `HowToPlay` spec) was added. Known limit: `yearsAgo` has no singular
form, unreachable while MIN_AGE is 18.

## Deployment Plan
No migration, no config. Merge to develop, ship with the next release; after deploy,
check the rules card on a fresh browser and « il y a N ans » on a reveal.

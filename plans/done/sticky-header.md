# Sticky header

Third PR from the testers' feedback of 2026-11-23: the footer, with « How to play », was
out of sight during a round, and the long legal pages had no way back to the menu. User
decision (2026-11-23): a sticky header with a « ? » icon, rather than a back-to-top
button. Frontend only.

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Phase 1: Header
Status: Complete

- [x] 1. `app.html`: `<app-header class="sticky top-0 z-40 block">` (the header already
      has its own background and bottom border, 56/64 px)
- [x] 2. `styles.css`: `html { scroll-padding-top: 4.5rem }`, so an anchor lands under
      the header rather than behind it
- [x] 3. Header: a `lucideCircleQuestionMark` link to `/how-to-play`, on every screen size,
      named and titled « How to play » (GAME_TEXT, already translated)

Tests: interface — the app's header carries `sticky top-0`; the « How to play » link
points to `/how-to-play`, is titled, and is not in a `hidden` (desktop-only) block. No e2e
change: the one « How to play » lookup targets a region, not a link. No backend.

### Verification Plan
- `pnpm test`, `pnpm lint`, `pnpm build`; screenshot of `/fr/privacy` scrolled, phone
  and desktop

### Phase Summary
Done 2026-11-23. 505 specs pass, lint and build clean; the scrolled privacy page keeps the
header with the « ? » on both sizes.

## Final Recap
Stacked on `feature/free-play`. architecture-reviewer: stacking checked (popovers z-10,
dropdowns z-30 inside the header, confetti z-50, CDK overlays 1000), no 🔴/🟠; its 🟡 on
`z-40` asserted in the test, the single `scroll-padding-top` kept (generous on both sizes). Noticed on the way: `/privacy` still says « séries de
l'enquête libre », left to the user (a legal text).

## Deployment Plan
No config. After deploy: scroll `/fr/privacy` and a round on a phone, the header stays;
« ? » opens the rules.

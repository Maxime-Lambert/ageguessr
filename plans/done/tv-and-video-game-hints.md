# Television and Video games hints (8th and 9th categories)

Fourth PR from the testers' feedback of 2026-11-23. Two lead categories: Television (a
series or a show first aired) and Video games (a game released). Same extraction and rule
as the seven others. User decisions (2026-11-23): TV with « big shows or series », video
games even though they cover no year before the seventies, the leak on the age accepted
(hints come after the first guess), only the categories available shown, cards of one
size (done in `fix/tester-feedback-quick-wins`).

## For Future Agents
As work proceeds: mark checkboxes `- [x]` as items complete; when a phase is done,
set its status to `Complete` and write its **Phase Summary**; run the phase's
**Verification Plan** and record the result before moving on.

## Phase 1: Backend
Status: Complete

- [x] `HintCategory.TELEVISION`, `HintCategory.VIDEO_GAMES`
- [x] `V30__add_television_and_video_game_categories.sql`: the CHECK constraints of
      `hints`, `game_plays`, `free_series_plays`
- [x] `HintProperties.perRound` `@Max(9)`, `RevealHintCommand` / `RevealSeriesHintCommand`
      `@Size(max = 9)`
- [x] Tests: `HintRulesTest` (both accepted; the « unknown category » example was « TV »,
      now « COOKING »), `HintConstraintsIntegrationTest` (V30), `GameFunctionalTest` (a
      round offers, shows and keeps both)

### Verification Plan
- targeted tests, then the full `./mvnw test`

### Phase Summary
Done 2026-11-23. Targeted tests pass (67).

## Phase 2: Extraction
Status: Complete

- [x] `extract_notoriety.py`: `TELEVISION` (any subclass of television program, by start
      or release date, no episode nor season, no hentai), `VIDEO_GAMES` (video game by
      release date, no eroge, hentai or erotic video game), sitelinks 12, top 80, wording
      « Première diffusion de « X » » / « First aired: "X" », « Sortie du jeu vidéo « X » »
      / « Release of the video game "X" »
- [x] `television.json`, `video-games.json`; the seven other files untouched
- [x] Samples read (1962, 1978, 1985, 1996, 2008, 2014, FR and EN); genres of every chosen
      item scanned for adult content; texts scanned for crime words (fiction only)

Coverage (median hints a year): Television 10 from the sixties (FR 583 hints, EN 572,
25/27 years under 5, all before 1960); Video games 10 from the eighties (381 each, 48
years under 5, all before 1980). Those years simply offer no such lead.

### Verification Plan
- `--report` as above; `ImportHintsIntegrationTest.everyLineOfTheShippedFilesIsAccepted`

### Phase Summary
Done 2026-11-23. The genre scan found eroge (School Days, Yosuga no Sora, Fate/stay
night…), an erotic game (Custer's Revenge): now out by genre. Kept: mainstream series
Wikidata tags « erotic television series » (Sex and the City), ecchi or adult-animation
tags (Futurama). Lost with the rule: Second Life (tagged erotic video game). Import test
passes on the shipped files.

## Phase 3: Frontend
Status: Complete

- [x] `HintCategory` (game and hint-curation models), `CATEGORY_LABELS` (« Television /
      Télévision », « Video games / Jeux vidéo »), admin labels, `LeadIcon` (`lucideTv`,
      `lucideGamepad2`), lead help (kind and a real example: Friends 1994, Super Mario
      Bros. 1985), FR translations
- [x] Tests: icons and help for 9 categories, a round offering the nine

### Verification Plan
- `pnpm test`, `pnpm lint`, `pnpm build`

### Phase Summary
Done 2026-11-23. 508 specs pass, lint clean.

## Final Recap
Docs: `architecture.md` (categories, the leak accepted), `scripts/hints/README.md`
(sources). Reviews: architecture-reviewer, mirrors the literature PR in every layer, 🟡
stale « 7 » comments on the reveal commands fixed. code-reviewer, nothing blocking: its
stale-comment 🟡 the same; its claim that `Q172067` is « eroticism » contradicts Wikidata
(label « hentai » when queried), left; mature but mainstream titles (Postal, Manhunt,
Leisure Suit Larry, Jackass, Nip/Tuck) left to the user's editorial call. `./mvnw test`
and `pnpm test` pass.

## Deployment Plan
1. Merge (stacked on `feature/sticky-header`), release to main; Flyway applies V30
   (constraints only).
2. On the VPS, an ordinary import (no `retire-absent`: the other files did not change):
   `docker compose -f docker-compose.prod.yml run --rm backend --spring.profiles.active=prod,import-hints`
   Expected: `added` about 1 917, `refused={}`.
3. `/admin/hints`: Television and Video games columns; in a round on a recent photo,
   nine leads in a 3 × 3 grid.

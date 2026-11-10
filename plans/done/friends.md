# Friends (Semaine 6)

A signed-in player adds friends, either by their exact username or with a personal
invite link. They see the list of their friends and their pending requests, compare
today's daily game scores with their friends once their own game is finished, and see a
leaderboard of friends ranked by average score per game. One PR, branch `feature/friends`.

User decisions (2026-11-10):
- **Adding a friend**: exact username (no partial search, so the accounts cannot be
  listed), or a personal invite link that the friend opens and accepts in one click.
- **Leaderboard**: average points per game.
- **Today's comparison**: friends' daily total and points per round, shown only once my
  own daily game is complete. No access to a friend's full history or profile.

Rules confirmed by the user (2026-11-10, after the first draft):
- **Leaderboard ranking**: plain average, no minimum number of games (a minimum was
  judged too complicated for what it brings). A « game » and the average follow the
  profile's definitions (`HistoryCalculator`: complete days with a maximum above 0).
  Ties: more games first, then username. A player with no game yet is listed last,
  without an average.
- **Declining is silent**: declining or cancelling deletes the request. The sender is
  not told, the request just disappears from their list, and they can send a new one.
  No block list for now.
- **Limits**: at most 200 friends, at most 50 outgoing pending requests, and a `friends`
  rate limit of 30 per hour per IP on sending a request and on reading or accepting an
  invite code (a script must not test usernames or codes in bulk).
- **Who can be found**: only accounts with a verified email. An unknown username, an
  unverified account and oneself all give the same 404 « No player with this
  username ».
- **Crossing requests are not merged**: if B already asked A, A asking B gets a 409
  « This player already sent you a request », and the frontend points to the incoming
  request to accept. Nothing is accepted without an explicit click.
- **A friend's unfinished day**: shown as « In progress », without points, until their
  day is complete. A friend who has not played is « Not played yet ».
- **No cache**: today's comparison and the leaderboard are computed on every read (200
  friends × 3 rounds a day, in memory: a few milliseconds). Redis stays what it is today,
  the store of the rate limits (`RateLimiter`).

## Architecture decisions

- **New feature `friends`** (backend package `app.ageguessr.features.friends`, frontend
  folder `frontend/src/app/features/friends/`). It owns the graph of relations.
- **One table `friendships`** (V18) rather than two (`friend_requests` + `friendships`):
  a request and a friendship are the same pair at two statuses (`PENDING`, then
  `ACCEPTED`). Columns: `id`, `requester_id`, `addressee_id` (both
  `REFERENCES users ON DELETE CASCADE`, so deleting an account removes its friendships),
  `status`, `created_at`, `accepted_at`. Unique index on
  `(LEAST(requester_id, addressee_id), GREATEST(...))`: one relation per pair whatever its
  direction, even on two simultaneous clicks. `CHECK (requester_id <> addressee_id)`.
- **Invite codes** in `friend_invites` (V18): `user_id` PK (`ON DELETE CASCADE`), `code`
  unique (8 characters base32 Crockford, `SecureRandom`, like `ShareCodes`),
  `created_at`. Created on first read, regenerated on demand (the old link stops
  working). Accepting an invite creates an `ACCEPTED` friendship directly: sharing the
  link is the consent of its owner.
- **Actions by relation id, never by user id**: the frontend gets the `friendships.id`
  of each row and the friend's username, never their internal `users.id` or email.
- **Usernames read by native SQL on `users`**, like the challenge leaderboard: `friends`
  does not import `auth` (precedent of `game`, which does not either).
- **New ArchUnit exception `friends → game`**: today's comparison and the leaderboard
  need the game's rules (complete day, maximum, neutralized round, average, streak).
  They are written once: `HistoryCalculator` moves from `game/GetHistory` (package-
  private) to a public `game/PlayerHistory.java`, used by `GetHistory` and by `friends`.
  `game` never imports `friends`. Documented in `docs/decisions/architecture.md`
  (« Dépendances entre features » + « Système d'amis » rewritten) and enforced by
  `ArchitectureTests`.
- **Nothing of today's answer leaks**: today's comparison returns points only, never a
  guess, a name or an age, and only once the caller's own day is complete (same rule
  as the profile: every round still playable is finished). Before that, the endpoint
  returns `{ "locked": true }` and no friend data.
- **Frontend: no cross-feature import added.** The day summary links to `/friends` with
  a plain `routerLink`, the header menu gets a « Friends » item (layout already reads
  `Auth`).

## Files

Backend (`ageguessr-api/src/main/java/app/ageguessr/`):
- `resources/db/migration/V18__create_friendships.sql` (+ `friend_invites`)
- `features/friends/Friendship.java`, `FriendshipStatus.java`,
  `FriendshipRepository.java`, `FriendInvite.java`, `FriendInviteRepository.java`,
  `FriendUsers.java` (native queries on `users`: find a verified account by username,
  usernames by ids), `FriendErrors.java`, `FriendLimits.java` (`app.friends.*`
  properties: max friends, max pending)
- Use cases, one package each (controller + handler + command/query + response):
  `ListFriends/` (`GET /api/friends`: friends, incoming, outgoing),
  `SendFriendRequest/` (`POST /api/friends/requests` `{username}`),
  `AcceptFriendRequest/` (`POST /api/friends/requests/{id}/accept`),
  `DeleteFriendRequest/` (`DELETE /api/friends/requests/{id}`: decline or cancel),
  `RemoveFriend/` (`DELETE /api/friends/{id}`),
  `GetInvite/` (`GET /api/friends/invite`), `RegenerateInvite/`
  (`POST /api/friends/invite`), `PreviewInvite/` (`GET /api/friends/invite/{code}`:
  username of the owner), `AcceptInvite/` (`POST /api/friends/invite/{code}/accept`),
  `GetFriendsToday/` (`GET /api/friends/today`), `GetFriendsLeaderboard/`
  (`GET /api/friends/leaderboard`)
- `features/game/PlayerHistory.java` (moved from `GetHistory/HistoryCalculator.java`),
  `features/game/GetHistory/GetHistoryHandler.java` (uses it)
- `shared/ratelimit/RateLimitFilter.java` (`friends` bucket), `config/SecurityConfig`
  (`/api/friends/**` authenticated), `application*.yml` (`app.friends.*`,
  rate limit)
- Tests: `features/friends/**/*Test.java`, `integration/friends/`,
  `functional/friends/FriendsFunctionalTest.java`, `architecture/ArchitectureTests.java`

Frontend (`frontend/src/app/`):
- `features/friends/friends-api.ts`, `models.ts`, `testing.ts`
- `features/friends/friends-page/` (`/friends`, `authGuard`): add by username, invite
  link (copy, regenerate), incoming requests (accept, decline), outgoing (cancel),
  friends (remove, with confirmation), today's comparison, leaderboard
- `features/friends/friends-today/`, `features/friends/friends-leaderboard/`
- `features/friends/invite/` (`/friends/invite/:code`): owner's username + « Add as
  friend »; a guest is sent to `/login` and brought back (same `returnUrl` as the
  existing guards)
- `app.routes.ts`, `features/layout/header/header.html` (+ `.ts`, « Friends » menu item
  with the count of incoming requests), `features/game/day-summary/day-summary.html`
  (« Compare with your friends » link when signed in)
- `locale/messages.fr.xlf` (every new string), `features/legal/privacy/*` +
  `legal-dates.ts` (friends see your username, daily scores and average; deleting the
  account deletes the friendships)
- Tests: `*.spec.ts` next to each file, `e2e/component/friends.spec.ts`,
  `e2e/component-fr/friends.spec.ts`, `e2e/journeys/friends.spec.ts`

Docs: `docs/decisions/architecture.md`, `docs/roadmap.md` (Semaine 6 checked, prod checks
added), this plan moved to `plans/done/friends.md` at the end.

## Phase 1: Backend — the friends graph
Status: Complete

- [x] V18 migration: `friendships` (pair index, CHECKs, cascades) and `friend_invites`
- [x] Entities, repositories, `FriendUsers` native queries, `FriendLimits` properties
- [x] `SendFriendRequest`: exact username (case-insensitive, like the unique index), the
      same 404 for unknown, unverified or oneself, already friends → 409, already asked → 409, crossing request → 409
      `INCOMING_REQUEST_EXISTS`, limits → 409, each with a code the frontend translates
- [x] `AcceptFriendRequest` (addressee only), `DeleteFriendRequest` (addressee declines
      or requester cancels), `RemoveFriend` (either side, `ACCEPTED` only); any other
      caller or id → 404, never 403 (does not reveal that the id exists)
- [x] `ListFriends`: friends sorted by username, incoming and outgoing requests, newest
      first; usernames only
- [x] Invites: `GetInvite` (creates on first read), `RegenerateInvite`, `PreviewInvite`,
      `AcceptInvite` (own code → 409, already friends → 200 idempotent, pending request
      between the two → accepted, limits)
- [x] `friends` rate limit (30 per hour per IP), `/api/friends/**` authenticated

Tests:
- Unit (JUnit + Mockito): each handler's rules above (who may act, crossing request
  refused, limits, idempotent invite, 404 for a foreign id)
- Integration (Testcontainers): pair unique index (A→B then B→A insert fails),
  `requester <> addressee`, cascade on account deletion, `FriendUsers` case-insensitive
  lookup that skips unverified accounts
- Functional (`@SpringBootTest`): every endpoint through the real security chain: 401
  without a token, the JSON never contains an email or a `users.id`, the full cycle
  send → list → accept → remove, invite preview → accept, rate limit 429
- Architecture: `friends` does not import `auth`; `game` does not import `friends`
- Mutation: runs on the PR to `main` (informative); the handlers' rules are unit-tested
  so PIT can measure them

### Verification Plan
- `cd ageguessr-api && ./mvnw test` passes (unit, integration, functional, architecture)
- `grep -rn "email\|userId" ageguessr-api/src/main/java/app/ageguessr/features/friends/*/*Response.java`
  finds nothing

### Phase Summary
Done 2026-11-10. V18 (`friendships` with the pair index, `friend_invites`), entities,
`FriendshipRepository`, `FriendUsers` (native SQL on `users`, extracted after the architecture review),
`FriendLimits`, `FriendInviteCodes` (reuses `game/ShareCodes`, so the `friends → game`
ArchUnit exception landed here already), 9 use cases. `app.friends.*` had to be added to
`src/test/resources/application.properties` too (it replaces the main file in tests).
Verification: `./mvnw test` green (1194 tests with Phase 2), no email or `users.id` in
any response (asserted in `FriendsFunctionalTest`).

## Phase 2: Backend — today's comparison and leaderboard
Status: Complete

- [x] Move `HistoryCalculator` to public `game/PlayerHistory` (same tests, moved to
      `features/game/PlayerHistoryTest.java`), `GetHistory` uses it, no behaviour change
- [x] ArchUnit: allow `friends → game`, forbid anything else from `friends`; update the
      Javadoc of the rule
- [x] `GetFriendsToday`: caller's day complete? else `{locked: true}`. Else, for me and
      each friend: username, status (`COMPLETE`, `IN_PROGRESS`, `NOT_PLAYED`), and for
      `COMPLETE` the total, the maximum and the points per round; sorted by total
- [x] `GetFriendsLeaderboard`: me + friends, average, games played, current streak, rank
      (average, then games, then username; no game → last); one query for all plays of the group, rules in
      `PlayerHistory`
- [x] `docs/decisions/architecture.md`: « Dépendances entre features » (third exception)
      and « Système d'amis » rewritten with this plan's decisions

Tests:
- Unit: a pure `FriendsRanking` (sort by average, ties, no game last, me included),
  today's status per player (complete, in progress, not played, neutralized round)
- Integration: the group plays query returns only the given accounts' daily plays
- Functional: `GET /api/friends/today` locked before my day is complete, then points
  without any guess, name or age (raw JSON checked, like `GameFunctionalTest`); a
  non-friend's scores never appear; leaderboard with 2 friends
- Architecture: the new exception rule
- No QA journey for this phase: covered by the Phase 4 journey

### Verification Plan
- `cd ageguessr-api && ./mvnw test` passes; `GetHistory` tests unchanged and green

### Phase Summary
Done 2026-11-10. `HistoryCalculator` → public `game/PlayerHistory` (git mv, tests moved,
no behaviour change); new `game/PlayerHistories` reads a group in two queries
(`GamePlayRepository.findByUserIdIn`); `GetHistoryHandler` reuses its `toPlay`. Friends:
`FriendGroup` (caller + friends + usernames), pure `TodayStandings` and
`LeaderboardRanking`. Added beyond the plan: `/api/friends/today` and `/leaderboard`
share the `game` rate limit counter with `/api/game/history` (costly reads).
`architecture.md` rewritten (dependencies, Système d'amis, history privacy note).
Verification: `./mvnw test` → 1194 tests, 0 failure.

## Phase 3: Frontend — friends page and invite link
Status: Complete

- [x] `FriendsApi` + models (error codes mapped to translated messages)
- [x] `/friends` page: add by username form (the crossing-request error links to the
      incoming request), invite link (copy with the existing share
      helper, regenerate with confirmation), incoming (accept, decline), outgoing
      (cancel), friends (remove with a CDK confirmation dialog); empty states
- [x] `/friends/invite/:code`: preview, « Add as friend », own link, unknown/expired
      code (« This link no longer works »), guest → login and back
- [x] Header menu « Friends » with the incoming count (screen-reader text), routes with
      `authGuard`
- [x] FR translations of every string in `messages.fr.xlf`, `shared/ui` components only

Tests:
- Unit (Vitest): `FriendsApi`, the page (each action, errors, empty states), invite page
  (preview, accept, guest redirect, bad code), header item and count
- Interface (Playwright component, EN + FR): friends page and invite page on a phone and
  on desktop, with network mocks; run by the CI only (no local Playwright, see memory)
- No backend test here

### Verification Plan
- `cd frontend && pnpm test`, `pnpm lint`, `pnpm format:check` pass
- `pnpm build` passes (FR translations complete)

### Phase Summary
Done 2026-11-10. `FriendsApi` (holds `incomingCount` for the header badge), `friends-text.ts`
(every string + `friendsError`, 404 meaning depends on context), `/friends` page,
`/friends/invite/:code` page, header « Friends » item with the count, FR translations.
Deviations: removal and link replacement are confirmed inline (Yes/No) rather than in a
CDK dialog, lighter for a one-line action; the today and leaderboard components (planned
in Phase 4) were built here as presentational components fed by the page. New front
exceptions: `layout → friends` (badge), `friends → game` (`SHARE_TARGET` for copy/share),
`game → auth` reused for the « Compare with your friends » link. `asLoggedIn` (Playwright
session helper) now answers `/api/friends`, which the header calls once signed in.
Verification: `pnpm test` 414 → all pass (65 files), lint and prettier clean, `ng build`
passes (FR complete). Playwright component specs written (EN + FR), run by the CI only.

## Phase 4: Frontend — comparison, leaderboard, journey, legal
Status: Complete

- [x] `FriendsToday` component on `/friends`: locked message with a link to the daily
      game, then the ranked list (me highlighted, points per round as score dots like
      the day summary)
- [x] `FriendsLeaderboard` component on `/friends`: rank, username, average, games,
      streak; players with no game last, « No game yet »
- [x] Day summary: « Compare with your friends » link to `/friends` when signed in
- [x] Privacy policy EN + FR (what friends see, deletion), date updated
- [x] QA journey `e2e/journeys/friends.spec.ts`: two accounts sign up and confirm, A sends
      a request by username, B accepts, both finish the seeded daily game, A sees B's
      score; and B adds A through A's invite link (the critical path named in
      `docs/testing-strategy.md`). Journeys do not run in CI today: listed as a prod
      check to run by hand
- [x] Reviews: `architecture-reviewer` and `code-reviewer` (user data, new endpoints,
      RGPD), fix what they find
- [x] Roadmap: Semaine 6 checked, prod checks added; plan moved to `plans/done/friends.md`

Tests:
- Unit (Vitest): today (locked, statuses, sort, me highlighted), leaderboard (order, no
  game yet), day summary link (signed in / guest), privacy page text
- Interface (Playwright component, EN + FR): today and leaderboard on a phone
- QA journey above
- No new backend test

### Verification Plan
- `cd ageguessr-api && ./mvnw test` and `cd frontend && pnpm test && pnpm lint && pnpm format:check && pnpm build` pass
- CI green on the PR (Playwright component EN + FR)

### Phase Summary
Done 2026-11-10. Privacy policy EN + FR (what friends see, retention, rate limits), date
2026-11-10, spec added. QA journey added at the end of `e2e/journeys/daily-game.spec.ts`
rather than a new `friends.spec.ts`: it seeds today's game, and the seeding tests must run
serially in one file. Reviews: code-reviewer found nothing blocking; two rate-limit gaps
fixed (every non-GET `/api/friends*` now on the `friends` counter, test added); the timing
difference of the username lookup is accepted (authenticated, 30/h, same 404).
architecture-reviewer found nothing blocking; fixed: `SHARE_TARGET` moved to
`shared/share-target.ts`, real HTTP error forwarded on the invite load, `FriendUsers`
extracted from `FriendshipRepository`, doc line wrapped. Roadmap: Semaine 6 checked, prod
checks added. Verification: `./mvnw test` 1195 tests green; `pnpm test` 415 green, lint,
prettier and build clean; Playwright component specs run by the CI.

## Edge cases
- Request to oneself, to an unknown or unverified username → same 404
- Request already pending either way, already friends → 409 (crossing request: its own
  code, never accepted automatically)
- Two simultaneous requests A→B and B→A → the pair index refuses one, mapped to 409
- Accepting a request that was cancelled meanwhile → 404
- Limits reached (200 friends, 50 pending) on send, accept and invite
- Invite: own code, regenerated (old code 404), owner deleted, already friends
- Account deletion → friendships and invite gone (cascade), friends no longer see it
- Username changed → friends see the new one (read at each request, never stored)
- Today: my day not complete → locked; a friend's neutralized round; a friend who
  played as a guest then signed in (plays already claimed by `ClaimGuestPlays`)
- Leaderboard: no friend yet (only me, with an invitation to add some), nobody has
  played

## Final Recap
Friends shipped end to end in one PR: V18 graph (one row per pair), 11 endpoints under
`/api/friends`, today's comparison locked until the caller's day is complete (points only),
leaderboard by plain average, invite links, header badge, FR translations, privacy policy.
Game rules shared through the public `game/PlayerHistory` + `PlayerHistories` (ArchUnit
exception `friends → game`). Known limit: a guest who opens an invite link and has no
account yet goes to the login page; registering from there does not bring them back to the
invite (the register page has no `returnUrl`).

## Deployment Plan
1. PR `feature/friends` → `develop` (CI: backend, frontend, Playwright component EN + FR).
2. Release PR `develop` → `main` with a **merge commit**; the mutation job runs there.
3. Deploy is automatic (GHCR images, VPS). Flyway applies V18 at API start: two new tables,
   no change to existing ones, nothing to back-fill.
4. New optional env vars (defaults are fine): `FRIENDS_RATE_LIMIT_MAX_ATTEMPTS` (30),
   `FRIENDS_RATE_LIMIT_WINDOW_MINUTES` (60), `FRIENDS_MAX_FRIENDS` (200),
   `FRIENDS_MAX_PENDING_REQUESTS` (50).
5. Prod checks by hand: listed in `docs/roadmap.md` (« Amis » lines), plus the friends QA
   journey to run against a throwaway stack.

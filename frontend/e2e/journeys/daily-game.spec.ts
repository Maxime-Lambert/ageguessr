import { expect, test } from '@playwright/test';
import { BACKEND_URL, logIn, signUp, uniqueAccount } from './support';
import { pickLeads } from '../component/game-mocks';

// Both tests seed today's game, which deletes today's plays: they must not run at once.
test.describe.configure({ mode: 'serial' });

/**
 * Today's game against the full app, as a guest: the e2e-profiled backend replaces
 * today's rounds with three known people (GameTestSupportController), aged 40, 55 and 30.
 */
test('a guest plays the three rounds of the day, through a reload, to the case report', async ({
  page,
  request,
}) => {
  const seeded = await request.post(`${BACKEND_URL}/test-support/seed-daily-game`);
  expect(seeded.ok()).toBe(true);
  expect((await seeded.json()).ages).toEqual([40, 55, 30]);

  await page.goto('/en/');
  const age = page.getByLabel('Age');

  // Round 1: a wrong guess, a hint, then a reload in the middle of the round.
  await expect(page.getByRole('heading', { name: 'Round 1 of 3' })).toBeVisible();
  await age.fill('30');
  await page.getByRole('button', { name: 'Guess', exact: true }).click();
  await expect(page.getByText('Older than 30, by 6 to 15 years')).toBeVisible();
  // The seed offers three categories: all three are picked.
  await pickLeads(page);
  const hint = page.getByText(/E2E MUSIC hint|Indice E2E MUSIC/);
  await expect(hint).toBeVisible();

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Round 1 of 3' })).toBeVisible();
  await expect(page.getByText('Older than 30, by 6 to 15 years')).toBeVisible();
  await expect(hint).toBeVisible();
  await expect(page.getByText(/E2E SPORT hint|Indice E2E SPORT/)).toBeVisible();

  await age.fill('40');
  await page.getByRole('button', { name: 'Final guess' }).click();
  await expect(page.getByRole('link', { name: /E2E 1$|E2E Person 1/ })).toBeVisible();
  await expect(page.getByText('E2E Photographer')).toBeVisible();
  await page.getByRole('button', { name: 'Next round' }).click();

  // Round 2: an exact first guess closes the round at once.
  await expect(page.getByRole('heading', { name: 'Round 2 of 3' })).toBeVisible();
  await age.fill('55');
  await page.getByRole('button', { name: 'Guess', exact: true }).click();
  await expect(page.getByText('Spot on, first try.')).toBeVisible();
  await page.getByRole('button', { name: 'Next round' }).click();

  // Round 3, then the report: 184 + 500, 1 000, 184 + 500.
  await expect(page.getByRole('heading', { name: 'Round 3 of 3' })).toBeVisible();
  await age.fill('20');
  await page.getByRole('button', { name: 'Guess', exact: true }).click();
  await pickLeads(page);
  await age.fill('30');
  await page.getByRole('button', { name: 'Final guess' }).click();
  await page.getByRole('button', { name: 'See the summary' }).click();

  await expect(page.getByRole('heading', { name: 'Summary' })).toBeVisible();
  await expect(page.getByText('2368 out of 3000')).toBeVisible();

  // The day is played: coming back shows the report, not a new round.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Summary' })).toBeVisible();
});

test('a guest who then signs up finds the day on the profile, with a streak of 1', async ({
  page,
  request,
}) => {
  const seeded = await request.post(`${BACKEND_URL}/test-support/seed-daily-game`);
  expect(seeded.ok()).toBe(true);

  // Three exact first guesses, as a guest: a complete day at 3 000.
  await page.goto('/en/');
  const age = page.getByLabel('Age');
  for (const [index, years] of [40, 55, 30].entries()) {
    await expect(page.getByRole('heading', { name: `Round ${index + 1} of 3` })).toBeVisible();
    await age.fill(String(years));
    await page.getByRole('button', { name: 'Guess', exact: true }).click();
    await expect(page.getByText('Spot on, first try.')).toBeVisible();
    await page.getByRole('button', { name: index < 2 ? 'Next round' : 'See the summary' }).click();
  }
  await expect(page.getByText('3000 out of 3000')).toBeVisible();

  // Signing up then logging in, on the same browser: the guest's day joins the account.
  const account = uniqueAccount('history');
  await signUp(page, request, account);
  await logIn(page, account.email, account.password);
  await expect(
    page.getByRole('banner').getByRole('button', { name: account.username }),
  ).toBeVisible();

  await page.goto('/en/profile');
  await expect(page.getByRole('heading', { name: account.username })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Current streak' })).toBeVisible();
  const streak = page.locator('section[aria-labelledby="streak-title"]');
  await expect(streak).toContainText('1');
  const day = page.getByRole('article');
  await expect(day).toHaveCount(1);
  await expect(day).toContainText('3,000 out of 3,000');
  await expect(day).toContainText('E2E');
});

/**
 * Sign-up → friend request → today's comparison (the critical path
 * of docs/testing-strategy.md). In this file because it seeds today's game too, and the
 * seeding tests must run one after the other. Two browsers, one per player.
 */
test('two players become friends and compare the day', async ({ browser, request }) => {
  const seeded = await request.post(`${BACKEND_URL}/test-support/seed-daily-game`);
  expect(seeded.ok()).toBe(true);
  // A new context does not inherit the project's options: pass its base URL on.
  const player = async () =>
    (await browser.newContext({ baseURL: test.info().project.use.baseURL })).newPage();
  const alice = await player();
  const bob = await player();
  const aliceAccount = uniqueAccount('alice');
  const bobAccount = uniqueAccount('bob');
  await signUp(alice, request, aliceAccount);
  await logIn(alice, aliceAccount.email, aliceAccount.password);
  await signUp(bob, request, bobAccount);
  await logIn(bob, bobAccount.email, bobAccount.password);

  // Alice asks Bob by his exact username; Bob sees the count in his menu and accepts.
  await alice.goto('/en/friends');
  await alice.locator('#friend-username').fill(bobAccount.username);
  await alice.getByRole('button', { name: 'Send the request' }).click();
  await expect(alice.getByText(`Request sent to ${bobAccount.username}.`)).toBeVisible();

  await bob.goto('/en/');
  await bob.getByRole('banner').getByRole('button', { name: bobAccount.username }).click();
  await bob.getByRole('link', { name: /Friends/ }).click();
  const incoming = bob.locator('[data-incoming]');
  await expect(incoming).toContainText(aliceAccount.username);
  await incoming.getByRole('button', { name: 'Accept' }).click();
  await expect(bob.locator('[data-friend]')).toContainText(aliceAccount.username);

  // Locked for Alice until her own day is done; Bob plays first.
  await alice.reload();
  await expect(alice.getByText("Finish today's game to see your friends' scores.")).toBeVisible();
  for (const playing of [bob, alice]) {
    await playing.goto('/en/');
    for (const [index, years] of [40, 55, 30].entries()) {
      await expect(playing.getByRole('heading', { name: `Round ${index + 1} of 3` })).toBeVisible();
      await playing.getByLabel('Age').fill(String(years));
      await playing.getByRole('button', { name: 'Guess', exact: true }).click();
      await playing
        .getByRole('button', { name: index < 2 ? 'Next round' : 'See the summary' })
        .click();
    }
  }
  await alice.getByRole('link', { name: 'Compare with your friends' }).click();
  const today = alice.locator('section[aria-labelledby="friends-today-title"]');
  await expect(today).toContainText(bobAccount.username);
  await expect(today).toContainText('3,000 out of 3,000');
});

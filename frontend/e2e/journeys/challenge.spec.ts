import { Page, expect, test } from '@playwright/test';
import { BACKEND_URL } from './support';

/**
 * Plays the round on screen to its end, whatever the age of its photo: a guess of 40,
 * then, unless it was exact, the first lead offered and 40 again. The series draws its
 * photos at random from the whole pool, so the journey cannot know them in advance.
 */
async function playRound(page: Page): Promise<void> {
  const closed = page.getByRole('button', { name: /^(Next case|See the case report)$/ });
  const finalGuess = page.getByRole('button', { name: 'Final guess' });
  const lead = page.getByText('Pick a lead');
  await page.getByLabel('Age').fill('40');
  await page.getByRole('button', { name: 'Guess', exact: true }).click();
  await expect(closed.or(finalGuess).or(lead)).toBeVisible();
  if (await lead.isVisible()) {
    await page
      .getByRole('button', { name: /^(Music|Film)$/ })
      .first()
      .click();
  }
  if (!(await closed.isVisible())) {
    await page.getByLabel('Age').fill('40');
    await finalGuess.click();
  }
  await closed.click();
}

async function playSeries(page: Page, rounds: number): Promise<void> {
  for (let i = 1; i <= rounds; i++) {
    await expect(page.getByRole('heading', { name: `Case ${i} of ${rounds}` })).toBeVisible();
    await playRound(page);
  }
  await expect(page.getByRole('heading', { name: 'Case report' })).toBeVisible();
}

/**
 * A free series against the full app, then the challenge: its creator shares it, a friend
 * (another browser, so another guest) takes it up, and both end up on the leaderboard.
 */
test('a guest plays a series, challenges a friend, and both see the leaderboard', async ({
  page,
  browser,
  request,
}) => {
  const seeded = await request.post(`${BACKEND_URL}/test-support/seed-free-pool`);
  expect(seeded.ok()).toBe(true);

  await page.goto('/en/free');
  await page.getByRole('button', { name: '5 cases' }).click();
  await playSeries(page, 5);

  await page.getByRole('button', { name: 'Challenge friends' }).click();
  await page.getByLabel('Your name in the challenge').fill('Sherlock');
  await page.getByRole('button', { name: 'Create the link' }).click();
  const link = await page.getByLabel('Link to the challenge').inputValue();
  const code = link.match(/\/challenge\/([0-9A-Z]{8})$/)?.[1];
  expect(code).toBeTruthy();

  // The link carries no language: Caddy picks the friend's. In dev, the English server.
  const friendContext = await browser.newContext({ baseURL: test.info().project.use.baseURL });
  const friend = await friendContext.newPage();
  await friend.goto(`/en/challenge/${code}`);
  await expect(friend.getByText('Sherlock challenges you on 5 cases.')).toBeVisible();
  await friend.getByLabel('Your name in the challenge').fill('Watson');
  await friend.getByRole('button', { name: 'Take up the challenge' }).click();
  await playSeries(friend, 5);

  await expect(friend.getByRole('heading', { name: 'Leaderboard' })).toBeVisible();
  await expect(friend.getByRole('row', { name: /Sherlock/ })).toBeVisible();
  await expect(friend.getByRole('row', { name: /Watson/ })).toContainText('you');

  // One try each: opening the link again shows the leaderboard, not a new series.
  await friend.reload();
  await expect(friend.getByRole('heading', { name: 'Leaderboard' })).toBeVisible();
  await friendContext.close();
});

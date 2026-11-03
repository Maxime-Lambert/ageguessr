import { expect, test } from '@playwright/test';
import { BACKEND_URL } from './support';

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
  await expect(page.getByRole('heading', { name: 'Case 1 of 3' })).toBeVisible();
  await age.fill('30');
  await page.getByRole('button', { name: 'Guess', exact: true }).click();
  await expect(page.getByText('Older than 30')).toBeVisible();
  await page.getByRole('button', { name: 'Music' }).click();
  const hint = page.getByText(/E2E MUSIC hint|Indice E2E MUSIC/);
  await expect(hint).toBeVisible();

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Case 1 of 3' })).toBeVisible();
  await expect(page.getByText('Older than 30')).toBeVisible();
  await expect(hint).toBeVisible();

  await age.fill('40');
  await page.getByRole('button', { name: 'Final guess' }).click();
  await expect(page.getByRole('link', { name: /E2E 1$|E2E Person 1/ })).toBeVisible();
  await expect(page.getByText('E2E Photographer')).toBeVisible();
  await page.getByRole('button', { name: 'Next case' }).click();

  // Round 2: an exact first guess closes the round at once.
  await expect(page.getByRole('heading', { name: 'Case 2 of 3' })).toBeVisible();
  await age.fill('55');
  await page.getByRole('button', { name: 'Guess', exact: true }).click();
  await expect(page.getByText('Spot on, first try.')).toBeVisible();
  await page.getByRole('button', { name: 'Next case' }).click();

  // Round 3, then the report: 184 + 500, 1 000, 184 + 500.
  await expect(page.getByRole('heading', { name: 'Case 3 of 3' })).toBeVisible();
  await age.fill('20');
  await page.getByRole('button', { name: 'Guess', exact: true }).click();
  await page.getByRole('button', { name: 'Film' }).click();
  await age.fill('30');
  await page.getByRole('button', { name: 'Final guess' }).click();
  await page.getByRole('button', { name: 'See the case report' }).click();

  await expect(page.getByRole('heading', { name: 'Case report' })).toBeVisible();
  await expect(page.getByText('2368 out of 3000')).toBeVisible();

  // The day is played: coming back shows the report, not a new round.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Case report' })).toBeVisible();
});

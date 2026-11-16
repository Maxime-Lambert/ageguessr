import { expect, test } from '@playwright/test';
import { BACKEND_URL } from './support';

/**
 * A past day against the full app, as a guest: the e2e-profiled backend seeds yesterday's
 * game with three known people (aged 40, 55 and 30), played from the calendar.
 */
test('a guest catches up on yesterday from the calendar, which then shows it finished', async ({
  page,
  request,
}) => {
  const seeded = await request.post(`${BACKEND_URL}/test-support/seed-daily-game?daysAgo=1`);
  expect(seeded.ok()).toBe(true);
  const { date } = (await seeded.json()) as { date: string };
  const today = new Date().toISOString().slice(0, 7);
  await page.addInitScript(() => localStorage.setItem('ageguessr-rules-seen', '1'));

  await page.goto('/en/calendar');
  await expect(page.getByRole('heading', { name: 'Calendar', level: 1 })).toBeVisible();
  // Yesterday may be in last month, on the 1st.
  if (date.slice(0, 7) !== today) {
    await page.getByRole('button', { name: 'Previous month' }).click();
  }
  const yesterday = page.locator(`[data-day]:has(a[href$="/day/${date}"])`);
  await expect(yesterday).toHaveAttribute('data-state', 'AVAILABLE');
  await yesterday.getByRole('link').click();

  await expect(page).toHaveURL(new RegExp(`/en/day/${date}$`));
  const age = page.getByLabel('Age');
  for (const [index, years] of [40, 55, 30].entries()) {
    await expect(page.getByRole('heading', { name: `Round ${index + 1} of 3` })).toBeVisible();
    await age.fill(String(years));
    await page.getByRole('button', { name: 'Guess', exact: true }).click();
    await expect(page.getByText('Spot on, first try.')).toBeVisible();
    await page.getByRole('button', { name: index < 2 ? 'Next round' : 'See the summary' }).click();
  }
  await expect(page.getByText('3000 out of 3000')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Share my result' })).toHaveCount(0);

  await page.getByRole('link', { name: 'Back to the calendar' }).first().click();
  if (date.slice(0, 7) !== today) {
    await page.getByRole('button', { name: 'Previous month' }).click();
  }
  await expect(page.locator(`[data-day]:has(a[href$="/day/${date}"])`)).toHaveAttribute(
    'data-state',
    'FINISHED',
  );
});

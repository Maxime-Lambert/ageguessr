import { Page, expect, test } from '@playwright/test';
import { USER, asLoggedIn } from './session';

const ADMIN = { ...USER, username: 'Admin', role: 'ADMIN' as const };

function pending(id: string) {
  return {
    hintId: id,
    category: 'SCIENCE',
    year: 1976,
    audience: 'FR',
    textEn: null,
    textFr: `Découverte ${id}.`,
    sourceUrl: `https://en.wikipedia.org/wiki/${id}`,
    sourceKind: 'year-page',
  };
}

const CATEGORIES = ['MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES'];
const COVERAGE = {
  minimum: 5,
  pending: 2,
  gaps: 10,
  rows: [1975, 1976].map((year) => ({
    year,
    cells: CATEGORIES.map((category) => ({
      category,
      fr: category === 'MUSIC' ? 10 : 0,
      en: category === 'MUSIC' ? 10 : 0,
    })),
  })),
};

type Review = { hintId: string; decision: string; textFr?: string };

/** Mocks the admin API; returns the reviews sent, in order. */
async function mockAdminApi(page: Page): Promise<Review[]> {
  const reviews: Review[] = [];
  let firstLoad = true;
  await page.route('**/api/admin/hints/pending**', (route) => {
    const body = firstLoad
      ? { totalPending: 2, items: [pending('h1'), pending('h2')] }
      : { totalPending: 1, items: [] };
    firstLoad = false;
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
  await page.route('**/api/admin/hints/coverage', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(COVERAGE),
    }),
  );
  await page.route('**/api/admin/hints/*/review', (route) => {
    const hintId = route.request().url().split('/').at(-2) ?? '';
    const body = route.request().postDataJSON() as { decision: string; textFr?: string };
    reviews.push({ hintId, ...body });
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        hintId,
        reviewStatus: body.decision,
        textEn: null,
        textFr: body.textFr ?? `Découverte ${hintId}.`,
      }),
    });
  });
  return reviews;
}

test.describe('hint curation', () => {
  test('an admin corrects a hint, approves from the keyboard and can undo', async ({ page }) => {
    await asLoggedIn(page, ADMIN);
    const reviews = await mockAdminApi(page);
    await page.goto('/admin/hints');

    const text = page.getByLabel('French text');
    await expect(text).toHaveValue('Découverte h1.');

    // Letters typed in the text are a correction, never a shortcut.
    await text.fill('Corrigé a r');
    expect(reviews).toEqual([]);
    await text.blur();
    await page.keyboard.press('a');
    await expect(text).toHaveValue('Découverte h2.');
    expect(reviews).toEqual([{ hintId: 'h1', decision: 'APPROVED', textFr: 'Corrigé a r' }]);

    await page.keyboard.press('u');
    await expect(text).toHaveValue('Corrigé a r');
    await expect.poll(() => reviews.at(-1)).toEqual({ hintId: 'h1', decision: 'PENDING' });
  });

  test('shows the coverage and its gaps', async ({ page }) => {
    await asLoggedIn(page, ADMIN);
    await mockAdminApi(page);
    await page.goto('/admin/hints');

    await expect(page.getByRole('heading', { name: 'Coverage' })).toBeVisible();
    await expect(page.getByText('10 gaps')).toBeVisible();
    await expect(page.getByRole('row', { name: /^1976/ })).toContainText('10 / 10');
  });

  test('an admin finds the page from the header', async ({ page }) => {
    await asLoggedIn(page, ADMIN);
    await mockAdminApi(page);
    await page.goto('/');

    await page.getByRole('link', { name: 'Hints' }).click();

    await expect(page).toHaveURL(/\/admin\/hints$/);
  });

  test('a regular user is sent home and never sees the hints link', async ({ page }) => {
    await asLoggedIn(page, USER);
    await page.goto('/admin/hints');

    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('link', { name: 'Hints' })).toHaveCount(0);
  });

  test('fits a phone screen without horizontal scrolling', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await asLoggedIn(page, ADMIN);
    await mockAdminApi(page);
    await page.goto('/admin/hints');
    await expect(page.getByText('10 gaps')).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });
});

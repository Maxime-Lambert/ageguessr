import { Page, expect, test } from '@playwright/test';
import { USER, asLoggedIn } from './session';

const ADMIN = { ...USER, username: 'Admin', role: 'ADMIN' as const };

/** 1x1 PNG: the browser must really decode and paint the blob: image. */
const PIXEL = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
  'base64',
);

function pending(id: string, alive: boolean) {
  return {
    photoId: id,
    person: {
      id: `person-${id}`,
      wikidataId: 'Q1',
      nameEn: `Person ${id}`,
      nameFr: null,
      birthDate: '1950-03-10',
      deathDate: alive ? null : '2010-01-01',
      sitelinks: 42,
    },
    takenFrom: '1976-07-14',
    takenTo: '1976-07-14',
    ageAtPhoto: 26,
    dateSource: 'BOTH',
    commonsFile: `Photo ${id}.jpg`,
    sourceUrl: `https://commons.wikimedia.org/wiki/File:Photo_${id}.jpg`,
    licenseShortName: 'CC BY-SA 4.0',
    licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0',
    authorText: 'Someone',
    otherPhotos: [],
  };
}

/** A photo players reported, in today's game: shown above the pending queue. */
const REPORTED = {
  photoId: 'r1',
  reviewStatus: 'APPROVED',
  ageAtPhoto: 40,
  sourceUrl: 'https://commons.wikimedia.org/wiki/File:r1.jpg',
  person: { id: 'person-r1', wikidataId: 'Q7', nameEn: 'Reported r1', nameFr: null },
  reasons: { WRONG_PERSON: 2 },
  total: 2,
  firstReportedAt: '2026-11-03T08:00:00Z',
  inTodaysGame: true,
};

/** Mocks the admin API; returns the review decisions sent, in order. */
async function mockAdminApi(page: Page): Promise<{ photoId: string; decision: string }[]> {
  const reviews: { photoId: string; decision: string }[] = [];
  let firstLoad = true;
  await page.route('**/api/admin/photos/pending**', (route) => {
    const body = firstLoad
      ? { totalPending: 2, items: [pending('p1', false), pending('p2', true)] }
      : { totalPending: 1, items: [] };
    firstLoad = false;
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    });
  });
  await page.route('**/api/admin/photos/reported', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: [REPORTED] }),
    }),
  );
  await page.route('**/api/admin/photos/*/reports/dismiss', (route) =>
    route.fulfill({ status: 204 }),
  );
  await page.route('**/api/admin/photos/*/image', (route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL }),
  );
  await page.route('**/api/admin/photos/*/review', (route) => {
    const photoId = route.request().url().split('/').at(-2) ?? '';
    const decision = (route.request().postDataJSON() as { decision: string }).decision;
    reviews.push({ photoId, decision });
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ photoId, reviewStatus: decision }),
    });
  });
  return reviews;
}

test.describe('photo curation', () => {
  test('an admin reviews photos from the keyboard and can undo', async ({ page }) => {
    await asLoggedIn(page, ADMIN);
    const reviews = await mockAdminApi(page);
    await page.goto('/en/admin/curation');

    await expect(page.getByRole('heading', { name: 'Person p1' })).toBeVisible();
    const image = page.getByRole('img', { name: 'Photo of Person p1 under review' });
    await expect(image).toHaveAttribute('src', /^blob:/);
    await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBe(1);

    await page.keyboard.press('a');
    await expect(page.getByRole('heading', { name: 'Person p2' })).toBeVisible();
    expect(reviews).toEqual([{ photoId: 'p1', decision: 'APPROVED' }]);

    // A living person is approved like anyone else.
    await page.keyboard.press('a');
    // Shortcuts are ignored while a decision is in flight: wait for its outcome on
    // screen before pressing U, or the undo can be dropped.
    await expect(page.getByText('Nothing left to review.')).toBeVisible();
    expect(reviews[1]).toEqual({ photoId: 'p2', decision: 'APPROVED' });

    await page.keyboard.press('u');
    await expect(page.getByRole('heading', { name: 'Person p2' })).toBeVisible();
    await expect.poll(() => reviews.at(-1)).toEqual({ photoId: 'p2', decision: 'PENDING' });
  });

  test('an admin keeps a reported photo, apart from the pending queue', async ({ page }) => {
    await asLoggedIn(page, ADMIN);
    const reviews = await mockAdminApi(page);
    await page.goto('/en/admin/curation');

    const reported = page.getByRole('region', { name: 'Reported photos' });
    await expect(reported.getByText("In today's game")).toBeVisible();
    await expect(reported.getByText('Wrong person (2)')).toBeVisible();
    await expect(
      reported.getByRole('img', { name: 'Reported photo of Reported r1' }),
    ).toBeVisible();

    await reported.getByRole('button', { name: 'Keep' }).click();

    await expect(reported.getByText('No photo reported.')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Person p1' })).toBeVisible();
    expect(reviews).toEqual([]);
  });

  test('typing in the exclusion search never reviews a photo', async ({ page }) => {
    await asLoggedIn(page, ADMIN);
    const reviews = await mockAdminApi(page);
    await page.goto('/en/admin/curation');
    await expect(page.getByRole('heading', { name: 'Person p1' })).toBeVisible();

    await page.getByLabel('Name or Wikidata ID').fill('ar');

    await expect(page.getByRole('heading', { name: 'Person p1' })).toBeVisible();
    expect(reviews).toEqual([]);
  });

  test('a regular user is sent home and never sees the curation link', async ({ page }) => {
    await asLoggedIn(page, USER);
    await page.goto('/en/admin/curation');

    await expect(page).toHaveURL(/\/en\/$/);
    await expect(page.getByRole('link', { name: 'Curation' })).toHaveCount(0);
  });

  test('fits a phone screen without horizontal scrolling', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await asLoggedIn(page, ADMIN);
    await mockAdminApi(page);
    await page.goto('/en/admin/curation');
    await expect(page.getByRole('heading', { name: 'Person p1' })).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });
});

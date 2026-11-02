import { Page, expect, test } from '@playwright/test';
import { asGuest } from './session';

/** 1x1 PNG: the browser must really decode and paint the photo. */
const PIXEL = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
  'base64',
);

const DATE = '2026-11-02';

function round(position: number, overrides: Record<string, unknown> = {}) {
  return {
    position,
    status: 'PLAYABLE',
    imageUrl: `/api/images/public-${position}`,
    step: 'NOT_STARTED',
    firstGuess: null,
    direction: null,
    categories: null,
    hint: null,
    result: null,
    ...overrides,
  };
}

const RESULT = {
  age: 40,
  firstGuess: 30,
  firstPoints: 184,
  secondGuess: 38,
  secondPoints: 409,
  points: 593,
  person: {
    name: 'Jane Doe',
    wikipediaUrl: 'https://www.wikidata.org/wiki/Special:GoToLinkedPage/enwiki/Q42',
  },
  credit: {
    author: 'Jane Photographer',
    license: 'CC BY-SA 4.0',
    licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0',
    sourceUrl: 'https://commons.wikimedia.org/wiki/File:Jane.jpg',
  },
  hint: {
    category: 'MUSIC',
    text: 'A hit topped the charts.',
    year: 1986,
    sourceUrl: 'https://en.wikipedia.org/wiki/Hit',
  },
};

function json(body: unknown) {
  return { status: 200, contentType: 'application/json', body: JSON.stringify(body) };
}

/** Mocks the game API for one round played to the end; returns the bodies sent, in order. */
async function mockGame(page: Page, rounds = [round(1), round(2), round(3)]): Promise<unknown[]> {
  const sent: unknown[] = [];
  await asGuest(page);
  await page.route('**/api/game/today', (route) =>
    route.fulfill(json({ date: DATE, rounds, score: { points: 0, max: 3000, complete: false } })),
  );
  await page.route('**/api/images/*', (route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL }),
  );
  await page.route(`**/api/game/${DATE}/rounds/*/*`, (route) => {
    const request = route.request();
    const [position, step] = request.url().split('/').slice(-2);
    sent.push(request.postDataJSON());
    const n = Number(position);
    if (step === 'first-guess') {
      return route.fulfill(
        json(
          round(n, {
            step: 'AWAITING_HINT',
            firstGuess: 30,
            direction: 'OLDER',
            categories: ['MUSIC', 'FILM'],
          }),
        ),
      );
    }
    if (step === 'hint') {
      return route.fulfill(
        json(
          round(n, {
            step: 'AWAITING_SECOND_GUESS',
            firstGuess: 30,
            direction: 'OLDER',
            hint: { category: 'MUSIC', text: 'A hit topped the charts.' },
          }),
        ),
      );
    }
    return route.fulfill(json(round(n, { step: 'FINISHED', firstGuess: 30, result: RESULT })));
  });
  return sent;
}

test.describe('daily game', () => {
  test('a guest plays a round from the photo to the verdict', async ({ page }) => {
    const sent = await mockGame(page);
    await page.goto('/');

    const photo = page.getByRole('img', { name: 'The person to guess' });
    await expect(photo).toBeVisible();
    // The archive treatment, which jsdom cannot see: never a photo shown untreated.
    expect(await photo.evaluate((img) => getComputedStyle(img).filter)).toContain('sepia');

    await page.getByLabel('Age').fill('30');
    await page.getByRole('button', { name: 'Guess', exact: true }).click();
    await expect(page.getByText('Older than 30')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Music' })).toBeFocused();

    await page.getByRole('button', { name: 'Music' }).click();
    await expect(page.getByText('A hit topped the charts.')).toBeVisible();
    await expect(page.getByLabel('Age')).toBeFocused();

    await page.getByLabel('Age').fill('38');
    await page.getByRole('button', { name: 'Final guess' }).click();

    await expect(page.getByRole('link', { name: 'Jane Doe' })).toHaveAttribute('target', '_blank');
    await expect(page.getByText('593')).toBeVisible();
    await expect(page.getByText('Jane Photographer')).toBeVisible();
    expect(sent).toEqual([
      { guess: 30, language: expect.stringMatching(/^(EN|FR)$/) },
      { category: 'MUSIC' },
      { guess: 38 },
    ]);

    await page.getByRole('button', { name: 'Next case' }).click();
    await expect(page.getByRole('heading', { name: 'Case 2 of 3' })).toBeVisible();
  });

  test('refuses an age outside 18 to 100 without sending it', async ({ page }) => {
    const sent = await mockGame(page);
    await page.goto('/');

    await page.getByLabel('Age').fill('12');
    await page.getByRole('button', { name: 'Guess', exact: true }).click();

    await expect(page.getByText('Enter a whole age from 18 to 100.')).toBeVisible();
    expect(sent).toEqual([]);
  });

  test('skips a withdrawn photo', async ({ page }) => {
    await mockGame(page, [round(1, { status: 'NEUTRALIZED', imageUrl: null }), round(2)]);
    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Case 2 of 2' })).toBeVisible();
  });

  test.describe('on a phone', () => {
    test.use({ viewport: { width: 375, height: 667 }, hasTouch: true, isMobile: true });

    test('fits the screen with the photo and the guess field', async ({ page }) => {
      await mockGame(page);
      await page.goto('/');

      const photo = page.getByRole('img', { name: 'The person to guess' });
      await expect(photo).toBeVisible();
      await expect(page.getByLabel('Age')).toBeInViewport();
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(0);
      const box = await photo.boundingBox();
      expect(box!.width).toBeLessThanOrEqual(375);
    });
  });
});

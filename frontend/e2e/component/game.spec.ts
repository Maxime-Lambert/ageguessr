import { expect, test } from '@playwright/test';
import { mockGame, round } from './game-mocks';

test.describe('daily game', () => {
  test('a guest plays a round from the photo to the verdict', async ({ page }) => {
    const sent = await mockGame(page);
    await page.goto('/en/');

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
    await page.goto('/en/');

    await page.getByLabel('Age').fill('12');
    await page.getByRole('button', { name: 'Guess', exact: true }).click();

    await expect(page.getByText('Enter a whole age from 18 to 100.')).toBeVisible();
    expect(sent).toEqual([]);
  });

  test('skips a withdrawn photo', async ({ page }) => {
    await mockGame(page, [round(1, { status: 'NEUTRALIZED', imageUrl: null }), round(2)]);
    await page.goto('/en/');

    await expect(page.getByRole('heading', { name: 'Case 2 of 2' })).toBeVisible();
  });

  test.describe('on a phone', () => {
    test.use({ viewport: { width: 375, height: 667 }, hasTouch: true, isMobile: true });

    test('fits the screen with the photo and the guess field', async ({ page }) => {
      await mockGame(page);
      await page.goto('/en/');

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

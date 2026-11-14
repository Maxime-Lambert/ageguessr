import { expect, test } from '@playwright/test';
import { mockChallenge, mockSeries } from './game-mocks';

test.describe('free investigation', () => {
  test.describe('on a phone', () => {
    test.use({ viewport: { width: 375, height: 667 }, hasTouch: true, isMobile: true });

    test('a guest picks a length and plays with the photo and the guess field on screen', async ({
      page,
    }) => {
      const started = await mockSeries(page, 10);
      await page.goto('/en/free');

      await expect(page.getByRole('heading', { name: 'Series', level: 1 })).toBeVisible();
      await page.getByRole('button', { name: '10 rounds' }).click();

      await expect(page.getByRole('img', { name: 'The person to guess' })).toBeVisible();
      await expect(page.getByLabel('Age')).toBeInViewport();
      expect(started).toEqual([{ size: 10 }]);
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(0);
    });

    test('a guest takes up a challenge and sees the leaderboard', async ({ page }) => {
      const joined = await mockChallenge(page, 1);
      await page.goto('/en/challenge/K7F2QX9M');

      await expect(page.getByText('Sherlock challenges you on 1 rounds.')).toBeVisible();
      await page.getByLabel('Your name in the challenge').fill('Watson');
      await page.getByRole('button', { name: 'Take up the challenge' }).click();

      await page.getByLabel('Age').fill('40');
      await page.getByRole('button', { name: 'Guess', exact: true }).click();
      await page.getByRole('button', { name: 'See the summary' }).click();

      await expect(page.getByRole('heading', { name: 'Leaderboard' })).toBeVisible();
      await expect(page.getByRole('row', { name: /Watson/ })).toContainText('1000 / 1000');
      expect(joined).toEqual([{ nickname: 'Watson' }]);
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(0);
    });
  });

  test('is reachable from the header on a wide screen', async ({ page }) => {
    await mockSeries(page);
    await page.setViewportSize({ width: 1024, height: 768 });
    await page.goto('/en/free');

    await page.getByRole('banner').getByRole('link', { name: 'Series' }).click();
    await expect(page).toHaveURL(/\/en\/free$/);
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });
});

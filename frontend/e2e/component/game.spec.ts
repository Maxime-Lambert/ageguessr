import { expect, test } from '@playwright/test';
import { captureShare, mockGame, mockPlayedDay, round, sharedTexts } from './game-mocks';

test.describe('daily game', () => {
  test('shares the result of the day, without any answer', async ({ page, baseURL }) => {
    await captureShare(page);
    await mockPlayedDay(page);
    await page.goto('/en/');

    await page.getByRole('button', { name: 'Share my result' }).click();

    await expect
      .poll(() => sharedTexts(page))
      .toEqual([
        `AgeGuessr · Case of Nov 13, 2026 cracked with flair!\n2340 / 3000 points\n🎯🟩⬛🟥\n🎯 first try · 🟩 very close · 🟥 far off · ⬛ photo withdrawn\nTake on today's case at ${new URL(baseURL!).origin}`,
      ]);
  });

  test('a guest plays a round from the photo to the verdict', async ({ page }) => {
    const sent = await mockGame(page);
    await page.goto('/en/');

    const photo = page.getByRole('img', { name: 'The person to guess' });
    await expect(photo).toBeVisible();
    // The archive treatment, which jsdom cannot see: never a photo shown untreated.
    expect(await photo.evaluate((img) => getComputedStyle(img).filter)).toContain('sepia');

    await page.getByLabel('Age').fill('30');
    await page.getByRole('button', { name: 'Guess', exact: true }).click();
    await expect(page.getByText('Older than 30, by 6 to 15 years')).toBeVisible();
    await expect(page.getByText('Between 36 and 45')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Music', exact: true })).toBeFocused();

    await page.getByRole('button', { name: 'Music', exact: true }).click();
    await page.getByRole('button', { name: 'Film', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Open these leads' })).toBeDisabled();
    await page.getByRole('button', { name: 'Sport', exact: true }).click();
    await expect(page.getByText('3 of 3 picked')).toBeVisible();
    await expect(page.getByRole('button', { name: 'World events', exact: true })).toBeDisabled();
    await page.getByRole('button', { name: 'Open these leads' }).click();
    await expect(page.getByText('A hit topped the charts.')).toBeVisible();
    await expect(page.getByText('A team won the cup.')).toBeVisible();
    await expect(page.getByLabel('Age')).toBeFocused();

    // The final guess starts at the edge of the range nearest the first guess.
    await expect(page.getByLabel('Age')).toHaveValue('36');
    await page.getByLabel('Age').fill('38');
    await page.getByRole('button', { name: 'Final guess' }).click();

    await expect(page.getByRole('link', { name: 'Jane Doe' })).toHaveAttribute('target', '_blank');
    await expect(page.getByText('593')).toBeVisible();
    await expect(page.getByText('Jane Photographer')).toBeVisible();
    expect(sent).toEqual([
      { guess: 30, language: expect.stringMatching(/^(EN|FR)$/) },
      { categories: ['MUSIC', 'FILM', 'SPORT'] },
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

    test('shows the rules on a first visit, then the round once they are dismissed', async ({
      page,
    }) => {
      await mockGame(page);
      // Runs after skipRules' script: this page is a first visit.
      await page.addInitScript(() => localStorage.removeItem('ageguessr-rules-seen'));
      await page.goto('/en/');

      const rules = page.getByRole('region', { name: 'How to play' });
      await expect(rules).toBeVisible();
      await rules.getByRole('button', { name: 'Got it' }).click();

      await expect(rules).toBeHidden();
      await expect(page.getByLabel('Age')).toBeInViewport();
    });
  });
});

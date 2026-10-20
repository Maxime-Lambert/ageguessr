import { expect, test } from '@playwright/test';
import { asGuest, asLoggedIn } from './session';

test.describe('header', () => {
  test('a guest can reach log in and sign up', async ({ page }) => {
    await asGuest(page);
    await page.goto('/');

    const header = page.getByRole('banner');
    await header.getByRole('link', { name: 'Sign up' }).click();
    await expect(page).toHaveURL(/\/register$/);

    await header.getByRole('link', { name: 'Log in' }).click();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('a logged-in user sees their username, linking to the account page', async ({ page }) => {
    await asLoggedIn(page);
    await page.goto('/');

    await expect(page.getByRole('banner').getByRole('link', { name: 'Log in' })).toHaveCount(0);
    await page.getByRole('link', { name: 'Account settings for Player' }).click();
    await expect(page).toHaveURL(/\/account$/);
  });

  test('logging out turns the header back to guest mode', async ({ page }) => {
    await asLoggedIn(page);
    await page.route('**/api/auth/logout', (route) => route.fulfill({ status: 204, body: '' }));
    await page.goto('/');

    await page.getByRole('button', { name: 'Log out' }).click();

    await expect(page.getByRole('banner').getByRole('link', { name: 'Log in' })).toBeVisible();
  });

  test('fits a phone screen without horizontal scrolling, even with a 20-character username', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await asLoggedIn(page, {
      id: '1',
      email: 'me@x.com',
      username: 'A_very-long_name_20',
      emailVerified: true,
    });
    await page.goto('/');
    await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });
});

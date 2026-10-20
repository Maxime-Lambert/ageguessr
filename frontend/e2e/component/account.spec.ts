import { expect, test } from '@playwright/test';
import { USER, asGuest, asLoggedIn } from './session';

test.describe('account page', () => {
  test('a guest is sent to log in, and comes back after', async ({ page }) => {
    await asGuest(page);

    await page.goto('/account');

    await expect(page).toHaveURL(/\/login\?returnUrl=%2Faccount$/);
  });

  test('a hard reload while logged in stays on the account page', async ({ page }) => {
    await asLoggedIn(page);

    await page.goto('/account');

    await expect(page.getByRole('heading', { name: 'Your account' })).toBeVisible();
    await expect(page.locator('#username')).toHaveValue('Player');
  });

  test('renaming updates the header', async ({ page }) => {
    await asLoggedIn(page);
    await page.route('**/api/auth/me/username', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...USER, username: 'NewName' }),
      }),
    );

    await page.goto('/account');
    await page.locator('#username').fill('NewName');
    await page.getByRole('button', { name: 'Save username' }).click();

    await expect(page.getByText('Username updated.')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Account settings for NewName' })).toBeVisible();
  });

  test('requesting a deletion asks to confirm by email', async ({ page }) => {
    await asLoggedIn(page);
    await page.route('**/api/auth/me/deletion', (route) =>
      route.fulfill({ status: 202, body: '' }),
    );

    await page.goto('/account');
    await page.locator('#deletionPassword').fill('tortoise-violin-42');
    await page.getByRole('button', { name: 'Delete my account' }).click();

    await expect(page.getByText('We sent a confirmation link to')).toBeVisible();
  });
});

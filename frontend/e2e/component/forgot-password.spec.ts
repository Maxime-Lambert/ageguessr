import { expect, test } from '@playwright/test';
import { asGuest } from './session';

test.describe('forgot-password page', () => {
  test('is reachable from the login page', async ({ page }) => {
    await asGuest(page);
    await page.goto('/login');

    await page.getByRole('link', { name: 'Forgot password?' }).click();

    await expect(page.getByRole('heading', { name: 'Forgot your password?' })).toBeVisible();
  });

  test('shows the same neutral confirmation whatever the email', async ({ page }) => {
    await asGuest(page);
    await page.route('**/api/auth/forgot-password', (route) =>
      route.fulfill({ status: 204, body: '' }),
    );

    await page.goto('/forgot-password');
    await page.locator('#email').fill('me@x.com');
    await page.getByRole('button', { name: 'Send reset link' }).click();

    await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible();
    await expect(page.getByText('If an account exists for')).toBeVisible();
  });
});

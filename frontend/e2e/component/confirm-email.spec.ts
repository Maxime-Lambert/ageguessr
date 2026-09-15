import { expect, test } from '@playwright/test';

test.describe('confirm-email page', () => {
  test('shows success once the backend confirms the token', async ({ page }) => {
    await page.route('**/api/auth/confirm-email', (route) =>
      route.fulfill({ status: 204, body: '' }),
    );

    await page.goto('/confirm-email?token=valid-token');

    await expect(page.getByRole('heading', { name: 'Email confirmed' })).toBeVisible();
  });

  test('shows an error when the token is rejected', async ({ page }) => {
    await page.route('**/api/auth/confirm-email', (route) =>
      route.fulfill({ status: 404, contentType: 'application/json', body: '{}' }),
    );

    await page.goto('/confirm-email?token=expired-token');

    await expect(page.getByRole('heading', { name: 'Link invalid or expired' })).toBeVisible();
  });

  test('shows an error immediately when there is no token in the URL', async ({ page }) => {
    await page.goto('/confirm-email');

    await expect(page.getByRole('heading', { name: 'Link invalid or expired' })).toBeVisible();
  });
});

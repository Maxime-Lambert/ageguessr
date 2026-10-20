import { expect, test } from '@playwright/test';
import { asGuest } from './session';

test.describe('confirm-account-deletion page', () => {
  test('opening the link alone deletes nothing', async ({ page }) => {
    await asGuest(page);
    let called = false;
    await page.route('**/api/auth/confirm-account-deletion', (route) => {
      called = true;
      return route.fulfill({ status: 204, body: '' });
    });

    await page.goto('/confirm-account-deletion?token=the-token');
    await expect(page.getByRole('heading', { name: 'Delete your account?' })).toBeVisible();
    await page.waitForLoadState('networkidle');

    expect(called).toBe(false);
  });

  test('deletes after the explicit click', async ({ page }) => {
    await asGuest(page);
    await page.route('**/api/auth/confirm-account-deletion', (route) =>
      route.fulfill({ status: 204, body: '' }),
    );

    await page.goto('/confirm-account-deletion?token=the-token');
    await page.getByRole('button', { name: 'Delete my account permanently' }).click();

    await expect(page.getByRole('heading', { name: 'Account deleted' })).toBeVisible();
  });
});

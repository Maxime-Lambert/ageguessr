import { expect, test } from '@playwright/test';

test.describe('login form', () => {
  test('password input is masked', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('#password')).toHaveAttribute('type', 'password');
  });

  test('shows a generic error on invalid credentials', async ({ page }) => {
    await page.route('**/api/auth/login', (route) =>
      route.fulfill({ status: 401, contentType: 'application/json', body: '{}' }),
    );

    await page.goto('/login');
    await page.locator('#email').fill('user@x.com');
    await page.locator('#password').fill('wrong-password');
    await page.getByRole('button', { name: 'Log in' }).click();

    await expect(page.getByText('Invalid email or password.')).toBeVisible();
  });

  test('offers to resend the verification email on an unverified account', async ({ page }) => {
    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ errorCode: 'EMAIL_NOT_VERIFIED', message: 'nope' }),
      }),
    );
    await page.route('**/api/auth/resend-verification', (route) =>
      route.fulfill({ status: 202, contentType: 'application/json', body: '{}' }),
    );

    await page.goto('/login');
    await page.locator('#email').fill('unverified@x.com');
    await page.locator('#password').fill('password123');
    await page.getByRole('button', { name: 'Log in' }).click();

    const resendButton = page.getByRole('button', { name: 'Resend verification email' });
    await expect(resendButton).toBeVisible();
    await resendButton.click();

    await expect(page.getByText('Verification email sent')).toBeVisible();
  });
});

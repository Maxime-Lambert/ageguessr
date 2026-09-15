import { expect, test } from '@playwright/test';

test.describe('register form', () => {
  test('shows an error when the passwords do not match', async ({ page }) => {
    await page.goto('/register');
    await page.locator('#email').fill('user@x.com');
    await page.locator('#password').fill('password123');
    await page.locator('#confirmPassword').fill('different123');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page.getByText("Passwords don't match.")).toBeVisible();
  });

  test('shows a duplicate-email message on 409', async ({ page }) => {
    await page.route('**/api/auth/register', (route) =>
      route.fulfill({ status: 409, contentType: 'application/json', body: '{}' }),
    );

    await page.goto('/register');
    await page.locator('#email').fill('taken@x.com');
    await page.locator('#password').fill('password123');
    await page.locator('#confirmPassword').fill('password123');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page.getByText('An account with this email already exists.')).toBeVisible();
  });

  test('navigates to check-email on success', async ({ page }) => {
    await page.route('**/api/auth/register', (route) =>
      route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ userId: '1', email: 'user@x.com' }),
      }),
    );

    await page.goto('/register');
    await page.locator('#email').fill('user@x.com');
    await page.locator('#password').fill('password123');
    await page.locator('#confirmPassword').fill('password123');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page).toHaveURL(/\/check-email/);
    await expect(page.getByText('user@x.com')).toBeVisible();
  });
});

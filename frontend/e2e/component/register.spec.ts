import { expect, test } from '@playwright/test';

test.describe('register form', () => {
  test('shows an error when the passwords do not match', async ({ page }) => {
    await page.goto('/register');
    await page.locator('#email').fill('user@x.com');
    await page.locator('#username').fill('Player');
    await page.locator('#password').fill('tortoise-violin-42');
    await page.locator('#confirmPassword').fill('different-violin-42');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page.getByText("Passwords don't match.")).toBeVisible();
  });

  test('shows a duplicate-email message on 409', async ({ page }) => {
    await page.route('**/api/auth/register', (route) =>
      route.fulfill({ status: 409, contentType: 'application/json', body: '{}' }),
    );

    await page.goto('/register');
    await page.locator('#email').fill('taken@x.com');
    await page.locator('#username').fill('Player');
    await page.locator('#password').fill('tortoise-violin-42');
    await page.locator('#confirmPassword').fill('tortoise-violin-42');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page.getByText('An account with this email already exists.')).toBeVisible();
  });

  test('navigates to check-email on success', async ({ page }) => {
    await page.route('**/api/auth/register', (route) =>
      route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ userId: '1', email: 'user@x.com', username: 'Player' }),
      }),
    );

    await page.goto('/register');
    await page.locator('#email').fill('user@x.com');
    await page.locator('#username').fill('Player');
    await page.locator('#password').fill('tortoise-violin-42');
    await page.locator('#confirmPassword').fill('tortoise-violin-42');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page).toHaveURL(/\/check-email/);
    await expect(page.getByText('user@x.com')).toBeVisible();
  });

  test('shows the username format right under the field', async ({ page }) => {
    await page.goto('/register');
    await page.locator('#username').fill('no spaces');
    await page.locator('#password').click();

    await expect(page.getByText('3 to 20 characters: letters, digits, _ or -.')).toBeVisible();
  });

  test('flags a taken username on the username field', async ({ page }) => {
    await page.route('**/api/auth/register', (route) =>
      route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({ errorCode: 'USERNAME_TAKEN', message: 'taken' }),
      }),
    );

    await page.goto('/register');
    await page.locator('#email').fill('user@x.com');
    await page.locator('#username').fill('Taken');
    await page.locator('#password').fill('tortoise-violin-42');
    await page.locator('#confirmPassword').fill('tortoise-violin-42');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page.getByText('This username is already taken.')).toBeVisible();
  });

  test('shows a too-common password under the password field', async ({ page }) => {
    await page.route('**/api/auth/register', (route) =>
      route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({ errorCode: 'PASSWORD_TOO_COMMON', message: 'too common' }),
      }),
    );

    await page.goto('/register');
    await page.locator('#email').fill('user@x.com');
    await page.locator('#username').fill('Player');
    await page.locator('#password').fill('qwerty123456');
    await page.locator('#confirmPassword').fill('qwerty123456');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page.getByText('This password is too common.')).toBeVisible();

    await page.locator('#password').fill('tortoise-violin-42');
    await expect(page.getByText('At least 12 characters. Avoid common passwords.')).toBeVisible();
  });

  test('flags a password shorter than 12 characters once the field is left', async ({ page }) => {
    await page.goto('/register');
    await page.locator('#password').fill('elevenchars');
    await page.locator('#confirmPassword').click();

    await expect(
      page.locator('p.text-danger', { hasText: 'At least 12 characters.' }),
    ).toBeVisible();
  });
});

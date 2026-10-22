import { APIRequestContext, Page, expect } from '@playwright/test';

export const BACKEND_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

/**
 * No real inbox in this environment - the e2e-profiled backend records emails instead
 * of sending them (TestSupportController, gated by the "e2e" Spring profile, never
 * active in the prod deployment config). Returns the token of the last email sent.
 */
export async function lastEmailToken(request: APIRequestContext, email: string): Promise<string> {
  const response = await request.get(
    `${BACKEND_URL}/test-support/last-email-token?email=${encodeURIComponent(email)}`,
  );
  expect(response.ok()).toBe(true);
  const { token } = await response.json();
  return token;
}

export function uniqueAccount(prefix: string) {
  const id = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  return {
    email: `${prefix}-${id}@x.com`,
    username: `${prefix.slice(0, 4)}${id}`.slice(0, 20),
    password: 'tortoise-violin-42',
  };
}

/** Full sign-up through the UI, confirmed through the emailed link. */
export async function signUp(
  page: Page,
  request: APIRequestContext,
  account: { email: string; username: string; password: string },
): Promise<void> {
  await page.goto('/register');
  await page.locator('#email').fill(account.email);
  await page.locator('#username').fill(account.username);
  await page.locator('#password').fill(account.password);
  await page.locator('#confirmPassword').fill(account.password);
  await page.locator('#acceptTerms').check();
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page).toHaveURL(/\/check-email/);

  await page.goto(`/confirm-email?token=${await lastEmailToken(request, account.email)}`);
  await expect(page.getByRole('heading', { name: 'Email confirmed' })).toBeVisible();
}

export async function logIn(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
}
